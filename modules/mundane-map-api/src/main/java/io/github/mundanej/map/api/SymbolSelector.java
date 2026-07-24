package io.github.mundanej.map.api;

/** Closed immutable selection of one symbol for a geometry role. */
public sealed interface SymbolSelector
        permits FixedSymbolSelector,
                FilteredSymbolSelector,
                CategoricalSymbolSelector,
                GraduatedSymbolSelector,
                InterpolatedSymbolSelector,
                RuleSymbolSelector {
    /**
     * Returns the one geometry role produced by this selector.
     *
     * @return marker, line, or fill role
     */
    SymbolRole role();
}
