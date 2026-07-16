package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import java.util.Objects;

/** Read-only marker affine coefficients and their axis-aligned nominal screen bounds. */
public final class MarkerTransform {
    private final double m00;
    private final double m10;
    private final double m01;
    private final double m11;
    private final double m02;
    private final double m12;
    private final Envelope nominalScreenBounds;

    MarkerTransform(
            double m00,
            double m10,
            double m01,
            double m11,
            double m02,
            double m12,
            Envelope nominalScreenBounds) {
        this.m00 = m00;
        this.m10 = m10;
        this.m01 = m01;
        this.m11 = m11;
        this.m02 = m02;
        this.m12 = m12;
        this.nominalScreenBounds = nominalScreenBounds;
    }

    /**
     * Returns the local-x to screen-x coefficient.
     *
     * @return affine coefficient
     */
    public double m00() {
        return m00;
    }

    /**
     * Returns the local-x to screen-y coefficient.
     *
     * @return affine coefficient
     */
    public double m10() {
        return m10;
    }

    /**
     * Returns the local-y to screen-x coefficient.
     *
     * @return affine coefficient
     */
    public double m01() {
        return m01;
    }

    /**
     * Returns the local-y to screen-y coefficient.
     *
     * @return affine coefficient
     */
    public double m11() {
        return m11;
    }

    /**
     * Returns the screen-x translation coefficient.
     *
     * @return logical-screen x translation
     */
    public double m02() {
        return m02;
    }

    /**
     * Returns the screen-y translation coefficient.
     *
     * @return logical-screen y translation
     */
    public double m12() {
        return m12;
    }

    /**
     * Returns the axis-aligned bounds of the transformed view-box corners.
     *
     * @return immutable nominal bounds in logical screen pixels
     */
    public Envelope nominalScreenBounds() {
        return nominalScreenBounds;
    }

    /**
     * Inverse-maps one finite screen coordinate into marker-local coordinates.
     *
     * @param screen coordinate in logical screen pixels
     * @return coordinate in the marker's toolkit-neutral local path space
     */
    public Coordinate screenToLocal(Coordinate screen) {
        Objects.requireNonNull(screen, "screen");
        double maximum =
                Math.max(
                        Math.max(Math.abs(m00), Math.abs(m01)),
                        Math.max(Math.abs(m10), Math.abs(m11)));
        int exponent = Math.getExponent(maximum);
        double a = Math.scalb(m00, -exponent);
        double b = Math.scalb(m01, -exponent);
        double c = Math.scalb(m10, -exponent);
        double d = Math.scalb(m11, -exponent);
        double determinant = a * d - b * c;
        if (!Double.isFinite(determinant) || determinant == 0.0) {
            throw new IllegalStateException("Marker transform must remain finite and invertible");
        }
        double translatedX = Math.scalb(screen.x(), -exponent) - Math.scalb(m02, -exponent);
        double translatedY = Math.scalb(screen.y(), -exponent) - Math.scalb(m12, -exponent);
        double localX = (d * translatedX - b * translatedY) / determinant;
        double localY = (-c * translatedX + a * translatedY) / determinant;
        if (!Double.isFinite(localX) || !Double.isFinite(localY)) {
            throw new IllegalArgumentException("Screen coordinate is outside the inverse domain");
        }
        return new Coordinate(localX, localY);
    }
}
