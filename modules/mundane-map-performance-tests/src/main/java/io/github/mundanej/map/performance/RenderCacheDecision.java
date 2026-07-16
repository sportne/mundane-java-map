package io.github.mundanej.map.performance;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Checked one-run application of the predeclared G7-004 retain/delete rules. */
record RenderCacheDecision(String candidate, String decision, Map<String, Boolean> checks) {
    static List<RenderCacheDecision> evaluate(
            EvidenceConfiguration configuration, List<EvidenceSample> samples) {
        if (configuration.profile() != EvidenceConfiguration.Profile.BASELINE
                || configuration.investigation()) {
            return List.of(notEvaluated("VECTOR_TEMPLATE"));
        }
        LinkedHashMap<String, EvidenceSample> byId = new LinkedHashMap<>();
        samples.forEach(sample -> byId.put(sample.scenarioId(), sample));
        return List.of(evaluateOne("VECTOR_TEMPLATE", () -> vectorDecision(byId)));
    }

    private static RenderCacheDecision vectorDecision(Map<String, EvidenceSample> samples) {
        EvidenceSample baseline = require(samples, "symbol-heavy-render");
        EvidenceSample cold = require(samples, "symbol-heavy-render-template-cache-cold");
        EvidenceSample warm = require(samples, "symbol-heavy-render-template-cache-warm");
        long requests = counter(warm, "vectorTemplateCacheRequests");
        long hits = counter(warm, "vectorTemplateCacheHits");
        long builds = counter(warm, "vectorTemplateBuilds");
        long baselineBuilds = counter(baseline, "vectorTemplateBuilds");
        LinkedHashMap<String, Boolean> checks = new LinkedHashMap<>();
        checks.put("warmHitRateAtLeast99Percent", atLeast(hits, requests, 99, 100));
        checks.put(
                "warmBuildReductionAtLeast99Percent",
                reductionAtLeast(baselineBuilds, builds, 99, 100));
        checks.put(
                "warmMedianAtMost95Percent",
                atMost(warm.medianNanos(), baseline.medianNanos(), 19, 20));
        checks.put(
                "coldMedianAtMost102Percent",
                atMost(cold.medianNanos(), baseline.medianNanos(), 51, 50));
        checks.put("semanticMatch", warm.observation().digest() == baseline.observation().digest());
        checks.put("noBypassOrEviction", noEvents(samples, "template-cache", "vectorTemplate"));
        checks.put(
                "peakLogicalBytesAtMost4MiB",
                counter(warm, "vectorTemplateCachePeakLogicalBytes") <= 4L * 1_024L * 1_024L);
        return decided("VECTOR_TEMPLATE", checks);
    }

    private static boolean noEvents(
            Map<String, EvidenceSample> samples, String idFragment, String prefix) {
        for (Map.Entry<String, EvidenceSample> entry : samples.entrySet()) {
            if (entry.getKey().contains(idFragment)
                    && (counter(entry.getValue(), prefix + "CacheBypasses") != 0
                            || counter(entry.getValue(), prefix + "CacheEvictions") != 0)) {
                return false;
            }
        }
        return true;
    }

    private static RenderCacheDecision decided(
            String candidate, LinkedHashMap<String, Boolean> checks) {
        boolean retained = checks.values().stream().allMatch(Boolean::booleanValue);
        return new RenderCacheDecision(
                candidate,
                retained ? "RETAINED" : "REJECTED",
                java.util.Collections.unmodifiableMap(new LinkedHashMap<>(checks)));
    }

    private static RenderCacheDecision rejected(String candidate, String check, boolean value) {
        return new RenderCacheDecision(candidate, "REJECTED", Map.of(check, value));
    }

    private static RenderCacheDecision evaluateOne(
            String candidate, java.util.function.Supplier<RenderCacheDecision> evaluator) {
        try {
            return evaluator.get();
        } catch (ArithmeticException | IllegalArgumentException failure) {
            return rejected(candidate, "completeCheckedEvidence", false);
        }
    }

    private static RenderCacheDecision notEvaluated(String candidate) {
        return new RenderCacheDecision(candidate, "NOT_EVALUATED", Map.of());
    }

    private static EvidenceSample require(Map<String, EvidenceSample> samples, String id) {
        EvidenceSample result = samples.get(id);
        if (result == null) {
            throw new IllegalArgumentException("Missing cache-decision scenario: " + id);
        }
        return result;
    }

    private static long counter(EvidenceSample sample, String key) {
        Long result = sample.observation().counters().get(key);
        if (result == null || result < 0) {
            throw new IllegalArgumentException("Missing cache-decision counter: " + key);
        }
        return result;
    }

    static boolean atMost(long candidate, long baseline, long numerator, long denominator) {
        requireRatioInputs(candidate, baseline, numerator, denominator);
        return BigInteger.valueOf(denominator)
                        .multiply(BigInteger.valueOf(candidate))
                        .compareTo(
                                BigInteger.valueOf(numerator)
                                        .multiply(BigInteger.valueOf(baseline)))
                <= 0;
    }

    static boolean atLeast(long part, long whole, long numerator, long denominator) {
        requireRatioInputs(part, whole, numerator, denominator);
        return BigInteger.valueOf(denominator)
                        .multiply(BigInteger.valueOf(part))
                        .compareTo(
                                BigInteger.valueOf(numerator).multiply(BigInteger.valueOf(whole)))
                >= 0;
    }

    static boolean reductionAtLeast(
            long baselineBuilds, long candidateBuilds, long numerator, long denominator) {
        if (baselineBuilds <= 0
                || candidateBuilds < 0
                || candidateBuilds > baselineBuilds
                || numerator < 0
                || denominator <= 0) {
            return false;
        }
        return BigInteger.valueOf(denominator)
                        .multiply(
                                BigInteger.valueOf(baselineBuilds)
                                        .subtract(BigInteger.valueOf(candidateBuilds)))
                        .compareTo(
                                BigInteger.valueOf(numerator)
                                        .multiply(BigInteger.valueOf(baselineBuilds)))
                >= 0;
    }

    private static void requireRatioInputs(
            long left, long right, long numerator, long denominator) {
        if (left < 0 || right <= 0 || numerator < 0 || denominator <= 0) {
            throw new IllegalArgumentException("Invalid cache-decision ratio input");
        }
    }
}
