package atri.palaash.jforge.engine.legacy;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.inference.InferenceRequest;
import atri.palaash.jforge.model.ModelDescriptor;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Pure mapping between the typed {@link GenerationRequest} and the legacy
 * {@link InferenceRequest} record, fixing the historical batch/steps
 * conflation.
 *
 * <p>Legacy pipelines interpret {@code InferenceRequest.batch()} as the
 * <em>step count</em> (e.g. {@code int steps = clamp(request.batch())} in
 * every pipeline). The typed request keeps {@link GenerationRequest#steps()}
 * and {@link GenerationRequest#batchSize()} separate; this mapper forwards
 * {@code steps} into the legacy {@code batch} slot so the legacy engine
 * continues to produce the correct number of denoising steps.
 *
 * <p>Real batched inference is not yet available on the legacy engine (each
 * {@code run()} produces a single image), so {@code batchSize > 1} is
 * realised as sequential runs with derived seeds — see
 * {@link #seedFor(GenerationRequest, int)}.
 */
public final class RequestMapper {

    private RequestMapper() {
    }

    /**
     * Build a legacy {@link InferenceRequest} from a typed request.
     *
     * @param request    typed generation request
     * @param model      legacy model descriptor resolved from the bundle
     * @param progress   legacy progress callback (may be null)
     * @param cancel     legacy cancellation flag (may be null)
     * @return a fully-populated legacy request
     */
    public static InferenceRequest toInferenceRequest(
            GenerationRequest request,
            ModelDescriptor model,
            Consumer<String> progress,
            AtomicBoolean cancel) {
        String inputImage = request.inputImage()
                .map(image -> image.path().toString())
                .orElse(request.mask().map(mask -> mask.path().toString()).orElse(""));

        boolean preferGpu = request.backendId()
                .map(backend -> backend.toLowerCase().contains("cuda")
                        || backend.toLowerCase().contains("gpu")
                        || backend.toLowerCase().contains("rocm"))
                .orElse(false);

        return new InferenceRequest(
                model,
                request.prompt(),
                request.negativePrompt(),
                request.cfgScale(),
                request.seed(),
                request.steps(),          // legacy "batch" slot carries the step count
                request.width(),
                request.height(),
                "",
                false,
                inputImage,
                preferGpu,
                progress,
                cancel);
    }

    /**
     * Deterministic per-item seed for a batched request. The base seed maps
     * to the first output; item {@code i} uses {@code seed + i}. Same inputs
     * always produce the same seed sequence.
     *
     * @param request the typed request
     * @param index   batch item index in {@code [0, batchSize)}
     * @return the seed for that item
     */
    public static long seedFor(GenerationRequest request, int index) {
        return request.seed() + index;
    }
}
