package io.github.mundanej.map.io.maplibre.style;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MapLibreFilterCoverageTest {
    @Test
    void deferredSymbolRetainsDeclarationAndValueSemantics() {
        MapLibreSymbolSpec spec =
                new MapLibreSymbolSpec(
                        new MapLibreSymbolSpec.IconExpression.Literal("marker"),
                        1,
                        2,
                        0.5,
                        SymbolAnchor.CENTER,
                        3,
                        4,
                        SymbolRotationMode.SCREEN_RELATIVE,
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        1);
        MapLibreDeferredSymbol first = new MapLibreDeferredSymbol(spec);
        MapLibreDeferredSymbol equal = new MapLibreDeferredSymbol(spec);
        MapLibreDeferredSymbol different =
                new MapLibreDeferredSymbol(
                        new MapLibreSymbolSpec(
                                new MapLibreSymbolSpec.IconExpression.Literal("other"),
                                1,
                                2,
                                0.5,
                                SymbolAnchor.CENTER,
                                3,
                                4,
                                SymbolRotationMode.SCREEN_RELATIVE,
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                1));

        assertEquals(spec, first.spec());
        assertEquals(1, first.opacity());
        assertEquals(first, equal);
        assertEquals(first.hashCode(), equal.hashCode());
        assertNotEquals(first, different);
        assertNotEquals(first, "marker");
        assertTrue(first.rendererKey().value().contains("deferred-symbol"));
        assertTrue(first.toString().contains("marker"));
    }

    @Test
    void compilerExpandsBranchingFiltersIntoClosedPredicates() {
        MapLibreReadLimits limits = MapLibreReadLimits.defaults();
        MapLibreFilters.CompiledFilter match =
                MapLibreFilters.compile(
                        List.of("match", List.of("get", "kind"), "city", true, "port", false, true),
                        limits,
                        0,
                        CancellationToken.none(),
                        "/match");
        MapLibreFilters.CompiledFilter conditional =
                MapLibreFilters.compile(
                        List.of(
                                "case",
                                List.of("has", "selected"),
                                true,
                                List.of("==", List.of("geometry-type"), "Point"),
                                false,
                                true),
                        limits,
                        match.nodes(),
                        CancellationToken.none(),
                        "/case");
        MapLibreFilters.CompiledFilter step =
                MapLibreFilters.compile(
                        List.of(
                                "step",
                                List.of("get", "rank"),
                                false,
                                BigDecimal.ONE,
                                true,
                                BigDecimal.TEN,
                                List.of("has", "enabled")),
                        limits,
                        match.nodes() + conditional.nodes(),
                        CancellationToken.none(),
                        "/step");

        assertTrue(match.nodes() > 1);
        assertTrue(conditional.nodes() > 1);
        assertTrue(step.nodes() > 1);
        assertFalse(MapLibreFilters.apply(Optional.empty(), match.predicate()).isPresent());
    }

    @Test
    void branchingValidationReportsStableFailureReasons() {
        assertFailure(List.of("match", List.of("get", "kind"), "city", true), "arity");
        assertFailure(List.of("case", true, true), "arity");
        assertFailure(List.of("step", "not-a-property", false, BigDecimal.ONE, true), "stepInput");
        assertFailure(List.of("step", List.of("get", "rank"), false, "one", true), "stepStop");
        assertFailure(
                List.of(
                        "step",
                        List.of("get", "rank"),
                        false,
                        BigDecimal.TEN,
                        true,
                        BigDecimal.ONE,
                        false),
                "stopOrder");
        assertThrows(
                MapLibreReadException.class,
                () ->
                        MapLibreFilters.compileLayerFilter(
                                List.of("case", true, true, false),
                                MapLibreReadLimits.defaults(),
                                0,
                                CancellationToken.none(),
                                "/layer"));
    }

    private static void assertFailure(List<Object> expression, String reason) {
        MapLibreReadException failure =
                assertThrows(
                        MapLibreReadException.class,
                        () ->
                                MapLibreFilters.compile(
                                        expression,
                                        MapLibreReadLimits.defaults(),
                                        0,
                                        CancellationToken.none(),
                                        "/filter"));
        assertEquals(reason, failure.problem().context().get("reason"));
    }
}
