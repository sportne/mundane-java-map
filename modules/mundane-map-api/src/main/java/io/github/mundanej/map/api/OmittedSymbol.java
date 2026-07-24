package io.github.mundanej.map.api;

import java.util.Objects;

/**
 * Internal-facing, renderer-free selector result that explicitly omits one geometry role.
 *
 * <p>Resolvers remove this sentinel before paint, hit testing, export, renderer preflight, or
 * public resolved portrayal output. It exists so bounded selectors can represent valid branch
 * values such as zero line width without inventing a degenerate renderer value.
 */
public final class OmittedSymbol implements Symbol {
    private static final SymbolRendererKey KEY =
            new SymbolRendererKey("io.github.mundanej.map.symbol.omitted");

    private final SymbolRole role;

    private OmittedSymbol(SymbolRole role) {
        this.role = Objects.requireNonNull(role, "role");
        FixedSymbolSelector.requireVectorRole(role);
    }

    /**
     * Creates an omission for one vector role.
     *
     * @param role marker, line, or fill role
     * @return immutable omission
     */
    public static OmittedSymbol of(SymbolRole role) {
        return new OmittedSymbol(role);
    }

    @Override
    public SymbolRole role() {
        return role;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return KEY;
    }

    @Override
    public double opacity() {
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof OmittedSymbol omitted && role == omitted.role;
    }

    @Override
    public int hashCode() {
        return role.hashCode();
    }
}
