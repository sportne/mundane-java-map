package io.github.mundanej.map.api;

/** Synchronous externally serialized all-or-nothing raster source. */
public interface RasterSource extends AutoCloseable {
    /** Returns immutable opening metadata. */
    RasterSourceMetadata metadata();

    /** Returns captured effective limits. */
    RasterSourceLimits limits();

    /** Returns immutable opening warnings. */
    DiagnosticReport openingDiagnostics();

    /** Performs one synchronous all-or-nothing read. */
    RasterRead read(RasterRequest request, CancellationToken cancellation);

    /** Returns whether explicitly closed. */
    boolean isClosed();

    /** Closes the source idempotently. */
    @Override
    void close();
}
