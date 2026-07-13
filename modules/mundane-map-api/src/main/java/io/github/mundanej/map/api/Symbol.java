package io.github.mundanej.map.api;

/** An immutable, toolkit-neutral feature portrayal. */
public interface Symbol {
    /** Returns the geometry role fulfilled by this value. */
    SymbolRole role();

    /** Returns the explicit renderer lookup key. */
    SymbolRendererKey rendererKey();

    /** Returns this symbol's opacity in the inclusive range from zero through one. */
    double opacity();
}
