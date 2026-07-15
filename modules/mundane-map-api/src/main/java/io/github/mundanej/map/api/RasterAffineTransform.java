package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable affine transform from raster pixel-center coordinates to map coordinates.
 *
 * <p>The six coefficients use the world-file convention: {@code mapX = a * column + b * row + c}
 * and {@code mapY = d * column + e * row + f}. Integer grid coordinates identify pixel centers.
 */
public final class RasterAffineTransform {
    private final double a;
    private final double d;
    private final double b;
    private final double e;
    private final double c;
    private final double f;
    private final double inverseA;
    private final double inverseB;
    private final double inverseC;
    private final double inverseD;
    private final double inverseE;
    private final double inverseF;

    private RasterAffineTransform(double a, double d, double b, double e, double c, double f) {
        this.a = canonical(finite(a, "a"));
        this.d = canonical(finite(d, "d"));
        this.b = canonical(finite(b, "b"));
        this.e = canonical(finite(e, "e"));
        this.c = canonical(finite(c, "c"));
        this.f = canonical(finite(f, "f"));

        double scale =
                Math.max(Math.max(Math.abs(a), Math.abs(b)), Math.max(Math.abs(d), Math.abs(e)));
        if (!(scale > 0.0) || !Double.isFinite(scale)) {
            throw new RasterPlacementException(RasterPlacementException.Reason.SINGULAR);
        }
        double normalizedA = a / scale;
        double normalizedB = b / scale;
        double normalizedD = d / scale;
        double normalizedE = e / scale;
        double determinant = Math.fma(normalizedA, normalizedE, -normalizedB * normalizedD);
        if (determinant == 0.0 || !Double.isFinite(determinant)) {
            throw new RasterPlacementException(RasterPlacementException.Reason.SINGULAR);
        }
        inverseA = checkedInverse(normalizedE, determinant, scale);
        inverseB = checkedInverse(-normalizedB, determinant, scale);
        inverseD = checkedInverse(-normalizedD, determinant, scale);
        inverseE = checkedInverse(normalizedA, determinant, scale);
        inverseC = inverseTranslation(-inverseA, c, -inverseB, f);
        inverseF = inverseTranslation(-inverseD, c, -inverseE, f);
    }

    /**
     * Creates a validated transform in physical world-file order {@code A,D,B,E,C,F}.
     *
     * @param a map-x coefficient for grid columns
     * @param d map-y coefficient for grid columns
     * @param b map-x coefficient for grid rows
     * @param e map-y coefficient for grid rows
     * @param c map-x translation
     * @param f map-y translation
     * @return validated immutable transform
     * @throws IllegalArgumentException if any coefficient is not finite
     * @throws RasterPlacementException if the linear transform is singular or its inverse cannot be
     *     represented finitely
     */
    public static RasterAffineTransform of(
            double a, double d, double b, double e, double c, double f) {
        return new RasterAffineTransform(a, d, b, e, c, f);
    }

    /**
     * Returns the map-x coefficient for grid columns.
     *
     * @return map-x column coefficient
     */
    public double a() {
        return a;
    }

    /**
     * Returns the map-y coefficient for grid columns.
     *
     * @return map-y column coefficient
     */
    public double d() {
        return d;
    }

    /**
     * Returns the map-x coefficient for grid rows.
     *
     * @return map-x row coefficient
     */
    public double b() {
        return b;
    }

    /**
     * Returns the map-y coefficient for grid rows.
     *
     * @return map-y row coefficient
     */
    public double e() {
        return e;
    }

    /**
     * Returns the map-x translation.
     *
     * @return map-x translation
     */
    public double c() {
        return c;
    }

    /**
     * Returns the map-y translation.
     *
     * @return map-y translation
     */
    public double f() {
        return f;
    }

