package io.github.mundanej.map.performance;

import java.util.LinkedHashMap;
import java.util.Map;

record EvidenceObservation(long digest, Map<String, Long> counters) {
    EvidenceObservation {
        counters = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(counters));
        counters.forEach(
                (key, value) -> {
                    if (value < 0) {
                        throw new IllegalArgumentException("Negative evidence counter: " + key);
                    }
                });
    }
}
