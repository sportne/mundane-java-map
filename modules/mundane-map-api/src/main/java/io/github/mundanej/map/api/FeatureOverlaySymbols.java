package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable role-complete symbol bundle for one interaction overlay.
 *
 * @param marker marker-role symbol used for point features
 * @param line line-role symbol used for linear features
 * @param fill fill-role symbol used for polygon features
 */
@SuppressWarnings("deprecation")
public record FeatureOverlaySymbols(MarkerSymbol marker, LineSymbol line, FillSymbol fill) {
    private static final Envelope UNIT_BOX = new Envelope(-1.0, -1.0, 1.0, 1.0);

    /** Validates the three exact role-specific symbol contracts. */
    public FeatureOverlaySymbols {
        requireRole(marker, SymbolRole.MARKER, "marker");
        requireRole(line, SymbolRole.LINE, "line");
        requireRole(fill, SymbolRole.FILL, "fill");
    }

    /**
     * Returns the source-listed translucent amber hover treatment.
     *
     * @return immutable default hover symbols
     */
    public static FeatureOverlaySymbols defaultHover() {
        return defaults(diamondPath(), 18.0, 4.0, 7.0, new Rgba(255, 170, 0, 176));
    }

    /**
     * Returns the source-listed opaque blue selection treatment.
     *
     * @return immutable default selection symbols
     */
    public static FeatureOverlaySymbols defaultSelection() {
        return defaults(circlePath(), 14.0, 2.0, 3.0, new Rgba(0, 102, 204, 255));
    }

    private static FeatureOverlaySymbols defaults(
            VectorPath path,
            double markerSize,
            double markerStrokeWidth,
            double geometryStrokeWidth,
            Rgba color) {
        SymbolStroke markerStroke =
                new SymbolStroke(
                        color, new SymbolLength(markerStrokeWidth, SymbolUnit.SCREEN_PIXEL));
        VectorMarkerSymbol marker =
                VectorMarkerSymbol.of(
                        path,
                        UNIT_BOX,
                        Rgba.TRANSPARENT,
                        Optional.of(markerStroke),
                        MarkerPlacement.centeredScreen(markerSize),
                        1.0);
        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                color,
                                new SymbolLength(geometryStrokeWidth, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        SolidFillSymbol fill = SolidFillSymbol.of(Rgba.TRANSPARENT, Optional.of(line), 1.0);
        return new FeatureOverlaySymbols(marker, line, fill);
    }

    private static VectorPath diamondPath() {
        return VectorPath.builder()
                .moveTo(0.0, -1.0)
                .lineTo(1.0, 0.0)
                .lineTo(0.0, 1.0)
                .lineTo(-1.0, 0.0)
                .close()
                .build();
    }

    private static VectorPath circlePath() {
        double control = 0.5522847498307936;
        return VectorPath.builder()
                .moveTo(1.0, 0.0)
                .cubicTo(1.0, control, control, 1.0, 0.0, 1.0)
                .cubicTo(-control, 1.0, -1.0, control, -1.0, 0.0)
                .cubicTo(-1.0, -control, -control, -1.0, 0.0, -1.0)
                .cubicTo(control, -1.0, 1.0, -control, 1.0, 0.0)
                .close()
                .build();
    }

    private static void requireRole(Symbol symbol, SymbolRole role, String name) {
        Objects.requireNonNull(symbol, name);
        if (symbol instanceof FeatureStyle || symbol.role() != role) {
            throw new IllegalArgumentException(name + " must have " + role + " role");
        }
    }
}
