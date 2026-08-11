package atri.palaash.jforge.api;

import java.util.Objects;

/**
 * Configuration for a single LoRA applied during generation.
 * <p>
 * Multiple LoRAs may be stacked; order in the request list matters for
 * family-compatible pipelines that support drag-reorder semantics.
 */
public record LoRAConfig(
        /** Model identifier of the LoRA (must be a registered/installable LoRA). */
        String modelId,
        /** Blend strength, typically in [0.0, 2.0], 1.0 being the trained scale. */
        double strength,
        /** When false the LoRA is listed but not applied. */
        boolean enabled
) {
    public LoRAConfig {
        Objects.requireNonNull(modelId, "modelId");
    }

    public LoRAConfig(String modelId, double strength) {
        this(modelId, strength, true);
    }
}