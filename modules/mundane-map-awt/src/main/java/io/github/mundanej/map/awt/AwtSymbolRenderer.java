package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Symbol;

/**
 * An explicitly registered AWT renderer for one symbol role/key slot.
 *
 * <p>Implementations are called only during a paint operation. They must not retain the supplied
 * context or graphics copies beyond {@link #render(Symbol, AwtSymbolRenderContext)}. A renderer is
 * responsible for applying its symbol's opacity after the context's inherited opacity.
 */
public interface AwtSymbolRenderer {
    /**
     * Returns whether this renderer supports the non-null value shape.
     *
     * <p>This check must be deterministic, side-effect-free, and non-throwing for every non-null
     * symbol. Returning false produces a stable renderer-value-mismatch diagnostic.
     */
    boolean supports(Symbol value);

    /**
     * Renders a supported value in the paint-call-scoped context.
     *
     * <p>A marker renderer must return nominal marker bounds, while a line or fill renderer must
     * return {@link SymbolRenderResult#none()}. Every graphics copy obtained from the context must
     * be disposed by the implementation. Returning null or the wrong result shape produces a stable
     * invalid-result diagnostic.
     */
    SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context);

    /**
     * Tests the supported value's logical painted footprint.
     *
     * <p>The default keeps custom renderers deterministically non-hittable until they explicitly
     * opt in. Implementations must honor the component clip exposed by the callback-scoped context.
     */
    default boolean hitTest(Symbol value, AwtSymbolHitContext context) {
        return false;
    }
}
