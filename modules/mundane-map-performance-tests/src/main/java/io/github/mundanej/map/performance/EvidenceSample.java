package io.github.mundanej.map.performance;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;

record EvidenceSample(
        String scenarioId,
        List<Long> rawNanos,
        long medianNanos,
        long p95Nanos,
        long operationsPerSecondMilli,
        EvidenceObservation observation) {
    EvidenceSample {
        rawNanos = List.copyOf(rawNanos);
    }

    static EvidenceSample of(
            String scenarioId, long[] values, long operations, EvidenceObservation observation) {
        if (operations <= 0) {
            throw new IllegalArgumentException("Batch operations must be positive");
        }
        long[] sorted = values.clone();
        Arrays.sort(sorted);
        long median = median(sorted);
        int rank =
                Math.toIntExact(
                        (Math.addExact(Math.multiplyExact(95L, sorted.length), 99) / 100) - 1);
        long p95 = sorted[rank];
        BigInteger throughput =
                BigInteger.valueOf(operations)
                        .multiply(BigInteger.valueOf(1_000_000_000_000L))
                        .divide(BigInteger.valueOf(median));
        return new EvidenceSample(
                scenarioId,
                Arrays.stream(values).boxed().toList(),
                median,
                p95,
                throughput.longValueExact(),
                observation);
    }

    static long median(long[] sorted) {
        if (sorted.length == 0) {
            throw new IllegalArgumentException("At least one measurement is required");
        }
        for (long value : sorted) {
            if (value <= 0) {
                throw new IllegalArgumentException("Measured nanos must be positive");
            }
        }
        int middle = sorted.length / 2;
        return sorted.length % 2 == 1
                ? sorted[middle]
                : sorted[middle - 1] + (sorted[middle] - sorted[middle - 1]) / 2;
    }

    EvidenceSample withObservation(EvidenceObservation replacement) {
        return new EvidenceSample(
                scenarioId, rawNanos, medianNanos, p95Nanos, operationsPerSecondMilli, replacement);
    }
}
