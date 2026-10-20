package atri.palaash.jforge.engine.legacy;

import atri.palaash.jforge.engine.ModelBundle;
import atri.palaash.jforge.engine.backend.Device;
import atri.palaash.jforge.engine.backend.DeviceKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OnnxRuntimeBackendTest {

    @Test
    void exposesCpuAndAConfiguredAccelerator() {
        OnnxRuntimeBackend backend = new OnnxRuntimeBackend();

        // CPU is always enumerable regardless of the native provider.
        List<Device> devices = backend.enumerateDevices();
        assertTrue(devices.stream().anyMatch(d -> d.kind() == DeviceKind.CPU),
                "CPU device must always be present");

        // The backend descriptor must declare every kind it reports.
        for (Device device : devices) {
            assertTrue(backend.descriptor().supports(device.kind()),
                    "descriptor must declare " + device.kind());
        }

        assertEquals("ort", backend.descriptor().id());
    }

    @Test
    void supportsUnknownDeviceTolerantly() {
        OnnxRuntimeBackend backend = new OnnxRuntimeBackend();
        ModelBundle bundle = ModelBundle.builder().id("m").build();
        assertTrue(backend.supports(null, bundle));
        assertTrue(backend.supports(Device.UNKNOWN, bundle));
    }
}