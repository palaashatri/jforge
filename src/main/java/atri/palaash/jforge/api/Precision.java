package atri.palaash.jforge.api;

import java.util.Optional;
import java.util.OptionalInt;

/**
 * Supported numeric precisions for model weights / computation.
 * <p>
 * Whether a given precision is actually executable depends on the
 * backend and the model; the backend reports its supported set.
 */
public enum Precision {
    FP32("FP32"),
    FP16("FP16"),
    BF16("BF16"),
    INT8("INT8"),
    INT4("INT4");

    private final String displayName;

    Precision(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Parse from a case-insensitive string (e.g. "fp16", "fp32").
     *
     * @return the matching precision, or {@link Optional#empty()} when unknown
     */
    public static Optional<Precision> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (Precision p : values()) {
            if (p.displayName().equalsIgnoreCase(value) || p.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(p);
            }
        }
        return Optional.empty();
    }
}