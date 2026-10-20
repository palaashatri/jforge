package atri.palaash.jforge.engine;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A simple, thread-safe {@link CancellationToken} backed by an
 * {@link AtomicBoolean}.
 */
public final class CancellationFlag implements CancellationToken {

    private final AtomicBoolean flag = new AtomicBoolean(false);

    /** Requests cancellation. Safe to call from any thread. */
    public void cancel() {
        flag.set(true);
    }

    @Override
    public boolean isCancelled() {
        return flag.get();
    }
}