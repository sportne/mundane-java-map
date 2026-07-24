package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.CancellationToken;
import java.util.Objects;

/**
 * Immutable style-read policy.
 *
 * @param limits parser ceilings
 * @param cancellation caller cancellation token
 */
public record MapLibreReadOptions(MapLibreReadLimits limits, CancellationToken cancellation) {
    /** Validates options. */
    public MapLibreReadOptions {
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
    }

    /**
     * Returns default bounded, non-cancelled options.
     *
     * @return immutable defaults
     */
    public static MapLibreReadOptions defaults() {
        return new MapLibreReadOptions(MapLibreReadLimits.defaults(), CancellationToken.none());
    }
}
