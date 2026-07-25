package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeType;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

record GeoPackageAttributeColumn(String name, Kind kind, boolean nullable, long declaredMaximum) {
    private static final Pattern DECLARATION = Pattern.compile("([A-Z]+)(?:\\(([1-9][0-9]*)\\))?");

    static GeoPackageAttributeColumn parse(
            String sourceId,
            String name,
            String declaration,
            boolean nullable,
            GeoPackageLimits limits) {
        if (declaration == null) {
            throw schema(sourceId);
        }
        Matcher matcher = DECLARATION.matcher(declaration.toUpperCase(Locale.ROOT));
        if (!matcher.matches()) {
            throw schema(sourceId);
        }
        Kind kind =
                switch (matcher.group(1)) {
                    case "BOOLEAN" -> Kind.BOOLEAN;
                    case "TINYINT" -> Kind.TINY_INTEGER;
                    case "SMALLINT" -> Kind.SMALL_INTEGER;
                    case "MEDIUMINT" -> Kind.MEDIUM_INTEGER;
                    case "INT", "INTEGER" -> Kind.INTEGER;
                    case "FLOAT" -> Kind.FLOAT;
                    case "REAL", "DOUBLE" -> Kind.REAL;
                    case "TEXT" -> Kind.TEXT;
                    case "BLOB" -> Kind.BLOB;
                    case "DATE" -> Kind.DATE;
                    case "DATETIME" -> Kind.DATETIME;
                    default -> throw schema(sourceId);
                };
        String size = matcher.group(2);
        if (size != null && kind != Kind.TEXT && kind != Kind.BLOB) {
            throw schema(sourceId);
        }
        long maximum = Long.MAX_VALUE;
        if (size != null) {
            try {
                maximum = Long.parseLong(size);
            } catch (NumberFormatException exception) {
                throw schema(sourceId);
            }
            long effective =
                    kind == Kind.TEXT
                            ? limits.maximumTextValueCharacters()
                            : limits.maximumBlobBytes();
            if (maximum > effective) {
                throw schema(sourceId);
            }
        }
        return new GeoPackageAttributeColumn(name, kind, nullable, maximum);
    }

    AttributeField field() {
        return new AttributeField(
                name,
                switch (kind) {
                    case BOOLEAN -> AttributeType.LOGICAL;
                    case TINY_INTEGER, SMALL_INTEGER, MEDIUM_INTEGER, INTEGER ->
                            AttributeType.INTEGER;
                    case FLOAT, REAL -> AttributeType.FLOATING;
                    case TEXT, DATETIME -> AttributeType.TEXT;
                    case BLOB -> AttributeType.BINARY;
                    case DATE -> AttributeType.DATE;
                },
                nullable);
    }

    private static io.github.mundanej.map.api.SourceException schema(String sourceId) {
        return GeoPackageFailures.failure(
                sourceId,
                "GEOPACKAGE_SCHEMA_INVALID",
                "GeoPackage schema is invalid",
                Map.of("object", "selectedTable", "field", "columns", "reason", "type"));
    }

    enum Kind {
        BOOLEAN,
        TINY_INTEGER,
        SMALL_INTEGER,
        MEDIUM_INTEGER,
        INTEGER,
        FLOAT,
        REAL,
        TEXT,
        BLOB,
        DATE,
        DATETIME
    }
}
