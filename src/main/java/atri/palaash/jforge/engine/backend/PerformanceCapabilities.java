package atri.palaash.jforge.engine.backend;

import atri.palaash.jforge.api.Precision;
import atri.palaash.jforge.api.Quantization;

import java.util.Objects;
import java.util.Set;

/**
 * Relative performance characteristics of a backend, used for auto-scheduling
 * and for the model browser's "will it run here?" hints.
 */
public record PerformanceCapabilities(
        /** Precisions validated on this backend. */
        Set<Precision> supportedPrecisions,
        /** Quantizations validated on this backend. */
        Set<Quantization> supportedQuantizations,
        /** Recommended quantization for large models on this device. */
        Quantization recommendedQuantization,
        /** Qualitative speed indicator (values are backend-relative). */
        String speedClass,
        /** Whether this backend supports multi-device. */
        boolean multiDevice
) {
    public static final PerformanceCapabilities UNKNOWN =
            new PerformanceCapabilities(Set.of(Precision.FP32), Set.of(Quantization.NONE),
                    Quantization.NONE, "unknown", false);

    public PerformanceCapabilities {
        supportedPrecisions = supportedPrecisions == null
                ? Set.of() : Set.copyOf(supportedPrecisions);
        supportedQuantizations = supportedQuantizations == null
                ? Set.of() : Set.copyOf(supportedQuantizations);
        recommendedQuantization = Objects.requireNonNullElse(recommendedQuantization, Quantization.NONE);
        speedClass = Objects.requireNonNullElse(speedClass, "unknown");
    }
}