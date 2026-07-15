package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Envelope;
import java.util.Objects;
import java.util.Optional;

/** Immutable instruction for locating an encoded image in map coordinates. */
public final class ImagePlacement {
    /** Supported placement instructions. */
    public enum Kind {
        /** No map placement. */
        UNPLACED,
        /** Caller-supplied axis-aligned map bounds. */
        AXIS_ALIGNED,
        /** Explicitly requested direct-sibling world file. */
        WORLD_FILE
    }

    private final Kind kind;
    private final Optional<Envelope> mapBounds;
    private final Optional<CrsMetadata> crs;

    /**
     * Retains the G6-001 constructor for unplaced or axis-aligned placement.
     *
     * @param mapBounds optional positive-area map bounds
     * @param crs optional CRS, permitted only with bounds
     */
    public ImagePlacement(Optional<Envelope> mapBounds, Optional<CrsMetadata> crs) {
        this(mapBounds.isPresent() ? Kind.AXIS_ALIGNED : Kind.UNPLACED, mapBounds, crs);
    }

    private ImagePlacement(Kind kind, Optional<Envelope> mapBounds, Optional<CrsMetadata> crs) {
        this.kind = Objects.requireNonNull(kind, "kind");
        this.mapBounds = Objects.requireNonNull(mapBounds, "mapBounds");
        this.crs = Objects.requireNonNull(crs, "crs");
        if (kind == Kind.AXIS_ALIGNED) {
            Envelope bounds = mapBounds.orElseThrow();
            if (bounds.width() <= 0 || bounds.height() <= 0) {
                throw new IllegalArgumentException("Image bounds must have positive area");
            }
        } else if (mapBounds.isPresent()) {
            throw new IllegalArgumentException("Only axis-aligned placement has explicit bounds");
        }
        if (kind == Kind.UNPLACED && crs.isPresent()) {
            throw new IllegalArgumentException("An unplaced image cannot declare a CRS");
        }
    }

    /**
     * Returns an image with no map placement.
     *
     * @return unplaced instruction
     */
    public static ImagePlacement unplaced() {
        return new ImagePlacement(Kind.UNPLACED, Optional.empty(), Optional.empty());
    }

    /**
     * Creates an axis-aligned placement with optional CRS metadata.
     *
     * @param bounds positive-area map bounds
     * @param crs optional declared CRS metadata
     * @return axis-aligned instruction
     * @throws NullPointerException if {@code bounds} or {@code crs} is {@code null}
     * @throws IllegalArgumentException if {@code bounds} does not have positive area
     */
    public static ImagePlacement axisAligned(Envelope bounds, Optional<CrsMetadata> crs) {
        return new ImagePlacement(
                Kind.AXIS_ALIGNED,
                Optional.of(Objects.requireNonNull(bounds, "bounds")),
                Objects.requireNonNull(crs, "crs"));
    }

    /**
     * Creates an axis-aligned placement with declared CRS metadata.
     *
     * @param bounds positive-area map bounds
     * @param crs declared CRS metadata
     * @return axis-aligned instruction
     * @throws NullPointerException if {@code bounds} or {@code crs} is {@code null}
     * @throws IllegalArgumentException if {@code bounds} does not have positive area
     */
    public static ImagePlacement axisAligned(Envelope bounds, CrsMetadata crs) {
        return axisAligned(bounds, Optional.of(Objects.requireNonNull(crs, "crs")));
    }

    /**
     * Explicitly requests world-file placement without declaring a CRS.
     *
     * @return world-file instruction without CRS metadata
     */
    public static ImagePlacement worldFile() {
        return worldFile(Optional.empty());
    }

    /**
     * Explicitly requests world-file placement with optional caller-declared CRS metadata.
     *
     * @param crs optional caller-declared CRS metadata
     * @return world-file instruction
     * @throws NullPointerException if {@code crs} is {@code null}
     */
    public static ImagePlacement worldFile(Optional<CrsMetadata> crs) {
        return new ImagePlacement(
                Kind.WORLD_FILE, Optional.empty(), Objects.requireNonNull(crs, "crs"));
    }

    /**
     * Explicitly requests world-file placement with caller-declared CRS metadata.
     *
     * @param crs caller-declared CRS metadata
     * @return world-file instruction
     * @throws NullPointerException if {@code crs} is {@code null}
     */
    public static ImagePlacement worldFile(CrsMetadata crs) {
        return worldFile(Optional.of(Objects.requireNonNull(crs, "crs")));
    }

    /**
     * Returns the instruction kind.
     *
     * @return instruction kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns explicit bounds only for axis-aligned placement.
     *
     * @return explicit axis-aligned bounds, when applicable
     */
    public Optional<Envelope> mapBounds() {
        return mapBounds;
    }

    /**
     * Returns caller-declared CRS metadata.
     *
     * @return caller-declared CRS metadata, when supplied
     */
    public Optional<CrsMetadata> crs() {
        return crs;
    }

    @Override
    public boolean equals(Object object) {
        return object instanceof ImagePlacement other
                && kind == other.kind
                && mapBounds.equals(other.mapBounds)
                && crs.equals(other.crs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, mapBounds, crs);
    }

    @Override
    public String toString() {
        return "ImagePlacement[kind=" + kind + ", mapBounds=" + mapBounds + ", crs=" + crs + ']';
    }
}
