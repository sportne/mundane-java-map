package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Deterministic component interpolation for the closed Level 1 vector-symbol family. */
final class SymbolInterpolation {
    private SymbolInterpolation() {}

    static Symbol interpolate(Symbol lower, Symbol upper, double fraction) {
        if (lower.getClass() != upper.getClass()
                || lower.role() != upper.role()
                || lower.rendererKey().equals(upper.rendererKey()) == false) {
            throw new IllegalArgumentException("interpolation endpoints are incompatible");
        }
        if (lower instanceof SolidLineSymbol left) {
            SolidLineSymbol right = (SolidLineSymbol) upper;
            if (left.startMarker().isPresent()
                    || left.endMarker().isPresent()
                    || right.startMarker().isPresent()
                    || right.endMarker().isPresent()) {
                throw new IllegalArgumentException("endpoint markers cannot be interpolated");
            }
            return SolidLineSymbol.of(
                    stroke(left.stroke(), right.stroke(), fraction),
                    scalar(left.opacity(), right.opacity(), fraction));
        }
        if (lower instanceof SolidFillSymbol left) {
            SolidFillSymbol right = (SolidFillSymbol) upper;
            Optional<Symbol> outline = optional(left.outline(), right.outline(), fraction);
            return SolidFillSymbol.of(
                    color(left.fill(), right.fill(), fraction),
                    outline,
                    scalar(left.opacity(), right.opacity(), fraction));
        }
        if (lower instanceof VectorMarkerSymbol left) {
            VectorMarkerSymbol right = (VectorMarkerSymbol) upper;
            return VectorMarkerSymbol.of(
                    path(left.path(), right.path(), fraction),
                    envelope(left.viewBox(), right.viewBox(), fraction),
                    color(left.fill(), right.fill(), fraction),
                    optionalStroke(left.stroke(), right.stroke(), fraction),
                    placement(left.placement(), right.placement(), fraction),
                    scalar(left.opacity(), right.opacity(), fraction));
        }
        if (lower instanceof CompositeSymbol left) {
            CompositeSymbol right = (CompositeSymbol) upper;
            if (left.children().size() != right.children().size()) {
                throw new IllegalArgumentException("composite endpoint sizes must match");
            }
            List<Symbol> children = new ArrayList<>(left.children().size());
            for (int index = 0; index < left.children().size(); index++) {
                children.add(
                        interpolate(
                                left.children().get(index), right.children().get(index), fraction));
            }
            return CompositeSymbol.of(children, scalar(left.opacity(), right.opacity(), fraction));
        }
        throw new IllegalArgumentException("symbol type cannot be interpolated");
    }

    private static Optional<Symbol> optional(
            Optional<Symbol> lower, Optional<Symbol> upper, double fraction) {
        if (lower.isEmpty() != upper.isEmpty()) {
            throw new IllegalArgumentException("optional endpoint structures must match");
        }
        return lower.isEmpty()
                ? Optional.empty()
                : Optional.of(interpolate(lower.orElseThrow(), upper.orElseThrow(), fraction));
    }

    private static Optional<SymbolStroke> optionalStroke(
            Optional<SymbolStroke> lower, Optional<SymbolStroke> upper, double fraction) {
        if (lower.isEmpty() != upper.isEmpty()) {
            throw new IllegalArgumentException("optional stroke structures must match");
        }
        return lower.isEmpty()
                ? Optional.empty()
                : Optional.of(stroke(lower.orElseThrow(), upper.orElseThrow(), fraction));
    }

    private static SymbolStroke stroke(SymbolStroke lower, SymbolStroke upper, double fraction) {
        if (lower.width().unit() != upper.width().unit()) {
            throw new IllegalArgumentException("stroke units must match");
        }
        return new SymbolStroke(
                color(lower.color(), upper.color(), fraction),
                new SymbolLength(
                        scalar(lower.width().value(), upper.width().value(), fraction),
                        lower.width().unit()));
    }

    private static MarkerPlacement placement(
            MarkerPlacement lower, MarkerPlacement upper, double fraction) {
        if (lower.anchor() != upper.anchor()
                || lower.rotationMode() != upper.rotationMode()
                || lower.size().unit() != upper.size().unit()) {
            throw new IllegalArgumentException("marker placement modes must match");
        }
        return new MarkerPlacement(
                new SymbolSize(
                        scalar(lower.size().width(), upper.size().width(), fraction),
                        scalar(lower.size().height(), upper.size().height(), fraction),
                        lower.size().unit()),
                lower.anchor(),
                scalar(lower.offsetX(), upper.offsetX(), fraction),
                scalar(lower.offsetY(), upper.offsetY(), fraction),
                scalar(lower.rotationDegrees(), upper.rotationDegrees(), fraction),
                lower.rotationMode());
    }

    private static VectorPath path(VectorPath lower, VectorPath upper, double fraction) {
        if (lower.commandCount() != upper.commandCount()
                || lower.ordinateCount() != upper.ordinateCount()) {
            throw new IllegalArgumentException("vector path structures must match");
        }
        for (int index = 0; index < lower.commandCount(); index++) {
            if (lower.commandAt(index) != upper.commandAt(index)) {
                throw new IllegalArgumentException("vector path commands must match");
            }
        }
        double[] ordinates = new double[lower.ordinateCount()];
        for (int index = 0; index < ordinates.length; index++) {
            ordinates[index] = scalar(lower.ordinateAt(index), upper.ordinateAt(index), fraction);
        }
        return VectorPath.of(lower.toCommandArray(), ordinates);
    }

    private static Envelope envelope(Envelope lower, Envelope upper, double fraction) {
        return new Envelope(
                scalar(lower.minX(), upper.minX(), fraction),
                scalar(lower.minY(), upper.minY(), fraction),
                scalar(lower.maxX(), upper.maxX(), fraction),
                scalar(lower.maxY(), upper.maxY(), fraction));
    }

    private static Rgba color(Rgba lower, Rgba upper, double fraction) {
        return new Rgba(
                channel(lower.red(), upper.red(), fraction),
                channel(lower.green(), upper.green(), fraction),
                channel(lower.blue(), upper.blue(), fraction),
                channel(lower.alpha(), upper.alpha(), fraction));
    }

    private static int channel(int lower, int upper, double fraction) {
        return (int) StrictMath.round(scalar(lower, upper, fraction));
    }

    private static double scalar(double lower, double upper, double fraction) {
        return lower + (upper - lower) * fraction;
    }
}
