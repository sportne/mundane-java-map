package io.github.mundanej.map.example.livetrack;

record TrackSimulationConfig(int population, long seed, int workers, IouKalmanConfig filterConfig) {
    static final long REFERENCE_SEED = 0x4d554e44414e454cL;

    TrackSimulationConfig {
        if (population < 1 || population > 1_000_000) {
            throw new IllegalArgumentException("population is outside [1, 1000000]");
        }
        if (workers < 1 || workers > Math.min(32, population)) {
            throw new IllegalArgumentException("workers is outside the approved range");
        }
        if (filterConfig == null) {
            throw new NullPointerException("filterConfig");
        }
    }

    static TrackSimulationConfig reference(int population, int workers) {
        return new TrackSimulationConfig(
                population, REFERENCE_SEED, workers, IouKalmanConfig.REFERENCE);
    }

    static int defaultWorkers(int population) {
        return Math.min(Math.min(8, Runtime.getRuntime().availableProcessors()), population);
    }
}
