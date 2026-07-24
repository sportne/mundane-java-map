package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Immutable caller-supplied portrayal evaluation context.
 *
 * @param scaleDenominator optional finite nonnegative scale denominator
 * @param zoomLevel optional finite zoom level
 * @param geometryType optional normalized geometry category
 */
public record PortrayalEvaluationContext(
        OptionalDouble scaleDenominator,
        OptionalDouble zoomLevel,
        Optional<PortrayalGeometryType> geometryType) {
    /** Context without scale information. */
    public static final PortrayalEvaluationContext UNSCALED =
            new PortrayalEvaluationContext(
                    OptionalDouble.empty(), OptionalDouble.empty(), Optional.empty());

    /** Validates the context. */
    public PortrayalEvaluationContext {
        Objects.requireNonNull(scaleDenominator, "scaleDenominator");
        Objects.requireNonNull(zoomLevel, "zoomLevel");
        Objects.requireNonNull(geometryType, "geometryType");
        scaleDenominator.ifPresent(
                value -> {
                    if (!Double.isFinite(value) || value < 0) {
                        throw new IllegalArgumentException(
                                "scale denominator must be finite and nonnegative");
                    }
                });
        zoomLevel.ifPresent(
                value -> {
                    if (!Double.isFinite(value)) {
                        throw new IllegalArgumentException("zoom level must be finite");
                    }
                });
        geometryType = geometryType.map(Objects::requireNonNull);
    }

    /**
     * Compatibility constructor for a scale-only context.
     *
     * @param scaleDenominator optional finite nonnegative scale denominator
     */
    public PortrayalEvaluationContext(OptionalDouble scaleDenominator) {
        this(scaleDenominator, OptionalDouble.empty(), Optional.empty());
    }

    /**
     * Creates a context containing one scale denominator.
     *
     * @param denominator finite nonnegative denominator
     * @return immutable context
     */
    public static PortrayalEvaluationContext atScale(double denominator) {
        return new PortrayalEvaluationContext(
                OptionalDouble.of(denominator), OptionalDouble.empty(), Optional.empty());
    }

    /**
     * Creates a context containing scale and zoom.
     *
     * @param denominator finite nonnegative scale denominator
     * @param zoom finite zoom level
     * @return immutable context
     */
    public static PortrayalEvaluationContext atScaleAndZoom(double denominator, double zoom) {
        return new PortrayalEvaluationContext(
                OptionalDouble.of(denominator), OptionalDouble.of(zoom), Optional.empty());
    }

    /**
     * Returns a context with one normalized geometry category.
     *
     * @param type normalized category
     * @return immutable context retaining scale and zoom
     */
    public PortrayalEvaluationContext withGeometryType(PortrayalGeometryType type) {
        return new PortrayalEvaluationContext(
                scaleDenominator, zoomLevel, Optional.of(Objects.requireNonNull(type, "type")));
    }
}
