package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeNull;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

final class MapLibreValues {
    private MapLibreValues() {}

    static Map<String, Object> metadata(Map<String, Object> values, String name) {
        Objects.requireNonNull(values, name);
        LinkedHashMap<String, Object> copy = new LinkedHashMap<>();
        values.forEach(
                (key, value) -> {
                    Objects.requireNonNull(key, name + " key");
                    Objects.requireNonNull(value, name + " value");
                    if (key.length() > 1_048_576 || !isScalar(value)) {
                        throw new IllegalArgumentException(
                                name + " is outside the scalar metadata profile");
                    }
                    copy.put(key, value);
                });
        return Collections.unmodifiableMap(copy);
    }

    static String retained(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.length() > 1_048_576) {
            throw new IllegalArgumentException(name + " is outside the bounded profile");
        }
        return value;
    }

    private static boolean isScalar(Object value) {
        return value instanceof String
                || value instanceof Boolean
                || value instanceof BigDecimal
                || value == AttributeNull.INSTANCE;
    }
}
