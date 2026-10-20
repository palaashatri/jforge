package atri.palaash.jforge.perf;

import atri.palaash.jforge.api.GenerationRequest;
import atri.palaash.jforge.api.GenerationResult;
import atri.palaash.jforge.api.JForge;

import java.util.ArrayList;
import java.util.List;

public final class BenchmarkHarness {
    public record RunConfig(String modelId, int width, int height, int steps, int repeats) {}

    public static List<BenchmarkResult> benchmark(JForge forge, RunConfig cfg) {
        List<BenchmarkResult> results = new ArrayList<>();
        for (int i = 0; i < cfg.repeats(); i++) {
            GenerationRequest req = GenerationRequest.builder()
                    .model(cfg.modelId()).prompt("benchmark test").width(cfg.width()).height(cfg.height()).steps(cfg.steps()).build();
            long start = System.nanoTime();
            GenerationResult r = forge.generate(req);
            long elapsed = (System.nanoTime() - start) / 1_000_000;
            long peak = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            String backend = r.backend().isBlank() ? "unknown" : r.backend();
            String device = r.device().isBlank() ? "cpu" : r.device();
            results.add(BenchmarkResult.of(cfg.modelId(), backend, device, elapsed, cfg.steps(), peak));
        }
        return results;
    }

    public static BenchmarkResult average(List<BenchmarkResult> runs) {
        if (runs.isEmpty()) throw new IllegalArgumentException("no runs");
        long total = runs.stream().mapToLong(BenchmarkResult::totalMillis).sum();
        long peak = runs.stream().mapToLong(BenchmarkResult::peakMemoryBytes).max().orElse(0);
        int steps = runs.get(0).steps();
        String model = runs.get(0).modelId();
        String backend = runs.get(0).backend();
        String device = runs.get(0).device();
        return BenchmarkResult.of(model, backend, device, total / runs.size(), steps, peak);
    }
}
