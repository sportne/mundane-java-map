package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.geojson.GeoJsonFiles;
import io.github.mundanej.map.io.geojson.GeoJsonOpenOptions;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeScenarioCoverageTest {
    @TempDir Path temporaryDirectory;

    @Test
    void geoJsonCountFailureAndCleanupRemainDeterministic() throws Exception {
        byte[] empty =
                "{\"type\":\"FeatureCollection\",\"features\":[]}".getBytes(StandardCharsets.UTF_8);
        try (FeatureSource source =
                GeoJsonFiles.open(
                        empty,
                        new SourceIdentity("empty", "Empty"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none())) {
            IllegalStateException failure =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    invokePrivate(
                                            NativeGeoJsonSmokeScenario.class,
                                            "assertRecords",
                                            new Class<?>[] {FeatureSource.class, int.class},
                                            source,
                                            1));
            assertTrue(failure.getMessage().contains("query count"));
        }

        Path cleanup = temporaryDirectory.resolve("geojson-cleanup");
        Files.createDirectories(cleanup);
        Files.writeString(cleanup.resolve("round-trip.geojson"), "{}");
        Files.writeString(cleanup.resolve("round-trip-second.geojson"), "{}");
        invokePrivate(
                NativeGeoJsonSmokeScenario.class, "cleanup", new Class<?>[] {Path.class}, cleanup);
        assertFalse(Files.exists(cleanup));
    }

    @Test
    void gpxMalformedFixtureWriteFailureKeepsStableCause() throws Exception {
        Path existing = temporaryDirectory.resolve("malformed-native.gpx");
        Files.writeString(existing, "occupied");
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                invokePrivate(
                                        NativeGpxSmokeScenario.class,
                                        "malformed",
                                        new Class<?>[] {Path.class},
                                        existing));
        assertTrue(failure.getMessage().contains("fixture write failed"));
        assertFalse(Files.exists(existing));

        Path noIgnoredField = temporaryDirectory.resolve("no-ignored-field.gpx");
        Files.writeString(
                noIgnoredField,
                """
                <gpx xmlns="http://www.topografix.com/GPX/1/1"
                     version="1.1" creator="coverage">
                  <wpt lat="0" lon="0"/>
                  <wpt lat="1" lon="1"/>
                  <wpt lat="2" lon="2"/>
                </gpx>
                """);
        IllegalStateException warning =
                assertThrows(
                        IllegalStateException.class,
                        () -> NativeGpxSmokeScenario.run(noIgnoredField));
        assertTrue(warning.getMessage().contains("warning is missing"));
    }

    @Test
    void svgScenarioAlsoRunsWhenAlreadyOnTheEventThread() throws Exception {
        SwingUtilities.invokeAndWait(NativeSvgSmokeScenario::run);

        Path nonempty = temporaryDirectory.resolve("nonempty");
        Files.createDirectory(nonempty);
        Files.writeString(nonempty.resolve("child"), "retained");
        Exception primary = new Exception("primary");
        invokePrivate(
                NativeSvgSmokeScenario.class,
                "deleteAfterFailure",
                new Class<?>[] {Path.class, Exception.class},
                nonempty,
                primary);
        assertEquals(1, primary.getSuppressed().length);
    }

    private static Object invokePrivate(
            Class<?> owner, String name, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = owner.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException exception) {
            if (exception.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (exception.getCause() instanceof Error error) {
                throw error;
            }
            throw new AssertionError(exception.getCause());
        } catch (ReflectiveOperationException exception) {
            throw new LinkageError(exception.getMessage(), exception);
        }
    }
}
