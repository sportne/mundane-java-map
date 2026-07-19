package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable closed selectors for the marker, line, and fill roles of one vector binding. */
public final class FeaturePortrayal {
    private final Optional<SymbolSelector> marker;
    private final Optional<SymbolSelector> line;
    private final Optional<SymbolSelector> fill;

    /**
     * Creates a portrayal with at least one role selector.
     *
     * @param marker optional marker-role selector
     * @param line optional line-role selector
     * @param fill optional fill-role selector
     */
    public FeaturePortrayal(
            Optional<? extends SymbolSelector> marker,
            Optional<? extends SymbolSelector> line,
            Optional<? extends SymbolSelector> fill) {
        this.marker = copy(marker, SymbolRole.MARKER, "marker");
        this.line = copy(line, SymbolRole.LINE, "line");
        this.fill = copy(fill, SymbolRole.FILL, "fill");
        if (this.marker.isEmpty() && this.line.isEmpty() && this.fill.isEmpty()) {
            throw new IllegalArgumentException("portrayal requires at least one selector");
        }
    }

    /**
     * Returns a fixed three-role portrayal compatible with existing source bindings.
     *
     * @param marker marker-role symbol
     * @param line line-role symbol
     * @param fill fill-role symbol
     * @return immutable fixed portrayal
     */
    public static FeaturePortrayal fixed(Symbol marker, Symbol line, Symbol fill) {
        return new FeaturePortrayal(
                Optional.of(new FixedSymbolSelector(marker)),
                Optional.of(new FixedSymbolSelector(line)),
                Optional.of(new FixedSymbolSelector(fill)));
    }

    /**
     * Returns a portrayal containing only one marker selector.
     *
     * @param marker marker-role selector
     * @return immutable marker-only portrayal
     */
    public static FeaturePortrayal markers(SymbolSelector marker) {
        return new FeaturePortrayal(Optional.of(marker), Optional.empty(), Optional.empty());
    }

    /**
     * Returns the optional marker selector.
     *
     * @return optional marker-role selector
     */
    public Optional<SymbolSelector> marker() {
        return marker;
    }

    /**
     * Returns the optional line selector.
     *
     * @return optional line-role selector
     */
    public Optional<SymbolSelector> line() {
        return line;
    }

    /**
     * Returns the optional fill selector.
     *
     * @return optional fill-role selector
     */
    public Optional<SymbolSelector> fill() {
        return fill;
    }

    /**
     * Returns present selectors in marker, line, fill order.
     *
     * @return immutable ordered selector list
     */
    public List<SymbolSelector> selectors() {
        java.util.ArrayList<SymbolSelector> result = new java.util.ArrayList<>(3);
        marker.ifPresent(result::add);
        line.ifPresent(result::add);
        fill.ifPresent(result::add);
        return List.copyOf(result);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof FeaturePortrayal portrayal
                && marker.equals(portrayal.marker)
                && line.equals(portrayal.line)
                && fill.equals(portrayal.fill);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marker, line, fill);
    }

    @Override
    public String toString() {
        return "FeaturePortrayal[marker=" + marker + ", line=" + line + ", fill=" + fill + ']';
    }

    private static Optional<SymbolSelector> copy(
            Optional<? extends SymbolSelector> selector, SymbolRole role, String field) {
        Objects.requireNonNull(selector, field);
        Optional<SymbolSelector> copied = selector.map(Objects::requireNonNull);
        if (copied.isPresent() && copied.orElseThrow().role() != role) {
            throw new IllegalArgumentException(field + " selector must have role " + role);
        }
        return copied;
    }
}
