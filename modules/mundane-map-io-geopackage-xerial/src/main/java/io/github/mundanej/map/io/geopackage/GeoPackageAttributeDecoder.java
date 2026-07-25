package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.regex.Pattern;

final class GeoPackageAttributeDecoder {
    private static final Pattern DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern DATE_TIME =
            Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}\\.[0-9]{3}Z");

    private GeoPackageAttributeDecoder() {}

    static DecodedAttribute decode(
            String sourceId,
            ResultSet row,
            int valueIndex,
            int storageIndex,
            int lengthIndex,
            GeoPackageAttributeColumn column,
            GeoPackageLimits limits)
            throws SQLException {
        String storage = row.getString(storageIndex);
        if ("null".equals(storage)) {
            if (!column.nullable()) {
                throw invalid(sourceId, "null");
            }
            return new DecodedAttribute(AttributeNull.INSTANCE, 0, 1);
        }
        return switch (column.kind()) {
            case BOOLEAN -> integer(sourceId, row, valueIndex, storage, 0, 1, value -> value == 1);
            case TINY_INTEGER ->
                    integer(
                            sourceId,
                            row,
                            valueIndex,
                            storage,
                            Byte.MIN_VALUE,
                            Byte.MAX_VALUE,
                            value -> value);
            case SMALL_INTEGER ->
                    integer(
                            sourceId,
                            row,
                            valueIndex,
                            storage,
                            Short.MIN_VALUE,
                            Short.MAX_VALUE,
                            value -> value);
            case MEDIUM_INTEGER ->
                    integer(
                            sourceId,
                            row,
                            valueIndex,
                            storage,
                            Integer.MIN_VALUE,
                            Integer.MAX_VALUE,
                            value -> value);
            case INTEGER ->
                    integer(
                            sourceId,
                            row,
                            valueIndex,
                            storage,
                            Long.MIN_VALUE,
                            Long.MAX_VALUE,
                            value -> value);
            case FLOAT -> floating(sourceId, row, valueIndex, storage, true);
            case REAL -> floating(sourceId, row, valueIndex, storage, false);
            case TEXT ->
                    text(
                            sourceId,
                            row,
                            valueIndex,
                            lengthIndex,
                            storage,
                            column,
                            limits,
                            TextKind.TEXT);
            case DATE ->
                    text(
                            sourceId,
                            row,
                            valueIndex,
                            lengthIndex,
                            storage,
                            column,
                            limits,
                            TextKind.DATE);
            case DATETIME ->
                    text(
                            sourceId,
                            row,
                            valueIndex,
                            lengthIndex,
                            storage,
                            column,
                            limits,
                            TextKind.DATE_TIME);
            case BLOB -> blob(sourceId, row, valueIndex, lengthIndex, storage, column, limits);
        };
    }

    private static DecodedAttribute integer(
            String sourceId,
            ResultSet row,
            int index,
            String storage,
            long minimum,
            long maximum,
            java.util.function.LongFunction<Object> conversion)
            throws SQLException {
        if (!"integer".equals(storage)) {
            throw invalid(sourceId, "storageClass");
        }
        long value = row.getLong(index);
        if (value < minimum || value > maximum) {
            throw invalid(sourceId, "range");
        }
        return new DecodedAttribute(conversion.apply(value), 0, 8);
    }

    private static DecodedAttribute floating(
            String sourceId, ResultSet row, int index, String storage, boolean requireFloat)
            throws SQLException {
        if (!"real".equals(storage)) {
            throw invalid(sourceId, "storageClass");
        }
        double value = row.getDouble(index);
        if (!Double.isFinite(value) || (requireFloat && (double) (float) value != value)) {
            throw invalid(sourceId, "range");
        }
        return new DecodedAttribute(value, 0, 8);
    }

    private static DecodedAttribute text(
            String sourceId,
            ResultSet row,
            int index,
            int lengthIndex,
            String storage,
            GeoPackageAttributeColumn column,
            GeoPackageLimits limits,
            TextKind kind)
            throws SQLException {
        if (!"text".equals(storage)) {
            throw invalid(sourceId, "storageClass");
        }
        long encodedLength = row.getLong(lengthIndex);
        long encodedMaximum =
                Math.min(
                        limits.maximumBlobBytes(),
                        Math.multiplyExact((long) limits.maximumTextValueCharacters(), 4));
        if (row.wasNull() || encodedLength < 0 || encodedLength > encodedMaximum) {
            throw invalid(sourceId, "range");
        }
        byte[] encoded = row.getBytes(index);
        if (encoded == null || encoded.length != encodedLength) {
            throw invalid(sourceId, "encoding");
        }
        String value = decodeUtf8(sourceId, encoded);
        long maximum = Math.min(column.declaredMaximum(), limits.maximumTextValueCharacters());
        if (value.length() > maximum) {
            throw invalid(sourceId, "range");
        }
        Object canonical =
                switch (kind) {
                    case TEXT -> value;
                    case DATE -> date(sourceId, value);
                    case DATE_TIME -> dateTime(sourceId, value);
                };
        return new DecodedAttribute(canonical, value.length(), 2L * value.length());
    }

    private static DecodedAttribute blob(
            String sourceId,
            ResultSet row,
            int index,
            int lengthIndex,
            String storage,
            GeoPackageAttributeColumn column,
            GeoPackageLimits limits)
            throws SQLException {
        if (!"blob".equals(storage)) {
            throw invalid(sourceId, "storageClass");
        }
        long maximum = Math.min(column.declaredMaximum(), limits.maximumBlobBytes());
        long length = row.getLong(lengthIndex);
        if (row.wasNull() || length < 0 || length > maximum) {
            throw invalid(sourceId, "range");
        }
        byte[] bytes = row.getBytes(index);
        if (bytes == null || bytes.length != length) {
            throw invalid(sourceId, "value");
        }
        return new DecodedAttribute(new AttributeBytes(bytes), 0, 2L * bytes.length);
    }

    private static String decodeUtf8(String sourceId, byte[] encoded) {
        if (encoded == null) {
            throw invalid(sourceId, "encoding");
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw invalid(sourceId, "encoding");
        }
    }

    private static LocalDate date(String sourceId, String value) {
        if (!DATE.matcher(value).matches()) {
            throw invalid(sourceId, "value");
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeException exception) {
            throw invalid(sourceId, "value");
        }
    }

    private static String dateTime(String sourceId, String value) {
        if (!DATE_TIME.matcher(value).matches()) {
            throw invalid(sourceId, "value");
        }
        try {
            Instant.parse(value);
            return value;
        } catch (DateTimeException exception) {
            throw invalid(sourceId, "value");
        }
    }

    private static io.github.mundanej.map.api.SourceException invalid(
            String sourceId, String reason) {
        return GeoPackageFailures.failure(
                sourceId,
                "GEOPACKAGE_RECORD_INVALID",
                "GeoPackage feature record is invalid",
                Map.of("field", "attribute", "reason", reason));
    }

    record DecodedAttribute(Object value, long textCharacters, long ownedBytes) {}

    private enum TextKind {
        TEXT,
        DATE,
        DATE_TIME
    }
}
