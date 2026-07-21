package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.Symbol;
import java.util.Objects;

/**
 * Open feature source and its three role-correct resolved symbols.
 *
 * @param definition persisted feature-layer definition
 * @param source session-owned feature source
 * @param marker resolved marker-role symbol
 * @param line resolved line-role symbol
 * @param fill resolved fill-role symbol
 */
public record OpenedWorkspaceFeatureLayer(
        WorkspaceFeatureLayer definition,
        FeatureSource source,
        Symbol marker,
        Symbol line,
        Symbol fill)
        implements OpenedWorkspaceLayer {
    /** Validates the resolved source and exact symbol roles. */
    public OpenedWorkspaceFeatureLayer {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(source, "source");
        WorkspaceSymbols.requireRole(marker, io.github.mundanej.map.api.SymbolRole.MARKER);
        WorkspaceSymbols.requireRole(line, io.github.mundanej.map.api.SymbolRole.LINE);
        WorkspaceSymbols.requireRole(fill, io.github.mundanej.map.api.SymbolRole.FILL);
    }
}
