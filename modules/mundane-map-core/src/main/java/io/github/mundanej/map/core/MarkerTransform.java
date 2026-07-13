package io.github.mundanej.map.core;

import io.github.mundanej.map.api.Envelope;

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

    /** Returns the local-x to screen-x coefficient. */
    public double m00() {
        return m00;
    }

    /** Returns the local-x to screen-y coefficient. */
    public double m10() {
        return m10;
    }

    /** Returns the local-y to screen-x coefficient. */
    public double m01() {
        return m01;
    }

    /** Returns the local-y to screen-y coefficient. */
    public double m11() {
        return m11;
    }

    /** Returns the screen-x translation coefficient. */
    public double m02() {
        return m02;
    }

    /** Returns the screen-y translation coefficient. */
    public double m12() {
        return m12;
    }

    /** Returns the axis-aligned bounds of the transformed view-box corners. */
    public Envelope nominalScreenBounds() {
        return nominalScreenBounds;
    }
}
