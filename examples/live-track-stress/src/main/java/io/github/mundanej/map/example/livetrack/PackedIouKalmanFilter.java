package io.github.mundanej.map.example.livetrack;

import java.util.Arrays;

/** Structure-of-arrays forward IOU-Kalman Filter state estimator for one shard. */
final class PackedIouKalmanFilter {
    private static final int MAX_TRACKS = 1_000_000;
    private static final double MAX_PREDICTION_SECONDS = 60.0;
    private static final int COEFFICIENT_COUNT = 5;

    private final IouKalmanConfig config;
    private final double[] x;
    private final double[] y;
    private final double[] velocityX;
    private final double[] velocityY;
    private final double[] positionVariance;
    private final double[] positionVelocityCovariance;
    private final double[] velocityVariance;
    private final long[] timestampSeconds;
    private final boolean[] initialized;
    private final double[] integerCoefficients = new double[60 * COEFFICIENT_COUNT];
    private final double[] displayCoefficients = new double[COEFFICIENT_COUNT];
    private final double[] displayTransitions = new double[61];
    private long acceptedReports;
    private long rejectedReports;
    private long innovationCount;
    private double normalizedInnovationSum;
    private double normalizedInnovationMaximum;

    PackedIouKalmanFilter(int trackCount, IouKalmanConfig config) {
        if (trackCount < 1 || trackCount > MAX_TRACKS) {
            throw new IllegalArgumentException("trackCount is outside [1, 1000000]");
        }
        this.config = config;
        x = new double[trackCount];
        y = new double[trackCount];
        velocityX = new double[trackCount];
        velocityY = new double[trackCount];
        positionVariance = new double[trackCount];
        positionVelocityCovariance = new double[trackCount];
        velocityVariance = new double[trackCount];
        timestampSeconds = new long[trackCount];
        initialized = new boolean[trackCount];
        for (int delta = 1; delta <= 60; delta++) {
            IouModel.coefficients(config, delta, displayCoefficients);
            System.arraycopy(
                    displayCoefficients,
                    0,
                    integerCoefficients,
                    (delta - 1) * COEFFICIENT_COUNT,
                    COEFFICIENT_COUNT);
        }
        Arrays.fill(displayCoefficients, 0.0);
    }

    int trackCount() {
        return x.length;
    }

    boolean initialize(int track, long timestamp, double measuredX, double measuredY) {
        checkTrack(track);
        requireFiniteMeasurement(measuredX, measuredY);
        if (initialized[track]) {
            rejectedReports++;
            return false;
        }
        x[track] = measuredX;
        y[track] = measuredY;
        velocityX[track] = 0.0;
        velocityY[track] = 0.0;
        positionVariance[track] = config.measurementVariance();
        positionVelocityCovariance[track] = 0.0;
        velocityVariance[track] = config.sigma() * config.sigma() / (2.0 * config.beta());
        timestampSeconds[track] = timestamp;
        initialized[track] = true;
        acceptedReports++;
        return true;
    }

    boolean update(int track, long timestamp, double measuredX, double measuredY) {
        checkTrack(track);
        requireFiniteMeasurement(measuredX, measuredY);
        if (!initialized[track]) {
            return initialize(track, timestamp, measuredX, measuredY);
        }
        long stateTimestamp = timestampSeconds[track];
        if (timestamp <= stateTimestamp) {
            rejectedReports++;
            return false;
        }
        long delta = timestamp - stateTimestamp;
        if (delta <= 0L || delta > 60L) {
            rejectedReports++;
            return false;
        }

        int coefficientOffset = Math.toIntExact((delta - 1L) * COEFFICIENT_COUNT);
        double a = integerCoefficients[coefficientOffset + IouModel.A];
        double e = integerCoefficients[coefficientOffset + IouModel.E];
        double predictedX = x[track] + a * velocityX[track];
        double predictedY = y[track] + a * velocityY[track];
        double p00 = positionVariance[track];
        double p01 = positionVelocityCovariance[track];
        double p11 = velocityVariance[track];
        double predictedP00 =
                p00
                        + 2.0 * a * p01
                        + a * a * p11
                        + integerCoefficients[coefficientOffset + IouModel.Q00];
        double predictedP01 =
                e * p01 + a * e * p11 + integerCoefficients[coefficientOffset + IouModel.Q01];
        double predictedP11 = e * e * p11 + integerCoefficients[coefficientOffset + IouModel.Q11];

        double innovationVariance = predictedP00 + config.measurementVariance();
        double positionGain = predictedP00 / innovationVariance;
        double velocityGain = predictedP01 / innovationVariance;
        double innovationX = measuredX - predictedX;
        double innovationY = measuredY - predictedY;
        double normalizedInnovation =
                (innovationX * innovationX + innovationY * innovationY) / innovationVariance;
        if (!Double.isFinite(normalizedInnovation)) {
            rejectedReports++;
            throw new IllegalStateException("IOU_INNOVATION_NON_FINITE");
        }
        double updatedX = predictedX + positionGain * innovationX;
        double updatedY = predictedY + positionGain * innovationY;
        double updatedVelocityX = e * velocityX[track] + velocityGain * innovationX;
        double updatedVelocityY = e * velocityY[track] + velocityGain * innovationY;
        if (!Double.isFinite(updatedX)
                || !Double.isFinite(updatedY)
                || !Double.isFinite(updatedVelocityX)
                || !Double.isFinite(updatedVelocityY)) {
            rejectedReports++;
            throw new IllegalStateException("IOU_STATE_NON_FINITE");
        }

        double residualPosition = 1.0 - positionGain;
        double measurementVariance = config.measurementVariance();
        double updatedP00 =
                residualPosition * residualPosition * predictedP00
                        + positionGain * positionGain * measurementVariance;
        double updatedP01 =
                residualPosition * (predictedP01 - velocityGain * predictedP00)
                        + positionGain * velocityGain * measurementVariance;
        double updatedP11 =
                predictedP11
                        - 2.0 * velocityGain * predictedP01
                        + velocityGain * velocityGain * predictedP00
                        + velocityGain * velocityGain * measurementVariance;
        validateAndStoreCovariance(track, updatedP00, updatedP01, updatedP11);
        x[track] = updatedX;
        y[track] = updatedY;
        velocityX[track] = updatedVelocityX;
        velocityY[track] = updatedVelocityY;
        timestampSeconds[track] = timestamp;
        acceptedReports++;
        innovationCount++;
        normalizedInnovationSum += normalizedInnovation;
        normalizedInnovationMaximum = Math.max(normalizedInnovationMaximum, normalizedInnovation);
        return true;
    }

