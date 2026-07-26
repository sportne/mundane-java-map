package io.github.mundanej.map.io.mbtiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.TreeMap;

final class MbTilesCatalogReader {
    private static final List<Column> METADATA_COLUMNS =
            List.of(new Column("name", "TEXT"), new Column("value", "TEXT"));
    private static final List<Column> TILE_COLUMNS =
            List.of(
                    new Column("zoom_level", "INTEGER"),
                    new Column("tile_column", "INTEGER"),
                    new Column("tile_row", "INTEGER"),
                    new Column("tile_data", "BLOB"));

    private MbTilesCatalogReader() {}

    static Snapshot read(String sourceId, MbTilesSession session, CancellationToken cancellation) {
        session.beforeOperation(cancellation, "metadata");
        try {
            validateInventory(sourceId, session, cancellation);
            validateColumns(sourceId, session, "metadata", METADATA_COLUMNS);
            validateColumns(sourceId, session, "tiles", TILE_COLUMNS);
            MetadataValues values = readMetadata(sourceId, session, cancellation);
            Map<Integer, MbTilesTileProfile> profiles =
                    readTileProfiles(sourceId, session, cancellation, values.format());
            MbTilesMetadata metadata =
                    values.toMetadata(sourceId, profiles.keySet().stream().toList());
            session.afterOperation(cancellation, "metadata");
            session.verifyBeforePublication(cancellation, "publish");
            return new Snapshot(metadata, profiles);
        } catch (SQLException exception) {
            SourceException primary = session.queryFailure(exception, "metadata");
            session.suppressOperationCleanup(primary, cancellation, "metadata");
            throw primary;
        } catch (RuntimeException | Error failure) {
            session.suppressOperationCleanup(failure, cancellation, "metadata");
            throw failure;
        }
    }

