package atri.palaash.jforge.inference;

import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import atri.palaash.jforge.model.TaskType;
import atri.palaash.jforge.storage.ModelStorage;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

/**
 * Entry point for ONNX inference: dispatches each request to the
 * architecture-specific pipeline class.
 * <p>
 * This class is a thin router. All shared state and reusable helpers
 * (session caching, tokenizers, tensor extraction, scheduler math, image
 * conversion) live in {@link OnnxPipelineBase}; each model architecture has
 * its own pipeline class ({@link Sd15OnnxPipeline}, {@link SdTurboOnnxPipeline},
 * {@link SdxlTurboOnnxPipeline}, {@link SdxlBaseOnnxPipeline},
 * {@link Sd3OnnxPipeline}, {@link RealEsrganOnnxPipeline}). GPU provider
 * selection is handled here because it is shared by every pipeline.
 * </p>
 */
public class GenericOnnxService extends OnnxPipelineBase implements InferenceService {

    /* ── Pre-warmed environment & provider cache ────────────────────── */
    /** Name of the GPU execution provider detected at startup (e.g. "CoreMLExecutionProvider"), or empty if using CPU. */
    private static volatile String detectedProvider = "";

    /** Whether the GPU availability probe has already been run (prevents repeated attempts). */
    private static boolean gpuProbed = false;
    /** Cached execution provider key; used to invalidate sessions when the EP changes between runs. */
    private static volatile String cachedEpKey = "";

    /** The kind of image task this service handles (e.g. text-to-image, upscaling). */
    private final TaskType taskType;
    /** Async executor for running inference without blocking the UI thread. */
    private final Executor executor;

    /**
     * Creates a new inference service for the given task type.
     *
     * @param taskType what kind of model this service will run (text-to-image, upscaling, etc.)
     * @param storage  provides the filesystem paths where model files are stored
     * @param executor thread pool for running inference asynchronously
     */
    public GenericOnnxService(TaskType taskType, ModelStorage storage, Executor executor) {
        super(storage);
        this.taskType = taskType;
        this.executor = executor;
        probeGpuOnce();
    }

    /**
     * Probe for available GPU execution providers once at startup.
     * Tries each candidate EP (CoreML, CUDA, TensorRT, etc.) and caches the first one that works.
     * Sets {@link #detectedProvider} to the winner, or leaves it empty for CPU-only.
     */
    private static synchronized void probeGpuOnce() {
        if (gpuProbed) return;
        gpuProbed = true;
        try {
            OrtEnvironment.getEnvironment(); // pre-warm
        } catch (Exception e) {
            System.err.println("[JForge] WARN: ONNX Runtime env init: " + e.getMessage());
        }
        try (OrtSession.SessionOptions opts = new OrtSession.SessionOptions()) {
            opts.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
            int cpus = Runtime.getRuntime().availableProcessors();
            opts.setIntraOpNumThreads(Math.max(1, cpus - 1));
            opts.setInterOpNumThreads(Math.max(1, Math.min(cpus / 2, 4)));
            tryEnableMemoryOptimizations(opts);
            // Attempt GPU providers; falls back to CPU if none available
            String os = System.getProperty("os.name", "").toLowerCase();
            String provider = tryProbeGpuProvider(opts, os);
            if (provider != null && !provider.isEmpty()) {
                detectedProvider = provider;
                System.out.println("[JForge] GPU probe: " + provider + " available ✓");
            } else {
                System.out.println("[JForge] GPU probe: no GPU provider available, will use CPU");
            }
        } catch (Exception e) {
            System.out.println("[JForge] GPU probe: " + e.getMessage());
        }
    }

    /**
     * Return the cached GPU provider name, or empty string if CPU is used.
     */
    public static String detectedProvider() {
        return detectedProvider;
    }

