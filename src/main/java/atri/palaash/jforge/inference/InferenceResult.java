package atri.palaash.jforge.inference;

/**
 * The outcome of a single inference run.
 * <p>
 * Think of this as a receipt you get back from the kitchen: it tells
 * you whether the order succeeded, what the result was (a generated
 * image path, a text output, or an error message), and where any
 * artifact file ended up on disk.
 */
public record InferenceResult(
        /** {@code true} if inference completed without errors. */
        boolean success,
        /**
         * The primary output — typically the file path of the generated
         * image, or an empty string on failure.
         */
        String output,
        /**
         * Human-readable details about the result.  On success this might
         * say "Generated 4 images"; on failure it contains the error
         * description.
         */
        String details,
        /**
         * Full path to the generated artifact file on disk (image, video,
         * etc.).  Empty string if no artifact was produced.
         */
        String artifactPath,
        /**
         * MIME-like type label for the artifact (e.g. "image/png",
         * "image/jpeg", or "none" on failure).
         */
        String artifactType
) {

    /**
     * Creates a success result with the given output and artifact info.
     *
     * @param output       the primary result (e.g. file path or generated text)
     * @param details      human-readable description of what happened
     * @param artifactPath path to the generated file on disk
     * @param artifactType type label for the artifact (e.g. "image/png")
     * @return a new {@code InferenceResult} with {@code success = true}
     */
    public static InferenceResult ok(String output, String details, String artifactPath, String artifactType) {
        return new InferenceResult(true, output, details, artifactPath, artifactType);
    }

    /**
     * Creates a failure result carrying only an error description.
     *
     * @param details human-readable error description
     * @return a new {@code InferenceResult} with {@code success = false}
     */
    public static InferenceResult fail(String details) {
        return new InferenceResult(false, "", details, "", "none");
    }
}
