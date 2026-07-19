package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable closed selectors for the marker, line, and fill roles of one vector binding. */
public final class FeaturePortrayal {
    private final Optional<SymbolSelector> marker;
    private final Optional<SymbolSelector> line;
    private final Optional<SymbolSelector> fill;
    private final Optional<PointLabelProfile> pointLabel;

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
        this(marker, line, fill, Optional.empty());
    }

    /**
     * Creates a portrayal with at least one role selector and an optional singular-point label.
     *
     * @param marker optional marker-role selector
     * @param line optional line-role selector
     * @param fill optional fill-role selector
     * @param pointLabel optional point-label profile, which requires a marker selector
     */
    public FeaturePortrayal(
            Optional<? extends SymbolSelector> marker,
            Optional<? extends SymbolSelector> line,
            Optional<? extends SymbolSelector> fill,
            Optional<PointLabelProfile> pointLabel) {
        this.marker = copy(marker, SymbolRole.MARKER, "marker");
        this.line = copy(line, SymbolRole.LINE, "line");
        this.fill = copy(fill, SymbolRole.FILL, "fill");
        Objects.requireNonNull(pointLabel, "pointLabel");
        this.pointLabel = pointLabel.map(Objects::requireNonNull);
        if (this.marker.isEmpty() && this.line.isEmpty() && this.fill.isEmpty()) {
            throw new IllegalArgumentException("portrayal requires at least one selector");
        }
        if (this.pointLabel.isPresent() && this.marker.isEmpty()) {
            throw new IllegalArgumentException("pointLabel requires a marker selector");
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
     * Returns the optional singular-point label profile.
     *
     * @return optional immutable profile
     */
    public Optional<PointLabelProfile> pointLabel() {
        return pointLabel;
    }

    /**
     * Returns an equal-role portrayal with the supplied point-label profile.
     *
     * @param profile non-null singular-point profile
     * @return new immutable portrayal
     */
    public FeaturePortrayal withPointLabel(PointLabelProfile profile) {
        return new FeaturePortrayal(marker, line, fill, Optional.of(profile));
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
                && fill.equals(portrayal.fill)
                && pointLabel.equals(portrayal.pointLabel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(marker, line, fill, pointLabel);
    }

    @Override
    public String toString() {
        return "FeaturePortrayal[marker="
                + marker
                + ", line="
                + line
                + ", fill="
                + fill
                + ", pointLabel="
                + pointLabel
                + ']';
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
