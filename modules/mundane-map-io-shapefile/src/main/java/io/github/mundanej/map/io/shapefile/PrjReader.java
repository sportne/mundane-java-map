package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.core.CrsRegistry;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Final opening phase for bounded PRJ retention and two-profile recognition. */
final class PrjReader {
    static final int MAXIMUM_PROFILE_BYTES = 65_536;
    private static final int CHARACTER_LIMIT = CrsMetadata.RETAINED_DEFINITION_LIMIT;
    private static final int TOKEN_STORAGE_BYTES = 512 + 1024 * Integer.BYTES + 16;

    record Result(Optional<CrsMetadata> metadata, List<SourceDiagnostic> warnings) {}

    private PrjReader() {}

    static Result missing(Optional<CrsDefinition> override) {
        return new Result(
                override.map(
                        definition ->
                                CrsMetadata.recognized(
                                        definition, Optional.empty(), Optional.empty())),
                List.of());
    }

    static Result read(
            String source,
            Path path,
            ShapefileFileAccess access,
            Optional<CrsDefinition> override,
            ShapefileLimits limits,
            ShapefileAccounting accounting,
            CancellationToken cancellation) {
        byte[] bytes = readBytes(source, path, access, limits, accounting, cancellation);
        int base = bom(bytes) ? 3 : 0;
        char[] characters = decode(source, bytes, base, accounting, cancellation);
        int length = decodedLength(source, bytes, base, characters, cancellation);
        accounting.decodedCharacters("prj", length, base);
        if (blank(source, bytes, base, cancellation)) {
            checkpoint(source, cancellation);
            return new Result(
                    missing(override).metadata(),
                    List.of(warning(source, "SHAPEFILE_PRJ_BLANK", base, Map.of())));
        }
        checkpoint(source, cancellation);
        accounting.allocate(
                "prj", Math.multiplyExact((long) length, 2), OptionalLong.empty(), base);
        checkpoint(source, cancellation);
        String retained = new String(characters, 0, length);
        accounting.allocate("prj", TOKEN_STORAGE_BYTES, OptionalLong.empty(), base);
        checkpoint(source, cancellation);
        PrjTokenizer tokenizer = new PrjTokenizer(source, bytes, base, cancellation);
        tokenizer.scan();
        checkpoint(source, cancellation);
        String recognizedKey = PrjRecognizer.recognize(tokenizer);
        if (recognizedKey != null) {
            CrsDefinition recognized = CrsRegistry.level1().resolve(recognizedKey);
            if (override.isPresent() && !override.orElseThrow().equals(recognized)) {
                throw failure(
                        source,
                        "SHAPEFILE_CRS_CONFLICT",
                        tokenizer.start(0),
                        Map.of(
                                "declared",
                                recognized.canonicalIdentifier(),
                                "override",
                                override.orElseThrow().canonicalIdentifier()));
            }
            checkpoint(source, cancellation);
            return new Result(
                    Optional.of(
                            CrsMetadata.recognized(
                                    recognized, Optional.empty(), Optional.of(retained))),
                    List.of());
        }
        if (override.isPresent()) {
            CrsDefinition selected = override.orElseThrow();
            checkpoint(source, cancellation);
            return new Result(
                    Optional.of(
                            CrsMetadata.recognized(
                                    selected, Optional.empty(), Optional.of(retained))),
                    List.of(
                            warning(
                                    source,
                                    "SHAPEFILE_PRJ_OVERRIDE_USED",
                                    base,
                                    Map.of("selected", selected.canonicalIdentifier()))));
        }
        checkpoint(source, cancellation);
        return new Result(
                Optional.of(CrsMetadata.unknown(Optional.empty(), Optional.of(retained))),
                List.of(warning(source, "SHAPEFILE_PRJ_CRS_UNRECOGNIZED", base, Map.of())));
    }

