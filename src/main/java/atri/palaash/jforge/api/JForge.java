package atri.palaash.jforge.api;

import atri.palaash.jforge.engine.CancellationToken;
import atri.palaash.jforge.engine.LoadOptions;
import atri.palaash.jforge.engine.ModelBundle;
import atri.palaash.jforge.engine.ProgressListener;
import atri.palaash.jforge.engine.legacy.LegacyInferencePipeline;
import atri.palaash.jforge.engine.legacy.OnnxRuntimeBackend;
import atri.palaash.jforge.inference.GenericOnnxService;
import atri.palaash.jforge.inference.InferenceService;
import atri.palaash.jforge.model.ModelDescriptor;
import atri.palaash.jforge.model.ModelRegistry;
import atri.palaash.jforge.storage.ModelStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Entry point to the embeddable JForge engine.
 *
 * <pre>{@code
 * try (JForge forge = JForge.create()) {
 *     GenerationResult result = forge.generate(
 *             GenerationRequest.builder()
 *                     .model("sd_v15_onnx")
 *                     .prompt("a cat")
 *                     .steps(20)
 *                     .build());
 *     result.images().get(0).path();
 * }
 * }</pre>
 *
 * <p>This facade wires the legacy ONNX Runtime engine behind the new typed
 * {@link GenerationRequest} API through a {@link LegacyInferencePipeline}.
 * It requires no Swing/Compose classes and no UI.
 */
public final class JForge implements AutoCloseable {

    private final ModelRegistry registry;
    private final ExecutorService executor;
    private final LegacyInferencePipeline pipeline;

    private JForge(ModelRegistry registry, ExecutorService executor,
                   LegacyInferencePipeline pipeline) {
        this.registry = registry;
        this.executor = executor;
        this.pipeline = pipeline;
    }

    /**
     * Create an engine rooted at the default models directory.
     *
     * @return a new engine; caller must close it
     * @throws IOException if the model root cannot be created
     */
    public static JForge create() throws IOException {
        return create(defaultModelRoot());
    }

    /**
     * Create an engine rooted at an explicit models directory.
     *
     * @param modelRoot directory where models are stored (created if missing)
     * @return a new engine; caller must close it
     * @throws IOException if the model root cannot be created
     */
    public static JForge create(Path modelRoot) throws IOException {
        Files.createDirectories(modelRoot);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        ModelRegistry registry = new ModelRegistry();
        ModelStorage storage = new ModelStorage(modelRoot);
        // TaskType is selected per model in the legacy engine; null disables the
        // fallback branch that only matters for interaction with the UI.
        GenericOnnxService delegate = new GenericOnnxService(
                atri.palaash.jforge.model.TaskType.TEXT_TO_IMAGE, storage, executor);
        InferenceService service = delegate;
        LegacyInferencePipeline pipeline = new LegacyInferencePipeline(
                service, bundle -> resolveBundle(registry, bundle));
        return new JForge(registry, executor, pipeline);
    }

    private static ModelDescriptor resolveBundle(ModelRegistry registry, ModelBundle bundle) {
        return registry.allModels().stream()
                .filter(d -> d.id().equals(bundle.id()))
                .findFirst()
                .orElse(null);
    }

    /**
     * All registered model descriptors (id → descriptor).
     *
     * @return the model registry
     */
    public ModelRegistry registry() {
        return registry;
    }

    /**
     * The pipeline driving this facade (advisory; the legacy engine owns
     * session and scheduler selection internally).
     *
     * @return the pipeline
     */
    public LegacyInferencePipeline pipeline() {
        return pipeline;
    }

    /**
     * Execute a generation request on the configured pipeline.
     *
     * @param request the typed request
     * @return the result (success or an actionable failure)
     */
    public GenerationResult generate(GenerationRequest request) {
        return generate(request, ProgressListener.NONE, CancellationToken.NONE);
    }

    /**
     * Execute a generation request with progress and cancellation hooks.
     *
     * @param request      the typed request
     * @param progress     progress listener (never null)
     * @param cancellation cancellation token (never null)
     * @return the result
     */
    public GenerationResult generate(GenerationRequest request,
                                     ProgressListener progress,
                                     CancellationToken cancellation) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(progress, "progress");
        Objects.requireNonNull(cancellation, "cancellation");
        ModelBundle bundle = bundleFor(request);
        try (var loaded = pipeline.load(bundle, new OnnxRuntimeBackend(), LoadOptions.DEFAULT)) {
            return pipeline.generate(loaded, request, progress, cancellation);
        } catch (Exception e) {
            return GenerationResult.fail(request.modelId(), messageOf(e));
        }
    }

    private static ModelBundle bundleFor(GenerationRequest request) {
        return ModelBundle.builder()
                .id(request.modelId())
                .architecture("stable-diffusion-1.x")
                .family("legacy")
                .recommendedSteps(request.steps())
                .recommendedCfg(request.cfgScale())
                .build();
    }

    private static String messageOf(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }

    @Override
    public void close() {
        executor.close();
    }

    private static Path defaultModelRoot() {
        String home = System.getProperty("user.home", ".");
        return Path.of(home, ".jforge", "models");
    }
}