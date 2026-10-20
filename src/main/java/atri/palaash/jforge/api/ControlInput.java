package atri.palaash.jforge.api;

import java.util.Objects;

/**
 * A single conditioning control (ControlNet-style) attached to a generation.
 * <p>
 * The control carries its own source image, a preprocessor hint, and
 * windowing/strength parameters.  Multiple controls are allowed when the
 * pipeline declares {@code CONTROLNET} (or another multi-control capability).
 */
public record ControlInput(
        /** Control type (e.g. "canny", "depth", "pose", "scribble", "segmentation", "tile"). */
        String type,
        /** Absolute path to the conditioning source image. */
        ImageInput source,
        /** Preprocessor name (e.g. "canny-edge", "midas-depth"). Empty = none. */
        String preprocessor,
        /** Overall strength in [0,1]. */
        double strength,
        /** Denoising window start, fraction in [0,1]. */
        double startPercent,
        /** Denoising window end, fraction in [0,1]. */
        double endPercent,
        /** When false the control is listed but not applied. */
        boolean enabled
) {
    public ControlInput {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(source, "source");
    }

    public ControlInput(String type, ImageInput source, String preprocessor, double strength) {
        this(type, source, preprocessor, strength, 0.0, 1.0, true);
    }
}