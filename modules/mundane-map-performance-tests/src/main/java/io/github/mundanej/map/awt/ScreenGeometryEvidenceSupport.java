package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.core.CrsRegistry;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

/** Explicit non-published bridge for paired performance evidence. */
public final class ScreenGeometryEvidenceSupport {
    private ScreenGeometryEvidenceSupport() {}

    /** Creates an evidence-only map view in the selected fixed implementation mode. */
    public static MapView view(
            CrsRegistry registry, CrsDefinition mapCrs, CrsDefinition displayCrs, boolean level1) {
        return new MapView(
                registry,
                mapCrs,
                displayCrs,
                SymbolRendererRegistry.builtIn(),
                level1
                        ? ScreenGeometryOptimizationMode.LEVEL1
                        : ScreenGeometryOptimizationMode.DISABLED);
    }

    /** Paints once and returns only call-local deterministic work facts. */
    public static PaintResult paint(MapView view, BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        try {
            ScreenGeometryPaintResult result = view.paintWithScreenGeometryResult(graphics);
            return new PaintResult(
                    result.inputCoordinates(),
                    result.projectedCoordinates(),
                    result.renderCoordinates(),
                    result.lineFragments(),
                    result.culledPaths(),
                    result.fallbackPlans(),
                    result.retainedRenderGeometryBytes());
        } finally {
            graphics.dispose();
        }
    }

    /** Non-published immutable result consumed by the performance harness. */
    public record PaintResult(
            long inputCoordinates,
            long projectedCoordinates,
            long renderCoordinates,
            long lineFragments,
            long culledPaths,
            long fallbackPlans,
            long retainedRenderGeometryBytes) {
        /** Adds two sequential paint calls with checked arithmetic. */
        public PaintResult plus(PaintResult other) {
            return new PaintResult(
                    Math.addExact(inputCoordinates, other.inputCoordinates),
                    Math.addExact(projectedCoordinates, other.projectedCoordinates),
                    Math.addExact(renderCoordinates, other.renderCoordinates),
                    Math.addExact(lineFragments, other.lineFragments),
                    Math.addExact(culledPaths, other.culledPaths),
                    Math.addExact(fallbackPlans, other.fallbackPlans),
                    Math.addExact(retainedRenderGeometryBytes, other.retainedRenderGeometryBytes));
        }
    }
}
