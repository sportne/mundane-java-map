package io.github.mundanej.map.performance;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TimeZone;
import java.util.regex.Pattern;

record EvidenceConfiguration(
        Profile profile,
        long seed,
        int warmups,
        int measurements,
        Optional<String> scenario,
        Optional<String> revision,
        boolean investigation) {
    static final long SEED = 0x4d554e44414e454aL;
    static final List<String> JVM_SETTINGS =
            List.of(
                    "-Xms512m",
                    "-Xmx512m",
                    "-XX:+UseG1GC",
                    "-Djava.awt.headless=true",
                    "-Dfile.encoding=UTF-8",
                    "-Duser.language=en",
                    "-Duser.country=US",
                    "-Duser.timezone=UTC");
    private static final Pattern DECIMAL = Pattern.compile("0|[1-9][0-9]*");
    private static final Pattern REVISION = Pattern.compile("[0-9a-f]{7,64}");

    static EvidenceConfiguration system(List<String> declaredScenarios) {
        Profile profile = Profile.valueOf(System.getProperty("performanceProfile", "BASELINE"));
        Optional<String> scenario = Optional.ofNullable(System.getProperty("performanceScenario"));
        scenario.ifPresent(
                value -> {
                    if (!declaredScenarios.contains(value)) {
                        throw new IllegalArgumentException("Unknown performanceScenario: " + value);
                    }
                });
        String warmupProperty = System.getProperty("performanceWarmups");
        String measurementProperty = System.getProperty("performanceMeasurements");
        int warmups = parse(warmupProperty, profile.warmups(), 0, 100, "performanceWarmups");
        int measurements =
                parse(
                        measurementProperty,
                        profile.measurements(),
                        1,
                        100,
                        "performanceMeasurements");
        String explicitRevision = System.getProperty("performanceRevision");
        String revisionValue =
                explicitRevision != null ? explicitRevision : System.getProperty("GITHUB_SHA");
        Optional<String> revision = Optional.ofNullable(revisionValue);
        revision.ifPresent(
                value -> {
                    if (!REVISION.matcher(value).matches()) {
                        throw new IllegalArgumentException("Invalid performance revision");
                    }
                });
        verifyRuntime();
        boolean investigation =
                scenario.isPresent() || warmupProperty != null || measurementProperty != null;
        return new EvidenceConfiguration(
                profile, SEED, warmups, measurements, scenario, revision, investigation);
    }

    private static int parse(String value, int fallback, int minimum, int maximum, String name) {
        if (value == null) {
            return fallback;
        }
        if (!DECIMAL.matcher(value).matches()) {
            throw new IllegalArgumentException(name + " must be an ASCII decimal integer");
        }
        long parsed = Long.parseLong(value);
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException(name + " is outside its supported range");
        }
        return Math.toIntExact(parsed);
    }

    private static void verifyRuntime() {
        long expectedHeap = 512L * 1_024 * 1_024;
        java.lang.management.MemoryUsage heap =
                java.lang.management.ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        boolean g1 =
                java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .map(java.lang.management.GarbageCollectorMXBean::getName)
                        .anyMatch(name -> name.contains("G1"));
        if (!"21".equals(System.getProperty("java.specification.version"))) {
            throw new IllegalStateException("Performance evidence requires Java 21");
        }
        if (!"UTF-8".equalsIgnoreCase(System.getProperty("file.encoding"))
                || !Locale.US.equals(Locale.getDefault())
                || !"UTC".equals(TimeZone.getDefault().getID())
                || !Boolean.parseBoolean(System.getProperty("java.awt.headless"))
                || heap.getInit() != expectedHeap
                || heap.getMax() != expectedHeap
                || !g1) {
            throw new IllegalStateException("Performance evidence runtime settings do not match");
        }
    }

    enum Profile {
        BASELINE(5, 20),
        SMOKE(1, 2);

        private final int warmups;
        private final int measurements;

        Profile(int warmups, int measurements) {
            this.warmups = warmups;
            this.measurements = measurements;
        }

        int warmups() {
            return warmups;
        }

        int measurements() {
            return measurements;
        }
    }
}
