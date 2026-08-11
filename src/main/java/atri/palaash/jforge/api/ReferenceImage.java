package atri.palaash.jforge.api;

import java.util.Objects;

/**
 * A reference image for stylization (IP-Adapter style or simple concat
 * reference conditioning) when the pipeline supports it.
 */
public record ReferenceImage(
        /** Absolute path to the reference image. */
        ImageInput image,
        /** Conditioning strength in [0,1]. */
        double strength
) {
    public ReferenceImage {
        Objects.requireNonNull(image, "image");
    }

    public ReferenceImage(ImageInput image) {
        this(image, 1.0);
    }
}