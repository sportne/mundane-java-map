package io.github.mundanej.map.io.kml;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KmlGeometryTest {
    private static final SourceIdentity IDENTITY =
            new SourceIdentity("kml-geometry-test", "KML geometry test");

    @Test
    void mapsPolygonExteriorAndHolesWithoutReordering() {
        FeatureRecord record =
                only(
                        """
                        <Placemark><Polygon>
                          <outerBoundaryIs><LinearRing><coordinates>
                            -4,-4 4,-4 4,4 -4,4 -4,-4
                          </coordinates></LinearRing></outerBoundaryIs>
                          <innerBoundaryIs><LinearRing><coordinates>
                            -1,-1 -1,1 1,1 1,-1 -1,-1
                          </coordinates></LinearRing></innerBoundaryIs>
                        </Polygon></Placemark>
                        """);
        PolygonGeometry polygon = assertInstanceOf(PolygonGeometry.class, record.geometry());
        assertEquals("polygon", record.attributes().get("geometryKind"));
        assertEquals(CoordinateSequence.of(-4, -4, 4, -4, 4, 4, -4, 4, -4, -4), polygon.exterior());
        assertEquals(
                List.of(CoordinateSequence.of(-1, -1, -1, 1, 1, 1, 1, -1, -1, -1)),
                polygon.holes());
    }

    @Test
    void acceptsOrderedPolygonAndLinearRingControls() {
        FeatureRecord record =
                only(
                        """
                        <Placemark><Polygon>
                          <extrude>0</extrude>
                          <tessellate>false</tessellate>
                          <altitudeMode>clampToGround</altitudeMode>
                          <outerBoundaryIs><LinearRing>
                            <extrude>false</extrude>
                            <tessellate>0</tessellate>
                            <altitudeMode>clampToGround</altitudeMode>
                            <coordinates>0,0 2,0 2,2 0,0</coordinates>
                          </LinearRing></outerBoundaryIs>
                        </Polygon></Placemark>
                        """);
        assertInstanceOf(PolygonGeometry.class, record.geometry());
    }

    @Test
    void mapsEveryHomogeneousMultiGeometryToOrdinaryPackedGeometry() {
        List<FeatureRecord> records =
                all(
                        """
                        <Document>
                          <Placemark><MultiGeometry>
                            <Point><coordinates>-0,-0</coordinates></Point>
                            <Point><coordinates>2,3</coordinates></Point>
                          </MultiGeometry></Placemark>
                          <Placemark><MultiGeometry>
                            <LineString><coordinates>0,0 1,1</coordinates></LineString>
                            <LineString><coordinates>2,2 3,3 4,4</coordinates></LineString>
                          </MultiGeometry></Placemark>
                          <Placemark><MultiGeometry>
                            <Polygon><outerBoundaryIs><LinearRing><coordinates>
                              0,0 2,0 2,2 0,2 0,0
                            </coordinates></LinearRing></outerBoundaryIs></Polygon>
                            <Polygon><outerBoundaryIs><LinearRing><coordinates>
                              3,3 4,3 4,4 3,4 3,3
                            </coordinates></LinearRing></outerBoundaryIs></Polygon>
                          </MultiGeometry></Placemark>
                        </Document>
                        """);
        MultiPointGeometry points =
                assertInstanceOf(MultiPointGeometry.class, records.get(0).geometry());
        assertEquals(CoordinateSequence.of(0, 0, 2, 3), points.coordinates());
        assertEquals("multipoint", records.get(0).attributes().get("geometryKind"));

        MultiLineStringGeometry lines =
                assertInstanceOf(MultiLineStringGeometry.class, records.get(1).geometry());
        assertEquals(2, lines.partCount());
        assertArrayEquals(new int[] {0, 2, 5}, lines.partOffsets());
        assertEquals("multiline", records.get(1).attributes().get("geometryKind"));

        MultiPolygonGeometry polygons =
                assertInstanceOf(MultiPolygonGeometry.class, records.get(2).geometry());
        assertEquals(2, polygons.polygonCount());
        assertEquals(2, polygons.ringCount());
        assertEquals("multipolygon", records.get(2).attributes().get("geometryKind"));
    }

    @Test
    void rejectsOpenShortMixedNestedAndEmptyGeometryPredictably() {
        assertValueFailure(
                """
                <Placemark><Polygon><outerBoundaryIs><LinearRing>
                  <coordinates>0,0 1,0 0,0</coordinates>
                </LinearRing></outerBoundaryIs></Polygon></Placemark>
                """,
                "outerRing",
                "cardinality");
        assertValueFailure(
                """
                <Placemark><Polygon><outerBoundaryIs><LinearRing>
                  <coordinates>0,0 1,0 1,1 0,1</coordinates>
                </LinearRing></outerBoundaryIs></Polygon></Placemark>
                """,
                "outerRing",
                "closure");
        assertProfileFailure(
                """
                <Placemark><MultiGeometry>
                  <Point><coordinates>0,0</coordinates></Point>
                  <LineString><coordinates>0,0 1,1</coordinates></LineString>
                </MultiGeometry></Placemark>
                """);
        assertProfileFailure(
                """
                <Placemark><MultiGeometry>
                  <Point><coordinates>0,0</coordinates></Point>
                  <LineString/>
                </MultiGeometry></Placemark>
                """);
        assertProfileFailure(
                """
                <Placemark><MultiGeometry>
                  <MultiGeometry><Point><coordinates>0,0</coordinates></Point></MultiGeometry>
                </MultiGeometry></Placemark>
                """);
        assertProfileFailure("<Placemark><MultiGeometry/></Placemark>");
    }

    @Test
    void rejectsInteriorRingBeforeExteriorRing() {
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                all(
                                        """
                                        <Placemark><Polygon>
                                          <innerBoundaryIs><LinearRing><coordinates>
                                            0,0 1,0 1,1 0,0
                                          </coordinates></LinearRing></innerBoundaryIs>
                                          <outerBoundaryIs><LinearRing><coordinates>
                                            -1,-1 2,-1 2,2 -1,-1
                                          </coordinates></LinearRing></outerBoundaryIs>
                                        </Polygon></Placemark>
                                        """));
        assertEquals("KML_XML_INVALID", failure.terminal().code());
        assertEquals(Map.of("reason", "order"), failure.terminal().context());
    }

    @Test
    void rejectsOutOfOrderPolygonAndLinearRingControls() {
        assertXmlOrderFailure(
                """
                <Placemark><Polygon>
                  <outerBoundaryIs><LinearRing>
                    <coordinates>0,0 1,0 1,1 0,0</coordinates>
                  </LinearRing></outerBoundaryIs>
                  <altitudeMode>clampToGround</altitudeMode>
                </Polygon></Placemark>
                """);
        assertXmlOrderFailure(
                """
                <Placemark><Polygon>
                  <outerBoundaryIs><LinearRing>
                    <coordinates>0,0 1,0 1,1 0,0</coordinates>
                    <extrude>0</extrude>
                  </LinearRing></outerBoundaryIs>
                </Polygon></Placemark>
                """);
    }

    @Test
    void enforcesLogicalGeometryCoordinatesAndMultipartPartsProspectively() {
        String polygon =
                """
                <Placemark><Polygon>
                  <outerBoundaryIs><LinearRing>
                    <coordinates>0,0 2,0 2,2 0,0</coordinates>
                  </LinearRing></outerBoundaryIs>
                  <innerBoundaryIs><LinearRing>
                    <coordinates>.5,.5 1,.5 1,1 .5,.5</coordinates>
                  </LinearRing></innerBoundaryIs>
                </Polygon></Placemark>
                """;
        assertEquals(1, all(polygon, limits(8, 4)).size());
        assertLimit(allFailure(polygon, limits(7, 4)), "geometryCoordinates", "8", "7");

        String polygons =
                """
                <Placemark><MultiGeometry>
                  <Polygon><outerBoundaryIs><LinearRing>
                    <coordinates>0,0 1,0 1,1 0,0</coordinates>
                  </LinearRing></outerBoundaryIs></Polygon>
                  <Polygon><outerBoundaryIs><LinearRing>
                    <coordinates>2,2 3,2 3,3 2,2</coordinates>
                  </LinearRing></outerBoundaryIs></Polygon>
                </MultiGeometry></Placemark>
                """;
        assertEquals(1, all(polygons, limits(8, 4)).size());
        assertLimit(allFailure(polygons, limits(8, 3)), "parts", "4", "3");
    }

    private static void assertValueFailure(String feature, String field, String reason) {
        SourceException failure = assertThrows(SourceException.class, () -> all(feature));
        assertEquals("KML_VALUE_INVALID", failure.terminal().code());
        assertEquals(Map.of("field", field, "reason", reason), failure.terminal().context());
    }

    private static void assertProfileFailure(String feature) {
        SourceException failure = assertThrows(SourceException.class, () -> all(feature));
        assertEquals("KML_PROFILE_UNSUPPORTED", failure.terminal().code());
        assertEquals(Map.of("construct", "multiGeometry"), failure.terminal().context());
    }

    private static void assertXmlOrderFailure(String feature) {
        SourceException failure = assertThrows(SourceException.class, () -> all(feature));
        assertEquals("KML_XML_INVALID", failure.terminal().code());
        assertEquals(Map.of("reason", "order"), failure.terminal().context());
    }

    private static void assertLimit(
            SourceException failure, String limit, String requested, String maximum) {
        assertEquals("SOURCE_LIMIT_EXCEEDED", failure.terminal().code());
        assertEquals(limit, failure.terminal().context().get("limit"));
        assertEquals(requested, failure.terminal().context().get("requested"));
        assertEquals(maximum, failure.terminal().context().get("maximum"));
    }

    private static FeatureRecord only(String feature) {
        List<FeatureRecord> records = all(feature);
        assertEquals(1, records.size());
        return records.get(0);
    }

    private static List<FeatureRecord> all(String feature) {
        return all(feature, KmlLimits.defaults());
    }

    private static SourceException allFailure(String feature, KmlLimits limits) {
        return assertThrows(SourceException.class, () -> all(feature, limits));
    }

    private static List<FeatureRecord> all(String feature, KmlLimits limits) {
        String document =
                """
                <?xml version="1.0" encoding="UTF-8"?>
                <kml xmlns="http://www.opengis.net/kml/2.2">
                """
                        + feature
                        + "\n</kml>";
        FeatureSource source =
                KmlFiles.openSnapshot(
                        document.getBytes(StandardCharsets.UTF_8),
                        IDENTITY,
                        KmlOpenOptions.defaults().withFormatLimits(limits),
                        CancellationToken.none());
        List<FeatureRecord> records = new ArrayList<>();
        try (source;
                FeatureCursor cursor =
                        source.openCursor(FeatureQuery.all(), CancellationToken.none())) {
            while (cursor.advance()) {
                records.add(cursor.current());
            }
            assertFalse(cursor.advance());
        }
        assertTrue(source.isClosed());
        return records;
    }

    private static KmlLimits limits(int maximumCoordinatesPerGeometry, int maximumParts) {
        KmlLimits defaults = KmlLimits.defaults();
        return new KmlLimits(
                defaults.maximumInputBytes(),
                defaults.maximumXmlDepth(),
                defaults.maximumXmlEvents(),
                defaults.maximumElements(),
                defaults.maximumAttributes(),
                defaults.maximumNamespaceDeclarations(),
                defaults.maximumFeatureDepth(),
                defaults.maximumPhysicalFeatures(),
                defaults.maximumTotalCoordinates(),
                maximumCoordinatesPerGeometry,
                maximumParts,
                defaults.maximumScalarCharacters(),
                defaults.maximumTextCharacters(),
                defaults.maximumNumberCharacters(),
                defaults.maximumOwnedBytes(),
                defaults.retainedWarnings());
    }
}
