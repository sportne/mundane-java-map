package io.github.mundanej.map.workspace;

import io.github.mundanej.map.core.CrsRegistry;
import java.util.Objects;

/**
 * Explicit immutable registries used to open one workspace transaction.
 *
 * @param crsRegistry exact CRS registry
 * @param sources exact local source-opener registry
 * @param catalogs exact external symbol-catalog registry
 */
public record WorkspaceOpenContext(
        CrsRegistry crsRegistry,
        WorkspaceSourceRegistry sources,
        WorkspaceSymbolCatalogRegistry catalogs) {
    /** Validates required instance-owned registries. */
    public WorkspaceOpenContext {
        Objects.requireNonNull(crsRegistry, "crsRegistry");
        Objects.requireNonNull(sources, "sources");
        Objects.requireNonNull(catalogs, "catalogs");
    }
}
