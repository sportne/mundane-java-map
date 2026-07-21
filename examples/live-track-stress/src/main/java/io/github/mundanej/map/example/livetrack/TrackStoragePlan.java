package io.github.mundanej.map.example.livetrack;

record TrackStoragePlan(long logicalBytes, long largestAllocation, long maximumHeap) {
    private static final long HEADROOM = 256L * 1024L * 1024L;
    private static final long PER_TRACK_BYTES = 113L;
    private static final long PER_SHARD_BYTES = 3_200L;

    static TrackStoragePlan preflight(TrackSimulationConfig config, long maximumHeap) {
        if (maximumHeap <= 0L) {
            throw new IllegalArgumentException("maximumHeap must be positive");
        }
        long logical =
                Math.addExact(
                        Math.multiplyExact((long) config.population(), PER_TRACK_BYTES),
                        Math.multiplyExact((long) config.workers(), PER_SHARD_BYTES));
        long hardCeiling = Math.multiplyExact((long) config.population(), 192L);
        int largestShard =
                config.population() / config.workers()
                        + (config.population() % config.workers() == 0 ? 0 : 1);
        long largest = Math.multiplyExact((long) largestShard, Double.BYTES);
        long withHeadroom = Math.addExact(logical, HEADROOM);
        long sixtyPercent = maximumHeap / 5L * 3L;
        if (logical > hardCeiling || withHeadroom > maximumHeap || logical > sixtyPercent) {
            throw new IllegalArgumentException("LIVE_TRACK_STORAGE_LIMIT");
        }
        return new TrackStoragePlan(logical, largest, maximumHeap);
    }
}
