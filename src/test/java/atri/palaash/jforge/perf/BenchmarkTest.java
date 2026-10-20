package atri.palaash.jforge.perf;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BenchmarkTest {
    @Test void resultCalculatesStepsPerSecond() {
        BenchmarkResult r = BenchmarkResult.of("sd15", "cuda", "cuda:0", 2000, 20, 1024);
        assertEquals(10.0, r.stepsPerSecond(), 1e-9);
        assertEquals(100.0, r.avgStepMillis(), 1e-9);
    }
    @Test void averageAggregates() {
        List<BenchmarkResult> runs = List.of(
                BenchmarkResult.of("m", "cpu", "cpu", 1000, 10, 100),
                BenchmarkResult.of("m", "cpu", "cpu", 2000, 10, 200)
        );
        BenchmarkResult avg = BenchmarkHarness.average(runs);
        assertEquals(1500, avg.totalMillis());
        assertEquals(10, avg.steps());
    }
    @Test void averageRejectsEmpty() {
        assertThrows(IllegalArgumentException.class, () -> BenchmarkHarness.average(List.of()));
    }
}
