package io.github.mundanej.map.api;

import java.util.concurrent.atomic.AtomicBoolean;

/** Owner of a monotonic cancellation token. */
public final class CancellationSource {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CancellationToken token = cancelled::get;

    /** Creates an initially active cancellation source. */
    public CancellationSource() {}

    /**
     * Returns the stable token.
     *
     * @return cross-thread token owned by this source
     */
    public CancellationToken token() {
        return token;
    }

    /** Requests cancellation idempotently. */
    public void cancel() {
        cancelled.set(true);
    }
}