    /**
     * Evict all cached ORT sessions and tokenizers, and reset the EP cache key
     * (e.g. when the user explicitly switches execution providers).
     */
    public static synchronized void clearCache() {
        OnnxPipelineBase.clearCaches();
        cachedEpKey = "";
    }

    /**
     * {@inheritDoc}
     * <p>
     * Dispatches to the correct pipeline based on the model ID in the request.
     * Supported models: Real-ESRGAN, SD v1.5, SD Turbo, SDXL Turbo, SDXL Base, SD 3.x.
     * Also intercepts stderr to capture ONNX Runtime diagnostic messages and forwards them
     * to the progress callback.
     * </p>
     *
     * @param request the inference request specifying model, prompt, dimensions, etc.
     * @return a future that resolves to the inference result (image path or error)
     */
    @Override
    public CompletableFuture<InferenceResult> run(InferenceRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            if (!storage.isAvailable(request.model())) {
                System.out.println("[JForge] ERROR: Model not found locally: " + request.model().displayName());
                return InferenceResult.fail("Model not found locally. Open Model Manager from the menu bar and download it first.");
            }

            Path modelPath = storage.modelPath(request.model());
            System.out.println("[JForge] Starting inference: " + request.model().displayName()
                    + " (" + request.model().id() + ")");

            // Temporarily intercept stderr so ONNX Runtime native warnings
            // appear in the application's Log tab instead of only in the console.
            PrintStream origErr = System.err;
            TeeOutputStream tee = new TeeOutputStream(origErr, request.progressCallback());
            System.setErr(new PrintStream(tee, true));

            try {
                request.reportProgress("Loading ONNX Runtime environment…");
                OrtEnvironment environment = OrtEnvironment.getEnvironment();
                try (OrtSession.SessionOptions sessionOptions = new OrtSession.SessionOptions()) {
                    sessionOptions.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT);
                    int cpus = Runtime.getRuntime().availableProcessors();
                    sessionOptions.setIntraOpNumThreads(Math.max(1, cpus - 1));
                    sessionOptions.setInterOpNumThreads(Math.max(1, Math.min(cpus / 2, 4)));
                    tryEnableMemoryOptimizations(sessionOptions);
                    ProviderSelection providerSelection = configureExecutionProvider(sessionOptions, request.preferGpu());
                    System.out.println("[JForge] Using EP: " + providerSelection.provider()
                            + (providerSelection.notes().isBlank() ? "" : " (" + providerSelection.notes() + ")"));

                    // Invalidate session cache when the execution provider changes.
                    String epKey = providerSelection.provider() + "|" + request.preferGpu();
                    if (!epKey.equals(cachedEpKey)) {
                        clearCache();
                        cachedEpKey = epKey;
                    }
                    if ("realesrgan".equals(request.model().id())) {
                        OrtSession session = getOrCreateSession(environment,
                                modelPath, sessionOptions);
                        return new RealEsrganOnnxPipeline(storage)
                                .run(environment, session, request, providerSelection.provider());
                    }
                    if ("sd_v15_onnx".equals(request.model().id())) {
                        return new Sd15OnnxPipeline(storage)
                                .run(environment, sessionOptions, request, providerSelection.provider());
                    }
                    if ("sd_turbo_onnx".equals(request.model().id())) {
                        return new SdTurboOnnxPipeline(storage)
                                .run(environment, sessionOptions, request, providerSelection.provider());
                    }
                    if ("sdxl_turbo_onnx".equals(request.model().id())) {
                        return new SdxlTurboOnnxPipeline(storage)
                                .run(environment, sessionOptions, request, providerSelection.provider());
                    }
                    if ("sdxl_base_onnx".equals(request.model().id())) {
                        return new SdxlBaseOnnxPipeline(storage)
                                .run(environment, sessionOptions, request, providerSelection.provider());
                    }
                    // SD 3.x (converted) — detected by transformer/ directory structure
                    if (request.model().relativePath() != null
                            && request.model().relativePath().contains("transformer/")) {
                        return new Sd3OnnxPipeline(storage)
                                .run(environment, sessionOptions, request, providerSelection.provider());
                    }
                    String details = "Model loaded but generation is not implemented for this ONNX pipeline: "
                            + request.model().displayName() + " | task=" + taskType.displayName()
                            + " | EP=" + providerSelection.provider()
                            + providerSelection.noteSuffix();
                    System.out.println("[JForge] WARN: " + details);
                    return InferenceResult.fail(details);
                }
            } catch (OrtException ex) {
                String message = ex.getMessage() == null ? "Unknown ONNX Runtime error" : ex.getMessage();
                System.out.println("[JForge] ERROR: ONNX Runtime error: " + message);
                if (message.contains("NhwcConv")) {
                    return InferenceResult.fail(
                            "This ONNX model uses custom ops (e.g., NhwcConv) not available in CPUExecutionProvider. "
                                    + "Choose a CPU-compatible ONNX export or switch to a different model.\nOriginal error: "
                                    + message
                    );
                }
                if (message.contains("weights.pb")) {
                    return InferenceResult.fail(
                            "Model requires external tensor data (weights.pb) that is missing in the same directory as the ONNX file. "
                                    + "Import the complete ONNX bundle.\nOriginal error: " + message
                    );
                }
                return InferenceResult.fail("Failed to load ONNX model on CPU: " + message);
            } finally {
                System.setErr(origErr);
            }
        }, executor);
    }

    /* ── Execution provider selection ───────────────────────────────── */

    /**
     * Build the EP preference list for the current platform, try each in order,
     * and return the first one that the loaded ONNX Runtime native library supports.
     *
     * <p>Priority order per platform (highest → lowest):
     * <ul>
     *   <li><b>macOS</b>: CoreML (GPU+ANE+CPU) → CPU</li>
     *   <li><b>Windows</b>: TensorRT-RTX → TensorRT → CUDA → DirectML → OpenVINO → CPU</li>
     *   <li><b>Linux</b>: TensorRT → CUDA → ROCm → OpenVINO → CPU</li>
     * </ul>
     *
     * <p>Override with {@code -Djforge.ep=cuda} (or any key) to force a specific EP.
     */
    private ProviderSelection configureExecutionProvider(OrtSession.SessionOptions options, boolean preferGpu) {
        String os = System.getProperty("os.name", "unknown").toLowerCase();
        String arch = System.getProperty("os.arch", "unknown").toLowerCase();
        String forced = System.getProperty("jforge.ep", "").trim().toLowerCase();

        List<String> preference = new ArrayList<>();
        if (!preferGpu) {
            preference.add("cpu");
        } else if (!forced.isBlank()) {
            preference.add(forced);
            preference.add("cpu");
        } else if (os.contains("mac")) {
            preference.add("coreml");
            preference.add("cpu");
        } else if (os.contains("win")) {
            preference.add("tensorrt_rtx");   // RTX 30xx+ (Ampere+)
            preference.add("tensorrt");        // any NVIDIA with TensorRT libs
            preference.add("cuda");            // CUDA fallback
            preference.add("directml");        // AMD / Intel / any DX12 GPU
            preference.add("openvino");        // Intel CPUs/GPUs/NPUs
            preference.add("cpu");
        } else {
            // Linux
            preference.add("tensorrt");
            preference.add("cuda");
            preference.add("rocm");            // AMD GPUs
            preference.add("openvino");
            preference.add("cpu");
        }

        StringBuilder notes = new StringBuilder();
        List<String> failReasons = new ArrayList<>();
        System.out.println("[JForge] EP preference order: " + preference
                + "  (os=" + os + ", arch=" + arch + ")");

        if (!preferGpu) {
            System.out.println("[JForge] GPU not requested for this session — using CPUExecutionProvider");
            return new ProviderSelection("CPUExecutionProvider", "GPU not requested");
        }

        for (String candidate : preference) {
            if ("cpu".equals(candidate)) {
                // All GPU EPs exhausted — log summary
                System.out.println("[JForge] WARN: Falling back to CPUExecutionProvider");
                if (!failReasons.isEmpty()) {
                    System.out.println("[JForge] WARN: Reason: every GPU execution provider was unavailable:");
                    for (String reason : failReasons) {
                        System.out.println("[JForge] WARN:   - " + reason);
                    }
                    System.out.println("[JForge] WARN: Tip: install the matching GPU runtime "
                            + "(e.g. CUDA/cuDNN for NVIDIA, ROCm for AMD) or use "
                            + "-Djforge.ep=<provider> to force a specific EP.");
                }
                return new ProviderSelection("CPUExecutionProvider", notes.toString());
            }
            String failReason = tryEnableProvider(options, candidate, notes);
            if (failReason == null) {
                String display = providerDisplayName(candidate);
                System.out.println("[JForge] ✓ Enabled " + display);
                return new ProviderSelection(display, notes.toString());
            }
            failReasons.add(failReason);
        }
        // Preference list didn't include "cpu" explicitly — shouldn't happen, but handle it
        System.out.println("[JForge] WARN: No EP available, falling back to CPUExecutionProvider");
        if (!failReasons.isEmpty()) {
            System.out.println("[JForge] WARN: Reasons:");
            for (String reason : failReasons) {
                System.out.println("[JForge] WARN:   - " + reason);
            }
        }
        return new ProviderSelection("CPUExecutionProvider", notes.toString());
    }

    /**
     * Best-effort enable CPU memory arena and memory pattern optimization
     * via reflection (available in newer ORT Java bindings).
     */
    private static void tryEnableMemoryOptimizations(OrtSession.SessionOptions opts) {
        try {
            opts.getClass().getMethod("setMemoryPatternOptimization", boolean.class)
                    .invoke(opts, true);
        } catch (Exception ignored) { }
        try {
            opts.getClass().getMethod("setEnableCpuMemArena", boolean.class)
                    .invoke(opts, true);
        } catch (Exception ignored) { }
    }

    /**
     * Quick GPU provider probe — tries each candidate EP once and returns
     * the first that succeeds, or empty string for CPU.
     */
    private static String tryProbeGpuProvider(OrtSession.SessionOptions opts, String os) {
        List<String> candidates = new ArrayList<>();
        if (os.contains("mac")) {
            candidates.add("coreml");
        } else if (os.contains("win")) {
            candidates.add("tensorrt_rtx");
            candidates.add("tensorrt");
            candidates.add("cuda");
            candidates.add("directml");
        } else {
            candidates.add("tensorrt");
            candidates.add("cuda");
            candidates.add("rocm");
        }
        StringBuilder notes = new StringBuilder();
        for (String c : candidates) {
            String fail = tryEnableProvider(opts, c, notes);
            if (fail == null) return providerDisplayName(c);
        }
        return "";
    }

    /**
     * Attempt to enable a single execution provider on the given session options.
     * Uses reflection to call the appropriate {@code add<Provider>} method, so the code
     * compiles and runs even when the native GPU libraries aren't on the classpath.
     *
     * @param options   the session options to configure
     * @param candidate the provider name (e.g. "cuda", "coreml", "tensorrt")
     * @param notes     StringBuilder to accumulate diagnostic notes about availability
     * @return null if the provider was enabled successfully, or a human-readable failure reason
     */
    private static String tryEnableProvider(OrtSession.SessionOptions options, String candidate, StringBuilder notes) {
        String failDetail = null;
        try {
            boolean ok = switch (candidate) {

                /* ── NVIDIA ───────────────────────────────────────────── */
                case "tensorrt_rtx" ->
                    // NvTensorRtRtxExecutionProvider – RTX 30xx+ only
                    // Java method not yet in stock Maven artifacts; try reflection just in case
                    invokeNoArg(options, "addNvTensorRtRtx")
                    || invokeIntArg(options, "addNvTensorRtRtx", 0);

                case "tensorrt" ->
                    // TensorrtExecutionProvider – available in onnxruntime_gpu
                    invokeIntArg(options, "addTensorrt", 0)
                    || invokeNoArg(options, "addTensorrt");

                case "cuda" ->
                    invokeNoArg(options, "addCUDA");

                /* ── Apple ────────────────────────────────────────────── */
                case "coreml" -> {
                    // addCoreML(long flags) — note: parameter is long, not int!
                    // Flag 0x0 = ALL compute units (CPU+GPU+ANE — best for M-series)
                    // TODO(future): Convert models with Apple's coremltools so all ops are
                    //   CoreML-native. Currently only ~33% of UNet nodes run on CoreML and
                    //   the text_encoder embedding (49408×768) exceeds CoreML's 16384-dim
                    //   limit, forcing most of the graph to CPU. A coremltools-converted
                    //   model would run fully on GPU/ANE with no CPU transfers and no
                    //   ORT-level context leak.
                    boolean coreOk = invokeLongArg(options, "addCoreML", 0L);
                    if (!coreOk) { coreOk = invokeNoArg(options, "addCoreML"); }
                    yield coreOk;
                }

                /* ── Microsoft ────────────────────────────────────────── */
                case "directml" ->
                    invokeIntArg(options, "addDirectML", 0)
                    || invokeNoArg(options, "addDirectML");

                /* ── Intel ────────────────────────────────────────────── */
                case "openvino" ->
                    // addOpenVINO(String) — device type "GPU" preferred, "CPU" fallback
                    invokeStringArg(options, "addOpenVINO", "GPU")
                    || invokeStringArg(options, "addOpenVINO", "CPU")
                    || invokeNoArg(options, "addOpenVINO");

                /* ── AMD ──────────────────────────────────────────────── */
                case "rocm" ->
                    invokeNoArg(options, "addROCM");

                default -> false;
            };
            if (!ok) {
                failDetail = candidate + ": native library not found in classpath";
            }
        } catch (Exception ex) {
            String msg = ex.getMessage();
            if (msg == null && ex.getCause() != null) { msg = ex.getCause().getMessage(); }
            failDetail = candidate + ": " + (msg != null ? msg : ex.getClass().getSimpleName());
        }

        if (failDetail != null) {
            if (!notes.isEmpty()) { notes.append("; "); }
            notes.append(candidate).append(" not available");
            System.out.println("[JForge] WARN: ✗ " + failDetail);
        }
        return failDetail;
    }

    /* ── Reflection helpers for EP registration ──────────────────────── */

    /**
     * Invoke a no-arg method on SessionOptions via reflection.
     */
    private static boolean invokeNoArg(OrtSession.SessionOptions options, String methodName) {
        try {
            options.getClass().getMethod(methodName).invoke(options);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Invoke a method on SessionOptions that takes a single int argument, via reflection.
     */
    private static boolean invokeIntArg(OrtSession.SessionOptions options, String methodName, int arg) {
        try {
            options.getClass().getMethod(methodName, int.class).invoke(options, arg);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Invoke a method on SessionOptions that takes a single long argument, via reflection.
     * Needed for CoreML EP which uses a long flags parameter.
     */
    private static boolean invokeLongArg(OrtSession.SessionOptions options, String methodName, long arg) {
        try {
            options.getClass().getMethod(methodName, long.class).invoke(options, arg);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Invoke a method on SessionOptions that takes a single String argument, via reflection.
     * Used for OpenVINO EP which expects a device type string ("GPU" or "CPU").
     */
    private static boolean invokeStringArg(OrtSession.SessionOptions options, String methodName, String arg) {
        try {
            options.getClass().getMethod(methodName, String.class).invoke(options, arg);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /**
     * Convert a short provider identifier to its ONNX Runtime execution provider display name.
     */
    private static String providerDisplayName(String candidate) {
        return switch (candidate) {
            case "tensorrt_rtx" -> "NvTensorRtRtxExecutionProvider";
            case "tensorrt"     -> "TensorrtExecutionProvider";
            case "cuda"         -> "CUDAExecutionProvider";
            case "coreml"       -> "CoreMLExecutionProvider";
            case "directml"     -> "DmlExecutionProvider";
            case "openvino"     -> "OpenVINOExecutionProvider";
            case "rocm"         -> "ROCMExecutionProvider";
            default             -> "CPUExecutionProvider";
        };
    }

    /**
     * Result of selecting an execution provider: the provider display name and any
     * diagnostic notes about what other providers were tried and why they failed.
     */
    private record ProviderSelection(String provider, String notes) {
        /**
         * Returns a formatted suffix string for log messages, or empty if no notes exist.
         *
         * @return a string like " | cuda not available; ..." or ""
         */
        String noteSuffix() {
            if (notes == null || notes.isBlank()) {
                return "";
            }
            return " | " + notes;
        }
    }

    /**
     * An OutputStream that writes to the original stderr AND forwards
     * complete lines to a progress callback (for ONNX Runtime log capture).
     */
    private static class TeeOutputStream extends OutputStream {
        /** Maximum bytes to accumulate before force-flushing a partial line (64 KB). */
        private static final int MAX_LINE_BUFFER = 64 * 1024; // 64 KB guard
        /** The original stderr PrintStream that all output is also written to. */
        private final PrintStream original;
        /** Consumer that receives each complete line for forwarding to the UI's Log tab. */
        private final Consumer<String> callback;
        /** Buffer accumulating bytes until a newline is encountered. */
        private final ByteArrayOutputStream lineBuffer = new ByteArrayOutputStream();

        TeeOutputStream(PrintStream original, Consumer<String> callback) {
            this.original = original;
            this.callback = callback;
        }

        /**
         * Write a single byte: forward to original stderr, buffer for line detection.
         * When a newline byte ('\n') is received, the buffered line is flushed to the callback.
         */
        @Override
        public void write(int b) {
            original.write(b);
            if (b == '\n') {
                flushLine();
            } else {
                lineBuffer.write(b);
                if (lineBuffer.size() > MAX_LINE_BUFFER) flushLine();
            }
        }

        /**
         * Write a byte array: forward to original stderr, scan for newlines to flush lines.
         * A 64 KB guard forces a partial flush if the buffer grows too large without a newline.
         */
        @Override
        public void write(byte[] buf, int off, int len) {
            original.write(buf, off, len);
            for (int i = off; i < off + len; i++) {
                if (buf[i] == '\n') {
                    flushLine();
                } else {
                    lineBuffer.write(buf[i]);
                    if (lineBuffer.size() > MAX_LINE_BUFFER) flushLine();
                }
            }
        }

        /**
         * Flush the original stderr. Does NOT flush the line buffer (that happens on newlines).
         */
        @Override
        public void flush() {
            original.flush();
        }

        /**
         * Flush the accumulated line buffer to the progress callback.
         * Cleans up ONNX Runtime timestamp prefixes and filters noisy messages
         * (e.g. "Context leak detected").
         */
        private void flushLine() {
            String line = lineBuffer.toString(StandardCharsets.UTF_8).trim();
            lineBuffer.reset();
            if (!line.isEmpty() && callback != null) {
                // Filter out noisy/irrelevant lines
                if (line.contains("Context leak detected")) { return; }
                // Clean up ORT timestamp prefix: keep only the message after the bracket
                int bracket = line.indexOf(']');
                String display = (bracket > 0 && bracket < line.length() - 2)
                        ? "ORT: " + line.substring(bracket + 2).trim()
                        : "ORT: " + line;
                callback.accept(display);
            }
        }
    }
}
