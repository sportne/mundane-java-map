package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Repeated marker graphic used as a fill or stroke paint.
 *
 * @param graphic marker-role graphic
 * @param size positive graphic size
 * @param gap positive repeat gap
 * @param rotationDegrees finite clockwise rotation
 * @param opacity finite opacity from zero through one
 */
public record GraphicPaint(
        Symbol graphic, SymbolSize size, SymbolLength gap, double rotationDegrees, double opacity) {
    /** Creates and validates a repeated graphic paint. */
    public GraphicPaint {
        Objects.requireNonNull(graphic, "graphic");
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(gap, "gap");
        if (graphic.role() != SymbolRole.MARKER) {
            throw new IllegalArgumentException("graphic must have marker role");
        }
        Objects.requireNonNull(graphic.rendererKey(), "graphic.rendererKey");
        if (!Double.isFinite(rotationDegrees)) {
            throw new IllegalArgumentException("rotationDegrees must be finite");
        }
        if (!Double.isFinite(opacity) || opacity < 0 || opacity > 1) {
            throw new IllegalArgumentException("opacity must be between zero and one");
        }
        rotationDegrees = rotationDegrees == 0.0 ? 0.0 : rotationDegrees;
        opacity = opacity == 0.0 ? 0.0 : opacity;
    }
}
