package io.github.mundanej.map.io.http.tiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.RasterSource;

/** Explicit synchronous HTTP XYZ acquisition client. */
public interface HttpXyzTileClient extends AutoCloseable {
    /**
     * Fetches one bounded region and returns a fully detached raster source.
     *
     * @param region requested canonical XYZ region
     * @param cancellation operation-local cancellation signal
     * @return caller-owned detached raster source
     */
    RasterSource fetch(XyzTileRegion region, CancellationToken cancellation);

    /**
     * Returns whether this client has been explicitly closed.
     *
     * @return whether closed
     */
    boolean isClosed();

    /** Closes this client idempotently. */
    @Override
    void close();
}
