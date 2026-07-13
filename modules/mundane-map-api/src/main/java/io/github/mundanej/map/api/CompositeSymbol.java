package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;

/** An immutable ordered, role-homogeneous symbol composition. */
@SuppressWarnings("deprecation")
public final class CompositeSymbol implements Symbol {
    /** The explicit built-in composite renderer key. */
    public static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.composite");

    private final List<Symbol> children;
    private final double opacity;
    private final SymbolRole role;

    private CompositeSymbol(List<? extends Symbol> children, double opacity) {
        Objects.requireNonNull(children, "children");
        if (children.isEmpty()) {
            throw new IllegalArgumentException("children must not be empty");
        }
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException("opacity must be finite and between zero and one");
        }
        this.children = List.copyOf(children);
        SymbolRole inferred = null;
        for (Symbol child : this.children) {
            Objects.requireNonNull(child, "child");
            SymbolRole childRole = requireSupportedRole(child);
            if (inferred == null) {
                inferred = childRole;
            } else if (inferred != childRole) {
                throw new IllegalArgumentException("children must have one symbol role");
            }
        }
        this.role = inferred;
        this.opacity = opacity == 0.0 ? 0.0 : opacity;
    }

    /** Creates an ordered, non-empty composition. */
    public static CompositeSymbol of(List<? extends Symbol> children, double opacity) {
        return new CompositeSymbol(children, opacity);
    }

    /** Returns the immutable child list in bottom-to-top paint order. */
    public List<Symbol> children() {
        return children;
    }

    @Override
    public double opacity() {
        return opacity;
    }

    @Override
    public SymbolRole role() {
        return role;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    private static SymbolRole requireSupportedRole(Symbol symbol) {
        SymbolRole declaredRole = symbol.role();
        if (symbol instanceof FeatureStyle || declaredRole == SymbolRole.LEGACY_GEOMETRY) {
            throw new IllegalArgumentException("legacy symbols cannot be composed");
        }
        if (symbol instanceof CompositeSymbol composite) {
            return composite.role();
        }
        int interfaceCount =
                (symbol instanceof MarkerSymbol ? 1 : 0)
                        + (symbol instanceof LineSymbol ? 1 : 0)
                        + (symbol instanceof FillSymbol ? 1 : 0);
        if (interfaceCount != 1 || declaredRole == null) {
            throw new IllegalArgumentException("child must implement exactly one symbol role");
        }
        SymbolRole expected =
                symbol instanceof MarkerSymbol
                        ? SymbolRole.MARKER
                        : symbol instanceof LineSymbol ? SymbolRole.LINE : SymbolRole.FILL;
        if (declaredRole != expected) {
            throw new IllegalArgumentException("child symbol role does not match its contract");
        }
        Objects.requireNonNull(symbol.rendererKey(), "child.rendererKey");
        double childOpacity = symbol.opacity();
        if (!Double.isFinite(childOpacity) || childOpacity < 0.0 || childOpacity > 1.0) {
            throw new IllegalArgumentException(
                    "child opacity must be finite and between zero and one");
        }
        return expected;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CompositeSymbol composite
                && children.equals(composite.children)
                && Double.compare(opacity, composite.opacity) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(children, opacity);
    }

    @Override
    public String toString() {
        return "CompositeSymbol{children=" + children + ", opacity=" + opacity + '}';
    }
}
