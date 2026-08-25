package atri.palaash.jforge.engine.backend;

/**
 * A single usable compute device (GPU or CPU). Values may be unknown and
 * are reported as negative numbers / empty strings.
 */
public record Device(
        String id,
        String name,
        DeviceKind kind,
        long totalMemoryBytes,
        long availableMemoryBytes,
        String driver
) {

    public static final Device UNKNOWN = new Device("", "unknown", DeviceKind.OTHER, -1, -1, "");

    public Device {
        if (name == null || name.isBlank()) {
            name = "unknown";
        }
        if (kind == null) {
            kind = DeviceKind.OTHER;
        }
        driver = driver == null ? "" : driver;
        if (id == null || id.isBlank()) {
            id = name.toLowerCase().replace(' ', '-');
        }
    }

    /** Memory in MB, or 0 if unknown. */
    public long totalMemoryMb() {
        return totalMemoryBytes > 0 ? totalMemoryBytes / (1024 * 1024) : 0;
    }

    public long availableMemoryMb() {
        return availableMemoryBytes > 0 ? availableMemoryBytes / (1024 * 1024) : 0;
    }
}