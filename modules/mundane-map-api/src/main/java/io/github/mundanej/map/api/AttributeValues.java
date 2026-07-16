package io.github.mundanej.map.api;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Canonical Level 1 attribute-value operations. */
public final class AttributeValues {
    private AttributeValues() {}

    /**
     * Returns an insertion-ordered immutable canonical copy.
     *
     * @param attributes source attributes
     * @return immutable canonical attributes
     */
    public static Map<String, Object> canonicalize(Map<String, ?> attributes) {
        Objects.requireNonNull(attributes, "attributes");
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ?> entry : attributes.entrySet()) {
            String key = requireName(entry.getKey());
            if (copy.containsKey(key)) {
                throw new IllegalArgumentException("Duplicate attribute name: " + key);
            }
            copy.put(key, canonicalizeValue(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    /**
     * Returns the canonical representation of one supported value.
     *
     * @param value supported non-null attribute value
     * @return immutable canonical representation
     */
    public static Object canonicalizeValue(Object value) {
        Objects.requireNonNull(value, "attribute value");
        if (value instanceof String
                || value instanceof Boolean
                || value instanceof Long
                || value instanceof BigDecimal
                || value instanceof LocalDate
                || value instanceof AttributeNull
                || value instanceof AttributeBytes) {
            return value;
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return ((Number) value).longValue();
        }
        if (value instanceof Double number) {
            return requireFinite(number);
        }
        if (value instanceof Float number) {
            return (double) requireFinite(number);
        }
        if (value instanceof BigInteger number) {
            return new BigDecimal(number);
        }
        if (value instanceof byte[] bytes) {
            return new AttributeBytes(bytes);
        }
        throw new IllegalArgumentException("Unsupported attribute value type");
    }

    static String requireName(String name) {
        Objects.requireNonNull(name, "attribute name");
        if (name.isBlank() || name.length() > 256) {
            throw new IllegalArgumentException(
                    "attribute name must be non-blank and at most 256 characters");
        }
        return name;
    }

    private static <T extends Number> T requireFinite(T value) {
        if (!Double.isFinite(value.doubleValue())) {
            throw new IllegalArgumentException("Floating attribute values must be finite");
        }
        return value;
    }
}
