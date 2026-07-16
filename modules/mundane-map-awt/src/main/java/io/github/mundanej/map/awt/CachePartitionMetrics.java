package io.github.mundanej.map.awt;

/** Exact event and ending/peak state for one typed cache partition in one paint operation. */
record CachePartitionMetrics(
        long requests,
        long hits,
        long misses,
        long builds,
        long admissions,
        long evictions,
        long bypasses,
        long buildUnits,
        long currentEntries,
        long currentLogicalBytes,
        long peakEntries,
        long peakLogicalBytes) {
    static CachePartitionMetrics empty() {
        return new CachePartitionMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }
}
