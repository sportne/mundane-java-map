package io.github.mundanej.map.workspace;

/** Closed descriptor for one source owned by an opened workspace session. */
public sealed interface OpenedWorkspaceLayer
        permits OpenedWorkspaceFeatureLayer, OpenedWorkspaceRasterLayer {
    /**
     * Returns the immutable persisted layer definition.
     *
     * @return persisted layer definition
     */
    WorkspaceLayerDefinition definition();
}
