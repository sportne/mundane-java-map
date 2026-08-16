package io.github.mundanej.map.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class WktCrsValuesTest {
    private static final CrsEllipsoid WGS84 = new CrsEllipsoid("WGS 84", 6_378_137, 298.257223563);
    private static final WktCrsAxis LATITUDE =
            new WktCrsAxis(
                    "Geodetic latitude (Lat)",
                    "Lat",
                    CrsAxisDirection.NORTH,
                    1,
                    "degree",
                    Math.PI / 180);
    private static final WktCrsAxis LONGITUDE =
            new WktCrsAxis(
                    "Geodetic longitude (Lon)",
                    "Lon",
                    CrsAxisDirection.EAST,
                    2,
                    "degree",
                    Math.PI / 180);

    @Test
    void ellipsoidAndAxisValuesRetainExplicitMetadata() {
        assertEquals(1.0 / 298.257223563, WGS84.flattening());
        assertEquals(0, new CrsEllipsoid("Sphere", 6_371_000, 0).flattening());
        assertEquals("Lat", LATITUDE.abbreviation());
        assertEquals(CrsAxisDirection.NORTH, LATITUDE.direction());

        assertThrows(NullPointerException.class, () -> new CrsEllipsoid(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CrsEllipsoid(" ", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new CrsEllipsoid("x", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CrsEllipsoid("x", 1, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WktCrsAxis("x", "x", CrsAxisDirection.EAST, 0, "metre", 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WktCrsAxis("x", "x", CrsAxisDirection.EAST, 1, "metre", 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WktCrsAxis(" ", "x", CrsAxisDirection.EAST, 1, "metre", 1));
    }

    @Test
    void geographicProjectedVerticalAndCompoundShapesAreImmutable() {
        WktCrsDefinition geographic = geographic();
        WktCrsDefinition projected =
                new WktCrsDefinition(
                        "WGS 84 / test",
                        WktCrsKind.PROJECTED,
                        Optional.of("TEST:1"),
                        geographic.datumName(),
                        geographic.ellipsoid(),
                        List.of(
                                new WktCrsAxis(
                                        "Easting (E)", "E", CrsAxisDirection.EAST, 1, "metre", 1),
                                new WktCrsAxis(
                                        "Northing (N)",
                                        "N",
                                        CrsAxisDirection.NORTH,
                                        2,
                                        "metre",
                                        1)),
                        geographic.identifier(),
                        Optional.of("Transverse Mercator"),
                        Map.of("Scale factor at natural origin", 0.9996),
                        List.of());
        WktCrsDefinition vertical =
                new WktCrsDefinition(
                        "height",
                        WktCrsKind.VERTICAL,
                        Optional.empty(),
                        Optional.of("datum"),
                        Optional.empty(),
                        List.of(
                                new WktCrsAxis(
                                        "Height (H)", "H", CrsAxisDirection.UP, 1, "metre", 1)),
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(),
                        List.of());
        WktCrsDefinition compound =
                new WktCrsDefinition(
                        "compound",
                        WktCrsKind.COMPOUND,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(),
                        List.of(geographic, vertical));

        assertEquals("TEST:1", projected.identifier().orElseThrow());
        assertEquals(List.of(geographic, vertical), compound.components());
        assertEquals(2, geographic.axes().size());
    }

    @Test
    void invalidDefinitionShapesAndBoundsFailImmediately() {
        WktCrsDefinition geographic = geographic();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WktCrsDefinition(
                                "bad",
                                WktCrsKind.GEOGRAPHIC,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                List.of(),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WktCrsDefinition(
                                "bad",
                                WktCrsKind.PROJECTED,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                List.of(LATITUDE, LONGITUDE),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WktCrsDefinition(
                                "bad",
                                WktCrsKind.COMPOUND,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                List.of(),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of(),
                                List.of(geographic)));
        WktCrsAxis duplicateOrder =
                new WktCrsAxis("Longitude", "L", CrsAxisDirection.EAST, 1, "degree", 1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WktCrsDefinition(
                                "bad",
                                WktCrsKind.GEOGRAPHIC,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                List.of(LATITUDE, duplicateOrder),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of(),
                                List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WktCrsDefinition(
                                "bad",
                                WktCrsKind.GEOGRAPHIC,
                                Optional.empty(),
                                Optional.empty(),
                                Optional.empty(),
                                List.of(LATITUDE, LONGITUDE),
                                Optional.empty(),
                                Optional.empty(),
                                Map.of("x", Double.NaN),
                                List.of()));
    }

    private static WktCrsDefinition geographic() {
        return new WktCrsDefinition(
                "WGS 84",
                WktCrsKind.GEOGRAPHIC,
                Optional.of("EPSG:4326"),
                Optional.of("World Geodetic System 1984"),
                Optional.of(WGS84),
                List.of(LATITUDE, LONGITUDE),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }
}
