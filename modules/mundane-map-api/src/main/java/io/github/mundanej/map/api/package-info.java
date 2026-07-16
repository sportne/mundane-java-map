/**
 * Toolkit-neutral public contracts for geometry, symbols, interaction, coordinate reference
 * systems, bounded feature/raster/elevation sources, and structured diagnostics.
 *
 * <p>Public values are immutable and defensively copy mutable arrays and collections. Geometry and
 * source coordinates use explicit x/y order in their declared CRS; logical screen coordinates use
 * x-right/y-down units independent of device scale. Sources, cursors, and raster reads document
 * their ownership, close, cancellation, and limit behavior at the declaring type. Stable diagnostic
 * codes, severity, location, and ordered context are contractual; message and cause text are for
 * humans and debugging.
 *
 * <p>Toolkit and format implementations use explicit caller-owned registries and factories. This
 * package performs no reflection, classpath scanning, automatic provider discovery, or AWT
 * conversion.
 */
package io.github.mundanej.map.api;
