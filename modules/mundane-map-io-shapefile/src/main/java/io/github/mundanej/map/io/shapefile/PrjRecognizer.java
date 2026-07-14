package io.github.mundanej.map.io.shapefile;

/** Deliberately narrow structural matcher for the two approved ESRI WKT1 profiles. */
final class PrjRecognizer {
    private static final String EPSG_4326 =
            String.join(
                    "",
                    "GEOGCS[\"GCS_WGS_1984\",DATUM[\"D_WGS_1984\",",
                    "SPHEROID[\"WGS_1984\",6378137,298.257223563]],",
                    "PRIMEM[\"Greenwich\",0],UNIT[\"Degree\",0.0174532925199433]]");
    private static final String EPSG_3857 =
            String.join(
                    "",
                    "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\",",
                    EPSG_4326,
                    ",PROJECTION[\"Mercator_Auxiliary_Sphere\"],",
                    "PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],",
                    "PARAMETER[\"Central_Meridian\",0],PARAMETER[\"Standard_Parallel_1\",0],",
                    "PARAMETER[\"Auxiliary_Sphere_Type\",0],UNIT[\"Meter\",1]]");

    private PrjRecognizer() {}

    static String recognize(PrjTokenizer tokens) {
        tokens.checkpoint();
        String result =
                matches(tokens, EPSG_4326)
                        ? "EPSG:4326"
                        : matches(tokens, EPSG_3857) ? "EPSG:3857" : null;
        tokens.checkpoint();
        return result;
    }

    private static boolean matches(PrjTokenizer tokens, String expected) {
        int position = 0;
        for (int token = 0; token < tokens.tokenCount(); token++) {
            while (position < expected.length() && whitespace(expected.charAt(position))) {
                position++;
            }
            if (position == expected.length()) {
                return false;
            }
            int start = position;
            byte kind;
            char first = expected.charAt(position);
            if (letter(first) || first == '_') {
                position++;
                while (position < expected.length()) {
                    char next = expected.charAt(position);
                    if (!letter(next) && !digit(next) && next != '_') {
                        break;
                    }
                    position++;
                }
                kind = PrjTokenizer.IDENTIFIER;
            } else if (first == '"') {
                position++;
                while (position < expected.length() && expected.charAt(position) != '"') {
                    position++;
                }
                position++;
                kind = PrjTokenizer.STRING;
            } else if (first == '+' || first == '-' || first == '.' || digit(first)) {
                position = expectedDecimal(expected, position);
                kind = PrjTokenizer.NUMBER;
            } else {
                position++;
                kind =
                        switch (first) {
                            case '[' -> PrjTokenizer.OPEN;
                            case ']' -> PrjTokenizer.CLOSE;
                            case ',' -> PrjTokenizer.COMMA;
                            default -> throw new IllegalStateException("Invalid canonical PRJ");
                        };
            }
            if (tokens.kind(token) != kind
                    || !valueEquals(tokens, token, expected, start, position, kind)) {
                return false;
            }
        }
        while (position < expected.length() && whitespace(expected.charAt(position))) {
            position++;
        }
        return position == expected.length();
    }

    private static boolean valueEquals(
            PrjTokenizer tokens,
            int token,
            String expected,
            int expectedStart,
            int expectedEnd,
            byte kind) {
        if (kind == PrjTokenizer.OPEN || kind == PrjTokenizer.CLOSE || kind == PrjTokenizer.COMMA) {
            return true;
        }
        if (kind == PrjTokenizer.NUMBER) {
            return numericEquals(tokens, token, expected, expectedStart, expectedEnd);
        }
        int inputStart = tokens.start(token);
        int inputEnd = tokens.end(token);
        if (inputEnd - inputStart != expectedEnd - expectedStart) {
            return false;
        }
        byte[] input = tokens.input();
        for (int index = 0; index < inputEnd - inputStart; index++) {
            tokens.checkpoint(inputStart + index);
            int actual = input[inputStart + index] & 0xff;
            int wanted = expected.charAt(expectedStart + index);
            if (kind == PrjTokenizer.IDENTIFIER) {
                actual = upper(actual);
                wanted = upper(wanted);
            }
            if (actual != wanted) {
                return false;
            }
        }
        return true;
    }

