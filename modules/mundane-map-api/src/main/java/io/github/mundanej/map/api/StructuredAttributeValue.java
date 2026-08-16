package io.github.mundanej.map.api;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, bounded array/object attribute value containing canonical scalar leaves. */
public final class StructuredAttributeValue {
    /** Stable diagnostic code for a structured-value limit failure. */
    public static final String LIMIT_EXCEEDED = "ATTRIBUTE_STRUCTURE_LIMIT_EXCEEDED";

    private final Object value;
    private final int valueCount;
    private final int depth;
    private final long logicalSizeBytes;

    private StructuredAttributeValue(
            Object value, int valueCount, int depth, long logicalSizeBytes) {
        this.value = value;
        this.valueCount = valueCount;
        this.depth = depth;
        this.logicalSizeBytes = logicalSizeBytes;
    }

    /**
     * Canonicalizes a structured array or object under default limits.
     *
     * @param value source list or string-keyed map
     * @return immutable structured value
     */
    public static StructuredAttributeValue of(Object value) {
        return of(value, StructuredAttributeLimits.DEFAULT);
    }

    /**
     * Canonicalizes a structured array or object under explicit limits.
     *
     * @param value source list or string-keyed map
     * @param limits safety limits
     * @return immutable structured value
     * @throws IllegalArgumentException for unsupported values or exceeded limits
     */
    public static StructuredAttributeValue of(Object value, StructuredAttributeLimits limits) {
        Objects.requireNonNull(limits, "limits");
        Counter counter = new Counter(limits);
        Node root = canonicalize(value, 0, counter);
        if (!(root.value() instanceof List<?>) && !(root.value() instanceof Map<?, ?>)) {
            throw new IllegalArgumentException(
                    "A structured attribute root must be an array or object");
        }
        return new StructuredAttributeValue(
                root.value(), counter.valueCount, counter.maximumDepth, root.logicalSizeBytes());
    }

    /**
     * Returns the immutable canonical list or insertion-ordered map.
     *
     * @return canonical value
     */
    public Object value() {
        return value;
    }

    /**
     * Returns the total node count, including the root.
     *
     * @return bounded value count
     */
    public int valueCount() {
        return valueCount;
    }

    /**
     * Returns the deepest array/object edge from the root.
     *
     * @return nesting depth
     */
    public int depth() {
        return depth;
    }

    /**
     * Returns deterministic owned logical payload bytes.
     *
     * @return nonnegative logical size
     */
    public long logicalSizeBytes() {
        return logicalSizeBytes;
    }

    private static Node canonicalize(Object source, int depth, Counter counter) {
        Objects.requireNonNull(source, "structured attribute value");
        counter.visit(depth);
        if (source instanceof List<?> list) {
            counter.checkArray(list.size());
            List<Object> values = new ArrayList<>(list.size());
            long bytes = 8;
            for (Object child : list) {
                Node node = canonicalize(child, depth + 1, counter);
                values.add(node.value());
                bytes = Math.addExact(bytes, node.logicalSizeBytes());
            }
            return new Node(List.copyOf(values), bytes);
        }
        if (source instanceof Map<?, ?> map) {
            counter.checkObject(map.size());
            LinkedHashMap<String, Object> values = new LinkedHashMap<>();
            long bytes = 8;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("Structured object keys must be strings");
                }
                String name = AttributeValues.requireName(key);
                if (values.containsKey(name)) {
                    throw new IllegalArgumentException("Duplicate structured object name: " + name);
                }
                Node node = canonicalize(entry.getValue(), depth + 1, counter);
                values.put(name, node.value());
                bytes =
                        Math.addExact(
                                bytes,
                                Math.addExact(
                                        Math.multiplyExact(name.length(), 2L),
                                        node.logicalSizeBytes()));
            }
            return new Node(Collections.unmodifiableMap(values), bytes);
        }
        Object scalar = AttributeValues.canonicalizeScalar(source);
        return new Node(scalar, scalarBytes(scalar));
    }

    private static long scalarBytes(Object scalar) {
        if (scalar instanceof String text) {
            return Math.multiplyExact(text.length(), 2L);
        }
        if (scalar instanceof AttributeBytes bytes) {
            return bytes.length();
        }
        if (scalar instanceof java.math.BigDecimal decimal) {
            return Math.addExact(
                    4L, Math.max(1, (decimal.unscaledValue().abs().bitLength() + 7L) / 8L));
        }
        return 8;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof StructuredAttributeValue structured
                && value.equals(structured.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "StructuredAttributeValue" + value;
    }

    private record Node(Object value, long logicalSizeBytes) {}

    private static final class Counter {
        private final StructuredAttributeLimits limits;
        private int valueCount;
        private int maximumDepth;

        private Counter(StructuredAttributeLimits limits) {
            this.limits = limits;
        }

        private void visit(int depth) {
            if (depth > limits.maxDepth()) {
                throw limit("maxDepth", depth, limits.maxDepth());
            }
            valueCount = Math.addExact(valueCount, 1);
            if (valueCount > limits.maxValues()) {
                throw limit("maxValues", valueCount, limits.maxValues());
            }
            maximumDepth = Math.max(maximumDepth, depth);
        }

        private void checkArray(int size) {
            if (size > limits.maxArrayElements()) {
                throw limit("maxArrayElements", size, limits.maxArrayElements());
            }
        }

        private void checkObject(int size) {
            if (size > limits.maxObjectMembers()) {
                throw limit("maxObjectMembers", size, limits.maxObjectMembers());
            }
        }

        private static IllegalArgumentException limit(String name, int actual, int maximum) {
            return new IllegalArgumentException(
                    LIMIT_EXCEEDED + ": " + name + " " + actual + " exceeds " + maximum);
        }
    }
}
