package io.github.mundanej.map.symbology.milstd2525;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class MilitarySymbolIdTest {
    private static final String FRIEND_INFANTRY = "150310000012110000000030000000";

    @Test
    void parsesCanonicalizesAndExposesEveryField() {
        MilitarySymbolId id = MilitarySymbolId.parse("15031000001211aB25020c3dEaD0Ff");

        assertEquals("15031000001211AB25020C3DEAD0FF", id.canonical());
        assertEquals(0x15, id.version());
        assertEquals(0, id.context());
        assertEquals(3, id.standardIdentity());
        assertEquals(0x10, id.symbolSet());
        assertEquals(0, id.status());
        assertEquals(0, id.headquartersTaskForceDummy());
        assertEquals(0, id.amplifyingDescriptor());
        assertEquals(0x12, id.entity());
        assertEquals(0x11, id.entityType());
        assertEquals(0xAB, id.entitySubtype());
        assertEquals(0x1211AB, id.entityCode());
        assertEquals(0x25, id.sectorOneModifier());
        assertEquals(0x02, id.sectorTwoModifier());
        assertEquals(0, id.sectorOneCommonModifierSelector());
        assertEquals(0xC, id.sectorTwoCommonModifierSelector());
        assertEquals(3, id.frameShape());
        assertEquals(0xDEAD, id.reserved());
        assertEquals(0x0FF, id.countryOrEntityCode());
        assertEquals(id.canonical(), id.toString());
    }

    @Test
    void equalityAndHashUseAllPackedPositions() {
        MilitarySymbolId first = MilitarySymbolId.parse(FRIEND_INFANTRY);
        MilitarySymbolId same = MilitarySymbolId.parse(FRIEND_INFANTRY.toLowerCase(Locale.ROOT));
        MilitarySymbolId different = MilitarySymbolId.parse("150310000012110000000030000001");

        assertEquals(first, same);
        assertEquals(first.hashCode(), same.hashCode());
        assertNotEquals(first, different);
    }

    @Test
    void nullLengthAndCharacterFailuresAreStableAndBounded() {
        assertProblem(
                assertThrows(MilitarySymbolException.class, () -> MilitarySymbolId.parse(null)),
                "MIL2525_SIDC_NULL",
                0,
                0,
                "");
        assertProblem(
                assertThrows(MilitarySymbolException.class, () -> MilitarySymbolId.parse("ABC")),
                "MIL2525_SIDC_LENGTH",
                0,
                0,
                "3");
        assertProblem(
                assertThrows(
                        MilitarySymbolException.class,
                        () -> MilitarySymbolId.parse("15031000001211000000003000000Z")),
                "MIL2525_SIDC_CHARACTER",
                30,
                30,
                "Z");
    }

    private static void assertProblem(
            MilitarySymbolException failure, String code, int start, int end, String value) {
        assertEquals(code, failure.problem().code());
        assertEquals(start, failure.problem().startPosition());
        assertEquals(end, failure.problem().endPosition());
        assertEquals(value, failure.problem().value());
    }
}
