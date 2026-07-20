package io.github.mundanej.map.workspace;

import java.util.Objects;

/**
 * Immutable local feature-layer definition.
 *
 * @param id stable layer identity
 * @param name layer display name
 * @param source portable feature-source reference
 * @param symbols exact external catalog symbol references
 */
public record WorkspaceFeatureLayer(
        String id, String name, WorkspaceSourceReference source, WorkspaceSymbolReferences symbols)
        implements WorkspaceLayerDefinition {
    /** Validates bounded layer text and required values. */
    public WorkspaceFeatureLayer {
        id = WorkspaceText.text(id, "id", 256, true);
        name = WorkspaceText.text(name, "name", 256, false);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(symbols, "symbols");
    }
}
