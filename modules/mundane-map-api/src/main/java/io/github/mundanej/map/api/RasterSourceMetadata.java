package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** Immutable raster dimensions, placement, and coordinate-reference metadata. */
public final class RasterSourceMetadata {
    private final SourceIdentity identity;
    private final int width;
    private final int height;
    private final Optional<Envelope> mapBounds;
    private final Optional<RasterGridPlacement> gridPlacement;
    private final Optional<CrsMetadata> crs;

    /**
     * Creates compatible unplaced or exact axis-aligned metadata.
     *
     * <p>A present {@code mapBounds} becomes the authoritative axis-aligned grid placement. Use
     * {@link #withPlacement(SourceIdentity, int, int, RasterGridPlacement, Optional)} for affine
     * placement so the represented envelope is derived rather than duplicated by callers.
     *
     * @param identity stable source identity
     * @param width positive grid-column count
     * @param height positive grid-row count
     * @param mapBounds optional exact axis-aligned outer bounds
     * @param crs optional declared CRS metadata
     */
    public RasterSourceMetadata(
            SourceIdentity identity,
            int width,
            int height,
            Optional<Envelope> mapBounds,
            Optional<CrsMetadata> crs) {
        this(
                identity,
                width,
                height,
                requireBounds(mapBounds),
                mapBounds.map(RasterGridPlacement::axisAligned),
                crs);
    }

