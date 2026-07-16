package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
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
                scenario.runTimedBatch();
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
}
