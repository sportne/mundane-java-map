package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable tagged placement of a raster grid in map coordinates. */
public final class RasterGridPlacement {
    /** Available placement representations. */
    public enum Kind {
        /** Exact axis-aligned outer bounds. */
        AXIS_ALIGNED,
        /** Affine mapping from pixel centers to map coordinates. */
        AFFINE
    }

    private final Kind kind;
    private final Envelope axisAlignedBounds;
    private final RasterAffineTransform affineTransform;

    private RasterGridPlacement(
            Kind kind, Envelope axisAlignedBounds, RasterAffineTransform affineTransform) {
        this.kind = kind;
        this.axisAlignedBounds = axisAlignedBounds;
        this.affineTransform = affineTransform;
    }

    /**
     * Creates an axis-aligned placement with exact positive-area outer bounds.
     *
     * @param exactBounds exact grid outer bounds
     * @return axis-aligned placement
     * @throws NullPointerException if {@code exactBounds} is {@code null}
     * @throws IllegalArgumentException if {@code exactBounds} does not have positive area
     */
    public static RasterGridPlacement axisAligned(Envelope exactBounds) {
        Objects.requireNonNull(exactBounds, "exactBounds");
        if (!(exactBounds.width() > 0.0) || !(exactBounds.height() > 0.0)) {
            throw new IllegalArgumentException(
                    "Axis-aligned raster bounds must have positive area");
        }
        return new RasterGridPlacement(Kind.AXIS_ALIGNED, exactBounds, null);
    }

    /**
     * Creates an affine pixel-center placement.
     *
     * @param transform pixel-center-to-map transform
     * @return affine placement
     * @throws NullPointerException if {@code transform} is {@code null}
     */
    public static RasterGridPlacement affine(RasterAffineTransform transform) {
        return new RasterGridPlacement(
                Kind.AFFINE, null, Objects.requireNonNull(transform, "transform"));
    }

    /**
     * Returns the representation kind.
     *
     * @return representation kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns exact outer bounds only for {@link Kind#AXIS_ALIGNED}.
     *
     * @return axis-aligned bounds, when this is an axis placement
     */
    public Optional<Envelope> axisAlignedBounds() {
        return Optional.ofNullable(axisAlignedBounds);
    }

    /**
     * Returns the pixel-center transform only for {@link Kind#AFFINE}.
     *
     * @return affine transform, when this is an affine placement
     */
    public Optional<RasterAffineTransform> affineTransform() {
        return Optional.ofNullable(affineTransform);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RasterGridPlacement that
                && kind == that.kind
                && Objects.equals(axisAlignedBounds, that.axisAlignedBounds)
                && Objects.equals(affineTransform, that.affineTransform);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, axisAlignedBounds, affineTransform);
    }

    @Override
    public String toString() {
        return kind == Kind.AXIS_ALIGNED
                ? "RasterGridPlacement[kind=AXIS_ALIGNED, axisAlignedBounds="
                        + axisAlignedBounds
                        + ']'
                : "RasterGridPlacement[kind=AFFINE, affineTransform=" + affineTransform + ']';
    }
}
