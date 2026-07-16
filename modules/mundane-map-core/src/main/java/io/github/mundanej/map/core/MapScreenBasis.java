package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import java.util.Objects;

/** A validated uniform projected-map-unit to logical-screen-pixel similarity basis. */
public final class MapScreenBasis {
    private static final double SIMILARITY_TOLERANCE = 1.0e-12;

    private final Coordinate xUnitScreenDelta;
    private final Coordinate yUnitScreenDelta;
    private final double determinant;
    private final double uniformScale;
    private final double xAxisScreenBearingDegrees;

    private MapScreenBasis(
            Coordinate xUnitScreenDelta,
            Coordinate yUnitScreenDelta,
            double determinant,
            double uniformScale,
            double xAxisScreenBearingDegrees) {
        this.xUnitScreenDelta = xUnitScreenDelta;
        this.yUnitScreenDelta = yUnitScreenDelta;
        this.determinant = determinant;
        this.uniformScale = uniformScale;
        this.xAxisScreenBearingDegrees = xAxisScreenBearingDegrees;
    }

    /**
     * Creates a finite, orientation-reversing uniform similarity basis.
     *
     * @param xUnitScreenDelta logical-screen delta for one positive projected x unit
     * @param yUnitScreenDelta logical-screen delta for one positive projected y unit
     * @return validated immutable similarity basis
     */
    public static MapScreenBasis of(Coordinate xUnitScreenDelta, Coordinate yUnitScreenDelta) {
        Objects.requireNonNull(xUnitScreenDelta, "xUnitScreenDelta");
        Objects.requireNonNull(yUnitScreenDelta, "yUnitScreenDelta");
        double xLength = StrictMath.hypot(xUnitScreenDelta.x(), xUnitScreenDelta.y());
        double yLength = StrictMath.hypot(yUnitScreenDelta.x(), yUnitScreenDelta.y());
        if (!Double.isFinite(xLength) || xLength <= 0.0) {
            throw new IllegalArgumentException("xUnitScreenDelta must have finite positive length");
        }
        if (!Double.isFinite(yLength) || yLength <= 0.0) {
            throw new IllegalArgumentException("yUnitScreenDelta must have finite positive length");
        }
        double determinant =
                xUnitScreenDelta.x() * yUnitScreenDelta.y()
                        - xUnitScreenDelta.y() * yUnitScreenDelta.x();
        if (!Double.isFinite(determinant) || determinant >= 0.0) {
            throw new IllegalArgumentException("basis determinant must be finite and negative");
        }
        double normalizedDot =
                (xUnitScreenDelta.x() / xLength) * (yUnitScreenDelta.x() / yLength)
                        + (xUnitScreenDelta.y() / xLength) * (yUnitScreenDelta.y() / yLength);
        if (!Double.isFinite(normalizedDot)
                || StrictMath.abs(normalizedDot) > SIMILARITY_TOLERANCE) {
            throw new IllegalArgumentException("basis vectors must be perpendicular");
        }
        double relativeScaleDifference =
                StrictMath.abs(xLength - yLength) / StrictMath.max(xLength, yLength);
        if (!Double.isFinite(relativeScaleDifference)
                || relativeScaleDifference > SIMILARITY_TOLERANCE) {
            throw new IllegalArgumentException("basis vectors must have equal scale");
        }
        double uniformScale = StrictMath.sqrt(StrictMath.abs(determinant));
        if (!Double.isFinite(uniformScale) || uniformScale <= 0.0) {
            throw new IllegalArgumentException("basis uniform scale must be finite and positive");
        }
        double bearing =
                normalizeDegrees(
                        StrictMath.toDegrees(
                                StrictMath.atan2(xUnitScreenDelta.y(), xUnitScreenDelta.x())));
        return new MapScreenBasis(
                xUnitScreenDelta, yUnitScreenDelta, determinant, uniformScale, bearing);
    }

    /**
     * Returns the screen delta corresponding to one projected positive-x map unit.
     *
     * @return immutable logical-screen delta
     */
    public Coordinate xUnitScreenDelta() {
        return xUnitScreenDelta;
    }

    /**
     * Returns the screen delta corresponding to one projected positive-y map unit.
     *
     * @return immutable logical-screen delta
     */
    public Coordinate yUnitScreenDelta() {
        return yUnitScreenDelta;
    }

    /**
     * Returns the negative orientation-reversing determinant.
     *
     * @return finite determinant in squared screen-pixels per squared map unit
     */
    public double determinant() {
        return determinant;
    }

    /**
     * Returns logical screen pixels per projected map unit.
     *
     * @return finite positive uniform scale
     */
    public double uniformScale() {
        return uniformScale;
    }

    /**
     * Returns the clockwise screen bearing of projected positive x.
     *
     * @return normalized bearing in degrees from zero inclusive to 360 exclusive
     */
    public double xAxisScreenBearingDegrees() {
        return xAxisScreenBearingDegrees;
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        return normalized == 0.0 ? 0.0 : normalized;
    }
}