    static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static void validateInventory(
            String sourceId, MbTilesSession session, CancellationToken cancellation)
            throws SQLException {
        long count = 0;
        boolean metadata = false;
        boolean tiles = false;
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT type,name,tbl_name FROM sqlite_schema ORDER BY type,name")) {
            while (rows.next()) {
                checkpoint(sourceId, cancellation, ++count);
                if (count > session.limits().maximumSchemaObjects()) {
                    throw limit(
                            sourceId,
                            "schemaObjects",
                            count,
                            session.limits().maximumSchemaObjects());
                }
                String type = rows.getString(1);
                String name = rows.getString(2);
                String table = rows.getString(3);
                if (name == null || name.indexOf('\0') >= 0) {
                    throw schema(sourceId, "metadata", "name", "value");
                }
                if (name.length() > session.limits().maximumIdentifierCharacters()) {
                    throw limit(
                            sourceId,
                            "identifierCharacters",
                            name.length(),
                            session.limits().maximumIdentifierCharacters());
                }
                if (name.startsWith("sqlite_")) {
                    continue;
                }
                if ("table".equals(type) && "metadata".equals(name)) {
                    metadata = true;
                } else if ("table".equals(type) && "tiles".equals(name)) {
                    tiles = true;
                } else if (("index".equals(type) || "trigger".equals(type))
                        && ("metadata".equals(table) || "tiles".equals(table))) {
                    // Ordinary indexes and inert triggers are accepted.
                } else if ("view".equals(type)) {
                    throw unsupported(sourceId, "view");
                } else {
                    throw unsupported(sourceId, "object");
                }
            }
        }
        if (!metadata) {
            throw schema(sourceId, "metadata", "name", "missing");
        }
        if (!tiles) {
            throw schema(sourceId, "tiles", "zoom", "missing");
        }
    }

    private static void validateColumns(
            String sourceId, MbTilesSession session, String table, List<Column> expected)
            throws SQLException {
        List<Column> actual = new ArrayList<>();
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery("PRAGMA table_info(" + quote(table) + ')')) {
            while (rows.next()) {
                actual.add(new Column(rows.getString("name"), rows.getString("type")));
                if (actual.size() > session.limits().maximumColumns()) {
                    throw limit(
                            sourceId, "columns", actual.size(), session.limits().maximumColumns());
                }
            }
        }
        if (actual.size() != expected.size()) {
            throw schema(sourceId, table, table.equals("tiles") ? "zoom" : "name", "constraint");
        }
        for (int index = 0; index < expected.size(); index++) {
            Column wanted = expected.get(index);
            Column found = actual.get(index);
            if (!wanted.name().equals(found.name())) {
                throw schema(sourceId, table, role(table, wanted.name()), "constraint");
            }
            if (!wanted.type().equalsIgnoreCase(found.type())) {
                throw schema(sourceId, table, role(table, wanted.name()), "type");
            }
        }
    }

    private static MetadataValues readMetadata(
            String sourceId, MbTilesSession session, CancellationToken cancellation)
            throws SQLException {
        LinkedHashMap<String, String> known = new LinkedHashMap<>();
        HashSet<String> names = new HashSet<>();
        long rowsSeen = 0;
        long characters = 0;
        int ignored = 0;
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT name,value,typeof(name),typeof(value)"
                                        + " FROM metadata ORDER BY name,rowid")) {
            while (rows.next()) {
                checkpoint(sourceId, cancellation, ++rowsSeen);
                if (rowsSeen > session.limits().maximumMetadataRows()) {
                    throw limit(
                            sourceId,
                            "metadataRows",
                            rowsSeen,
                            session.limits().maximumMetadataRows());
                }
                if (!"text".equals(rows.getString(3)) || !"text".equals(rows.getString(4))) {
                    throw metadata(sourceId, "name", "encoding");
                }
                String name = rows.getString(1);
                String value = rows.getString(2);
                if (name == null || value == null) {
                    throw metadata(sourceId, "name", "missing");
                }
                if (name.length() > session.limits().maximumTextValueCharacters()
                        || value.length() > session.limits().maximumTextValueCharacters()) {
                    throw limit(
                            sourceId,
                            "textValueCharacters",
                            Math.max(name.length(), value.length()),
                            session.limits().maximumTextValueCharacters());
                }
                characters =
                        Math.addExact(characters, Math.addExact(name.length(), value.length()));
                if (characters > session.limits().maximumTextCharacters()) {
                    throw limit(
                            sourceId,
                            "textCharacters",
                            characters,
                            session.limits().maximumTextCharacters());
                }
                if (!names.add(name)) {
                    throw metadata(sourceId, isKnown(name) ? name : "name", "duplicate");
                }
                if (isKnown(name)) {
                    known.put(name, value);
                } else {
                    ignored++;
                }
            }
        }
        return MetadataValues.parse(sourceId, known, ignored);
    }

    private static Map<Integer, MbTilesTileProfile> readTileProfiles(
            String sourceId,
            MbTilesSession session,
            CancellationToken cancellation,
            EncodedRasterFormat format)
            throws SQLException {
        TreeMap<Integer, Bounds> bounds = new TreeMap<>();
        Coordinate previous = null;
        long rowsSeen = 0;
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                """
                                SELECT zoom_level,tile_column,tile_row,
                                       typeof(zoom_level),typeof(tile_column),typeof(tile_row),
                                       typeof(tile_data),length(tile_data)
                                  FROM tiles
                                 ORDER BY zoom_level,tile_row,tile_column,rowid
                                """)) {
            while (rows.next()) {
                checkpoint(sourceId, cancellation, ++rowsSeen);
                if (rowsSeen > session.limits().maximumRows()) {
                    throw limit(sourceId, "rows", rowsSeen, session.limits().maximumRows());
                }
                int zoom = exactInteger(sourceId, rows, 1, 4, "zoom");
                int x = exactInteger(sourceId, rows, 2, 5, "x");
                int tmsY = exactInteger(sourceId, rows, 3, 6, "y");
                if (zoom < 0) {
                    throw tile(sourceId, "zoom", "range");
                }
                if (zoom > session.limits().maximumZoom()) {
                    throw limit(sourceId, "zoom", zoom, session.limits().maximumZoom());
                }
                int axis = 1 << zoom;
                if (x < 0 || x >= axis || tmsY < 0 || tmsY >= axis) {
                    throw tile(sourceId, x < 0 || x >= axis ? "x" : "y", "range");
                }
                Coordinate coordinate = new Coordinate(zoom, x, tmsY);
                if (coordinate.equals(previous)) {
                    throw tile(sourceId, "x", "duplicate");
                }
                previous = coordinate;
                long blobLength = rows.getLong(8);
                if (!"blob".equals(rows.getString(7)) || rows.wasNull() || blobLength <= 0) {
                    throw tile(sourceId, "data", "null");
                }
                if (blobLength > session.limits().maximumBlobBytes()) {
                    throw limit(
                            sourceId, "blobBytes", blobLength, session.limits().maximumBlobBytes());
                }
                int xyzY = axis - 1 - tmsY;
                bounds.computeIfAbsent(zoom, ignored -> new Bounds()).include(x, xyzY);
            }
        }
        if (bounds.isEmpty()) {
            throw unsupported(sourceId, "zoom");
        }
        if (bounds.size() > session.limits().maximumZoomLevels()) {
            throw limit(
                    sourceId, "zoomLevels", bounds.size(), session.limits().maximumZoomLevels());
        }
        LinkedHashMap<Integer, MbTilesTileProfile> profiles = new LinkedHashMap<>();
        bounds.forEach(
                (zoom, extent) -> {
                    if (extent.width() > session.limits().maximumMatrixAxis()
                            || extent.height() > session.limits().maximumMatrixAxis()) {
                        throw limit(
                                sourceId,
                                "matrixAxis",
                                Math.max(extent.width(), extent.height()),
                                session.limits().maximumMatrixAxis());
                    }
                    try {
                        int rasterWidth = Math.multiplyExact(extent.width(), 256);
                        int rasterHeight = Math.multiplyExact(extent.height(), 256);
                        if (rasterWidth <= 0 || rasterHeight <= 0) {
                            throw new ArithmeticException("non-positive raster");
                        }
                    } catch (ArithmeticException exception) {
                        throw tile(sourceId, "x", "range");
                    }
                    profiles.put(
                            zoom,
                            new MbTilesTileProfile(
                                    zoom,
                                    extent.minimumX,
                                    extent.minimumY,
                                    extent.maximumX,
                                    extent.maximumY,
                                    format));
                });
        return Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
    }

    private static int exactInteger(
            String sourceId, ResultSet rows, int valueIndex, int typeIndex, String field)
            throws SQLException {
        if (!"integer".equals(rows.getString(typeIndex))) {
            throw tile(sourceId, field, "range");
        }
        long value = rows.getLong(valueIndex);
        if (rows.wasNull() || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw tile(sourceId, field, "range");
        }
        return Math.toIntExact(value);
    }

    private static void checkpoint(String sourceId, CancellationToken cancellation, long row) {
        if ((row & 4_095) == 1) {
            MbTilesFailures.checkpoint(sourceId, cancellation::isCancellationRequested, "metadata");
        }
    }

    private static boolean isKnown(String name) {
        return switch (name) {
            case "name",
                    "format",
                    "bounds",
                    "center",
                    "minzoom",
                    "maxzoom",
                    "type",
                    "version",
                    "description",
                    "attribution" ->
                    true;
            default -> false;
        };
    }

    private static String role(String table, String column) {
        if ("metadata".equals(table)) {
            return "name";
        }
        return switch (column) {
            case "zoom_level" -> "zoom";
            case "tile_column" -> "x";
            case "tile_row" -> "y";
            case "tile_data" -> "data";
            default -> "columns";
        };
    }

    private static SourceException schema(
            String sourceId, String object, String field, String reason) {
        return MbTilesFailures.failure(
                sourceId,
                "MBTILES_SCHEMA_INVALID",
                "MBTiles schema is invalid",
                Map.of("object", object, "field", field, "reason", reason));
    }

    private static SourceException metadata(String sourceId, String field, String reason) {
        return MbTilesFailures.failure(
                sourceId,
                "MBTILES_METADATA_INVALID",
                "MBTiles metadata is invalid",
                Map.of("field", field, "reason", reason));
    }

    private static SourceException tile(String sourceId, String field, String reason) {
        return MbTilesFailures.failure(
                sourceId,
                "MBTILES_TILE_INVALID",
                "MBTiles tile is invalid",
                Map.of("field", field, "reason", reason));
    }

    private static SourceException unsupported(String sourceId, String construct) {
        return MbTilesFailures.failure(
                sourceId,
                "MBTILES_PROFILE_UNSUPPORTED",
                "MBTiles construct is outside the supported profile",
                Map.of("construct", construct));
    }

    private static SourceException limit(
            String sourceId, String name, long requested, long maximum) {
        return MbTilesFailures.failure(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "MBTiles operation limit exceeded",
                Map.of(
                        "scope",
                        "mbtilesOpen",
                        "limit",
                        name,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }

    record Snapshot(MbTilesMetadata metadata, Map<Integer, MbTilesTileProfile> profiles) {
        Snapshot {
            profiles = Collections.unmodifiableMap(new LinkedHashMap<>(profiles));
        }
    }

    private record Column(String name, String type) {}

    private record Coordinate(int zoom, int x, int tmsY) {}

    private static final class Bounds {
        private int minimumX = Integer.MAX_VALUE;
        private int minimumY = Integer.MAX_VALUE;
        private int maximumX = Integer.MIN_VALUE;
        private int maximumY = Integer.MIN_VALUE;

        private void include(int x, int y) {
            minimumX = Math.min(minimumX, x);
            minimumY = Math.min(minimumY, y);
            maximumX = Math.max(maximumX, x);
            maximumY = Math.max(maximumY, y);
        }

        private int width() {
            return Math.addExact(Math.subtractExact(maximumX, minimumX), 1);
        }

        private int height() {
            return Math.addExact(Math.subtractExact(maximumY, minimumY), 1);
        }
    }

    private record MetadataValues(
            String name,
            EncodedRasterFormat format,
            Optional<Envelope> bounds,
            Optional<MbTilesCenter> center,
            OptionalInt minimumZoom,
            OptionalInt maximumZoom,
            Optional<String> type,
            Optional<String> revision,
            Optional<String> description,
            Optional<String> attribution,
            int ignored) {
        private static MetadataValues parse(
                String sourceId, Map<String, String> values, int ignored) {
            String name = required(sourceId, values, "name");
            if (name.isBlank()) {
                throw metadata(sourceId, "name", "value");
            }
            EncodedRasterFormat format =
                    parseFormat(sourceId, required(sourceId, values, "format"));
            Optional<Envelope> bounds =
                    Optional.ofNullable(values.get("bounds"))
                            .map(value -> parseBounds(sourceId, value));
            Optional<MbTilesCenter> center =
                    Optional.ofNullable(values.get("center"))
                            .map(value -> parseCenter(sourceId, value));
            OptionalInt minimum = parseZoom(sourceId, values.get("minzoom"), "minzoom");
            OptionalInt maximum = parseZoom(sourceId, values.get("maxzoom"), "maxzoom");
            if (minimum.isPresent()
                    && maximum.isPresent()
                    && minimum.getAsInt() > maximum.getAsInt()) {
                throw metadata(sourceId, "minzoom", "order");
            }
            return new MetadataValues(
                    name,
                    format,
                    bounds,
                    center,
                    minimum,
                    maximum,
                    optional(values, "type"),
                    optional(values, "version"),
                    optional(values, "description"),
                    optional(values, "attribution"),
                    ignored);
        }

        private MbTilesMetadata toMetadata(String sourceId, List<Integer> zooms) {
            if (minimumZoom.isPresent() && zooms.getFirst() < minimumZoom.getAsInt()) {
                throw metadata(sourceId, "minzoom", "range");
            }
            if (maximumZoom.isPresent() && zooms.getLast() > maximumZoom.getAsInt()) {
                throw metadata(sourceId, "maxzoom", "range");
            }
            DiagnosticReport report =
                    ignored == 0
                            ? DiagnosticReport.empty()
                            : new DiagnosticReport(
                                    List.of(
                                            new SourceDiagnostic(
                                                    "MBTILES_METADATA_IGNORED",
                                                    DiagnosticSeverity.WARNING,
                                                    sourceId,
                                                    Optional.of(DiagnosticLocation.empty()),
                                                    "Unknown MBTiles metadata was ignored",
                                                    Map.of("count", Integer.toString(ignored)))),
                                    0);
            return new MbTilesMetadata(
                    name,
                    format,
                    bounds,
                    center,
                    minimumZoom,
                    maximumZoom,
                    type,
                    revision,
                    description,
                    attribution,
                    zooms,
                    report);
        }

        private static String required(String sourceId, Map<String, String> values, String field) {
            String value = values.get(field);
            if (value == null) {
                throw metadata(sourceId, field, "missing");
            }
            return value;
        }

        private static Optional<String> optional(Map<String, String> values, String key) {
            return Optional.ofNullable(values.get(key));
        }

        private static EncodedRasterFormat parseFormat(String sourceId, String value) {
            return switch (value) {
                case "png", "image/png" -> EncodedRasterFormat.PNG;
                case "jpg", "image/jpeg" -> EncodedRasterFormat.JPEG;
                default -> throw unsupported(sourceId, "format");
            };
        }

        private static Envelope parseBounds(String sourceId, String value) {
            double[] parts = doubles(sourceId, value, 4, "bounds");
            if (parts[0] < -180 || parts[2] > 180 || parts[1] < -90 || parts[3] > 90) {
                throw metadata(sourceId, "bounds", "range");
            }
            try {
                return new Envelope(parts[0], parts[1], parts[2], parts[3]);
            } catch (IllegalArgumentException exception) {
                throw metadata(sourceId, "bounds", "order");
            }
        }

        private static MbTilesCenter parseCenter(String sourceId, String value) {
            double[] parts = doubles(sourceId, value, 3, "center");
            if (parts[2] != Math.rint(parts[2])) {
                throw metadata(sourceId, "center", "syntax");
            }
            try {
                return new MbTilesCenter(parts[0], parts[1], Math.toIntExact((long) parts[2]));
            } catch (IllegalArgumentException | ArithmeticException exception) {
                throw metadata(sourceId, "center", "range");
            }
        }

        private static OptionalInt parseZoom(String sourceId, String value, String field) {
            if (value == null) {
                return OptionalInt.empty();
            }
            try {
                int zoom = Integer.parseInt(value);
                if (zoom < 0 || zoom > 22) {
                    throw metadata(sourceId, field, "range");
                }
                return OptionalInt.of(zoom);
            } catch (NumberFormatException exception) {
                throw metadata(sourceId, field, "syntax");
            }
        }

        private static double[] doubles(String sourceId, String value, int expected, String field) {
            String[] text = value.split(",", -1);
            if (text.length != expected) {
                throw metadata(sourceId, field, "syntax");
            }
            double[] parsed = new double[expected];
            for (int index = 0; index < expected; index++) {
                try {
                    parsed[index] = Double.parseDouble(text[index]);
                } catch (NumberFormatException exception) {
                    throw metadata(sourceId, field, "syntax");
                }
                if (!Double.isFinite(parsed[index])) {
                    throw metadata(sourceId, field, "range");
                }
            }
            return parsed;
        }
    }
}
