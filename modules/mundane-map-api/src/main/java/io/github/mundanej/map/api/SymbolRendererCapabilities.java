package io.github.mundanej.map.api;

/** Renderer extension contract requiring an explicit decision for every symbol value. */
@FunctionalInterface
public interface SymbolRendererCapabilities {
    /**
     * Returns the exact acceptance, named approximation, or stable rejection decision.
     *
     * @param symbol immutable symbol construct
     * @return explicit capability decision
     */
    RendererCapability capability(Symbol symbol);
}