    double displayX(int track, double displayTimestampSeconds) {
        checkDisplayTime(track, displayTimestampSeconds);
        return x[track] + transitionForDisplay(track, displayTimestampSeconds) * velocityX[track];
    }

    double displayY(int track, double displayTimestampSeconds) {
        checkDisplayTime(track, displayTimestampSeconds);
        return y[track] + transitionForDisplay(track, displayTimestampSeconds) * velocityY[track];
    }

    void copyDisplayPositions(
            double displayTimestampSeconds, double[] positionsX, double[] positionsY, int offset) {
        if (!Double.isFinite(displayTimestampSeconds)) {
            throw new IllegalArgumentException("display time must be finite");
        }
        if (offset < 0
                || offset + trackCount() > positionsX.length
                || offset + trackCount() > positionsY.length) {
            throw new IndexOutOfBoundsException("offset");
        }
        prepareDisplayTransitions(displayTimestampSeconds);
        long wholeSecond = (long) StrictMath.floor(displayTimestampSeconds);
        for (int track = 0; track < trackCount(); track++) {
            checkInitializedTrack(track);
            long age = wholeSecond - timestampSeconds[track];
            double delta = displayTimestampSeconds - timestampSeconds[track];
            if (age < 0L || age >= displayTransitions.length || delta < 0.0 || delta > 60.0) {
                throw new IllegalArgumentException(
                        "display time is outside [state time, state time + 60]");
            }
            double transition = displayTransitions[(int) age];
            positionsX[offset + track] = x[track] + transition * velocityX[track];
            positionsY[offset + track] = y[track] + transition * velocityY[track];
        }
    }

    double innovationVariance(int track, double measurementTimestampSeconds) {
        checkDisplayTime(track, measurementTimestampSeconds);
        double delta = measurementTimestampSeconds - timestampSeconds[track];
        if (delta == 0.0) {
            return positionVariance[track] + config.measurementVariance();
        }
        IouModel.coefficients(config, delta, displayCoefficients);
        double a = displayCoefficients[IouModel.A];
        double predictedP00 =
                positionVariance[track]
                        + 2.0 * a * positionVelocityCovariance[track]
                        + a * a * velocityVariance[track]
                        + displayCoefficients[IouModel.Q00];
        return predictedP00 + config.measurementVariance();
    }

    double x(int track) {
        checkInitializedTrack(track);
        return x[track];
    }

    double y(int track) {
        checkInitializedTrack(track);
        return y[track];
    }

    double velocityX(int track) {
        checkInitializedTrack(track);
        return velocityX[track];
    }

    double velocityY(int track) {
        checkInitializedTrack(track);
        return velocityY[track];
    }

    double positionVariance(int track) {
        checkInitializedTrack(track);
        return positionVariance[track];
    }

    double positionVelocityCovariance(int track) {
        checkInitializedTrack(track);
        return positionVelocityCovariance[track];
    }

    double velocityVariance(int track) {
        checkInitializedTrack(track);
        return velocityVariance[track];
    }

    long timestampSeconds(int track) {
        checkInitializedTrack(track);
        return timestampSeconds[track];
    }

    long acceptedReports() {
        return acceptedReports;
    }

    long rejectedReports() {
        return rejectedReports;
    }

    long innovationCount() {
        return innovationCount;
    }

    double normalizedInnovationSum() {
        return normalizedInnovationSum;
    }

