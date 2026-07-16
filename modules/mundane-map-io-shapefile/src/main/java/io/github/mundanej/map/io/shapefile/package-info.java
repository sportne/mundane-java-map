/**
 * Bounded, read-only ESRI Shapefile access through the toolkit-neutral map source contracts.
 *
 * <p>{@link io.github.mundanej.map.io.shapefile.Shapefiles} is the public entry point. Callers own
 * each opened source and must close it. The reader supports the documented Level 1 two-dimensional
 * SHP/SHX/DBF/CPG/PRJ profile, applies explicit allocation and record limits, and reports malformed
 * or unsupported input through stable structured diagnostics. It performs no classpath scanning,
 * CRS guessing, or AWT conversion.
 */
package io.github.mundanej.map.io.shapefile;
