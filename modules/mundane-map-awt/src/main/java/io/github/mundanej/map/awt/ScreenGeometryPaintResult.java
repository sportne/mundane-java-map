package io.github.mundanej.map.awt;

/** Package-private immutable work facts returned only by the performance evidence seam. */
record ScreenGeometryPaintResult(
        long inputCoordinates,
        long projectedCoordinates,
        long renderCoordinates,
        long lineFragments,
        long culledPaths,
        long fallbackPlans,
        long retainedRenderGeometryBytes,
        RenderCachePaintMetrics cacheMetrics) {
    ScreenGeometryPaintResult(
            long inputCoordinates,
            long projectedCoordinates,
            long renderCoordinates,
            long lineFragments,
            long culledPaths,
            long fallbackPlans,
            long retainedRenderGeometryBytes) {
        this(
                inputCoordinates,
                projectedCoordinates,
                renderCoordinates,
                lineFragments,
                culledPaths,
                fallbackPlans,
                retainedRenderGeometryBytes,
                RenderCachePaintMetrics.empty());
    }
}
