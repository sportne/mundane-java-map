package io.github.mundanej.map.io.svg;

final class SvgNumbers {
    private SvgNumbers() {}

    static Cursor cursor(
            String sourceId,
            String text,
            String field,
            SvgImportLimits limits,
            SvgImportBudget budget) {
        return new Cursor(sourceId, text, field, limits.maximumNumberTokenCharacters(), budget);
    }

    static final class Cursor {
        private final String sourceId;
        private final String text;
        private final int maximumTokenCharacters;
        private final SvgImportBudget budget;
        private int offset;
        private boolean hasValue;
        private String lastField;

        Cursor(
                String sourceId,
                String text,
                String field,
                int maximumTokenCharacters,
                SvgImportBudget budget) {
            this.sourceId = sourceId;
            this.text = text;
            this.lastField = field;
            this.maximumTokenCharacters = maximumTokenCharacters;
            this.budget = budget;
        }

        int offset() {
            return offset;
        }

        boolean atEnd() {
            skipWhitespace();
            if (offset < text.length() && text.charAt(offset) == ',') {
                int next = offset + 1;
                while (next < text.length() && isWhitespace(text.charAt(next))) {
                    next++;
                }
                if (!hasValue
                        || next == text.length()
                        || text.charAt(next) == ','
                        || isAsciiLetter(text.charAt(next))) {
                    throw SvgFailures.value(sourceId, lastField, "syntax");
                }
                return false;
            }
            return offset == text.length();
        }

        boolean nextIsNumber() {
            prepareNumber(lastField);
            if (offset == text.length()) {
                return false;
            }
            char value = text.charAt(offset);
            return value == '+' || value == '-' || value == '.' || isDigit(value);
        }

        char peekRaw() {
            prepareNumber(lastField);
            return offset == text.length() ? '\0' : text.charAt(offset);
        }

        char readLetter() {
            skipWhitespace();
            if (offset < text.length() && text.charAt(offset) == ',') {
                throw SvgFailures.value(sourceId, lastField, "syntax");
            }
            if (offset == text.length() || !isAsciiLetter(text.charAt(offset))) {
                return '\0';
            }
            hasValue = false;
            return text.charAt(offset++);
        }

        double read(String field) {
            prepareNumber(field);
            int start = offset;
            if (offset < text.length()
                    && (text.charAt(offset) == '+' || text.charAt(offset) == '-')) {
                offset++;
            }
            boolean integer = digits();
            boolean fraction = false;
            if (offset < text.length() && text.charAt(offset) == '.') {
                offset++;
                fraction = digits();
            }
            if (!integer && !fraction) {
                throw SvgFailures.value(sourceId, field, "syntax");
            }
            if (offset < text.length()
                    && (text.charAt(offset) == 'e' || text.charAt(offset) == 'E')) {
                int exponent = offset;
                int digit = offset + 1;
                if (digit < text.length()
                        && (text.charAt(digit) == '+' || text.charAt(digit) == '-')) {
                    digit++;
                }
                if (digit < text.length() && isDigit(text.charAt(digit))) {
                    offset = digit;
                    digits();
                } else if (!(digit < text.length() && isAsciiLetter(text.charAt(digit)))) {
                    throw SvgFailures.value(sourceId, field, "syntax");
                } else {
                    offset = exponent;
                }
            }
            int length = offset - start;
            if (length > maximumTokenCharacters) {
                throw SvgFailures.limit(
                        sourceId, "numberTokenCharacters", length, maximumTokenCharacters);
            }
            if (offset < text.length()) {
                char next = text.charAt(offset);
                if ((isAsciiLetter(next) && !field.equals("d"))
                        || (field.equals("d") && beginsSvgLengthUnit())
                        || next == '%') {
                    throw SvgFailures.unsupported(sourceId, "unit");
                }
                if (!(isWhitespace(next)
                        || next == ','
                        || next == '+'
                        || next == '-'
                        || isAsciiLetter(next))) {
                    throw SvgFailures.value(sourceId, field, "syntax");
                }
            }
            double result;
            try {
                budget.chargeOwned(Math.multiplyExact((long) length, 2L));
                result = Double.parseDouble(text.substring(start, offset));
            } catch (NumberFormatException exception) {
                throw SvgFailures.value(sourceId, field, "syntax");
            }
            if (!Double.isFinite(result)) {
                throw SvgFailures.value(sourceId, field, "nonFinite");
            }
            hasValue = true;
            lastField = field;
            return result == 0.0 ? 0.0 : result;
        }

        void requireEnd(String field) {
            if (!atEnd()) {
                throw SvgFailures.value(sourceId, field, "arity");
            }
        }

        private boolean digits() {
            int start = offset;
            while (offset < text.length() && isDigit(text.charAt(offset))) {
                offset++;
            }
            return offset > start;
        }

        private boolean beginsSvgLengthUnit() {
            return startsWith("em")
                    || startsWith("ex")
                    || startsWith("px")
                    || startsWith("pt")
                    || startsWith("pc")
                    || startsWith("cm")
                    || startsWith("mm")
                    || startsWith("in");
        }

        private boolean startsWith(String value) {
            return text.regionMatches(offset, value, 0, value.length());
        }

        private void prepareNumber(String field) {
            skipWhitespace();
            if (offset < text.length() && text.charAt(offset) == ',') {
                if (!hasValue) {
                    throw SvgFailures.value(sourceId, field, "syntax");
                }
                offset++;
                skipWhitespace();
                if (offset == text.length()
                        || text.charAt(offset) == ','
                        || isAsciiLetter(text.charAt(offset))) {
                    throw SvgFailures.value(sourceId, field, "syntax");
                }
                hasValue = false;
            }
        }

        private void skipWhitespace() {
            while (offset < text.length() && isWhitespace(text.charAt(offset))) {
                offset++;
            }
        }

        private static boolean isDigit(char value) {
            return value >= '0' && value <= '9';
        }

        private static boolean isAsciiLetter(char value) {
            return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
        }

        private static boolean isWhitespace(char value) {
            return value == ' ' || value == '\t' || value == '\r' || value == '\n';
        }
    }
}
