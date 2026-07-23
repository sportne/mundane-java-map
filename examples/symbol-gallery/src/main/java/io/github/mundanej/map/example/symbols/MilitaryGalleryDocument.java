package io.github.mundanej.map.example.symbols;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalog;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolCatalogEntry;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolId;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbolPalette;
import io.github.mundanej.map.symbology.milstd2525.MilitarySymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Project-authored reference matrix for the bounded MIL-STD-2525 gallery profile. */
record MilitaryGalleryDocument(List<GallerySection> sections) {
    private static final MarkerPlacement PLACEMENT = MarkerPlacement.centeredScreen(52);

    MilitaryGalleryDocument {
        sections = List.copyOf(sections);
    }

    static MilitaryGalleryDocument create() {
        return new MilitaryGalleryDocument(
                List.of(
                        entities(),
                        identitiesAndStatuses(),
                        modifiersAndFallbacks(),
                        darkPalette()));
    }

    private static GallerySection entities() {
        ArrayList<GalleryCase> cases = new ArrayList<>();
        int index = 0;
        for (MilitarySymbolCatalogEntry entry : MilitarySymbolCatalog.entries()) {
            String sidc = sidc(3, entry.symbolSet(), 0, entry.entityCode(), 0, 0);
            cases.add(
                    pointCase(
                            "mil-entity-%02x-%06x".formatted(entry.symbolSet(), entry.entityCode()),
                            "%s — %s".formatted(symbolSetName(entry.symbolSet()), entry.name()),
                            index++,
                            sidc,
                            MilitarySymbolPalette.lightBackground()));
        }
        return new GallerySection("mil-entities", "2525 entities", cases);
    }

    private static GallerySection identitiesAndStatuses() {
        ArrayList<GalleryCase> cases = new ArrayList<>();
        String[] identities = {
            "Pending", "Unknown", "Assumed friend", "Friend", "Neutral", "Suspect", "Hostile"
        };
        int index = 0;
        for (int identity = 0; identity <= 6; identity++) {
            for (int status = 0; status <= 1; status++) {
                String sidc = sidc(identity, 0x10, status, 0x121100, 0, 0);
                cases.add(
                        pointCase(
                                "mil-identity-" + identity + "-status-" + status,
                                identities[identity] + (status == 0 ? " — present" : " — planned"),
                                index++,
                                sidc,
                                MilitarySymbolPalette.lightBackground()));
            }
        }
        return new GallerySection("mil-identities", "2525 identity/status", cases);
    }

    private static GallerySection modifiersAndFallbacks() {
        List<Variation> variations =
                List.of(
                        new Variation(
                                "unit-sector-one-25",
                                "Land Unit modifier 25",
                                0x10,
                                0x121100,
                                0x25,
                                0),
                        new Variation(
                                "unit-sector-one-77",
                                "Land Unit modifier 77",
                                0x10,
                                0x121100,
                                0x77,
                                0),
                        new Variation(
                                "unit-sector-two-02",
                                "Land Unit modifier 02",
                                0x10,
                                0x121100,
                                0,
                                0x02),
                        new Variation(
                                "equipment-sector-one-13",
                                "Equipment modifier 13",
                                0x15,
                                0x120200,
                                0x13,
                                0),
                        new Variation(
                                "equipment-sector-two-06",
                                "Equipment modifier 06",
                                0x15,
                                0x120200,
                                0,
                                0x06),
                        new Variation(
                                "activity-sector-one-17",
                                "Activity modifier 17",
                                0x40,
                                0x140000,
                                0x17,
                                0),
                        new Variation(
                                "activity-sector-two-04",
                                "Activity modifier 04",
                                0x40,
                                0x140000,
                                0,
                                0x04));
        ArrayList<GalleryCase> cases = new ArrayList<>();
        int index = 0;
        for (Variation variation : variations) {
            cases.add(
                    pointCase(
                            "mil-" + variation.id(),
                            variation.title(),
                            index++,
                            sidc(
                                    3,
                                    variation.symbolSet(),
                                    0,
                                    variation.entity(),
                                    variation.sectorOne(),
                                    variation.sectorTwo()),
                            MilitarySymbolPalette.lightBackground()));
        }
        String light = sidc(6, 0x10, 1, 0x130300, 0x25, 0x02);
        cases.add(
                pointCase(
                        "mil-palette-light",
                        "Light-background palette",
                        index++,
                        light,
                        MilitarySymbolPalette.lightBackground()));
        String degradedEntity = sidc(3, 0x10, 0, 0x999999, 0, 0);
        cases.add(
                resolvedCase(
                        "mil-degraded-entity",
                        "Degraded entity — frame only (MIL2525_ENTITY_UNSUPPORTED)",
                        index++,
                        degradedEntity,
                        MilitarySymbols.resolveDegraded(
                                        MilitarySymbolId.parse(degradedEntity),
                                        PLACEMENT,
                                        MilitarySymbolPalette.lightBackground())
                                .symbol()));
        String degradedModifier = sidc(3, 0x10, 0, 0x121100, 0xff, 0);
        cases.add(
                resolvedCase(
                        "mil-degraded-modifier",
                        "Degraded modifier — omitted (MIL2525_MODIFIER_UNSUPPORTED)",
                        index++,
                        degradedModifier,
                        MilitarySymbols.resolveDegraded(
                                        MilitarySymbolId.parse(degradedModifier),
                                        PLACEMENT,
                                        MilitarySymbolPalette.lightBackground())
                                .symbol()));
        String unsupported = sidc(3, 0x10, 0, 0x121100, 0, 0);
        unsupported = unsupported.substring(0, 2) + "F" + unsupported.substring(3);
        cases.add(diagnosticCase(index, unsupported));
        return new GallerySection("mil-variations", "2525 modifiers/fallbacks", cases);
    }

