package io.github.mundanej.map.api;

import java.util.Objects;

/** Shared toolkit-neutral validation for the bounded one-line point-label text profile. */
public final class PointLabelTexts {
    /** Maximum retained Unicode code points in one label. */
    public static final int MAXIMUM_CODE_POINTS = 256;

    private PointLabelTexts() {}

    /**
     * Validates one exact non-blank, bounded, single-line label.
     *
     * @param text exact label text, which is not trimmed or reformatted
     * @return Unicode code-point count
     * @throws NullPointerException if text is null
     * @throws IllegalArgumentException if text is blank, too long, or contains a line separator
     */
    public static int requireSupported(String text) {
        Objects.requireNonNull(text, "text");
        if (text.isBlank()) {
            throw new ValidationException(FailureReason.BLANK, -1);
        }
        int codePoints = 0;
        int lineSeparator = -1;
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            codePoints++;
            if (codePoints > MAXIMUM_CODE_POINTS) {
                throw new ValidationException(FailureReason.TOO_LONG, -1);
            }
            if (lineSeparator < 0 && isLineSeparator(codePoint)) {
                lineSeparator = codePoint;
            }
            offset += Character.charCount(codePoint);
        }
        if (lineSeparator >= 0) {
            throw new ValidationException(FailureReason.MULTILINE, lineSeparator);
        }
        return codePoints;
    }

    /**
     * Returns whether one code point is excluded by the single-line profile.
     *
     * @param codePoint Unicode code point
     * @return true for CR, LF, line separator, or paragraph separator
     */
    public static boolean isLineSeparator(int codePoint) {
        return codePoint == '\r' || codePoint == '\n' || codePoint == 0x2028 || codePoint == 0x2029;
    }

    /** Stable reason exposed to presentation adapters that translate runtime text failures. */
    public enum FailureReason {
        /** Empty or whitespace-only text. */
        BLANK,
        /** More than the supported number of Unicode code points. */
        TOO_LONG,
        /** A prohibited line or paragraph separator. */
        MULTILINE
    }

    /** Field-validation failure with a bounded machine-readable reason. */
    @SuppressWarnings("serial")
    public static final class ValidationException extends IllegalArgumentException {
        /** Stable validation reason. */
        private final FailureReason reason;

        /** Prohibited line separator, or negative one. */
        private final int codePoint;

        private ValidationException(FailureReason reason, int codePoint) {
            super(message(reason));
            this.reason = reason;
            this.codePoint = codePoint;
        }

        /**
         * Returns why validation failed.
         *
         * @return stable failure reason
         */
        public FailureReason reason() {
            return reason;
        }

        /**
         * Returns the prohibited separator, or negative one when not applicable.
         *
         * @return Unicode code point or negative one
         */
        public int codePoint() {
            return codePoint;
        }

        private static String message(FailureReason reason) {
            return switch (reason) {
                case BLANK -> "point-label text must be non-blank";
                case TOO_LONG -> "point-label text must contain at most 256 Unicode code points";
                case MULTILINE -> "point-label text must not contain a line separator";
            };
        }
    }
}
