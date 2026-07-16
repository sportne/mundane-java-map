package io.github.mundanej.map.awt;

/** Package-private immutable work facts returned only by the performance evidence seam. */
record ScreenGeometryPaintResult(
        long inputCoordinates,
        long projectedCoordinates,
        long renderCoordinates,
        long lineFragments,
        long culledPaths,
        long fallbackPlans,
        long retainedRenderGeometryBytes) {}
