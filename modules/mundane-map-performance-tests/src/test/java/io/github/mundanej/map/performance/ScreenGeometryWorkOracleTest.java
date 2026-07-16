package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class ScreenGeometryWorkOracleTest {
    @TempDir Path temporary;

    @Test
    void smokeFrozenWorkCountersMatchTheIndependentReference() {
        verifyReference(EvidenceConfiguration.Profile.SMOKE);
    }

    @Test
    void baselineFrozenWorkCountersMatchTheIndependentReference() {
        verifyReference(EvidenceConfiguration.Profile.BASELINE);
    }

    @Test
    void renderCacheFrozenFactsMatchIndependentSmokeAndBaselineReferences() {
        for (EvidenceConfiguration.Profile profile : EvidenceConfiguration.Profile.values()) {
            LinkedHashMap<String, Map<String, Long>> frozen = new LinkedHashMap<>();
            for (String id : RenderCacheWorkOracleV1.ids()) {
                frozen.put(id, RenderCacheWorkOracleV1.expected(profile, id));
            }
            assertEquals(
                    frozen, IndependentScreenGeometryWorkOracle.deriveCacheCandidates(profile));
        }
    }

    @Test
    void renderCacheSmokeProductionCaptureMatchesEveryFrozenCounter() throws Exception {
        for (String id : RenderCacheWorkOracleV1.ids()) {
            var scenarios =
                    ScenarioRegistry.create(
                            EvidenceConfiguration.Profile.SMOKE,
                            temporary.resolve("render-cache").resolve(id),
                            Optional.of(id));
            EvidenceScenario scenario = scenarios.getFirst();
            try {
                scenario.setupScenario();
                scenario.prepareSample();
                runTimed(scenario);
                EvidenceObservation actual = scenario.observeSample();
                assertEquals(
                        RenderCacheWorkOracleV1.expected(EvidenceConfiguration.Profile.SMOKE, id),
                        actual.counters(),
                        id);
                scenario.oracle().verify(actual);
            } finally {
                scenario.close();
            }
        }
    }

    @Test
    void everyRenderCacheCounterDimensionAndRowOrderRejectNegativeControls() {
        for (EvidenceConfiguration.Profile profile : EvidenceConfiguration.Profile.values()) {
            for (String id : RenderCacheWorkOracleV1.ids()) {
                Map<String, Long> expected = RenderCacheWorkOracleV1.expected(profile, id);
                EvidenceObservation frozen = new EvidenceObservation(1L, expected);
                for (String dimension : expected.keySet()) {
                    LinkedHashMap<String, Long> changed = new LinkedHashMap<>(expected);
                    changed.compute(dimension, (ignored, value) -> Math.addExact(value, 1L));
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    ScenarioOracleV1.exact(frozen)
                                            .verify(new EvidenceObservation(1L, changed)),
                            profile + "/" + id + "/" + dimension);
                }
            }
        }
        List<String> rows =
                ScenarioRegistry.ids().stream()
                        .filter(RenderCacheWorkOracleV1.ids()::contains)
                        .toList();
        RenderCacheWorkOracleV1.verifyRowOrder(rows);
        ArrayList<String> changed = new ArrayList<>(rows);
        changed.add(changed.removeFirst());
        assertThrows(
                IllegalStateException.class, () -> RenderCacheWorkOracleV1.verifyRowOrder(changed));
    }

    @Test
    void coldClearingAndWarmPreseedRemainOperationLocalWithZeroOrMultipleWarmups()
            throws Exception {
        for (int warmups : List.of(0, 2)) {
            for (String id :
                    List.of(
                            "symbol-heavy-render-template-cache-cold",
                            "symbol-heavy-render-template-cache-warm")) {
                EvidenceConfiguration configuration =
                        new EvidenceConfiguration(
                                EvidenceConfiguration.Profile.SMOKE,
                                EvidenceConfiguration.SEED,
                                warmups,
                                2,
                                Optional.of(id),
                                Optional.empty(),
                                true);
                var scenarios =
                        ScenarioRegistry.create(
                                EvidenceConfiguration.Profile.SMOKE,
                                temporary.resolve("warmups-" + warmups).resolve(id),
                                Optional.of(id));
                EvidenceReport report = new EvidenceRunner().run(configuration, scenarios);
                assertTrue(new String(report.json(), StandardCharsets.UTF_8).contains(id));
            }
        }
    }

    @Test
    void smokeProductionCaptureMatchesTheFrozenWorkCounters() throws Exception {
        verifyProduction(EvidenceConfiguration.Profile.SMOKE);
    }

    @Test
    @EnabledIfSystemProperty(named = "performanceBaselineOracle", matches = "true")
    void baselineProductionCaptureMatchesTheFrozenWorkCounters() throws Exception {
        verifyProduction(EvidenceConfiguration.Profile.BASELINE);
    }

    @Test
    void everyCounterDimensionAndModeRejectsNegativeControls() {
        for (EvidenceConfiguration.Profile profile : EvidenceConfiguration.Profile.values()) {
            for (String id : ScreenGeometryWorkOracleV1.ids()) {
                Map<String, Long> expected = ScreenGeometryWorkOracleV1.expected(profile, id);
                EvidenceObservation frozen = new EvidenceObservation(1L, expected);
                for (String dimension : expected.keySet()) {
                    LinkedHashMap<String, Long> changed = new LinkedHashMap<>(expected);
                    changed.compute(dimension, (ignored, value) -> Math.addExact(value, 1L));
                    EvidenceObservation negativeControl = new EvidenceObservation(1L, changed);
                    assertThrows(
                            IllegalStateException.class,
                            () -> ScenarioOracleV1.exact(frozen).verify(negativeControl),
                            profile + "/" + id + "/" + dimension);
                }
                assertThrows(
                        UnsupportedOperationException.class,
                        () -> expected.put("projectedCoordinates", 0L));
                String expectedMode = ScreenGeometryWorkOracleV1.vectorPathState(id);
                ScreenGeometryWorkOracleV1.verifyVectorPathState(id, expectedMode);
                String changedMode =
                        expectedMode.equals("DISABLED") ? "LEVEL1_OPERATION_LOCAL" : "DISABLED";
                assertNotEquals(expectedMode, changedMode);
                assertThrows(
                        IllegalStateException.class,
                        () -> ScreenGeometryWorkOracleV1.verifyVectorPathState(id, changedMode));
            }
        }
    }

    @Test
    void rowMappingAndSemanticAliasesAreImmutable() {
        List<String> actualRows =
                ScenarioRegistry.ids().stream()
                        .filter(ScreenGeometryWorkOracleV1.ids()::contains)
                        .toList();
        ScreenGeometryWorkOracleV1.verifyRowOrder(actualRows);
        List<String> rotated = new ArrayList<>(actualRows);
        rotated.add(rotated.removeFirst());
        assertThrows(
                IllegalStateException.class,
                () -> ScreenGeometryWorkOracleV1.verifyRowOrder(rotated));

        Map<String, String> aliases = ScenarioOracleV1.frozenDigests();
        assertEquals(
                aliases.get("SMOKE/small-vector-render-v1"),
                aliases.get("SMOKE/small-vector-render-optimized"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> aliases.put("SMOKE/small-vector-render-optimized", "changed"));
    }

    @Test
    void independentReferenceHasNoProductionPaintOrScenarioCounterBackchannel() throws Exception {
        Path source =
                Path.of(
                        "modules/mundane-map-performance-tests/src/test/java/"
                                + "io/github/mundanej/map/performance/"
                                + "IndependentScreenGeometryWorkOracle.java");
        if (!Files.exists(source)) {
            source =
                    Path.of(
                            "src/test/java/io/github/mundanej/map/performance/"
                                    + "IndependentScreenGeometryWorkOracle.java");
        }
        String text = Files.readString(source, StandardCharsets.UTF_8);
        for (String forbidden :
                List.of(
                        "MapView",
                        "ScreenGeometryEvidenceSupport",
                        "ScenarioRegistry",
                        "sourceCounters",
                        "paintWithScreenGeometryResult")) {
            assertFalse(
                    Pattern.compile("\\b" + Pattern.quote(forbidden) + "\\b").matcher(text).find(),
                    forbidden);
        }
    }

    private static void verifyReference(EvidenceConfiguration.Profile profile) {
        assertEquals(frozen(profile), IndependentScreenGeometryWorkOracle.derive(profile));
    }

    private void verifyProduction(EvidenceConfiguration.Profile profile) throws Exception {
        LinkedHashMap<String, Map<String, Long>> actualByScenario = new LinkedHashMap<>();
        for (String id : ScreenGeometryWorkOracleV1.ids()) {
            var scenarios =
                    ScenarioRegistry.create(
                            profile,
                            temporary.resolve(profile.name()).resolve(id),
                            Optional.of(id));
            EvidenceScenario scenario = scenarios.getFirst();
            try {
                scenario.setupScenario();
                scenario.prepareSample();
                runTimed(scenario);
                EvidenceObservation actual = scenario.observeSample();
                actualByScenario.put(id, actual.counters());
                assertEquals(
                        ScreenGeometryWorkOracleV1.vectorPathState(id),
                        scenario.vectorPathState(),
                        id);
            } finally {
                scenario.close();
            }
        }
        assertEquals(frozen(profile), actualByScenario);
    }

    private static Map<String, Map<String, Long>> frozen(EvidenceConfiguration.Profile profile) {
        LinkedHashMap<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (String id : ScreenGeometryWorkOracleV1.ids()) {
            result.put(id, ScreenGeometryWorkOracleV1.expected(profile, id));
        }
        return java.util.Collections.unmodifiableMap(result);
    }

    private static void runTimed(EvidenceScenario scenario) throws Exception {
        if (scenario.runsOnEdt()) {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            scenario.runTimedBatch();
                        } catch (Exception failure) {
                            throw new IllegalStateException(failure);
                        }
                    });
        } else {
            scenario.runTimedBatch();
        }
    }
}
