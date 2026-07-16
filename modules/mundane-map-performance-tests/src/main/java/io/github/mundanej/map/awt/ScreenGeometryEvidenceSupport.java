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
        return view(registry, mapCrs, displayCrs, level1, false);
    }

    /** Creates an evidence view with the evidence-retained vector-template cache selected. */
    public static MapView view(
            CrsRegistry registry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            boolean level1,
            boolean vectorTemplateCache) {
        AwtRenderCacheMode cacheMode =
                vectorTemplateCache
                        ? AwtRenderCacheMode.VECTOR_TEMPLATE
                        : AwtRenderCacheMode.DISABLED;
        return new MapView(
                registry,
                mapCrs,
                displayCrs,
                SymbolRendererRegistry.builtIn(),
                level1
                        ? ScreenGeometryOptimizationMode.LEVEL1
                        : ScreenGeometryOptimizationMode.DISABLED,
                cacheMode);
    }

    /** Clears the retained vector-template partition before a cold sample. */
    public static void clearVectorTemplateCache(MapView view) {
        view.clearVectorTemplateCacheForEvidence();
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
                    result.retainedRenderGeometryBytes(),
                    CacheFacts.from(result.cacheMetrics()));
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
            long retainedRenderGeometryBytes,
            CacheFacts cacheFacts) {
        /** Adds two sequential paint calls with checked arithmetic. */
        public PaintResult plus(PaintResult other) {
            return new PaintResult(
                    Math.addExact(inputCoordinates, other.inputCoordinates),
                    Math.addExact(projectedCoordinates, other.projectedCoordinates),
                    Math.addExact(renderCoordinates, other.renderCoordinates),
                    Math.addExact(lineFragments, other.lineFragments),
                    Math.addExact(culledPaths, other.culledPaths),
                    Math.addExact(fallbackPlans, other.fallbackPlans),
                    Math.addExact(retainedRenderGeometryBytes, other.retainedRenderGeometryBytes),
                    cacheFacts.plus(other.cacheFacts));
        }
    }

    /** Public only inside the non-published performance fixture source set. */
    public record CacheFacts(PartitionFacts vectorTemplate) {
        static CacheFacts from(RenderCachePaintMetrics metrics) {
            return new CacheFacts(PartitionFacts.from(metrics.vectorTemplate()));
        }

        /** Returns the empty operation-local fact set. */
        public static CacheFacts empty() {
            return new CacheFacts(PartitionFacts.empty());
        }

        CacheFacts plus(CacheFacts other) {
            return new CacheFacts(vectorTemplate.plus(other.vectorTemplate));
        }
    }

    /** Exact facts for one typed private cache partition. */
    public record PartitionFacts(
            long requests,
            long hits,
            long misses,
            long builds,
            long admissions,
            long evictions,
            long bypasses,
            long buildUnits,
            long currentEntries,
            long currentLogicalBytes,
            long peakEntries,
            long peakLogicalBytes) {
        static PartitionFacts from(CachePartitionMetrics metrics) {
            return new PartitionFacts(
                    metrics.requests(),
                    metrics.hits(),
                    metrics.misses(),
                    metrics.builds(),
                    metrics.admissions(),
                    metrics.evictions(),
                    metrics.bypasses(),
                    metrics.buildUnits(),
                    metrics.currentEntries(),
                    metrics.currentLogicalBytes(),
                    metrics.peakEntries(),
                    metrics.peakLogicalBytes());
        }

        static PartitionFacts empty() {
            return new PartitionFacts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        PartitionFacts plus(PartitionFacts other) {
            return new PartitionFacts(
                    Math.addExact(requests, other.requests),
                    Math.addExact(hits, other.hits),
                    Math.addExact(misses, other.misses),
                    Math.addExact(builds, other.builds),
                    Math.addExact(admissions, other.admissions),
                    Math.addExact(evictions, other.evictions),
                    Math.addExact(bypasses, other.bypasses),
                    Math.addExact(buildUnits, other.buildUnits),
                    other.currentEntries,
                    other.currentLogicalBytes,
                    Math.max(peakEntries, other.peakEntries),
                    Math.max(peakLogicalBytes, other.peakLogicalBytes));
        }
    }
}
