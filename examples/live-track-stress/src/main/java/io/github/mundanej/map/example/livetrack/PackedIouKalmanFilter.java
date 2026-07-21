package io.github.mundanej.map.example.livetrack;

import java.util.Arrays;

final class PackedIouKalmanFilter {
    private static final int MAX_TRACKS = 1_000_000;
    private static final double MAX_PREDICTION_SECONDS = 60.0;

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
    private final double[] coefficients = new double[5];
    private long acceptedReports;
    private long rejectedReports;

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

        IouModel.coefficients(config, (double) delta, coefficients);
        double a = coefficients[IouModel.A];
        double e = coefficients[IouModel.E];
        double predictedX = x[track] + a * velocityX[track];
        double predictedY = y[track] + a * velocityY[track];
        double p00 = positionVariance[track];
        double p01 = positionVelocityCovariance[track];
        double p11 = velocityVariance[track];
        double predictedP00 = p00 + 2.0 * a * p01 + a * a * p11 + coefficients[IouModel.Q00];
        double predictedP01 = e * p01 + a * e * p11 + coefficients[IouModel.Q01];
        double predictedP11 = e * e * p11 + coefficients[IouModel.Q11];

        double innovationVariance = predictedP00 + config.measurementVariance();
        double positionGain = predictedP00 / innovationVariance;
        double velocityGain = predictedP01 / innovationVariance;
        double innovationX = measuredX - predictedX;
        double innovationY = measuredY - predictedY;
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

    double innovationVariance(int track, double measurementTimestampSeconds) {
        checkDisplayTime(track, measurementTimestampSeconds);
        double delta = measurementTimestampSeconds - timestampSeconds[track];
        if (delta == 0.0) {
            return positionVariance[track] + config.measurementVariance();
        }
        IouModel.coefficients(config, delta, coefficients);
        double a = coefficients[IouModel.A];
        double predictedP00 =
                positionVariance[track]
                        + 2.0 * a * positionVelocityCovariance[track]
                        + a * a * velocityVariance[track]
                        + coefficients[IouModel.Q00];
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

    long logicalBytes() {
        return Math.addExact(
                Math.multiplyExact((long) trackCount(), 7L * Double.BYTES + Long.BYTES + 1L),
                (long) coefficients.length * Double.BYTES);
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
        Arrays.fill(coefficients, 0.0);
        acceptedReports = 0L;
        rejectedReports = 0L;
    }

    private double transitionForDisplay(int track, double displayTimestampSeconds) {
        double delta = displayTimestampSeconds - timestampSeconds[track];
        if (delta == 0.0) {
            return 0.0;
        }
        IouModel.coefficients(config, delta, coefficients);
        return coefficients[IouModel.A];
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
