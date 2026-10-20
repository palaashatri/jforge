package atri.palaash.jforge.perf;

public record BenchmarkResult(String modelId, String backend, String device,
                              long totalMillis, int steps, double stepsPerSecond,
                              double avgStepMillis, long peakMemoryBytes) {
    public static BenchmarkResult of(String modelId, String backend, String device, long totalMillis, int steps, long peakMem) {
        double sps = totalMillis > 0 ? steps * 1000.0 / totalMillis : 0;
        double avg = steps > 0 ? (double) totalMillis / steps : 0;
        return new BenchmarkResult(modelId, backend, device, totalMillis, steps, sps, avg, peakMem);
    }
}
