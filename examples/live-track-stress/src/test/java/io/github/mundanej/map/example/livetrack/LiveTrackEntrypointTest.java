package io.github.mundanej.map.example.livetrack;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LiveTrackEntrypointTest {
    @Test
    void boundedSmokeCompletesTheFullLifecycleAndReportsConservedTelemetry() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            LiveTrackSmoke.main(new String[0]);
        } finally {
            System.setOut(original);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("population=10000"));
        assertTrue(report.contains("seconds=120"));
        assertTrue(report.contains("frames=13"));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackSmoke.main(new String[] {"unexpected"}));
    }

    @Test
    void profilingEntrypointsRejectEveryMalformedBoundaryBeforeAllocatingLargeTiers() {
        assertThrows(IllegalArgumentException.class, () -> LiveTrackScaleProbe.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackScaleProbe.main(new String[] {"workers", "10"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackScaleProbe.main(new String[] {"0", "10"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackScaleProbe.main(new String[] {"33", "10"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackScaleProbe.main(new String[] {"1", "seconds"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackScaleProbe.main(new String[] {"1", "9"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackScaleProbe.main(new String[] {"1", "11"}));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackScaleProbe.main(new String[] {"1", "3610"}));

        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackPresentationProbe.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackPresentationProbe.main(new String[] {"unsupported"}));
        assertThrows(IllegalArgumentException.class, () -> LiveTrackEvidence.main(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> LiveTrackEvidence.main(new String[] {"unsupported"}));
    }

    @Test
    void minimumScaleProbeCompletesTheHundredThousandTrackLifecycle() {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            LiveTrackScaleProbe.main(new String[] {"1", "10"});
        } finally {
            System.setOut(original);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("workers=1"));
        assertTrue(report.contains("seconds=10"));
        assertTrue(report.contains("frames=2"));
        assertTrue(report.contains("coloredPixels="));
    }

    @Test
    void presentationAndHeadlessEntrypointsCompleteTheirBoundedProfiles() throws Exception {
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            LiveTrackStress.main(new String[] {"--headless"});
            LiveTrackPresentationProbe.main(new String[] {"10k"});
        } finally {
            System.setOut(original);
        }
        String report = captured.toString(StandardCharsets.UTF_8);
        assertTrue(report.contains("Live-track slice: population=10000"));
        assertTrue(report.contains("Live-track presentation: population=10000"));
        assertTrue(report.contains("EDT-paint-p95="));
    }
}
