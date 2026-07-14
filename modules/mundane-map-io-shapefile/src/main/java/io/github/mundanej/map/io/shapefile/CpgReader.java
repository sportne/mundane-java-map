package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceDiagnostic;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/* Bounded CPG token reader and finite DBF encoding resolver. */
final class CpgReader {
    record Result(DbfEncoding encoding) {}

    private CpgReader() {}

    static Result resolve(
            String source,
            Path cpg,
            ShapefileFileAccess access,
            int ldid,
            ShapefileOpenOptions options,
            ShapefileAccounting accounting,
            CancellationToken cancellation,
            List<SourceDiagnostic> warnings) {
        Optional<DbfEncoding> cpgEncoding = Optional.empty();
        if (cpg != null) {
            cpgEncoding = read(source, cpg, access, options, accounting, cancellation, warnings);
        }
        Optional<DbfEncoding> ldidEncoding = fromLdid(ldid);
        Optional<DbfEncoding> override = options.dbfEncodingOverride();
        DbfEncoding selected;
        if (override.isPresent()) {
            selected = override.orElseThrow();
        } else if (cpgEncoding.isPresent()) {
            selected = cpgEncoding.orElseThrow();
        } else if (ldidEncoding.isPresent()) {
            selected = ldidEncoding.orElseThrow();
        } else {
            selected = DbfEncoding.WINDOWS_1252;
        }
        if (override.isPresent() && cpgEncoding.isPresent() && cpgEncoding.get() != selected) {
            warnings.add(conflict(source, "cpg", -1, selected, cpgEncoding.get()));
        }
        if (ldidEncoding.isPresent() && ldidEncoding.get() != selected) {
            warnings.add(conflict(source, "dbf", 29, selected, ldidEncoding.get()));
        }
        if (override.isEmpty() && cpgEncoding.isEmpty() && ldidEncoding.isEmpty()) {
            warnings.add(
                    DbfDiagnostics.warning(
                            source,
                            "SHAPEFILE_ENCODING_FALLBACK",
                            "dbf",
                            OptionalLong.empty(),
                            OptionalInt.empty(),
                            Optional.empty(),
                            29,
                            Map.of("selected", selected.name())));
        }
        return new Result(selected);
    }

