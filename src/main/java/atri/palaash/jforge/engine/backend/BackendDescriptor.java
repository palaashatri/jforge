package atri.palaash.jforge.engine.backend;

import java.util.Objects;
import java.util.Set;

/**
 * Static identity of a {@link ComputeBackend} implementation.
 */
public record BackendDescriptor(
        /** Stable identifier, e.g. "ort" or "ort-cuda". */
        String id,
        /** Human-readable name, e.g. "ONNX Runtime". */
        String displayName,
        /** Device kinds this backend can drive. */
        Set<DeviceKind> supportedDeviceKinds,
        /** Backend API version. */
        int apiVersion
) {
    public BackendDescriptor {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
        supportedDeviceKinds = supportedDeviceKinds == null
                ? Set.of() : Set.copyOf(supportedDeviceKinds);
    }

    public boolean supports(DeviceKind kind) {
        return supportedDeviceKinds.contains(kind);
    }
}