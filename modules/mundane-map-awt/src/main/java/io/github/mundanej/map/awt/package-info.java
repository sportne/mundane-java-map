/**
 * Swing and Java2D integration for the toolkit-neutral map contracts.
 *
 * <p>{@link io.github.mundanej.map.awt.MapView} and the other Swing components are created and
 * mutated on the Swing event-dispatch thread unless a declaration explicitly says otherwise.
 * Immutable render options may be created on any thread, and {@link
 * io.github.mundanej.map.awt.MapLayerBinding} synchronizes its documented lifecycle and cooperative
 * cancellation operations. Owned bindings transfer source lifecycle to the view; borrowed bindings
 * do not.
 *
 * <p>Applications install symbol renderers and encoded-raster decoders through explicit,
 * instance-owned registries with exact keys and deterministic duplicate diagnostics. The module
 * does not discover application providers. {@link io.github.mundanej.map.awt.AwtRasterDecoders}
 * does intentionally query the JDK Image I/O registry, accepting only the bounded {@code
 * java.desktop} PNG and JPEG providers documented by that factory.
 */
package io.github.mundanej.map.awt;
