package io.github.mundanej.map.api;

/** A toolkit-neutral symbol that portrays line geometry. */
public interface LineSymbol extends Symbol {
    @Override
    default SymbolRole role() {
        return SymbolRole.LINE;
    }
}