    /**
     * Transforms a pixel-center grid coordinate to map coordinates.
     *
     * @param columnCenter grid-column center coordinate
     * @param rowCenter grid-row center coordinate
     * @return transformed map coordinate
     * @throws IllegalArgumentException if an input is not finite
     * @throws ArithmeticException if the finite result cannot be represented
     */
    public Coordinate gridToMap(double columnCenter, double rowCenter) {
        finite(columnCenter, "columnCenter");
        finite(rowCenter, "rowCenter");
        return new Coordinate(
                checkedComposition(a, columnCenter, b, rowCenter, c, "forward x"),
                checkedComposition(d, columnCenter, e, rowCenter, f, "forward y"));
    }

    /**
     * Inverse-transforms a map coordinate to pixel-center grid coordinates.
     *
     * @param mapCoordinate finite map coordinate
     * @return grid-center coordinate whose x is column and y is row
     * @throws NullPointerException if {@code mapCoordinate} is null
     * @throws ArithmeticException if the finite result cannot be represented
     */
    public Coordinate mapToGrid(Coordinate mapCoordinate) {
        Objects.requireNonNull(mapCoordinate, "mapCoordinate");
        return new Coordinate(
                checkedComposition(
                        inverseA,
                        mapCoordinate.x(),
                        inverseB,
                        mapCoordinate.y(),
                        inverseC,
                        "inverse column"),
                checkedComposition(
                        inverseD,
                        mapCoordinate.x(),
                        inverseE,
                        mapCoordinate.y(),
                        inverseF,
                        "inverse row"));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RasterAffineTransform that
                && Double.doubleToLongBits(a) == Double.doubleToLongBits(that.a)
                && Double.doubleToLongBits(d) == Double.doubleToLongBits(that.d)
                && Double.doubleToLongBits(b) == Double.doubleToLongBits(that.b)
                && Double.doubleToLongBits(e) == Double.doubleToLongBits(that.e)
                && Double.doubleToLongBits(c) == Double.doubleToLongBits(that.c)
                && Double.doubleToLongBits(f) == Double.doubleToLongBits(that.f);
    }

    @Override
    public int hashCode() {
        return Objects.hash(a, d, b, e, c, f);
    }

    @Override
    public String toString() {
        return "RasterAffineTransform[a="
                + a
                + ", d="
                + d
                + ", b="
                + b
                + ", e="
                + e
                + ", c="
                + c
                + ", f="
                + f
                + ']';
    }

    private static double checkedInverse(double numerator, double determinant, double scale) {
        double value = (numerator / scale) / determinant;
        if (!Double.isFinite(value)) {
            value = (numerator / determinant) / scale;
        }
        if (!Double.isFinite(value)) {
            throw new RasterPlacementException(RasterPlacementException.Reason.INVERSE_NON_FINITE);
        }
        return canonical(value);
    }

    private static double inverseTranslation(
            double firstCoefficient,
            double firstValue,
            double secondCoefficient,
            double secondValue) {
        try {
            return canonical(
                    checkedComposition(
                            firstCoefficient,
                            firstValue,
                            secondCoefficient,
                            secondValue,
                            "inverse translation"));
        } catch (ArithmeticException exception) {
            throw new RasterPlacementException(
                    RasterPlacementException.Reason.INVERSE_NON_FINITE, exception);
        }
    }

    private static double checkedComposition(
            double firstCoefficient,
            double firstValue,
            double secondCoefficient,
            double secondValue,
            String operation) {
        return checkedComposition(
                firstCoefficient, firstValue, secondCoefficient, secondValue, 0.0, operation);
    }

    private static double checkedComposition(
            double firstCoefficient,
            double firstValue,
            double secondCoefficient,
            double secondValue,
            double translation,
            String operation) {
        double secondTerm = Math.fma(secondCoefficient, secondValue, translation);
        double result = Math.fma(firstCoefficient, firstValue, secondTerm);
        if (!Double.isFinite(secondTerm) || !Double.isFinite(result)) {
            throw new ArithmeticException("Raster affine " + operation + " is not finite");
        }
        return canonical(result);
    }

    private static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value;
    }

    private static double canonical(double value) {
        return value == 0.0 ? 0.0 : value;
    }
}
