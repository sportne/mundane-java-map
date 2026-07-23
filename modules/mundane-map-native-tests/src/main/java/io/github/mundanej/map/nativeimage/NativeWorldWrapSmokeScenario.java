package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.core.GeographicSeamSplitter;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.HorizontalWrapException;
import io.github.mundanej.map.core.HorizontalWrapPlan;
import java.util.Map;

/** Resource-free world-wrap success and stable-failure path used by the native smoke. */
final class NativeWorldWrapSmokeScenario {
    private NativeWorldWrapSmokeScenario() {}

    static void run() {
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        double limit = wrap.canonicalMaximumX();
        HorizontalWrapPlan plan = wrap.plan(limit - 10_000.0, limit + 10_000.0, 100.0);
        NativeLevel1SmokeAssertions.require(
                plan.canonicalIntervals().size() == 2 && plan.visibleCopyCount() == 2,
                "world-wrap-success",
                "seam plan changed");
        NativeLevel1SmokeAssertions.require(
                wrap.canonicalTileColumn(-1L, 4L) == 3L,
                "world-wrap-success",
                "canonical tile-column math changed");
        GeographicSeamSplitter.Result seam =
                GeographicSeamSplitter.split(
                        new LineStringGeometry(CoordinateSequence.of(170.0, 10.0, -170.0, 20.0)),
                        CancellationToken.none());
        NativeLevel1SmokeAssertions.require(
                seam.insertedCrossings() == 1
                        && seam.fragments().size() == 2
                        && seam.fragments().getFirst().worldOffset() == 0L
                        && seam.fragments().getLast().worldOffset() == 1L,
                "world-wrap-seam",
                "geographic seam fragments changed");

        HorizontalWrap limited = new HorizontalWrap(-180.0, 180.0, 2, 16);
        try {
            limited.plan(-540.0, 540.0, 1.0);
            throw new IllegalStateException(
                    "world-wrap-diagnostic: excessive copies were accepted");
        } catch (HorizontalWrapException failure) {
            NativeLevel1SmokeAssertions.require(
                    failure.problem().code().equals("WORLD_WRAP_COPY_LIMIT_EXCEEDED"),
                    "world-wrap-diagnostic",
                    "copy-limit code changed");
            NativeLevel1SmokeAssertions.require(
                    failure.problem().context().equals(Map.of("maximum", "2", "requested", "3")),
                    "world-wrap-diagnostic",
                    "copy-limit context changed");
        }
    }
}
