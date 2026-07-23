package io.github.mundanej.map.symbology.milstd2525;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

class MilitarySymbolProfileTest {
    private static final MilitarySymbolProfile PROFILE =
            MilitarySymbolProfile.standard2525EChange1();

    @Test
    void supportedInventoryAndModifierCartesianProductsClassify() {
        assertSame(PROFILE, MilitarySymbolProfile.standard2525EChange1());
        List<Inventory> inventory =
                List.of(
                        new Inventory(
                                "10",
                                "3",
                                List.of("121100", "120500", "130300", "140700", "161300"),
                                List.of("00", "25", "77"),
                                List.of("00", "02")),
                        new Inventory(
                                "15",
                                "4",
                                List.of("110100", "110200", "120200", "140200", "140800"),
                                List.of("00", "13"),
                                List.of("00", "06")),
                        new Inventory(
                                "40",
                                "8",
                                List.of("120000", "131500", "140000", "170103", "170202"),
                                List.of("00", "17"),
                                List.of("00", "04")));

        for (Inventory entry : inventory) {
            for (String entity : entry.entities()) {
                for (String sectorOne : entry.sectorOne()) {
                    for (String sectorTwo : entry.sectorTwo()) {
                        assertSupported(
                                sidc(
                                        entry.symbolSet(),
                                        entity,
                                        sectorOne,
                                        sectorTwo,
                                        entry.frame()));
                    }
                }
            }
        }

        assertAssessment(
                sidc("10", "121100", "13", "00", "3"),
                MilitarySymbolSupport.DEGRADED_MODIFIER,
                "MIL2525_MODIFIER_UNSUPPORTED",
                17,
                18);
        assertAssessment(
                sidc("15", "120200", "00", "04", "4"),
                MilitarySymbolSupport.DEGRADED_MODIFIER,
                "MIL2525_MODIFIER_UNSUPPORTED",
                19,
                20);
        assertAssessment(
                sidc("40", "140000", "25", "00", "8"),
                MilitarySymbolSupport.DEGRADED_MODIFIER,
                "MIL2525_MODIFIER_UNSUPPORTED",
                17,
                18);
    }

    @Test
    void everyHardFieldHasStablePrecedenceAndLocation() {
        assertUnsupported(replace(sidc(), 1, 2, "14"), "MIL2525_SIDC_VERSION", 1, 2);
        assertUnsupported(replace(sidc(), 3, 3, "1"), "MIL2525_CONTEXT_UNSUPPORTED", 3, 3);
        assertUnsupported(replace(sidc(), 4, 4, "7"), "MIL2525_IDENTITY_UNSUPPORTED", 4, 4);
        assertUnsupported(replace(sidc(), 5, 6, "01"), "MIL2525_SYMBOL_SET_UNSUPPORTED", 5, 6);
        assertUnsupported(replace(sidc(), 7, 7, "2"), "MIL2525_STATUS_UNSUPPORTED", 7, 7);
        assertUnsupported(
                replace(sidc(), 8, 8, "1"), "MIL2525_HQ_TASK_FORCE_DUMMY_UNSUPPORTED", 8, 8);
        assertUnsupported(
                replace(sidc(), 9, 10, "11"), "MIL2525_AMPLIFYING_DESCRIPTOR_UNSUPPORTED", 9, 10);
        assertUnsupported(
                replace(sidc(), 21, 22, "10"), "MIL2525_COMMON_MODIFIER_UNSUPPORTED", 21, 22);
        assertUnsupported(replace(sidc(), 23, 23, "4"), "MIL2525_FRAME_SHAPE_MISMATCH", 23, 23);
        assertUnsupported(replace(sidc(), 24, 27, "0001"), "MIL2525_RESERVED_NONZERO", 24, 27);
        assertUnsupported(replace(sidc(), 28, 30, "840"), "MIL2525_COUNTRY_UNSUPPORTED", 28, 30);
    }

