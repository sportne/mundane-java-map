package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeValueCandidate;
import io.github.mundanej.map.api.AttributeValueConversion;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Map;
import java.util.Optional;

/** Deterministic closed conversions shared by standards-neutral portrayal selectors. */
final class AttributeValueConversions {
    private AttributeValueConversions() {}

    static Optional<ThematicValue> convert(
            Object primary, AttributeValueConversion conversion, Map<String, Object> attributes) {
        if (conversion.operation() == AttributeValueConversion.Operation.IDENTITY) {
            return ThematicValue.fromAttribute(primary);
        }
        if (conversion.operation() == AttributeValueConversion.Operation.TO_STRING) {
            return Optional.of(ThematicValue.text(LabelTextValues.stringify(primary)));
        }
        if (conversion.candidates().isEmpty()) {
            return toNumber(primary);
        }
        for (AttributeValueCandidate candidate : conversion.candidates()) {
            Object value =
                    candidate instanceof AttributeValueCandidate.Attribute attribute
                            ? attributes.getOrDefault(attribute.name(), AttributeNull.INSTANCE)
                            : literal((AttributeValueCandidate.Literal) candidate);
            Optional<ThematicValue> converted = toNumber(value);
            if (converted.isPresent()) {
                return converted;
            }
        }
        return Optional.empty();
    }

    private static Object literal(AttributeValueCandidate.Literal candidate) {
        return candidate.value().value();
    }

    private static Optional<ThematicValue> toNumber(Object value) {
        if (value == AttributeNull.INSTANCE) {
            return Optional.of(ThematicValue.numeric(BigDecimal.ZERO));
        }
        if (value instanceof Boolean logical) {
            return Optional.of(ThematicValue.numeric(logical ? BigDecimal.ONE : BigDecimal.ZERO));
        }
        Optional<ThematicValue> canonical = ThematicValue.fromAttribute(value);
        if (canonical.isPresent() && canonical.orElseThrow().kind() == ThematicValue.Kind.NUMERIC) {
            return canonical;
        }
        if (!(value instanceof String text)) {
            return Optional.empty();
        }
        String stripped = stripEcmaWhitespace(text);
        if (stripped.isEmpty()) {
            return Optional.of(ThematicValue.numeric(BigDecimal.ZERO));
        }
        try {
            BigDecimal result;
            if (stripped.startsWith("0x") || stripped.startsWith("0X")) {
                result = new BigDecimal(new BigInteger(stripped.substring(2), 16));
            } else if (stripped.startsWith("0b") || stripped.startsWith("0B")) {
                result = new BigDecimal(new BigInteger(stripped.substring(2), 2));
            } else if (stripped.startsWith("0o") || stripped.startsWith("0O")) {
                result = new BigDecimal(new BigInteger(stripped.substring(2), 8));
            } else {
                result = new BigDecimal(stripped);
            }
            return Optional.of(ThematicValue.numeric(result));
        } catch (NumberFormatException failure) {
            return Optional.empty();
        }
    }

    private static String stripEcmaWhitespace(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!ecmaWhitespace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!ecmaWhitespace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    private static boolean ecmaWhitespace(int codePoint) {
        return codePoint == 0x0009
                || codePoint == 0x000a
                || codePoint == 0x000b
                || codePoint == 0x000c
                || codePoint == 0x000d
                || codePoint == 0x0020
                || codePoint == 0x00a0
                || codePoint == 0x1680
                || codePoint == 0x2000
                || codePoint == 0x2001
                || codePoint == 0x2002
                || codePoint == 0x2003
                || codePoint == 0x2004
                || codePoint == 0x2005
                || codePoint == 0x2006
                || codePoint == 0x2007
                || codePoint == 0x2008
                || codePoint == 0x2009
                || codePoint == 0x200a
                || codePoint == 0x2028
                || codePoint == 0x2029
                || codePoint == 0x202f
                || codePoint == 0x205f
                || codePoint == 0x3000
                || codePoint == 0xfeff;
    }
}
