package io.github.mundanej.map.api;

/** A toolkit-neutral symbol that portrays polygon geometry. */
public interface FillSymbol extends Symbol {
    @Override
    default SymbolRole role() {
        return SymbolRole.FILL;
    }
}
