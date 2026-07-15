package io.github.mundanej.map.performance;

interface EvidenceScenario extends AutoCloseable {
    String id();

    String nextExperiment();

    long batchOperations();

    String workUnit();

    String sourceCacheState();

    default String viewCacheState() {
        return "NONE";
    }

    default void setupScenario() throws Exception {}

    void prepareSample() throws Exception;

    void runTimedBatch() throws Exception;

    EvidenceObservation observeSample() throws Exception;

    default boolean runsOnEdt() {
        return false;
    }

    default void finishSample() throws Exception {}

    ScenarioOracle oracle();

    @Override
    default void close() {}
}
