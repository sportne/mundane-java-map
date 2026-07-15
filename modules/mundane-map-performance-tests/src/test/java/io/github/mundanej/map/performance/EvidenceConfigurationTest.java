package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Locale;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EvidenceConfigurationTest {
    private Locale locale;
    private TimeZone zone;

    @BeforeEach
    void installCanonicalRuntime() {
        locale = Locale.getDefault();
        zone = TimeZone.getDefault();
        Locale.setDefault(Locale.US);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        System.setProperty("java.awt.headless", "true");
        clearOverrides();
        System.setProperty("performanceProfile", "SMOKE");
    }

    @AfterEach
    void restoreRuntime() {
        clearOverrides();
        Locale.setDefault(locale);
        TimeZone.setDefault(zone);
    }

    @Test
    void defaultsUseProfileAndSeedWithoutInvestigation() {
        EvidenceConfiguration configuration = EvidenceConfiguration.system(ScenarioRegistry.ids());
        assertEquals(EvidenceConfiguration.SEED, configuration.seed());
        assertEquals(1, configuration.warmups());
        assertEquals(2, configuration.measurements());
        assertFalse(configuration.investigation());
    }

    @Test
    void exactOverridesAndRevisionAreAccepted() {
        System.setProperty("performanceScenario", "memory-query-window");
        System.setProperty("performanceWarmups", "0");
        System.setProperty("performanceMeasurements", "100");
        System.setProperty("performanceRevision", "0123456789abcdef");
        EvidenceConfiguration configuration = EvidenceConfiguration.system(ScenarioRegistry.ids());
        assertEquals(0, configuration.warmups());
        assertEquals(100, configuration.measurements());
        assertTrue(configuration.investigation());
        assertEquals("0123456789abcdef", configuration.revision().orElseThrow());
    }

    @Test
    void malformedOverridesAreRejectedWithoutClamping() {
        for (String invalid : new String[] {"-1", "+1", "01", " 1", "101"}) {
            System.setProperty("performanceWarmups", invalid);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> EvidenceConfiguration.system(ScenarioRegistry.ids()));
        }
        System.clearProperty("performanceWarmups");
        System.setProperty("performanceMeasurements", "0");
        assertThrows(
                IllegalArgumentException.class,
                () -> EvidenceConfiguration.system(ScenarioRegistry.ids()));
        System.setProperty("performanceMeasurements", "1");
        System.setProperty("performanceScenario", "missing");
        assertThrows(
                IllegalArgumentException.class,
                () -> EvidenceConfiguration.system(ScenarioRegistry.ids()));
        System.clearProperty("performanceScenario");
        System.setProperty("performanceRevision", "ABCDEF0");
        assertThrows(
                IllegalArgumentException.class,
                () -> EvidenceConfiguration.system(ScenarioRegistry.ids()));
    }

    private static void clearOverrides() {
        System.clearProperty("performanceScenario");
        System.clearProperty("performanceWarmups");
        System.clearProperty("performanceMeasurements");
        System.clearProperty("performanceRevision");
        System.clearProperty("GITHUB_SHA");
        System.clearProperty("performanceProfile");
    }
}
