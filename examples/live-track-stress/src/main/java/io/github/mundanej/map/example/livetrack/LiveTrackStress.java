package io.github.mundanej.map.example.livetrack;

/** Entry point for the staged live-track stress example. */
public final class LiveTrackStress {
    private LiveTrackStress() {}

    /**
     * Runs the current estimator-only slice.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        PackedIouKalmanFilter filter = new PackedIouKalmanFilter(1, IouKalmanConfig.REFERENCE);
        filter.initialize(0, 0L, 1_000.0, 2_000.0);
        filter.update(0, 10L, 1_250.0, 1_900.0);
        System.out.printf(
                "IOU-Kalman slice: x=%.3f y=%.3f accepted=%d rejected=%d%n",
                filter.x(0), filter.y(0), filter.acceptedReports(), filter.rejectedReports());
    }
}
