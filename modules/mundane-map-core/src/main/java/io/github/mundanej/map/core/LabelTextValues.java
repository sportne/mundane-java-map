package io.github.mundanej.map.core;

import io.github.mundanej.map.api.AttributeNull;
import java.math.BigDecimal;

/** Closed deterministic string conversion for point-label attribute values. */
final class LabelTextValues {
    private LabelTextValues() {}

    static String stringify(Object value) {
        if (value == AttributeNull.INSTANCE) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        if (value instanceof Boolean logical) {
            return logical.toString();
        }
        if (value instanceof Long number) {
            return Long.toString(number);
        }
        if (value instanceof Double number && Double.isFinite(number)) {
            return decimal(BigDecimal.valueOf(number));
        }
        if (value instanceof BigDecimal number) {
            return decimal(number);
        }
        return "";
    }

    private static String decimal(BigDecimal value) {
        BigDecimal normalized = value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
        int exponent = normalized.precision() - normalized.scale() - 1;
        if (exponent >= 21 || exponent <= -7) {
            String digits = normalized.unscaledValue().abs().toString();
            String significand =
                    digits.length() == 1 ? digits : digits.charAt(0) + "." + digits.substring(1);
            return (normalized.signum() < 0 ? "-" : "")
                    + significand
                    + 'e'
                    + (exponent >= 0 ? "+" : "")
                    + exponent;
        }
        return normalized.toPlainString();
    }
}
