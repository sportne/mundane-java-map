package io.github.mundanej.map.performance;

import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.HorizontalWrapPlan;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Descriptive disabled-versus-enabled horizontal-wrap planning workloads. */
final class WorldWrapEvidenceScenarios {
    private static final double[] MINIMUMS = {
        -19_000_000.0, 19_000_000.0, 39_000_000.0, -41_000_000.0
    };
    private static final double[] MAXIMUMS = {
        -17_000_000.0, 21_000_000.0, 43_000_000.0, -37_000_000.0
    };

    private WorldWrapEvidenceScenarios() {}

    static void append(
            List<EvidenceScenario> target,
            EvidenceConfiguration.Profile profile,
            Optional<String> selected) {
        append(target, profile, selected, false);
        append(target, profile, selected, true);
    }

    private static void append(
            List<EvidenceScenario> target,
            EvidenceConfiguration.Profile profile,
            Optional<String> selected,
            boolean enabled) {
        String id = "world-wrap-plan-" + (enabled ? "wrapped" : "disabled");
        if (selected.isEmpty() || selected.orElseThrow().equals(id)) {
            target.add(new PlanningScenario(profile, id, enabled));
        }
    }

    private static final class PlanningScenario implements EvidenceScenario {
        private final EvidenceConfiguration.Profile profile;
        private final String id;
        private final boolean enabled;
        private final int operations;
        private final Map<String, Long> counters;
        private final HorizontalWrap wrap = HorizontalWrap.webMercator();
        private long checksum;

        private PlanningScenario(
                EvidenceConfiguration.Profile profile, String id, boolean enabled) {
            this.profile = profile;
            this.id = id;
            this.enabled = enabled;
            operations = profile == EvidenceConfiguration.Profile.BASELINE ? 65_536 : 2_048;
            counters = counters(operations, enabled);
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public String nextExperiment() {
            return "compare the paired disabled and wrapped rows; no wall-clock threshold";
        }

        @Override
        public long batchOperations() {
            return operations;
        }

        @Override
        public String workUnit() {
            return "viewIntervals";
        }

        @Override
        public String sourceCacheState() {
            return "NOT_APPLICABLE";
        }

        @Override
        public void prepareSample() {
            checksum = 0L;
        }

        @Override
        public void runTimedBatch() {
            long result = 0L;
            for (int operation = 0; operation < operations; operation++) {
                int index = operation & 3;
                double minimum = MINIMUMS[index];
                double maximum = MAXIMUMS[index];
                if (enabled) {
                    HorizontalWrapPlan plan = wrap.plan(minimum, maximum, 10_000.0);
                    result +=
                            plan.canonicalIntervals().size()
                                    + plan.visibleCopyCount()
                                    + (plan.fullWorld() ? 1L : 0L);
                } else {
                    result += minimum < maximum ? 1L : 0L;
                }
            }
            checksum = result;
        }

        @Override
        public EvidenceObservation observeSample() {
            if (checksum <= 0L) {
                throw new IllegalStateException("World-wrap planning work changed");
            }
            return ObservationDigests.observation(
                    profile, id, counters, digest -> digest.add(checksum));
        }

        @Override
        public ScenarioOracle oracle() {
            return ScenarioOracleV1.exact(ScenarioOracleV1.expected(profile, id, counters));
        }

        private static Map<String, Long> counters(int operations, boolean enabled) {
            LinkedHashMap<String, Long> result = new LinkedHashMap<>();
            result.put("viewIntervals", (long) operations);
            result.put("wrapEnabled", enabled ? 1L : 0L);
            result.put("wallClockGate", 0L);
            return Collections.unmodifiableMap(result);
        }
    }
}
