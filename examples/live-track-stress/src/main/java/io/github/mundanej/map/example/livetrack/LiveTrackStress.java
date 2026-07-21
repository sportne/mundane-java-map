package io.github.mundanej.map.example.livetrack;

/** Entry point for the staged live-track stress example. */
public final class LiveTrackStress {
    private LiveTrackStress() {}

    /**
     * Opens the global chart when invoked with {@code --chart}; otherwise runs the deterministic
     * 10,000-track headless slice.
     *
     * @param args no arguments or the single {@code --chart} option
     */
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--chart")) {
            NaturalEarthChart.launch();
            return;
        }
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: live-track-stress [--chart]");
        }
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
