package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

class IndependentProfileOracleTest {
    @TempDir Path temporary;

    @Test
    void smokeDigestsComeFromIndependentProfileFixturesAndPublicPaths() throws Exception {
        verify(EvidenceConfiguration.Profile.SMOKE);
    }

    @Test
    @EnabledIfSystemProperty(named = "performanceBaselineOracle", matches = "true")
    void baselineDigestsComeFromIndependentProfileFixturesAndPublicPaths() throws Exception {
        verify(EvidenceConfiguration.Profile.BASELINE);
    }

    private void verify(EvidenceConfiguration.Profile profile) throws Exception {
        Map<String, String> independentlyDerived =
                IndependentProfileOracle.derive(
                        profile,
                        temporary.resolve(profile.name().toLowerCase(java.util.Locale.ROOT)));
        assertEquals(frozen(profile), independentlyDerived, profile.name());
    }

    private static Map<String, String> frozen(EvidenceConfiguration.Profile profile) {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        for (String scenario : ScenarioRegistry.ids()) {
            result.put(
                    scenario,
                    ScenarioOracleV1.frozenDigests().get(profile.name() + '/' + scenario));
        }
        return Map.copyOf(result);
    }
}