    private static boolean numericEquals(
            PrjTokenizer tokens, int token, String expected, int expectedStart, int expectedEnd) {
        int inputStart = tokens.start(token);
        int inputEnd = tokens.end(token);
        byte[] input = tokens.input();
        boolean inputZero = zero(tokens, input, inputStart, inputEnd);
        boolean expectedZero = zero(expected, expectedStart, expectedEnd);
        if (inputZero || expectedZero) {
            return inputZero && expectedZero;
        }
        if (negative(input[inputStart]) != negative(expected.charAt(expectedStart))) {
            return false;
        }
        int inputDigits = significantDigits(tokens, input, inputStart, inputEnd);
        int expectedDigits = significantDigits(expected, expectedStart, expectedEnd);
        if (inputDigits != expectedDigits
                || power(tokens, input, inputStart, inputEnd)
                        != power(expected, expectedStart, expectedEnd)) {
            return false;
        }
        for (int ordinal = 0; ordinal < inputDigits; ordinal++) {
            if (significantDigit(tokens, input, inputStart, inputEnd, ordinal)
                    != significantDigit(expected, expectedStart, expectedEnd, ordinal)) {
                return false;
            }
        }
        return true;
    }

    private static int expectedDecimal(String value, int start) {
        int offset = start;
        if (value.charAt(offset) == '+' || value.charAt(offset) == '-') {
            offset++;
        }
        while (offset < value.length() && digit(value.charAt(offset))) {
            offset++;
        }
        if (offset < value.length() && value.charAt(offset) == '.') {
            offset++;
            while (offset < value.length() && digit(value.charAt(offset))) {
                offset++;
            }
        }
        if (offset < value.length()
                && (value.charAt(offset) == 'e' || value.charAt(offset) == 'E')) {
            offset++;
            if (offset < value.length()
                    && (value.charAt(offset) == '+' || value.charAt(offset) == '-')) {
                offset++;
            }
            while (offset < value.length() && digit(value.charAt(offset))) {
                offset++;
            }
        }
        return offset;
    }

