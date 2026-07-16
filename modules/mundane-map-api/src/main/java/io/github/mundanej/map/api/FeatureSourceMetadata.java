package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Immutable metadata captured when a feature source opens.
 *
 * @param identity bounded logical identity with no implicit locator
 * @param extent optional source-coordinate extent
 * @param featureCount optional non-negative feature count
 * @param schema optional immutable attribute schema
 * @param crs optional retained and possibly recognized CRS metadata
 */
public record FeatureSourceMetadata(
        SourceIdentity identity,
        Optional<Envelope> extent,
        OptionalLong featureCount,
        Optional<AttributeSchema> schema,
        Optional<CrsMetadata> crs) {
    /** Validates metadata. */
    public FeatureSourceMetadata {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(extent, "extent");
        Objects.requireNonNull(featureCount, "featureCount");
        Objects.requireNonNull(schema, "schema");
        Objects.requireNonNull(crs, "crs");
        if (featureCount.isPresent() && featureCount.getAsLong() < 0) {
            throw new IllegalArgumentException("featureCount must be non-negative");
        }
    }
}
