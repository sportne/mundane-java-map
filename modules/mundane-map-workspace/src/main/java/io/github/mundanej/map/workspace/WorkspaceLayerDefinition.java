package io.github.mundanej.map.workspace;

/** Closed immutable workspace layer definition. */
public sealed interface WorkspaceLayerDefinition
        permits WorkspaceFeatureLayer, WorkspaceRasterLayer {
    /**
     * Returns the stable layer identity.
     *
     * @return exact non-blank layer identity
     */
    String id();

    /**
     * Returns the display name.
     *
     * @return bounded display name, possibly empty
     */
    String name();

    /**
     * Returns the portable source reference.
     *
     * @return immutable source reference
     */
    WorkspaceSourceReference source();
}
