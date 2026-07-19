package io.github.mundanej.map.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Optional;

/** Exact closed value used by categorical and graduated portrayal selectors. */
public final class ThematicValue {
    /** Supported exact thematic value forms. */
    public enum Kind {
        /** Exact text. */
        TEXT,
        /** Boolean logical value. */
        LOGICAL,
        /** Normalized finite decimal number. */
        NUMERIC,
        /** Exact local date. */
        DATE,
        /** Explicit canonical null. */
        NULL
    }

    private static final ThematicValue NULL_VALUE =
            new ThematicValue(Kind.NULL, AttributeNull.INSTANCE);

    private final Kind kind;
    private final Object value;

    private ThematicValue(Kind kind, Object value) {
        this.kind = kind;
        this.value = value;
    }

    /**
     * Returns an exact text category.
     *
     * @param value retained exact text
     * @return immutable text category
     */
    public static ThematicValue text(String value) {
        return new ThematicValue(Kind.TEXT, Objects.requireNonNull(value, "value"));
    }

    /**
     * Returns an exact logical category.
     *
     * @param value boolean category
     * @return immutable logical category
     */
    public static ThematicValue logical(boolean value) {
        return new ThematicValue(Kind.LOGICAL, value);
    }

    /**
     * Returns a normalized integral numeric category.
     *
     * @param value integral category
     * @return immutable normalized numeric category
     */
    public static ThematicValue numeric(long value) {
        return new ThematicValue(Kind.NUMERIC, normalize(BigDecimal.valueOf(value)));
    }

    /**
     * Returns a normalized finite floating numeric category.
     *
     * @param value finite floating category
     * @return immutable normalized numeric category
     * @throws IllegalArgumentException when {@code value} is not finite
     */
    public static ThematicValue numeric(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("value must be finite");
        }
        return new ThematicValue(Kind.NUMERIC, normalize(BigDecimal.valueOf(value)));
    }

    /**
     * Returns a normalized decimal numeric category.
     *
     * @param value non-null decimal category
     * @return immutable normalized numeric category
     */
    public static ThematicValue numeric(BigDecimal value) {
        return new ThematicValue(Kind.NUMERIC, normalize(Objects.requireNonNull(value, "value")));
    }

    /**
     * Returns an exact date category.
     *
     * @param value retained exact date
     * @return immutable date category
     */
    public static ThematicValue date(LocalDate value) {
        return new ThematicValue(Kind.DATE, Objects.requireNonNull(value, "value"));
    }

    /**
     * Returns the singleton explicit-null category.
     *
     * @return immutable explicit-null category
     */
    public static ThematicValue nullValue() {
        return NULL_VALUE;
    }

    /**
     * Converts one canonical feature attribute when it has a supported thematic form.
     *
     * @param value present canonical attribute value
     * @return exact thematic value, or empty for unsupported binary values
     */
    public static Optional<ThematicValue> fromAttribute(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof String text) {
            return Optional.of(text(text));
        }
        if (value instanceof Boolean logical) {
            return Optional.of(logical(logical));
        }
        if (value instanceof Long number) {
            return Optional.of(numeric(number));
        }
        if (value instanceof Double number) {
            return Optional.of(numeric(number));
        }
        if (value instanceof BigDecimal number) {
            return Optional.of(numeric(number));
        }
        if (value instanceof LocalDate date) {
            return Optional.of(date(date));
        }
        if (value instanceof AttributeNull) {
            return Optional.of(NULL_VALUE);
        }
        return Optional.empty();
    }

    /**
     * Returns the value form.
     *
     * @return exact value kind
     */
    public Kind kind() {
        return kind;
    }

    /**
     * Returns the exact immutable retained value.
     *
     * @return string, boolean, normalized decimal, date, or {@link AttributeNull#INSTANCE}
     */
    public Object value() {
        return value;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof ThematicValue thematic
                && kind == thematic.kind
                && value.equals(thematic.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, value);
    }

    @Override
    public String toString() {
        return "ThematicValue[kind=" + kind + ", value=" + value + ']';
    }

    private static BigDecimal normalize(BigDecimal value) {
        return value.signum() == 0 ? BigDecimal.ZERO : value.stripTrailingZeros();
    }
}
