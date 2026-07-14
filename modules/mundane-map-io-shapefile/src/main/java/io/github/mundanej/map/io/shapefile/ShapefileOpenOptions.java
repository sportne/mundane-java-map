package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.FeatureSourceLimits;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable options controlling Shapefile opening and subsequent feature queries.
 *
 * <p>The defaults apply the Level 1 source and parser limits and do not assume a coordinate
 * reference system. A CRS override is explicit metadata; it does not transform coordinates.
 */
public final class ShapefileOpenOptions {
    private static final ShapefileOpenOptions DEFAULTS =
            new ShapefileOpenOptions(
                    FeatureSourceLimits.LEVEL_1, ShapefileLimits.defaults(), Optional.empty());
    private final FeatureSourceLimits featureSourceLimits;
    private final ShapefileLimits shapefileLimits;
    private final Optional<CrsDefinition> crsOverride;

    private ShapefileOpenOptions(
            FeatureSourceLimits feature, ShapefileLimits shape, Optional<CrsDefinition> crs) {
        featureSourceLimits = Objects.requireNonNull(feature, "featureSourceLimits");
        shapefileLimits = Objects.requireNonNull(shape, "shapefileLimits");
        crsOverride = Objects.requireNonNull(crs, "crsOverride");
    }

    /**
     * Returns the shared options instance containing default limits and no CRS override.
     *
     * @return default Shapefile opening options
     */
    public static ShapefileOpenOptions defaults() {
        return DEFAULTS;
    }

    /**
     * Returns the limits applied by the format-neutral feature-source contract.
     *
     * @return feature-source query and allocation limits
     */
    public FeatureSourceLimits featureSourceLimits() {
        return featureSourceLimits;
    }

    /**
     * Returns the format-specific parser limits.
     *
     * @return Shapefile component, record, topology, text, and allocation limits
     */
    public ShapefileLimits shapefileLimits() {
        return shapefileLimits;
    }

    /**
     * Returns the recognized explicit CRS override, if one was supplied.
     *
     * @return the explicit CRS definition, or an empty value when coordinates remain undeclared
     */
    public Optional<CrsDefinition> crsOverride() {
        return crsOverride;
    }

    /**
     * Returns a copy with different format-neutral feature-source limits.
     *
     * @param featureSourceLimits limits to apply to feature queries and materialization
     * @return a copy containing {@code featureSourceLimits}
     * @throws NullPointerException if {@code featureSourceLimits} is {@code null}
     */
    public ShapefileOpenOptions withFeatureSourceLimits(FeatureSourceLimits featureSourceLimits) {
        return new ShapefileOpenOptions(featureSourceLimits, shapefileLimits, crsOverride);
    }

    /**
     * Returns a copy with different format-specific parser limits.
     *
     * @param shapefileLimits limits to apply while opening and parsing Shapefile components
     * @return a copy containing {@code shapefileLimits}
     * @throws NullPointerException if {@code shapefileLimits} is {@code null}
     */
    public ShapefileOpenOptions withShapefileLimits(ShapefileLimits shapefileLimits) {
        return new ShapefileOpenOptions(featureSourceLimits, shapefileLimits, crsOverride);
    }

    /**
     * Returns a copy with an explicit recognized CRS override.
     *
     * <p>The override declares the coordinate system; it does not transform coordinates.
     *
     * @param crsOverride recognized CRS definition to associate with the source
     * @return a copy containing {@code crsOverride}
     * @throws NullPointerException if {@code crsOverride} is {@code null}
     */
    public ShapefileOpenOptions withCrsOverride(CrsDefinition crsOverride) {
        return new ShapefileOpenOptions(
                featureSourceLimits,
                shapefileLimits,
                Optional.of(Objects.requireNonNull(crsOverride, "crsOverride")));
    }

    /**
     * Returns a copy without a CRS override.
     *
     * @return a copy whose {@link #crsOverride()} is empty
     */
    public ShapefileOpenOptions withoutCrsOverride() {
        return new ShapefileOpenOptions(featureSourceLimits, shapefileLimits, Optional.empty());
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ShapefileOpenOptions v
                && featureSourceLimits.equals(v.featureSourceLimits)
                && shapefileLimits.equals(v.shapefileLimits)
                && crsOverride.equals(v.crsOverride);
    }

    @Override
    public int hashCode() {
        return Objects.hash(featureSourceLimits, shapefileLimits, crsOverride);
    }

    @Override
    public String toString() {
        return "ShapefileOpenOptions[featureSourceLimits="
                + featureSourceLimits
                + ", shapefileLimits="
                + shapefileLimits
                + ", crsOverride="
                + crsOverride.map(CrsDefinition::canonicalIdentifier)
                + "]";
    }
}
