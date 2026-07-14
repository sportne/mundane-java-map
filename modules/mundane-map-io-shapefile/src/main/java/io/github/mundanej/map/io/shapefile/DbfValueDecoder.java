package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SourceDiagnostic;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.function.Consumer;

/** Bounded locale-independent conversion of selected DBF scalar slices. */
final class DbfValueDecoder {
    private static final char INVALID = '\ue000';
    private static final String ASCII =
            String.join(
                    "",
                    "\000\001\002\003\004\005\006\007\010\011\012\013\014\015\016\017",
                    "\020\021\022\023\024\025\026\027\030\031\032\033\034\035\036\037",
                    " !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`",
                    "abcdefghijklmnopqrstuvwxyz{|}~\177");
    private static final String WINDOWS_1252 =
            String.join(
                    "",
                    ASCII,
                    "\u20ac\ue000\u201a\u0192\u201e\u2026\u2020\u2021\u02c6\u2030\u0160\u2039\u0152\ue000\u017d\ue000",
                    "\ue000\u2018\u2019\u201c\u201d\u2022\u2013\u2014\u02dc\u2122\u0161\u203a\u0153\ue000\u017e\u0178",
                    "\u00a0\u00a1\u00a2\u00a3\u00a4\u00a5\u00a6\u00a7\u00a8\u00a9\u00aa\u00ab\u00ac\u00ad\u00ae\u00af",
                    "\u00b0\u00b1\u00b2\u00b3\u00b4\u00b5\u00b6\u00b7\u00b8\u00b9\u00ba\u00bb\u00bc\u00bd\u00be\u00bf",
                    "\u00c0\u00c1\u00c2\u00c3\u00c4\u00c5\u00c6\u00c7\u00c8\u00c9\u00ca\u00cb\u00cc\u00cd\u00ce\u00cf",
                    "\u00d0\u00d1\u00d2\u00d3\u00d4\u00d5\u00d6\u00d7\u00d8\u00d9\u00da\u00db\u00dc\u00dd\u00de\u00df",
                    "\u00e0\u00e1\u00e2\u00e3\u00e4\u00e5\u00e6\u00e7\u00e8\u00e9\u00ea\u00eb\u00ec\u00ed\u00ee\u00ef",
                    "\u00f0\u00f1\u00f2\u00f3\u00f4\u00f5\u00f6\u00f7\u00f8\u00f9\u00fa\u00fb\u00fc\u00fd\u00fe\u00ff");
    private static final String IBM437 =
            String.join(
                    "",
                    ASCII,
                    "\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5",
                    "\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00a2\u00a3\u00a5\u20a7\u0192",
                    "\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u2310\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb",
                    "\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510",
                    "\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567",
                    "\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580",
                    "\u03b1\u00df\u0393\u03c0\u03a3\u03c3\u00b5\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u03c6\u03b5\u2229",
                    "\u2261\u00b1\u2265\u2264\u2320\u2321\u00f7\u2248\u00b0\u2219\u00b7\u221a\u207f\u00b2\u25a0\u00a0");
    private static final String IBM850 =
            String.join(
                    "",
                    ASCII,
                    "\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5",
                    "\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8\u00a3\u00d8\u00d7\u0192",
                    "\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1\u00aa\u00ba\u00bf\u00ae\u00ac\u00bd\u00bc\u00a1\u00ab\u00bb",
                    "\u2591\u2592\u2593\u2502\u2524\u00c1\u00c2\u00c0\u00a9\u2563\u2551\u2557\u255d\u00a2\u00a5\u2510",
                    "\u2514\u2534\u252c\u251c\u2500\u253c\u00e3\u00c3\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u00a4",
                    "\u00f0\u00d0\u00ca\u00cb\u00c8\u0131\u00cd\u00ce\u00cf\u2518\u250c\u2588\u2584\u00a6\u00cc\u2580",
                    "\u00d3\u00df\u00d4\u00d2\u00f5\u00d5\u00b5\u00fe\u00de\u00da\u00db\u00d9\u00fd\u00dd\u00af\u00b4",
                    "\u00ad\u00b1\u2017\u00be\u00b6\u00a7\u00f7\u00b8\u00b0\u00a8\u00b7\u00b9\u00b3\u00b2\u25a0\u00a0");