    private RasterSourceMetadata(
            SourceIdentity identity,
            int width,
            int height,
            Optional<Envelope> mapBounds,
            Optional<RasterGridPlacement> gridPlacement,
            Optional<CrsMetadata> crs) {
        this.identity = Objects.requireNonNull(identity, "identity");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Raster dimensions must be positive");
        }
        this.width = width;
        this.height = height;
        this.mapBounds = Objects.requireNonNull(mapBounds, "mapBounds");
        this.gridPlacement = Objects.requireNonNull(gridPlacement, "gridPlacement");
        this.crs = Objects.requireNonNull(crs, "crs");
    }

    /**
     * Creates placed metadata and derives its represented map envelope from the placement.
     *
     * @param identity stable source identity
     * @param width positive grid-column count
     * @param height positive grid-row count
     * @param placement authoritative grid placement
     * @param crs optional declared CRS metadata
     * @return placed metadata with a derived conservative map envelope
     * @throws NullPointerException if {@code identity}, {@code placement}, or {@code crs} is {@code
     *     null}
     * @throws IllegalArgumentException if either grid dimension is not positive
     * @throws RasterPlacementException if an affine corner, edge, or envelope is non-finite, or if
     *     the represented affine footprint or envelope does not have positive area
     */
    public static RasterSourceMetadata withPlacement(
            SourceIdentity identity,
            int width,
            int height,
            RasterGridPlacement placement,
            Optional<CrsMetadata> crs) {
        Objects.requireNonNull(placement, "placement");
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Raster dimensions must be positive");
        }
        Envelope bounds = representedBounds(width, height, placement);
        return new RasterSourceMetadata(
                identity, width, height, Optional.of(bounds), Optional.of(placement), crs);
    }

    /**
     * Returns the stable source identity.
     *
     * @return stable source identity
     */
    public SourceIdentity identity() {
        return identity;
    }

    /**
     * Returns the number of raster columns.
     *
     * @return positive raster-column count
     */
    public int width() {
        return width;
    }

    /**
     * Returns the number of raster rows.
     *
     * @return positive raster-row count
     */
    public int height() {
        return height;
    }

    /**
     * Returns the conservative represented map envelope, when placed.
     *
     * @return represented map envelope, when placed
     */
    public Optional<Envelope> mapBounds() {
        return mapBounds;
    }

    /**
     * Returns the authoritative grid placement, when placed.
     *
     * @return authoritative grid placement, when placed
     */
    public Optional<RasterGridPlacement> gridPlacement() {
        return gridPlacement;
    }

    /**
     * Returns declared coordinate-reference metadata, when supplied.
     *
     * @return declared CRS metadata, when supplied
     */
    public Optional<CrsMetadata> crs() {
        return crs;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof RasterSourceMetadata that
                && width == that.width
                && height == that.height
                && identity.equals(that.identity)
                && mapBounds.equals(that.mapBounds)
                && gridPlacement.equals(that.gridPlacement)
                && crs.equals(that.crs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(identity, width, height, mapBounds, gridPlacement, crs);
    }

    @Override
    public String toString() {
        return "RasterSourceMetadata[identity="
                + identity
                + ", width="
                + width
                + ", height="
                + height
                + ", mapBounds="
                + mapBounds
                + ", gridPlacement="
                + gridPlacement
                + ", crs="
                + crs
                + ']';
    }

    private static Optional<Envelope> requireBounds(Optional<Envelope> mapBounds) {
        Objects.requireNonNull(mapBounds, "mapBounds");
        mapBounds.ifPresent(
                bounds -> {
                    if (!(bounds.width() > 0.0) || !(bounds.height() > 0.0)) {
                        throw new IllegalArgumentException(
                                "Raster map bounds must have positive area");
                    }
                });
        return mapBounds;
    }

    private static Envelope representedBounds(
            int width, int height, RasterGridPlacement placement) {
        if (placement.kind() == RasterGridPlacement.Kind.AXIS_ALIGNED) {
            return placement.axisAlignedBounds().orElseThrow();
        }
        RasterAffineTransform transform = placement.affineTransform().orElseThrow();
        Coordinate northWest;
        Coordinate northEast;
        Coordinate southEast;
        Coordinate southWest;
        try {
            northWest = transform.gridToMap(-0.5, -0.5);
            northEast = transform.gridToMap(width - 0.5, -0.5);
            southEast = transform.gridToMap(width - 0.5, height - 0.5);
            southWest = transform.gridToMap(-0.5, height - 0.5);
        } catch (ArithmeticException exception) {
            throw new RasterPlacementException(
                    RasterPlacementException.Reason.CORNER_NON_FINITE, exception);
        }
        requireRepresentedFootprint(northWest, northEast, southWest);
        double minX =
                Math.min(
                        Math.min(northWest.x(), northEast.x()),
                        Math.min(southEast.x(), southWest.x()));
        double minY =
                Math.min(
                        Math.min(northWest.y(), northEast.y()),
                        Math.min(southEast.y(), southWest.y()));
        double maxX =
                Math.max(
                        Math.max(northWest.x(), northEast.x()),
                        Math.max(southEast.x(), southWest.x()));
        double maxY =
                Math.max(
                        Math.max(northWest.y(), northEast.y()),
                        Math.max(southEast.y(), southWest.y()));
        double spanX = maxX - minX;
        double spanY = maxY - minY;
        if (!Double.isFinite(spanX) || !Double.isFinite(spanY)) {
            throw new RasterPlacementException(RasterPlacementException.Reason.ENVELOPE_NON_FINITE);
        }
        if (!(spanX > 0.0) || !(spanY > 0.0)) {
            throw new RasterPlacementException(
                    RasterPlacementException.Reason.ENVELOPE_NON_POSITIVE);
        }
        return new Envelope(minX, minY, maxX, maxY);
    }

    private static void requireRepresentedFootprint(
            Coordinate northWest, Coordinate northEast, Coordinate southWest) {
        double horizontalX = northEast.x() - northWest.x();
        double horizontalY = northEast.y() - northWest.y();
        double verticalX = southWest.x() - northWest.x();
        double verticalY = southWest.y() - northWest.y();
        if (!Double.isFinite(horizontalX)
                || !Double.isFinite(horizontalY)
                || !Double.isFinite(verticalX)
                || !Double.isFinite(verticalY)) {
            throw new RasterPlacementException(RasterPlacementException.Reason.ENVELOPE_NON_FINITE);
        }
        double scale =
                Math.max(
                        Math.max(Math.abs(horizontalX), Math.abs(horizontalY)),
                        Math.max(Math.abs(verticalX), Math.abs(verticalY)));
        if (!(scale > 0.0)) {
            throw new RasterPlacementException(
                    RasterPlacementException.Reason.ENVELOPE_NON_POSITIVE);
        }
        double determinant =
                Math.fma(
                        horizontalX / scale,
                        verticalY / scale,
                        -(horizontalY / scale) * (verticalX / scale));
        if (!Double.isFinite(determinant)) {
            throw new RasterPlacementException(RasterPlacementException.Reason.ENVELOPE_NON_FINITE);
        }
        if (determinant == 0.0) {
            throw new RasterPlacementException(
                    RasterPlacementException.Reason.ENVELOPE_NON_POSITIVE);
        }
    }
}
