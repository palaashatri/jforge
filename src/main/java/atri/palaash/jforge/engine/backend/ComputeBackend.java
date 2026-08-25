package atri.palaash.jforge.engine.backend;

import atri.palaash.jforge.engine.ModelBundle;

import java.util.List;

/**
 * Backend SPI: an execution substrate (ONNX Runtime, native CUDA, Metal,
 * OpenVINO, ...). Backends own device enumeration, session creation, and
 * memory introspection.
 */
public interface ComputeBackend {

    /**
     * Static backend identity.
     *
     * @return backend descriptor
     */
    BackendDescriptor descriptor();

    /**
     * Whether this backend can run the given model on the given device.
     *
     * @param device the candidate device
     * @param model  the model bundle
     * @return true if supported
     */
    boolean supports(Device device, ModelBundle model);

    /**
     * Load a compute session for the model bundle, bound to the chosen
     * device.
     *
     * @param device the device to run on
     * @param model  the model bundle
     * @return a loaded session
     */
    BackendSession load(Device device, ModelBundle model);

    /**
     * Enumerate all usable devices for this backend.
     *
     * @return list of devices (possibly empty)
     */
    List<Device> enumerateDevices();

    /**
     * Current memory information for the best/default device.
     *
     * @return memory info
     */
    MemoryInfo memoryInfo();

    /**
     * Performance characteristics for scheduling.
     *
     * @return performance capabilities
     */
    PerformanceCapabilities capabilities();
}