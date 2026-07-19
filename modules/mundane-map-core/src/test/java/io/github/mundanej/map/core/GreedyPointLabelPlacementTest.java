package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ScreenBox;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GreedyPointLabelPlacementTest {
    private static final LabelTextStyle STYLE =
            new LabelTextStyle(Rgba.rgb(20, 30, 40), LabelWeight.NORMAL, 12);
    private static final ScreenBox COMPONENT = new ScreenBox(0, 0, 100, 100);
    private static final ScreenBox TEXT = new ScreenBox(0, 0, 10, 10);

    @Test
    void higherPriorityThenTopmostOrdinalWinsAndResultsReturnInPaintOrder() {
        List<PlacedPointLabel> priority =
                GreedyPointLabelPlacement.place(
                        COMPONENT,
                        List.of(
                                request("low", 0, 0, 20, 20, 0, PointLabelPosition.E),
                                request("high", 1, 5, 20, 20, 0, PointLabelPosition.E)));
        assertEquals(List.of("high"), ids(priority));

        List<PlacedPointLabel> topmost =
                GreedyPointLabelPlacement.place(
                        COMPONENT,
                        List.of(
                                request("bottom", 0, 0, 20, 20, 0, PointLabelPosition.E),
                                request("top", 1, 0, 20, 20, 0, PointLabelPosition.E)));
        assertEquals(List.of("top"), ids(topmost));

        List<PlacedPointLabel> paintOrder =
                GreedyPointLabelPlacement.place(
                        COMPONENT,
                        List.of(
                                request("third", 2, 0, 60, 20, 0, PointLabelPosition.E),
                                request("first", 0, 0, 0, 20, 0, PointLabelPosition.E),
                                request("second", 1, 0, 30, 20, 0, PointLabelPosition.E)));
        assertEquals(List.of("first", "second", "third"), ids(paintOrder));
    }

    @Test
    void usesDeclaredFallbackAfterCollisionAndClipping() {
        PointLabelPlacementRequest blocker =
                request("blocker", 0, 10, 30, 20, 0, PointLabelPosition.E);
        PointLabelPlacementRequest fallback =
                request("fallback", 1, 0, 30, 20, 0, PointLabelPosition.E, PointLabelPosition.W);

        List<PlacedPointLabel> collision =
                GreedyPointLabelPlacement.place(COMPONENT, List.of(blocker, fallback));
        assertEquals(List.of("blocker", "fallback"), ids(collision));
        assertTrue(collision.get(1).visualBounds().maxX() <= 30);

        PointLabelPlacementRequest clipped =
                request("clipped", 0, 0, 90, 20, 0, PointLabelPosition.E, PointLabelPosition.W);
        PlacedPointLabel placed =
                GreedyPointLabelPlacement.place(COMPONENT, List.of(clipped)).getFirst();
        assertTrue(placed.visualBounds().maxX() <= 90);
    }

    @Test
    void edgeAndCornerTouchingDoNotCollideButPositiveAreaOverlapDoes() {
        PointLabelPlacementRequest first = request("first", 0, 1, 0, 0, 0, PointLabelPosition.E);
        PointLabelPlacementRequest touching =
                request("touching", 1, 0, 10, 0, 0, PointLabelPosition.E);
        PointLabelPlacementRequest overlap =
                request("overlap", 2, 0, 9, 0, 0, PointLabelPosition.E);

        assertEquals(
                List.of("first", "touching"),
                ids(GreedyPointLabelPlacement.place(COMPONENT, List.of(first, touching))));
        assertEquals(
                List.of("first"),
                ids(GreedyPointLabelPlacement.place(COMPONENT, List.of(first, overlap))));

        PointLabelPlacementRequest corner =
                request("corner", 1, 0, 10, 10, 0, PointLabelPosition.E);
        assertEquals(
                List.of("first", "corner"),
                ids(GreedyPointLabelPlacement.place(COMPONENT, List.of(first, corner))));

        PointLabelPlacementRequest zeroWidth =
                withTextBounds(
                        request("zero-width", 1, 0, 5, 0, 0, PointLabelPosition.E),
                        new ScreenBox(0, 0, 0, 10));
        assertEquals(
                List.of("first", "zero-width"),
                ids(GreedyPointLabelPlacement.place(COMPONENT, List.of(first, zeroWidth))));
    }

    @Test
    void enforcesRequestCandidateAndCollisionWorkLimitsWithBoundedContexts() {
        List<PointLabelPlacementRequest> tooMany = new ArrayList<>();
        PointLabelPlacementRequest repeated =
                request("request", 0, 0, 0, 0, 0, PointLabelPosition.E);
        for (int index = 0; index <= GreedyPointLabelPlacement.MAXIMUM_REQUESTS; index++) {
            tooMany.add(repeated);
        }
        assertProblem(
                assertThrows(
                        LabelPlacementException.class,
                        () -> GreedyPointLabelPlacement.place(COMPONENT, tooMany)),
                "LABEL_REQUEST_LIMIT_EXCEEDED",
                "4096",
                "4097");

        List<PointLabelPlacementRequest> untraversableOversized =
                new AbstractList<>() {
                    @Override
                    public PointLabelPlacementRequest get(int index) {
                        throw new AssertionError("oversized input must not be traversed");
                    }

                    @Override
                    public int size() {
                        return GreedyPointLabelPlacement.MAXIMUM_REQUESTS + 1;
                    }
                };
        assertProblem(
                assertThrows(
                        LabelPlacementException.class,
                        () -> GreedyPointLabelPlacement.place(COMPONENT, untraversableOversized)),
                "LABEL_REQUEST_LIMIT_EXCEEDED",
                "4096",
                "4097");

        PointLabelPlacementRequest twoCandidates =
                request("candidate", 0, 0, 90, 0, 0, PointLabelPosition.E, PointLabelPosition.W);
        assertProblem(
                assertThrows(
                        LabelPlacementException.class,
                        () ->
                                GreedyPointLabelPlacement.place(
                                        COMPONENT,
                                        List.of(twoCandidates),
                                        new GreedyPointLabelPlacement.Limits(1, 1, 10))),
                "LABEL_CANDIDATE_LIMIT_EXCEEDED",
                "1",
                "2");

        assertProblem(
                assertThrows(
                        LabelPlacementException.class,
                        () ->
                                GreedyPointLabelPlacement.place(
                                        COMPONENT,
                                        List.of(
                                                request("one", 0, 0, 0, 0, 0, PointLabelPosition.E),
                                                request(
                                                        "two",
                                                        1,
                                                        0,
                                                        30,
                                                        0,
                                                        0,
                                                        PointLabelPosition.E)),
                                        new GreedyPointLabelPlacement.Limits(2, 2, 0))),
                "LABEL_COLLISION_WORK_LIMIT_EXCEEDED",
                "0",
                "1");
    }

    @Test
    void rejectsAmbiguousOrdinalsAndNonZeroComponentOriginsAsCallerErrors() {
        PointLabelPlacementRequest first = request("first", 0, 0, 0, 0, 0, PointLabelPosition.E);
        PointLabelPlacementRequest duplicate =
                request("duplicate", 0, 0, 30, 0, 0, PointLabelPosition.E);
        assertThrows(
                IllegalArgumentException.class,
                () -> GreedyPointLabelPlacement.place(COMPONENT, List.of(first, duplicate)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GreedyPointLabelPlacement.place(
                                new ScreenBox(1, 0, 100, 100), List.of(first)));
    }

    private static void assertProblem(
            LabelPlacementException failure, String code, String limit, String attempted) {
        assertEquals(code, failure.problem().code());
        assertEquals(
                List.of("limit", "attempted"), List.copyOf(failure.problem().context().keySet()));
        assertEquals(limit, failure.problem().context().get("limit"));
        assertEquals(attempted, failure.problem().context().get("attempted"));
        assertTrue(failure.problem().context().values().stream().noneMatch("request"::equals));
    }

    private static List<String> ids(List<PlacedPointLabel> labels) {
        return labels.stream().map(PlacedPointLabel::featureId).toList();
    }

    private static PointLabelPlacementRequest request(
            String id,
            int ordinal,
            int priority,
            double markerMinX,
            double markerMinY,
            double padding,
            PointLabelPosition... positions) {
        PointLabelProfile profile =
                new PointLabelProfile(
                        FeatureName.INSTANCE,
                        STYLE,
                        List.of(positions),
                        0,
                        0,
                        0,
                        padding,
                        priority,
                        ResolutionRange.ALL);
        return new PointLabelPlacementRequest(
                "layer",
                id,
                id,
                STYLE,
                new ScreenBox(markerMinX, markerMinY, markerMinX + 10, markerMinY + 10),
                TEXT,
                10,
                profile,
                0,
                ordinal,
                ordinal);
    }

    private static PointLabelPlacementRequest withTextBounds(
            PointLabelPlacementRequest request, ScreenBox textBounds) {
        return new PointLabelPlacementRequest(
                request.layerId(),
                request.featureId(),
                request.text(),
                request.style(),
                request.markerBounds(),
                textBounds,
                request.advance(),
                request.profile(),
                request.layerIndex(),
                request.featureIndex(),
                request.ordinaryPaintOrdinal());
    }
}
