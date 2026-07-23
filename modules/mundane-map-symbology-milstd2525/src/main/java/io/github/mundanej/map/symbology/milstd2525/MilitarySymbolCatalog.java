package io.github.mundanej.map.symbology.milstd2525;

import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.ThematicValue;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Explicit immutable finite catalog and portrayal factory for the approved profile. */
public final class MilitarySymbolCatalog {
    private static final MilitarySymbolCatalogEntry[] ENTRIES =
            validateCatalog(
                            List.of(
                                    entry(0x10, 0x121100, "Infantry"),
                                    entry(0x10, 0x120500, "Armor/Mechanized"),
                                    entry(0x10, 0x130300, "Field Artillery"),
                                    entry(0x10, 0x140700, "Engineer"),
                                    entry(0x10, 0x161300, "Medical"),
                                    entry(0x15, 0x110100, "Rifle"),
                                    entry(0x15, 0x110200, "Machine Gun"),
                                    entry(0x15, 0x120200, "Tank"),
                                    entry(0x15, 0x140200, "Medical"),
                                    entry(0x15, 0x140800, "Cross Country Truck"),
                                    entry(0x40, 0x120000, "Civil Disturbance"),
                                    entry(0x40, 0x131500, "Law Enforcement Operation"),
                                    entry(0x40, 0x140000, "Fire Event"),
                                    entry(0x40, 0x170103, "Earthquake Epicenter"),
                                    entry(0x40, 0x170202, "Flood")))
                    .toArray(MilitarySymbolCatalogEntry[]::new);

    private MilitarySymbolCatalog() {}

    /**
     * Returns the declaration-ordered immutable approved entity inventory.
     *
     * @return fifteen catalog entries
     */
    public static List<MilitarySymbolCatalogEntry> entries() {
        return List.of(ENTRIES);
    }

    /**
     * Builds an exact SIDC-attribute portrayal for every supported catalog combination.
     *
     * <p>Missing, non-text, malformed, and unsupported attribute values omit the marker. Resolution
     * itself remains available through {@link MilitarySymbols} when a structured diagnostic is
     * required.
     *
     * @param attribute exact feature SIDC attribute
     * @param placement common ordinary marker placement
     * @param palette approved palette
     * @param opacity common symbol opacity
     * @return immutable marker portrayal with no fallback
     */
    public static FeaturePortrayal portrayal(
            String attribute,
            MarkerPlacement placement,
            MilitarySymbolPalette palette,
            double opacity) {
        Objects.requireNonNull(attribute, "attribute");
        ArrayList<CategoricalSymbolRule> rules = new ArrayList<>(980);
        for (MilitarySymbolCatalogEntry entry : ENTRIES) {
            for (int identity = 0; identity <= 6; identity++) {
                for (int status = 0; status <= 1; status++) {
                    for (int sectorOne : sectorOne(entry.symbolSet())) {
                        for (int sectorTwo : sectorTwo(entry.symbolSet())) {
                            String sidc =
                                    sidc(
                                            identity,
                                            entry.symbolSet(),
                                            status,
                                            entry.entityCode(),
                                            sectorOne,
                                            sectorTwo);
                            rules.add(
                                    new CategoricalSymbolRule(
                                            ThematicValue.text(sidc),
                                            MilitarySymbols.resolveStrict(
                                                    MilitarySymbolId.parse(sidc),
                                                    placement,
                                                    palette,
                                                    opacity)));
                        }
                    }
                }
            }
        }
        return FeaturePortrayal.markers(
                new CategoricalSymbolSelector(attribute, rules, Optional.empty()));
    }

    static String sidc(
            int identity, int symbolSet, int status, int entityCode, int sectorOne, int sectorTwo) {
        int frame =
                switch (symbolSet) {
                    case 0x10 -> 3;
                    case 0x15 -> 4;
                    case 0x40 -> 8;
                    default -> throw new IllegalArgumentException("unsupported symbol set");
                };
        return String.format(
                Locale.ROOT,
                "150%1X%02X%1X000%06X%02X%02X00%1X0000000",
                identity,
                symbolSet,
                status,
                entityCode,
                sectorOne,
                sectorTwo,
                frame);
    }

    private static MilitarySymbolCatalogEntry entry(int set, int entity, String name) {
        return new MilitarySymbolCatalogEntry(set, entity, name);
    }

    static List<MilitarySymbolCatalogEntry> validateCatalog(
            List<MilitarySymbolCatalogEntry> entries) {
        List<MilitarySymbolCatalogEntry> copy = List.copyOf(entries);
        Set<Long> keys = new HashSet<>();
        for (MilitarySymbolCatalogEntry entry : copy) {
            long key = ((long) entry.symbolSet() << 24) | entry.entityCode();
            if (!keys.add(key)) {
                throw new IllegalArgumentException("duplicate military symbol catalog key");
            }
            if (MilitarySymbolPaths.entity(entry.symbolSet(), entry.entityCode()) == null) {
                throw new IllegalArgumentException("catalog entry has no reachable vector path");
            }
        }
        return copy;
    }

    private static int[] sectorOne(int symbolSet) {
        return switch (symbolSet) {
            case 0x10 -> new int[] {0x00, 0x25, 0x77};
            case 0x15 -> new int[] {0x00, 0x13};
            case 0x40 -> new int[] {0x00, 0x17};
            default -> throw new IllegalArgumentException("unsupported symbol set");
        };
    }

    private static int[] sectorTwo(int symbolSet) {
        return switch (symbolSet) {
            case 0x10 -> new int[] {0x00, 0x02};
            case 0x15 -> new int[] {0x00, 0x06};
            case 0x40 -> new int[] {0x00, 0x04};
            default -> throw new IllegalArgumentException("unsupported symbol set");
        };
    }
}
