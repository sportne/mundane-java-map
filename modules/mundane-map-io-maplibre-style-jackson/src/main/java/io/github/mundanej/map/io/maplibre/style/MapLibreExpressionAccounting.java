package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.CancellationToken;
import java.util.List;
import java.util.Map;

/** Allocation-free structural accounting for expression-valued style properties. */
final class MapLibreExpressionAccounting {
    private MapLibreExpressionAccounting() {}

    static Counts count(
            Map<String, Object> layout,
            Map<String, Object> paint,
            MapLibreReadLimits limits,
            CancellationToken cancellation,
            String location) {
        Counter counter = new Counter(limits, cancellation);
        layout.forEach(
                (name, value) -> {
                    if (expression(value)) {
                        counter.visit(value, location + "/layout/" + name, 1);
                    }
                });
        paint.forEach(
                (name, value) -> {
                    if (expression(value)) {
                        counter.visit(value, location + "/paint/" + name, 1);
                    }
                });
        return new Counts(
                counter.totalNodes(),
                counter.stops,
                counter.categories,
                counter.rules,
                counter.ownedBytes());
    }

    private static boolean expression(Object value) {
        return value instanceof List<?> list
                && !list.isEmpty()
                && list.getFirst() instanceof String;
    }

    private static final class Counter {
        private final MapLibreReadLimits limits;
        private final CancellationToken cancellation;
        private int nodes;
        private int generatedNodes;
        private int stops;
        private int categories;
        private int rules;

        private Counter(MapLibreReadLimits limits, CancellationToken cancellation) {
            this.limits = limits;
            this.cancellation = cancellation;
        }

        private void visit(Object value, String location, int depth) {
            nodes++;
            if ((nodes & 255) == 0 && cancellation.isCancellationRequested()) {
                throw MapLibreStyles.failure("MAPLIBRE_CANCELLED", location, Map.of());
            }
            if (nodes > limits.maximumExpressionNodes()) {
                throw limit(location, "expressionNodes", nodes, limits.maximumExpressionNodes());
            }
            if (depth > limits.maximumExpressionDepth()) {
                throw limit(location, "expressionDepth", depth, limits.maximumExpressionDepth());
            }
            if (!expression(value)) {
                return;
            }
            List<?> expression = (List<?>) value;
            String operation = (String) expression.getFirst();
            if ("literal".equals(operation)) {
                return;
            }
            if ("interpolate".equals(operation) || "step".equals(operation)) {
                int added = Math.max(0, (expression.size() - 3) / 2);
                stops = add(stops, added, limits.maximumStops(), location, "stops");
            } else if ("match".equals(operation)) {
                int added = Math.max(0, (expression.size() - 3) / 2);
                categories =
                        add(categories, added, limits.maximumCategories(), location, "categories");
            } else if ("case".equals(operation)) {
                int added = Math.max(0, expression.size() / 2);
                rules = add(rules, added, limits.maximumProducedRules(), location, "producedRules");
                long bridgeNodes = ((long) added * (added + 1)) / 2;
                if ((long) totalNodes() + bridgeNodes > limits.maximumExpressionNodes()) {
                    throw limit(
                            location,
                            "expressionNodes",
                            (long) totalNodes() + bridgeNodes,
                            limits.maximumExpressionNodes());
                }
                generatedNodes = Math.addExact(generatedNodes, Math.toIntExact(bridgeNodes));
            }
            for (int index = 1; index < expression.size(); index++) {
                visit(expression.get(index), location + '/' + index, depth + 1);
            }
        }

        private long ownedBytes() {
            try {
                return Math.addExact(
                        Math.multiplyExact((long) totalNodes(), 32L),
                        Math.addExact(
                                Math.multiplyExact((long) stops, 24L),
                                Math.addExact(
                                        Math.multiplyExact((long) categories, 32L),
                                        Math.multiplyExact((long) rules, 48L))));
            } catch (ArithmeticException failure) {
                return Long.MAX_VALUE;
            }
        }

        private int totalNodes() {
            return Math.addExact(nodes, generatedNodes);
        }

        private static int add(
                int current, int increment, int maximum, String location, String name) {
            long result = (long) current + increment;
            if (result > maximum) {
                throw limit(location, name, result, maximum);
            }
            return (int) result;
        }
    }

    private static MapLibreReadException limit(
            String location, String name, long actual, long maximum) {
        return MapLibreStyles.failure(
                "MAPLIBRE_LIMIT_EXCEEDED",
                location,
                Map.of(
                        "limit", name,
                        "actual", Long.toString(actual),
                        "maximum", Long.toString(maximum)));
    }

    record Counts(int nodes, int stops, int categories, int rules, long ownedBytes) {}
}
