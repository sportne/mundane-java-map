package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoTiffEvidenceScenariosTest {
    @TempDir Path temporary;

    @Test
    void scenariosProduceDeterministicSemanticEvidenceForBothProfiles() throws Exception {
        Map<String, String> actual = new LinkedHashMap<>();
        for (EvidenceConfiguration.Profile profile : EvidenceConfiguration.Profile.values()) {
            List<EvidenceScenario> scenarios = new ArrayList<>();
            GeoTiffEvidenceScenarios.append(
                    scenarios, profile, Optional.empty(), temporary.resolve(profile.name()));
            assertEquals(2, scenarios.size());
            for (EvidenceScenario scenario : scenarios) {
                EvidenceObservation observation = executeOnce(scenario);
                scenario.oracle().verify(observation);
                actual.put(
                        profile.name() + '/' + scenario.id(),
                        EvidenceReport.hex(observation.digest()));
                assertTrue(observation.counters().get("encodedBytes") > 0);
            }
        }
        assertEquals(
                Map.of(
                        "BASELINE/geotiff-raster-window-read", "b84b9cf3c4a6d03c",
                        "BASELINE/geotiff-eager-elevation-open", "88d23b44189c4782",
                        "SMOKE/geotiff-raster-window-read", "682d35ff80a068af",
                        "SMOKE/geotiff-eager-elevation-open", "842bd41dec68d17c"),
                actual);
    }

    private static EvidenceObservation executeOnce(EvidenceScenario scenario) throws Exception {
        try (scenario) {
            scenario.setupScenario();
            scenario.prepareSample();
            scenario.runTimedBatch();
            EvidenceObservation observation = scenario.observeSample();
            scenario.finishSample();
            scenario.finishScenario();
            return observation;
        }
    }
}
