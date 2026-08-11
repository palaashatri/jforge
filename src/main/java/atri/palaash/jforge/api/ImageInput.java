package atri.palaash.jforge.api;

import java.nio.file.Path;
import java.util.Objects;

/**
 * An image supplied to the pipeline as conditioning input (img2img source,
 * inpainting base, reference style, control preprocessor input, etc.).
 */
public record ImageInput(
        /** Absolute path to the image file. */
        Path path,
        /**
         * Suggested target resolution to preserve aspect ratio. When zero
         * the pipeline falls back to the request dimensions.
         */
        int width,
        int height
) {
    public ImageInput {
        Objects.requireNonNull(path, "path");
    }

    public ImageInput(Path path) {
        this(path, 0, 0);
    }
}