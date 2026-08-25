package atri.palaash.jforge.engine;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.engine.backend.ComputeBackend;

/**
 * Core engine SPI. A pipeline is a self-contained implementation of one
 * generation strategy (SD 1.5, SDXL, SD 3.x, FLUX, upscale, video, ...).
 *
 * <p>Lifecycle:
 * <ol>
 *   <li>{@link #descriptor()} / {@link #capabilities()} — static identity
 *       and capability metadata, safe to call at any time.</li>
 *   <li>{@link #load(ModelBundle, ComputeBackend, LoadOptions)} — obtain a
 *       {@link LoadedPipeline}; heavy model loading happens here.</li>
 *   <li>{@link #generate(LoadedPipeline, GenerationRequest, ProgressListener,
 *       CancellationToken)} — run one or more jobs against a loaded
 *       pipeline. A loaded pipeline may be reused across calls.</li>
 *   <li>Callers close the {@link LoadedPipeline} to release native
 *       resources.</li>
 * </ol>
 */
public interface GenerationPipeline {

    /**
     * Static pipeline identity.
     *
     * @return pipeline descriptor
     */
    PipelineDescriptor descriptor();

    /**
     * Declared capabilities of this pipeline. The UI is generated from
     * this metadata.
     *
     * @return pipeline capabilities
     */
    PipelineCapabilities capabilities();

    /**
     * Load the model bundle on the given backend. Implementations may
     * lazily defer work, but must return a usable pipeline.
     *
     * @param model   the model bundle to load
     * @param backend the compute backend to run on
     * @param options load options (precision, quantization, device)
     * @return a loaded pipeline instance
     * @throws Exception if loading fails (missing files, unsupported
     *                   model/backend combination, OOM, etc.)
     */
    LoadedPipeline load(ModelBundle model, ComputeBackend backend, LoadOptions options)
            throws Exception;

    /**
     * Execute one generation request.
     *
     * @param pipeline    a pipeline obtained from {@link #load}
     * @param request     fully typed generation request
     * @param progress    progress listener (may be {@link ProgressListener#NONE})
     * @param cancellation cancellation token (may be {@link CancellationToken#NONE})
     * @return the generation result; failures are returned as failed results
     *         with actionable messages, not thrown
     */
    GenerationResult generate(LoadedPipeline pipeline,
                              GenerationRequest request,
                              ProgressListener progress,
                              CancellationToken cancellation);
}