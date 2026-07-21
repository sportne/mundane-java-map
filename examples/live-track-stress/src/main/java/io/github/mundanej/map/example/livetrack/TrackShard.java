package io.github.mundanej.map.example.livetrack;

import java.util.Arrays;

/** Owns one contiguous track range, its timing wheel, truth state, and packed estimator. */
final class TrackShard {
    static final double WORLD_X = 20_037_508.342789244;
    static final double MAX_Y = 15_538_711.09630922;
    private static final int WHEEL_SIZE = 64;
    private static final double MIN_SPEED = 30.0;
    private static final double MAX_SPEED = 250.0;

    private final int firstTrackId;
    private final long seed;
    private final IouKalmanConfig filterConfig;
    private final PackedIouKalmanFilter filter;
    private final double[] truthX;
    private final double[] truthY;
    private final double[] course;
    private final double[] speed;
    private final long[] sequence;
    private final int[] nextDueSecond;
    private final int[] nextLink;
    private final int[] bucketHeads = new int[WHEEL_SIZE];
    private final double[] gaussianPair = new double[2];
    private long initializationReports;
    private long scheduledReports;
    private long processedReports;
    private long evidenceProcessedReports;
    private long lateReports;
    private long pendingReports;
    private long tracksVisited;

    TrackShard(int firstTrackId, int trackCount, long seed, IouKalmanConfig filterConfig) {
        this.firstTrackId = firstTrackId;
        this.seed = seed;
        this.filterConfig = filterConfig;
        filter = new PackedIouKalmanFilter(trackCount, filterConfig);
        truthX = new double[trackCount];
        truthY = new double[trackCount];
        course = new double[trackCount];
        speed = new double[trackCount];
        sequence = new long[trackCount];
        nextDueSecond = new int[trackCount];
        nextLink = new int[trackCount];
        Arrays.fill(bucketHeads, -1);
        initialize();
    }

    void advanceSecond(int simulationSecond, boolean late) throws InterruptedException {
        int slot = simulationSecond & (WHEEL_SIZE - 1);
        int track = bucketHeads[slot];
        bucketHeads[slot] = -1;
        while (track >= 0) {
            int following = nextLink[track];
            nextLink[track] = -1;
            if (nextDueSecond[track] != simulationSecond) {
                throw new IllegalStateException("LIVE_TRACK_WHEEL_SLOT_MISMATCH");
            }
            pendingReports--;
            process(track, simulationSecond);
            processedReports++;
            evidenceProcessedReports++;
            if (late) {
                lateReports++;
            }
            tracksVisited++;
            if ((tracksVisited & 255L) == 0L && Thread.currentThread().isInterrupted()) {
                throw new InterruptedException("live-track shard cancelled");
            }
            track = following;
        }
    }

    int firstTrackId() {
        return firstTrackId;
    }

    int trackCount() {
        return truthX.length;
    }

    long initializationReports() {
        return initializationReports;
    }

    long scheduledReports() {
        return scheduledReports;
    }

    long processedReports() {
        return processedReports;
    }

    long evidenceProcessedReports() {
        return evidenceProcessedReports;
    }

    long lateReports() {
        return lateReports;
    }

    long rejectedReports() {
        return filter.rejectedReports();
    }

    long tracksVisited() {
        return tracksVisited;
    }

    long pendingReports() {
        return pendingReports;
    }

    void addAccuracy(double[] totals) {
        if (totals.length < 5) {
            throw new IllegalArgumentException("accuracy target needs five entries");
        }
        for (int track = 0; track < trackCount(); track++) {
            double deltaX = filter.x(track) - truthX[track];
            double deltaY = filter.y(track) - truthY[track];
            totals[0] += deltaX * deltaX + deltaY * deltaY;
        }
        totals[1] += trackCount();
        totals[2] += filter.normalizedInnovationSum();
        totals[3] += filter.innovationCount();
        totals[4] = Math.max(totals[4], filter.normalizedInnovationMaximum());
    }

