package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Eager bounded DBF header/schema validation and table opening. */
final class DbfReader {
    private static final int HEADER_BYTES = 32;
    private static final int DESCRIPTOR_BYTES = 32;

    record Result(DbfTable table) {}

    private DbfReader() {}

    static Result read(
            String source,
            Path dbf,
            Path cpg,
            ShapefileFileAccess access,
            ShxIndex index,
            ShapefileOpenOptions options,
            ShapefileAccounting accounting,
            CancellationToken cancellation,
            List<SourceDiagnostic> warnings) {
        ShapefileFileAccess.Channel channel;
        try {
            checkpoint(source, cancellation);
            channel = access.open(dbf);
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "dbf", "open", -1, exception);
        }
        Throwable primary = null;
        try {
            checkpoint(source, cancellation);
            long size = size(source, channel, cancellation);
            if (size > options.shapefileLimits().maximumComponentBytes()) {
                throw ShapefileFailures.limit(
                        source,
                        "shapefileOpen",
                        "componentBytes",
                        size,
                        options.shapefileLimits().maximumComponentBytes(),
                        "dbf",
                        OptionalLong.empty(),
                        0);
            }
            checkpoint(source, cancellation);
            accounting.allocate("dbf", 65, OptionalLong.empty(), 0);
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES);
            ByteBuffer descriptor = ByteBuffer.allocate(DESCRIPTOR_BYTES);
            ByteBuffer suffix = ByteBuffer.allocate(1);
            readExact(source, channel, header, 0, "fileLayout", cancellation);
            header.order(ByteOrder.LITTLE_ENDIAN);
            int version = header.get(0) & 0xff;
            validateVersion(source, header, version);
            long rows = Integer.toUnsignedLong(header.getInt(4));
            accounting.dbfRows(rows, 4);
            int headerLength = Short.toUnsignedInt(header.getShort(8));
            int recordLength = Short.toUnsignedInt(header.getShort(10));
            if (headerLength < 33 || (headerLength - 33) % DESCRIPTOR_BYTES != 0) {
                throw headerFailure(source, 8, "headerLength", "mismatch");
            }
            int fieldCount = (headerLength - 33) / DESCRIPTOR_BYTES;
            accounting.dbfFields(fieldCount, 8);
            if (recordLength < 1) {
                throw headerFailure(source, 10, "recordLength", "mismatch");
            }
            long rowBytes;
            long expected;
            try {
                rowBytes = Math.multiplyExact(rows, recordLength);
                expected = Math.addExact(headerLength, rowBytes);
            } catch (ArithmeticException exception) {
                throw headerFailure(source, 4, "rowCount", "mismatch");
            }
            validateLayout(source, channel, size, expected, suffix, cancellation);
            if (index != null && rows != index.size()) {
                throw countMismatch(source, rows, index.size());
            }
            checkpoint(source, cancellation);
            accounting.allocate(
                    "dbf", Math.multiplyExact((long) fieldCount, 33), OptionalLong.empty(), 32);
            checkpoint(source, cancellation);
            String[] names = new String[fieldCount];
            byte[] types = new byte[fieldCount];
            int[] plan = new int[Math.multiplyExact(fieldCount, 4)];
            List<AttributeField> schemaFields = new ArrayList<>(fieldCount);
            int rowOffset = 1;
            for (int field = 0; field < fieldCount; field++) {
                checkpoint(source, cancellation);
                long descriptorOffset = 32L + (long) field * DESCRIPTOR_BYTES;
                readExact(
                        source, channel, descriptor, descriptorOffset, "fileLayout", cancellation);
                String name =
                        readName(source, descriptor, field, descriptorOffset, names, accounting);
                int type = descriptor.get(11) & 0xff;
                int width = descriptor.get(16) & 0xff;
                int decimals = descriptor.get(17) & 0xff;
                if (width == 0) {
                    throw fieldFailure(
                            source, field, Optional.of(name), descriptorOffset + 16, "width");
                }
                accounting.dbfFieldWidth(width, field, descriptorOffset + 16);
                if ((version == 0x04 || version == 0x05) && (descriptor.get(31) & 0xff) > 1) {
                    throw headerFailure(source, descriptorOffset + 31, "mdxFlag", "unsupported");
                }
                int schemaOrdinal = -1;
                AttributeType attributeType = supportedType(type, width, decimals);
                if (attributeType == null && supportedCode(type)) {
                    String reason = invalidSupportedReason(type, width);
                    throw fieldFailure(
                            source,
                            field,
                            Optional.of(name),
                            descriptorOffset + (reason.equals("width") ? 16 : 17),
                            reason);
                }
                if (attributeType == null) {
                    warnings.add(
                            DbfDiagnostics.warning(
                                    source,
                                    "SHAPEFILE_DBF_FIELD_UNSUPPORTED",
                                    "dbf",
                                    OptionalLong.empty(),
                                    OptionalInt.of(field),
                                    Optional.of(name),
                                    descriptorOffset + 11,
                                    Map.of()));
                } else {
                    schemaOrdinal = schemaFields.size();
                    schemaFields.add(new AttributeField(name, attributeType, true));
                }
                names[field] = name;
                types[field] = (byte) type;
                int planOffset = field * 4;
                plan[planOffset] = rowOffset;
                plan[planOffset + 1] = width;
                plan[planOffset + 2] = decimals;
                plan[planOffset + 3] = schemaOrdinal;
                rowOffset = Math.addExact(rowOffset, width);
            }
            if (rowOffset != recordLength) {
                throw fieldFailure(
                        source,
                        Math.max(0, fieldCount - 1),
                        fieldCount == 0 ? Optional.empty() : Optional.of(names[fieldCount - 1]),
                        10,
                        "rowLayout");
            }
            readExact(
                    source,
                    channel,
                    suffix,
                    32L + (long) fieldCount * DESCRIPTOR_BYTES,
                    "terminator",
                    cancellation);
            if ((suffix.get(0) & 0xff) != 0x0d) {
                throw headerFailure(
                        source,
                        32L + (long) fieldCount * DESCRIPTOR_BYTES,
                        "terminator",
                        "mismatch");
            }
            CpgReader.Result encoding =
                    CpgReader.resolve(
                            source,
                            cpg,
                            access,
                            header.get(29) & 0xff,
                            options,
                            accounting,
                            cancellation,
                            warnings);
            checkpoint(source, cancellation);
            accounting.allocate(
                    "dbf",
                    Math.multiplyExact((long) schemaFields.size(), 40),
                    OptionalLong.empty(),
                    32);
            checkpoint(source, cancellation);
            AttributeSchema schema = new AttributeSchema(schemaFields);
            return new Result(
                    new DbfTable(
                            source,
                            channel,
                            size,
                            rows,
                            headerLength,
                            recordLength,
                            encoding.encoding(),
                            schema,
                            names,
                            types,
                            plan));
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            if (primary != null) {
                try {
                    channel.close();
                } catch (IOException exception) {
                    primary.addSuppressed(exception);
                }
            }
        }
    }

    private static long size(
            String source, ShapefileFileAccess.Channel channel, CancellationToken cancellation) {
        try {
            long size = channel.size();
            checkpoint(source, cancellation);
            if (size < 0) {
                throw ShapefileFailures.io(
                        source, "dbf", "size", -1, new IOException("negative captured size"));
            }
            return size;
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "dbf", "size", -1, exception);
        }
    }

    private static void validateVersion(String source, ByteBuffer header, int version) {
        if (version != 0x03 && version != 0x04 && version != 0x05) {
            throw headerFailure(source, 0, "version", "unsupported");
        }
        if (version == 0x04 || version == 0x05) {
            if (header.get(14) != 0) {
                throw headerFailure(source, 14, "transaction", "nonZero");
            }
            if (header.get(15) != 0) {
                throw headerFailure(source, 15, "encryption", "nonZero");
            }
            if ((header.get(28) & 0xff) > 1) {
                throw headerFailure(source, 28, "mdxFlag", "unsupported");
            }
        }
    }

    private static void validateLayout(
            String source,
            ShapefileFileAccess.Channel channel,
            long size,
            long expected,
            ByteBuffer suffix,
            CancellationToken cancellation) {
        if (size == expected) {
            return;
        }
        if (size > expected) {
            readExact(source, channel, suffix, expected, "fileLayout", cancellation);
            if ((suffix.get(0) & 0xff) == 0x1a && size == expected + 1) {
                return;
            }
            long offset = (suffix.get(0) & 0xff) == 0x1a ? expected + 1 : expected;
            throw headerFailure(source, offset, "fileLayout", "trailingData");
        }
        throw headerFailure(source, size, "fileLayout", "mismatch");
    }

    private static String readName(
            String source,
            ByteBuffer descriptor,
            int field,
            long descriptorOffset,
            String[] previous,
            ShapefileAccounting accounting) {
        int end = -1;
        for (int index = 0; index < 11; index++) {
            if (descriptor.get(index) == 0) {
                end = index;
                break;
            }
        }
        if (end < 0) {
            throw fieldFailure(
                    source, field, Optional.empty(), descriptorOffset, "nameUnterminated");
        }
        if (end == 0) {
            throw fieldFailure(source, field, Optional.empty(), descriptorOffset, "nameEmpty");
        }
        for (int index = 0; index < end; index++) {
            int value = descriptor.get(index) & 0xff;
            if (value < 0x20 || value > 0x7e) {
                throw fieldFailure(
                        source, field, Optional.empty(), descriptorOffset + index, "nameNonAscii");
            }
        }
        if (descriptor.get(0) == 0x20 || descriptor.get(end - 1) == 0x20) {
            throw fieldFailure(source, field, Optional.empty(), descriptorOffset, "nameWhitespace");
        }
        accounting.allocate(
                "dbf", Math.multiplyExact((long) end, 4), OptionalLong.empty(), descriptorOffset);
        char[] characters = new char[end];
        for (int index = 0; index < end; index++) {
            characters[index] = (char) (descriptor.get(index) & 0xff);
        }
        String name = new String(characters);
        for (int prior = 0; prior < field; prior++) {
            if (asciiEqualsIgnoreCase(name, previous[prior])) {
                throw fieldFailure(
                        source, field, Optional.of(name), descriptorOffset, "nameDuplicate");
            }
        }
        return name;
    }

    private static AttributeType supportedType(int type, int width, int decimals) {
        return switch (type) {
            case 'C' -> width <= 254 && decimals == 0 ? AttributeType.TEXT : null;
            case 'N' ->
                    width <= 20 && (decimals == 0 || decimals <= width - 2)
                            ? decimals == 0 ? AttributeType.INTEGER : AttributeType.DECIMAL
                            : null;
            case 'F' ->
                    width <= 20 && (decimals == 0 || decimals <= width - 2)
                            ? AttributeType.FLOATING
                            : null;
            case 'L' -> width == 1 && decimals == 0 ? AttributeType.LOGICAL : null;
            case 'D' -> width == 8 && decimals == 0 ? AttributeType.DATE : null;
            default -> null;
        };
    }

    private static boolean supportedCode(int type) {
        return type == 'C' || type == 'N' || type == 'F' || type == 'L' || type == 'D';
    }

    private static String invalidSupportedReason(int type, int width) {
        boolean widthValid =
                switch (type) {
                    case 'C' -> width <= 254;
                    case 'N', 'F' -> width <= 20;
                    case 'L' -> width == 1;
                    case 'D' -> width == 8;
                    default -> true;
                };
        return widthValid ? "decimals" : "width";
    }

    private static SourceException headerFailure(
            String source, long offset, String field, String reason) {
        return DbfDiagnostics.failure(
                source,
                "SHAPEFILE_DBF_HEADER_INVALID",
                "dbf",
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                offset,
                Map.of("field", field, "reason", reason));
    }

    private static SourceException fieldFailure(
            String source, int field, Optional<String> name, long offset, String reason) {
        return DbfDiagnostics.failure(
                source,
                "SHAPEFILE_DBF_FIELD_INVALID",
                "dbf",
                OptionalLong.empty(),
                OptionalInt.of(field),
                name,
                offset,
                Map.of("reason", reason));
    }

    private static SourceException countMismatch(String source, long dbfRows, long shpRecords) {
        return DbfDiagnostics.failure(
                source,
                "SHAPEFILE_DBF_RECORD_COUNT_MISMATCH",
                "dbf",
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                4,
                Map.of("dbfRows", Long.toString(dbfRows), "shpRecords", Long.toString(shpRecords)));
    }

    private static void readExact(
            String source,
            ShapefileFileAccess.Channel channel,
            ByteBuffer target,
            long offset,
            String field,
            CancellationToken cancellation) {
        target.clear();
        int total = 0;
        int zeroReads = 0;
        try {
            while (target.hasRemaining()) {
                checkpoint(source, cancellation);
                int count = channel.read(target, offset + total);
                checkpoint(source, cancellation);
                zeroReads = Shapefiles.trackReadProgress(count, zeroReads);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    total += count;
                }
            }
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "dbf", "read", offset + total, exception);
        }
        if (target.hasRemaining()) {
            throw headerFailure(source, offset + total, field, "truncated");
        }
        target.flip();
    }

    private static boolean asciiEqualsIgnoreCase(String first, String second) {
        if (first.length() != second.length()) {
            return false;
        }
        for (int index = 0; index < first.length(); index++) {
            char a = first.charAt(index);
            char b = second.charAt(index);
            if (a >= 'a' && a <= 'z') {
                a = (char) (a - 32);
            }
            if (b >= 'a' && b <= 'z') {
                b = (char) (b - 32);
            }
            if (a != b) {
                return false;
            }
        }
        return true;
    }

    private static void checkpoint(String source, CancellationToken cancellation) {
        Shapefiles.checkpoint(source, cancellation);
    }
}
