package io.github.mundanej.map.core;

/** Stable outcome of one screen-geometry optimization attempt. */
public enum ScreenGeometryOptimizationOutcome {
    /** Authoritative geometry is already the rendering geometry. */
    UNCHANGED,
    /** A clipped or simplified rendering geometry was produced. */
    OPTIMIZED,
    /** No centerline or filled path intersects the expanded screen clip. */
    PATH_CULLED,
    /** A budget, topology, or numeric safety rule retained authoritative geometry. */
    FALLBACK
}