    void resetEvidenceMetrics() {
        filter.resetEvidenceMetrics();
        evidenceProcessedReports = 0L;
    }

    long logicalBytes() {
        long localPerTrack = 4L * Double.BYTES + Long.BYTES + 2L * Integer.BYTES;
        return Math.addExact(
                Math.addExact(
                        Math.multiplyExact((long) trackCount(), localPerTrack),
                        Math.addExact(
                                (long) bucketHeads.length * Integer.BYTES,
                                (long) gaussianPair.length * Double.BYTES)),
                filter.logicalBytes());
    }

    double truthX(int globalTrackId) {
        return truthX[local(globalTrackId)];
    }

    double truthY(int globalTrackId) {
        return truthY[local(globalTrackId)];
    }

    double estimatedX(int globalTrackId) {
        return filter.x(local(globalTrackId));
    }

    double estimatedY(int globalTrackId) {
        return filter.y(local(globalTrackId));
    }

    void copyDisplayPositions(
            double timestampSeconds, double[] positionsX, double[] positionsY, int offset) {
        filter.copyDisplayPositions(timestampSeconds, positionsX, positionsY, offset);
    }

    double course(int globalTrackId) {
        return course[local(globalTrackId)];
    }

    double speed(int globalTrackId) {
        return speed[local(globalTrackId)];
    }

    int nextDueSecond(int globalTrackId) {
        return nextDueSecond[local(globalTrackId)];
    }

    long sequence(int globalTrackId) {
        return sequence[local(globalTrackId)];
    }

    boolean hasExactlyOneWheelEntryPerTrack() {
        boolean[] seen = new boolean[trackCount()];
        int entries = 0;
        for (int slot = 0; slot < bucketHeads.length; slot++) {
            int track = bucketHeads[slot];
            while (track >= 0) {
                if (track >= trackCount() || seen[track] || (nextDueSecond[track] & 63) != slot) {
                    return false;
                }
                seen[track] = true;
                entries++;
                if (entries > trackCount()) {
                    return false;
                }
                track = nextLink[track];
            }
        }
        return entries == trackCount() && entries == pendingReports;
    }

    void reset() {
        filter.reset();
        Arrays.fill(truthX, 0.0);
        Arrays.fill(truthY, 0.0);
        Arrays.fill(course, 0.0);
        Arrays.fill(speed, 0.0);
        Arrays.fill(sequence, 0L);
        Arrays.fill(nextDueSecond, 0);
        Arrays.fill(nextLink, -1);
        Arrays.fill(bucketHeads, -1);
        initializationReports = 0L;
        scheduledReports = 0L;
        processedReports = 0L;
        evidenceProcessedReports = 0L;
        lateReports = 0L;
        pendingReports = 0L;
        tracksVisited = 0L;
        initialize();
    }

    long checksum(long prior) {
        long value = prior;
        for (int track = 0; track < trackCount(); track++) {
            value = 31L * value + Double.doubleToLongBits(truthX[track]);
            value = 31L * value + Double.doubleToLongBits(truthY[track]);
            value = 31L * value + Double.doubleToLongBits(filter.x(track));
            value = 31L * value + Double.doubleToLongBits(filter.y(track));
            value = 31L * value + sequence[track];
            value = 31L * value + nextDueSecond[track];
        }
        return value;
    }

