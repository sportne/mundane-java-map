package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CrsAxis;
import io.github.mundanej.map.api.CrsAxisMeaning;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.CrsUnit;
import io.github.mundanej.map.api.Envelope;

/** Immutable Level 1 coordinate-reference definitions. */
public final class CrsDefinitions {
    /** EPSG:4326 in the library's longitude/latitude x/y convention. */
    public static final CrsDefinition EPSG_4326 =
            new CrsDefinition(
                    "EPSG:4326",
                    CrsKind.GEOGRAPHIC,
                    new CrsAxis(CrsAxisMeaning.LONGITUDE, CrsUnit.DEGREE),
                    new CrsAxis(CrsAxisMeaning.LATITUDE, CrsUnit.DEGREE),
                    new Envelope(-180.0, -90.0, 180.0, 90.0));

    /** EPSG:3857 in easting/northing metres. */
    public static final CrsDefinition EPSG_3857 =
            new CrsDefinition(
                    "EPSG:3857",
                    CrsKind.PROJECTED,
                    new CrsAxis(CrsAxisMeaning.EASTING, CrsUnit.METRE),
                    new CrsAxis(CrsAxisMeaning.NORTHING, CrsUnit.METRE),
                    new Envelope(
                            -WebMercatorProjection.WORLD_LIMIT,
                            -WebMercatorProjection.WORLD_LIMIT,
                            WebMercatorProjection.WORLD_LIMIT,
                            WebMercatorProjection.WORLD_LIMIT));

    private CrsDefinitions() {}
}
