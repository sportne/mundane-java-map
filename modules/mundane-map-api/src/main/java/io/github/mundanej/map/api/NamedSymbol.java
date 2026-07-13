package io.github.mundanej.map.api;

import java.util.Objects;

/** An immutable exact catalog name and symbol pair. */
@SuppressWarnings("deprecation")
public record NamedSymbol(String name, Symbol symbol) {
    /** Creates a named non-legacy symbol. */
    public NamedSymbol {
        name = requireName(name);
        Objects.requireNonNull(symbol, "symbol");
        if (symbol instanceof FeatureStyle || symbol.role() == SymbolRole.LEGACY_GEOMETRY) {
            throw new IllegalArgumentException("symbol must not use the legacy role");
        }
        SymbolRole expected;
        if (symbol instanceof CompositeSymbol composite) {
            expected = composite.role();
        } else {
            int interfaceCount =
                    (symbol instanceof MarkerSymbol ? 1 : 0)
                            + (symbol instanceof LineSymbol ? 1 : 0)
                            + (symbol instanceof FillSymbol ? 1 : 0);
            if (interfaceCount != 1) {
                throw new IllegalArgumentException("symbol must implement exactly one symbol role");
            }
            expected =
                    symbol instanceof MarkerSymbol
                            ? SymbolRole.MARKER
                            : symbol instanceof LineSymbol ? SymbolRole.LINE : SymbolRole.FILL;
        }
        if (symbol.role() != expected) {
            throw new IllegalArgumentException("symbol role does not match its contract");
        }
        Objects.requireNonNull(symbol.rendererKey(), "symbol.rendererKey");
        double opacity = symbol.opacity();
        if (!Double.isFinite(opacity) || opacity < 0.0 || opacity > 1.0) {
            throw new IllegalArgumentException(
                    "symbol opacity must be finite and between zero and one");
        }
    }

    static String requireName(String value) {
        Objects.requireNonNull(value, "name");
        if (value.isBlank() || !value.equals(value.strip())) {
            throw new IllegalArgumentException(
                    "name must be non-blank and have no edge whitespace");
        }
        return value;
    }
}