    private final String source;
    private final DbfTable table;
    private final ShapefileAccounting accounting;
    private final CancellationToken cancellation;
    private final Consumer<SourceDiagnostic> warnings;
    private final byte[] bytes;
    private final char[] characters;

    DbfValueDecoder(
            String source,
            DbfTable table,
            ShapefileAccounting accounting,
            CancellationToken cancellation,
            Consumer<SourceDiagnostic> warnings,
            byte[] bytes,
            char[] characters) {
        this.source = source;
        this.table = table;
        this.accounting = accounting;
        this.cancellation = cancellation;
        this.warnings = warnings;
        this.bytes = bytes;
        this.characters = characters;
    }

    Object decode(long record, int field, ByteBuffer input) {
        int length = table.fieldWidth(field);
        input.get(bytes, 0, length);
        checkpoint();
        return switch (table.type(field)) {
            case 'C' -> text(record, field, length);
            case 'N' -> numeric(record, field, length);
            case 'F' -> floating(record, field, length);
            case 'L' -> logical(record, field);
            case 'D' -> date(record, field, length);
            default -> throw new IllegalStateException("Unsupported field entered projection");
        };
    }

    static int mappedCodeUnit(DbfEncoding encoding, int unsignedByte) {
        if (unsignedByte < 0 || unsignedByte > 255) {
            throw new IllegalArgumentException("unsignedByte must be in 0..255");
        }
        String table = manualTable(encoding);
        char mapped = table.charAt(unsignedByte);
        return mapped == INVALID ? -1 : mapped;
    }

    static long mappingChecksum(DbfEncoding encoding) {
        String table = manualTable(encoding);
        long checksum = 0xcbf29ce484222325L;
        for (int index = 0; index < table.length(); index++) {
            int value = table.charAt(index) == INVALID ? -1 : table.charAt(index);
            checksum ^= value & 0xffffL;
            checksum *= 0x100000001b3L;
        }
        return checksum;
    }

    private Object text(long record, int field, int length) {
        boolean allSpaces = true;
        boolean allZero = true;
        int end = length;
        for (int index = 0; index < length; index++) {
            checkpoint(index);
            allSpaces &= bytes[index] == 0x20;
            allZero &= bytes[index] == 0;
        }
        if (allSpaces || allZero) {
            return nullValue(record, field);
        }
        while (end > 0 && bytes[end - 1] == 0x20) {
            end--;
        }
        for (int index = 0; index < end; index++) {
            if (bytes[index] == 0) {
                return invalid(record, field, "embeddedZero");
            }
        }
        int produced = decodeText(record, field, end);
        if (produced < 0) {
            return AttributeNull.INSTANCE;
        }
        checkpoint();
        allocate(Math.multiplyExact((long) produced, 2), record, field);
        checkpoint();
        return new String(characters, 0, produced);
    }

