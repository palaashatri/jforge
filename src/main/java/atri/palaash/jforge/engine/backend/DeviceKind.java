package atri.palaash.jforge.engine.backend;

/**
 * Kind of compute device / accelerator.
 */
public enum DeviceKind {
    CPU,
    CUDA,
    ROCM,
    COREML,
    DIRECTML,
    OPENVINO,
    TENSORRT,
    METAL,
    OTHER
}