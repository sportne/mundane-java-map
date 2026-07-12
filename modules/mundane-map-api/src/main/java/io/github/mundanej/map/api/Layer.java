package io.github.mundanej.map.api;

import java.util.List;
import java.util.Optional;

/** A named, ordered source of features. */
public interface Layer {
    /** Returns a stable layer identifier. */
    String id();

    /** Returns a display name. */
    String name();

    /** Returns the current ordered feature snapshot. */
    List<Feature> features();

    /** Returns the source-coordinate envelope when the layer is non-empty. */
    Optional<Envelope> envelope();
}
