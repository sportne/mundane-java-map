package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable viewport/query request in source coordinates.
 *
 * @param sourceBounds optional source-coordinate envelope; empty requests the whole source
 * @param attributes requested attribute selection
 * @param tighterLimits optional per-query limits that may only tighten source limits
 */
public record FeatureQuery(
        Optional<Envelope> sourceBounds,
        AttributeSelection attributes,
        Optional<FeatureQueryLimits> tighterLimits) {
    /** Validates a query. */
    public FeatureQuery {
        Objects.requireNonNull(sourceBounds, "sourceBounds");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(tighterLimits, "tighterLimits");
    }

    /**
     * Returns an unbounded all-attribute query.
     *
     * @return immutable whole-source query
     */
    public static FeatureQuery all() {
        return new FeatureQuery(Optional.empty(), AttributeSelection.ALL, Optional.empty());
    }
}
