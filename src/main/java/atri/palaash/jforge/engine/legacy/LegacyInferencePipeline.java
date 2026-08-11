package atri.palaash.jforge.engine.legacy;

import atri.palaash.jforge.api.GeneratedImage;
import atri.palaash.jforge.api.GenerationManifest;
import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.engine.CancellationToken;
import atri.palaash.jforge.engine.Capability;
import atri.palaash.jforge.engine.GenerationPipeline;
import atri.palaash.jforge.engine.GenerationProgress;
import atri.palaash.jforge.engine.LoadOptions;
import atri.palaash.jforge.engine.LoadedPipeline;
import atri.palaash.jforge.engine.ModelBundle;
import atri.palaash.jforge.engine.PipelineCapabilities;
import atri.palaash.jforge.engine.PipelineDescriptor;
import atri.palaash.jforge.engine.ProgressListener;
import atri.palaash.jforge.engine.backend.ComputeBackend;
import atri.palaash.jforge.inference.InferenceRequest;
import atri.palaash.jforge.inference.InferenceResult;
import atri.palaash.jforge.inference.InferenceService;
import atri.palaash.jforge.model.ModelDescriptor;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A {@link GenerationPipeline} that drives the existing legacy ONNX Runtime
 * engine via {@link InferenceService}. This is the bridge that makes the new
 * typed engine API executable today, without a risky one-shot rewrite of the
 * 2800-line legacy service.
 *
 * <p>Honest limitations (documented in TRUTH.md): legacy pipelines select the
 * scheduler internally by model id, so {@link GenerationRequest#scheduler()}
 * is advisory here; and {@code batchSize > 1} is executed as sequential runs
 * with derived seeds ({@link RequestMapper#seedFor}) until real batched
 * inference lands.
 */
public final class LegacyInferencePipeline implements GenerationPipeline {

    private final InferenceService delegate;
    private final ModelBundleResolver modelResolver;

    /**
     * @param delegate       the legacy inference service to drive
     * @param modelResolver  resolves a typed {@link ModelBundle} to the legacy
     *                       {@link ModelDescriptor} needed by the old engine
     */
    public LegacyInferencePipeline(InferenceService delegate, ModelBundleResolver modelResolver) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.modelResolver = Objects.requireNonNull(modelResolver, "modelResolver");
    }

    @Override
    public PipelineDescriptor descriptor() {
        return new PipelineDescriptor(
                "legacy-ort", "Legacy ONNX Runtime pipeline", "onnx-runtime", 1);
    }

    @Override
    public PipelineCapabilities capabilities() {
        return PipelineCapabilities.builder()
                .capabilities(Capability.TEXT_TO_IMAGE, Capability.NEGATIVE_PROMPT, Capability.CFG)
                .build();
    }

    @Override
    public LoadedPipeline load(ModelBundle model, ComputeBackend backend, LoadOptions options)
            throws Exception {
        if (model == null) {
            throw new IllegalArgumentException("model must not be null");
        }
        ModelDescriptor descriptor = modelResolver.resolve(model);
        if (descriptor == null) {
            throw new IllegalArgumentException("no legacy ModelDescriptor registered for bundle: " + model.id());
        }
        return new LegacyLoaded(descriptor(), descriptor);
    }

    @Override
    public GenerationResult generate(LoadedPipeline pipeline,
                                     GenerationRequest request,
                                     ProgressListener progress,
                                     CancellationToken cancellation) {
        Objects.requireNonNull(pipeline, "pipeline");
        Objects.requireNonNull(request, "request");
        ProgressListener listener = progress == null ? ProgressListener.NONE : progress;
        CancellationToken token = cancellation == null ? CancellationToken.NONE : cancellation;

        LegacyLoaded loaded = (LegacyLoaded) pipeline;
        long start = System.currentTimeMillis();
        List<GeneratedImage> images = new ArrayList<>();

        // Resolve the random-seed marker once at the generation boundary so
        // isRandomSeed() is truthful, the batch derives from one base seed,
        // and the resolved seed is recorded in the manifest.
        GenerationRequest effective = request.isRandomSeed()
                ? request.toBuilder().seed(
                        java.util.concurrent.ThreadLocalRandom.current().nextLong()).build()
                : request;

        for (int i = 0; i < effective.batchSize(); i++) {
            if (token.isCancelled()) {
                return GenerationResult.fail(UUID.randomUUID().toString(),
                        "Cancelled by user after " + images.size() + " image(s).");
            }
            GenerationRequest item = effective.batchSize() == 1
                    ? effective
                    : effective.toBuilder().seed(RequestMapper.seedFor(effective, i)).build();
            listener.onProgress(GenerationProgress.EMPTY);

            AtomicBoolean cancelFlag = new AtomicBoolean();
            java.util.function.Consumer<String> legacyProgress =
                    msg -> {
                        if (token.isCancelled()) {
                            cancelFlag.set(true);
                        }
                        reportProgress(listener, msg, i + 1, effective.batchSize());
                    };
            InferenceRequest legacy = RequestMapper.toInferenceRequest(
                    item, loaded.model(), legacyProgress, cancelFlag);
            legacy.reportProgress("Starting generation " + (i + 1) + "/" + effective.batchSize());

            if (token.isCancelled()) {
                return GenerationResult.fail(UUID.randomUUID().toString(),
                        "Cancelled by user after " + images.size() + " image(s).");
            }

            try {
                InferenceResult result = delegate.run(legacy).get();
                if (!result.success() || result.artifactPath().isBlank()) {
                    String error = result.details().isBlank() ? "Legacy engine produced no output." : result.details();
                    return GenerationResult.fail(item.modelId(), error);
                }
                images.add(new GeneratedImage(Path.of(result.artifactPath()), effective.width(), effective.height(), "png"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return GenerationResult.fail(item.modelId(), "Generation interrupted.");
            } catch (Exception e) {
                return GenerationResult.fail(item.modelId(),
                        "Generation failed: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        GenerationManifest manifest = GenerationManifest.fromRequest(effective,
                new GenerationManifest.RuntimeFacts(
                        "legacy-ort", "", "", "", "", "", elapsed + "ms",
                        System.getProperty("java.version", "")));
        return GenerationResult.ok(UUID.randomUUID().toString(), images, manifest,
                elapsed, "", "onnx-runtime");
    }

    private static void reportProgress(ProgressListener listener, String msg, int item, int total) {
        listener.onProgress(new GenerationProgress(
                msg == null ? "" : msg, item, total, -1, -1, -1, "", "onnx-runtime", -1, false));
    }

    /**
     * Resolves a typed model bundle to the legacy model descriptor.
     */
    @FunctionalInterface
    public interface ModelBundleResolver {
        ModelDescriptor resolve(ModelBundle bundle);
    }

    private record LegacyLoaded(PipelineDescriptor pipelineDescriptor, ModelDescriptor model)
            implements LoadedPipeline {
        @Override
        public PipelineDescriptor descriptor() {
            return pipelineDescriptor;
        }

        @Override
        public void close() {
            // Legacy engine owns its sessions via GenericOnnxService cache.
        }
    }
}
