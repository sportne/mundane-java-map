package io.github.mundanej.map.api;

import java.util.List;
import java.util.Optional;

/**
 * A named, ordered immutable feature snapshot for small already-available data sets.
 *
 * <p>Implementations return stable immutable snapshots. Use {@link FeatureSource} and a toolkit
 * binding when records need bounded or lazy access.
 */
public interface Layer {
    /**
     * Returns a stable layer identifier.
     *
     * @return non-blank layer identity
     */
    String id();

    /**
     * Returns a display name.
     *
     * @return non-null presentation name
     */
    String name();

    /**
     * Returns the current ordered feature snapshot.
     *
     * @return immutable snapshot in paint order
     */
    List<Feature> features();

    /**
     * Returns the source-coordinate envelope when the layer is non-empty.
     *
     * @return optional source-coordinate envelope
     */
    Optional<Envelope> envelope();
}
