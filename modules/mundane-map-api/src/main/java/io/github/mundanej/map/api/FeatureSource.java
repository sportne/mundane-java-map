package io.github.mundanej.map.api;

/** Synchronous externally serialized feature source. */
public interface FeatureSource extends AutoCloseable {
    /**
     * Returns immutable opening metadata.
     *
     * @return metadata that remains valid after close
     */
    FeatureSourceMetadata metadata();

    /**
     * Returns captured effective limits.
     *
     * @return immutable effective limits
     */
    FeatureSourceLimits limits();

    /**
     * Returns immutable opening warnings.
     *
     * @return diagnostics that remain valid after close
     */
    DiagnosticReport openingDiagnostics();

    /**
     * Opens the sole live pull cursor.
     *
     * @param query immutable source-coordinate query
     * @param cancellation cross-thread cancellation token
     * @return caller-owned cursor that must be closed
     */
    FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation);

    /**
     * Returns whether explicitly closed.
     *
     * @return whether the source is closed
     */
    boolean isClosed();

    /** Closes the source and its live cursor idempotently. */
    @Override
    void close();
}