    private void initialize() {
        for (int localTrack = 0; localTrack < trackCount(); localTrack++) {
            int trackId = firstTrackId + localTrack;
            truthX[localTrack] =
                    -WORLD_X
                            + 2.0
                                    * WORLD_X
                                    * DeterministicDraws.uniform(
                                            seed, trackId, 0L, 0, DeterministicDraws.TRUTH_TAG);
            truthY[localTrack] =
                    -MAX_Y
                            + 2.0
                                    * MAX_Y
                                    * DeterministicDraws.uniform(
                                            seed, trackId, 0L, 1, DeterministicDraws.TRUTH_TAG);
            course[localTrack] =
                    2.0
                            * StrictMath.PI
                            * DeterministicDraws.uniform(
                                    seed, trackId, 0L, 2, DeterministicDraws.TRUTH_TAG);
            speed[localTrack] =
                    MIN_SPEED
                            + (MAX_SPEED - MIN_SPEED)
                                    * DeterministicDraws.uniform(
                                            seed, trackId, 0L, 3, DeterministicDraws.TRUTH_TAG);
            DeterministicDraws.gaussianPair(
                    seed, trackId, 0L, 0, DeterministicDraws.MEASUREMENT_TAG, gaussianPair);
            double measuredX =
                    truthX[localTrack]
                            + filterConfig.measurementStandardDeviation() * gaussianPair[0];
            double measuredY =
                    truthY[localTrack]
                            + filterConfig.measurementStandardDeviation() * gaussianPair[1];
            filter.initialize(localTrack, 0L, measuredX, measuredY);
            initializationReports++;
            schedule(localTrack, DeterministicDraws.intervalSeconds(seed, trackId, 0L));
        }
    }

    private void process(int track, int simulationSecond) {
        int trackId = firstTrackId + track;
        int delta = simulationSecond - (int) filter.timestampSeconds(track);
        truthX[track] += speed[track] * StrictMath.cos(course[track]) * delta;
        truthY[track] += speed[track] * StrictMath.sin(course[track]) * delta;
        wrapAndReflect(track);
        long reportSequence = sequence[track] + 1L;
        DeterministicDraws.gaussianPair(
                seed, trackId, reportSequence, 0, DeterministicDraws.TRUTH_TAG, gaussianPair);
        speed[track] =
                clamp(
                        speed[track] + 3.0 * StrictMath.sqrt(delta) * gaussianPair[0],
                        MIN_SPEED,
                        MAX_SPEED);
        course[track] =
                normalizeCourse(course[track] + 0.005 * StrictMath.sqrt(delta) * gaussianPair[1]);
        DeterministicDraws.gaussianPair(
                seed, trackId, reportSequence, 0, DeterministicDraws.MEASUREMENT_TAG, gaussianPair);
        double measuredX =
                truthX[track] + filterConfig.measurementStandardDeviation() * gaussianPair[0];
        double measuredY =
                truthY[track] + filterConfig.measurementStandardDeviation() * gaussianPair[1];
        filter.update(track, simulationSecond, measuredX, measuredY);
        sequence[track] = reportSequence;
        int following =
                simulationSecond
                        + DeterministicDraws.intervalSeconds(seed, trackId, reportSequence);
        schedule(track, following);
    }

    private void schedule(int track, int dueSecond) {
        nextDueSecond[track] = dueSecond;
        int slot = dueSecond & (WHEEL_SIZE - 1);
        nextLink[track] = bucketHeads[slot];
        bucketHeads[slot] = track;
        scheduledReports++;
        pendingReports++;
    }

    private void wrapAndReflect(int track) {
        double width = 2.0 * WORLD_X;
        while (truthX[track] > WORLD_X) {
            truthX[track] -= width;
        }
        while (truthX[track] < -WORLD_X) {
            truthX[track] += width;
        }
        if (truthY[track] > MAX_Y) {
            truthY[track] = 2.0 * MAX_Y - truthY[track];
            course[track] = normalizeCourse(2.0 * StrictMath.PI - course[track]);
        } else if (truthY[track] < -MAX_Y) {
            truthY[track] = -2.0 * MAX_Y - truthY[track];
            course[track] = normalizeCourse(2.0 * StrictMath.PI - course[track]);
        }
    }

    private int local(int globalTrackId) {
        int localTrack = globalTrackId - firstTrackId;
        if (localTrack < 0 || localTrack >= trackCount()) {
            throw new IndexOutOfBoundsException("track is outside shard");
        }
        return localTrack;
    }

    private static double normalizeCourse(double value) {
        double normalized = value % (2.0 * StrictMath.PI);
        return normalized < 0.0 ? normalized + 2.0 * StrictMath.PI : normalized;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
