package io.github.mundanej.map.performance;

@FunctionalInterface
interface ScenarioOracle {
    void verify(EvidenceObservation observation);
}