    private static byte[] readBytes(
            String source,
            Path path,
            ShapefileFileAccess access,
            ShapefileLimits limits,
            ShapefileAccounting accounting,
            CancellationToken cancellation) {
        checkpoint(source, cancellation);
        ShapefileFileAccess.Channel channel;
        try {
            channel = access.open(path);
        } catch (IOException exception) {
            throw ShapefileFailures.io(source, "prj", "open", -1, exception);
        }
        Throwable primary = null;
        try {
            checkpoint(source, cancellation);
            long captured;
            try {
                captured = channel.size();
            } catch (IOException exception) {
                throw ShapefileFailures.io(source, "prj", "size", -1, exception);
            }
            checkpoint(source, cancellation);
            if (captured < 0) {
                throw ShapefileFailures.io(
                        source, "prj", "size", -1, new IOException("negative captured size"));
            }
            long maximum = Math.min(limits.maximumPrjBytes(), (long) MAXIMUM_PROFILE_BYTES);
            if (captured > maximum) {
                throw ShapefileFailures.limit(
                        source,
                        "shapefileOpen",
                        "prjBytes",
                        captured,
                        maximum,
                        "prj",
                        OptionalLong.empty(),
                        0);
            }
            int length = Math.toIntExact(captured);
            accounting.allocate("prj", length, OptionalLong.empty(), 0);
            checkpoint(source, cancellation);
            byte[] bytes = new byte[length];
            ByteBuffer target = ByteBuffer.wrap(bytes);
            int total = 0;
            int zeroReads = 0;
            try {
                while (total < length) {
                    target.limit(Math.min(length, total + 4096));
                    checkpoint(source, cancellation);
                    int count = channel.read(target, total);
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
                throw ShapefileFailures.io(source, "prj", "read", total, exception);
            }
            if (total < length) {
                throw failure(
                        source, "SHAPEFILE_PRJ_INVALID", total, Map.of("reason", "truncated"));
            }
            long actual;
            try {
                actual = channel.size();
            } catch (IOException exception) {
                throw ShapefileFailures.io(source, "prj", "size", -1, exception);
            }
            checkpoint(source, cancellation);
            if (actual != captured) {
                throw failure(
                        source,
                        "SHAPEFILE_PRJ_INVALID",
                        0,
                        Map.of(
                                "reason",
                                "sizeChanged",
                                "capturedBytes",
                                Long.toString(captured),
                                "actualBytes",
                                Long.toString(actual)));
            }
            checkpoint(source, cancellation);
            return bytes;
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } finally {
            close(source, channel, primary);
            if (primary == null) {
                checkpoint(source, cancellation);
            }
        }
    }

    private static void close(
            String source, ShapefileFileAccess.Channel channel, Throwable primary) {
        try {
            channel.close();
        } catch (IOException exception) {
            if (primary == null) {
                throw ShapefileFailures.io(source, "prj", "close", -1, exception);
            }
            primary.addSuppressed(exception);
        }
    }

    private static char[] decode(
            String source,
            byte[] bytes,
            int base,
            ShapefileAccounting accounting,
            CancellationToken cancellation) {
        checkpoint(source, cancellation);
        int capacity = Math.min(bytes.length - base, CHARACTER_LIMIT + 1);
        accounting.allocate("prj", (long) capacity * Character.BYTES, OptionalLong.empty(), base);
        checkpoint(source, cancellation);
        char[] characters = new char[capacity];
        return characters;
    }

    private static int decodedLength(
            String source,
            byte[] bytes,
            int base,
            char[] characters,
            CancellationToken cancellation) {
        var decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer input = ByteBuffer.wrap(bytes, base, bytes.length - base);
        CharBuffer output = CharBuffer.wrap(characters);
        int inputLimit = input.limit();
        if (!input.hasRemaining()) {
            decoder.decode(input, output, true);
        }
        while (input.position() < inputLimit) {
            int chunkLimit = Math.min(inputLimit, input.position() + 4096);
            input.limit(chunkLimit);
            var decoded = decoder.decode(input, output, chunkLimit == inputLimit);
            if (decoded.isError()) {
                throw failure(
                        source,
                        "SHAPEFILE_PRJ_INVALID",
                        input.position(),
                        Map.of("reason", "encoding"));
            }
            if (decoded.isOverflow()) {
                throw tooLong(source);
            }
            checkpoint(source, cancellation);
            input.limit(inputLimit);
        }
        var flushed = decoder.flush(output);
        if (flushed.isOverflow() || output.position() > CHARACTER_LIMIT) {
            throw tooLong(source);
        }
        checkpoint(source, cancellation);
        return output.position();
    }

    private static SourceException tooLong(String source) {
        return failure(
                source,
                "CRS_RETAINED_DEFINITION_TOO_LONG",
                -1,
                Map.of("maximum", "16384", "requested", "16385"));
    }

    private static boolean blank(
            String source, byte[] bytes, int base, CancellationToken cancellation) {
        for (int index = base; index < bytes.length; index++) {
            if (((index - base) & 4095) == 0) {
                checkpoint(source, cancellation);
            }
            int value = bytes[index] & 0xff;
            if (value != 0x20 && (value < 0x09 || value > 0x0d)) {
                return false;
            }
        }
        return true;
    }

    private static boolean bom(byte[] bytes) {
        return bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf;
    }

    private static SourceDiagnostic warning(
            String source, String code, long offset, Map<String, String> context) {
        return new SourceDiagnostic(
                code,
                DiagnosticSeverity.WARNING,
                source,
                Optional.of(
                        new DiagnosticLocation(
                                Optional.of("prj"),
                                OptionalLong.empty(),
                                OptionalInt.empty(),
                                OptionalInt.empty(),
                                Optional.empty(),
                                offset < 0 ? OptionalLong.empty() : OptionalLong.of(offset))),
                "Shapefile coordinate-reference diagnostic",
                context);
    }

    static SourceException failure(
            String source, String code, long offset, Map<String, String> context) {
        return ShapefileFailures.failure(
                source,
                code,
                "prj",
                OptionalLong.empty(),
                offset,
                "Shapefile coordinate-reference metadata is invalid",
                context);
    }

    private static void checkpoint(String source, CancellationToken cancellation) {
        Shapefiles.checkpoint(source, cancellation);
    }
}
