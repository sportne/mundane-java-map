package io.github.mundanej.map.api;

/** Toolkit-neutral raster sampling modes. */
public enum RasterInterpolation {
    /** Select the nearest source pixel without blending. */
    NEAREST,
    /** Blend the nearest four source pixels bilinearly. */
    BILINEAR
}
