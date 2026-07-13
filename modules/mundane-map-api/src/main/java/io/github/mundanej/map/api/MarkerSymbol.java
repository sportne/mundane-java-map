package io.github.mundanej.map.api;

/** A toolkit-neutral symbol that portrays point geometry. */
public interface MarkerSymbol extends Symbol {
    @Override
    default SymbolRole role() {
        return SymbolRole.MARKER;
    }
}
