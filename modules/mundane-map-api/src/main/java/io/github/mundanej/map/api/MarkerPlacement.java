package io.github.mundanej.map.api;

import java.util.Objects;

/** Immutable marker size, anchor, offset, and clockwise rotation policy. */
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

    /** Creates a centered square screen-pixel placement with no offset or rotation. */
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
