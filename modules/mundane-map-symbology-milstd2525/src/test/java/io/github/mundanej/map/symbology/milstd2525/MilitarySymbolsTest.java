package io.github.mundanej.map.symbology.milstd2525;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MilitarySymbolsTest {
    private static final MarkerPlacement PLACEMENT = MarkerPlacement.centeredScreen(48);

    @Test
    void everyIdentityAndStatusResolvesTheInfantrySliceWithExpectedLayers() {
        Set<Object> framePaths = new HashSet<>();
        for (int identity = 0; identity <= 6; identity++) {
            for (int status = 0; status <= 1; status++) {
                CompositeSymbol symbol =
                        composite(
                                MilitarySymbols.resolveStrict(
                                        id(identity, status, "121100"),
                                        PLACEMENT,
                                        MilitarySymbolPalette.lightBackground()));
                boolean segmented = status == 1 || identity == 0 || identity == 2 || identity == 5;
                assertEquals(segmented ? 3 : 2, symbol.children().size());
                VectorMarkerSymbol frame = marker(symbol, 0);
                assertEquals(PLACEMENT, frame.placement());
                assertEquals(expectedFill(identity), frame.fill());
                assertSame(
                        MilitarySymbolPaths.INFANTRY,
                        marker(symbol, symbol.children().size() - 1).path());
                framePaths.add(frame.path());
            }
        }
        assertEquals(4, framePaths.size());
    }

    @Test
    void palettesPlacementRotationAndOpacityRemainOrdinarySymbolProperties() {
        MarkerPlacement placement =
                new MarkerPlacement(
                        new SymbolSize(52, 38, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.SOUTH_EAST,
                        3,
                        -4,
                        37,
                        SymbolRotationMode.MAP_RELATIVE);
        CompositeSymbol light =
                composite(
                        MilitarySymbols.resolveStrict(
                                id(3, 0, "121100"),
                                placement,
                                MilitarySymbolPalette.lightBackground(),
                                0.45));
        CompositeSymbol dark =
                composite(
                        MilitarySymbols.resolveStrict(
                                id(3, 0, "121100"),
                                placement,
                                MilitarySymbolPalette.darkBackground()));

        assertEquals(0.45, light.opacity());
        assertEquals(placement, marker(light, 0).placement());
        assertEquals(Rgba.rgb(0, 107, 140), marker(light, 0).fill());
        assertEquals(Rgba.rgb(128, 225, 255), marker(dark, 0).fill());
        assertEquals(Rgba.rgb(0, 0, 0), marker(light, 1).stroke().orElseThrow().color());
        assertEquals(Rgba.rgb(255, 255, 255), marker(dark, 1).stroke().orElseThrow().color());
    }

    @Test
    void degradedEntityReturnsFrameAndStrictResolutionRetainsDiagnostic() {
        MilitarySymbolId unsupported = id(6, 0, "FFFFFF");

        MilitarySymbolException failure =
                assertThrows(
                        MilitarySymbolException.class,
                        () ->
                                MilitarySymbols.resolveStrict(
                                        unsupported,
                                        PLACEMENT,
                                        MilitarySymbolPalette.lightBackground()));
        assertEquals("MIL2525_ENTITY_UNSUPPORTED", failure.problem().code());

        MilitarySymbolResolution degraded =
                MilitarySymbols.resolveDegraded(
                        unsupported, PLACEMENT, MilitarySymbolPalette.lightBackground());
        assertEquals("MIL2525_ENTITY_UNSUPPORTED", degraded.problem().orElseThrow().code());
        assertEquals(1, composite(degraded.symbol()).children().size());
    }

    @Test
    void catalogEntriesAndModifiersNotDrawnByThisSliceReportTheRenderLimit() {
        MilitarySymbolId armor = id(3, 0, "120500");
        MilitarySymbolException entityFailure =
                assertThrows(
                        MilitarySymbolException.class,
                        () ->
                                MilitarySymbols.resolveStrict(
                                        armor, PLACEMENT, MilitarySymbolPalette.lightBackground()));
        assertEquals("MIL2525_RENDER_LIMIT", entityFailure.problem().code());
        assertEquals("entity", entityFailure.problem().field());
        assertEquals(
                "MIL2525_RENDER_LIMIT",
                MilitarySymbols.resolveDegraded(
                                armor, PLACEMENT, MilitarySymbolPalette.lightBackground())
                        .problem()
                        .orElseThrow()
                        .code());

        MilitarySymbolId modified =
                MilitarySymbolId.parse(
                        replace(MilitarySymbolFixtures.FRIEND_INFANTRY_PRESENT, 17, 18, "25"));
        MilitarySymbolException modifierFailure =
                assertThrows(
                        MilitarySymbolException.class,
                        () ->
                                MilitarySymbols.resolveStrict(
                                        modified,
                                        PLACEMENT,
                                        MilitarySymbolPalette.lightBackground()));
        assertEquals("MIL2525_RENDER_LIMIT", modifierFailure.problem().code());
        assertEquals("sectorOneModifier", modifierFailure.problem().field());
    }

    @Test
    void hardFailuresAndInvalidOpacityNeverRender() {
        MilitarySymbolId hardFailure =
                MilitarySymbolId.parse(replace(id(3, 0, "121100").canonical(), 3, 3, "1"));
        assertThrows(
                MilitarySymbolException.class,
                () ->
                        MilitarySymbols.resolveDegraded(
                                hardFailure, PLACEMENT, MilitarySymbolPalette.lightBackground()));
        for (double opacity : new double[] {-0.1, 1.1, Double.NaN}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            MilitarySymbols.resolveStrict(
                                    id(3, 0, "121100"),
                                    PLACEMENT,
                                    MilitarySymbolPalette.lightBackground(),
                                    opacity));
        }
    }

    @Test
    void plannedAndPresentFramesAreStructurallyDifferent() {
        Symbol present =
                MilitarySymbols.resolveStrict(
                        id(3, 0, "121100"), PLACEMENT, MilitarySymbolPalette.lightBackground());
        Symbol planned =
                MilitarySymbols.resolveStrict(
                        id(3, 1, "121100"), PLACEMENT, MilitarySymbolPalette.lightBackground());
        assertNotEquals(present, planned);
        assertTrue(marker(composite(planned), 1).fill().alpha() == 0);
    }

    private static Rgba expectedFill(int identity) {
        MilitarySymbolPalette palette = MilitarySymbolPalette.lightBackground();
        return switch (identity) {
            case 0, 1 -> palette.unknown();
            case 2, 3 -> palette.friend();
            case 4 -> palette.neutral();
            case 5 -> palette.suspect();
            case 6 -> palette.hostile();
            default -> throw new AssertionError();
        };
    }

    private static CompositeSymbol composite(Symbol symbol) {
        return (CompositeSymbol) symbol;
    }

    private static VectorMarkerSymbol marker(CompositeSymbol symbol, int index) {
        return (VectorMarkerSymbol) symbol.children().get(index);
    }

    private static MilitarySymbolId id(int identity, int status, String entity) {
        String value = MilitarySymbolFixtures.firstInfantry(identity, status);
        value = replace(value, 11, 16, entity);
        return MilitarySymbolId.parse(value);
    }

    private static String replace(String value, int start, int end, String replacement) {
        return value.substring(0, start - 1)
                + replacement.toUpperCase(Locale.ROOT)
                + value.substring(end);
    }
}
