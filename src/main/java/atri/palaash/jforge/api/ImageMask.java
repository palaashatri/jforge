package atri.palaash.jforge.api;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A binary mask used for inpainting / outpainting.
 * <p>
 * The mask image must be a single-channel-like PNG/JPG where opaque (or
 * white) pixels mark the region to regenerate and transparent (or black)
 * pixels mark the region to preserve.  Interpretation depends on the
 * {@code invert} flag.
 */
public record ImageMask(
        /** Absolute path to the mask image file. */
        Path path,
        /** When true the mask semantics are inverted before use. */
        boolean invert
) {
    public ImageMask {
        Objects.requireNonNull(path, "path");
    }

    public ImageMask(Path path) {
        this(path, false);
    }
}