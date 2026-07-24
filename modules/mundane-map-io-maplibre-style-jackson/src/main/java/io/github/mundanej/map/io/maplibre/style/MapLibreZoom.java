package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.core.CrsDefinitions;
import java.util.Map;
import java.util.Objects;

/** Exact Web Mercator resolution-to-zoom boundary for MapLibre bindings. */
public final class MapLibreZoom {
    private static final double WORLD_WIDTH = 2.0 * StrictMath.PI * 6_378_137.0;

    private MapLibreZoom() {}

    /**
     * Derives fractional zoom from EPSG:3857 horizontal world units per logical pixel.
     *
     * @param displayCrs exact recognized display CRS
     * @param worldUnitsPerPixel finite positive horizontal resolution
     * @return finite, unclamped fractional zoom
     * @throws MapLibreBindException for a non-Web-Mercator context
     */
    public static double fromWebMercatorResolution(
            CrsDefinition displayCrs, double worldUnitsPerPixel) {
        Objects.requireNonNull(displayCrs, "displayCrs");
        if (!CrsDefinitions.EPSG_3857.equals(displayCrs)) {
            throw new MapLibreBindException(
                    new MapLibreProblem(
                            "MAPLIBRE_ZOOM_CONTEXT_UNSUPPORTED",
                            "bind",
                            "/zoom",
                            Map.of("reason", "crs")));
        }
        if (!Double.isFinite(worldUnitsPerPixel) || worldUnitsPerPixel <= 0.0) {
            throw new IllegalArgumentException("worldUnitsPerPixel must be finite and positive");
        }
        double zoom =
                StrictMath.log(WORLD_WIDTH / (512.0 * worldUnitsPerPixel)) / StrictMath.log(2);
        if (!Double.isFinite(zoom)) {
            throw new IllegalArgumentException("resolution does not produce a finite zoom");
        }
        return zoom;
    }
}
