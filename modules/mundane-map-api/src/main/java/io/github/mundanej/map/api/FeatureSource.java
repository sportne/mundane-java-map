package io.github.mundanej.map.api;

/** Synchronous externally serialized feature source. */
public interface FeatureSource extends AutoCloseable {
    /** Returns immutable opening metadata. */
    FeatureSourceMetadata metadata();

    /** Returns captured effective limits. */
    FeatureSourceLimits limits();

    /** Returns immutable opening warnings. */
    DiagnosticReport openingDiagnostics();

    /** Opens the sole live pull cursor. */
    FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation);

    /** Returns whether explicitly closed. */
    boolean isClosed();

    /** Closes the source and its live cursor idempotently. */
    @Override
    void close();
}
