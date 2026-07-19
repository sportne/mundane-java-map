package io.github.mundanej.map.core;

import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.LabelPlacementProblem;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.ScreenBox;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded deterministic greedy placement for singular-point labels. */
public final class GreedyPointLabelPlacement {
    /** Maximum eligible requests in one operation. */
    public static final int MAXIMUM_REQUESTS = 4_096;

    /** Maximum candidate boxes evaluated in one operation. */
    public static final int MAXIMUM_CANDIDATES = 32_768;

    /** Maximum accepted-box comparisons in one operation. */
    public static final long MAXIMUM_COLLISION_COMPARISONS = 10_000_000L;

    private static final Limits DEFAULT_LIMITS =
            new Limits(MAXIMUM_REQUESTS, MAXIMUM_CANDIDATES, MAXIMUM_COLLISION_COMPARISONS);

    private GreedyPointLabelPlacement() {}

    /**
     * Places requests by priority and topmost paint order, returning accepted labels in paint
     * order.
     *
     * @param componentBounds finite logical component bounds starting at zero
     * @param requests measured requests in any order, with unique paint ordinals
     * @return immutable accepted labels in ascending ordinary paint order
     * @throws LabelPlacementException when a fixed operation limit is exceeded
     */
    public static List<PlacedPointLabel> place(
            ScreenBox componentBounds, List<PointLabelPlacementRequest> requests) {
        return place(componentBounds, requests, DEFAULT_LIMITS);
    }

    static List<PlacedPointLabel> place(
            ScreenBox componentBounds, List<PointLabelPlacementRequest> requests, Limits limits) {
        Objects.requireNonNull(componentBounds, "componentBounds");
        Objects.requireNonNull(requests, "requests");
        Objects.requireNonNull(limits, "limits");
        if (componentBounds.minX() != 0.0 || componentBounds.minY() != 0.0) {
            throw new IllegalArgumentException("componentBounds must start at zero");
        }
        int requestCount = requests.size();
        if (requestCount > limits.requests()) {
            throw batchFailure(
                    "LABEL_REQUEST_LIMIT_EXCEEDED",
                    "Point-label request limit exceeded",
                    limits.requests(),
                    requestCount);
        }
        List<PointLabelPlacementRequest> retained = List.copyOf(requests);
        requireUniqueOrdinals(retained);

        List<PointLabelPlacementRequest> admissionOrder = new ArrayList<>(retained);
        admissionOrder.sort(
                Comparator.<PointLabelPlacementRequest>comparingInt(
                                request -> request.profile().priority())
                        .reversed()
                        .thenComparing(
                                Comparator.comparingInt(
                                                PointLabelPlacementRequest::ordinaryPaintOrdinal)
                                        .reversed()));

        List<PlacedPointLabel> accepted = new ArrayList<>();
        long candidates = 0;
        long comparisons = 0;
        for (PointLabelPlacementRequest request : admissionOrder) {
            for (PointLabelPosition position : request.profile().positions()) {
                candidates++;
                if (candidates > limits.candidates()) {
                    throw batchFailure(
                            "LABEL_CANDIDATE_LIMIT_EXCEEDED",
                            "Point-label candidate limit exceeded",
                            limits.candidates(),
                            candidates);
                }
                PlacedPointLabel candidate = candidate(request, position);
                if (!contained(componentBounds, candidate.collisionBounds())) {
                    continue;
                }
                boolean collides = false;
                for (PlacedPointLabel prior : accepted) {
                    comparisons++;
                    if (comparisons > limits.comparisons()) {
                        throw batchFailure(
                                "LABEL_COLLISION_WORK_LIMIT_EXCEEDED",
                                "Point-label collision-work limit exceeded",
                                limits.comparisons(),
                                comparisons);
                    }
                    if (positiveAreaIntersection(
                            candidate.collisionBounds(), prior.collisionBounds())) {
                        collides = true;
                        break;
                    }
                }
                if (!collides) {
                    accepted.add(candidate);
                    break;
                }
            }
        }
        accepted.sort(Comparator.comparingInt(PlacedPointLabel::ordinaryPaintOrdinal));
        return List.copyOf(accepted);
    }

    private static PlacedPointLabel candidate(
            PointLabelPlacementRequest request, PointLabelPosition position) {
        return PointLabelLayouts.place(
                request.layerId(),
                request.featureId(),
                request.text(),
                request.style(),
                request.markerBounds(),
                request.relativeVisualBounds(),
                request.advance(),
                request.profile(),
                position,
                request.layerIndex(),
                request.featureIndex(),
                request.ordinaryPaintOrdinal());
    }

    private static void requireUniqueOrdinals(List<PointLabelPlacementRequest> requests) {
        Set<Integer> ordinals = new HashSet<>();
        for (PointLabelPlacementRequest request : requests) {
            if (!ordinals.add(request.ordinaryPaintOrdinal())) {
                throw new IllegalArgumentException("ordinaryPaintOrdinal values must be unique");
            }
        }
    }

    private static boolean contained(ScreenBox container, ScreenBox candidate) {
        return candidate.minX() >= container.minX()
                && candidate.minY() >= container.minY()
                && candidate.maxX() <= container.maxX()
                && candidate.maxY() <= container.maxY();
    }

    private static boolean positiveAreaIntersection(ScreenBox first, ScreenBox second) {
        return Math.min(first.maxX(), second.maxX()) > Math.max(first.minX(), second.minX())
                && Math.min(first.maxY(), second.maxY()) > Math.max(first.minY(), second.minY());
    }

    private static LabelPlacementException batchFailure(
            String code, String message, long limit, long attempted) {
        Map<String, String> context = new LinkedHashMap<>();
        context.put("limit", Long.toString(limit));
        context.put("attempted", Long.toString(attempted));
        return new LabelPlacementException(new LabelPlacementProblem(code, message, context));
    }

    record Limits(int requests, long candidates, long comparisons) {
        Limits {
            if (requests < 0 || candidates < 0 || comparisons < 0) {
                throw new IllegalArgumentException("placement limits must be non-negative");
            }
        }
    }
}
