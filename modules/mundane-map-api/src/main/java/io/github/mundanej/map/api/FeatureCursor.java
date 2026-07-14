package io.github.mundanej.map.api;

/** Externally serialized closeable pull cursor. */
public interface FeatureCursor extends AutoCloseable {
    /** Advances to the next current record. */
    boolean advance();

    /** Returns the current record after a successful advance. */
    FeatureRecord current();

    /** Returns the warnings observed so far. */
    DiagnosticReport diagnostics();

    /** Returns whether explicitly closed. */
    boolean isClosed();

    /** Closes early idempotently. */
    @Override
    void close();
}
