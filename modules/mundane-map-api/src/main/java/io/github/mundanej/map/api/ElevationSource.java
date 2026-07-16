package io.github.mundanej.map.api;

import java.util.OptionalDouble;

/**
 * Synchronous externally serialized source of regularly sampled numeric elevation data.
 *
 * <p>Metadata, limits, and opening diagnostics remain available after close. Implementations mark
 * themselves closed before cleanup, close resources once in encounter order, and never retry failed
 * cleanup. A cleanup failure is reported as {@code SOURCE_CLOSE_FAILED}; the first failure is
 * primary and later failures are suppressed in encounter order.
 */
public interface ElevationSource extends AutoCloseable {
    /**
     * Returns immutable opening metadata.
     *
     * @return metadata that remains valid after close
     */
    ElevationSourceMetadata metadata();

    /**
     * Returns captured effective limits.
     *
     * @return immutable effective limits that remain valid after close
     */
    ElevationSourceLimits limits();

    /**
     * Returns immutable opening warnings.
     *
     * @return diagnostics that remain valid after close
     */
    DiagnosticReport openingDiagnostics();

    /**
     * Returns one row-major sample without interpolation.
     *
     * @param column zero-based west-to-east column
     * @param row zero-based north-to-south row
     * @return finite sample in the declared unit, or empty for no-data
     * @throws IllegalStateException if the source is closed
     * @throws IndexOutOfBoundsException if either index is outside the grid
     */
    OptionalDouble sample(int column, int row);

    /**
     * Returns whether explicitly closed.
     *
     * @return whether the source is closed
     */
    boolean isClosed();

    /**
     * Closes the source idempotently and releases its owned storage or handles.
     *
     * @throws SourceException if cleanup fails after the source has been marked closed
     */
    @Override
    void close();
}
