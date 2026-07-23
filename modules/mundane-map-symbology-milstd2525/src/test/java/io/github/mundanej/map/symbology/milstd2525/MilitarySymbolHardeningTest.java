package io.github.mundanej.map.symbology.milstd2525;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.MarkerPlacement;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MilitarySymbolHardeningTest {
    private static final String BASE = MilitarySymbolFixtures.FRIEND_INFANTRY_PRESENT;
    private static final MarkerPlacement PLACEMENT = MarkerPlacement.centeredScreen(44);

    @Test
    void everySinglePositionMutationHasDeterministicBoundedPrecedence() {
        String[] expectedCodes = {
            "MIL2525_SIDC_VERSION",
            "MIL2525_SIDC_VERSION",
            "MIL2525_CONTEXT_UNSUPPORTED",
            "MIL2525_IDENTITY_UNSUPPORTED",
            "MIL2525_SYMBOL_SET_UNSUPPORTED",
            "MIL2525_SYMBOL_SET_UNSUPPORTED",
            "MIL2525_STATUS_UNSUPPORTED",
            "MIL2525_HQ_TASK_FORCE_DUMMY_UNSUPPORTED",
            "MIL2525_AMPLIFYING_DESCRIPTOR_UNSUPPORTED",
            "MIL2525_AMPLIFYING_DESCRIPTOR_UNSUPPORTED",
            "MIL2525_ENTITY_UNSUPPORTED",
            "MIL2525_ENTITY_UNSUPPORTED",
            "MIL2525_ENTITY_UNSUPPORTED",
            "MIL2525_ENTITY_UNSUPPORTED",
            "MIL2525_ENTITY_UNSUPPORTED",
            "MIL2525_ENTITY_UNSUPPORTED",
            "MIL2525_MODIFIER_UNSUPPORTED",
            "MIL2525_MODIFIER_UNSUPPORTED",
            "MIL2525_MODIFIER_UNSUPPORTED",
            "MIL2525_MODIFIER_UNSUPPORTED",
            "MIL2525_COMMON_MODIFIER_UNSUPPORTED",
            "MIL2525_COMMON_MODIFIER_UNSUPPORTED",
            "MIL2525_FRAME_SHAPE_MISMATCH",
            "MIL2525_RESERVED_NONZERO",
            "MIL2525_RESERVED_NONZERO",
            "MIL2525_RESERVED_NONZERO",
            "MIL2525_RESERVED_NONZERO",
            "MIL2525_COUNTRY_UNSUPPORTED",
            "MIL2525_COUNTRY_UNSUPPORTED",
            "MIL2525_COUNTRY_UNSUPPORTED"
        };
        for (int index = 0; index < MilitarySymbolId.LENGTH; index++) {
            char[] mutated = BASE.toCharArray();
            mutated[index] = 'F';
            MilitarySymbolId id = MilitarySymbolId.parse(new String(mutated));
            MilitarySymbolAssessment assessment =
                    MilitarySymbolProfile.standard2525EChange1().assess(id);
            assertEquals(
                    expectedCodes[index], assessment.problem().orElseThrow().code(), "" + index);
            assertTrue(assessment.problem().orElseThrow().value().length() <= 6);

            MilitarySymbolException strictFailure =
                    assertThrows(
                            MilitarySymbolException.class,
                            () ->
                                    MilitarySymbols.resolveStrict(
                                            id,
                                            PLACEMENT,
                                            MilitarySymbolPalette.lightBackground()));
            assertEquals(expectedCodes[index], strictFailure.problem().code());
        }
    }

    @Test
    void parserRejectsOversizedAndNonAsciiInputsBeforeSemanticResolution() {
        String oversized = "0".repeat(1_000_000);
        MilitarySymbolException lengthFailure =
                assertThrows(
                        MilitarySymbolException.class, () -> MilitarySymbolId.parse(oversized));
        assertEquals("MIL2525_SIDC_LENGTH", lengthFailure.problem().code());
        assertEquals("1000000", lengthFailure.problem().value());

        for (char hostile : new char[] {' ', '\n', '\0', '\u00e9', '\ud800'}) {
            String value = BASE.substring(0, 14) + hostile + BASE.substring(15);
            MilitarySymbolException failure =
                    assertThrows(
                            MilitarySymbolException.class, () -> MilitarySymbolId.parse(value));
            assertEquals("MIL2525_SIDC_CHARACTER", failure.problem().code());
            assertEquals(15, failure.problem().startPosition());
            assertTrue(failure.problem().value().length() <= 1);
        }
    }

    @Test
    void projectAuthoredReferenceMatrixIsChecksummedCompleteAndResolvable() throws Exception {
        byte[] matrixBytes;
        try (InputStream stream =
                MilitarySymbolCatalog.class.getResourceAsStream(
                        "/META-INF/mundane-map/milstd2525-reference-matrix.tsv")) {
            assertNotNull(stream);
            matrixBytes = stream.readAllBytes();
        }
        Properties provenance = new Properties();
        try (InputStream stream =
                MilitarySymbolCatalog.class.getResourceAsStream(
                        "/META-INF/mundane-map/milstd2525-provenance.properties")) {
            provenance.load(java.util.Objects.requireNonNull(stream));
        }
        String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(matrixBytes));
        assertEquals(provenance.getProperty("fixture.referenceMatrixSha256"), digest);
        assertTrue(provenance.getProperty("fixture.referenceMatrix").contains("project-authored"));

        List<String> lines = new ArrayList<>();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                new java.io.ByteArrayInputStream(matrixBytes),
                                StandardCharsets.UTF_8))) {
            reader.lines().filter(line -> !line.startsWith("#")).forEach(lines::add);
        }
        assertEquals(expectedReferenceRows(), lines);
        List<String[]> rows = lines.stream().map(line -> line.split("\\t", -1)).toList();
        assertEquals(22, rows.size());
        assertEquals(15, rows.stream().filter(row -> row[0].equals("entity")).count());
        assertEquals(7, rows.stream().filter(row -> row[0].equals("modifier")).count());
        assertEquals(
                22,
                rows.stream()
                        .map(row -> row[2])
                        .distinct()
                        .peek(
                                sidc ->
                                        MilitarySymbols.resolveStrict(
                                                MilitarySymbolId.parse(sidc),
                                                PLACEMENT,
                                                MilitarySymbolPalette.lightBackground()))
                        .count());
        rows.forEach(
                row -> {
                    assertEquals(4, row.length);
                    assertTrue(row[3].startsWith("Appendix A Table A-"));
                });
    }

    private static List<String> expectedReferenceRows() {
        return List.of(
                "entity\tLand Unit Infantry\t150310000012110000000030000000"
                        + "\tAppendix A Table A-XXIII",
                "entity\tLand Unit Armor/Mechanized\t150310000012050000000030000000"
                        + "\tAppendix A Table A-XXIII",
                "entity\tLand Unit Field Artillery\t150310000013030000000030000000"
                        + "\tAppendix A Table A-XXIII",
                "entity\tLand Unit Engineer\t150310000014070000000030000000"
                        + "\tAppendix A Table A-XXIII",
                "entity\tLand Unit Medical\t150310000016130000000030000000"
                        + "\tAppendix A Table A-XXIII",
                "entity\tLand Equipment Rifle\t150315000011010000000040000000"
                        + "\tAppendix A Table A-XXIX",
                "entity\tLand Equipment Machine Gun\t150315000011020000000040000000"
                        + "\tAppendix A Table A-XXIX",
                "entity\tLand Equipment Tank\t150315000012020000000040000000"
                        + "\tAppendix A Table A-XXIX",
                "entity\tLand Equipment Medical\t150315000014020000000040000000"
                        + "\tAppendix A Table A-XXIX",
                "entity\tLand Equipment Cross Country Truck"
                        + "\t150315000014080000000040000000\tAppendix A Table A-XXIX",
                "entity\tActivities Civil Disturbance\t150340000012000000000080000000"
                        + "\tAppendix A Table A-XLVIII",
                "entity\tActivities Law Enforcement Operation"
                        + "\t150340000013150000000080000000\tAppendix A Table A-XLVIII",
                "entity\tActivities Fire Event\t150340000014000000000080000000"
                        + "\tAppendix A Table A-XLVIII",
                "entity\tActivities Earthquake Epicenter"
                        + "\t150340000017010300000080000000\tAppendix A Table A-XLVIII",
                "entity\tActivities Flood\t150340000017020200000080000000"
                        + "\tAppendix A Table A-XLVIII",
                "modifier\tLand Unit sector one 25\t150310000012110025000030000000"
                        + "\tAppendix A Table A-XXIV",
                "modifier\tLand Unit sector one 77\t150310000012110077000030000000"
                        + "\tAppendix A Table A-XXIV",
                "modifier\tLand Unit sector two 02\t150310000012110000020030000000"
                        + "\tAppendix A Table A-XXV",
                "modifier\tLand Equipment sector one 13"
                        + "\t150315000012020013000040000000\tAppendix A Table A-XXX",
                "modifier\tLand Equipment sector two 06"
                        + "\t150315000012020000060040000000\tAppendix A Table A-XXXI",
                "modifier\tActivities sector one 17\t150340000014000017000080000000"
                        + "\tAppendix A Table A-XLIX",
                "modifier\tActivities sector two 04\t150340000014000000040080000000"
                        + "\tAppendix A Table A-L");
    }
}
