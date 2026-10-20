package atri.palaash.jforge.engine;

import atri.palaash.jforge.api.Precision;
import atri.palaash.jforge.api.Quantization;

import java.util.Objects;

/**
 * Immutable load options passed to {@link GenerationPipeline#load}.
 * Empty/absent fields fall back to pipeline defaults.
 */
public record LoadOptions(
        Precision precision,
        Quantization quantization,
        String backendId,
        String deviceId,
        String memoryMode,
        boolean allowCpuOffload
) {
    public static final LoadOptions DEFAULT =
            new LoadOptions(null, null, null, null, "balanced", true);

    public LoadOptions {
        memoryMode = Objects.requireNonNullElse(memoryMode, "balanced");
    }
}