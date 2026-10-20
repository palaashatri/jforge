package atri.palaash.jforge.api;

/**
 * Miscellaneous per-job options for a {@link GenerationRequest}.
 */
public record GenerationOptions(
        /** Whether to decode low-cost intermediate previews during denoising. */
        boolean intermediatePreviews,
        /**
         * Preview cadence: produce a preview every N steps (when
         * {@code intermediatePreviews} is true). Zero = every step.
         */
        int previewEveryNSteps,
        /** Optional output prefix used for artifact filenames. */
        String outputPrefix
) {
    public static final GenerationOptions DEFAULT =
            new GenerationOptions(false, 0, "");

    public GenerationOptions {
        if (previewEveryNSteps < 0) {
            throw new IllegalArgumentException("previewEveryNSteps must be >= 0");
        }
        if (outputPrefix == null) {
            outputPrefix = "";
        }
    }
}