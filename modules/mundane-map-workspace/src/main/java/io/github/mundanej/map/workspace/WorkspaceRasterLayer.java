package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.RasterInterpolation;
import java.util.Objects;

/**
 * Immutable local raster-layer definition.
 *
 * @param id stable layer identity
 * @param name layer display name
 * @param source portable raster-source reference
 * @param interpolation nearest or bilinear sampling
 * @param opacity finite opacity in {@code [0,1]}
 */
public record WorkspaceRasterLayer(
        String id,
        String name,
        WorkspaceSourceReference source,
        RasterInterpolation interpolation,
        double opacity)
        implements WorkspaceLayerDefinition {
    /** Validates bounded layer text and raster presentation values. */
    public WorkspaceRasterLayer {
        id = WorkspaceText.text(id, "id", 256, true);
        name = WorkspaceText.text(name, "name", 256, false);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(interpolation, "interpolation");
        opacity = WorkspaceText.unitInterval(opacity, "opacity");
    }
}
