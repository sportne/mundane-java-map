package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Bounded linear interpolation between compatible endpoint symbols.
 *
 * <p>Attribute input requires an exact attribute name. Zoom input requires an explicit zoom level
 * in {@link PortrayalEvaluationContext}. Missing, null, non-numeric, or incompatible runtime input
 * selects the fixed fallback.
 */
public final class InterpolatedSymbolSelector implements SymbolSelector {
    /** Maximum number of interpolation stops. */
    public static final int MAXIMUM_STOPS = 64;

    private final InterpolationInput input;
    private final Optional<String> attribute;
    private final List<InterpolatedSymbolStop> stops;
    private final Symbol fallback;
    private final SymbolRole role;
    private final AttributeValueConversion conversion;

    private InterpolatedSymbolSelector(
            InterpolationInput input,
            Optional<String> attribute,
            List<InterpolatedSymbolStop> stops,
            Symbol fallback,
            AttributeValueConversion conversion) {
        this.input = Objects.requireNonNull(input, "input");
        Objects.requireNonNull(attribute, "attribute");
        this.attribute = attribute.map(AttributeValues::requireName);
        this.stops = List.copyOf(Objects.requireNonNull(stops, "stops"));
        this.fallback = Objects.requireNonNull(fallback, "fallback");
        this.conversion = Objects.requireNonNull(conversion, "conversion");
        if ((input == InterpolationInput.ATTRIBUTE) != this.attribute.isPresent()) {
            throw new IllegalArgumentException(
                    "attribute must be present only for attribute input");
        }
        if (this.stops.size() < 2 || this.stops.size() > MAXIMUM_STOPS) {
            throw new IllegalArgumentException("stops must contain between 2 and 64 entries");
        }
        InterpolatedSymbolStop previous = null;
        for (InterpolatedSymbolStop stop : this.stops) {
            Objects.requireNonNull(stop, "stop");
            if (previous != null && previous.input().compareTo(stop.input()) >= 0) {
                throw new IllegalArgumentException("stop inputs must be strictly increasing");
            }
            if (stop.symbol().role() != fallback.role()) {
                throw new IllegalArgumentException("endpoint and fallback roles must match");
            }
            previous = stop;
        }
        FixedSymbolSelector.requireVectorRole(fallback.role());
        this.role = fallback.role();
    }

    /**
     * Creates an attribute-driven selector.
     *
     * @param attribute exact canonical attribute name
     * @param stops increasing endpoint stops
     * @param fallback symbol used for invalid runtime input
     * @return immutable selector
     */
    public static InterpolatedSymbolSelector attribute(
            String attribute, List<InterpolatedSymbolStop> stops, Symbol fallback) {
        return new InterpolatedSymbolSelector(
                InterpolationInput.ATTRIBUTE,
                Optional.of(attribute),
                stops,
                fallback,
                AttributeValueConversion.IDENTITY);
    }

    /**
     * Creates a converted attribute-driven selector.
     *
     * @param attribute exact canonical attribute name
     * @param stops increasing endpoint stops
     * @param fallback symbol used for invalid runtime input
     * @param conversion closed input conversion
     * @return immutable selector
     */
    public static InterpolatedSymbolSelector expressionInput(
            String attribute,
            List<InterpolatedSymbolStop> stops,
            Symbol fallback,
            AttributeValueConversion conversion) {
        return new InterpolatedSymbolSelector(
                InterpolationInput.ATTRIBUTE, Optional.of(attribute), stops, fallback, conversion);
    }

    /**
     * Creates a zoom-driven selector.
     *
     * @param stops increasing endpoint stops
     * @param fallback symbol used without zoom context
     * @return immutable selector
     */
    public static InterpolatedSymbolSelector zoom(
            List<InterpolatedSymbolStop> stops, Symbol fallback) {
        return new InterpolatedSymbolSelector(
                InterpolationInput.ZOOM,
                Optional.empty(),
                stops,
                fallback,
                AttributeValueConversion.IDENTITY);
    }

    /**
     * Returns the interpolation input kind.
     *
     * @return input kind
     */
    public InterpolationInput input() {
        return input;
    }

    /**
     * Returns the exact attribute for attribute input.
     *
     * @return exact attribute for attribute input
     */
    public Optional<String> attribute() {
        return attribute;
    }

    /**
     * Returns the immutable increasing endpoint stops.
     *
     * @return immutable increasing endpoint stops
     */
    public List<InterpolatedSymbolStop> stops() {
        return stops;
    }

    /**
     * Returns the invalid-input fallback.
     *
     * @return invalid-input fallback
     */
    public Symbol fallback() {
        return fallback;
    }

    /**
     * Returns the input conversion.
     *
     * @return input conversion
     */
    public AttributeValueConversion conversion() {
        return conversion;
    }

    @Override
    public SymbolRole role() {
        return role;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof InterpolatedSymbolSelector selector
                && input == selector.input
                && attribute.equals(selector.attribute)
                && stops.equals(selector.stops)
                && fallback.equals(selector.fallback)
                && conversion.equals(selector.conversion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(input, attribute, stops, fallback, conversion);
    }
}
