package atri.palaash.jforge.api;

import java.util.Optional;

/**
 * Weight quantization strategy for a model bundle.
 * <p>
 * Not every quantization is supported by every backend/model; a bundle
 * declares the quantization options that are actually executable.
 */
public enum Quantization {
    /** No weight quantization (store/run at native precision). */
    NONE("None"),
    /** 8-bit weight quantization. */
    Q8("Q8"),
    /** 6-bit weight quantization. */
    Q6("Q6"),
    /** 5-bit weight quantization. */
    Q5("Q5"),
    /** 4-bit weight quantization. */
    Q4("Q4");

    private final String displayName;

    Quantization(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * Parse from a case-insensitive string (e.g. "q4", "q8", "none").
     *
     * @return the matching quantization, or {@link Optional#empty()} when unknown
     */
    public static Optional<Quantization> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        for (Quantization q : values()) {
            if (q.displayName().equalsIgnoreCase(value) || q.name().equalsIgnoreCase(value.trim())) {
                return Optional.of(q);
            }
        }
        return Optional.empty();
    }
}