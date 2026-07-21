package io.github.mundanej.map.example.livetrack;

/** Entry point for the staged live-track stress example. */
public final class LiveTrackStress {
    private LiveTrackStress() {}

    /**
     * Opens the 10,000-track live picture by default, a scale tier with {@code --population=100000}
     * or {@code --population=1000000}, the land chart with {@code --chart}, or the deterministic
     * simulator-only slice with {@code --headless}.
     *
     * @param args no arguments or one of {@code --chart}, {@code --headless}, and {@code
     *     --population=100000}, and {@code --population=1000000}
     */
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--chart")) {
            NaturalEarthChart.launch();
            return;
        }
        if (args.length == 0) {
            LiveTrackViewer.launch();
            return;
        }
        if (args.length == 1 && args[0].equals("--population=100000")) {
            LiveTrackViewer.launch(100_000);
            return;
        }
        if (args.length == 1 && args[0].equals("--population=1000000")) {
            LiveTrackViewer.launch(1_000_000);
            return;
        }
        if (args.length != 1 || !args[0].equals("--headless")) {
            throw new IllegalArgumentException(
                    "Usage: live-track-stress "
                            + "[--chart|--headless|--population=100000|--population=1000000]");
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
