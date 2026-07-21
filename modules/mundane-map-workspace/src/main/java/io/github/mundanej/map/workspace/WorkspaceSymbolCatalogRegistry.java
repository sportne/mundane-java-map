package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.NamedSymbolCatalog;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Instance-owned immutable registry of exact application symbol catalogs. */
public final class WorkspaceSymbolCatalogRegistry {
    private final Map<String, NamedSymbolCatalog> catalogs;

    private WorkspaceSymbolCatalogRegistry(Map<String, NamedSymbolCatalog> catalogs) {
        this.catalogs = Map.copyOf(catalogs);
    }

    /**
     * Returns a new single-use explicit catalog builder.
     *
     * @return empty builder
     */
    public static Builder builder() {
        return new Builder();
    }

    NamedSymbolCatalog find(String id) {
        return catalogs.get(id);
    }

    /** Single-use symbol-catalog registry builder. */
    public static final class Builder {
        private final Map<String, NamedSymbolCatalog> catalogs = new LinkedHashMap<>();
        private boolean consumed;

        private Builder() {}

        /**
         * Registers one immutable exact catalog.
         *
         * @param id exact dotted lowercase catalog ID
         * @param catalog immutable named catalog
         * @return this builder
         */
        public Builder register(String id, NamedSymbolCatalog catalog) {
            requireUsable();
            String checked = WorkspaceText.openerId(id);
            if (catalogs.putIfAbsent(checked, Objects.requireNonNull(catalog, "catalog")) != null) {
                throw new IllegalArgumentException("symbol catalog ID is already registered");
            }
            return this;
        }

        /**
         * Builds the immutable registry and consumes this builder.
         *
         * @return immutable registry
         */
        public WorkspaceSymbolCatalogRegistry build() {
            requireUsable();
            consumed = true;
            return new WorkspaceSymbolCatalogRegistry(catalogs);
        }

        private void requireUsable() {
            if (consumed) {
                throw new IllegalStateException("catalog registry builder is already consumed");
            }
        }
    }
}
