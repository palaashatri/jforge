package atri.palaash.jforge.engine;

/**
 * A loaded, ready-to-execute pipeline instance. The exact type is
 * implementation-specific; the pipeline interface accepts it back on
 * {@code generate}. MUST be closed when no longer needed to release
 * native sessions / GPU memory.
 */
public interface LoadedPipeline extends AutoCloseable {

    /**
     * The descriptor of the pipeline that produced this instance.
     *
     * @return pipeline descriptor
     */
    PipelineDescriptor descriptor();

    @Override
    void close();
}