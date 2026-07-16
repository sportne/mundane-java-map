package io.github.mundanej.map.api;

/** Cross-thread read-only cancellation signal. */
@FunctionalInterface
public interface CancellationToken {
    /**
     * Returns whether cancellation has been requested.
     *
     * @return whether cancellation is requested
     */
    boolean isCancellationRequested();

    /**
     * Returns a token that is never cancelled.
     *
     * @return shared never-cancelled token
     */
    static CancellationToken none() {
        return NeverCancelled.INSTANCE;
    }

    /** Stateless implementation used by {@link #none()}. */
    enum NeverCancelled implements CancellationToken {
        /** The shared never-cancelled token. */
        INSTANCE;

        @Override
        public boolean isCancellationRequested() {
            return false;
        }
    }
}
