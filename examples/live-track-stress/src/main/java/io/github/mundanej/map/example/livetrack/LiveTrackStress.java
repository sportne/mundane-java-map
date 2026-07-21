package io.github.mundanej.map.example.livetrack;

/** Entry point for the staged live-track stress example. */
public final class LiveTrackStress {
    private LiveTrackStress() {}

    /**
     * Runs the current deterministic 10,000-track headless slice.
     *
     * @param args ignored command-line arguments
     */
    public static void main(String[] args) {
        int population = 10_000;
        int workers = TrackSimulationConfig.defaultWorkers(population);
        try (LiveTrackCoordinator coordinator =
                new LiveTrackCoordinator(TrackSimulationConfig.reference(population, workers))) {
            coordinator.start(0L);
            coordinator.advanceTo(120);
            System.out.printf(
                    "Live-track slice: population=%d workers=%d seconds=%d reports=%d checksum=%016x%n",
                    population,
                    workers,
                    coordinator.simulationSecond(),
                    coordinator.processedReports(),
                    coordinator.checksum());
        }
    }
}
