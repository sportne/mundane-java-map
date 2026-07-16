package io.github.mundanej.map.performance;

import java.util.LinkedHashMap;
import java.util.Map;

final class ScenarioOracleV1 {
    private static final Map<String, String> FROZEN =
            Map.ofEntries(
                    entry("BASELINE", "memory-query-full", "d4da23a839eb2a48"),
                    entry("BASELINE", "memory-query-window", "5f4d383c501cd46b"),
                    entry("BASELINE", "dense-vector-render", "ef9c8b51b0161c12"),
                    entry("BASELINE", "symbol-heavy-render", "bab603885f61b84a"),
                    entry("BASELINE", "hit-test-sweep", "0e482955bd4df5fe"),
                    entry("BASELINE", "shapefile-query-window", "40f419feca886494"),
                    entry("BASELINE", "shapefile-render-window", "b76b1a1b48e14b55"),
                    entry("BASELINE", "png-window-bilinear-disabled", "2125ea9e978a8b2e"),
                    entry("BASELINE", "jpeg-window-bilinear-preseeded", "040aebf7d620a9b6"),
                    entry("BASELINE", "affine-raster-pan", "c9178011eeb45578"),
                    entry("BASELINE", "vector-pan-sequence", "0791a2a96393ed1a"),
                    entry("BASELINE", "vector-zoom-sequence", "0f00fd437fbd4f25"),
                    entry("BASELINE", "index-build-128", "2bc8fe25bef25ada"),
                    entry("BASELINE", "index-build-8192", "44c8bcf1ece91769"),
                    entry("BASELINE", "index-build-131072", "322961c99fccd029"),
                    entry("BASELINE", "index-query-linear-32", "288a7287b9bfe58f"),
                    entry("BASELINE", "index-query-str16-32", "cd7de60a47b74642"),
                    entry("BASELINE", "index-query-linear-128", "2b1511dc7c0f3f03"),
                    entry("BASELINE", "index-query-str16-128", "32a0f63c8f4d651c"),
                    entry("BASELINE", "index-query-linear-512", "157f78ef4bfa67a1"),
                    entry("BASELINE", "index-query-str16-512", "661110628b0ce2e7"),
                    entry("BASELINE", "index-query-linear-2048", "3e75015f63fc7a50"),
                    entry("BASELINE", "index-query-str16-2048", "9813a578e2f289b4"),
                    entry("BASELINE", "index-query-linear-8192", "33835a2e2464c70a"),
                    entry("BASELINE", "index-query-str16-8192", "4c4dcf61c976df3b"),
                    entry("BASELINE", "index-query-linear-32768", "b98d8f14219ef99b"),
                    entry("BASELINE", "index-query-str16-32768", "51a12946d23765bd"),
                    entry("BASELINE", "index-query-linear-131072", "9d02d7e8c1b39abc"),
                    entry("BASELINE", "index-query-str16-131072", "95d3705013070a13"),
                    entry("SMOKE", "memory-query-full", "ac5d092e34ba63eb"),
                    entry("SMOKE", "memory-query-window", "93b262ceb74391da"),
                    entry("SMOKE", "dense-vector-render", "c0516beaa147bdf3"),
                    entry("SMOKE", "symbol-heavy-render", "39dc8aca11857707"),
                    entry("SMOKE", "hit-test-sweep", "d8ab3fcaaf4e716e"),
                    entry("SMOKE", "shapefile-query-window", "08e3ed8a2961a978"),
                    entry("SMOKE", "shapefile-render-window", "49e098bcb0291bde"),
                    entry("SMOKE", "png-window-bilinear-disabled", "598b98846f4029ab"),
                    entry("SMOKE", "jpeg-window-bilinear-preseeded", "3924cf2a8ac04826"),
                    entry("SMOKE", "affine-raster-pan", "65accc605d7e170b"),
                    entry("SMOKE", "vector-pan-sequence", "0b82bd929c2c2213"),
                    entry("SMOKE", "vector-zoom-sequence", "56d246ef2d1394ce"),
                    entry("SMOKE", "index-build-128", "324d9003171db243"),
                    entry("SMOKE", "index-build-8192", "b3755bcb97e923d2"),
                    entry("SMOKE", "index-build-131072", "a816722a5eb10d1e"),
                    entry("SMOKE", "index-query-linear-32", "d6283980a45ca455"),
                    entry("SMOKE", "index-query-str16-32", "4e1918d498e508c7"),
                    entry("SMOKE", "index-query-linear-128", "972b779b8851970b"),
                    entry("SMOKE", "index-query-str16-128", "ae91ce918352d82b"),
                    entry("SMOKE", "index-query-linear-512", "e41c1a466a8bb327"),
                    entry("SMOKE", "index-query-str16-512", "a5a318affad7cd07"),
                    entry("SMOKE", "index-query-linear-2048", "afc136e1356e182b"),
                    entry("SMOKE", "index-query-str16-2048", "c191bde3f1a5cf1f"),
                    entry("SMOKE", "index-query-linear-8192", "11d4b3d8656db364"),
                    entry("SMOKE", "index-query-str16-8192", "6f3dbe7a066481a0"),
                    entry("SMOKE", "index-query-linear-32768", "98fc26ae129f37c9"),
                    entry("SMOKE", "index-query-str16-32768", "9473e8aa535d5d3a"),
                    entry("SMOKE", "index-query-linear-131072", "01bb4dce2d2c818c"),
                    entry("SMOKE", "index-query-str16-131072", "20ef892eccb890f3"));

    private ScenarioOracleV1() {}

    static EvidenceObservation expected(
            EvidenceConfiguration.Profile profile, String scenarioId, Map<String, Long> counters) {
        String frozen = FROZEN.get(profile.name() + '/' + scenarioId);
        if (frozen == null) {
            throw new IllegalStateException("Missing frozen scenario oracle: " + scenarioId);
        }
        return new EvidenceObservation(
                Long.parseUnsignedLong(frozen, 16), new LinkedHashMap<>(counters));
    }

    static ScenarioOracle exact(EvidenceObservation expected) {
        return actual -> {
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                        "Semantic oracle mismatch: expected "
                                + EvidenceReport.hex(expected.digest())
                                + ", actual "
                                + EvidenceReport.hex(actual.digest()));
            }
        };
    }

    static Map<String, String> frozenDigests() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>(FROZEN);
        for (String profile : java.util.List.of("BASELINE", "SMOKE")) {
            alias(result, profile, "memory-query-window-indexed", "memory-query-window");
            alias(result, profile, "hit-test-sweep-indexed", "hit-test-sweep");
            alias(result, profile, "dense-vector-render-indexed", "dense-vector-render");
            alias(result, profile, "vector-pan-sequence-indexed", "vector-pan-sequence");
            alias(result, profile, "vector-zoom-sequence-indexed", "vector-zoom-sequence");
        }
        return Map.copyOf(result);
    }

    private static void alias(
            Map<String, String> target, String profile, String alias, String original) {
        target.put(profile + '/' + alias, target.get(profile + '/' + original));
    }

    private static Map.Entry<String, String> entry(String profile, String scenario, String digest) {
        return Map.entry(profile + '/' + scenario, digest);
    }
}
