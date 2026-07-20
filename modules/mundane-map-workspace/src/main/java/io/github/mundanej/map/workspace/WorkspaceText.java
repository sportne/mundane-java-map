package io.github.mundanej.map.workspace;

import java.util.Objects;
import java.util.regex.Pattern;

final class WorkspaceText {
    static final int MAX_LAYERS = 4_096;
    static final long MAX_MODEL_BYTES = 33_554_432L;
    static final String NAMESPACE = "urn:mundanej:map:workspace";
    static final String SUFFIX = ".mmap.xml";
    private static final Pattern OPENER = Pattern.compile("[a-z][a-z0-9-]*(?:\\.[a-z][a-z0-9-]*)+");

    private WorkspaceText() {}

    static String text(String value, String name, int maximum, boolean nonBlank) {
        Objects.requireNonNull(value, name);
        if (value.length() > maximum || (nonBlank && value.isBlank())) {
            throw new IllegalArgumentException(
                    name
                            + (nonBlank ? " must be non-blank and at most " : " must be at most ")
                            + maximum
                            + " characters");
        }
        requireXml(value, name);
        return value;
    }

    static String openerId(String value) {
        text(value, "openerId", 128, true);
        if (!OPENER.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "openerId must use the dotted lowercase key grammar");
        }
        return value;
    }

    static String symbolName(String value, String name) {
        text(value, name, 256, true);
        if (!value.equals(value.strip())) {
            throw new IllegalArgumentException(name + " must not have surrounding whitespace");
        }
        return value;
    }

    static double finite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
        return value == 0.0 ? 0.0 : value;
    }

    static double positive(double value, String name) {
        double checked = finite(value, name);
        if (checked <= 0.0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }

    static double unitInterval(double value, String name) {
        double checked = finite(value, name);
        if (checked < 0.0 || checked > 1.0) {
            throw new IllegalArgumentException(name + " must be between zero and one");
        }
        return checked;
    }

    static void requireXml(String value, String name) {
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            if (!validXmlCodePoint(codePoint)) {
                throw new IllegalArgumentException(name + " contains an invalid XML 1.0 character");
            }
            offset += Character.charCount(codePoint);
        }
    }

    static boolean validXmlCodePoint(int value) {
        return value == 0x9
                || value == 0xA
                || value == 0xD
                || (value >= 0x20 && value <= 0xD7FF)
                || (value >= 0xE000 && value <= 0xFFFD)
                || (value >= 0x10000 && value <= 0x10FFFF);
    }

    static long add(long current, long addition) {
        try {
            return Math.addExact(current, addition);
        } catch (ArithmeticException failure) {
            throw new IllegalArgumentException(
                    "workspace logical allocation is not representable", failure);
        }
    }

    static long stringBytes(String value) {
        return Math.multiplyExact((long) value.length(), 2L);
    }
}
