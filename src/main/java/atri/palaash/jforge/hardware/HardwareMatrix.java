package atri.palaash.jforge.hardware;

import java.util.List;
import java.util.Map;

public final class HardwareMatrix {
    public record Entry(String gpu, String backend, String status, String notes) {}
    private static final List<Entry> MATRIX = List.of(
            new Entry("NVIDIA RTX 4090", "CUDA/TensorRT", "verified", "CI + local"),
            new Entry("NVIDIA RTX 3080", "CUDA", "verified", "local"),
            new Entry("AMD RDNA3", "ROCm/DirectML", "unverified", "ROCm via ONNX Runtime; no physical hardware"),
            new Entry("Intel Arc A770", "OpenVINO", "unverified", "OpenVINO via ONNX Runtime"),
            new Entry("Apple M2", "CoreML", "verified", "macOS CI"),
            new Entry("Apple M1", "CoreML", "unverified", "community validation")
    );
    public static List<Entry> all() { return MATRIX; }
    public static Map<String, String> summary() {
        java.util.HashMap<String,String> m = new java.util.HashMap<>();
        for (Entry e : MATRIX) m.put(e.gpu(), e.status());
        return Map.copyOf(m);
    }
}
