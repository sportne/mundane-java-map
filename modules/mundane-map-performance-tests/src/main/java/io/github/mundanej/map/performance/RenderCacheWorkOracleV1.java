package io.github.mundanej.map.performance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Frozen schema-v1 exact work facts for the retained vector-template comparison rows. */
final class RenderCacheWorkOracleV1 {
    private static final List<String> IDS =
            List.of(
                    "symbol-heavy-render-template-cache-cold",
                    "symbol-heavy-render-template-cache-warm");
    private static final Map<String, Map<String, Long>> FROZEN = frozen();

    private RenderCacheWorkOracleV1() {}

    static List<String> ids() {
        return IDS;
    }

    static Map<String, Long> expectedOrDefault(
            EvidenceConfiguration.Profile profile,
            String scenarioId,
            Map<String, Long> defaultCounters) {
        return FROZEN.getOrDefault(profile.name() + '/' + scenarioId, defaultCounters);
    }

    static Map<String, Long> expected(EvidenceConfiguration.Profile profile, String scenarioId) {
        Map<String, Long> result = FROZEN.get(profile.name() + '/' + scenarioId);
        if (result == null) {
            throw new IllegalArgumentException("No render-cache work oracle for " + scenarioId);
        }
        return result;
    }

    static void verifyRowOrder(List<String> actual) {
        if (!IDS.equals(actual)) {
            throw new IllegalStateException("Render-cache evidence row order changed");
        }
    }

    private static Map<String, Map<String, Long>> frozen() {
        LinkedHashMap<String, Map<String, Long>> result = new LinkedHashMap<>();
        vectorPair(result, EvidenceConfiguration.Profile.SMOKE, 272);
        vectorPair(result, EvidenceConfiguration.Profile.BASELINE, 4_352);
        return Map.copyOf(result);
    }

    private static void vectorPair(
            Map<String, Map<String, Long>> target,
            EvidenceConfiguration.Profile profile,
            long requests) {
        add(
                target,
                profile,
                "symbol-heavy-render-template-cache-cold",
                facts(requests, requests - 9, 9, 9, 9, 0, 434, 9, 4_869, 9, 4_869));
        add(
                target,
                profile,
                "symbol-heavy-render-template-cache-warm",
                facts(requests, requests, 0, 0, 0, 0, 0, 9, 4_869, 9, 4_869));
    }

    private static void add(
            Map<String, Map<String, Long>> target,
            EvidenceConfiguration.Profile profile,
            String id,
            CacheFacts facts) {
        LinkedHashMap<String, Long> counters =
                new LinkedHashMap<>(
                        ScreenGeometryWorkOracleV1.expected(profile, "symbol-heavy-render"));
        String prefix = "vectorTemplate";
        counters.put(prefix + "CacheRequests", facts.requests());
        counters.put(prefix + "CacheHits", facts.hits());
        counters.put(prefix + "CacheMisses", facts.misses());
        counters.put(prefix + "Builds", facts.builds());
        counters.put(prefix + "CacheAdmissions", facts.admissions());
        counters.put(prefix + "CacheEvictions", facts.evictions());
        counters.put(prefix + "CacheBypasses", 0L);
        counters.put(prefix + "BuildUnits", facts.buildUnits());
        counters.put(prefix + "CacheCurrentEntries", facts.currentEntries());
        counters.put(prefix + "CacheCurrentLogicalBytes", facts.currentBytes());
        counters.put(prefix + "CachePeakEntries", facts.peakEntries());
        counters.put(prefix + "CachePeakLogicalBytes", facts.peakBytes());
        target.put(profile.name() + '/' + id, Map.copyOf(counters));
    }

    private static CacheFacts facts(
            long requests,
            long hits,
            long misses,
            long builds,
            long admissions,
            long evictions,
            long buildUnits,
            long currentEntries,
            long currentBytes,
            long peakEntries,
            long peakBytes) {
        return new CacheFacts(
                requests,
                hits,
                misses,
                builds,
                admissions,
                evictions,
                buildUnits,
                currentEntries,
                currentBytes,
                peakEntries,
                peakBytes);
    }

    private record CacheFacts(
            long requests,
            long hits,
            long misses,
            long builds,
            long admissions,
            long evictions,
            long buildUnits,
            long currentEntries,
            long currentBytes,
            long peakEntries,
            long peakBytes) {}
}