    private static Optional<DbfEncoding> read(
            String source,
            Path path,
            ShapefileFileAccess access,
            ShapefileOpenOptions options,
            ShapefileAccounting accounting,
            CancellationToken cancellation,
            List<SourceDiagnostic> warnings) {
        ShapefileFileAccess.Channel channel;
        try {
            channel = access.open(path);
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "cpg", "open", -1, exception);
        }
        Throwable primary = null;
        try {
            checkpoint(source, cancellation);
            long size;
            try {
                size = channel.size();
            } catch (IOException exception) {
                throw ShapefileFailures.io(source, "cpg", "size", -1, exception);
            }
            checkpoint(source, cancellation);
            if (size > options.shapefileLimits().maximumCpgBytes()) {
                throw ShapefileFailures.limit(
                        source,
                        "shapefileOpen",
                        "cpgBytes",
                        size,
                        options.shapefileLimits().maximumCpgBytes(),
                        OptionalLong.empty(),
                        0);
            }
            int length = Math.toIntExact(size);
            checkpoint(source, cancellation);
            accounting.allocate(length, OptionalLong.empty(), 0);
            checkpoint(source, cancellation);
            byte[] bytes = new byte[length];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            readExact(source, channel, buffer, cancellation);
            return parse(source, bytes, warnings);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            close(source, channel, primary);
        }
    }

    private static void close(
            String source, ShapefileFileAccess.Channel channel, Throwable primary) {
        try {
            channel.close();
        } catch (IOException exception) {
            if (primary != null) {
                primary.addSuppressed(exception);
            } else {
                throw ShapefileFailures.io(source, "cpg", "close", -1, exception);
            }
        }
    }

    private static Optional<DbfEncoding> parse(
            String source, byte[] bytes, List<SourceDiagnostic> warnings) {
        int start = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            start = 3;
        }
        for (int index = start; index < bytes.length; index++) {
            if ((bytes[index] & 0x80) != 0) {
                warnings.add(invalid(source, index, "nonAscii"));
                return Optional.empty();
            }
        }
        int end = bytes.length;
        while (start < end && whitespace(bytes[start] & 0xff)) {
            start++;
        }
        while (end > start && whitespace(bytes[end - 1] & 0xff)) {
            end--;
        }
        if (start == end) {
            warnings.add(invalid(source, 0, "empty"));
            return Optional.empty();
        }
        for (int index = start; index < end; index++) {
            if (whitespace(bytes[index] & 0xff)) {
                warnings.add(invalid(source, index, "multipleTokens"));
                return Optional.empty();
            }
        }
        Optional<DbfEncoding> value = alias(bytes, start, end);
        if (value.isEmpty()) {
            warnings.add(invalid(source, 0, "unknown"));
        }
        return value;
    }

    private static Optional<DbfEncoding> alias(byte[] bytes, int start, int end) {
        if (matches(bytes, start, end, "UTF-8")
                || matches(bytes, start, end, "UTF8")
                || matches(bytes, start, end, "65001")) {
            return Optional.of(DbfEncoding.UTF_8);
        }
        if (matches(bytes, start, end, "ISO-8859-1")
                || matches(bytes, start, end, "ISO8859-1")
                || matches(bytes, start, end, "88591")) {
            return Optional.of(DbfEncoding.ISO_8859_1);
        }
        if (matches(bytes, start, end, "WINDOWS-1252")
                || matches(bytes, start, end, "CP1252")
                || matches(bytes, start, end, "1252")) {
            return Optional.of(DbfEncoding.WINDOWS_1252);
        }
        if (matches(bytes, start, end, "IBM437")
                || matches(bytes, start, end, "CP437")
                || matches(bytes, start, end, "437")) {
            return Optional.of(DbfEncoding.IBM437);
        }
        if (matches(bytes, start, end, "IBM850")
                || matches(bytes, start, end, "CP850")
                || matches(bytes, start, end, "850")) {
            return Optional.of(DbfEncoding.IBM850);
        }
        return Optional.empty();
    }

    private static Optional<DbfEncoding> fromLdid(int ldid) {
        return switch (ldid) {
            case 0x01 -> Optional.of(DbfEncoding.IBM437);
            case 0x02 -> Optional.of(DbfEncoding.IBM850);
            case 0x03, 0x57 -> Optional.of(DbfEncoding.WINDOWS_1252);
            default -> Optional.empty();
        };
    }

    private static SourceDiagnostic conflict(
            String source,
            String component,
            long offset,
            DbfEncoding selected,
            DbfEncoding ignored) {
        return DbfDiagnostics.warning(
                source,
                "SHAPEFILE_ENCODING_CONFLICT",
                component,
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                offset,
                Map.of("selected", selected.name(), "ignored", ignored.name()));
    }

    private static SourceDiagnostic invalid(String source, int offset, String reason) {
        return DbfDiagnostics.warning(
                source,
                "SHAPEFILE_CPG_INVALID",
                "cpg",
                OptionalLong.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                offset,
                Map.of("reason", reason));
    }

    private static boolean matches(byte[] bytes, int start, int end, String expected) {
        if (end - start != expected.length()) {
            return false;
        }
        for (int index = 0; index < expected.length(); index++) {
            int actual = bytes[start + index] & 0xff;
            int target = expected.charAt(index);
            if (actual >= 'a' && actual <= 'z') {
                actual -= 32;
            }
            if (target >= 'a' && target <= 'z') {
                target -= 32;
            }
            if (actual != target) {
                return false;
            }
        }
        return true;
    }

    private static boolean whitespace(int value) {
        return value == 0x20 || (value >= 0x09 && value <= 0x0d);
    }

    private static void readExact(
            String source,
            ShapefileFileAccess.Channel channel,
            ByteBuffer buffer,
            CancellationToken cancellation) {
        int total = 0;
        try {
            while (buffer.hasRemaining()) {
                checkpoint(source, cancellation);
                int count = channel.read(buffer, total);
                checkpoint(source, cancellation);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    total += count;
                }
            }
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "cpg", "read", total, exception);
        }
        if (buffer.hasRemaining()) {
            throw ShapefileFailures.io(
                    source, "cpg", "read", total, new IOException("captured short read"));
        }
    }

    private static void checkpoint(String source, CancellationToken cancellation) {
        Shapefiles.checkpoint(source, cancellation);
    }
}
