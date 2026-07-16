package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Immutable marker size, anchor, offset, and clockwise rotation policy.
 *
 * @param size marker width, height, and unit
 * @param anchor point on the marker aligned to its geometry position
 * @param offsetX finite horizontal offset expressed in the size unit
 * @param offsetY finite vertical offset expressed in the size unit
 * @param rotationDegrees clockwise rotation normalized to {@code [0, 360)} degrees
 * @param rotationMode whether rotation is screen-relative or map-relative
 */
public record MarkerPlacement(
        SymbolSize size,
        SymbolAnchor anchor,
        double offsetX,
        double offsetY,
        double rotationDegrees,
        SymbolRotationMode rotationMode) {
    /** Creates and canonicalizes a marker placement. */
    public MarkerPlacement {
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(anchor, "anchor");
        Objects.requireNonNull(rotationMode, "rotationMode");
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY)) {
            throw new IllegalArgumentException("offsets must be finite");
        }
        if (!Double.isFinite(rotationDegrees)) {
            throw new IllegalArgumentException("rotationDegrees must be finite");
        }
        offsetX = canonicalZero(offsetX);
        offsetY = canonicalZero(offsetY);
        rotationDegrees = normalizeDegrees(rotationDegrees);
    }

    /**
     * Creates a centered square screen-pixel placement with no offset or rotation.
     *
     * @param sizePixels positive width and height in logical screen pixels
     * @return immutable centered placement
     */
    public static MarkerPlacement centeredScreen(double sizePixels) {
        return new MarkerPlacement(
                SymbolSize.square(sizePixels, SymbolUnit.SCREEN_PIXEL),
                SymbolAnchor.CENTER,
                0.0,
                0.0,
                0.0,
                SymbolRotationMode.SCREEN_RELATIVE);
    }

    private static double normalizeDegrees(double value) {
        double normalized = value % 360.0;
        if (normalized < 0.0) {
            normalized += 360.0;
        }
        return canonicalZero(normalized);
    }

    private static double canonicalZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }
}
