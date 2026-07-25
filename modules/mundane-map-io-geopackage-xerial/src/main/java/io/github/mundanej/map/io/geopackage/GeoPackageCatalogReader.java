package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.core.CrsRegistry;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

final class GeoPackageCatalogReader {
    private static final String FEATURE_SQL =
            """
            SELECT c.table_name, g.column_name, g.geometry_type_name, g.srs_id, g.z, g.m,
                   c.min_x, c.min_y, c.max_x, c.max_y, c.srs_id
              FROM gpkg_contents c
              JOIN gpkg_geometry_columns g ON g.table_name = c.table_name
             WHERE c.data_type = 'features'
             ORDER BY c.table_name
            """;

    private GeoPackageCatalogReader() {}

    static GeoPackageCatalog read(
            String sourceId,
            GeoPackageSession session,
            CancellationToken cancellation,
            boolean onlyPointTypes) {
        session.beforeOperation(cancellation, "inspect");
        try {
            validateRequiredTables(sourceId, session);
            validateExtensions(sourceId, session);
            Map<Integer, CrsMetadata> crs = readCrs(sourceId, session);
            List<GeoPackageFeatureTable> tables = new ArrayList<>();
            LinkedHashSet<String> contentTables = new LinkedHashSet<>();
            try (Statement statement = session.connection().createStatement();
                    ResultSet rows = statement.executeQuery(FEATURE_SQL)) {
                while (rows.next()) {
                    checkpoint(sourceId, cancellation);
                    String table = identifier(sourceId, rows.getString(1), session.limits());
                    String geometryColumn =
                            identifier(sourceId, rows.getString(2), session.limits());
                    String geometryTypeText = rows.getString(3).toUpperCase(Locale.ROOT);
                    int srsId = rows.getInt(4);
                    if (rows.wasNull() || rows.getInt(11) != srsId || rows.wasNull()) {
                        throw schema(sourceId, "contents", "srsId", "reference");
                    }
                    if (rows.getInt(5) != 0 || rows.getInt(6) != 0) {
                        throw unsupported(sourceId, "dimension");
                    }
                    if (!geometryTypeText.equals("POINT")
                            && !geometryTypeText.equals("MULTIPOINT")
                            && !geometryTypeText.equals("GEOMETRY")) {
                        if (onlyPointTypes) {
                            throw unsupported(sourceId, "geometryType");
                        }
                        continue;
                    }
                    GeoPackageGeometryType geometryType =
                            switch (geometryTypeText) {
                                case "POINT" -> GeoPackageGeometryType.POINT;
                                case "MULTIPOINT" -> GeoPackageGeometryType.MULTI_POINT;
                                case "GEOMETRY" -> GeoPackageGeometryType.GEOMETRY;
                                default -> throw unsupported(sourceId, "geometryType");
                            };
                    CrsMetadata metadata = crs.get(srsId);
                    if (metadata == null) {
                        throw schema(sourceId, "geometryColumns", "srsId", "reference");
                    }
                    String primaryKey = primaryKey(sourceId, session, table, geometryColumn);
                    contentTables.add(table);
                    Optional<Envelope> extent = extent(sourceId, rows);
                    long count = rowCount(sourceId, session, table);
                    tables.add(
                            new GeoPackageFeatureTable(
                                    table,
                                    geometryColumn,
                                    geometryType,
                                    primaryKey,
                                    new AttributeSchema(List.of()),
                                    srsId,
                                    metadata,
                                    extent,
                                    OptionalLong.of(count)));
                    if (tables.size() > session.limits().maximumSchemaObjects()) {
                        throw limit(
                                sourceId,
                                "schemaObjects",
                                tables.size(),
                                session.limits().maximumSchemaObjects());
                    }
                }
            }
            validateContentsProfile(sourceId, session, contentTables);
            validateObjectInventory(sourceId, session, contentTables);
            session.afterOperation(cancellation, "publish");
            return new GeoPackageCatalog(tables, List.of(), DiagnosticReport.empty());
        } catch (SQLException exception) {
            throw session.queryFailure(exception, "catalog");
        }
    }