    private int decodeText(long record, int field, int length) {
        DbfEncoding encoding = table.encoding();
        if (encoding == DbfEncoding.UTF_8 || encoding == DbfEncoding.ISO_8859_1) {
            var decoder =
                    (encoding == DbfEncoding.UTF_8
                                    ? StandardCharsets.UTF_8
                                    : StandardCharsets.ISO_8859_1)
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT);
            ByteBuffer input = ByteBuffer.wrap(bytes, 0, length);
            CharBuffer output = CharBuffer.wrap(characters);
            var decoded = decoder.decode(input, output, true);
            var flushed = decoded.isError() ? decoded : decoder.flush(output);
            if (decoded.isError() || flushed.isError() || input.hasRemaining()) {
                int produced = output.position();
                decodedCharacters(produced, record, field);
                invalid(record, field, "encoding");
                return -1;
            }
            int produced = output.position();
            decodedCharacters(produced, record, field);
            return produced;
        }
        String mapping = manualTable(encoding);
        int produced = 0;
        for (int index = 0; index < length; index++) {
            checkpoint(index);
            char value = mapping.charAt(bytes[index] & 0xff);
            if (value == INVALID) {
                decodedCharacters(produced, record, field);
                invalid(record, field, "encoding");
                return -1;
            }
            characters[produced++] = value;
        }
        decodedCharacters(produced, record, field);
        return produced;
    }

    private Object numeric(long record, int field, int length) {
        int start = leadingSpace(length);
        int end = trailingSpace(start, length);
        if (start == end) {
            return nullValue(record, field);
        }
        if (containsZero(start, end)) {
            return invalid(record, field, "embeddedZero");
        }
        int decimals = table.decimalCount(field);
        if (decimals == 0) {
            if (!integerSyntax(start, end)) {
                return invalid(record, field, "syntax");
            }
            String value = ascii(record, field, start, end);
            try {
                long parsed = Long.parseLong(value);
                allocate(8, record, field);
                return parsed;
            } catch (NumberFormatException exception) {
                return invalid(record, field, "overflow");
            }
        }
        int scale = decimalScale(start, end);
        if (scale < 0) {
            return invalid(record, field, "syntax");
        }
        if (scale > decimals) {
            return invalid(record, field, "scale");
        }
        allocate(decimalBytes(start, end), record, field);
        return new BigDecimal(ascii(record, field, start, end));
    }

    private Object floating(long record, int field, int length) {
        int start = leadingSpace(length);
        int end = trailingSpace(start, length);
        if (start == end) {
            return nullValue(record, field);
        }
        if (containsZero(start, end)) {
            return invalid(record, field, "embeddedZero");
        }
        if (!floatingSyntax(start, end)) {
            return invalid(record, field, "syntax");
        }
        String value = ascii(record, field, start, end);
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException exception) {
            return invalid(record, field, "overflow");
        }
        if (!Double.isFinite(parsed)) {
            return invalid(record, field, "nonFinite");
        }
        allocate(8, record, field);
        return parsed;
    }

    private Object logical(long record, int field) {
        return switch (bytes[0]) {
            case 'T', 't', 'Y', 'y' -> scalar(record, field, true, 1);
            case 'F', 'f', 'N', 'n' -> scalar(record, field, false, 1);
            case ' ', '?' -> nullValue(record, field);
            case 0 -> invalid(record, field, "embeddedZero");
            default -> invalid(record, field, "logical");
        };
    }

    private Object date(long record, int field, int length) {
        boolean spaces = true;
        boolean zeroDate = true;
        for (int index = 0; index < length; index++) {
            spaces &= bytes[index] == ' ';
            zeroDate &= bytes[index] == '0';
            if (bytes[index] == 0) {
                return invalid(record, field, "embeddedZero");
            }
        }
        if (spaces || zeroDate) {
            return nullValue(record, field);
        }
        for (int index = 0; index < 8; index++) {
            if (bytes[index] < '0' || bytes[index] > '9') {
                return invalid(record, field, "date");
            }
        }
        int year = digits(0, 4);
        int month = digits(4, 6);
        int day = digits(6, 8);
        try {
            if (year == 0) {
                return invalid(record, field, "date");
            }
            return scalar(record, field, LocalDate.of(year, month, day), 8);
        } catch (DateTimeException exception) {
            return invalid(record, field, "date");
        }
    }

    private Object invalid(long record, int field, String reason) {
        checkpoint();
        warnings.accept(
                DbfDiagnostics.warning(
                        source,
                        "SHAPEFILE_DBF_VALUE_INVALID",
                        "dbf",
                        OptionalLong.of(record),
                        OptionalInt.of(field),
                        Optional.of(table.name(field)),
                        offset(record, field),
                        Map.of("reason", reason)));
        return nullValue(record, field);
    }

    private Object nullValue(long record, int field) {
        return scalar(record, field, AttributeNull.INSTANCE, 1);
    }

    private Object scalar(long record, int field, Object value, long bytes) {
        allocate(bytes, record, field);
        return value;
    }

    private String ascii(long record, int field, int start, int end) {
        int length = end - start;
        checkpoint();
        allocate(Math.multiplyExact((long) length, 2), record, field);
        for (int index = 0; index < length; index++) {
            characters[index] = (char) (bytes[start + index] & 0xff);
        }
        return new String(characters, 0, length);
    }

    private long decimalBytes(int start, int end) {
        long low = 0;
        long high = 0;
        if (bytes[start] == '+' || bytes[start] == '-') {
            start++;
        }
        for (int index = start; index < end; index++) {
            if (bytes[index] == '.') {
                continue;
            }
            long lowProduct = (low & 0xffff_ffffL) * 10 + bytes[index] - '0';
            long highProduct = (low >>> 32) * 10 + (lowProduct >>> 32);
            low = (highProduct << 32) | (lowProduct & 0xffff_ffffL);
            high = high * 10 + (highProduct >>> 32);
        }
        int bitLength =
                high == 0
                        ? low == 0 ? 0 : Long.SIZE - Long.numberOfLeadingZeros(low)
                        : Long.SIZE * 2 - Long.numberOfLeadingZeros(high);
        return 4L + Math.max(1, (bitLength + 7L) / 8L);
    }

    private void allocate(long bytesToCharge, long record, int field) {
        accounting.allocateDbf(
                bytesToCharge, record, field, table.name(field), offset(record, field));
    }

    private void decodedCharacters(long count, long record, int field) {
        accounting.decodedCharacters(
                count, record, field, table.name(field), offset(record, field));
    }

    private int leadingSpace(int length) {
        int start = 0;
        while (start < length && bytes[start] == ' ') {
            start++;
        }
        return start;
    }

    private int trailingSpace(int start, int length) {
        int end = length;
        while (end > start && bytes[end - 1] == ' ') {
            end--;
        }
        return end;
    }

    private boolean containsZero(int start, int end) {
        for (int index = start; index < end; index++) {
            if (bytes[index] == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean integerSyntax(int start, int end) {
        if (bytes[start] == '+' || bytes[start] == '-') {
            start++;
        }
        if (start == end) {
            return false;
        }
        for (int index = start; index < end; index++) {
            if (bytes[index] < '0' || bytes[index] > '9') {
                return false;
            }
        }
        return true;
    }

    private int decimalScale(int start, int end) {
        if (bytes[start] == '+' || bytes[start] == '-') {
            start++;
        }
        int digits = 0;
        int scale = -1;
        for (int index = start; index < end; index++) {
            if (bytes[index] == '.' && scale < 0) {
                scale = 0;
            } else if (bytes[index] >= '0' && bytes[index] <= '9') {
                digits++;
                if (scale >= 0) {
                    scale++;
                }
            } else {
                return -1;
            }
        }
        return digits == 0 || (scale == 0 && bytes[end - 1] == '.') ? -1 : Math.max(0, scale);
    }

    private boolean floatingSyntax(int start, int end) {
        if (bytes[start] == '+' || bytes[start] == '-') {
            start++;
        }
        boolean digit = false;
        boolean point = false;
        boolean exponent = false;
        boolean exponentDigit = false;
        for (int index = start; index < end; index++) {
            byte value = bytes[index];
            if (value >= '0' && value <= '9') {
                digit = true;
                if (exponent) {
                    exponentDigit = true;
                }
            } else if (value == '.' && !point && !exponent) {
                point = true;
            } else if ((value == 'e' || value == 'E') && digit && !exponent) {
                exponent = true;
                if (index + 1 < end && (bytes[index + 1] == '+' || bytes[index + 1] == '-')) {
                    index++;
                }
            } else {
                return false;
            }
        }
        return digit && (!exponent || exponentDigit);
    }

    private int digits(int start, int end) {
        int value = 0;
        for (int index = start; index < end; index++) {
            value = value * 10 + bytes[index] - '0';
        }
        return value;
    }

    private long offset(long record, int field) {
        return table.fieldAbsoluteOffset(record, field);
    }

    private void checkpoint(int work) {
        if ((work & 4095) == 0) {
            checkpoint();
        }
    }

    private void checkpoint() {
        Shapefiles.checkpoint(source, cancellation);
    }

    private static String manualTable(DbfEncoding encoding) {
        return switch (encoding) {
            case WINDOWS_1252 -> WINDOWS_1252;
            case IBM437 -> IBM437;
            case IBM850 -> IBM850;
            case UTF_8, ISO_8859_1 ->
                    throw new IllegalArgumentException("Encoding does not use a manual table");
        };
    }
}
