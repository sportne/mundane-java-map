package io.github.mundanej.map.api;

/** Supported packed coordinate dimensional models. */
public enum GeometryDimension {
    /** Two-dimensional x/y positions. */
    XY(2, -1, -1),
    /** Three-dimensional x/y/z positions. */
    XYZ(3, 2, -1),
    /** Three-dimensional x/y/m positions. */
    XYM(3, -1, 2),
    /** Four-dimensional x/y/z/m positions. */
    XYZM(4, 2, 3);

    private final int stride;
    private final int zOffset;
    private final int mOffset;

    GeometryDimension(int stride, int zOffset, int mOffset) {
        this.stride = stride;
        this.zOffset = zOffset;
        this.mOffset = mOffset;
    }

    /**
     * Returns the number of packed ordinates per position.
     *
     * @return two, three, or four
     */
    public int stride() {
        return stride;
    }

    /**
     * Returns whether positions carry a z ordinate.
     *
     * @return whether z is present
     */
    public boolean hasZ() {
        return zOffset >= 0;
    }

    /**
     * Returns whether positions carry an m ordinate.
     *
     * @return whether m is present
     */
    public boolean hasM() {
        return mOffset >= 0;
    }

    /**
     * Returns the packed z offset.
     *
     * @return zero-based offset within one position
     * @throws GeometryException when this model has no z ordinate
     */
    public int zOffset() {
        if (!hasZ()) {
            throw GeometryException.missingOrdinate("z", this);
        }
        return zOffset;
    }

    /**
     * Returns the packed m offset.
     *
     * @return zero-based offset within one position
     * @throws GeometryException when this model has no m ordinate
     */
    public int mOffset() {
        if (!hasM()) {
            throw GeometryException.missingOrdinate("m", this);
        }
        return mOffset;
    }

    /**
     * Returns the least model that can carry both inputs.
     *
     * @param other another model
     * @return union model
     */
    public GeometryDimension union(GeometryDimension other) {
        java.util.Objects.requireNonNull(other, "other");
        boolean z = hasZ() || other.hasZ();
        boolean m = hasM() || other.hasM();
        if (z && m) {
            return XYZM;
        }
        if (z) {
            return XYZ;
        }
        if (m) {
            return XYM;
        }
        return XY;
    }
}
