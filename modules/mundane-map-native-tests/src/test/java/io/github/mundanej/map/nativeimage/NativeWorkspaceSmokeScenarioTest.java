package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class NativeWorkspaceSmokeScenarioTest {
    @Test
    void runsTheExactWorkspaceScenarioUsedByTheNativeExecutable() {
        NativeWorkspaceSmokeScenario.run();
    }

    @Test
    void closesTheOpenedSourceWhenTheScenarioFails() {
        RuntimeException primary = new IllegalStateException("primary");
        AtomicReference<FeatureSource> source = new AtomicReference<>();

        RuntimeException actual =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                NativeWorkspaceSmokeScenario.run(
                                        session -> {
                                            source.set(
                                                    ((OpenedWorkspaceFeatureLayer)
                                                                    session.layers().getFirst())
                                                            .source());
                                            throw primary;
                                        },
                                        NativeWorkspaceSmokeScenario::deleteTree));

        assertSame(primary, actual);
        assertTrue(source.get().isClosed());
    }

    @Test
    void preservesTheScenarioFailureWhenCleanupAlsoFails() {
        RuntimeException primary = new IllegalStateException("primary");
        RuntimeException cleanup = new IllegalStateException("cleanup");

        RuntimeException actual =
                assertThrows(
                        RuntimeException.class,
                        () ->
                                NativeWorkspaceSmokeScenario.run(
                                        ignored -> {
                                            throw primary;
                                        },
                                        directory -> {
                                            NativeWorkspaceSmokeScenario.deleteTree(directory);
                                            throw cleanup;
                                        }));

        assertSame(primary, actual);
        assertSame(cleanup, actual.getSuppressed()[0]);
    }
}
