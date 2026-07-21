package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import java.util.Objects;

final class WorkspaceSymbols {
    private WorkspaceSymbols() {}

    static Symbol requireRole(Symbol symbol, SymbolRole role) {
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.role() != role) {
            throw new IllegalArgumentException("symbol does not have the required workspace role");
        }
        return symbol;
    }
}
