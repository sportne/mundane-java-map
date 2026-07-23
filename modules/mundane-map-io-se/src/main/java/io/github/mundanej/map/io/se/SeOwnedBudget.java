package io.github.mundanej.map.io.se;

final class SeOwnedBudget {
    private final String source;
    private final long maximum;
    private long charged;

    SeOwnedBudget(String source, SeReadLimits limits) {
        this.source = source;
        maximum = limits.maximumOwnedBytes();
    }

    void charge(long bytes, String path) {
        if (bytes < 0 || charged > maximum - bytes) {
            throw SeFailures.limit(
                    source, "ownedBytes", saturatedAdd(charged, bytes), maximum, path);
        }
        charged += bytes;
    }

    private static long saturatedAdd(long left, long right) {
        if (right < 0 || left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }
}
