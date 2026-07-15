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
                    entry("SMOKE", "vector-zoom-sequence", "56d246ef2d1394ce"));

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
        return FROZEN;
    }

    private static Map.Entry<String, String> entry(String profile, String scenario, String digest) {
        return Map.entry(profile + '/' + scenario, digest);
    }
}
