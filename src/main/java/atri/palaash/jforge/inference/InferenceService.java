package atri.palaash.jforge.inference;

import java.util.concurrent.CompletableFuture;

/**
 * Service interface for running AI inference models.
 * <p>
 * Think of this as a vending machine: you put in an {@link InferenceRequest}
 * (your order), and some time later you get back an {@link InferenceResult}
 * (your product).  Different implementations can use different backends
 * (ONNX Runtime, PyTorch, etc.) as long as they honour this contract.
 */
public interface InferenceService {

    /**
     * Executes an inference job asynchronously and returns a future that
     * will complete with the result.
     * <p>
     * The caller may poll, block on, or attach callbacks to the returned
     * {@link CompletableFuture}.  The future completes exceptionally if
     * a fatal error occurs during inference.
     *
     * @param request the fully specified inference job (model, prompt,
     *                dimensions, etc.)
     * @return a {@link CompletableFuture} that resolves to the
     *         {@link InferenceResult} when inference finishes
     * @throws NullPointerException if {@code request} is null
     */
    CompletableFuture<InferenceResult> run(InferenceRequest request);
}
