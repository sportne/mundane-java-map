package io.github.mundanej.map.api;

import java.util.Objects;
import java.util.Optional;

/** An immutable toolkit-neutral vector marker with explicit placement and optional stroke. */
public final class VectorMarkerSymbol implements MarkerSymbol {
    /** The explicit built-in vector-marker renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.vector-marker");

    private final VectorPath path;
    private final Envelope viewBox;
    private final Rgba fill;
    private final Optional<SymbolStroke> stroke;
    private final MarkerPlacement placement;
    private final double opacity;

    private VectorMarkerSymbol(
            VectorPath path,
            Envelope viewBox,
            Rgba fill,
            Optional<SymbolStroke> stroke,
            MarkerPlacement placement,
            double opacity) {
        this.path = Objects.requireNonNull(path, "path");
        this.viewBox = Objects.requireNonNull(viewBox, "viewBox");
        this.fill = Objects.requireNonNull(fill, "fill");
        this.stroke = Objects.requireNonNull(stroke, "stroke");
        this.placement = Objects.requireNonNull(placement, "placement");
        double viewBoxWidth = viewBox.width();
        double viewBoxHeight = viewBox.height();
        if (!Double.isFinite(viewBoxWidth)
                || !Double.isFinite(viewBoxHeight)
                || viewBoxWidth <= 0.0
                || viewBoxHeight <= 0.0) {
            throw new IllegalArgumentException(
                    "viewBox must have finite positive width and height");
        }
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("opacity must be finite and between zero and one");
        }
        Envelope pathEnvelope = path.coordinateEnvelope();
        if (pathEnvelope.minX() < viewBox.minX()
                || pathEnvelope.minY() < viewBox.minY()
                || pathEnvelope.maxX() > viewBox.maxX()
                || pathEnvelope.maxY() > viewBox.maxY()) {
            throw new IllegalArgumentException("path coordinates must remain inside viewBox");
        }
        requireClosedSubpaths(path);
        this.opacity = opacity == 0.0 ? 0.0 : opacity;
    }

    /** Creates a vector marker with explicit optional stroke and placement. */
    public static VectorMarkerSymbol of(
            VectorPath path,
            Envelope viewBox,
            Rgba fill,
            Optional<SymbolStroke> stroke,
            MarkerPlacement placement,
            double opacity) {
        return new VectorMarkerSymbol(path, viewBox, fill, stroke, placement, opacity);
    }

    /** Creates a centered, square, fill-only marker measured in logical screen pixels. */
    public static VectorMarkerSymbol filledScreen(
            VectorPath path, Envelope viewBox, Rgba fill, double screenSizePixels, double opacity) {
        return of(
                path,
                viewBox,
                fill,
                Optional.empty(),
                MarkerPlacement.centeredScreen(screenSizePixels),
                opacity);
    }

    /** Returns the immutable vector path. */
    public VectorPath path() {
        return path;
    }

    /** Returns the finite positive-area path coordinate bounds mapped to the marker rectangle. */
    public Envelope viewBox() {
        return viewBox;
    }

    /** Returns the marker fill color. */
    public Rgba fill() {
        return fill;
    }

    /** Returns the optional round marker stroke. */
    public Optional<SymbolStroke> stroke() {
        return stroke;
    }

    /** Returns the marker placement. */
    public MarkerPlacement placement() {
        return placement;
    }

    @Override
    public double opacity() {
        return opacity;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    private static void requireClosedSubpaths(VectorPath path) {
        boolean open = false;
        for (int index = 0; index < path.commandCount(); index++) {
            VectorPathCommand command = path.commandAt(index);
            if (command == VectorPathCommand.MOVE_TO) {
                if (open) {
                    throw new IllegalArgumentException("marker path subpaths must be closed");
                }
                open = true;
            } else if (command == VectorPathCommand.CLOSE) {
                open = false;
            }
        }
        if (open) {
            throw new IllegalArgumentException("marker path subpaths must be closed");
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VectorMarkerSymbol symbol
                && path.equals(symbol.path)
                && viewBox.equals(symbol.viewBox)
                && fill.equals(symbol.fill)
                && stroke.equals(symbol.stroke)
                && placement.equals(symbol.placement)
                && Double.compare(opacity, symbol.opacity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(path, viewBox, fill, stroke, placement, opacity);
    }

    @Override
    public String toString() {
        return "VectorMarkerSymbol{path="
                + path
                + ", viewBox="
                + viewBox
                + ", fill="
                + fill
                + ", stroke="
                + stroke
                + ", placement="
                + placement
                + ", opacity="
                + opacity
                + '}';
    }
}
