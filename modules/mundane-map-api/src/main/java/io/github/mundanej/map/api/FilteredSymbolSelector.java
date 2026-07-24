package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * One closed predicate guard around another immutable selector.
 *
 * <p>A false guard omits the role without evaluating the delegate. The wrapper permits format
 * adapters to compose feature filters with fixed, categorical, graduated, interpolated, or rule
 * selection without callbacks.
 *
 * @param predicate bounded immutable guard
 * @param delegate immutable role-preserving selector
 */
public record FilteredSymbolSelector(PortrayalPredicate predicate, SymbolSelector delegate)
        implements SymbolSelector {
    /** Validates the guard and role-preserving delegate. */
    public FilteredSymbolSelector {
        Objects.requireNonNull(predicate, "predicate");
        Objects.requireNonNull(delegate, "delegate");
        if (delegate instanceof FilteredSymbolSelector) {
            throw new IllegalArgumentException("filtered selectors must not be nested");
        }
        if (PortrayalPredicateBounds.validate(predicate) > PortrayalPredicateBounds.MAXIMUM_NODES) {
            throw new IllegalArgumentException("predicate exceeds its node limit");
        }
    }

    @Override
    public SymbolRole role() {
        return delegate.role();
    }
}
