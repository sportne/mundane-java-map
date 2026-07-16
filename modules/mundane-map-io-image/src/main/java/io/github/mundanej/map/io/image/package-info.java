/**
 * Bounded toolkit-neutral PNG and JPEG raster sources.
 *
 * <p>{@link io.github.mundanej.map.io.image.RasterImages} opens caller-owned sources using explicit
 * limits, placement, cache policy, source identity, and encoded-raster decoder. Axis-aligned or
 * six-coefficient world-file placement is supported; a world file never supplies a CRS, so callers
 * must declare one. The module contains no AWT/ImageIO types, automatic decoder discovery, or
 * heuristic CRS selection. Close and cancellation release in-flight work according to the public
 * raster-source contracts, and malformed/untrusted input is reported with stable diagnostics.
 */
package io.github.mundanej.map.io.image;
