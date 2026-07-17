package io.github.mundanej.map.performance;

/** Checked logical storage accounting for the eager DTED publication decision. */
final class DtedLogicalMemory {
    static final long PUBLISHED_LIMIT = 134_217_728L;
    static final long OPEN_PEAK_LIMIT = 268_435_456L;

    private DtedLogicalMemory() {}

    static long mask(long samples) {
        return Math.multiplyExact(8L, Math.floorDiv(Math.addExact(samples, 63L), 64L));
    }

    static long published(long samples) {
        return Math.addExact(Math.multiplyExact(8L, samples), mask(samples));
    }

    static long record(int rows) {
        return Math.addExact(12L, Math.multiplyExact(2L, rows));
    }

    static long openPeak(long samples, int rows) {
        return Math.addExact(
                2_700L, Math.addExact(record(rows), Math.multiplyExact(2L, published(samples))));
    }

    static boolean retainEager(long samples, int rows) {
        return withinDecisionLimits(published(samples), openPeak(samples, rows));
    }

    static boolean withinDecisionLimits(long publishedBytes, long openPeakBytes) {
        if (publishedBytes < 0 || openPeakBytes < 0) {
            throw new IllegalArgumentException("logical bytes must be nonnegative");
        }
        return publishedBytes <= PUBLISHED_LIMIT && openPeakBytes <= OPEN_PEAK_LIMIT;
    }
}
