package io.github.mundanej.map.api;

import java.util.Arrays;
import java.util.Objects;

/** Immutable value-based binary attribute. */
public final class AttributeBytes {
    private final byte[] bytes;

    /** Copies the supplied bytes. */
    public AttributeBytes(byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes").clone();
    }

    /** Returns the payload length. */
    public int length() {
        return bytes.length;
    }

    /** Returns the byte at an index. */
    public byte byteAt(int index) {
        return bytes[index];
    }

    /** Returns a defensive payload copy. */
    public byte[] toArray() {
        return bytes.clone();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof AttributeBytes value && Arrays.equals(bytes, value.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "AttributeBytes[length=" + bytes.length + "]";
    }
}
