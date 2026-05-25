package atri.palaash.jforge.model;

/**
 * Categories of generative-AI tasks that jForge can perform.
 * <p>
 * Think of this as a menu of capabilities: you pick a {@link TaskType},
 * and jForge knows which models and pipeline steps are relevant.
 * Currently supports text-to-image generation and image upscaling.
 */
public enum TaskType {
    /** Generate a new image from a text description (e.g. "a cat wearing a hat"). */
    TEXT_TO_IMAGE("Text → Image"),
    /** Increase the resolution of an existing image while preserving quality. */
    IMAGE_UPSCALE("Image Upscale");

    /** A human-friendly label shown in the UI. */
    private final String displayName;

    /**
     * Creates a task type with a readable display name.
     *
     * @param displayName the label to show in the user interface
     */
    TaskType(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the human-readable label for this task type.
     *
     * @return display name (e.g. "Text → Image")
     */
    public String displayName() {
        return displayName;
    }
}
