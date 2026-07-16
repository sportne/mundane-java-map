package io.github.mundanej.map.performance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ScreenGeometryWorkOracleV1 {
    private static final List<String> IDS =
            List.of(
                    "dense-vector-render",
                    "symbol-heavy-render",
                    "vector-pan-sequence",
                    "vector-zoom-sequence",
                    "dense-vector-render-indexed",
                    "vector-pan-sequence-indexed",
                    "vector-zoom-sequence-indexed",
                    "small-vector-render-unoptimized",
                    "small-vector-render-optimized",
                    "dense-vector-render-optimized",
                    "vector-pan-sequence-optimized",
                    "vector-zoom-sequence-optimized");
    private static final Set<String> LEVEL1_IDS =
            Set.of(
                    "small-vector-render-optimized",
                    "dense-vector-render-optimized",
                    "vector-pan-sequence-optimized",
                    "vector-zoom-sequence-optimized");
    private static final Map<String, Map<String, Long>> FROZEN = frozen();

    private ScreenGeometryWorkOracleV1() {}

    static List<String> ids() {
        return IDS;
    }

    static Map<String, Long> expectedOrDefault(
            EvidenceConfiguration.Profile profile,
            String scenarioId,
            Map<String, Long> defaultCounters) {
        return FROZEN.getOrDefault(profile.name() + '/' + scenarioId, defaultCounters);
    }

    static boolean applies(EvidenceConfiguration.Profile profile, String scenarioId) {
        return FROZEN.containsKey(profile.name() + '/' + scenarioId);
    }

    static Map<String, Long> expected(EvidenceConfiguration.Profile profile, String scenarioId) {
        Map<String, Long> result = FROZEN.get(profile.name() + '/' + scenarioId);
        if (result == null) {
            throw new IllegalArgumentException("No screen-geometry work oracle for " + scenarioId);
        }
        return result;
    }

    static String vectorPathState(String scenarioId) {
        if (!IDS.contains(scenarioId)) {
            throw new IllegalArgumentException("No screen-geometry mode oracle for " + scenarioId);
        }
        return LEVEL1_IDS.contains(scenarioId) ? "LEVEL1_OPERATION_LOCAL" : "DISABLED";
    }

    static void verifyVectorPathState(String scenarioId, String actual) {
        if (!vectorPathState(scenarioId).equals(actual)) {
            throw new IllegalStateException("Screen-geometry evidence mode changed: " + scenarioId);
        }
    }

    static void verifyRowOrder(List<String> actual) {
        if (!IDS.equals(actual)) {
            throw new IllegalStateException("Screen-geometry evidence row order changed");
        }
    }

    private static Map<String, Map<String, Long>> frozen() {
        LinkedHashMap<String, Map<String, Long>> result = new LinkedHashMap<>();
        add(result, "SMOKE", "dense-vector-render", work(1, 24, 2_080, 2_080, 2_080, 32, 0, 0, 0));
        add(result, "SMOKE", "symbol-heavy-render", work(1, 256, 288, 288, 288, 32, 0, 0, 0));
        add(result, "SMOKE", "vector-pan-sequence", work(4, 24, 8_320, 8_320, 8_320, 128, 0, 0, 0));
        add(result, "SMOKE", "vector-zoom-sequence", work(4, 24, 6_512, 6_512, 6_512, 64, 0, 0, 0));
        alias(result, "SMOKE", "dense-vector-render-indexed", "dense-vector-render");
        alias(result, "SMOKE", "vector-pan-sequence-indexed", "vector-pan-sequence");
        alias(result, "SMOKE", "vector-zoom-sequence-indexed", "vector-zoom-sequence");
        add(
                result,
                "SMOKE",
                "small-vector-render-unoptimized",
                work(1, 2, 228, 228, 228, 2, 0, 0, 0));
        add(
                result,
                "SMOKE",
                "small-vector-render-optimized",
                work(1, 2, 228, 228, 200, 2, 0, 1, 76));
        add(
                result,
                "SMOKE",
                "dense-vector-render-optimized",
                work(1, 24, 2_080, 2_080, 1_632, 32, 0, 8, 1_216));
        add(
                result,
                "SMOKE",
                "vector-pan-sequence-optimized",
                work(4, 24, 8_320, 8_320, 6_528, 128, 0, 32, 4_864));
        add(
                result,
                "SMOKE",
                "vector-zoom-sequence-optimized",
                work(4, 24, 6_512, 6_512, 5_420, 64, 2, 28, 5_608));
        add(
                result,
                "BASELINE",
                "dense-vector-render",
                work(1, 384, 90_624, 90_624, 90_624, 1_024, 0, 0, 0));
        add(
                result,
                "BASELINE",
                "symbol-heavy-render",
                work(1, 4_096, 4_608, 4_608, 4_608, 512, 0, 0, 0));
        add(
                result,
                "BASELINE",
                "vector-pan-sequence",
                work(16, 384, 1_442_144, 1_442_144, 1_442_144, 16_384, 0, 0, 0));
        add(
                result,
                "BASELINE",
                "vector-zoom-sequence",
                work(12, 384, 968_640, 968_640, 968_640, 10_872, 0, 0, 0));
        alias(result, "BASELINE", "dense-vector-render-indexed", "dense-vector-render");
        alias(result, "BASELINE", "vector-pan-sequence-indexed", "vector-pan-sequence");
        alias(result, "BASELINE", "vector-zoom-sequence-indexed", "vector-zoom-sequence");
        add(
                result,
                "BASELINE",
                "small-vector-render-unoptimized",
                work(1, 2, 452, 452, 452, 4, 0, 0, 0));
        add(
                result,
                "BASELINE",
                "small-vector-render-optimized",
                work(1, 2, 452, 452, 204, 4, 0, 1, 148));
        add(
                result,
                "BASELINE",
                "dense-vector-render-optimized",
                work(1, 384, 90_624, 90_624, 27_136, 1_024, 0, 128, 37_888));
        add(
                result,
                "BASELINE",
                "vector-pan-sequence-optimized",
                work(16, 384, 1_442_144, 1_442_144, 424_768, 16_384, 16, 2_008, 631_616));
        add(
                result,
                "BASELINE",
                "vector-zoom-sequence-optimized",
                work(12, 384, 968_640, 968_640, 294_576, 10_872, 0, 1_392, 402_264));
        return Map.copyOf(result);
    }

    private static void add(
            Map<String, Map<String, Long>> target,
            String profile,
            String scenario,
            Map<String, Long> counters) {
        target.put(profile + '/' + scenario, counters);
    }

    private static void alias(
            Map<String, Map<String, Long>> target, String profile, String alias, String original) {
        target.put(profile + '/' + alias, target.get(profile + '/' + original));
    }

    private static Map<String, Long> work(
            long frames,
            long features,
            long inputCoordinates,
            long projectedCoordinates,
            long renderCoordinates,
            long lineFragments,
            long culledPaths,
            long fallbackPlans,
            long retainedRenderGeometryBytes) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        result.put("frames", frames);
        result.put("features", features);
        result.put("portableInvariants", 6L);
        result.put("inputCoordinates", inputCoordinates);
        result.put("projectedCoordinates", projectedCoordinates);
        result.put("renderCoordinates", renderCoordinates);
        result.put("lineFragments", lineFragments);
        result.put("culledPaths", culledPaths);
        result.put("fallbackPlans", fallbackPlans);
        result.put("retainedRenderGeometryBytes", retainedRenderGeometryBytes);
        return Map.copyOf(result);
    }
}
