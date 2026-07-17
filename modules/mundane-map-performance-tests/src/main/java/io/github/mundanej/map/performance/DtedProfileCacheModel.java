package io.github.mundanej.map.performance;

import java.util.LinkedHashMap;

/** Untimed analytical profile-cache model; contains no parser, reader, or file I/O. */
final class DtedProfileCacheModel {
    static final int QUERIES = 65_536;
    static final long RECORD_BYTES = 7_214L;
    static final long DECODED_PROFILE_BYTES = 29_264L;

    private DtedProfileCacheModel() {}

    static Result replay(Trace trace, int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("width must be positive");
        }
        LinkedHashMap<Integer, Boolean> cache = new LinkedHashMap<>(width, 0.75f, true);
        long hits = 0;
        long misses = 0;
        for (int query = 0; query < QUERIES; query++) {
            int column =
                    trace == Trace.LOCAL
                            ? Math.floorDiv(query, 128)
                            : Math.floorMod(997 * query, 3_600);
            int accesses = (query & 1) == 0 ? 1 : 2;
            for (int offset = 0; offset < accesses; offset++) {
                int profile = column + offset;
                if (cache.get(profile) != null) {
                    hits++;
                } else {
                    misses++;
                    cache.put(profile, Boolean.TRUE);
                    if (cache.size() > width) {
                        Integer eldest = cache.keySet().iterator().next();
                        cache.remove(eldest);
                    }
                }
            }
        }
        long retained =
                Math.addExact(RECORD_BYTES, Math.multiplyExact(width, DECODED_PROFILE_BYTES));
        return new Result(width, hits, misses, Math.multiplyExact(misses, RECORD_BYTES), retained);
    }

    enum Trace {
        LOCAL,
        SCATTERED
    }

    record Result(int width, long hits, long misses, long bytesRead, long retainedBytes) {}
}
