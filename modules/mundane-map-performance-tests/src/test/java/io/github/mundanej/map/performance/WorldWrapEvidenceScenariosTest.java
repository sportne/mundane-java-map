package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldWrapEvidenceScenariosTest {
    @TempDir Path temporary;

    @Test
    void baselinePublishesPairedDescriptiveRowsWithoutATimingGate() throws Exception {
        EvidenceConfiguration configuration =
                new EvidenceConfiguration(
                        EvidenceConfiguration.Profile.BASELINE,
                        EvidenceConfiguration.SEED,
                        0,
                        1,
                        Optional.empty(),
                        Optional.empty(),
                        false);
        List<EvidenceScenario> scenarios = new java.util.ArrayList<>();
        WorldWrapEvidenceScenarios.append(scenarios, configuration.profile(), Optional.empty());

        EvidenceReport report = new EvidenceRunner().run(configuration, scenarios);
        String markdown = new String(report.markdown(), java.nio.charset.StandardCharsets.UTF_8);

        assertEquals(
                List.of("world-wrap-plan-disabled", "world-wrap-plan-wrapped"),
                scenarios.stream().map(EvidenceScenario::id).toList());
        assertEquals(2, occurrences(markdown, "`wallClockGate=0`"));
    }

    private static int occurrences(String value, String token) {
        return value.split(java.util.regex.Pattern.quote(token), -1).length - 1;
    }
}
