package atri.palaash.jforge.inference;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import ai.onnxruntime.TensorInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import atri.palaash.jforge.engine.scheduler.SchedulerMath;
import atri.palaash.jforge.engine.random.LatentNoise;
import atri.palaash.jforge.storage.ModelStorage;
import atri.palaash.jforge.tokenize.ClipTokenizer;
import atri.palaash.jforge.tokenize.T5Tokenizer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared state and helpers for all ONNX pipeline implementations.
 * <p>
 * Holds the ONNX session cache, tokenizer caches, model storage handle, and
 * the reusable math/encoding helpers (text encoders, tensor extraction,
 * scheduler math, image conversion). Concrete pipelines extend this class and
 * implement a single {@code run} method for their architecture, so no single
 * god object owns every pipeline.
 * </p>
 */
abstract class OnnxPipelineBase {

    /** Shared Jackson JSON mapper used for reading scheduler configs and tokenizer files. */
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /* ── Session & Tokenizer Caches ────────────────────────────────── */
    /**
     * Maximum number of ONNX sessions to keep cached simultaneously.
     * Each session can consume 200 MB – 6 GB+ of native memory, so we
     * cap the cache to prevent OOM.  An LRU insertion-order policy
     * evicts the oldest session when this limit is reached.
     */
    protected static final int MAX_CACHED_SESSIONS = 5;
    protected static final java.util.LinkedHashMap<String, OrtSession> SESSION_CACHE =
            new java.util.LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, OrtSession> eldest) {
                    if (size() > MAX_CACHED_SESSIONS) {
                        try {
                            System.out.println("[JForge] Evicting cached session (LRU): "
                                    + Path.of(eldest.getKey()).getFileName());
                            eldest.getValue().close();
                        } catch (Exception ignored) { }
                        return true;
                    }
                    return false;
                }
            };
    /** Cache of loaded CLIP tokenizers keyed by "vocabPath|mergesPath". Tokenizers are expensive to parse from JSON. */
    protected static final ConcurrentHashMap<String, ClipTokenizer> TOKENIZER_CACHE = new ConcurrentHashMap<>();
    /** Cache of loaded T5 (SentencePiece) tokenizers keyed by tokenizer.json path. */
    protected static final ConcurrentHashMap<String, T5Tokenizer> T5_TOKENIZER_CACHE = new ConcurrentHashMap<>();

    /** Persistent storage for locating model files on disk. */
    protected final ModelStorage storage;

    /**
     * @param storage provides the filesystem paths where model files are stored
     */
    OnnxPipelineBase(ModelStorage storage) {
        this.storage = storage;
    }

    /**
     * Evict all cached ORT sessions and tokenizers. Called when the execution
     * provider changes between runs.
     */
    public static synchronized void clearCaches() {
        SESSION_CACHE.forEach((k, session) -> {
            try { session.close(); } catch (Exception ignored) { }
        });
        SESSION_CACHE.clear();
        TOKENIZER_CACHE.clear();
        T5_TOKENIZER_CACHE.clear();
    }

    /* ── Cache helpers ─────────────────────────────────────────────── */

    /**
     * Return a cached OrtSession for the given model path, or create and
     * cache a new one. Subsequent inference calls skip expensive model
     * loading and graph optimization — the single biggest performance win.
     */
    protected synchronized OrtSession getOrCreateSession(OrtEnvironment env, Path modelPath,
                                           OrtSession.SessionOptions opts) throws OrtException {
        String key = modelPath.toAbsolutePath().toString();
        OrtSession existing = SESSION_CACHE.get(key);
        if (existing != null) {
            System.out.println("[JForge] Session cache hit: " + modelPath.getFileName());
            return existing;
        }
        System.out.println("[JForge] Loading ONNX session: " + modelPath.getFileName());
        OrtSession session = env.createSession(modelPath.toString(), opts);
        SESSION_CACHE.put(key, session);
        System.out.println("[JForge] Session loaded: " + modelPath.getFileName());
        return session;
    }

    /**
     * Close and remove a specific session from the cache to free native memory.
     * Used for sequential model loading in large pipelines (e.g. SD 3.5)
     * where text encoders are only needed temporarily.
     */
    protected synchronized void evictSession(Path modelPath) {
        String key = modelPath.toAbsolutePath().toString();
        OrtSession session = SESSION_CACHE.remove(key);
        if (session != null) {
            try {
                session.close();
                System.out.println("[JForge] Evicted session: " + modelPath.getFileName());
            } catch (Exception ignored) { }
        }
    }

    /**
     * Get or load a ClipTokenizer — vocab.json and merges.txt are cached
     * in memory so repeated inference calls skip disk I/O and JSON parsing.
     */
    protected ClipTokenizer getOrCreateTokenizer(Path vocabPath, Path mergesPath) throws Exception {
        String key = vocabPath.toAbsolutePath() + "|" + mergesPath.toAbsolutePath();
        ClipTokenizer cached = TOKENIZER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        ClipTokenizer tokenizer = ClipTokenizer.load(vocabPath, mergesPath);
        TOKENIZER_CACHE.put(key, tokenizer);
        return tokenizer;
    }

    /**
     * Get or load a T5Tokenizer — tokenizer.json is cached in memory.
     */
    protected T5Tokenizer getOrCreateT5Tokenizer(Path tokenizerJsonPath) throws Exception {
        String key = tokenizerJsonPath.toAbsolutePath().toString();
        T5Tokenizer cached = T5_TOKENIZER_CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        T5Tokenizer tokenizer = T5Tokenizer.load(tokenizerJsonPath);
        T5_TOKENIZER_CACHE.put(key, tokenizer);
        return tokenizer;
    }

    /**
     * Resolve a tokenizer file path — try the turbo model directory first, then fall back
     * to the SD v1.5 directory (which shares the same vocabulary).
     *
     * @param turboBase    base path of the SD Turbo model
     * @param relativeName relative path to the tokenizer file (e.g. "tokenizer/vocab.json")
     * @return the first existing path, or the turbo path (which will trigger a missing-file error)
     */
    protected Path resolveTokenizerFile(Path turboBase, String relativeName) {
        Path turboPath = turboBase.resolve(relativeName);
        if (java.nio.file.Files.exists(turboPath)) { return turboPath; }
        Path v15Path = storage.root().resolve("text-image").resolve("stable-diffusion-v15").resolve(relativeName);
        if (java.nio.file.Files.exists(v15Path)) { return v15Path; }
        return turboPath; // will trigger missing-file error
    }

    /* ── Shared math (delegates to the reusable engine schedulers) ──── */

    /**
     * Generate evenly-spaced timesteps for turbo distillation (1000 → 0 in {@code steps} jumps).
     *
     * @param steps number of denoising steps
     * @return array of timestep indices descending from 999 to 0
     */
    protected static int[] turboTimesteps(int steps) {
        return SchedulerMath.turboTimesteps(steps);
    }

    /**
     * Approximate sigma (noise level) from a timestep for the turbo Euler scheduler.
     *
     * @param timestep the current denoising timestep (0–999)
     * @return the approximate sigma value for that timestep
     */
    protected static float turboSigma(int timestep) {
        return SchedulerMath.turboSigma(timestep);
    }

    /**
     * Single Euler integration step: x_{{t-1}} = x_t + (sigma_prev - sigma) * noise_pred.
     *
     * @param latents   current latent array [1][C][H][W]
     * @param noisePred predicted noise (velocity) from the UNet [C][H][W]
     * @param sigma     noise level at current timestep
     * @param sigmaPrev noise level at the next (earlier) timestep
     * @return updated latents after one Euler step
     */
    protected static float[][][][] eulerStep(float[][][][] latents, float[][][] noisePred,
                                           float sigma, float sigmaPrev) {
        return SchedulerMath.eulerStep(latents, noisePred, sigma, sigmaPrev);
    }

    /**
     * Compute the alpha_cumprod schedule inline when scheduler_config.json is unavailable.
     *
     * @param trainTimesteps total number of training timesteps (typically 1000)
     * @param betaStart      initial beta value (default 0.00085)
     * @param betaEnd        final beta value (default 0.012)
     * @return float array of cumulative alpha values
     */
    protected float[] computeDefaultAlphaCumprod(int trainTimesteps, double betaStart, double betaEnd) {
        return SchedulerMath.computeAlphaCumprod(trainTimesteps, betaStart, betaEnd);
    }

    /**
     * Create an evenly-spaced schedule of timestep indices from (trainTimesteps-1) down to 0.
     *
     * @param steps          number of denoising steps requested
     * @param trainTimesteps total timesteps the model was trained with (typically 1000)
     * @return array of {@code steps} timestep indices in descending order
     */
    protected int[] createTimesteps(int steps, int trainTimesteps) {
        return SchedulerMath.createTimesteps(steps, trainTimesteps);
    }

    /**
     * Duplicate a single latent sample into a batch of two (for classifier-free guidance).
     *
     * @param latents the original latent tensor [1][C][H][W]
     * @return a batched tensor [2][C][H][W] with both copies identical
     */
    protected float[][][][] duplicateBatch(float[][][][] latents) {
        return SchedulerMath.duplicateBatch(latents);
    }

    /**
     * Apply classifier-free guidance: steer the denoising process toward the conditional
     * prediction by extrapolating away from the unconditional prediction.
     *
     * @param uncond        UNet output for the unconditional (negative prompt) embedding
     * @param cond          UNet output for the conditional (prompt) embedding
     * @param guidanceScale how strongly to follow the prompt (7.5 is typical for SD v1.5)
     * @return the guided noise prediction
     */
    protected float[][][] guidance(float[][][] uncond, float[][][] cond, float guidanceScale) {
        return SchedulerMath.guidance(uncond, cond, guidanceScale);
    }

    /**
     * Single DDIM (Denoising Diffusion Implicit Model) step.
     *
     * @param latents   current noisy latents [1][C][H][W]
     * @param eps       predicted noise from the UNet
     * @param alphaT    alpha_cumprod at the current timestep
     * @param alphaPrev alpha_cumprod at the previous (earlier) timestep
     * @return denoised latents for the next timestep
     */
    protected float[][][][] ddimStep(float[][][][] latents,
                                    float[][][] eps,
                                    float alphaT,
                                    float alphaPrev) {
        return SchedulerMath.ddimStep(latents, eps, alphaT, alphaPrev);
    }

    /**
     * Element-wise multiply all latent values by a constant scaling factor.
     *
     * @param latents the input latent tensor
     * @param scale   the scalar multiplier
     * @return a new tensor with every element multiplied by {@code scale}
     */
    protected float[][][][] scaleLatents(float[][][][] latents, float scale) {
        return SchedulerMath.scaleLatents(latents, scale);
    }

    /* ── Text encoders ─────────────────────────────────────────────── */

    /**
     * Run the CLIP text encoder and return the 3-D hidden states (embedding).
     * Handles both INT32 and INT64 input types automatically by inspecting the model's metadata.
     *
     * @param environment the ONNX Runtime environment
     * @param session     the loaded text encoder session
     * @param tokenIds    tokenised prompt IDs (padded to the model's max length)
     * @return the encoder output as [1, seqLen, hiddenDim]
     * @throws OrtException if the ONNX Runtime call fails or the output is empty
     */
    protected float[][][] runTextEncoder(OrtEnvironment environment, OrtSession session, long[] tokenIds) throws OrtException {
        String inputName = resolveInputName(session, "input_ids", 0);
        TensorInfo inputInfo = (TensorInfo) session.getInputInfo().get(inputName).getInfo();
        boolean wantsInt32 = inputInfo.type.toString().contains("INT32");

        OnnxTensor idsTensor;
        if (wantsInt32) {
            int[] ids32 = new int[tokenIds.length];
            for (int i = 0; i < tokenIds.length; i++) { ids32[i] = (int) tokenIds[i]; }
            idsTensor = OnnxTensor.createTensor(environment, new int[][]{ids32});
        } else {
            idsTensor = OnnxTensor.createTensor(environment, new long[][]{tokenIds});
        }

        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(inputName, idsTensor);
        try (OrtSession.Result result = session.run(inputs)) {
            float[][][] embedding = extractTensor3d(result);
            if (embedding == null) {
                throw new OrtException("Text encoder output is empty.");
            }
            return embedding;
        } finally {
            idsTensor.close();
        }
    }

    /**
     * Run a text encoder and attempt to extract the pooled output (second output).
     * Returns null if the model doesn't produce a 2D pooled output.
     */
    protected float[][] runTextEncoderPooled(OrtEnvironment env, OrtSession session, long[] tokenIds) throws OrtException {
        String inName = resolveInputName(session, "input_ids", 0);
        TensorInfo tInfo = (TensorInfo) session.getInputInfo().get(inName).getInfo();
        boolean wantsInt32 = tInfo.type.toString().contains("INT32");
        OnnxTensor idsTensor;
        if (wantsInt32) {
            int[] ids32 = new int[tokenIds.length];
            for (int i = 0; i < tokenIds.length; i++) ids32[i] = (int) tokenIds[i];
            idsTensor = OnnxTensor.createTensor(env, new int[][]{ids32});
        } else {
            idsTensor = OnnxTensor.createTensor(env, new long[][]{tokenIds});
        }
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(inName, idsTensor);
        try (OrtSession.Result r = session.run(inputs)) {
            for (Map.Entry<String, OnnxValue> entry : r) {
                if (entry.getValue() instanceof OnnxTensor t) {
                    Object v = t.getValue();
                    if (v instanceof float[][] a2) return a2;
                }
            }
        } finally { idsTensor.close(); }
        return null;
    }

    /**
     * Run T5-XXL encoder and return hidden states padded/truncated to [1, maxSeqLen, expectedDim].
     */
    protected float[][][] runT5Encoder(OrtEnvironment env, OrtSession session,
                                      long[] tokenIds, int maxSeqLen, int expectedDim) throws OrtException {
        String inName = resolveInputName(session, "input_ids", 0);
        TensorInfo tInfo = (TensorInfo) session.getInputInfo().get(inName).getInfo();
        boolean wantsInt32 = tInfo.type.toString().contains("INT32");
        OnnxTensor idsTensor;
        if (wantsInt32) {
            int[] ids32 = new int[tokenIds.length];
            for (int i = 0; i < tokenIds.length; i++) ids32[i] = (int) tokenIds[i];
            idsTensor = OnnxTensor.createTensor(env, new int[][]{ids32});
        } else {
            idsTensor = OnnxTensor.createTensor(env, new long[][]{tokenIds});
        }
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(inName, idsTensor);
        try (OrtSession.Result r = session.run(inputs)) {
            float[][][] raw = null;
            for (Map.Entry<String, OnnxValue> entry : r) {
                if (entry.getValue() instanceof OnnxTensor t) {
                    Object v = t.getValue();
                    if (v instanceof float[][][] a3) { raw = a3; break; }
                }
            }
            if (raw == null) {
                return new float[1][maxSeqLen][expectedDim]; // zeros fallback
            }
            // Pad or truncate to [1, maxSeqLen, expectedDim]
            int srcSeq = raw[0].length;
            int srcDim = raw[0][0].length;
            float[][][] result = new float[1][maxSeqLen][expectedDim];
            int copySeq = Math.min(srcSeq, maxSeqLen);
            int copyDim = Math.min(srcDim, expectedDim);
            for (int s = 0; s < copySeq; s++) {
                System.arraycopy(raw[0][s], 0, result[0][s], 0, copyDim);
            }
            return result;
        } finally {
            idsTensor.close();
        }
    }

    /* ── Tensor extraction / session helpers ───────────────────────── */

    /**
     * Extract the first 3-D float tensor from an ONNX session result.
     *
     * @param result the ONNX session output
     * @return the first [float[][][]] tensor found, or null if none exists
     * @throws OrtException if tensor value extraction fails
     */
    protected float[][][] extractTensor3d(OrtSession.Result result) throws OrtException {
        for (Map.Entry<String, OnnxValue> entry : result) {
            OnnxValue val = entry.getValue();
            if (val instanceof OnnxTensor tensor) {
                Object value = tensor.getValue();
                val.close();
                if (value instanceof float[][][] arr) {
                    return arr;
                }
            }
        }
        return null;
    }

    /**
     * Extract the first 4-D float tensor from an ONNX session result.
     *
     * @param result the ONNX session output
     * @return the first [float[][][][]] tensor found, or null if none exists
     * @throws OrtException if tensor value extraction fails
     */
    protected float[][][][] extractTensor4d(OrtSession.Result result) throws OrtException {
        for (Map.Entry<String, OnnxValue> entry : result) {
            OnnxValue val = entry.getValue();
            if (val instanceof OnnxTensor tensor) {
                Object value = tensor.getValue();
                val.close();
                if (value instanceof float[][][][] arr) {
                    return arr;
                }
            }
        }
        return null;
    }

    /**
     * Find the actual input name of an ONNX model by matching a preferred substring.
     * Falls back to positional index if no name contains the preferred string.
     *
     * @param session       the loaded ONNX session
     * @param preferred     preferred input name substring to match (e.g. "input_ids")
     * @param fallbackIndex positional index to use if no name matches the preferred string
     * @return the matched input name
     */
    protected String resolveInputName(OrtSession session, String preferred, int fallbackIndex) {
        List<String> names = new ArrayList<>(session.getInputNames());
        for (String name : names) {
            if (name.toLowerCase().contains(preferred.toLowerCase())) {
                return name;
            }
        }
        if (fallbackIndex >= 0 && fallbackIndex < names.size()) {
            return names.get(fallbackIndex);
        }
        return names.get(0);
    }

    /**
     * Create an ONNX tensor for the timestep input, matching the model's expected data type
     * (INT64, INT32, or FLOAT) by inspecting the session metadata.
     *
     * @param environment the ONNX Runtime environment
     * @param session     the loaded ONNX session (used to sniff the input type)
     * @param timestep    the scalar timestep value
     * @return an OnnxTensor containing the timestep in the correct type
     * @throws OrtException if tensor creation fails
     */
    protected OnnxTensor createTimestepTensor(OrtEnvironment environment, OrtSession session, int timestep) throws OrtException {
        String timestepName = resolveInputName(session, "timestep", 1);
        TensorInfo info = (TensorInfo) session.getInputInfo().get(timestepName).getInfo();
        if (info.type.toString().contains("INT64")) {
            return OnnxTensor.createTensor(environment, new long[]{timestep});
        }
        return OnnxTensor.createTensor(environment, new float[]{(float) timestep});
    }

    /**
     * Create a 4-D tensor of random Gaussian noise for the initial latent space.
     * SD models typically use 4-channel latents (for v1.5/SDXL) or 16-channel (for SD 3.x).
     *
     * @param seed         random seed for reproducible generation
     * @param latentHeight height of the latent grid (image height / 8)
     * @param latentWidth  width of the latent grid (image width / 8)
     * @return a [1][4][latentHeight][latentWidth] tensor filled with N(0,1) noise
     */
    protected float[][][][] randomLatents(long seed, int latentHeight, int latentWidth) {
        return LatentNoise.randomLatents(seed, 4, latentHeight, latentWidth);
    }

    /**
     * Load or compute the cumulative alpha schedule from a HuggingFace-style scheduler_config.json.
     *
     * @param schedulerConfigPath path to the scheduler_config.json file
     * @return float array of alpha_cumprod values from t=0 to t=trainTimesteps-1
     * @throws Exception if the JSON file cannot be read or parsed
     */
    protected float[] loadAlphaCumprod(Path schedulerConfigPath) throws Exception {
        Map<String, Object> config = OBJECT_MAPPER.readValue(schedulerConfigPath.toFile(), new TypeReference<>() {
        });
        int trainTimesteps = ((Number) config.getOrDefault("num_train_timesteps", 1000)).intValue();
        double betaStart = ((Number) config.getOrDefault("beta_start", 0.00085)).doubleValue();
        double betaEnd = ((Number) config.getOrDefault("beta_end", 0.012)).doubleValue();
        float[] alphaCumprod = new float[trainTimesteps];
        double cumulative = 1.0;
        for (int i = 0; i < trainTimesteps; i++) {
            double beta = betaStart + (betaEnd - betaStart) * i / Math.max(1, trainTimesteps - 1);
            cumulative *= (1.0 - beta);
            alphaCumprod[i] = (float) cumulative;
        }
        return alphaCumprod;
    }

    /* ── Image conversion / output ─────────────────────────────────── */

    /**
     * Convert a 3-D float tensor (channels × height × width) to a BufferedImage.
     * Values are assumed to be in the range [-1, 1] — they are mapped to [0, 255] byte values.
     *
     * @param tensor the decoded image tensor [3][H][W] (R, G, B)
     * @return a BufferedImage of TYPE_INT_RGB
     * @throws IllegalStateException if the tensor has fewer than 3 channels
     */
    protected BufferedImage tensorToImage(float[][][] tensor) {
        int channels = tensor.length;
        int height = tensor[0].length;
        int width = tensor[0][0].length;
        if (channels < 3) {
            throw new IllegalStateException("Decoded image tensor must contain at least 3 channels.");
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = toRgbByte(tensor[0][y][x]);
                int g = toRgbByte(tensor[1][y][x]);
                int b = toRgbByte(tensor[2][y][x]);
                image.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return image;
    }

    /**
     * Convert a float value (expected in [-1, 1]) to an 8-bit RGB byte (0–255).
     *
     * @param value the float pixel value (typically from a VAE decoder output)
     * @return an integer in the range [0, 255]
     */
    protected int toRgbByte(float value) {
        float normalized = (value / 2f) + 0.5f;
        float clipped = Math.max(0f, Math.min(1f, normalized));
        return Math.round(clipped * 255f);
    }

    /**
     * Write a BufferedImage to disk as a PNG file in the outputs/images directory.
     *
     * @param image  the image to save
     * @param prefix model-specific prefix (e.g. "sd-v15", "sdxl-base")
     * @return the absolute path to the saved PNG file
     * @throws java.io.IOException if file creation or image writing fails
     */
    protected Path writeOutputImage(BufferedImage image, String prefix) throws java.io.IOException {
        Path outputDir = storage.root().resolve("outputs").resolve("images");
        java.nio.file.Files.createDirectories(outputDir);
        String fileName = prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSSS")) + ".png";
        Path out = outputDir.resolve(fileName);
        ImageIO.write(image, "png", out.toFile());
        return out;
    }

    /* ── Real-ESRGAN image helpers ─────────────────────────────────── */

    /**
     * Convert a BufferedImage to a flat float array in NCHW (batch × channels × height × width) layout.
     * Pixel values are normalised to [0, 1].
     *
     * @param image the source image
     * @return a float array of length 3 * width * height, with channels stored as contiguous blocks
     */
    protected float[] imageToNchw(BufferedImage image) {
        int width = image.getWidth();
        int height = image.getHeight();
        float[] tensor = new float[3 * width * height];
        int redOffset = 0;
        int greenOffset = width * height;
        int blueOffset = 2 * width * height;

        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                float r = ((rgb >> 16) & 0xFF) / 255.0f;
                float g = ((rgb >> 8) & 0xFF) / 255.0f;
                float b = (rgb & 0xFF) / 255.0f;
                tensor[redOffset + idx] = r;
                tensor[greenOffset + idx] = g;
                tensor[blueOffset + idx] = b;
                idx++;
            }
        }
        return tensor;
    }

    /**
     * Extract the first 4-D image tensor (batch × 3 × H × W) from an ONNX session result.
     * Only the first batch element is returned, flattened to a 1-D float array.
     *
     * @param result the ONNX session output
     * @return an ImageOutput record containing the pixel data, width, and height, or null if no suitable tensor is found
     * @throws OrtException if tensor value extraction fails
     */
    protected ImageOutput extractFirstImageOutput(OrtSession.Result result) throws OrtException {
        for (Map.Entry<String, OnnxValue> entry : result) {
            OnnxValue value = entry.getValue();
            if (value instanceof OnnxTensor tensor) {
                TensorInfo info = (TensorInfo) tensor.getInfo();
                long[] shape = info.getShape();
                if (shape.length == 4 && (shape[1] == 3 || shape[1] == -1)) {
                    Object raw = tensor.getValue();
                    if (raw instanceof float[][][][] arr) {
                        int h = arr[0][0].length;
                        int w = arr[0][0][0].length;
                        float[] out = new float[3 * h * w];
                        int idx = 0;
                        for (int c = 0; c < 3; c++) {
                            for (int y = 0; y < h; y++) {
                                for (int x = 0; x < w; x++) {
                                    out[idx++] = arr[0][c][y][x];
                                }
                            }
                        }
                        return new ImageOutput(out, w, h);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Holds the raw pixel data and dimensions of an image output from an ONNX model.
     * The values array is flat NCHW (R block, G block, B block) of floats in [0, 1].
     */
    protected record ImageOutput(float[] values, int width, int height) {
    }

    /**
     * Convert a flat NCHW float array (R, G, B blocks) to a BufferedImage.
     * Values are expected in [0, 1] range and are clamped to [0, 1] before scaling to bytes.
     *
     * @param values flat float array of length 3 * width * height
     * @param width  image width in pixels
     * @param height image height in pixels
     * @return a BufferedImage of TYPE_INT_RGB
     */
    protected BufferedImage nchwToImage(float[] values, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int redOffset = 0;
        int greenOffset = width * height;
        int blueOffset = 2 * width * height;

        int idx = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int r = clampToByte(values[redOffset + idx]);
                int g = clampToByte(values[greenOffset + idx]);
                int b = clampToByte(values[blueOffset + idx]);
                int rgb = (r << 16) | (g << 8) | b;
                image.setRGB(x, y, rgb);
                idx++;
            }
        }
        return image;
    }

    /**
     * Clamp a float value to [0, 1] and scale to an 8-bit byte [0, 255].
     *
     * @param value the input float (expected in [0, 1] range)
     * @return an integer in [0, 255]
     */
    protected int clampToByte(float value) {
        float normalized = Math.max(0f, Math.min(1f, value));
        return Math.round(normalized * 255f);
    }

    /**
     * Run a single ESRGAN upscale pass. Uses tiling when the image does not
     * match the model's fixed input size, or runs directly otherwise.
     */
    protected BufferedImage esrganUpscaleOnce(OrtEnvironment environment, OrtSession session,
                                            BufferedImage inputImage, String inputName,
                                            int tileH, int tileW, int scaleFactor) throws Exception {
        int imgW = inputImage.getWidth();
        int imgH = inputImage.getHeight();
        boolean needsTiling = tileH > 0 && tileW > 0 && (imgW != tileW || imgH != tileH);

        if (!needsTiling) {
            // Direct inference — image matches model input or model accepts dynamic shapes.
            float[] tensorData = imageToNchw(inputImage);
            OnnxTensor tensor = OnnxTensor.createTensor(environment,
                    FloatBuffer.wrap(tensorData), new long[]{1, 3, imgH, imgW});
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, tensor);
            try (OrtSession.Result result = session.run(inputs)) {
                ImageOutput output = extractFirstImageOutput(result);
                if (output == null) { throw new RuntimeException("Real-ESRGAN produced no output tensor."); }
                return nchwToImage(output.values(), output.width(), output.height());
            } finally {
                tensor.close();
            }
        }

        // Tiled inference.
        int outW = imgW * scaleFactor;
        int outH = imgH * scaleFactor;
        BufferedImage outImage = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);

        int pad = Math.max(4, tileW / 16); // overlap padding to avoid seam artifacts
        for (int ty = 0; ty < imgH; ty += tileH - pad * 2) {
            for (int tx = 0; tx < imgW; tx += tileW - pad * 2) {
                int sx = Math.max(0, tx - pad);
                int sy = Math.max(0, ty - pad);
                int sw = Math.min(tileW, imgW - sx);
                int sh = Math.min(tileH, imgH - sy);

                BufferedImage tile = new BufferedImage(tileW, tileH, BufferedImage.TYPE_INT_RGB);
                java.awt.Graphics2D g = tile.createGraphics();
                g.drawImage(inputImage.getSubimage(sx, sy, sw, sh), 0, 0, null);
                g.dispose();

                float[] tileData = imageToNchw(tile);
                OnnxTensor tensor = OnnxTensor.createTensor(environment,
                        FloatBuffer.wrap(tileData), new long[]{1, 3, tileH, tileW});
                Map<String, OnnxTensor> inputs = new HashMap<>();
                inputs.put(inputName, tensor);

                try (OrtSession.Result result = session.run(inputs)) {
                    ImageOutput output = extractFirstImageOutput(result);
                    if (output == null) { continue; }
                    BufferedImage upTile = nchwToImage(output.values(), output.width(), output.height());

                    int dstPadX = (sx == 0 ? 0 : pad) * scaleFactor;
                    int dstPadY = (sy == 0 ? 0 : pad) * scaleFactor;
                    int dstX = sx * scaleFactor + dstPadX;
                    int dstY = sy * scaleFactor + dstPadY;
                    int copyW = Math.min(sw * scaleFactor - dstPadX, outW - dstX);
                    int copyH = Math.min(sh * scaleFactor - dstPadY, outH - dstY);
                    if (copyW <= 0 || copyH <= 0) { continue; }

                    BufferedImage region = upTile.getSubimage(dstPadX, dstPadY, copyW, copyH);
                    java.awt.Graphics2D g2 = outImage.createGraphics();
                    g2.drawImage(region, dstX, dstY, null);
                    g2.dispose();
                } finally {
                    tensor.close();
                }
            }
        }
        return outImage;
    }

    /** Run a 1-pixel tile through the model to discover its scale factor. */
    protected int detectScaleFactor(OrtEnvironment environment, OrtSession session,
                                  String inputName, int tileH, int tileW) {
        try {
            float[] probe = new float[3 * tileH * tileW];
            OnnxTensor tensor = OnnxTensor.createTensor(environment,
                    FloatBuffer.wrap(probe), new long[]{1, 3, tileH, tileW});
            Map<String, OnnxTensor> inputs = new HashMap<>();
            inputs.put(inputName, tensor);
            try (OrtSession.Result result = session.run(inputs)) {
                ImageOutput output = extractFirstImageOutput(result);
                if (output != null && output.width() > 0) {
                    return output.width() / tileW;
                }
            } finally {
                tensor.close();
            }
        } catch (Exception ignored) { }
        return 4; // default Real-ESRGAN scale
    }

    /**
     * Resize an image to the given target dimensions using the specified method.
     * Supported methods: ESRGAN Multi-Pass, Bicubic, Bilinear, Nearest Neighbor, Lanczos.
     * "ESRGAN Multi-Pass" falls back to Bicubic for the final adjustment step.
     */
    protected BufferedImage resizeImage(BufferedImage image, int targetW, int targetH, String method) {
        if (image.getWidth() == targetW && image.getHeight() == targetH) { return image; }

        Object interpolationHint = switch (method) {
            case "Nearest Neighbor" -> java.awt.RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR;
            case "Bilinear"         -> java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR;
            default                 -> java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC;
        };

        if ("Lanczos".equals(method)) {
            return lanczosResize(image, targetW, targetH);
        }

        BufferedImage resized = new BufferedImage(targetW, targetH, BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = resized.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION, interpolationHint);
        g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                java.awt.RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING,
                java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(image, 0, 0, targetW, targetH, null);
        g.dispose();
        return resized;
    }

    /**
     * Lanczos-3 resize (windowed sinc). Produces sharper results than bicubic
     * for large scale factors. Pure Java implementation.
     */
    protected BufferedImage lanczosResize(BufferedImage src, int dstW, int dstH) {
        int srcW = src.getWidth();
        int srcH = src.getHeight();
        int a = 3; // Lanczos-3 kernel radius

        // Horizontal pass → intermediate buffer
        BufferedImage hPass = new BufferedImage(dstW, srcH, BufferedImage.TYPE_INT_RGB);
        double xRatio = (double) srcW / dstW;
        for (int y = 0; y < srcH; y++) {
            for (int x = 0; x < dstW; x++) {
                double center = (x + 0.5) * xRatio - 0.5;
                int start = (int) Math.floor(center) - a + 1;
                int end = (int) Math.floor(center) + a;
                double r = 0, g = 0, b = 0, wSum = 0;
                for (int i = start; i <= end; i++) {
                    int clamped = Math.min(Math.max(i, 0), srcW - 1);
                    double w = lanczosWeight(center - i, a);
                    int rgb = src.getRGB(clamped, y);
                    r += ((rgb >> 16) & 0xFF) * w;
                    g += ((rgb >> 8) & 0xFF) * w;
                    b += (rgb & 0xFF) * w;
                    wSum += w;
                }
                if (wSum != 0) { r /= wSum; g /= wSum; b /= wSum; }
                hPass.setRGB(x, y, (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b));
            }
        }

        // Vertical pass
        BufferedImage dst = new BufferedImage(dstW, dstH, BufferedImage.TYPE_INT_RGB);
        double yRatio = (double) srcH / dstH;
        for (int x = 0; x < dstW; x++) {
            for (int y = 0; y < dstH; y++) {
                double center = (y + 0.5) * yRatio - 0.5;
                int start = (int) Math.floor(center) - a + 1;
                int end = (int) Math.floor(center) + a;
                double r = 0, g = 0, b = 0, wSum = 0;
                for (int i = start; i <= end; i++) {
                    int clamped = Math.min(Math.max(i, 0), srcH - 1);
                    double w = lanczosWeight(center - i, a);
                    int rgb = hPass.getRGB(x, clamped);
                    r += ((rgb >> 16) & 0xFF) * w;
                    g += ((rgb >> 8) & 0xFF) * w;
                    b += (rgb & 0xFF) * w;
                    wSum += w;
                }
                if (wSum != 0) { r /= wSum; g /= wSum; b /= wSum; }
                dst.setRGB(x, y, (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b));
            }
        }
        return dst;
    }

    /**
     * Compute the Lanczos kernel weight for a given distance x and kernel radius a.
     * Lanczos-3 uses a=3; Lanczos-2 uses a=2.
     * <p>
     * weight(x) = sin(pi*x)/pi * sin(pi*x/a) / (pi*x/a)  for |x| < a, 0 otherwise.
     * </p>
     *
     * @param x the distance from the sample point (may be fractional)
     * @param a the Lanczos kernel radius (typically 2 or 3)
     * @return the interpolation weight
     */
    protected static double lanczosWeight(double x, int a) {
        if (x == 0) { return 1.0; }
        if (Math.abs(x) >= a) { return 0.0; }
        double pix = Math.PI * x;
        return (a * Math.sin(pix) * Math.sin(pix / a)) / (pix * pix);
    }

    /**
     * Clamp a double value to the [0, 255] range and round to the nearest integer.
     * Used for converting floating-point pixel values to 8-bit RGB components.
     *
     * @param v the input value
     * @return an integer in [0, 255]
     */
    protected static int clamp8(double v) {
        return Math.min(255, Math.max(0, (int) Math.round(v)));
    }
}
