package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * One immutable ordered portrayal rule.
 *
 * @param name optional bounded diagnostic name
 * @param scale active scale interval
 * @param predicate optional ordinary predicate
 * @param elseRule whether this is an else rule
 * @param markers marker symbols in painter order
 * @param lines line symbols in painter order
 * @param fills fill symbols in painter order
 */
public record PortrayalRule(
        Optional<String> name,
        ScaleInterval scale,
        Optional<PortrayalPredicate> predicate,
        boolean elseRule,
        List<Symbol> markers,
        List<Symbol> lines,
        List<Symbol> fills) {
    /** Validates and defensively copies the rule. */
    public PortrayalRule {
        Objects.requireNonNull(name, "name");
        name =
                name.map(
                        value -> {
                            if (value.isBlank()
                                    || !value.equals(value.strip())
                                    || value.length() > 256) {
                                throw new IllegalArgumentException("name is outside bounds");
                            }
                            return value;
                        });
        Objects.requireNonNull(scale, "scale");
        Objects.requireNonNull(predicate, "predicate");
        if (elseRule && predicate.isPresent()) {
            throw new IllegalArgumentException("an else rule cannot have a predicate");
        }
        markers = symbols(markers, SymbolRole.MARKER, "markers");
        lines = symbols(lines, SymbolRole.LINE, "lines");
        fills = symbols(fills, SymbolRole.FILL, "fills");
        if (markers.isEmpty() && lines.isEmpty() && fills.isEmpty()) {
            throw new IllegalArgumentException("a rule requires at least one symbol");
        }
    }

    /**
     * Returns immutable marker symbols in painter order.
     *
     * @return immutable marker symbols
     */
    @Override
    public List<Symbol> markers() {
        return List.copyOf(markers);
    }

    /**
     * Returns immutable line symbols in painter order.
     *
     * @return immutable line symbols
     */
    @Override
    public List<Symbol> lines() {
        return List.copyOf(lines);
    }

    /**
     * Returns immutable fill symbols in painter order.
     *
     * @return immutable fill symbols
     */
    @Override
    public List<Symbol> fills() {
        return List.copyOf(fills);
    }

    private static List<Symbol> symbols(List<Symbol> values, SymbolRole role, String name) {
        List<Symbol> copy = List.copyOf(Objects.requireNonNull(values, name));
        if (copy.size() > 2_048) {
            throw new IllegalArgumentException(name + " exceeds structural bounds");
        }
        for (Symbol symbol : copy) {
            Objects.requireNonNull(symbol, "symbol");
            if (symbol.role() != role) {
                throw new IllegalArgumentException(name + " contains the wrong symbol role");
            }
        }
        return copy;
    }
}
