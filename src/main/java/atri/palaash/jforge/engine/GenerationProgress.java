package atri.palaash.jforge.engine;

/**
 * Structured progress report for a running generation.
 * <p>
 * Values that are unknown at a given moment are reported as negative
 * numbers or empty strings so the UI can render "…" placeholders.
 */
public record GenerationProgress(
        String phase,
        int step,
        int totalSteps,
        double iterationsPerSecond,
        long elapsedMillis,
        long etaMillis,
        String device,
        String backend,
        long memoryBytes,
        boolean intermediatePreviewReady
) {

    public static final GenerationProgress EMPTY =
            new GenerationProgress("", 0, 0, -1, 0, -1, "", "", -1, false);

    /** Fraction complete in [0,1]; 1.0 when totalSteps is 0 or reached. */
    public double fraction() {
        if (totalSteps <= 0) {
            return 0.0;
        }
        return Math.min(1.0, (double) step / totalSteps);
    }

    public boolean isDone() {
        return totalSteps > 0 && step >= totalSteps;
    }
}