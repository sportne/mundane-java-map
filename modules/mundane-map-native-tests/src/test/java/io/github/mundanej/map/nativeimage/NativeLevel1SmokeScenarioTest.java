package io.github.mundanej.map.nativeimage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.DistanceResult;
import io.github.mundanej.map.api.MeasurementPhase;
import io.github.mundanej.map.api.MeasurementState;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.DistanceStrategies;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class NativeLevel1SmokeScenarioTest {
    @Test
    void finalAggregateScenarioExercisesSemanticAndLifecycleResults() {
        NativeLevel1SmokeScenario.Result result = NativeLevel1SmokeScenario.run();

        assertEquals(5_000.0, result.planar().metres());
        assertEquals(
                NativeLevel1SmokeAssertions.GEOGRAPHIC_METRES,
                result.geographic().metres(),
                1.0e-6);
        NativeLevel1SmokeAssertions.verifyPreview(result.preview());
        NativeLevel1SmokeAssertions.verifyComplete(result.complete());
        assertTrue(result.renderSummary().nonWhitePixels() >= 200);
        assertTrue(result.toolReusedAfterClose());
    }

    @Test
    void aggregateSmokeCanRunTwiceWithoutRetainedState() {
        NativeSmokeMain.runSmoke();
        NativeSmokeMain.runSmoke();
    }

    @Test
    void assertionsRejectMutatedDiagnosticsDistancesStatesAndPixels() {
        assertInvariant(
                "registration-diagnostic",
                () ->
                        NativeLevel1SmokeAssertions.verifyDuplicateRenderer(
                                new SymbolException(
                                        SymbolException.RENDERER_NOT_REGISTERED,
                                        "mutated",
                                        Map.of(
                                                "role",
                                                "MARKER",
                                                "key",
                                                NativeLevel1SmokeScenario.RENDERER_KEY.value()))));
        assertInvariant(
                "measurement-planar",
                () ->
                        NativeLevel1SmokeAssertions.verifyPlanar(
                                DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857),
                                CrsDefinitions.EPSG_3857,
                                new DistanceResult(4_999.0)));
        MeasurementState movedPreview =
                new MeasurementState(
                        MeasurementPhase.MEASURING,
                        new double[] {1.0, 0.0},
                        Optional.of(new Coordinate(3_000.0, 4_000.0)),
                        DistanceResult.ZERO,
                        Optional.empty(),
                        Optional.of(new DistanceResult(5_000.0)));
        assertInvariant(
                "measurement-preview",
                () -> NativeLevel1SmokeAssertions.verifyPreview(movedPreview));

        int[] first = whitePixels();
        int[] second = first.clone();
        second[0] = 0xff000000;
        assertInvariant(
                "final-render-repeat",
                () -> NativeLevel1SmokeAssertions.verifyRender(first, second, 192, 160));
    }

    @Test
    void duplicateDiagnosticRejectsReversedContextIterationOrder() {
        LinkedHashMap<String, String> reversed = new LinkedHashMap<>();
        reversed.put("key", NativeLevel1SmokeScenario.RENDERER_KEY.value());
        reversed.put("role", "MARKER");

        assertInvariant(
                "registration-diagnostic",
                () ->
                        NativeLevel1SmokeAssertions.verifyDuplicateRenderer(
                                new SymbolException(
                                        SymbolException.RENDERER_DUPLICATE, "mutated", reversed)));
    }

    @Test
    void renderMutationControlsReachEveryObservableProbeAndBound() {
        int[] line = whitePixels();
        paintProbe(line, 1, 130, 0xff187060);
        paintProbe(line, 46, 130, 0xff187060);
        clearProbe(line, 46, 130);
        assertInvariant(
                "final-render-line",
                () -> NativeLevel1SmokeAssertions.verifyLineProbes(line, 192, 160));

        int[] marker = whitePixels();
        assertInvariant(
                "final-render-marker",
                () -> NativeLevel1SmokeAssertions.verifyMarkerProbe(marker, 192, 160));

        int[] measurement = whitePixels();
        assertInvariant(
                "measurement-render",
                () -> NativeLevel1SmokeAssertions.verifyMeasurementProbe(measurement, 192));
        assertInvariant(
                "measurement-label-render",
                () ->
                        NativeLevel1SmokeAssertions.verifyMeasurementLabelProbe(
                                measurement, 192, 160));

        int[] blank = whitePixels();
        blank[148 * 192 + 184] = 0xff000000;
        assertInvariant(
                "final-render-repeat",
                () -> NativeLevel1SmokeAssertions.verifyBlankProbe(blank, 192, 160));

        int[] outOfBounds = whitePixels();
        Arrays.fill(outOfBounds, 0, 199, 0xff000000);
        outOfBounds[137 * 192] = 0xff000000;
        assertInvariant(
                "final-render-repeat",
                () -> NativeLevel1SmokeAssertions.verifyNonWhiteBounds(outOfBounds, 192, 160));
    }

    @Test
    void geographicAndCompletedStateMutationControlsReachSemanticAssertions() {
        assertInvariant(
                "measurement-geographic",
                () ->
                        NativeLevel1SmokeAssertions.verifyGeographic(
                                DistanceStrategies.epsg4326GreatCircle(CrsDefinitions.EPSG_4326),
                                CrsDefinitions.EPSG_4326,
                                new DistanceResult(
                                        NativeLevel1SmokeAssertions.GEOGRAPHIC_METRES + 1.0)));

        assertInvariant(
                "measurement-state",
                () ->
                        NativeLevel1SmokeAssertions.verifyComplete(
                                completeState(
                                        MeasurementPhase.MEASURING,
                                        new double[] {0, 0, 3_000, 4_000, 6_000, 0},
                                        10_000)));
        assertInvariant(
                "measurement-state",
                () ->
                        NativeLevel1SmokeAssertions.verifyComplete(
                                completeState(
                                        MeasurementPhase.COMPLETE,
                                        new double[] {0, 0, 3_000, 4_000},
                                        10_000)));
        assertInvariant(
                "measurement-state",
                () ->
                        NativeLevel1SmokeAssertions.verifyComplete(
                                completeState(
                                        MeasurementPhase.COMPLETE,
                                        new double[] {0, 0, 3_001, 4_000, 6_000, 0},
                                        10_000)));
        assertInvariant(
                "measurement-state",
                () ->
                        NativeLevel1SmokeAssertions.verifyComplete(
                                completeState(
                                        MeasurementPhase.COMPLETE,
                                        new double[] {0, 0, 3_000, 4_000, 6_000, 0},
                                        9_999)));
    }

    private static MeasurementState completeState(
            MeasurementPhase phase, double[] vertices, double committedMetres) {
        return new MeasurementState(
                phase,
                vertices,
                Optional.empty(),
                new DistanceResult(committedMetres),
                Optional.of(new DistanceResult(5_000)),
                Optional.empty());
    }

    private static int[] whitePixels() {
        int[] pixels = new int[192 * 160];
        Arrays.fill(pixels, 0xffffffff);
        return pixels;
    }

    private static void paintProbe(int[] pixels, int centerX, int centerY, int color) {
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            Arrays.fill(pixels, y * 192 + centerX - 1, y * 192 + centerX + 2, color);
        }
    }

    private static void clearProbe(int[] pixels, int centerX, int centerY) {
        paintProbe(pixels, centerX, centerY, 0xffffffff);
    }

    private static void assertInvariant(String expected, Runnable operation) {
        IllegalStateException failure = assertThrows(IllegalStateException.class, operation::run);
        assertTrue(failure.getMessage().startsWith(expected + ":"), failure::getMessage);
    }
}
