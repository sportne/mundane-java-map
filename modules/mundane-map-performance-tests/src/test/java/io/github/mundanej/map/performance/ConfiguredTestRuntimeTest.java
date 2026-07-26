package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ConfiguredTestRuntimeTest {
    @Test
    void normalSuiteUsesConfiguredTestRuntime() {
        assertEquals(
                Integer.parseInt(System.getProperty("map.testJavaVersion")),
                Runtime.version().feature());
    }
}