    @Test
    void unknownEntitiesAndModifiersAreDegradableOnlyWithoutHardFailure() {
        assertAssessment(
                replace(sidc(), 11, 16, "FFFFFF"),
                MilitarySymbolSupport.DEGRADED_ENTITY,
                "MIL2525_ENTITY_UNSUPPORTED",
                11,
                16);
        assertAssessment(
                replace(sidc(), 17, 18, "01"),
                MilitarySymbolSupport.DEGRADED_MODIFIER,
                "MIL2525_MODIFIER_UNSUPPORTED",
                17,
                18);
        assertAssessment(
                replace(sidc(), 19, 20, "01"),
                MilitarySymbolSupport.DEGRADED_MODIFIER,
                "MIL2525_MODIFIER_UNSUPPORTED",
                19,
                20);
        assertAssessment(
                replace(replace(sidc(), 11, 16, "FFFFFF"), 23, 23, "4"),
                MilitarySymbolSupport.UNSUPPORTED,
                "MIL2525_ENTITY_UNSUPPORTED",
                11,
                16);
        assertAssessment(
                replace(replace(sidc(), 11, 16, "FFFFFF"), 17, 18, "01"),
                MilitarySymbolSupport.DEGRADED_ENTITY,
                "MIL2525_ENTITY_UNSUPPORTED",
                11,
                16);
        for (String laterHard :
                List.of(
                        replace(sidc(), 21, 22, "10"),
                        replace(sidc(), 23, 23, "4"),
                        replace(sidc(), 24, 27, "0001"),
                        replace(sidc(), 28, 30, "840"))) {
            assertAssessment(
                    replace(laterHard, 17, 18, "01"),
                    MilitarySymbolSupport.UNSUPPORTED,
                    "MIL2525_MODIFIER_UNSUPPORTED",
                    17,
                    18);
            assertAssessment(
                    replace(laterHard, 11, 16, "FFFFFF"),
                    MilitarySymbolSupport.UNSUPPORTED,
                    "MIL2525_ENTITY_UNSUPPORTED",
                    11,
                    16);
        }
    }

    @Test
    void allApprovedIdentitiesAndBothStatusesRemainSupported() {
        for (int identity = 0; identity <= 6; identity++) {
            for (int status = 0; status <= 1; status++) {
                String value =
                        replace(
                                replace(sidc(), 4, 4, Integer.toHexString(identity)),
                                7,
                                7,
                                Integer.toHexString(status));
                assertEquals(
                        MilitarySymbolSupport.SUPPORTED,
                        PROFILE.assess(MilitarySymbolId.parse(value)).support(),
                        value);
            }
        }
    }

    @Test
    void valueObjectsRejectBrokenInvariants() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MilitarySymbolProblem("bad", "field", 1, 1, "0"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MilitarySymbolAssessment(
                                MilitarySymbolSupport.SUPPORTED,
                                java.util.Optional.of(
                                        new MilitarySymbolProblem("CODE", "field", 1, 1, "0"))));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MilitarySymbolAssessment.problem(
                                MilitarySymbolSupport.SUPPORTED,
                                new MilitarySymbolProblem("CODE", "field", 1, 1, "0")));
    }

    private static void assertUnsupported(String value, String code, int start, int end) {
        assertAssessment(value, MilitarySymbolSupport.UNSUPPORTED, code, start, end);
    }

    private static void assertSupported(String value) {
        assertEquals(
                MilitarySymbolSupport.SUPPORTED,
                PROFILE.assess(MilitarySymbolId.parse(value)).support(),
                value);
    }

    private static void assertAssessment(
            String value, MilitarySymbolSupport support, String code, int start, int end) {
        MilitarySymbolAssessment assessment = PROFILE.assess(MilitarySymbolId.parse(value));
        assertEquals(support, assessment.support());
        assertTrue(assessment.problem().isPresent());
        assertEquals(code, assessment.problem().orElseThrow().code());
        assertEquals(start, assessment.problem().orElseThrow().startPosition());
        assertEquals(end, assessment.problem().orElseThrow().endPosition());
    }

    private static String sidc() {
        return sidc("10", "121100", "00", "00", "3");
    }

    private static String sidc(
            String symbolSet, String entity, String sectorOne, String sectorTwo, String frame) {
        return "1503" + symbolSet + "0000" + entity + sectorOne + sectorTwo + "00" + frame
                + "0000000";
    }

    private static String replace(String value, int start, int end, String replacement) {
        return value.substring(0, start - 1)
                + replacement.toUpperCase(Locale.ROOT)
                + value.substring(end);
    }

    private record Inventory(
            String symbolSet,
            String frame,
            List<String> entities,
            List<String> sectorOne,
            List<String> sectorTwo) {}
}
