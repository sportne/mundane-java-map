package io.github.mundanej.map.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

final class EvidenceRunner {
    private static volatile long consumer;

    EvidenceReport run(EvidenceConfiguration configuration, List<EvidenceScenario> declared)
            throws Exception {
        List<EvidenceScenario> selected =
                configuration
                        .scenario()
                        .map(id -> declared.stream().filter(item -> item.id().equals(id)).toList())
                        .orElse(declared);
        List<EvidenceSample> samples = new ArrayList<>();
        EvidenceReport report = null;
        Throwable primary = null;
        try {
            for (EvidenceScenario scenario : selected) {
                samples.add(runScenario(configuration, scenario));
            }
            samples = addObservedCrossover(configuration, samples);
            report = EvidenceReport.capture(configuration, selected, samples);
        } catch (Throwable failure) {
            primary = failure;
        } finally {
            for (int index = declared.size() - 1; index >= 0; index--) {
                try {
                    declared.get(index).close();
                } catch (Throwable failure) {
                    if (primary == null) {
                        primary = failure;
                    } else {
                        primary.addSuppressed(failure);
                    }
                }
            }
        }
        if (primary != null) {
            throwAsException(primary);
        }
        return java.util.Objects.requireNonNull(report, "report");
    }

    private static List<EvidenceSample> addObservedCrossover(
            EvidenceConfiguration configuration, List<EvidenceSample> samples) {
        if (configuration.scenario().isPresent()) {
            return samples;
        }
        java.util.LinkedHashMap<String, EvidenceSample> byId = new java.util.LinkedHashMap<>();
        samples.forEach(sample -> byId.put(sample.scenarioId(), sample));
        if (!byId.containsKey("index-query-linear-32")) {
            return samples;
        }
        long crossover = 0;
        for (int size : IndexComparisonFixture.SIZES) {
            EvidenceSample linear = requireSample(byId, "index-query-linear-" + size);
            EvidenceSample indexed = requireSample(byId, "index-query-str16-" + size);
            if (crossover == 0 && indexed.medianNanos() < linear.medianNanos()) {
                crossover = size;
            }
        }
        List<EvidenceSample> result = new ArrayList<>(samples.size());
        for (EvidenceSample sample : samples) {
            if (sample.scenarioId().startsWith("index-query-str16-")) {
                java.util.LinkedHashMap<String, Long> counters =
                        new java.util.LinkedHashMap<>(sample.observation().counters());
                counters.put("observedCrossoverRecords", crossover);
                result.add(
                        sample.withObservation(
                                new EvidenceObservation(sample.observation().digest(), counters)));
            } else {
                result.add(sample);
            }
        }
        return List.copyOf(result);
    }

    private static EvidenceSample requireSample(
            java.util.Map<String, EvidenceSample> samples, String id) {
        EvidenceSample result = samples.get(id);
        if (result == null) {
            throw new IllegalStateException("Missing crossover scenario: " + id);
        }
        return result;
    }

    private static EvidenceSample runScenario(
            EvidenceConfiguration configuration, EvidenceScenario scenario) throws Exception {
        if (scenario.batchOperations() <= 0) {
            throw new IllegalArgumentException("Batch operations must be positive");
        }
        scenario.setupScenario();
        for (int index = 0; index < configuration.warmups(); index++) {
            measureAndFinish(scenario, null);
        }
        long[] rawNanos = new long[configuration.measurements()];
        EvidenceObservation reference = null;
        for (int index = 0; index < rawNanos.length; index++) {
            ObservedTiming timed = measureAndFinish(scenario, reference);
            EvidenceObservation observation = timed.observation();
            rawNanos[index] = timed.nanos();
            reference = observation;
        }
        return EvidenceSample.of(scenario.id(), rawNanos, scenario.batchOperations(), reference);
    }

    private static ObservedTiming measureAndFinish(
            EvidenceScenario scenario, EvidenceObservation reference) throws Exception {
        Throwable primary = null;
        try {
            scenario.prepareSample();
            TimedObservation timed = measure(scenario);
            EvidenceObservation observation = scenario.observeSample();
            scenario.oracle().verify(observation);
            if (reference != null && !reference.equals(observation)) {
                throw new IllegalStateException("Scenario observation changed: " + scenario.id());
            }
            consume(observation);
            return new ObservedTiming(observation, timed.nanos());
        } catch (Throwable failure) {
            primary = failure;
            throwAsException(failure);
            throw new AssertionError("unreachable");
        } finally {
            try {
                scenario.finishSample();
            } catch (Throwable failure) {
                if (primary == null) {
                    throwAsException(failure);
                } else {
                    primary.addSuppressed(failure);
                }
            }
        }
    }

    private static TimedObservation measure(EvidenceScenario scenario) throws Exception {
        if (!scenario.runsOnEdt()) {
            long started = System.nanoTime();
            scenario.runTimedBatch();
            return new TimedObservation(elapsed(started));
        }
        AtomicReference<TimedObservation> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    long started = System.nanoTime();
                    try {
                        scenario.runTimedBatch();
                        result.set(new TimedObservation(elapsed(started)));
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                    }
                });
        if (failure.get() != null) {
            throwAsException(failure.get());
        }
        return result.get();
    }

    private static long elapsed(long started) {
        long result = System.nanoTime() - started;
        if (result <= 0) {
            throw new IllegalStateException("Measured nanos must be positive");
        }
        return result;
    }

    private static synchronized void consume(EvidenceObservation observation) {
        consumer ^= observation.digest();
        for (long value : observation.counters().values()) {
            consumer = Long.rotateLeft(consumer ^ value, 7);
        }
    }

    private static void throwAsException(Throwable failure) throws Exception {
        if (failure instanceof Exception exception) {
            throw exception;
        }
        throw (Error) failure;
    }

    private record ObservedTiming(EvidenceObservation observation, long nanos) {}
}
