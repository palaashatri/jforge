package atri.palaash.jforge.api;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * A single produced artifact of a generation (an image, video, etc.).
 * Artifacts are references on disk; pixel bytes flow through a
 * file-oriented pipeline path so results are inspectable and portable.
 */
public record GeneratedImage(
        /** Absolute path to the artifact file. */
        Path path,
        /** Width in pixels (0 if unknown). */
        int width,
        /** Height in pixels (0 if unknown). */
        int height,
        /** Image file format extension ("png", "jpg"). */
        String format
) {
    public static final GeneratedImage NONE =
            new GeneratedImage(Path.of(""), 0, 0, "");

    public GeneratedImage {
        Objects.requireNonNull(path, "path");
        if (format == null) {
            format = "";
        }
    }

    /** Whether this is the empty sentinel. */
    public boolean isNone() {
        return this == NONE;
    }
}