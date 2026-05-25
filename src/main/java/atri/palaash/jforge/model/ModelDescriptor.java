package atri.palaash.jforge.model;

/**
 * A named, versioned AI model that jForge can download and run.
 * <p>
 * Think of this as a library card for a model file: it tells jForge
 * what the model is called, where to find it on the internet, what kind
 * of task it performs (e.g. text-to-image, upscaling), and how big the
 * file is on disk.  A single diffusion pipeline may be made of several
 * {@code ModelDescriptor}s (one for the UNet, one for the text encoder,
 * one for the VAE decoder, etc.).
 */
public record ModelDescriptor(
        /** Unique identifier for this model (e.g. "sd_v15_onnx"). Used as a lookup key. */
        String id,
        /** Human-readable name shown in the UI (e.g. "Stable Diffusion v1.5 ONNX"). */
        String displayName,
        /** The kind of inference task this model supports. */
        TaskType taskType,
        /** Path relative to the models directory where the model file is (or will be) stored. */
        String relativePath,
        /** URL from which the model can be downloaded. */
        String sourceUrl,
        /** Free-text notes about the model, often describing auto-download behaviour. */
        String notes,
        /** Size of the model file on disk in bytes (0 if unknown or not yet downloaded). */
        long fileSizeBytes
) {

    /**
     * Convenience constructor that defaults {@code fileSizeBytes} to 0.
     * Useful when the exact file size isn't known ahead of time.
     *
     * @param id           unique model identifier
     * @param displayName  human-readable name
     * @param taskType     type of inference task
     * @param relativePath relative storage path for the model file
     * @param sourceUrl    download URL
     * @param notes        descriptive notes
     */
    public ModelDescriptor(String id, String displayName, TaskType taskType,
                           String relativePath, String sourceUrl, String notes) {
        this(id, displayName, taskType, relativePath, sourceUrl, notes, 0);
    }
}
