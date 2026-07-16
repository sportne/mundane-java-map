package io.github.mundanej.map.api;

/** Synchronous externally serialized all-or-nothing raster source. */
public interface RasterSource extends AutoCloseable {
    /**
     * Returns immutable opening metadata.
     *
     * @return metadata that remains valid after close
     */
    RasterSourceMetadata metadata();

    /**
     * Returns captured effective limits.
     *
     * @return immutable effective limits
     */
    RasterSourceLimits limits();

    /**
     * Returns immutable opening warnings.
     *
     * @return diagnostics that remain valid after close
     */
    DiagnosticReport openingDiagnostics();

    /**
     * Performs one synchronous all-or-nothing read.
     *
     * @param request bounded source-window request
     * @param cancellation cross-thread cancellation token
     * @return immutable successful raster read
     */
    RasterRead read(RasterRequest request, CancellationToken cancellation);

    /**
     * Returns whether explicitly closed.
     *
     * @return whether the source is closed
     */
    boolean isClosed();

    /** Closes the source idempotently. */
    @Override
    void close();
}