    private static GallerySection darkPalette() {
        String sidc = sidc(6, 0x10, 1, 0x130300, 0x25, 0x02);
        return new GallerySection(
                "mil-dark-palette",
                "2525 dark palette",
                List.of(
                        pointCase(
                                "mil-palette-dark",
                                "Dark-background palette",
                                0,
                                sidc,
                                MilitarySymbolPalette.darkBackground())));
    }

    private static GalleryCase resolvedCase(
            String id, String title, int index, String sidc, Symbol symbol) {
        int column = index % 5;
        int row = index / 5;
        Feature feature =
                new Feature(
                        id,
                        "",
                        new PointGeometry(new Coordinate(-140 + column * 70, 62 - row * 46)),
                        Map.of("sidc", sidc, "reference", title, "degraded", true),
                        symbol);
        return new GalleryCase(id, title, List.of(feature), GalleryCoverage.none());
    }

    private static GalleryCase diagnosticCase(int index, String sidc) {
        String title = "Unsupported — no military symbol (MIL2525_CONTEXT_UNSUPPORTED)";
        Symbol diagnostic =
                BuiltInMarkers.filledScreen(BuiltInMarker.X, Rgba.rgb(190, 35, 35), 34, 1);
        int column = index % 5;
        int row = index / 5;
        Feature feature =
                new Feature(
                        "mil-unsupported",
                        "",
                        new PointGeometry(new Coordinate(-140 + column * 70, 62 - row * 46)),
                        Map.of(
                                "sidc",
                                sidc,
                                "reference",
                                title,
                                "diagnostic",
                                "MIL2525_CONTEXT_UNSUPPORTED",
                                "militarySymbolOmitted",
                                true),
                        diagnostic);
        return new GalleryCase("mil-unsupported", title, List.of(feature), GalleryCoverage.none());
    }

    private static GalleryCase pointCase(
            String id, String title, int index, String sidc, MilitarySymbolPalette palette) {
        int column = index % 5;
        int row = index / 5;
        Coordinate coordinate = new Coordinate(-140 + column * 70, 62 - row * 46);
        Symbol symbol =
                MilitarySymbols.resolveStrict(MilitarySymbolId.parse(sidc), PLACEMENT, palette);
        Feature feature =
                new Feature(
                        id,
                        "",
                        new PointGeometry(coordinate),
                        Map.of("sidc", sidc, "reference", title),
                        symbol);
        return new GalleryCase(id, title, List.of(feature), GalleryCoverage.none());
    }

    private static String sidc(
            int identity, int symbolSet, int status, int entity, int sectorOne, int sectorTwo) {
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
                entity,
                sectorOne,
                sectorTwo,
                frame);
    }

    private static String symbolSetName(int symbolSet) {
        return switch (symbolSet) {
            case 0x10 -> "Land Unit";
            case 0x15 -> "Land Equipment";
            case 0x40 -> "Activities";
            default -> throw new IllegalArgumentException("unsupported symbol set");
        };
    }

    private record Variation(
            String id, String title, int symbolSet, int entity, int sectorOne, int sectorTwo) {}
}
