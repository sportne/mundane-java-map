package io.github.mundanej.map.api;

import java.util.concurrent.atomic.AtomicBoolean;

/** Owner of a monotonic cancellation token. */
public final class CancellationSource {
    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final CancellationToken token = cancelled::get;

    /** Returns the stable token. */
    public CancellationToken token() {
        return token;
    }

    /** Requests cancellation idempotently. */
    public void cancel() {
        cancelled.set(true);
    }
}
