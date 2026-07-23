package io.github.mundanej.map.symbology.milstd2525;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MilitarySymbolCatalogTest {
    private static final MarkerPlacement PLACEMENT = MarkerPlacement.centeredScreen(44);

    @Test
    void everyApprovedCombinationResolvesWithItsEntityAndModifiers() {
        int resolved = 0;
        Set<Object> entityPaths = new HashSet<>();
        for (MilitarySymbolCatalogEntry entry : MilitarySymbolCatalog.entries()) {
            for (int identity = 0; identity <= 6; identity++) {
                for (int status = 0; status <= 1; status++) {
                    for (int sectorOne : sectorOne(entry.symbolSet())) {
                        for (int sectorTwo : sectorTwo(entry.symbolSet())) {
                            MilitarySymbolId id =
                                    MilitarySymbolId.parse(
                                            MilitarySymbolCatalog.sidc(
                                                    identity,
                                                    entry.symbolSet(),
                                                    status,
                                                    entry.entityCode(),
                                                    sectorOne,
                                                    sectorTwo));
                            CompositeSymbol symbol =
                                    (CompositeSymbol)
                                            MilitarySymbols.resolveStrict(
                                                    id,
                                                    PLACEMENT,
                                                    MilitarySymbolPalette.lightBackground());
                            int frameLayers =
                                    status == 1 || identity == 0 || identity == 2 || identity == 5
                                            ? 2
                                            : 1;
                            assertEquals(
                                    frameLayers
                                            + 1
                                            + (sectorOne == 0 ? 0 : 1)
                                            + (sectorTwo == 0 ? 0 : 1),
                                    symbol.children().size());
                            entityPaths.add(
                                    ((VectorMarkerSymbol) symbol.children().get(frameLayers))
                                            .path());
                            resolved++;
                        }
                    }
                }
            }
        }
        assertEquals(980, resolved);
        assertEquals(15, entityPaths.size());
    }

    @Test
    void catalogAndPortrayalAreBoundedImmutableAndDeterministic() {
        assertEquals(15, MilitarySymbolCatalog.entries().size());
        assertThrows(
                UnsupportedOperationException.class, () -> MilitarySymbolCatalog.entries().clear());

        FeaturePortrayal first =
                MilitarySymbolCatalog.portrayal(
                        "sidc", PLACEMENT, MilitarySymbolPalette.lightBackground(), 0.8);
        FeaturePortrayal second =
                MilitarySymbolCatalog.portrayal(
                        "sidc", PLACEMENT, MilitarySymbolPalette.lightBackground(), 0.8);
        CategoricalSymbolSelector selector =
                (CategoricalSymbolSelector) first.marker().orElseThrow();
        assertEquals(980, selector.rules().size());
        assertTrue(selector.fallback().isEmpty());
        assertEquals(first, second);
        assertNotSame(first, second);
    }

    @Test
    void validationRejectsDuplicateAndUnreachableRowsBeforePublication() {
        MilitarySymbolCatalogEntry infantry = MilitarySymbolCatalog.entries().getFirst();
        assertThrows(
                IllegalArgumentException.class,
                () -> MilitarySymbolCatalog.validateCatalog(List.of(infantry, infantry)));
        ArrayList<MilitarySymbolCatalogEntry> unreachable =
                new ArrayList<>(MilitarySymbolCatalog.entries());
        unreachable.add(new MilitarySymbolCatalogEntry(0x10, 0x999999, "Missing"));
        assertThrows(
                IllegalArgumentException.class,
                () -> MilitarySymbolCatalog.validateCatalog(unreachable));
    }

    @Test
    void independentInventoryAndPathFingerprintsPinEverySemanticMapping() {
        List<MilitarySymbolCatalogEntry> expected =
                List.of(
                        new MilitarySymbolCatalogEntry(0x10, 0x121100, "Infantry"),
                        new MilitarySymbolCatalogEntry(0x10, 0x120500, "Armor/Mechanized"),
                        new MilitarySymbolCatalogEntry(0x10, 0x130300, "Field Artillery"),
                        new MilitarySymbolCatalogEntry(0x10, 0x140700, "Engineer"),
                        new MilitarySymbolCatalogEntry(0x10, 0x161300, "Medical"),
                        new MilitarySymbolCatalogEntry(0x15, 0x110100, "Rifle"),
                        new MilitarySymbolCatalogEntry(0x15, 0x110200, "Machine Gun"),
                        new MilitarySymbolCatalogEntry(0x15, 0x120200, "Tank"),
                        new MilitarySymbolCatalogEntry(0x15, 0x140200, "Medical"),
                        new MilitarySymbolCatalogEntry(0x15, 0x140800, "Cross Country Truck"),
                        new MilitarySymbolCatalogEntry(0x40, 0x120000, "Civil Disturbance"),
                        new MilitarySymbolCatalogEntry(0x40, 0x131500, "Law Enforcement Operation"),
                        new MilitarySymbolCatalogEntry(0x40, 0x140000, "Fire Event"),
                        new MilitarySymbolCatalogEntry(0x40, 0x170103, "Earthquake Epicenter"),
                        new MilitarySymbolCatalogEntry(0x40, 0x170202, "Flood"));
        assertEquals(expected, MilitarySymbolCatalog.entries());

        Map<String, Integer> entityFingerprints =
                Map.ofEntries(
                        Map.entry("10:121100", -2000040994),
                        Map.entry("10:120500", -2002093188),
                        Map.entry("10:130300", 683365435),
                        Map.entry("10:140700", 431523996),
                        Map.entry("10:161300", -1309827138),
                        Map.entry("15:110100", 729723423),
                        Map.entry("15:110200", 241322140),
                        Map.entry("15:120200", -986894404),
                        Map.entry("15:140200", 608840672),
                        Map.entry("15:140800", 1224737974),
                        Map.entry("40:120000", -861052782),
                        Map.entry("40:131500", 1584647372),
                        Map.entry("40:140000", 1538312320),
                        Map.entry("40:170103", -1539588257),
                        Map.entry("40:170202", 1429291457));
        for (MilitarySymbolCatalogEntry entry : expected) {
            String key = "%02X:%06X".formatted(entry.symbolSet(), entry.entityCode());
            assertEquals(
                    entityFingerprints.get(key),
                    MilitarySymbolPaths.entity(entry.symbolSet(), entry.entityCode()).hashCode(),
                    key);
        }

        assertEquals(-120134627, MilitarySymbolPaths.sectorOne(0x10, 0x25).hashCode());
        assertEquals(1101614046, MilitarySymbolPaths.sectorOne(0x10, 0x77).hashCode());
        assertEquals(-9231331, MilitarySymbolPaths.sectorTwo(0x10, 0x02).hashCode());
        assertEquals(-1100886303, MilitarySymbolPaths.sectorOne(0x15, 0x13).hashCode());
        assertEquals(1521468157, MilitarySymbolPaths.sectorTwo(0x15, 0x06).hashCode());
        assertEquals(-894350370, MilitarySymbolPaths.sectorOne(0x40, 0x17).hashCode());
        assertEquals(604231896, MilitarySymbolPaths.sectorTwo(0x40, 0x04).hashCode());
    }

    @Test
    void currentSupportedSidcsAreDigitsOnlySoCaseNormalizationIsIdentity() {
        for (CategoricalSymbolRule rule :
                ((CategoricalSymbolSelector)
                                MilitarySymbolCatalog.portrayal(
                                                "sidc",
                                                PLACEMENT,
                                                MilitarySymbolPalette.lightBackground(),
                                                1)
                                        .marker()
                                        .orElseThrow())
                        .rules()) {
            String sidc = (String) rule.value().value();
            assertTrue(sidc.matches("[0-9]{30}"), sidc);
            assertEquals(sidc, MilitarySymbolId.parse(sidc.toLowerCase(Locale.ROOT)).canonical());
        }
    }

    @Test
    void artifactProvenanceAccountsForEveryPathAndSharedFixture() throws Exception {
        Properties properties = new Properties();
        try (java.io.InputStream stream =
                MilitarySymbolCatalog.class.getResourceAsStream(
                        "/META-INF/mundane-map/milstd2525-provenance.properties")) {
            properties.load(java.util.Objects.requireNonNull(stream));
        }
        Set<String> expectedKeys =
                Set.of(
                        "format",
                        "standard",
                        "assistRecord",
                        "normalizedTextSha256",
                        "license",
                        "artwork",
                        "path.frame.unknown",
                        "path.frame.friend",
                        "path.frame.neutral",
                        "path.frame.hostile",
                        "path.frame.segmented.unknown",
                        "path.frame.segmented.friend",
                        "path.frame.segmented.neutral",
                        "path.frame.segmented.hostile",
                        "path.entity.10.121100",
                        "path.entity.10.120500",
                        "path.entity.10.130300",
                        "path.entity.10.140700",
                        "path.entity.10.161300",
                        "path.entity.15.110100",
                        "path.entity.15.110200",
                        "path.entity.15.120200",
                        "path.entity.15.140200",
                        "path.entity.15.140800",
                        "path.entity.40.120000",
                        "path.entity.40.131500",
                        "path.entity.40.140000",
                        "path.entity.40.170103",
                        "path.entity.40.170202",
                        "path.modifier.10.1.25",
                        "path.modifier.10.1.77",
                        "path.modifier.10.2.02",
                        "path.modifier.15.1.13",
                        "path.modifier.15.2.06",
                        "path.modifier.40.1.17",
                        "path.modifier.40.2.04",
                        "fixture.FRIEND_INFANTRY_PRESENT",
                        "fixture.referenceMatrix",
                        "fixture.referenceMatrixSha256");
        assertEquals(expectedKeys, properties.stringPropertyNames());
        assertEquals("1", properties.getProperty("format"));
        assertEquals("114934", properties.getProperty("assistRecord"));
        for (MilitarySymbolCatalogEntry entry : MilitarySymbolCatalog.entries()) {
            String key = "path.entity.%02X.%06X".formatted(entry.symbolSet(), entry.entityCode());
            assertTrue(properties.getProperty(key).contains(entry.name()), key);
        }
        for (String key :
                List.of(
                        "path.frame.unknown",
                        "path.frame.friend",
                        "path.frame.neutral",
                        "path.frame.hostile",
                        "path.frame.segmented.unknown",
                        "path.frame.segmented.friend",
                        "path.frame.segmented.neutral",
                        "path.frame.segmented.hostile",
                        "path.modifier.10.1.25",
                        "path.modifier.10.1.77",
                        "path.modifier.10.2.02",
                        "path.modifier.15.1.13",
                        "path.modifier.15.2.06",
                        "path.modifier.40.1.17",
                        "path.modifier.40.2.04",
                        "fixture.FRIEND_INFANTRY_PRESENT")) {
            assertTrue(properties.containsKey(key), key);
        }
        assertTrue(
                properties
                        .getProperty("fixture.FRIEND_INFANTRY_PRESENT")
                        .startsWith(MilitarySymbolFixtures.FRIEND_INFANTRY_PRESENT));
    }

    private static int[] sectorOne(int symbolSet) {
        return switch (symbolSet) {
            case 0x10 -> new int[] {0, 0x25, 0x77};
            case 0x15 -> new int[] {0, 0x13};
            case 0x40 -> new int[] {0, 0x17};
            default -> throw new AssertionError();
        };
    }

    private static int[] sectorTwo(int symbolSet) {
        return switch (symbolSet) {
            case 0x10 -> new int[] {0, 0x02};
            case 0x15 -> new int[] {0, 0x06};
            case 0x40 -> new int[] {0, 0x04};
            default -> throw new AssertionError();
        };
    }
}