    double normalizedInnovationMaximum() {
        return normalizedInnovationMaximum;
    }

    void resetEvidenceMetrics() {
        innovationCount = 0L;
        normalizedInnovationSum = 0.0;
        normalizedInnovationMaximum = 0.0;
    }

    long logicalBytes() {
        return Math.addExact(
                Math.multiplyExact((long) trackCount(), 7L * Double.BYTES + Long.BYTES + 1L),
                Math.multiplyExact(
                        (long) integerCoefficients.length
                                + displayCoefficients.length
                                + displayTransitions.length,
                        Double.BYTES));
    }

    void reset() {
        Arrays.fill(x, 0.0);
        Arrays.fill(y, 0.0);
        Arrays.fill(velocityX, 0.0);
        Arrays.fill(velocityY, 0.0);
        Arrays.fill(positionVariance, 0.0);
        Arrays.fill(positionVelocityCovariance, 0.0);
        Arrays.fill(velocityVariance, 0.0);
        Arrays.fill(timestampSeconds, 0L);
        Arrays.fill(initialized, false);
        Arrays.fill(displayCoefficients, 0.0);
        Arrays.fill(displayTransitions, 0.0);
        acceptedReports = 0L;
        rejectedReports = 0L;
        resetEvidenceMetrics();
    }

    private double transitionForDisplay(int track, double displayTimestampSeconds) {
        double delta = displayTimestampSeconds - timestampSeconds[track];
        if (delta == 0.0) {
            return 0.0;
        }
        int integerDelta = (int) delta;
        if (Double.doubleToLongBits(delta) == Double.doubleToLongBits((double) integerDelta)) {
            return integerCoefficients[(integerDelta - 1) * COEFFICIENT_COUNT + IouModel.A];
        }
        IouModel.coefficients(config, delta, displayCoefficients);
        return displayCoefficients[IouModel.A];
    }

    private void prepareDisplayTransitions(double displayTimestampSeconds) {
        double fraction = displayTimestampSeconds - StrictMath.floor(displayTimestampSeconds);
        for (int age = 0; age < displayTransitions.length; age++) {
            double delta = age + fraction;
            if (delta == 0.0) {
                displayTransitions[age] = 0.0;
            } else if (delta <= MAX_PREDICTION_SECONDS) {
                IouModel.coefficients(config, delta, displayCoefficients);
                displayTransitions[age] = displayCoefficients[IouModel.A];
            } else {
                displayTransitions[age] = Double.NaN;
            }
        }
    }

    private void checkDisplayTime(int track, double displayTimestampSeconds) {
        checkInitializedTrack(track);
        double delta = displayTimestampSeconds - timestampSeconds[track];
        if (!Double.isFinite(displayTimestampSeconds)
                || delta < 0.0
                || delta > MAX_PREDICTION_SECONDS) {
            throw new IllegalArgumentException(
                    "display time is outside [state time, state time + 60]");
        }
    }

    private void validateAndStoreCovariance(int track, double p00, double p01, double p11) {
        if (!Double.isFinite(p00) || !Double.isFinite(p01) || !Double.isFinite(p11)) {
            throw new IllegalStateException("IOU_COVARIANCE_NON_FINITE");
        }
        double diagonalScale = Math.max(1.0, Math.max(Math.abs(p00), Math.abs(p11)));
        double diagonalTolerance = 64.0 * Math.ulp(diagonalScale);
        if (p00 < -diagonalTolerance || p11 < -diagonalTolerance) {
            throw new IllegalStateException("IOU_COVARIANCE_NOT_PSD");
        }
        p00 = p00 < 0.0 ? 0.0 : p00;
        p11 = p11 < 0.0 ? 0.0 : p11;
        double determinant = p00 * p11 - p01 * p01;
        double determinantScale = Math.max(1.0, Math.abs(p00 * p11) + p01 * p01);
        double determinantTolerance = 64.0 * Math.ulp(determinantScale);
        if (determinant < -determinantTolerance) {
            throw new IllegalStateException("IOU_COVARIANCE_NOT_PSD");
        }
        if (determinant < 0.0) {
            p01 = p00 == 0.0 || p11 == 0.0 ? 0.0 : Math.copySign(StrictMath.sqrt(p00 * p11), p01);
        }
        positionVariance[track] = p00;
        positionVelocityCovariance[track] = p01;
        velocityVariance[track] = p11;
    }

    private void checkInitializedTrack(int track) {
        checkTrack(track);
        if (!initialized[track]) {
            throw new IllegalStateException("track is not initialized");
        }
    }

    private void checkTrack(int track) {
        if (track < 0 || track >= x.length) {
            throw new IndexOutOfBoundsException("track is outside packed storage");
        }
    }

    private static void requireFiniteMeasurement(double measuredX, double measuredY) {
        if (!Double.isFinite(measuredX) || !Double.isFinite(measuredY)) {
            throw new IllegalArgumentException("measurement coordinates must be finite");
        }
    }
}
