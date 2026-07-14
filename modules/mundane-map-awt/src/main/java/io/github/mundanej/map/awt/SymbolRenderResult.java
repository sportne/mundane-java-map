package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Envelope;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable result of one registered symbol renderer invocation.
 *
 * <p>Marker renderers return nominal screen bounds, including when fully transparent. Line and fill
 * renderers return {@link #none()}.
 */
public final class SymbolRenderResult {
    private static final SymbolRenderResult NONE =
            new SymbolRenderResult(Optional.empty(), AwtLogicalPaintPresence.UNKNOWN);
    private final Optional<Envelope> nominalMarkerBounds;
    private final AwtLogicalPaintPresence paintPresence;

    private SymbolRenderResult(
            Optional<Envelope> nominalMarkerBounds, AwtLogicalPaintPresence paintPresence) {
        this.nominalMarkerBounds = nominalMarkerBounds;
        this.paintPresence = paintPresence;
    }

    /** Returns the shared result for line/fill output without marker layout bounds. */
    public static SymbolRenderResult none() {
        return NONE;
    }

    /** Creates a line/fill result with explicit logical source-paint presence. */
    public static SymbolRenderResult none(AwtLogicalPaintPresence paintPresence) {
        return new SymbolRenderResult(
                Optional.empty(), Objects.requireNonNull(paintPresence, "paintPresence"));
    }

    /** Creates a marker result with required nominal screen bounds. */
    public static SymbolRenderResult markerBounds(Envelope bounds) {
        return new SymbolRenderResult(
                Optional.of(Objects.requireNonNull(bounds, "bounds")),
                AwtLogicalPaintPresence.UNKNOWN);
    }

    /** Creates a marker result with explicit logical source-paint presence. */
    public static SymbolRenderResult markerBounds(
            Envelope bounds, AwtLogicalPaintPresence paintPresence) {
        return new SymbolRenderResult(
                Optional.of(Objects.requireNonNull(bounds, "bounds")),
                Objects.requireNonNull(paintPresence, "paintPresence"));
    }

    /** Returns the optional nominal marker layout bounds. */
    public Optional<Envelope> nominalMarkerBounds() {
        return nominalMarkerBounds;
    }

    /** Returns renderer-reported logical source-paint presence. */
    public AwtLogicalPaintPresence paintPresence() {
        return paintPresence;
    }

    /** Returns a marker-bound union, treating {@link #none()} as an identity. */
    public SymbolRenderResult union(SymbolRenderResult other) {
        Objects.requireNonNull(other, "other");
        if (nominalMarkerBounds.isEmpty()) {
            return new SymbolRenderResult(
                    other.nominalMarkerBounds, unionPresence(paintPresence, other.paintPresence));
        }
        if (other.nominalMarkerBounds.isEmpty()) {
            return new SymbolRenderResult(
                    nominalMarkerBounds, unionPresence(paintPresence, other.paintPresence));
        }
        return markerBounds(
                nominalMarkerBounds.orElseThrow().union(other.nominalMarkerBounds.orElseThrow()),
                unionPresence(paintPresence, other.paintPresence));
    }

    private static AwtLogicalPaintPresence unionPresence(
            AwtLogicalPaintPresence first, AwtLogicalPaintPresence second) {
        if (first == AwtLogicalPaintPresence.PRESENT || second == AwtLogicalPaintPresence.PRESENT) {
            return AwtLogicalPaintPresence.PRESENT;
        }
        if (first == AwtLogicalPaintPresence.UNKNOWN || second == AwtLogicalPaintPresence.UNKNOWN) {
            return AwtLogicalPaintPresence.UNKNOWN;
        }
        return AwtLogicalPaintPresence.EMPTY;
    }
}
