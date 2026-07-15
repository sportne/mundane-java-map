package io.github.mundanej.map.performance;

record TimedObservation(long nanos) {
    TimedObservation {
        if (nanos <= 0) {
            throw new IllegalArgumentException("Measured nanos must be positive");
        }
    }
}
