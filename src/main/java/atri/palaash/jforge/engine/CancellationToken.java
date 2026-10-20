package atri.palaash.jforge.engine;

/**
 * Cancellation contract for long-running operations. Implementations must
 * be thread-safe (a concurrent set operation must be visible to the worker).
 */
public interface CancellationToken {

    /** Non-cancelling token shared by all callers that never cancel. */
    CancellationToken NONE = () -> false;

    /**
     * @return true when cancellation has been requested
     */
    boolean isCancelled();
}