package io.github.mundanej.map.example.livetrack;

/** Entry point for the staged live-track stress example. */
public final class LiveTrackStress {
    static final String USAGE =
            "Usage: live-track-stress "
                    + "[--chart|--headless|"
                    + "--population=<10000|100000|1000000> --seed=<long> --workers=<1..32> "
                    + "--report-profile=reference --fps=<0|1|2|5|10|15|30|60>]";

    private LiveTrackStress() {}

    /**
     * Opens the 10,000-track reference picture by default, a configured supported scale tier, the
     * land chart with {@code --chart}, or the deterministic simulator-only slice with {@code
     * --headless}.
     *
     * @param args an optional exclusive mode or viewer configuration options
     */
    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--chart")) {
            NaturalEarthChart.launch();
            return;
        }
        if (args.length == 1 && args[0].equals("--headless")) {
            runHeadless();
            return;
        }
        LiveTrackViewer.launch(parseViewerConfiguration(args));
    }

    static LiveTrackViewer.ViewerConfiguration parseViewerConfiguration(String[] args) {
        int population = 10_000;
        Long seed = null;
        Integer workers = null;
        int fps = 10;
        String reportProfile = "reference";
        boolean populationSet = false;
        boolean seedSet = false;
        boolean workersSet = false;
        boolean fpsSet = false;
        boolean profileSet = false;
        try {
            for (String argument : args) {
                if (argument.startsWith("--population=")) {
                    requireUnset(populationSet);
                    population = Integer.parseInt(value(argument, "--population="));
                    populationSet = true;
                } else if (argument.startsWith("--seed=")) {
                    requireUnset(seedSet);
                    seed = Long.decode(value(argument, "--seed="));
                    seedSet = true;
                } else if (argument.startsWith("--workers=")) {
                    requireUnset(workersSet);
                    workers = Integer.parseInt(value(argument, "--workers="));
                    workersSet = true;
                } else if (argument.startsWith("--report-profile=")) {
                    requireUnset(profileSet);
                    reportProfile = value(argument, "--report-profile=");
                    profileSet = true;
                } else if (argument.startsWith("--fps=")) {
                    requireUnset(fpsSet);
                    fps = Integer.parseInt(value(argument, "--fps="));
                    fpsSet = true;
                } else {
                    throw new IllegalArgumentException("unknown option");
                }
            }
            LiveTrackViewer.requireViewerPopulation(population);
            int selectedWorkers =
                    workers == null ? TrackSimulationConfig.defaultWorkers(population) : workers;
            TrackSimulationConfig reference =
                    TrackSimulationConfig.reference(population, selectedWorkers);
            TrackSimulationConfig simulation =
                    new TrackSimulationConfig(
                            reference.population(),
                            seed == null ? TrackSimulationConfig.REFERENCE_SEED : seed,
                            selectedWorkers,
                            reference.filterConfig());
            return new LiveTrackViewer.ViewerConfiguration(simulation, fps, reportProfile);
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException(USAGE, failure);
        }
    }

    private static void runHeadless() {
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

    private static String value(String argument, String prefix) {
        String value = argument.substring(prefix.length());
        if (value.isEmpty()) {
            throw new IllegalArgumentException("missing option value");
        }
        return value;
    }

    private static void requireUnset(boolean alreadySet) {
        if (alreadySet) {
            throw new IllegalArgumentException("duplicate option");
        }
    }
}
