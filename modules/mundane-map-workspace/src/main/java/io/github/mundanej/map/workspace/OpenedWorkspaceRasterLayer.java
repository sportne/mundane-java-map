package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.RasterSource;
import java.util.Objects;

/**
 * Open raster source with its persisted presentation definition.
 *
 * @param definition persisted raster-layer definition
 * @param source session-owned raster source
 */
public record OpenedWorkspaceRasterLayer(WorkspaceRasterLayer definition, RasterSource source)
        implements OpenedWorkspaceLayer {
    /** Validates required immutable definition and source. */
    public OpenedWorkspaceRasterLayer {
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(source, "source");
    }
}
