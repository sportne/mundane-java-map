package io.github.mundanej.map.api;

/** Cross-thread read-only cancellation signal. */
@FunctionalInterface
public interface CancellationToken {
    /** Returns whether cancellation has been requested. */
    boolean isCancellationRequested();

    /** Returns a token that is never cancelled. */
    static CancellationToken none() {
        return NeverCancelled.INSTANCE;
    }

    enum NeverCancelled implements CancellationToken {
        INSTANCE;

        @Override
        public boolean isCancellationRequested() {
            return false;
        }
    }
}
