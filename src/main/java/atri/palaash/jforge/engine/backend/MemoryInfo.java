package atri.palaash.jforge.engine.backend;

/**
 * Memory usage snapshot from a compute backend (device RAM/VRAM or
 * unified memory). Negative values mean "unknown".
 */
public record MemoryInfo(
        long totalBytes,
        long availableBytes,
        long peakBytes,
        long reservedByEngineBytes
) {

    public static final MemoryInfo UNKNOWN = new MemoryInfo(-1, -1, -1, -1);

    public long usedBytes() {
        if (totalBytes <= 0 || availableBytes < 0) {
            return -1;
        }
        return Math.max(0, totalBytes - availableBytes);
    }

    public long usedMb() {
        long used = usedBytes();
        return used > 0 ? used / (1024 * 1024) : 0;
    }

    public long totalMb() {
        return totalBytes > 0 ? totalBytes / (1024 * 1024) : 0;
    }
}