package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.VectorExportSnapshot;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Canonical static SVG 1.1 encoding for detached vector-export snapshots.
 *
 * <p>The input is an immutable, toolkit-neutral picture: this class never retains a live map view,
 * source, cursor, registry, AWT value, callback, stream, or output path. The closed profile
 * supports the six vector geometry families, built-in vector marker/line/fill/composite symbols,
 * endpoint markers, bounded hatches, and already measured point labels. Raster/elevation content,
 * raster icons, legacy/custom symbols, interaction overlays, metadata, arbitrary SVG fragments, and
 * image fallback are rejected before a result is published.
 *
 * <p>Encoding is deterministic UTF-8 with a closed element/attribute grammar, stable traversal
 * order, canonical finite-double tokens, bounded chunks, and no partial returned document. {@link
 * SvgExportLimits} bounds serialization work. Failures expose stable {@link SvgExportProblem}
 * values through {@link SvgExportException}; cancellation is all-or-nothing. Atomic file output
 * first completes encoding, then writes one unpredictable same-directory temporary, forces and
 * closes it, and replaces the target with an atomic move. There is no non-atomic fallback, in-place
 * write, pre-delete, backup, or ownership/permission-copy claim.
 */
public final class SvgMapExports {
    private SvgMapExports() {}

    /**
     * Encodes a snapshot with default limits and no cancellation.
     *
     * @param snapshot detached vector picture
     * @return fresh canonical UTF-8 SVG bytes
     */
    public static byte[] encode(VectorExportSnapshot snapshot) {
        return encode(snapshot, SvgExportLimits.defaults(), CancellationToken.none());
    }

    /**
     * Encodes a snapshot with explicit limits and no cancellation.
     *
     * @param snapshot detached vector picture
     * @param limits serialization limits
     * @return fresh canonical UTF-8 SVG bytes
     */
    public static byte[] encode(VectorExportSnapshot snapshot, SvgExportLimits limits) {
        return encode(snapshot, limits, CancellationToken.none());
    }

    /**
     * Encodes a complete canonical UTF-8 SVG document or returns no bytes on failure.
     *
     * @param snapshot detached vector picture
     * @param limits serialization limits
     * @param cancellation cancellation signal
     * @return fresh canonical UTF-8 SVG bytes
     */
    public static byte[] encode(
            VectorExportSnapshot snapshot, SvgExportLimits limits, CancellationToken cancellation) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        return new SvgExportWriter(snapshot, limits, cancellation).encode();
    }

    /**
     * Atomically writes a snapshot with default limits and no cancellation.
     *
     * @param target local output path
     * @param snapshot detached vector picture
     */
    public static void writeAtomically(Path target, VectorExportSnapshot snapshot) {
        writeAtomically(target, snapshot, SvgExportLimits.defaults(), CancellationToken.none());
    }

    /**
     * Atomically writes a snapshot with explicit limits and no cancellation.
     *
     * @param target local output path
     * @param snapshot detached vector picture
     * @param limits serialization limits
     */
    public static void writeAtomically(
            Path target, VectorExportSnapshot snapshot, SvgExportLimits limits) {
        writeAtomically(target, snapshot, limits, CancellationToken.none());
    }

    /**
     * Materializes valid bytes, then writes and atomically replaces the target.
     *
     * @param target local output path
     * @param snapshot detached vector picture
     * @param limits serialization limits
     * @param cancellation cancellation signal
     */
    public static void writeAtomically(
            Path target,
            VectorExportSnapshot snapshot,
            SvgExportLimits limits,
            CancellationToken cancellation) {
        Objects.requireNonNull(target, "target");
        byte[] bytes = encode(snapshot, limits, cancellation);
        SvgAtomicFiles.write(target, bytes, cancellation);
    }
}