    private static void validateRequiredTables(String sourceId, GeoPackageSession session)
            throws SQLException {
        for (String table :
                List.of("gpkg_spatial_ref_sys", "gpkg_contents", "gpkg_geometry_columns")) {
            try (PreparedStatement statement =
                    session.connection()
                            .prepareStatement("SELECT type FROM sqlite_schema WHERE name=?"); ) {
                statement.setString(1, table);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next() || !"table".equals(result.getString(1)) || result.next()) {
                        throw schema(sourceId, role(table), "name", "missing");
                    }
                }
            }
        }
        validateColumns(
                sourceId,
                session,
                "gpkg_spatial_ref_sys",
                "spatialRefSys",
                List.of(
                        new Column("srs_name", "TEXT", true, 0),
                        new Column("srs_id", "INTEGER", true, 1),
                        new Column("organization", "TEXT", true, 0),
                        new Column("organization_coordsys_id", "INTEGER", true, 0),
                        new Column("definition", "TEXT", true, 0),
                        new Column("description", "TEXT", false, 0)));
        validateColumns(
                sourceId,
                session,
                "gpkg_contents",
                "contents",
                List.of(
                        new Column("table_name", "TEXT", true, 1),
                        new Column("data_type", "TEXT", true, 0),
                        new Column("identifier", "TEXT", false, 0),
                        new Column("description", "TEXT", false, 0),
                        new Column("last_change", "DATETIME", true, 0),
                        new Column("min_x", "DOUBLE", false, 0),
                        new Column("min_y", "DOUBLE", false, 0),
                        new Column("max_x", "DOUBLE", false, 0),
                        new Column("max_y", "DOUBLE", false, 0),
                        new Column("srs_id", "INTEGER", false, 0)));
        validateColumns(
                sourceId,
                session,
                "gpkg_geometry_columns",
                "geometryColumns",
                List.of(
                        new Column("table_name", "TEXT", true, 1),
                        new Column("column_name", "TEXT", true, 2),
                        new Column("geometry_type_name", "TEXT", true, 0),
                        new Column("srs_id", "INTEGER", true, 0),
                        new Column("z", "TINYINT", true, 0),
                        new Column("m", "TINYINT", true, 0)));
        requireForeignKey(
                sourceId,
                session,
                "gpkg_contents",
                "contents",
                "srsId",
                "srs_id",
                "gpkg_spatial_ref_sys",
                "srs_id");
        requireForeignKey(
                sourceId,
                session,
                "gpkg_geometry_columns",
                "geometryColumns",
                "tableName",
                "table_name",
                "gpkg_contents",
                "table_name");
        requireForeignKey(
                sourceId,
                session,
                "gpkg_geometry_columns",
                "geometryColumns",
                "srsId",
                "srs_id",
                "gpkg_spatial_ref_sys",
                "srs_id");
    }

    private static void validateExtensions(String sourceId, GeoPackageSession session)
            throws SQLException {
        try (PreparedStatement statement =
                        session.connection()
                                .prepareStatement(
                                        "SELECT type FROM sqlite_schema WHERE name='gpkg_extensions'");
                ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                return;
            }
            if (!"table".equals(result.getString(1)) || result.next()) {
                throw unsupported(sourceId, "extension");
            }
        }
        try (Statement statement = session.connection().createStatement();
                ResultSet rows = statement.executeQuery("SELECT 1 FROM gpkg_extensions LIMIT 1")) {
            if (rows.next()) {
                throw unsupported(sourceId, "extension");
            }
        }
    }

    private static Map<Integer, CrsMetadata> readCrs(String sourceId, GeoPackageSession session)
            throws SQLException {
        java.util.LinkedHashMap<Integer, CrsMetadata> values = new java.util.LinkedHashMap<>();
        String sql =
                """
                SELECT srs_id, organization, organization_coordsys_id, definition
                  FROM gpkg_spatial_ref_sys ORDER BY srs_id
                """;
        try (Statement statement = session.connection().createStatement();
                ResultSet rows = statement.executeQuery(sql)) {
            long rowCount = 0;
            long textCharacters = 0;
            long ownedBytes = 0;
            while (rows.next()) {
                rowCount++;
                if (rowCount > session.limits().maximumMetadataRows()) {
                    throw limit(
                            sourceId,
                            "metadataRows",
                            rowCount,
                            session.limits().maximumMetadataRows());
                }
                int id = rows.getInt(1);
                String organization = rows.getString(2);
                int organizationId = rows.getInt(3);
                String definition = rows.getString(4);
                if (organization == null
                        || organization.isBlank()
                        || organization.length() > session.limits().maximumTextValueCharacters()
                        || definition == null
                        || definition.isBlank()
                        || definition.length() > session.limits().maximumTextValueCharacters()
                        || values.containsKey(id)) {
                    throw schema(sourceId, "spatialRefSys", "definition", "value");
                }
                textCharacters =
                        Math.addExact(
                                textCharacters,
                                Math.addExact(organization.length(), definition.length()));
                ownedBytes =
                        Math.addExact(
                                ownedBytes,
                                2L * Math.addExact(organization.length(), definition.length()));
                if (textCharacters > session.limits().maximumTextCharacters()) {
                    throw limit(
                            sourceId,
                            "textCharacters",
                            textCharacters,
                            session.limits().maximumTextCharacters());
                }
                if (ownedBytes > session.limits().maximumOwnedBytes()) {
                    throw limit(
                            sourceId,
                            "ownedBytes",
                            ownedBytes,
                            session.limits().maximumOwnedBytes());
                }
                CrsMetadata metadata;
                if ("EPSG".equalsIgnoreCase(organization)
                        && id == organizationId
                        && (id == 4326 || id == 3857)) {
                    String identifier = "EPSG:" + id;
                    metadata =
                            CrsMetadata.recognized(
                                    CrsRegistry.level1().resolve(identifier),
                                    Optional.of(identifier),
                                    Optional.of(definition));
                } else {
                    metadata =
                            CrsMetadata.unknown(Optional.of("GPKG:" + id), Optional.of(definition));
                }
                values.put(id, metadata);
            }
        }
        if (!values.containsKey(-1) || !values.containsKey(0) || !values.containsKey(4326)) {
            throw schema(sourceId, "spatialRefSys", "srsId", "missing");
        }
        validateRequiredCrsRelationship(sourceId, session, -1, "NONE", -1);
        validateRequiredCrsRelationship(sourceId, session, 0, "NONE", 0);
        validateRequiredCrsRelationship(sourceId, session, 4326, "EPSG", 4326);
        return Map.copyOf(values);
    }

    private static String primaryKey(
            String sourceId, GeoPackageSession session, String table, String geometryColumn)
            throws SQLException {
        String primaryKey = null;
        boolean geometryFound = false;
        try (Statement statement = session.connection().createStatement();
                ResultSet columns =
                        statement.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
            int count = 0;
            while (columns.next()) {
                count++;
                String name = identifier(sourceId, columns.getString("name"), session.limits());
                String type = columns.getString("type");
                int pk = columns.getInt("pk");
                if (name.equals(geometryColumn)) {
                    geometryFound = true;
                    if (!"BLOB".equalsIgnoreCase(type) || columns.getInt("notnull") != 1) {
                        throw schema(sourceId, "selectedTable", "geometry", "type");
                    }
                }
                if (pk != 0) {
                    if (primaryKey != null || pk != 1 || !"INTEGER".equalsIgnoreCase(type)) {
                        throw schema(sourceId, "selectedTable", "primaryKey", "constraint");
                    }
                    primaryKey = name;
                } else if (!name.equals(geometryColumn)) {
                    throw unsupported(sourceId, "contentType");
                }
            }
            if (count > session.limits().maximumColumns()) {
                throw limit(sourceId, "columns", count, session.limits().maximumColumns());
            }
        }
        if (primaryKey == null || !geometryFound) {
            throw schema(sourceId, "selectedTable", "primaryKey", "missing");
        }
        return primaryKey;
    }

    private static Optional<Envelope> extent(String sourceId, ResultSet rows) throws SQLException {
        Double minX = nullableDouble(rows, 7);
        Double minY = nullableDouble(rows, 8);
        Double maxX = nullableDouble(rows, 9);
        Double maxY = nullableDouble(rows, 10);
        if (minX == null && minY == null && maxX == null && maxY == null) {
            return Optional.empty();
        }
        if (minX == null || minY == null || maxX == null || maxY == null) {
            throw schema(sourceId, "contents", "minX", "nullability");
        }
        try {
            return Optional.of(new Envelope(minX, minY, maxX, maxY));
        } catch (IllegalArgumentException exception) {
            throw schema(sourceId, "contents", "minX", "value");
        }
    }

    private static void validateColumns(
            String sourceId,
            GeoPackageSession session,
            String table,
            String role,
            List<Column> expected)
            throws SQLException {
        List<Column> actual = new ArrayList<>();
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery("PRAGMA table_xinfo(" + quote(table) + ")")) {
            while (rows.next()) {
                if (rows.getInt("hidden") != 0) {
                    throw schema(sourceId, role, "columns", "constraint");
                }
                actual.add(
                        new Column(
                                rows.getString("name"),
                                rows.getString("type").toUpperCase(Locale.ROOT),
                                rows.getInt("notnull") == 1,
                                rows.getInt("pk")));
            }
        }
        if (!actual.equals(expected)) {
            throw schema(sourceId, role, "columns", "constraint");
        }
    }

    private static void validateRequiredCrsRelationship(
            String sourceId,
            GeoPackageSession session,
            int srsId,
            String organization,
            int organizationCode)
            throws SQLException {
        try (PreparedStatement statement =
                session.connection()
                        .prepareStatement(
                                """
                                SELECT organization, organization_coordsys_id
                                  FROM gpkg_spatial_ref_sys WHERE srs_id=?
                                """)) {
            statement.setInt(1, srsId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()
                        || !organization.equalsIgnoreCase(row.getString(1))
                        || row.getInt(2) != organizationCode
                        || row.wasNull()
                        || row.next()) {
                    throw schema(sourceId, "spatialRefSys", "organizationCode", "constraint");
                }
            }
        }
    }

    private static void requireForeignKey(
            String sourceId,
            GeoPackageSession session,
            String table,
            String role,
            String field,
            String from,
            String referencedTable,
            String to)
            throws SQLException {
        boolean found = false;
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery("PRAGMA foreign_key_list(" + quote(table) + ")")) {
            while (rows.next()) {
                if (from.equals(rows.getString("from"))
                        && referencedTable.equals(rows.getString("table"))
                        && to.equals(rows.getString("to"))) {
                    found = true;
                }
            }
        }
        if (!found) {
            throw schema(sourceId, role, field, "constraint");
        }
    }

    private static void validateContentsProfile(
            String sourceId, GeoPackageSession session, java.util.Set<String> featureTables)
            throws SQLException {
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                "SELECT table_name,data_type FROM gpkg_contents ORDER BY table_name")) {
            long count = 0;
            while (rows.next()) {
                count++;
                String table = identifier(sourceId, rows.getString(1), session.limits());
                String type = rows.getString(2);
                if (!"features".equals(type) || !featureTables.contains(table)) {
                    throw unsupported(sourceId, "contentType");
                }
                if (count > session.limits().maximumSchemaObjects()) {
                    throw limit(
                            sourceId,
                            "schemaObjects",
                            count,
                            session.limits().maximumSchemaObjects());
                }
            }
        }
    }

    private static void validateObjectInventory(
            String sourceId, GeoPackageSession session, java.util.Set<String> contentTables)
            throws SQLException {
        java.util.Set<String> allowedTables = new java.util.HashSet<>(contentTables);
        allowedTables.add("gpkg_spatial_ref_sys");
        allowedTables.add("gpkg_contents");
        allowedTables.add("gpkg_geometry_columns");
        allowedTables.add("gpkg_extensions");
        try (Statement statement = session.connection().createStatement();
                ResultSet rows =
                        statement.executeQuery(
                                """
                                SELECT type,name,tbl_name
                                  FROM sqlite_schema
                                 WHERE name NOT LIKE 'sqlite_%'
                                 ORDER BY type,name
                                """)) {
            long count = 0;
            while (rows.next()) {
                count++;
                String type = rows.getString(1);
                String name = identifier(sourceId, rows.getString(2), session.limits());
                String table = identifier(sourceId, rows.getString(3), session.limits());
                if ("table".equals(type)) {
                    if (!allowedTables.contains(name) || !name.equals(table)) {
                        throw unsupported(sourceId, "contentType");
                    }
                } else if ("index".equals(type) || "trigger".equals(type)) {
                    if (!allowedTables.contains(table)) {
                        throw unsupported(sourceId, "extension");
                    }
                } else {
                    throw schema(sourceId, "selectedTable", "kind", "view");
                }
                if (count > session.limits().maximumSchemaObjects()) {
                    throw limit(
                            sourceId,
                            "schemaObjects",
                            count,
                            session.limits().maximumSchemaObjects());
                }
            }
        }
        try (Statement statement = session.connection().createStatement();
                ResultSet rows = statement.executeQuery("PRAGMA database_list")) {
            boolean main = false;
            while (rows.next()) {
                String name = rows.getString("name");
                if ("main".equals(name) && !main) {
                    main = true;
                } else if (!"temp".equals(name)) {
                    throw unsupported(sourceId, "extension");
                }
            }
            if (!main) {
                throw schema(sourceId, "selectedTable", "kind", "missing");
            }
        }
    }

    private static Double nullableDouble(ResultSet rows, int index) throws SQLException {
        double value = rows.getDouble(index);
        return rows.wasNull() ? null : value;
    }

    private static long rowCount(String sourceId, GeoPackageSession session, String table)
            throws SQLException {
        try (Statement statement = session.connection().createStatement();
                ResultSet result = statement.executeQuery("SELECT count(*) FROM " + quote(table))) {
            if (!result.next()) {
                throw schema(sourceId, "selectedTable", "rowOrder", "value");
            }
            long count = result.getLong(1);
            if (count < 0 || count > session.limits().maximumRows()) {
                throw limit(sourceId, "rows", count, session.limits().maximumRows());
            }
            return count;
        }
    }

    static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private static String identifier(String sourceId, String value, GeoPackageLimits limits) {
        if (value == null
                || value.isBlank()
                || value.length() > limits.maximumIdentifierCharacters()
                || value.indexOf('\0') >= 0) {
            throw schema(sourceId, "selectedTable", "columns", "value");
        }
        return value;
    }

    private static String role(String table) {
        return switch (table) {
            case "gpkg_spatial_ref_sys" -> "spatialRefSys";
            case "gpkg_contents" -> "contents";
            case "gpkg_geometry_columns" -> "geometryColumns";
            default -> "selectedTable";
        };
    }

    private static void checkpoint(String sourceId, CancellationToken cancellation) {
        GeoPackageFailures.checkpoint(sourceId, cancellation::isCancellationRequested, "catalog");
    }

    private static io.github.mundanej.map.api.SourceException schema(
            String sourceId, String object, String field, String reason) {
        return GeoPackageFailures.failure(
                sourceId,
                "GEOPACKAGE_SCHEMA_INVALID",
                "GeoPackage schema is invalid",
                Map.of("object", object, "field", field, "reason", reason));
    }

    private static io.github.mundanej.map.api.SourceException unsupported(
            String sourceId, String construct) {
        return GeoPackageFailures.failure(
                sourceId,
                "GEOPACKAGE_PROFILE_UNSUPPORTED",
                "GeoPackage construct is outside the supported profile",
                Map.of("construct", construct));
    }

    private static io.github.mundanej.map.api.SourceException limit(
            String sourceId, String name, long requested, long maximum) {
        return GeoPackageFailures.failure(
                sourceId,
                "SOURCE_LIMIT_EXCEEDED",
                "GeoPackage operation limit exceeded",
                Map.of(
                        "scope",
                        "geopackageOpen",
                        "limit",
                        name,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)));
    }

    private record Column(String name, String type, boolean notNull, int primaryKeyOrder) {}
}
