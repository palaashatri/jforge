package atri.palaash.jforge.engine.backend;

/**
 * A loaded, backend-owned compute session for one model bundle.
 * MUST be closed to release native GPU/CPU resources.
 */
public interface BackendSession extends AutoCloseable {

    /**
     * Device this session is bound to.
     *
     * @return the active device
     */
    Device device();

    @Override
    void close();
}