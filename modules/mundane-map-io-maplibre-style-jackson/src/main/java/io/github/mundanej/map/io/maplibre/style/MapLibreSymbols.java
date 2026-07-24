package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeValueCandidate;
import io.github.mundanej.map.api.AttributeValueConversion;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.InterpolatedSymbolSelector;
import io.github.mundanej.map.api.InterpolatedSymbolStop;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.OmittedSymbol;
import io.github.mundanej.map.api.PortrayalLogicalOperator;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.ScaleInterval;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class MapLibreSymbols {
    private static final Envelope VIEW_BOX = new Envelope(-0.5, -0.5, 0.5, 0.5);
    private static final double CUBIC = 4.0 * (StrictMath.sqrt(2.0) - 1.0) / 3.0;

    private MapLibreSymbols() {}

    static Optional<FeaturePortrayal> literal(
            MapLibreLayerType type,
            Map<String, Object> layout,
            Map<String, Object> paint,
            String location,
            boolean renderingRequired,
            MapLibreReadLimits limits,
            CancellationToken cancellation) {
        DynamicProperty dynamic = dynamicProperty(type, layout, paint, location);
        if (dynamic != null) {
            try {
                FeaturePortrayal result =
                        new ExpressionCompiler(
                                        type,
                                        layout,
                                        paint,
                                        dynamic,
                                        location,
                                        renderingRequired,
                                        limits,
                                        cancellation)
                                .compile();
                FeaturePortrayalResolver.compile(result);
                return Optional.of(result);
            } catch (MapLibreReadException failure) {
                throw failure;
            } catch (IllegalArgumentException failure) {
                throw MapLibreStyles.failure(
                        "MAPLIBRE_EXPRESSION_TYPE",
                        dynamic.location(),
                        Map.of("reason", "incompatibleResults"),
                        failure);
            }
        }
        return staticLiteral(type, layout, paint, location, renderingRequired);
    }

    private static Optional<FeaturePortrayal> staticLiteral(
            MapLibreLayerType type,
            Map<String, Object> layout,
            Map<String, Object> paint,
            String location,
            boolean renderingRequired) {
        return switch (type) {
            case CIRCLE -> circle(paint, location + "/paint");
            case LINE -> line(layout, paint, location, renderingRequired);
            case FILL -> fill(paint, location + "/paint");
            case SYMBOL -> throw new AssertionError("symbol layers use deferred binding");
        };
    }

    private static Optional<FeaturePortrayal> circle(Map<String, Object> paint, String location) {
        requireMembers(
                paint,
                Set.of(
                        "circle-radius",
                        "circle-color",
                        "circle-opacity",
                        "circle-stroke-width",
                        "circle-stroke-color",
                        "circle-stroke-opacity",
                        "circle-translate",
                        "circle-translate-anchor"),
                location);
        double radius = number(paint, "circle-radius", 5.0, 0.0, 1_024.0, location);
        Rgba fill = color(paint, "circle-color", Rgba.rgb(0, 0, 0), location);
        double fillOpacity = number(paint, "circle-opacity", 1.0, 0.0, 1.0, location);
        double strokeWidth = number(paint, "circle-stroke-width", 0.0, 0.0, 1_024.0, location);
        Rgba stroke = color(paint, "circle-stroke-color", Rgba.rgb(0, 0, 0), location);
        double strokeOpacity = number(paint, "circle-stroke-opacity", 1.0, 0.0, 1.0, location);
        double[] translation = pair(paint, "circle-translate", new double[] {0.0, 0.0}, location);
        String anchor = string(paint, "circle-translate-anchor", "map", location);
        if (!anchor.equals("map") && !anchor.equals("viewport")) {
            throw value(location + "/circle-translate-anchor", "enum");
        }
        if (anchor.equals("map")
                && (Double.compare(translation[0], 0.0) != 0
                        || Double.compare(translation[1], 0.0) != 0)) {
            throw unsupported(location + "/circle-translate-anchor", "mapTranslation");
        }
        double outer = radius + strokeWidth;
        if (Double.compare(outer, 0.0) == 0) {
            return Optional.empty();
        }
        MarkerPlacement placement =
                new MarkerPlacement(
                        SymbolSize.square(outer * 2.0, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        translation[0],
                        translation[1],
                        0.0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        ArrayList<Symbol> children = new ArrayList<>(2);
        if (strokeWidth > 0.0 && stroke.alpha() > 0 && strokeOpacity > 0.0) {
            double innerFraction = radius / outer;
            children.add(
                    VectorMarkerSymbol.of(
                            annulus(innerFraction),
                            VIEW_BOX,
                            stroke,
                            Optional.empty(),
                            placement,
                            strokeOpacity));
        }
        if (radius > 0.0 && fill.alpha() > 0 && fillOpacity > 0.0) {
            double fraction = radius / outer;
            children.add(
                    VectorMarkerSymbol.of(
                            disk(fraction),
                            VIEW_BOX,
                            fill,
                            Optional.empty(),
                            placement,
                            fillOpacity));
        }
        if (children.isEmpty()) {
            return Optional.empty();
        }
        Symbol marker = children.size() == 1 ? children.get(0) : CompositeSymbol.of(children, 1.0);
        return Optional.of(FeaturePortrayal.markers(new FixedSymbolSelector(marker)));
    }

    private static Optional<FeaturePortrayal> line(
            Map<String, Object> layout,
            Map<String, Object> paint,
            String location,
            boolean renderingRequired) {
        requireMembers(
                paint,
                Set.of("line-color", "line-width", "line-opacity", "line-offset"),
                location + "/paint");
        double width = number(paint, "line-width", 1.0, 0.0, 1_024.0, location + "/paint");
        if (Double.compare(number(paint, "line-offset", 0.0, 0.0, 0.0, location + "/paint"), 0.0)
                != 0) {
            throw new IllegalStateException("validated zero line offset changed");
        }
        Rgba color = color(paint, "line-color", Rgba.rgb(0, 0, 0), location + "/paint");
        double opacity = number(paint, "line-opacity", 1.0, 0.0, 1.0, location + "/paint");
        String cap =
                layout.containsKey("line-cap")
                        ? string(layout, "line-cap", "", location + "/layout")
                        : null;
        String join =
                layout.containsKey("line-join")
                        ? string(layout, "line-join", "", location + "/layout")
                        : null;
        if ((cap != null && !"round".equals(cap)) || (join != null && !"round".equals(join))) {
            throw unsupported(location + "/layout", "lineCapJoin");
        }
        if (!renderingRequired || Double.compare(width, 0.0) == 0) {
            return Optional.empty();
        }
        if (cap == null || join == null) {
            throw unsupported(location + "/layout", "lineCapJoinRequired");
        }
        SolidLineSymbol symbol =
                SolidLineSymbol.of(
                        new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL)),
                        opacity);
        return Optional.of(
                new FeaturePortrayal(
                        Optional.empty(),
                        Optional.of(new FixedSymbolSelector(symbol)),
                        Optional.empty()));
    }

    private static Optional<FeaturePortrayal> fill(Map<String, Object> paint, String location) {
        requireMembers(
                paint,
                Set.of(
                        "fill-color",
                        "fill-opacity",
                        "fill-outline-color",
                        "fill-antialias",
                        "fill-translate",
                        "fill-translate-anchor"),
                location);
        if (!booleanValue(paint, "fill-antialias", true, location)) {
            throw unsupported(location + "/fill-antialias", "false");
        }
        double[] translation = pair(paint, "fill-translate", new double[] {0.0, 0.0}, location);
        if (Double.compare(translation[0], 0.0) != 0 || Double.compare(translation[1], 0.0) != 0) {
            throw unsupported(location + "/fill-translate", "nonzero");
        }
        if (paint.containsKey("fill-translate-anchor")) {
            String anchor = string(paint, "fill-translate-anchor", "map", location);
            if (!anchor.equals("map") && !anchor.equals("viewport")) {
                throw value(location + "/fill-translate-anchor", "enum");
            }
        }
        Rgba fill = color(paint, "fill-color", Rgba.rgb(0, 0, 0), location);
        Rgba outline =
                paint.containsKey("fill-outline-color")
                        ? color(paint, "fill-outline-color", Rgba.rgb(0, 0, 0), location)
                        : Rgba.rgb(fill.red(), fill.green(), fill.blue());
        double opacity = number(paint, "fill-opacity", 1.0, 0.0, 1.0, location);
        SolidLineSymbol outlineSymbol =
                SolidLineSymbol.of(
                        new SymbolStroke(outline, new SymbolLength(1.0, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        SolidFillSymbol symbol = SolidFillSymbol.of(fill, Optional.of(outlineSymbol), opacity);
        return Optional.of(
                new FeaturePortrayal(
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of(new FixedSymbolSelector(symbol))));
    }

    private static DynamicProperty dynamicProperty(
            MapLibreLayerType type,
            Map<String, Object> layout,
            Map<String, Object> paint,
            String location) {
        DynamicProperty selected = null;
        for (Map.Entry<String, Object> entry : layout.entrySet()) {
            if (isExpression(entry.getValue())) {
                selected =
                        selectDynamic(
                                selected,
                                new DynamicProperty(
                                        true,
                                        entry.getKey(),
                                        entry.getValue(),
                                        location + "/layout/" + entry.getKey()));
            }
        }
        for (Map.Entry<String, Object> entry : paint.entrySet()) {
            if (isExpression(entry.getValue())) {
                if (!dynamicPaintProperties(type).contains(entry.getKey())) {
                    throw MapLibreStyles.failure(
                            "MAPLIBRE_EXPRESSION_UNSUPPORTED",
                            location + "/paint/" + entry.getKey(),
                            Map.of("reason", "literalOnlyProperty"));
                }
                selected =
                        selectDynamic(
                                selected,
                                new DynamicProperty(
                                        false,
                                        entry.getKey(),
                                        entry.getValue(),
                                        location + "/paint/" + entry.getKey()));
            }
        }
        return selected;
    }

    private static Set<String> dynamicPaintProperties(MapLibreLayerType type) {
        return switch (type) {
            case CIRCLE ->
                    Set.of(
                            "circle-radius",
                            "circle-color",
                            "circle-opacity",
                            "circle-stroke-width",
                            "circle-stroke-color",
                            "circle-stroke-opacity");
            case LINE -> Set.of("line-color", "line-width", "line-opacity");
            case FILL -> Set.of("fill-color", "fill-opacity", "fill-outline-color");
            case SYMBOL -> Set.of();
        };
    }

    private static DynamicProperty selectDynamic(
            DynamicProperty current, DynamicProperty candidate) {
        if (current != null) {
            throw MapLibreStyles.failure(
                    "MAPLIBRE_EXPRESSION_UNSUPPORTED",
                    candidate.location(),
                    Map.of("reason", "multipleDynamicProperties"));
        }
        return candidate;
    }

    private static boolean isExpression(Object value) {
        return value instanceof List<?> list
                && !list.isEmpty()
                && list.getFirst() instanceof String;
    }

    private static FeaturePortrayal portrayal(SymbolSelectorResult selected) {
        return switch (selected.role()) {
            case MARKER -> FeaturePortrayal.markers(selected.selector());
            case LINE ->
                    new FeaturePortrayal(
                            Optional.empty(), Optional.of(selected.selector()), Optional.empty());
            case FILL ->
                    new FeaturePortrayal(
                            Optional.empty(), Optional.empty(), Optional.of(selected.selector()));
            case LEGACY_GEOMETRY -> throw new AssertionError("legacy role");
        };
    }

    private static final class ExpressionCompiler {
        private final MapLibreLayerType type;
        private final Map<String, Object> layout;
        private final Map<String, Object> paint;
        private final DynamicProperty dynamic;
        private final String location;
        private final boolean renderingRequired;
        private final MapLibreReadLimits limits;
        private final CancellationToken cancellation;

        private ExpressionCompiler(
                MapLibreLayerType type,
                Map<String, Object> layout,
                Map<String, Object> paint,
                DynamicProperty dynamic,
                String location,
                boolean renderingRequired,
                MapLibreReadLimits limits,
                CancellationToken cancellation) {
            this.type = type;
            this.layout = layout;
            this.paint = paint;
            this.dynamic = dynamic;
            this.location = location;
            this.renderingRequired = renderingRequired;
            this.limits = limits;
            this.cancellation = cancellation;
        }

        private FeaturePortrayal compile() {
            if (dynamic.layout()) {
                throw MapLibreStyles.failure(
                        "MAPLIBRE_EXPRESSION_UNSUPPORTED",
                        dynamic.location(),
                        Map.of("reason", "dynamicLayout"));
            }
            List<Object> expression = expression(dynamic.expression(), dynamic.location());
            String operation = operation(expression, dynamic.location());
            SymbolSelectorResult selected =
                    switch (operation) {
                        case "match" -> match(expression);
                        case "step" -> step(expression);
                        case "interpolate" -> interpolate(expression);
                        case "case" -> conditional(expression);
                        default ->
                                throw MapLibreStyles.failure(
                                        "MAPLIBRE_EXPRESSION_UNSUPPORTED",
                                        dynamic.location(),
                                        Map.of("reason", "propertyOperator"));
                    };
            return portrayal(selected);
        }

        private SymbolSelectorResult match(List<Object> expression) {
            if (expression.size() < 5 || (expression.size() & 1) == 0) {
                throw expressionType(dynamic.location(), "arity");
            }
            ExpressionInput input =
                    expressionInput(expression.get(1), dynamic.location() + "/1", false);
            int count = (expression.size() - 3) / 2;
            int maximum =
                    Math.min(limits.maximumCategories(), CategoricalSymbolSelector.MAXIMUM_RULES);
            if (count > maximum) {
                throw limit(dynamic.location(), "categories", count, maximum);
            }
            List<CategoricalSymbolRule> rules = new ArrayList<>(count);
            SymbolRole role = null;
            for (int index = 2; index < expression.size() - 1; index += 2) {
                cancelled(rules.size());
                ThematicValue value =
                        category(expression.get(index), dynamic.location() + '/' + index);
                Symbol symbol =
                        materialize(
                                constant(
                                        expression.get(index + 1),
                                        dynamic.location() + '/' + (index + 1)));
                role = sameRole(role, symbol);
                rules.add(new CategoricalSymbolRule(value, symbol));
            }
            Symbol fallback =
                    materialize(
                            constant(
                                    expression.getLast(),
                                    dynamic.location() + '/' + (expression.size() - 1)));
            role = sameRole(role, fallback);
            return new SymbolSelectorResult(
                    CategoricalSymbolSelector.expressionInput(
                            input.attribute(), rules, Optional.of(fallback), input.conversion()),
                    role);
        }

        private SymbolSelectorResult step(List<Object> expression) {
            if (expression.size() < 5 || (expression.size() & 1) == 0) {
                throw expressionType(dynamic.location(), "arity");
            }
            ExpressionInput input =
                    expressionInput(expression.get(1), dynamic.location() + "/1", true);
            Symbol fallback = materialize(constant(expression.get(2), dynamic.location() + "/2"));
            Symbol invalidFallback = defaultSymbol();
            int count = (expression.size() - 3) / 2;
            int maximum = Math.min(limits.maximumStops(), GraduatedSymbolSelector.MAXIMUM_STEPS);
            if (count > maximum) {
                throw limit(dynamic.location(), "stops", count, maximum);
            }
            List<GraduatedSymbolStep> steps = new ArrayList<>(count);
            BigDecimal previous = null;
            for (int index = 3; index < expression.size(); index += 2) {
                cancelled(steps.size());
                BigDecimal threshold =
                        decimal(expression.get(index), dynamic.location() + '/' + index);
                if (previous != null && previous.compareTo(threshold) >= 0) {
                    throw expressionType(dynamic.location() + '/' + index, "stopOrder");
                }
                Symbol symbol =
                        materialize(
                                constant(
                                        expression.get(index + 1),
                                        dynamic.location() + '/' + (index + 1)));
                sameRole(fallback.role(), symbol);
                steps.add(new GraduatedSymbolStep(threshold, symbol));
                previous = threshold;
            }
            return new SymbolSelectorResult(
                    input.zoom()
                            ? GraduatedSymbolSelector.zoom(
                                    steps, Optional.of(fallback), Optional.of(invalidFallback))
                            : GraduatedSymbolSelector.expressionInput(
                                    input.attribute(),
                                    steps,
                                    Optional.of(fallback),
                                    Optional.of(invalidFallback),
                                    input.conversion()),
                    fallback.role());
        }

        private SymbolSelectorResult interpolate(List<Object> expression) {
            if (expression.size() < 7 || (expression.size() & 1) == 0) {
                throw expressionType(dynamic.location(), "arity");
            }
            List<Object> curve = expression(expression.get(1), dynamic.location() + "/1");
            if (curve.size() != 1 || !"linear".equals(curve.getFirst())) {
                throw expressionType(dynamic.location() + "/1", "linear");
            }
            ExpressionInput input =
                    expressionInput(expression.get(2), dynamic.location() + "/2", true);
            int count = (expression.size() - 3) / 2;
            int maximum = Math.min(limits.maximumStops(), InterpolatedSymbolSelector.MAXIMUM_STOPS);
            if (count > maximum) {
                throw limit(dynamic.location(), "stops", count, maximum);
            }
            List<InterpolatedSymbolStop> stops = new ArrayList<>(count);
            SymbolRole role = null;
            BigDecimal previous = null;
            for (int index = 3; index < expression.size(); index += 2) {
                cancelled(stops.size());
                BigDecimal threshold =
                        decimal(expression.get(index), dynamic.location() + '/' + index);
                if (previous != null && previous.compareTo(threshold) >= 0) {
                    throw expressionType(dynamic.location() + '/' + index, "stopOrder");
                }
                Symbol symbol =
                        materialize(
                                constant(
                                        expression.get(index + 1),
                                        dynamic.location() + '/' + (index + 1)));
                role = sameRole(role, symbol);
                stops.add(new InterpolatedSymbolStop(threshold, symbol));
                previous = threshold;
            }
            Symbol fallback = defaultSymbol();
            sameRole(role, fallback);
            return new SymbolSelectorResult(
                    input.zoom()
                            ? InterpolatedSymbolSelector.zoom(stops, fallback)
                            : InterpolatedSymbolSelector.expressionInput(
                                    input.attribute(), stops, fallback, input.conversion()),
                    role);
        }

        private SymbolSelectorResult conditional(List<Object> expression) {
            if (expression.size() < 4 || (expression.size() & 1) != 0) {
                throw expressionType(dynamic.location(), "arity");
            }
            List<PortrayalRule> rules = new ArrayList<>();
            List<PortrayalPredicate> preceding = new ArrayList<>();
            SymbolRole role = null;
            int nodes = 0;
            for (int index = 1; index < expression.size() - 1; index += 2) {
                cancelled(rules.size());
                MapLibreFilters.CompiledFilter condition =
                        MapLibreFilters.compile(
                                expression.get(index),
                                limits,
                                nodes,
                                cancellation,
                                dynamic.location() + '/' + index);
                nodes += condition.nodes();
                List<PortrayalPredicate> active = new ArrayList<>();
                for (PortrayalPredicate earlier : preceding) {
                    cancelled(active.size());
                    active.add(
                            new PortrayalPredicate.Logical(
                                    PortrayalLogicalOperator.NOT, List.of(earlier)));
                }
                active.add(condition.predicate());
                PortrayalPredicate predicate =
                        active.size() == 1
                                ? active.getFirst()
                                : new PortrayalPredicate.Logical(
                                        PortrayalLogicalOperator.AND, active);
                Symbol symbol =
                        materialize(
                                constant(
                                        expression.get(index + 1),
                                        dynamic.location() + '/' + (index + 1)));
                role = sameRole(role, symbol);
                rules.add(rule(predicate, false, symbol));
                preceding.add(condition.predicate());
            }
            Symbol fallback =
                    materialize(
                            constant(
                                    expression.getLast(),
                                    dynamic.location() + '/' + (expression.size() - 1)));
            role = sameRole(role, fallback);
            rules.add(rule(null, true, fallback));
            RulePortrayalPlan plan = new RulePortrayalPlan(rules);
            FeaturePortrayal portrayal = plan.portrayal();
            return new SymbolSelectorResult(
                    switch (role) {
                        case MARKER -> portrayal.marker().orElseThrow();
                        case LINE -> portrayal.line().orElseThrow();
                        case FILL -> portrayal.fill().orElseThrow();
                        case LEGACY_GEOMETRY -> throw new AssertionError("legacy role");
                    },
                    role);
        }

        private PortrayalRule rule(PortrayalPredicate predicate, boolean otherwise, Symbol symbol) {
            List<Symbol> markers = symbol.role() == SymbolRole.MARKER ? List.of(symbol) : List.of();
            List<Symbol> lines = symbol.role() == SymbolRole.LINE ? List.of(symbol) : List.of();
            List<Symbol> fills = symbol.role() == SymbolRole.FILL ? List.of(symbol) : List.of();
            return new PortrayalRule(
                    Optional.empty(),
                    ScaleInterval.ALL,
                    Optional.ofNullable(predicate),
                    otherwise,
                    markers,
                    lines,
                    fills);
        }

        private Symbol defaultSymbol() {
            Map<String, Object> values = new java.util.LinkedHashMap<>(paint);
            values.remove(dynamic.name());
            return fixed(
                    staticLiteral(
                            type, materializationLayout(), Map.copyOf(values), location, true));
        }

        private Symbol materialize(Object value) {
            Map<String, Object> values = new java.util.LinkedHashMap<>(paint);
            values.put(dynamic.name(), value);
            return fixed(
                    staticLiteral(
                            type, materializationLayout(), Map.copyOf(values), location, true));
        }

        private Map<String, Object> materializationLayout() {
            if (renderingRequired
                    || type != MapLibreLayerType.LINE
                    || (layout.containsKey("line-cap") && layout.containsKey("line-join"))) {
                return layout;
            }
            Map<String, Object> values = new java.util.LinkedHashMap<>(layout);
            values.putIfAbsent("line-cap", "round");
            values.putIfAbsent("line-join", "round");
            return Map.copyOf(values);
        }

        private Symbol fixed(Optional<FeaturePortrayal> portrayal) {
            if (portrayal.isEmpty()) {
                return OmittedSymbol.of(
                        switch (type) {
                            case CIRCLE -> SymbolRole.MARKER;
                            case LINE -> SymbolRole.LINE;
                            case FILL -> SymbolRole.FILL;
                            case SYMBOL -> throw new AssertionError("symbol materialization");
                        });
            }
            if (portrayal.orElseThrow().selectors().size() != 1) {
                throw expressionType(dynamic.location(), "symbolResult");
            }
            var selector = portrayal.orElseThrow().selectors().getFirst();
            if (!(selector instanceof FixedSymbolSelector fixed)) {
                throw new AssertionError("materialized selector");
            }
            return fixed.symbol();
        }

        private void cancelled(int index) {
            if ((index & 255) == 0 && cancellation.isCancellationRequested()) {
                throw MapLibreStyles.failure("MAPLIBRE_CANCELLED", dynamic.location(), Map.of());
            }
        }
    }

    private static Object constant(Object value, String location) {
        if (!isExpression(value)) {
            return value;
        }
        List<Object> expression = expression(value, location);
        String operation = operation(expression, location);
        if ("literal".equals(operation) && expression.size() == 2) {
            return expression.get(1);
        }
        throw expressionType(location, "constantResult");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> expression(Object value, String location) {
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            throw expressionType(location, "expression");
        }
        return (List<Object>) list;
    }

    private static String operation(List<Object> expression, String location) {
        if (!(expression.getFirst() instanceof String operation)) {
            throw expressionType(location + "/0", "operator");
        }
        return operation;
    }

    private static String directGet(Object value, String location) {
        List<Object> input = expression(value, location);
        if (input.size() != 2
                || !"get".equals(operation(input, location))
                || !(input.get(1) instanceof String attribute)) {
            throw expressionType(location, "directGet");
        }
        return attribute;
    }

    private static ExpressionInput expressionInput(
            Object value, String location, boolean allowZoom) {
        List<Object> expression = expression(value, location);
        String operation = operation(expression, location);
        if ("get".equals(operation)) {
            return new ExpressionInput(
                    directGet(expression, location), AttributeValueConversion.IDENTITY, false);
        }
        if ("to-number".equals(operation) && expression.size() == 2) {
            return numericInput(expression, location);
        }
        if ("to-number".equals(operation) && expression.size() > 2 && expression.size() <= 9) {
            return numericInput(expression, location);
        }
        if (allowZoom && "zoom".equals(operation) && expression.size() == 1) {
            return new ExpressionInput("", AttributeValueConversion.IDENTITY, true);
        }
        throw expressionType(location, "expressionInput");
    }

    private static ExpressionInput numericInput(List<Object> expression, String location) {
        List<AttributeValueCandidate> candidates = new ArrayList<>(expression.size() - 1);
        String primaryAttribute = null;
        for (int index = 1; index < expression.size(); index++) {
            Object candidate = expression.get(index);
            if (isExpression(candidate)) {
                String attribute = directGet(candidate, location + '/' + index);
                if (primaryAttribute == null) {
                    primaryAttribute = attribute;
                }
                candidates.add(new AttributeValueCandidate.Attribute(attribute));
            } else {
                candidates.add(
                        new AttributeValueCandidate.Literal(
                                category(candidate, location + '/' + index)));
            }
        }
        if (primaryAttribute == null) {
            throw expressionType(location, "dynamicInput");
        }
        return new ExpressionInput(
                primaryAttribute, AttributeValueConversion.toNumber(candidates), false);
    }

    private static ThematicValue category(Object value, String location) {
        Object literal = constant(value, location);
        if (literal == io.github.mundanej.map.api.AttributeNull.INSTANCE) {
            return ThematicValue.nullValue();
        }
        if (literal instanceof String text) {
            return ThematicValue.text(text);
        }
        if (literal instanceof Boolean logical) {
            return ThematicValue.logical(logical);
        }
        if (literal instanceof BigDecimal number) {
            return ThematicValue.numeric(number);
        }
        throw expressionType(location, "category");
    }

    private static BigDecimal decimal(Object value, String location) {
        Object literal = constant(value, location);
        if (!(literal instanceof BigDecimal number)) {
            throw expressionType(location, "number");
        }
        return number;
    }

    private static SymbolRole sameRole(SymbolRole expected, Symbol symbol) {
        return sameRole(expected, symbol.role());
    }

    private static SymbolRole sameRole(SymbolRole expected, SymbolRole actual) {
        if (expected != null && expected != actual) {
            throw expressionType("", "resultType");
        }
        return actual;
    }

    private static MapLibreReadException expressionType(String location, String reason) {
        return MapLibreStyles.failure(
                "MAPLIBRE_EXPRESSION_TYPE", location, Map.of("reason", reason));
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

    private record DynamicProperty(
            boolean layout, String name, Object expression, String location) {}

    private record ExpressionInput(
            String attribute, AttributeValueConversion conversion, boolean zoom) {}

    private record SymbolSelectorResult(
            io.github.mundanej.map.api.SymbolSelector selector, SymbolRole role) {}

    private static VectorPath disk(double fraction) {
        return circleSubpath(VectorPath.builder(), 0.5 * fraction, false).build();
    }

    private static VectorPath annulus(double innerFraction) {
        VectorPath.Builder builder = circleSubpath(VectorPath.builder(), 0.5, false);
        if (innerFraction > 0.0) {
            circleSubpath(builder, 0.5 * innerFraction, true);
        }
        return builder.build();
    }

    private static VectorPath.Builder circleSubpath(
            VectorPath.Builder builder, double radius, boolean reverse) {
        double control = radius * CUBIC;
        if (reverse) {
            return builder.moveTo(0, -radius)
                    .cubicTo(-control, -radius, -radius, -control, -radius, 0)
                    .cubicTo(-radius, control, -control, radius, 0, radius)
                    .cubicTo(control, radius, radius, control, radius, 0)
                    .cubicTo(radius, -control, control, -radius, 0, -radius)
                    .close();
        }
        return builder.moveTo(0, -radius)
                .cubicTo(control, -radius, radius, -control, radius, 0)
                .cubicTo(radius, control, control, radius, 0, radius)
                .cubicTo(-control, radius, -radius, control, -radius, 0)
                .cubicTo(-radius, -control, -control, -radius, 0, -radius)
                .close();
    }

    private static void requireMembers(
            Map<String, Object> object, Set<String> accepted, String location) {
        for (String member : object.keySet()) {
            if (member.endsWith("-transition")) {
                throw unsupported(location + "/property", "transition");
            }
            if (!accepted.contains(member)) {
                throw unsupported(location + "/property", "property");
            }
        }
    }

    private static double number(
            Map<String, Object> object,
            String name,
            double fallback,
            double minimum,
            double maximum,
            String location) {
        if (!object.containsKey(name)) {
            return fallback;
        }
        Object value = object.get(name);
        rejectExpression(value, location + '/' + name);
        if (!(value instanceof BigDecimal decimal)) {
            throw value(location + '/' + name, "number");
        }
        double result = decimal.doubleValue();
        if (!Double.isFinite(result) || result < minimum || result > maximum) {
            throw value(location + '/' + name, "range");
        }
        return result == 0.0 ? 0.0 : result;
    }

    private static Rgba color(
            Map<String, Object> object, String name, Rgba fallback, String location) {
        if (!object.containsKey(name)) {
            return fallback;
        }
        Object value = object.get(name);
        rejectExpression(value, location + '/' + name);
        if (!(value instanceof String text)
                || (text.length() != 7 && text.length() != 9)
                || text.charAt(0) != '#') {
            throw value(location + '/' + name, "color");
        }
        try {
            int red = Integer.parseInt(text.substring(1, 3), 16);
            int green = Integer.parseInt(text.substring(3, 5), 16);
            int blue = Integer.parseInt(text.substring(5, 7), 16);
            int alpha = text.length() == 9 ? Integer.parseInt(text.substring(7, 9), 16) : 255;
            return new Rgba(red, green, blue, alpha);
        } catch (NumberFormatException failure) {
            throw MapLibreStyles.failure(
                    "MAPLIBRE_VALUE_INVALID",
                    location + '/' + name,
                    Map.of("reason", "color"),
                    failure);
        }
    }

    private static String string(
            Map<String, Object> object, String name, String fallback, String location) {
        if (!object.containsKey(name)) {
            return fallback;
        }
        Object value = object.get(name);
        rejectExpression(value, location + '/' + name);
        if (!(value instanceof String text)) {
            throw value(location + '/' + name, "string");
        }
        return text;
    }

    private static boolean booleanValue(
            Map<String, Object> object, String name, boolean fallback, String location) {
        if (!object.containsKey(name)) {
            return fallback;
        }
        Object value = object.get(name);
        rejectExpression(value, location + '/' + name);
        if (!(value instanceof Boolean result)) {
            throw value(location + '/' + name, "boolean");
        }
        return result;
    }

    private static double[] pair(
            Map<String, Object> object, String name, double[] fallback, String location) {
        if (!object.containsKey(name)) {
            return fallback.clone();
        }
        Object value = object.get(name);
        if (!(value instanceof List<?> values) || values.size() != 2) {
            rejectExpression(value, location + '/' + name);
            throw value(location + '/' + name, "pair");
        }
        return new double[] {
            finite(values.get(0), location + '/' + name + "/0"),
            finite(values.get(1), location + '/' + name + "/1")
        };
    }

    private static double finite(Object value, String location) {
        if (!(value instanceof BigDecimal decimal)) {
            throw value(location, "number");
        }
        double result = decimal.doubleValue();
        if (!Double.isFinite(result) || result < -65_536 || result > 65_536) {
            throw value(location, "range");
        }
        return result == 0.0 ? 0.0 : result;
    }

    private static void rejectExpression(Object value, String location) {
        if (value instanceof List<?> values
                && !values.isEmpty()
                && values.get(0) instanceof String) {
            throw MapLibreStyles.failure(
                    "MAPLIBRE_EXPRESSION_UNSUPPORTED", location, Map.of("reason", "literalSlice"));
        }
    }

    private static MapLibreReadException unsupported(String location, String reason) {
        return MapLibreStyles.failure(
                "MAPLIBRE_PROPERTY_UNSUPPORTED", location, Map.of("reason", reason));
    }

    private static MapLibreReadException value(String location, String reason) {
        return MapLibreStyles.failure("MAPLIBRE_VALUE_INVALID", location, Map.of("reason", reason));
    }
}
