package atri.palaash.jforge.engine;

/**
 * Callback receiving {@link GenerationProgress} updates during generation.
 * Invocations may arrive from a background worker thread; implementations
 * must marshal to their event loop if needed and must never block.
 */
@FunctionalInterface
public interface ProgressListener {
    void onProgress(GenerationProgress progress);

    /** No-op listener. */
    ProgressListener NONE = progress -> {
    };
}