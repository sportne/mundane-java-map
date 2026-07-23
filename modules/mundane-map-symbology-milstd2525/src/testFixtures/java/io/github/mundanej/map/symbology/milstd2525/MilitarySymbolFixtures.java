package io.github.mundanej.map.symbology.milstd2525;

import java.util.Locale;

/** Shared project-authored identifiers for examples and cross-module rendering tests. */
public final class MilitarySymbolFixtures {
    /** Supported present Friend Infantry with no modifiers. */
    public static final String FRIEND_INFANTRY_PRESENT = firstInfantry(3, 0);

    private MilitarySymbolFixtures() {}

    /**
     * Returns the supported first-slice Infantry identifier for one identity and status.
     *
     * @param identity supported standard identity from zero through six
     * @param status present zero or planned one
     * @return canonical identifier text
     */
    public static String firstInfantry(int identity, int status) {
        if (identity < 0 || identity > 6) {
            throw new IllegalArgumentException("identity must be between zero and six");
        }
        if (status < 0 || status > 1) {
            throw new IllegalArgumentException("status must be zero or one");
        }
        String value = "150310000012110000000030000000";
        value = replace(value, 4, Integer.toHexString(identity));
        return replace(value, 7, Integer.toHexString(status));
    }

    private static String replace(String value, int oneBasedPosition, String replacement) {
        int index = oneBasedPosition - 1;
        return value.substring(0, index)
                + replacement.toUpperCase(Locale.ROOT)
                + value.substring(index + 1);
    }
}
