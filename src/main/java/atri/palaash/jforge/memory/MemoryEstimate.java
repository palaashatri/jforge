package atri.palaash.jforge.memory;

public record MemoryEstimate(long estimatedBytes, long peakBytes, String breakdown) {
    public static MemoryEstimate of(long bytes, String breakdown) {
        return new MemoryEstimate(bytes, (long)(bytes * 1.2), breakdown);
    }
    public boolean fitsIn(long availableBytes) { return estimatedBytes <= availableBytes; }
    public String toHuman() {
        if (estimatedBytes < 1024) return estimatedBytes + " B";
        if (estimatedBytes < 1024*1024) return String.format("%.1f KB", estimatedBytes/1024.0);
        if (estimatedBytes < 1024*1024*1024) return String.format("%.1f MB", estimatedBytes/1024.0/1024);
        return String.format("%.2f GB", estimatedBytes/1024.0/1024/1024);
    }
}
