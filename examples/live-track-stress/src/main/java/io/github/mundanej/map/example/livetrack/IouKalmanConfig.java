package io.github.mundanej.map.example.livetrack;

/** Validated scalar parameters shared by every estimator in one run. */
record IouKalmanConfig(double beta, double sigma, double measurementStandardDeviation) {
    static final IouKalmanConfig REFERENCE = new IouKalmanConfig(0.05, 20.0, 5_000.0);

    IouKalmanConfig {
        requireFiniteRange(beta, 1.0e-6, 1.0, "beta");
        requireFiniteRange(sigma, Math.nextUp(0.0), 1_000.0, "sigma");
        requireFiniteRange(
                measurementStandardDeviation,
                Math.nextUp(0.0),
                1_000_000.0,
                "measurementStandardDeviation");
    }

    double measurementVariance() {
        return measurementStandardDeviation * measurementStandardDeviation;
    }

    private static void requireFiniteRange(
            double value, double minimum, double maximum, String name) {
        if (!Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " is outside the approved finite range");
        }
    }
}
