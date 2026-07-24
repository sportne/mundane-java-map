package io.github.mundanej.map.io.maplibre.style;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.exc.StreamConstraintsException;

/** Entry points for bounded detached MapLibre Style v8 reading. */
public final class MapLibreStyles {
    private MapLibreStyles() {}

    /**
     * Reads a defensively copied UTF-8 style snapshot using default limits.
     *
     * @param bytes caller-owned encoded bytes
     * @return detached immutable style
     */
    public static MapLibreStyle read(byte[] bytes) {
        return read(bytes, MapLibreReadOptions.defaults());
    }

    /**
     * Reads a defensively copied UTF-8 style snapshot.
     *
     * @param bytes caller-owned encoded bytes
     * @param options bounded read policy
     * @return detached immutable style
     */
    public static MapLibreStyle read(byte[] bytes, MapLibreReadOptions options) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(options, "options");
        checkCancelled(options);
        MapLibreReadLimits limits = options.limits();
        if (bytes.length > limits.maximumInputBytes()) {
            throw failure(
                    "MAPLIBRE_LIMIT_EXCEEDED",
                    "/",
                    Map.of(
                            "limit", "inputBytes",
                            "actual", Integer.toString(bytes.length),
                            "maximum", Integer.toString(limits.maximumInputBytes())));
        }
        rejectBom(bytes);
        validateUtf8(bytes);
        byte[] snapshot = Arrays.copyOf(bytes, bytes.length);
        int offset = hasUtf8Bom(snapshot) ? 3 : 0;
        try (JsonParser parser =
                MapLibreJacksonFactory.create(limits)
                        .createParser(
                                ObjectReadContext.empty(),
                                snapshot,
                                offset,
                                snapshot.length - offset)) {
            return new MapLibreParser(parser, options, snapshot.length).parse();
        } catch (MapLibreReadException failure) {
            throw failure;
        } catch (StreamConstraintsException failure) {
            throw failure(
                    "MAPLIBRE_LIMIT_EXCEEDED", "/", Map.of("limit", "jacksonConstraint"), failure);
        } catch (JacksonException failure) {
            throw failure("MAPLIBRE_JSON_INVALID", "/", Map.of("reason", "syntax"), failure);
        }
    }

    static MapLibreReadException failure(
            String code, String location, Map<String, String> context) {
        return new MapLibreReadException(new MapLibreProblem(code, "read", location, context));
    }

    static MapLibreReadException failure(
            String code, String location, Map<String, String> context, Throwable cause) {
        return new MapLibreReadException(
                new MapLibreProblem(code, "read", location, context), cause);
    }

    private static void checkCancelled(MapLibreReadOptions options) {
        if (options.cancellation().isCancellationRequested()) {
            throw failure("MAPLIBRE_CANCELLED", "/", Map.of());
        }
    }

    private static void rejectBom(byte[] bytes) {
        if (bytes.length >= 2
                && ((bytes[0] == (byte) 0xff && bytes[1] == (byte) 0xfe)
                        || (bytes[0] == (byte) 0xfe && bytes[1] == (byte) 0xff))) {
            throw failure("MAPLIBRE_JSON_INVALID", "/", Map.of("reason", "unsupportedEncoding"));
        }
        if (bytes.length >= 4
                && ((bytes[0] == 0
                                && bytes[1] == 0
                                && bytes[2] == (byte) 0xfe
                                && bytes[3] == (byte) 0xff)
                        || (bytes[0] == (byte) 0xff
                                && bytes[1] == (byte) 0xfe
                                && bytes[2] == 0
                                && bytes[3] == 0))) {
            throw failure("MAPLIBRE_JSON_INVALID", "/", Map.of("reason", "unsupportedEncoding"));
        }
    }

    private static void validateUtf8(byte[] bytes) {
        int offset = hasUtf8Bom(bytes) ? 3 : 0;
        try {
            StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset));
        } catch (CharacterCodingException failure) {
            throw failure("MAPLIBRE_JSON_INVALID", "/", Map.of("reason", "invalidUtf8"), failure);
        }
    }

    private static boolean hasUtf8Bom(byte[] bytes) {
        return bytes.length >= 3
                && bytes[0] == (byte) 0xef
                && bytes[1] == (byte) 0xbb
                && bytes[2] == (byte) 0xbf;
    }
}
