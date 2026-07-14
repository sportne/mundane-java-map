package io.github.mundanej.map.io.shapefile.corpus;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.shapefile.DbfEncoding;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class CorpusExpectations {
    private static final String WGS_84_PRJ =
            "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\","
                    + "6378137.0,298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\","
                    + "0.0174532925199433]]";
    private static final String WEB_MERCATOR_PRJ =
            "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\",GEOGCS[\"GCS_WGS_1984\","
                    + "DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,"
                    + "298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\","
                    + "0.0174532925199433]],PROJECTION[\"Mercator_Auxiliary_Sphere\"],"
                    + "PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],"
                    + "PARAMETER[\"Central_Meridian\",0],PARAMETER[\"Standard_Parallel_1\",0],"
                    + "PARAMETER[\"Auxiliary_Sphere_Type\",0],UNIT[\"Meter\",1]]";
    private static final String UNKNOWN_PRJ = "LOCAL_CS[\"Corpus unknown\",UNIT[\"metre\",1]]";
    private static final Map<String, Expectation> VALUES = values();

    private CorpusExpectations() {}

    static Set<String> ids() {
        return VALUES.keySet();
    }

    static Set<String> coveredTags(String id) {
        return expectation(id).tags();
    }

    static Expectation expectation(String id) {
        Expectation value = VALUES.get(id);
        if (value == null) {
            throw new IllegalArgumentException("Unknown corpus expectation: " + id);
        }
        return value;
    }

    private static Map<String, Expectation> values() {
        LinkedHashMap<String, Expectation> values = new LinkedHashMap<>();
        add(
                values,
                success(
                        "E_CURATED_POINT",
                        tags(
                                "crs-epsg-4326",
                                "dbf-decimal",
                                "dbf-integer",
                                "dbf-text",
                                "encoding-cpg",
                                "encoding-utf8",
                                "index-valid",
                                "shape-point"),
                        new Envelope(139.7494616, 35.6869628, 139.7494616, 35.6869628),
                        schema(
                                field("NAME", AttributeType.TEXT),
                                field("POP_MAX", AttributeType.INTEGER),
                                field("LATITUDE", AttributeType.DECIMAL),
                                field("LONGITUDE", AttributeType.DECIMAL)),
                        "EPSG:4326",
                        WGS_84_PRJ,
                        List.of(
                                record(
                                        1,
                                        new PointGeometry(new Coordinate(139.7494616, 35.6869628)),
                                        attributes(
                                                "NAME",
                                                "Tokyo",
                                                "POP_MAX",
                                                35_676_000L,
                                                "LATITUDE",
                                                new BigDecimal("35.686963"),
                                                "LONGITUDE",
                                                new BigDecimal("139.749462"))))));
        add(
                values,
                success(
                        "E_MULTIPOINT_NULL",
                        tags(
                                "dbf-date",
                                "dbf-floating",
                                "dbf-logical",
                                "dbf-text",
                                "encoding-cpg",
                                "encoding-ibm437",
                                "index-valid",
                                "shape-multipoint",
                                "shape-null"),
                        new Envelope(-5, -5, 5, 5),
                        schema(
                                field("NAME", AttributeType.TEXT),
                                field("ACTIVE", AttributeType.LOGICAL),
                                field("DAY", AttributeType.DATE),
                                field("RATIO", AttributeType.FLOATING)),
                        null,
                        null,
                        List.of(
                                record(
                                        2,
                                        new MultiPointGeometry(sequence(-5, -5, 0, 0, 5, 5)),
                                        attributes(
                                                "NAME",
                                                "Café",
                                                "ACTIVE",
                                                true,
                                                "DAY",
                                                LocalDate.of(2024, 1, 2),
                                                "RATIO",
                                                12.5)))));
        add(
                values,
                success(
                        "E_MULTIPART_LINE",
                        tags(
                                "dbf-integer",
                                "dbf-text",
                                "encoding-ibm850",
                                "encoding-ldid",
                                "index-valid",
                                "shape-multipart",
                                "shape-polyline"),
                        new Envelope(-10, 0, 15, 5),
                        schema(
                                field("NAME", AttributeType.TEXT),
                                field("COUNT", AttributeType.INTEGER)),
                        null,
                        null,
                        List.of(
                                record(
                                        1,
                                        MultiLineStringGeometry.of(
                                                sequence(-10, 0, -5, 5, 0, 0, 5, 0, 10, 5, 15, 0),
                                                new int[] {0, 3, 6}),
                                        attributes("NAME", "Málaga", "COUNT", 6L)))));
        List<FeatureRecord> polygons = polygonRecords();
        add(
                values,
                success(
                        "E_POLYGON_HOLE",
                        tags(
                                "crs-epsg-3857",
                                "dbf-text",
                                "encoding-cpg",
                                "encoding-windows1252",
                                "index-valid",
                                "shape-hole",
                                "shape-multipart",
                                "shape-polygon"),
                        new Envelope(0, 0, 80, 40),
                        schema(
                                field("NAME", AttributeType.TEXT),
                                field("NOTE", AttributeType.TEXT)),
                        "EPSG:3857",
                        WEB_MERCATOR_PRJ,
                        polygons,
                        List.of(),
                        List.of(
                                diagnostic(
                                        "SHAPEFILE_DBF_VALUE_INVALID",
                                        "dbf",
                                        2,
                                        1,
                                        "NOTE",
                                        129,
                                        Map.of("reason", "encoding")))));
        add(
                values,
                new Expectation(
                        "E_ISO_UNKNOWN",
                        tags(
                                "crs-explicit-override",
                                "crs-unknown-retained",
                                "dbf-text",
                                "encoding-cpg",
                                "encoding-explicit-override",
                                "encoding-iso88591",
                                "index-valid",
                                "shape-point"),
                        ShapefileOpenOptions.defaults()
                                .withCrsOverride(CrsRegistry.level1().resolve("EPSG:4326"))
                                .withDbfEncodingOverride(DbfEncoding.ISO_8859_1),
                        Optional.of(
                                new ExpectedSuccess(
                                        new Envelope(-3.5, 42.25, -3.5, 42.25),
                                        schema(field("NAME", AttributeType.TEXT)),
                                        "EPSG:4326",
                                        UNKNOWN_PRJ,
                                        List.of(
                                                record(
                                                        1,
                                                        new PointGeometry(
                                                                new Coordinate(-3.5, 42.25)),
                                                        attributes("NAME", "Café"))),
                                        List.of(
                                                diagnostic(
                                                        "SHAPEFILE_PRJ_OVERRIDE_USED",
                                                        "prj",
                                                        -1,
                                                        -1,
                                                        "",
                                                        0,
                                                        Map.of("selected", "EPSG:4326"))),
                                        0,
                                        List.of(),
                                        0)),
                        Optional.empty()));
        add(
                values,
                success(
                        "E_FALLBACK_DELETED",
                        tags(
                                "dbf-date",
                                "dbf-floating",
                                "dbf-logical",
                                "dbf-text",
                                "encoding-fallback",
                                "index-valid",
                                "record-deleted",
                                "shape-point"),
                        new Envelope(1, 1, 2, 2),
                        schema(
                                field("NAME", AttributeType.TEXT),
                                field("ACTIVE", AttributeType.LOGICAL),
                                field("DAY", AttributeType.DATE),
                                field("VALUE", AttributeType.FLOATING)),
                        null,
                        null,
                        List.of(
                                record(
                                        1,
                                        new PointGeometry(new Coordinate(1, 1)),
                                        attributes(
                                                "NAME",
                                                "Alpha",
                                                "ACTIVE",
                                                true,
                                                "DAY",
                                                LocalDate.of(2020, 2, 29),
                                                "VALUE",
                                                3.25))),
                        List.of(
                                diagnostic(
                                        "SHAPEFILE_ENCODING_FALLBACK",
                                        "dbf",
                                        -1,
                                        -1,
                                        "",
                                        29,
                                        Map.of("selected", "WINDOWS_1252"))),
                        List.of()));
        add(
                values,
                failure(
                        "E_POINTZ_REJECTED",
                        tags("shape-zm-rejected"),
                        "open",
                        error(
                                "SHAPEFILE_SHAPE_TYPE_UNSUPPORTED",
                                "shp",
                                -1,
                                -1,
                                "",
                                32,
                                Map.of("shapeType", "11")),
                        List.of()));
        add(
                values,
                new Expectation(
                        "E_CORRUPT_SHX",
                        tags("index-corrupt-ignored"),
                        ShapefileOpenOptions.defaults(),
                        Optional.of(
                                new ExpectedSuccess(
                                        new Envelope(0, 0, 80, 40),
                                        schema(
                                                field("NAME", AttributeType.TEXT),
                                                field("NOTE", AttributeType.TEXT)),
                                        "EPSG:3857",
                                        WEB_MERCATOR_PRJ,
                                        polygons,
                                        List.of(
                                                diagnostic(
                                                        "SHAPEFILE_SHX_IGNORED",
                                                        "shx",
                                                        -1,
                                                        -1,
                                                        "",
                                                        0,
                                                        Map.of("reason", "header"))),
                                        0,
                                        List.of(
                                                diagnostic(
                                                        "SHAPEFILE_DBF_VALUE_INVALID",
                                                        "dbf",
                                                        2,
                                                        1,
                                                        "NOTE",
                                                        129,
                                                        Map.of("reason", "encoding"))),
                                        0)),
                        Optional.empty()));
        add(
                values,
                failure(
                        "E_CORRUPT_DBF",
                        tags("dbf-corrupt-terminal"),
                        "cursor",
                        error(
                                "SHAPEFILE_DBF_RECORD_MARKER_INVALID",
                                "dbf",
                                1,
                                -1,
                                "",
                                161,
                                Map.of()),
                        List.of(
                                diagnostic(
                                        "SHAPEFILE_ENCODING_FALLBACK",
                                        "dbf",
                                        -1,
                                        -1,
                                        "",
                                        29,
                                        Map.of("selected", "WINDOWS_1252")))));
        return Map.copyOf(values);
    }

    private static List<FeatureRecord> polygonRecords() {
        PolygonGeometry first =
                new PolygonGeometry(
                        sequence(0, 0, 0, 40, 40, 40, 40, 0, 0, 0),
                        List.of(sequence(10, 10, 20, 10, 20, 20, 10, 20, 10, 10)));
        MultiPolygonGeometry second =
                MultiPolygonGeometry.of(
                        sequence(
                                50, 0, 50, 10, 60, 10, 60, 0, 50, 0, 70, 20, 70, 30, 80, 30, 80, 20,
                                70, 20),
                        new int[] {0, 5, 10},
                        new int[] {0, 1, 2});
        return List.of(
                record(1, first, attributes("NAME", "Café", "NOTE", "valid")),
                record(2, second, attributes("NAME", "Second", "NOTE", AttributeNull.INSTANCE)));
    }

    private static Expectation success(
            String id,
            Set<String> tags,
            Envelope extent,
            AttributeSchema schema,
            String crs,
            String retained,
            List<FeatureRecord> records) {
        return new Expectation(
                id,
                tags,
                ShapefileOpenOptions.defaults(),
                Optional.of(
                        new ExpectedSuccess(
                                extent, schema, crs, retained, records, List.of(), 0, List.of(),
                                0)),
                Optional.empty());
    }

    private static Expectation success(
            String id,
            Set<String> tags,
            Envelope extent,
            AttributeSchema schema,
            String crs,
            String retained,
            List<FeatureRecord> records,
            List<ExpectedDiagnostic> opening,
            List<ExpectedDiagnostic> cursor) {
        return new Expectation(
                id,
                tags,
                ShapefileOpenOptions.defaults(),
                Optional.of(
                        new ExpectedSuccess(
                                extent, schema, crs, retained, records, opening, 0, cursor, 0)),
                Optional.empty());
    }

    private static Expectation failure(
            String id,
            Set<String> tags,
            String phase,
            ExpectedDiagnostic diagnostic,
            List<ExpectedDiagnostic> opening) {
        return new Expectation(
                id,
                tags,
                ShapefileOpenOptions.defaults(),
                Optional.empty(),
                Optional.of(new ExpectedFailure(phase, opening, 0, List.of(diagnostic), 0)));
    }

    private static void add(Map<String, Expectation> values, Expectation expectation) {
        if (values.putIfAbsent(expectation.id(), expectation) != null) {
            throw new AssertionError("Duplicate expectation: " + expectation.id());
        }
    }

    private static AttributeField field(String name, AttributeType type) {
        return new AttributeField(name, type, true);
    }

    private static AttributeSchema schema(AttributeField... fields) {
        return new AttributeSchema(List.of(fields));
    }

    private static FeatureRecord record(
            int ordinal,
            io.github.mundanej.map.api.Geometry geometry,
            Map<String, Object> attributes) {
        return new FeatureRecord("record:" + ordinal, "", geometry, attributes);
    }

    private static CoordinateSequence sequence(double... ordinates) {
        return CoordinateSequence.of(ordinates);
    }

    private static Set<String> tags(String... values) {
        return Set.of(values);
    }

    private static Map<String, Object> attributes(Object... values) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put((String) values[index], values[index + 1]);
        }
        return result;
    }

    private static ExpectedDiagnostic diagnostic(
            String code,
            String component,
            long record,
            int field,
            String fieldName,
            long byteOffset,
            Map<String, String> context) {
        return new ExpectedDiagnostic(
                code, "WARNING", component, record, -1, field, fieldName, byteOffset, context);
    }

    private static ExpectedDiagnostic error(
            String code,
            String component,
            long record,
            int field,
            String fieldName,
            long byteOffset,
            Map<String, String> context) {
        return new ExpectedDiagnostic(
                code, "ERROR", component, record, -1, field, fieldName, byteOffset, context);
    }

    record Expectation(
            String id,
            Set<String> tags,
            ShapefileOpenOptions options,
            Optional<ExpectedSuccess> success,
            Optional<ExpectedFailure> failure) {}

    record ExpectedSuccess(
            Envelope extent,
            AttributeSchema schema,
            String canonicalCrs,
            String retainedCrs,
            List<FeatureRecord> records,
            List<ExpectedDiagnostic> openingDiagnostics,
            long openingOmittedWarningCount,
            List<ExpectedDiagnostic> cursorDiagnostics,
            long cursorOmittedWarningCount) {}

    record ExpectedFailure(
            String phase,
            List<ExpectedDiagnostic> openingDiagnostics,
            long openingOmittedWarningCount,
            List<ExpectedDiagnostic> diagnostics,
            long omittedWarningCount) {}

    record ExpectedDiagnostic(
            String code,
            String severity,
            String component,
            long record,
            int part,
            int field,
            String fieldName,
            long byteOffset,
            Map<String, String> context) {}
}
