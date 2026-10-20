package atri.palaash.jforge.engine;

import java.util.Objects;

/**
 * Static identity of a pipeline implementation.
 */
public record PipelineDescriptor(
        /** Stable identifier, e.g. "stable-diffusion-v15". */
        String id,
        /** Human-readable name, e.g. "Stable Diffusion v1.5". */
        String displayName,
        /** Model family, e.g. "stable-diffusion-1.x". */
        String family,
        /** Pipeline API version this implementation targets. */
        int apiVersion
) {
    public PipelineDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}