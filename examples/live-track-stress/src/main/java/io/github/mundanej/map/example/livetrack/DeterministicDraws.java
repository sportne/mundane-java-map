package io.github.mundanej.map.example.livetrack;

final class DeterministicDraws {
    static final long SCHEDULE_TAG = 0x5343484544554c45L;
    static final long TRUTH_TAG = 0x54525554484d4f54L;
    static final long MEASUREMENT_TAG = 0x4d4541535552454dL;

    private DeterministicDraws() {}

    static double uniform(long seed, int trackId, long sequence, int drawIndex, long tag) {
        return (raw(seed, trackId, sequence, drawIndex, tag) >>> 11) * 0x1.0p-53;
    }

    static double gaussian(
            long seed, int trackId, long sequence, int pairIndex, int member, long tag) {
        long first = raw(seed, trackId, sequence, 2 * pairIndex, tag);
        long second = raw(seed, trackId, sequence, 2 * pairIndex + 1, tag);
        double u1 = ((first >>> 11) + 1L) * 0x1.0p-53;
        double u2 = (second >>> 11) * 0x1.0p-53;
        double radius = StrictMath.sqrt(-2.0 * StrictMath.log(u1));
        double angle = 2.0 * StrictMath.PI * u2;
        return member == 0 ? radius * StrictMath.cos(angle) : radius * StrictMath.sin(angle);
    }

    static int intervalSeconds(long seed, int trackId, long sequence) {
        double uniform = uniform(seed, trackId, sequence, 0, SCHEDULE_TAG);
        int interval = 1 + (int) StrictMath.floor(StrictMath.log1p(-uniform) / StrictMath.log(0.9));
        return Math.min(60, interval);
    }

    private static long raw(long seed, int trackId, long sequence, int drawIndex, long tag) {
        long value = mix64(seed ^ tag);
        value = mix64(value ^ Integer.toUnsignedLong(trackId));
        value = mix64(value ^ sequence);
        return mix64(value ^ Integer.toUnsignedLong(drawIndex));
    }

    private static long mix64(long value) {
        long mixed = (value ^ (value >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }
}
