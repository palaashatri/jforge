package atri.palaash.jforge.engine.legacy;

import atri.palaash.jforge.api.Precision;
import atri.palaash.jforge.api.Quantization;
import atri.palaash.jforge.engine.ModelBundle;
import atri.palaash.jforge.engine.backend.BackendDescriptor;
import atri.palaash.jforge.engine.backend.BackendSession;
import atri.palaash.jforge.engine.backend.ComputeBackend;
import atri.palaash.jforge.engine.backend.Device;
import atri.palaash.jforge.engine.backend.DeviceKind;
import atri.palaash.jforge.engine.backend.MemoryInfo;
import atri.palaash.jforge.engine.backend.PerformanceCapabilities;
import atri.palaash.jforge.inference.GenericOnnxService;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * {@link ComputeBackend} over the existing ONNX Runtime integration.
 * <p>
 * Device enumeration is driven by {@link GenericOnnxService#detectedProvider()}
 * so the backend reports what the legacy engine can actually run today:
 * CPU always, CUDA only when the native ONNX Runtime build exposes it.
 */
public final class OnnxRuntimeBackend implements ComputeBackend {

    private final Set<DeviceKind> available;
    private final String provider;

    public OnnxRuntimeBackend() {
        this.provider = GenericOnnxService.detectedProvider();
        Set<DeviceKind> kinds = EnumSet.of(DeviceKind.CPU);
        String lower = provider.toLowerCase();
        if (lower.contains("cuda")) {
            kinds.add(DeviceKind.CUDA);
        }
        if (lower.contains("directml")) {
            kinds.add(DeviceKind.DIRECTML);
        }
        if (lower.contains("openvino")) {
            kinds.add(DeviceKind.OPENVINO);
        }
        this.available = Set.copyOf(kinds);
    }

    @Override
    public BackendDescriptor descriptor() {
        return new BackendDescriptor("ort", "ONNX Runtime", available, 1);
    }

    @Override
    public boolean supports(Device device, ModelBundle model) {
        if (device == null || device.kind() == DeviceKind.OTHER) {
            return true;
        }
        return available.contains(device.kind());
    }

    @Override
    public BackendSession load(Device device, ModelBundle model) {
        return new BackendSession() {
            @Override
            public Device device() {
                return device == null ? Device.UNKNOWN : device;
            }

            @Override
            public void close() {
            }
        };
    }

    @Override
    public List<Device> enumerateDevices() {
        List<Device> devices = new ArrayList<>();
        devices.add(new Device("cpu", "CPU", DeviceKind.CPU,
                Runtime.getRuntime().maxMemory(), Runtime.getRuntime().freeMemory(), ""));
        if (available.contains(DeviceKind.CUDA)) {
            devices.add(new Device("cuda:0", "CUDA GPU (" + provider + ")",
                    DeviceKind.CUDA, -1, -1, ""));
        }
        return devices;
    }

    @Override
    public MemoryInfo memoryInfo() {
        return MemoryInfo.UNKNOWN;
    }

    @Override
    public PerformanceCapabilities capabilities() {
        return new PerformanceCapabilities(
                Set.of(Precision.FP32),
                Set.of(Quantization.NONE),
                Quantization.NONE,
                "cpu",
                false);
    }
}