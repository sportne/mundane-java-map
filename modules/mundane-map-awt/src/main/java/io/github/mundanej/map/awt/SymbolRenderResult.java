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
    private static final SymbolRenderResult NONE = new SymbolRenderResult(Optional.empty());
    private final Optional<Envelope> nominalMarkerBounds;

    private SymbolRenderResult(Optional<Envelope> nominalMarkerBounds) {
        this.nominalMarkerBounds = nominalMarkerBounds;
    }

    /** Returns the shared result for line/fill output without marker layout bounds. */
    public static SymbolRenderResult none() {
        return NONE;
    }

    /** Creates a marker result with required nominal screen bounds. */
    public static SymbolRenderResult markerBounds(Envelope bounds) {
        return new SymbolRenderResult(Optional.of(Objects.requireNonNull(bounds, "bounds")));
    }

    /** Returns the optional nominal marker layout bounds. */
    public Optional<Envelope> nominalMarkerBounds() {
        return nominalMarkerBounds;
    }

    /** Returns a marker-bound union, treating {@link #none()} as an identity. */
    public SymbolRenderResult union(SymbolRenderResult other) {
        Objects.requireNonNull(other, "other");
        if (nominalMarkerBounds.isEmpty()) {
            return other;
        }
        if (other.nominalMarkerBounds.isEmpty()) {
            return this;
        }
        return markerBounds(
                nominalMarkerBounds.orElseThrow().union(other.nominalMarkerBounds.orElseThrow()));
    }
}
