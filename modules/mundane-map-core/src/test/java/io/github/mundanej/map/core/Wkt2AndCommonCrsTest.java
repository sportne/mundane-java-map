package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsAxisDirection;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.WktCrsAxis;
import io.github.mundanej.map.api.WktCrsDefinition;
import io.github.mundanej.map.api.WktCrsKind;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Wkt2AndCommonCrsTest {
    private static final double DEGREE = Math.PI / 180.0;

    @Test
    void pinnedCatalogHasStableOrderChecksumAndCanonicalRoundTrips() {
        assertEquals(
                List.of(
                        "EPSG:4326",
                        "EPSG:3857",
                        "EPSG:3395",
                        "EPSG:32618",
                        "EPSG:32633",
                        "EPSG:4269",
                        "EPSG:26915",
                        "EPSG:4277",
                        "EPSG:27700",
                        "EPSG:5703",
                        "EPSG:4979",
                        "PROFILE:NAD83+NAVD88"),
                CommonCrsCatalog.identifiers());
        assertEquals(
                "f91b37010154184f80b845f101839f71780d248311d112a27ae7fb5d8a38afe9",
                CommonCrsCatalog.SOURCE_SHA256);
        for (String identifier : CommonCrsCatalog.identifiers()) {
            WktCrsDefinition definition = CommonCrsCatalog.wktDefinition(identifier);
            assertEquals(definition, Wkt2.parse(Wkt2.write(definition)), identifier);
        }
        assertCode("CRS_REGISTRY_KEY_UNKNOWN", () -> CommonCrsCatalog.wktDefinition("epsg:4326"));
        assertThrows(NullPointerException.class, () -> CommonCrsCatalog.wktDefinition(null));
    }

    @Test
    void prjGeoTiffAndGeoPackageCorpusRetainsAdapterMetadata() throws IOException {
        WktCrsDefinition prj = parseResource("prj-utm33.wkt");
        WktCrsDefinition geotiff = parseResource("geotiff-world-mercator.wkt");
        WktCrsDefinition geopackage = parseResource("geopackage-nad83.wkt");

        assertEquals("EPSG:32633", prj.identifier().orElseThrow());
        assertEquals(
                WktCoordinateOperation.TRANSVERSE_MERCATOR, prj.operationMethod().orElseThrow());
        assertEquals("EPSG:3395", geotiff.identifier().orElseThrow());
        assertEquals(
                WktCoordinateOperation.MERCATOR_VARIANT_A, geotiff.operationMethod().orElseThrow());
        assertEquals(CrsAxisDirection.NORTH, geopackage.axes().get(0).direction());
        assertEquals(DEGREE, geopackage.axes().get(1).unitToSi());

        CrsRegistry registry = CrsRegistry.common();
        assertSame(CommonCrsCatalog.EPSG_32633, registry.resolve("urn:ogc:def:crs:EPSG::32633"));
        assertSame(
                CommonCrsCatalog.EPSG_4269,
                registry.resolve("http://www.opengis.net/def/crs/EPSG/0/4269"));
    }

    @Test
    void commonOperationsMatchAuthoritativeMethodControlPoints() {
        CrsRegistry registry = CrsRegistry.common();
        CrsOperation utm33 =
                registry.operation(CrsDefinitions.EPSG_4326, CommonCrsCatalog.EPSG_32633);
        assertCoordinate(new Coordinate(500_000, 0), utm33.transform(new Coordinate(15, 0)), 1e-7);
        Coordinate copenhagen = utm33.transform(new Coordinate(12, 55));
        assertCoordinate(new Coordinate(308_124.368, 6_098_907.825), copenhagen, 0.02);
        assertCoordinate(
                new Coordinate(12, 55),
                registry.operation(CommonCrsCatalog.EPSG_32633, CrsDefinitions.EPSG_4326)
                        .transform(copenhagen),
                1e-7);

        CrsOperation worldMercator =
                registry.operation(CrsDefinitions.EPSG_4326, CommonCrsCatalog.EPSG_3395);
        assertCoordinate(
                new Coordinate(111_319.490793, 0),
                worldMercator.transform(new Coordinate(1, 0)),
                1e-5);
        assertCoordinate(
                new Coordinate(1, 0),
                registry.operation(CommonCrsCatalog.EPSG_3395, CrsDefinitions.EPSG_4326)
                        .transform(new Coordinate(111_319.490793, 0)),
                1e-8);

        assertCoordinate(
                new Coordinate(500_000, 0),
                registry.operation(CommonCrsCatalog.EPSG_4269, CommonCrsCatalog.EPSG_26915)
                        .transform(new Coordinate(-93, 0)),
                1e-7);
        assertCoordinate(
                new Coordinate(400_000, -100_000),
                WktCoordinateOperation.between(
                                CommonCrsCatalog.wktDefinition("EPSG:4277"),
                                CommonCrsCatalog.wktDefinition("EPSG:27700"))
                        .transform(new Coordinate(49, -2)),
                1e-6);

        Envelope projected = utm33.transformEnvelopeStrict(new Envelope(14.9, 0, 15.1, 0.1));
        assertTrue(projected.maxX() > projected.minX());
        assertTrue(projected.maxY() > projected.minY());
        CrsException unavailable =
                assertThrows(
                        CrsException.class,
                        () ->
                                registry.operation(
                                        CommonCrsCatalog.EPSG_4269, CrsDefinitions.EPSG_4326));
        assertEquals("CRS_TRANSFORM_UNAVAILABLE", unavailable.problem().code());
    }

    @Test
    void nativeAxisOrderDirectionAndUnitsAreIndependentOfPresentation() {
        WktCrsDefinition geographic = CommonCrsCatalog.wktDefinition("EPSG:4326");
        WktCrsDefinition projected =
                projected(
                        geographic,
                        List.of(
                                new WktCrsAxis(
                                        "Northing (N)",
                                        "N",
                                        CrsAxisDirection.NORTH,
                                        1,
                                        "foot",
                                        0.3048),
                                new WktCrsAxis(
                                        "Easting (E)",
                                        "E",
                                        CrsAxisDirection.EAST,
                                        2,
                                        "foot",
                                        0.3048)));
        WktCoordinateOperation forward = WktCoordinateOperation.between(geographic, projected);
        assertEquals(geographic, forward.source());
        assertEquals(projected, forward.target());
        Coordinate nativeProjected = forward.transform(new Coordinate(0, 15));
        assertCoordinate(new Coordinate(0, 500_000 / 0.3048), nativeProjected, 1e-6);
        assertCoordinate(
                new Coordinate(0, 15),
                WktCoordinateOperation.between(projected, geographic).transform(nativeProjected),
                1e-9);

        WktCrsDefinition southwest =
                geographicWithAxes(
                        List.of(
                                new WktCrsAxis(
                                        "Latitude south (S)",
                                        "S",
                                        CrsAxisDirection.SOUTH,
                                        1,
                                        "degree",
                                        DEGREE),
                                new WktCrsAxis(
                                        "Longitude west (W)",
                                        "W",
                                        CrsAxisDirection.WEST,
                                        2,
                                        "degree",
                                        DEGREE)));
        WktCrsDefinition southwestProjected =
                projected(
                        southwest,
                        List.of(
                                new WktCrsAxis(
                                        "Westing (W)", "W", CrsAxisDirection.WEST, 1, "metre", 1),
                                new WktCrsAxis(
                                        "Southing (S)",
                                        "S",
                                        CrsAxisDirection.SOUTH,
                                        2,
                                        "metre",
                                        1)));
        Coordinate westSouth =
                WktCoordinateOperation.between(southwest, southwestProjected)
                        .transform(new Coordinate(0, -15));
        assertCoordinate(new Coordinate(-500_000, 0), westSouth, 1e-7);
    }

    @Test
    void boundedParserRejectsMalformedDeepAndUnsupportedProfiles() {
        assertCode("CRS_WKT_SYNTAX_INVALID", () -> Wkt2.parse("GEOGCRS[]"));
        assertCode("CRS_WKT_SYNTAX_INVALID", () -> Wkt2.parse("GEOGCRS[\"unterminated]"));
        assertCode("CRS_WKT_SYNTAX_INVALID", () -> Wkt2.parse("GEOGCRS[\"x\"] trailing"));
        assertCode("CRS_WKT_PROFILE_UNSUPPORTED", () -> Wkt2.parse("BOUNDCRS[\"unsupported\"]"));
        assertCode(
                "CRS_WKT_INPUT_LIMIT", () -> Wkt2.parse("x".repeat(Wkt2.MAXIMUM_CHARACTERS + 1)));

        String nested =
                "USAGE[".repeat(Wkt2.MAXIMUM_DEPTH + 1)
                        + "\"x\""
                        + "]".repeat(Wkt2.MAXIMUM_DEPTH + 1);
        assertCode("CRS_WKT_INPUT_LIMIT", () -> Wkt2.parse(nested));
        assertThrows(NullPointerException.class, () -> Wkt2.parse(null));
        assertThrows(NullPointerException.class, () -> Wkt2.write(null));
    }

    @Test
    void unsupportedOperationsAndBatchFailuresAreAtomicAndStable() {
        WktCrsDefinition geographic = CommonCrsCatalog.wktDefinition("EPSG:4326");
        WktCrsDefinition projected = projected(geographic, projectedAxes());
        WktCoordinateOperation operation = WktCoordinateOperation.between(geographic, projected);
        Coordinate identityInput = geographicPoint();
        assertSame(
                identityInput,
                WktCoordinateOperation.between(geographic, geographic).transform(identityInput));
        assertEquals(
                List.of(new Coordinate(500_000, 0)),
                operation.transformAll(List.of(geographicPoint())));
        assertCode(
                "CRS_TRANSFORM_LIMIT",
                () ->
                        operation.transformAll(
                                Collections.nCopies(
                                        WktCoordinateOperation.MAXIMUM_BATCH_COORDINATES + 1,
                                        geographicPoint())));
        assertCode(
                "CRS_COORDINATE_OUT_OF_DOMAIN", () -> operation.transform(new Coordinate(91, 15)));
        assertCode(
                "CRS_OPERATION_UNSUPPORTED",
                () ->
                        WktCoordinateOperation.between(
                                geographic, withMethod(projected, "Hotine Oblique Mercator")));
        assertCode(
                "CRS_OPERATION_UNSUPPORTED",
                () ->
                        WktCoordinateOperation.between(
                                CommonCrsCatalog.wktDefinition("EPSG:4979"), projected));
        assertCode(
                "CRS_OPERATION_UNSUPPORTED",
                () ->
                        WktCoordinateOperation.between(
                                CommonCrsCatalog.wktDefinition("PROFILE:NAD83+NAVD88"),
                                geographic));
    }

    private static WktCrsDefinition parseResource(String name) throws IOException {
        String path = "/io/github/mundanej/map/core/wkt2/" + name;
        try (InputStream stream =
                Objects.requireNonNull(Wkt2AndCommonCrsTest.class.getResourceAsStream(path))) {
            return Wkt2.parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Coordinate geographicPoint() {
        return new Coordinate(0, 15);
    }

    private static WktCrsDefinition geographicWithAxes(List<WktCrsAxis> axes) {
        WktCrsDefinition source = CommonCrsCatalog.wktDefinition("EPSG:4326");
        return new WktCrsDefinition(
                source.name(),
                source.kind(),
                Optional.of("TEST:GEO"),
                source.datumName(),
                source.ellipsoid(),
                axes,
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    private static List<WktCrsAxis> projectedAxes() {
        return List.of(
                new WktCrsAxis("Easting (E)", "E", CrsAxisDirection.EAST, 1, "metre", 1),
                new WktCrsAxis("Northing (N)", "N", CrsAxisDirection.NORTH, 2, "metre", 1));
    }

    private static WktCrsDefinition projected(WktCrsDefinition base, List<WktCrsAxis> axes) {
        return new WktCrsDefinition(
                "test transverse mercator",
                WktCrsKind.PROJECTED,
                Optional.of("TEST:TM"),
                base.datumName(),
                base.ellipsoid(),
                axes,
                base.identifier(),
                Optional.of(WktCoordinateOperation.TRANSVERSE_MERCATOR),
                Map.of(
                        "Latitude of natural origin", 0.0,
                        "Longitude of natural origin", 15 * DEGREE,
                        "Scale factor at natural origin", 0.9996,
                        "False easting", 500_000.0,
                        "False northing", 0.0),
                List.of());
    }

    private static WktCrsDefinition withMethod(WktCrsDefinition definition, String method) {
        return new WktCrsDefinition(
                definition.name(),
                definition.kind(),
                definition.identifier(),
                definition.datumName(),
                definition.ellipsoid(),
                definition.axes(),
                definition.baseIdentifier(),
                Optional.of(method),
                definition.parameters(),
                definition.components());
    }

    private static void assertCoordinate(Coordinate expected, Coordinate actual, double tolerance) {
        assertEquals(expected.x(), actual.x(), tolerance);
        assertEquals(expected.y(), actual.y(), tolerance);
    }

    private static void assertCode(String code, Runnable action) {
        CrsException failure = assertThrows(CrsException.class, action::run);
        assertEquals(code, failure.problem().code());
        assertTrue(failure.problem().context().isEmpty());
    }
}