    private static boolean zero(PrjTokenizer tokens, byte[] value, int start, int end) {
        for (int index = start; index < mantissaEnd(tokens, value, start, end); index++) {
            tokens.checkpoint(index);
            if (value[index] >= '1' && value[index] <= '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean zero(String value, int start, int end) {
        for (int index = start; index < mantissaEnd(value, start, end); index++) {
            if (value.charAt(index) >= '1' && value.charAt(index) <= '9') {
                return false;
            }
        }
        return true;
    }

    private static int significantDigits(PrjTokenizer tokens, byte[] value, int start, int end) {
        int first = firstNonzero(tokens, value, start, end);
        int last = lastNonzero(tokens, value, start, end);
        int count = 0;
        for (int index = first; index <= last; index++) {
            tokens.checkpoint(index);
            if (value[index] >= '0' && value[index] <= '9') {
                count++;
            }
        }
        return count;
    }

    private static int significantDigits(String value, int start, int end) {
        int first = firstNonzero(value, start, end);
        int last = lastNonzero(value, start, end);
        int count = 0;
        for (int index = first; index <= last; index++) {
            if (digit(value.charAt(index))) {
                count++;
            }
        }
        return count;
    }

    private static int significantDigit(
            PrjTokenizer tokens, byte[] value, int start, int end, int ordinal) {
        int first = firstNonzero(tokens, value, start, end);
        int last = lastNonzero(tokens, value, start, end);
        for (int index = first; index <= last; index++) {
            tokens.checkpoint(index);
            if (value[index] >= '0' && value[index] <= '9' && ordinal-- == 0) {
                return value[index];
            }
        }
        throw new IllegalStateException("Missing significant digit");
    }

    private static int significantDigit(String value, int start, int end, int ordinal) {
        int first = firstNonzero(value, start, end);
        int last = lastNonzero(value, start, end);
        for (int index = first; index <= last; index++) {
            if (digit(value.charAt(index)) && ordinal-- == 0) {
                return value.charAt(index);
            }
        }
        throw new IllegalStateException("Missing significant digit");
    }

    private static long power(PrjTokenizer tokens, byte[] value, int start, int end) {
        int mantissaEnd = mantissaEnd(tokens, value, start, end);
        int digits = 0;
        int beforePoint = 0;
        boolean point = false;
        for (int index = start; index < mantissaEnd; index++) {
            tokens.checkpoint(index);
            if (value[index] == '.') {
                point = true;
            } else if (value[index] >= '0' && value[index] <= '9') {
                digits++;
                if (!point) {
                    beforePoint++;
                }
            }
        }
        int trailing = trailingZeros(tokens, value, start, mantissaEnd);
        return saturatedAdd(
                exponent(tokens, value, mantissaEnd, end), beforePoint - (long) digits + trailing);
    }

    private static long power(String value, int start, int end) {
        int mantissaEnd = mantissaEnd(value, start, end);
        int digits = 0;
        int beforePoint = 0;
        boolean point = false;
        for (int index = start; index < mantissaEnd; index++) {
            if (value.charAt(index) == '.') {
                point = true;
            } else if (digit(value.charAt(index))) {
                digits++;
                if (!point) {
                    beforePoint++;
                }
            }
        }
        int trailing = trailingZeros(value, start, mantissaEnd);
        return saturatedAdd(
                exponent(value, mantissaEnd, end), beforePoint - (long) digits + trailing);
    }

    private static long exponent(PrjTokenizer tokens, byte[] value, int start, int end) {
        if (start == end) {
            return 0;
        }
        int index = start + 1;
        boolean negative = false;
        if (value[index] == '+' || value[index] == '-') {
            negative = value[index++] == '-';
        }
        long result = 0;
        while (index < end) {
            tokens.checkpoint(index);
            int digit = value[index++] - '0';
            result = result > (Long.MAX_VALUE - digit) / 10 ? Long.MAX_VALUE : result * 10 + digit;
        }
        return negative ? result == Long.MAX_VALUE ? Long.MIN_VALUE : -result : result;
    }

    private static long exponent(String value, int start, int end) {
        if (start == end) {
            return 0;
        }
        int index = start + 1;
        boolean negative = false;
        if (value.charAt(index) == '+' || value.charAt(index) == '-') {
            negative = value.charAt(index++) == '-';
        }
        long result = 0;
        while (index < end) {
            int digit = value.charAt(index++) - '0';
            result = result > (Long.MAX_VALUE - digit) / 10 ? Long.MAX_VALUE : result * 10 + digit;
        }
        return negative ? result == Long.MAX_VALUE ? Long.MIN_VALUE : -result : result;
    }

    private static int mantissaEnd(PrjTokenizer tokens, byte[] value, int start, int end) {
        for (int index = start; index < end; index++) {
            tokens.checkpoint(index);
            if (value[index] == 'e' || value[index] == 'E') {
                return index;
            }
        }
        return end;
    }

    private static int mantissaEnd(String value, int start, int end) {
        for (int index = start; index < end; index++) {
            if (value.charAt(index) == 'e' || value.charAt(index) == 'E') {
                return index;
            }
        }
        return end;
    }

    private static int firstNonzero(PrjTokenizer tokens, byte[] value, int start, int end) {
        int limit = mantissaEnd(tokens, value, start, end);
        for (int index = start; index < limit; index++) {
            tokens.checkpoint(index);
            if (value[index] >= '1' && value[index] <= '9') {
                return index;
            }
        }
        return limit;
    }

    private static int firstNonzero(String value, int start, int end) {
        int limit = mantissaEnd(value, start, end);
        for (int index = start; index < limit; index++) {
            if (value.charAt(index) >= '1' && value.charAt(index) <= '9') {
                return index;
            }
        }
        return limit;
    }

    private static int lastNonzero(PrjTokenizer tokens, byte[] value, int start, int end) {
        for (int index = mantissaEnd(tokens, value, start, end) - 1; index >= start; index--) {
            tokens.checkpoint(index);
            if (value[index] >= '1' && value[index] <= '9') {
                return index;
            }
        }
        return start;
    }

    private static int lastNonzero(String value, int start, int end) {
        for (int index = mantissaEnd(value, start, end) - 1; index >= start; index--) {
            if (value.charAt(index) >= '1' && value.charAt(index) <= '9') {
                return index;
            }
        }
        return start;
    }

    private static int trailingZeros(PrjTokenizer tokens, byte[] value, int start, int end) {
        int count = 0;
        for (int index = end - 1; index >= start; index--) {
            tokens.checkpoint(index);
            if (value[index] == '0') {
                count++;
            } else if (value[index] != '.') {
                break;
            }
        }
        return count;
    }

    private static int trailingZeros(String value, int start, int end) {
        int count = 0;
        for (int index = end - 1; index >= start; index--) {
            if (value.charAt(index) == '0') {
                count++;
            } else if (value.charAt(index) != '.') {
                break;
            }
        }
        return count;
    }

    private static long saturatedAdd(long first, long second) {
        if (second > 0 && first > Long.MAX_VALUE - second) {
            return Long.MAX_VALUE;
        }
        if (second < 0 && first < Long.MIN_VALUE - second) {
            return Long.MIN_VALUE;
        }
        return first + second;
    }

    private static boolean negative(byte value) {
        return value == '-';
    }

    private static boolean negative(char value) {
        return value == '-';
    }

    private static int upper(int value) {
        return value >= 'a' && value <= 'z' ? value - 32 : value;
    }

    private static boolean whitespace(char value) {
        return value == 0x20 || (value >= 0x09 && value <= 0x0d);
    }

    private static boolean letter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private static boolean digit(char value) {
        return value >= '0' && value <= '9';
    }
}
