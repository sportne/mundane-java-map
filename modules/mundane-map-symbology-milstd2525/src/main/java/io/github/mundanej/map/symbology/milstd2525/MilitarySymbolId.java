package io.github.mundanej.map.symbology.milstd2525;

import java.util.Objects;

/**
 * Immutable packed canonical 30-position MIL-STD-2525 symbol identifier.
 *
 * <p>Parsing accepts only ASCII hexadecimal characters and canonicalizes letters to uppercase. A
 * syntactically valid identifier may still be outside {@link MilitarySymbolProfile}'s bounded
 * support profile.
 */
public final class MilitarySymbolId {
    /** Required number of hexadecimal positions. */
    public static final int LENGTH = 30;

    private static final int HALF_LENGTH = LENGTH / 2;
    private static final char[] HEX = "0123456789ABCDEF".toCharArray();

    private final long high;
    private final long low;

    private MilitarySymbolId(long high, long low) {
        this.high = high;
        this.low = low;
    }

    /**
     * Parses and canonicalizes one exact 30-position hexadecimal identifier.
     *
     * @param value identifier text
     * @return packed immutable identifier
     * @throws MilitarySymbolException when null, incorrectly sized, or non-hexadecimal
     */
    public static MilitarySymbolId parse(String value) {
        if (value == null) {
            throw failure("MIL2525_SIDC_NULL", "sidc", 0, 0, "", "SIDC must not be null");
        }
        if (value.length() != LENGTH) {
            throw failure(
                    "MIL2525_SIDC_LENGTH",
                    "sidc",
                    0,
                    0,
                    Integer.toString(value.length()),
                    "SIDC must contain exactly 30 hexadecimal positions");
        }
        long first = 0L;
        long second = 0L;
        for (int index = 0; index < LENGTH; index++) {
            int nibble = hexNibble(value.charAt(index));
            if (nibble < 0) {
                throw failure(
                        "MIL2525_SIDC_CHARACTER",
                        "sidc",
                        index + 1,
                        index + 1,
                        String.valueOf(value.charAt(index)),
                        "SIDC contains a non-hexadecimal character");
            }
            if (index < HALF_LENGTH) {
                first = (first << 4) | nibble;
            } else {
                second = (second << 4) | nibble;
            }
        }
        return new MilitarySymbolId(first, second);
    }

    /**
     * Returns positions 1–2 as an unsigned hexadecimal value.
     *
     * @return version
     */
    public int version() {
        return value(1, 2);
    }

    /**
     * Returns the context at position 3.
     *
     * @return context
     */
    public int context() {
        return value(3, 3);
    }

    /**
     * Returns the standard identity at position 4.
     *
     * @return standard identity
     */
    public int standardIdentity() {
        return value(4, 4);
    }

    /**
     * Returns the symbol set at positions 5–6.
     *
     * @return symbol set
     */
    public int symbolSet() {
        return value(5, 6);
    }

    /**
     * Returns status at position 7.
     *
     * @return status
     */
    public int status() {
        return value(7, 7);
    }

    /**
     * Returns headquarters/task-force/dummy at position 8.
     *
     * @return headquarters/task-force/dummy value
     */
    public int headquartersTaskForceDummy() {
        return value(8, 8);
    }

    /**
     * Returns the amplifying descriptor at positions 9–10.
     *
     * @return amplifying descriptor
     */
    public int amplifyingDescriptor() {
        return value(9, 10);
    }

    /**
     * Returns the entity at positions 11–12.
     *
     * @return entity
     */
    public int entity() {
        return value(11, 12);
    }

    /**
     * Returns the entity type at positions 13–14.
     *
     * @return entity type
     */
    public int entityType() {
        return value(13, 14);
    }

    /**
     * Returns the entity subtype at positions 15–16.
     *
     * @return entity subtype
     */
    public int entitySubtype() {
        return value(15, 16);
    }

    /**
     * Returns the combined entity/type/subtype at positions 11–16.
     *
     * @return six-position entity code
     */
    public int entityCode() {
        return value(11, 16);
    }

    /**
     * Returns the symbol-set sector 1 modifier at positions 17–18.
     *
     * @return sector 1 modifier
     */
    public int sectorOneModifier() {
        return value(17, 18);
    }

    /**
     * Returns the symbol-set sector 2 modifier at positions 19–20.
     *
     * @return sector 2 modifier
     */
    public int sectorTwoModifier() {
        return value(19, 20);
    }

    /**
     * Returns the sector 1 common-modifier selector at position 21.
     *
     * @return sector 1 common-modifier selector
     */
    public int sectorOneCommonModifierSelector() {
        return value(21, 21);
    }

    /**
     * Returns the sector 2 common-modifier selector at position 22.
     *
     * @return sector 2 common-modifier selector
     */
    public int sectorTwoCommonModifierSelector() {
        return value(22, 22);
    }

    /**
     * Returns the frame shape at position 23.
     *
     * @return frame shape
     */
    public int frameShape() {
        return value(23, 23);
    }

    /**
     * Returns reserved positions 24–27.
     *
     * @return reserved value
     */
    public int reserved() {
        return value(24, 27);
    }

    /**
     * Returns the country/geopolitical/entity code at positions 28–30.
     *
     * @return country/geopolitical/entity code
     */
    public int countryOrEntityCode() {
        return value(28, 30);
    }

    /**
     * Returns the exact uppercase canonical representation.
     *
     * @return 30 ASCII hexadecimal characters
     */
    public String canonical() {
        char[] result = new char[LENGTH];
        for (int index = 0; index < LENGTH; index++) {
            result[index] = HEX[nibble(index + 1)];
        }
        return new String(result);
    }

    @Override
    public boolean equals(Object candidate) {
        return candidate instanceof MilitarySymbolId other
                && high == other.high
                && low == other.low;
    }

    @Override
    public int hashCode() {
        return Objects.hash(high, low);
    }

    @Override
    public String toString() {
        return canonical();
    }

    String slice(int startPosition, int endPosition) {
        char[] result = new char[endPosition - startPosition + 1];
        for (int position = startPosition; position <= endPosition; position++) {
            result[position - startPosition] = HEX[nibble(position)];
        }
        return new String(result);
    }

    private int value(int startPosition, int endPosition) {
        int result = 0;
        for (int position = startPosition; position <= endPosition; position++) {
            result = (result << 4) | nibble(position);
        }
        return result;
    }

    private int nibble(int oneBasedPosition) {
        if (oneBasedPosition <= HALF_LENGTH) {
            int shift = (HALF_LENGTH - oneBasedPosition) * 4;
            return (int) ((high >>> shift) & 0x0F);
        }
        int secondPosition = oneBasedPosition - HALF_LENGTH;
        int shift = (HALF_LENGTH - secondPosition) * 4;
        return (int) ((low >>> shift) & 0x0F);
    }

    private static int hexNibble(char value) {
        if (value >= '0' && value <= '9') {
            return value - '0';
        }
        if (value >= 'A' && value <= 'F') {
            return value - 'A' + 10;
        }
        if (value >= 'a' && value <= 'f') {
            return value - 'a' + 10;
        }
        return -1;
    }

    private static MilitarySymbolException failure(
            String code,
            String field,
            int startPosition,
            int endPosition,
            String value,
            String message) {
        return new MilitarySymbolException(
                message, new MilitarySymbolProblem(code, field, startPosition, endPosition, value));
    }
}
