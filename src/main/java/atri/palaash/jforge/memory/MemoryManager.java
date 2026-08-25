package atri.palaash.jforge.memory;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.engine.ModelBundle;

import java.util.LinkedHashMap;
import java.util.Map;

public final class MemoryManager {
    public enum MemoryMode { LOW, BALANCED, PERFORMANCE }

    private final long availableBytes;
    private MemoryMode mode = MemoryMode.BALANCED;
    private final Map<String, Long> loadedComponents = new LinkedHashMap<>();

    public MemoryManager(long availableBytes) { this.availableBytes = availableBytes; }

    public MemoryMode mode() { return mode; }
    public void setMode(MemoryMode m) { this.mode = m; }

    public MemoryEstimate estimate(ModelBundle bundle, GenerationRequest req) {
        long base = parseMemory(bundle.minimumMemory());
        long latent = (long) req.width() * req.height() * 4 * 4;
        long batch = latent * req.batchSize();
        long steps = batch * req.steps() / 20;
        long total = base + batch + steps;
        if (mode == MemoryMode.LOW) total = (long)(total * 0.7);
        if (mode == MemoryMode.PERFORMANCE) total = (long)(total * 1.3);
        String breakdown = String.format("base=%s + latents=%d + steps=%d (mode=%s)", bundle.minimumMemory(), batch, steps, mode);
        return MemoryEstimate.of(total, breakdown);
    }

    public boolean canFit(MemoryEstimate est) { return est.fitsIn(availableBytes); }

    public void trackLoaded(String component, long bytes) { loadedComponents.put(component, bytes); }
    public void evict(String component) { loadedComponents.remove(component); }
    public void evictAll() { loadedComponents.clear(); }
    public long usedBytes() { return loadedComponents.values().stream().mapToLong(Long::longValue).sum(); }
    public long freeBytes() { return Math.max(0, availableBytes - usedBytes()); }

    public static long parseMemory(String s) {
        if (s == null || s.isBlank() || s.equals("unknown")) return 2L * 1024 * 1024 * 1024;
        s = s.trim().toUpperCase();
        try {
            if (s.endsWith("GB")) return (long)(Double.parseDouble(s.replace("GB","").trim()) * 1024*1024*1024);
            if (s.endsWith("MB")) return (long)(Double.parseDouble(s.replace("MB","").trim()) * 1024*1024);
        } catch (Exception ignored) {}
        return 2L * 1024 * 1024 * 1024;
    }
}
