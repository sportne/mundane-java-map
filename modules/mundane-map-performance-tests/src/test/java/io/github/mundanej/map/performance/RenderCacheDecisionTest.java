package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class RenderCacheDecisionTest {
    @Test
    void checkedRatiosPassEqualityAndRejectOneBeyond() {
        assertTrue(RenderCacheDecision.atMost(90, 100, 9, 10));
        assertFalse(RenderCacheDecision.atMost(91, 100, 9, 10));
        assertTrue(RenderCacheDecision.atLeast(80, 100, 4, 5));
        assertFalse(RenderCacheDecision.atLeast(79, 100, 4, 5));
        assertTrue(RenderCacheDecision.reductionAtLeast(10, 2, 4, 5));
        assertFalse(RenderCacheDecision.reductionAtLeast(10, 3, 4, 5));
        assertFalse(RenderCacheDecision.reductionAtLeast(0, 0, 4, 5));
    }

    @Test
    void bigIntegerCrossProductsDoNotOverflow() {
        assertTrue(RenderCacheDecision.atMost(Long.MAX_VALUE, Long.MAX_VALUE, 1, 1));
        assertTrue(RenderCacheDecision.atLeast(Long.MAX_VALUE, Long.MAX_VALUE, 1, 1));
    }

    @Test
    void invalidOrZeroDenominatorsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> RenderCacheDecision.atMost(1, 0, 1, 1));
        assertThrows(
                IllegalArgumentException.class, () -> RenderCacheDecision.atLeast(-1, 1, 1, 1));
    }

    @Test
    void completeCanonicalEvidenceRendersEqualityAsRetained() {
        List<EvidenceSample> samples = passingSamples();
        List<RenderCacheDecision> decisions =
                RenderCacheDecision.evaluate(configuration(), samples);
        assertEquals("RETAINED", decisions.get(0).decision());
        assertTrue(decisions.get(0).checks().values().stream().allMatch(Boolean::booleanValue));
    }

    @Test
    void oneWarmRegressionRejectsTheVectorCandidate() {
        List<EvidenceSample> samples = new ArrayList<>(passingSamples());
        replace(
                samples,
                sample(
                        "symbol-heavy-render-template-cache-warm",
                        96,
                        3,
                        vectorCounters(100, 99, 1)));
        List<RenderCacheDecision> decisions =
                RenderCacheDecision.evaluate(configuration(), samples);
        assertEquals("REJECTED", decisions.get(0).decision());
    }

    @Test
    void missingBaselineBuildsRejectsTheVectorCandidate() {
        List<EvidenceSample> samples = new ArrayList<>(passingSamples());
        replace(samples, sample("symbol-heavy-render", 100, 3, Map.of()));
        List<RenderCacheDecision> decisions =
                RenderCacheDecision.evaluate(configuration(), samples);
        assertEquals("REJECTED", decisions.get(0).decision());
    }

    private static EvidenceConfiguration configuration() {
        return new EvidenceConfiguration(
                EvidenceConfiguration.Profile.BASELINE,
                EvidenceConfiguration.SEED,
                5,
                20,
                Optional.empty(),
                Optional.of("0123456"),
                false);
    }

    private static List<EvidenceSample> passingSamples() {
        List<EvidenceSample> result = new ArrayList<>();
        result.add(sample("symbol-heavy-render", 100, 3, baselineBuilds("vectorTemplate", 100)));
        result.add(
                sample(
                        "symbol-heavy-render-template-cache-cold",
                        102,
                        3,
                        vectorCounters(100, 0, 100)));
        result.add(
                sample(
                        "symbol-heavy-render-template-cache-warm",
                        95,
                        3,
                        vectorCounters(100, 99, 1)));
        return List.copyOf(result);
    }

    private static Map<String, Long> vectorCounters(long requests, long hits, long builds) {
        return candidateCounters("vectorTemplate", requests, hits, builds, 3_933);
    }

    private static Map<String, Long> candidateCounters(
            String prefix, long requests, long hits, long builds, long peakBytes) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        result.put(prefix + "CacheRequests", requests);
        result.put(prefix + "CacheHits", hits);
        result.put(prefix + "CacheMisses", requests - hits);
        result.put(prefix + "Builds", builds);
        result.put(prefix + "CacheAdmissions", builds);
        result.put(prefix + "CacheEvictions", 0L);
        result.put(prefix + "CacheBypasses", 0L);
        result.put(prefix + "BuildUnits", builds);
        result.put(prefix + "CacheCurrentEntries", builds);
        result.put(prefix + "CacheCurrentLogicalBytes", peakBytes);
        result.put(prefix + "CachePeakEntries", builds);
        result.put(prefix + "CachePeakLogicalBytes", peakBytes);
        return result;
    }

    private static Map<String, Long> baselineBuilds(String prefix, long builds) {
        return Map.of(prefix + "Builds", builds);
    }

    private static EvidenceSample sample(
            String id, long median, long digest, Map<String, Long> counters) {
        return new EvidenceSample(
                id, List.of(median), median, median, 1, new EvidenceObservation(digest, counters));
    }

    private static void replace(List<EvidenceSample> samples, EvidenceSample replacement) {
        for (int index = 0; index < samples.size(); index++) {
            if (samples.get(index).scenarioId().equals(replacement.scenarioId())) {
                samples.set(index, replacement);
                return;
            }
        }
        throw new AssertionError("Missing sample to replace");
    }
}
