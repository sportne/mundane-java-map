package io.github.mundanej.map.api;

/** Externally serialized closeable pull cursor. */
public interface FeatureCursor extends AutoCloseable {
    /**
     * Advances to the next current record.
     *
     * @return whether a current record is available
     */
    boolean advance();

    /**
     * Returns the current record after a successful advance.
     *
     * @return current immutable record
     */
    FeatureRecord current();

    /**
     * Returns the warnings observed so far.
     *
     * @return immutable diagnostic snapshot
     */
    DiagnosticReport diagnostics();

    /**
     * Returns whether explicitly closed.
     *
     * @return whether the cursor is closed
     */
    boolean isClosed();

    /** Closes early idempotently. */
    @Override
    void close();
}
