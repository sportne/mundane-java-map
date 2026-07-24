package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.LabelTextSource;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.LiteralLabelText;
import io.github.mundanej.map.api.PointLabelAnchorBasis;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PointLabelTexts;
import io.github.mundanej.map.api.PortrayalLogicalOperator;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.StringifiedTextAttribute;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.TextAttribute;
import io.github.mundanej.map.api.ThematicValue;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Closed parser for the bounded symbol icon and point-label profile. */
final class MapLibreSymbolParser {
    private static final Set<String> LAYOUT =
            Set.of(
                    "visibility",
                    "symbol-placement",
                    "symbol-sort-key",
                    "symbol-z-order",
                    "icon-image",
                    "icon-size",
                    "icon-rotate",
                    "icon-anchor",
                    "icon-offset",
                    "icon-rotation-alignment",
                    "icon-allow-overlap",
                    "icon-ignore-placement",
                    "icon-optional",
                    "text-field",
                    "text-font",
                    "text-size",
                    "text-anchor",
                    "text-variable-anchor",
                    "text-offset",
                    "text-radial-offset",
                    "text-padding",
                    "text-allow-overlap",
                    "text-ignore-placement",
                    "text-optional");
    private static final Set<String> PAINT = Set.of("icon-opacity", "text-color", "text-opacity");

    private MapLibreSymbolParser() {}

    static MapLibreSymbolSpec parse(
            Map<String, Object> layout,
            Map<String, Object> paint,
            MapLibreReadLimits limits,
            CancellationToken cancellation,
            String location) {
        try {
            return parseValidated(layout, paint, limits, cancellation, location);
        } catch (IllegalArgumentException failure) {
            throw value(location, "symbolValue");
        }
    }

    private static MapLibreSymbolSpec parseValidated(
            Map<String, Object> layout,
            Map<String, Object> paint,
            MapLibreReadLimits limits,
            CancellationToken cancellation,
            String location) {
        members(layout, LAYOUT, location + "/layout");
        members(paint, PAINT, location + "/paint");
        string(layout, "symbol-placement", "point", location + "/layout", Set.of("point"));
        string(layout, "symbol-z-order", null, location + "/layout", Set.of("source"));
        requireTrue(layout, "icon-allow-overlap", location + "/layout");
        requireTrue(layout, "icon-ignore-placement", location + "/layout");

        MapLibreSymbolSpec.IconExpression icon =
                iconExpression(
                        require(layout, "icon-image", location + "/layout"),
                        limits,
                        cancellation,
                        location + "/layout/icon-image");
        int catalogReferences = catalogReferences(icon);
        if (catalogReferences > limits.maximumCatalogReferences()) {
            throw limit(
                    location + "/layout/icon-image",
                    "catalogReferences",
                    catalogReferences,
                    limits.maximumCatalogReferences());
        }
        double size = number(layout, "icon-size", 1, 0, 128, false, location + "/layout");
        double rotation =
                number(
                        layout,
                        "icon-rotate",
                        0,
                        -Double.MAX_VALUE,
                        Double.MAX_VALUE,
                        true,
                        location + "/layout");
        double opacity = number(paint, "icon-opacity", 1, 0, 1, true, location + "/paint");
        SymbolAnchor anchor =
                symbolAnchor(
                        string(
                                layout,
                                "icon-anchor",
                                "center",
                                location + "/layout",
                                anchorNames()));
        double[] offset = pair(layout, "icon-offset", -1_000_000, 1_000_000, location + "/layout");
        if (rotation != 0 && (offset[0] != 0 || offset[1] != 0)) {
            throw unsupported(location + "/layout/icon-offset", "rotatedOffset");
        }
        String alignment =
                string(
                        layout,
                        "icon-rotation-alignment",
                        "auto",
                        location + "/layout",
                        Set.of("auto", "map", "viewport"));
        SymbolRotationMode rotationMode =
                "map".equals(alignment)
                        ? SymbolRotationMode.MAP_RELATIVE
                        : SymbolRotationMode.SCREEN_RELATIVE;

        Optional<PointLabelProfile> label =
                layout.containsKey("text-field")
                        ? label(layout, paint, location)
                        : noLabel(layout, paint, location);
        return new MapLibreSymbolSpec(
                icon,
                size,
                rotation,
                opacity,
                anchor,
                offset[0] * size,
                offset[1] * size,
                rotationMode,
                label,
                Optional.empty(),
                catalogReferences,
                limits.maximumCatalogReferences());
    }

    private static Optional<PointLabelProfile> label(
            Map<String, Object> layout, Map<String, Object> paint, String location) {
        Object iconValue = layout.get("icon-image");
        if (!(iconValue instanceof String)) {
            throw unsupported(location + "/layout/icon-image", "dynamicIconWithText");
        }
        requireTrue(layout, "icon-optional", location + "/layout");
        requireTrue(layout, "text-optional", location + "/layout");
        requireFalseOrAbsent(layout, "text-allow-overlap", location + "/layout");
        requireFalseOrAbsent(layout, "text-ignore-placement", location + "/layout");
        Object font = require(layout, "text-font", location + "/layout");
        if (!(font instanceof List<?> fonts)
                || fonts.size() != 1
                || !"SansSerif".equals(fonts.getFirst())) {
            throw value(location + "/layout/text-font", "font");
        }
        LabelTextSource text =
                textSource(layout.get("text-field"), location + "/layout/text-field");
        double textSize = number(layout, "text-size", 16, 1, 512, true, location + "/layout");
        Rgba color = color(paint, "text-color", Rgba.rgb(0, 0, 0), location + "/paint");
        double opacity = number(paint, "text-opacity", 1, 0, 1, true, location + "/paint");
        int alpha = (int) StrictMath.round(color.alpha() * opacity);
        Rgba effective = new Rgba(color.red(), color.green(), color.blue(), alpha);

        List<PointLabelPosition> positions =
                layout.containsKey("text-variable-anchor")
                        ? positions(
                                layout.get("text-variable-anchor"),
                                location + "/layout/text-variable-anchor")
                        : List.of(
                                labelPosition(
                                        string(
                                                layout,
                                                "text-anchor",
                                                "center",
                                                location + "/layout",
                                                anchorNames())));
        double[] offset = pair(layout, "text-offset", -64, 64, location + "/layout");
        double radial = number(layout, "text-radial-offset", 0, 0, 64, true, location + "/layout");
        if (radial != 0 && (offset[0] != 0 || offset[1] != 0)) {
            throw value(location + "/layout", "textOffsetConflict");
        }
        double padding = number(layout, "text-padding", 2, 0, 64, true, location + "/layout");
        int sortKey =
                integral(
                        layout,
                        "symbol-sort-key",
                        0,
                        -1_000_000_000,
                        1_000_000_000,
                        location + "/layout");
        if (effective.alpha() == 0) {
            return Optional.empty();
        }
        return Optional.of(
                new PointLabelProfile(
                        text,
                        new LabelTextStyle(effective, LabelWeight.NORMAL, textSize),
                        positions,
                        radial * textSize,
                        offset[0] * textSize,
                        offset[1] * textSize,
                        padding,
                        -sortKey,
                        ResolutionRange.ALL,
                        PointLabelAnchorBasis.FEATURE_POINT));
    }

    private static Optional<PointLabelProfile> noLabel(
            Map<String, Object> layout, Map<String, Object> paint, String location) {
        boolean textProperty =
                layout.keySet().stream().anyMatch(name -> name.startsWith("text-"))
                        || paint.keySet().stream().anyMatch(name -> name.startsWith("text-"));
        if (textProperty) {
            throw unsupported(location + "/layout", "textWithoutField");
        }
        if (layout.containsKey("icon-optional")) {
            throw unsupported(location + "/layout/icon-optional", "iconOnly");
        }
        return Optional.empty();
    }

    private static MapLibreSymbolSpec.IconExpression iconExpression(
            Object value,
            MapLibreReadLimits limits,
            CancellationToken cancellation,
            String location) {
        if (value instanceof String name) {
            return new MapLibreSymbolSpec.IconExpression.Literal(name);
        }
        List<Object> expression = expression(value, location);
        String operation = operation(expression, location);
        if ("get".equals(operation)) {
            return new MapLibreSymbolSpec.IconExpression.Attribute(
                    directGet(expression, location), false);
        }
        if ("to-string".equals(operation) && expression.size() == 2) {
            return new MapLibreSymbolSpec.IconExpression.Attribute(
                    directGet(expression.get(1), location + "/1"), true);
        }
        if ("match".equals(operation)) {
            if (expression.size() < 5 || (expression.size() & 1) == 0) {
                throw expressionType(location, "arity");
            }
            String attribute = directGet(expression.get(1), location + "/1");
            ArrayList<MapLibreSymbolSpec.MatchRule> rules = new ArrayList<>();
            HashSet<ThematicValue> categories = new HashSet<>();
            for (int index = 2; index < expression.size() - 1; index += 2) {
                ThematicValue category = thematic(expression.get(index), location + '/' + index);
                if (!categories.add(category)) {
                    throw value(location + '/' + index, "duplicateCategory");
                }
                rules.add(
                        new MapLibreSymbolSpec.MatchRule(
                                category,
                                literalName(
                                        expression.get(index + 1), location + '/' + (index + 1))));
            }
            return new MapLibreSymbolSpec.IconExpression.Match(
                    attribute,
                    rules,
                    literalName(expression.getLast(), location + '/' + (expression.size() - 1)));
        }
        if ("case".equals(operation)) {
            if (expression.size() < 4 || (expression.size() & 1) != 0) {
                throw expressionType(location, "arity");
            }
            ArrayList<MapLibreSymbolSpec.CaseRule> rules = new ArrayList<>();
            ArrayList<PortrayalPredicate> preceding = new ArrayList<>();
            int nodes = 0;
            for (int index = 1; index < expression.size() - 1; index += 2) {
                MapLibreFilters.CompiledFilter condition =
                        MapLibreFilters.compile(
                                expression.get(index),
                                limits,
                                nodes,
                                cancellation,
                                location + '/' + index);
                nodes += condition.nodes();
                PortrayalPredicate firstMatch = firstMatch(condition.predicate(), preceding);
                rules.add(
                        new MapLibreSymbolSpec.CaseRule(
                                firstMatch,
                                literalName(
                                        expression.get(index + 1), location + '/' + (index + 1))));
                preceding.add(condition.predicate());
            }
            return new MapLibreSymbolSpec.IconExpression.Case(
                    rules,
                    literalName(expression.getLast(), location + '/' + (expression.size() - 1)));
        }
        throw unsupported(location, "iconExpression");
    }

    private static LabelTextSource textSource(Object value, String location) {
        if (value instanceof String text) {
            try {
                return new LiteralLabelText(text);
            } catch (PointLabelTexts.ValidationException failure) {
                throw value(location, failure.reason().name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        List<Object> expression = expression(value, location);
        String operation = operation(expression, location);
        if ("get".equals(operation)) {
            return new TextAttribute(directGet(expression, location));
        }
        if ("to-string".equals(operation) && expression.size() == 2) {
            return new StringifiedTextAttribute(directGet(expression.get(1), location + "/1"));
        }
        throw unsupported(location, "textExpression");
    }

    private static List<PointLabelPosition> positions(Object value, String location) {
        if (!(value instanceof List<?> values) || values.isEmpty() || values.size() > 9) {
            throw value(location, "anchors");
        }
        ArrayList<PointLabelPosition> result = new ArrayList<>(values.size());
        HashSet<PointLabelPosition> unique = new HashSet<>();
        for (Object item : values) {
            if (!(item instanceof String name) || !anchorNames().contains(name)) {
                throw value(location, "anchor");
            }
            PointLabelPosition position = labelPosition(name);
            if (!unique.add(position)) {
                throw value(location, "duplicateAnchor");
            }
            result.add(position);
        }
        return List.copyOf(result);
    }

    private static PointLabelPosition labelPosition(String anchor) {
        return switch (anchor) {
            case "center" -> PointLabelPosition.CENTER;
            case "top" -> PointLabelPosition.N;
            case "top-right" -> PointLabelPosition.NE;
            case "right" -> PointLabelPosition.E;
            case "bottom-right" -> PointLabelPosition.SE;
            case "bottom" -> PointLabelPosition.S;
            case "bottom-left" -> PointLabelPosition.SW;
            case "left" -> PointLabelPosition.W;
            case "top-left" -> PointLabelPosition.NW;
            default -> throw new AssertionError(anchor);
        };
    }

    private static SymbolAnchor symbolAnchor(String anchor) {
        return switch (anchor) {
            case "center" -> SymbolAnchor.CENTER;
            case "top" -> SymbolAnchor.NORTH;
            case "top-right" -> SymbolAnchor.NORTH_EAST;
            case "right" -> SymbolAnchor.EAST;
            case "bottom-right" -> SymbolAnchor.SOUTH_EAST;
            case "bottom" -> SymbolAnchor.SOUTH;
            case "bottom-left" -> SymbolAnchor.SOUTH_WEST;
            case "left" -> SymbolAnchor.WEST;
            case "top-left" -> SymbolAnchor.NORTH_WEST;
            default -> throw new AssertionError(anchor);
        };
    }

    private static Set<String> anchorNames() {
        return Set.of(
                "center",
                "top",
                "top-right",
                "right",
                "bottom-right",
                "bottom",
                "bottom-left",
                "left",
                "top-left");
    }

    private static Object require(Map<String, Object> values, String name, String location) {
        if (!values.containsKey(name)) {
            throw value(location + '/' + name, "required");
        }
        return values.get(name);
    }

    private static void requireTrue(Map<String, Object> values, String name, String location) {
        if (!Boolean.TRUE.equals(values.get(name))) {
            throw value(location + '/' + name, "trueRequired");
        }
    }

    private static void requireFalseOrAbsent(
            Map<String, Object> values, String name, String location) {
        if (values.containsKey(name) && !Boolean.FALSE.equals(values.get(name))) {
            throw value(location + '/' + name, "falseRequired");
        }
    }

    private static String string(
            Map<String, Object> values,
            String name,
            String fallback,
            String location,
            Set<String> accepted) {
        if (!values.containsKey(name)) {
            if (fallback == null) {
                throw value(location + '/' + name, "required");
            }
            return fallback;
        }
        Object value = values.get(name);
        if (!(value instanceof String text) || !accepted.contains(text)) {
            throw value(location + '/' + name, "enum");
        }
        return text;
    }

    private static double number(
            Map<String, Object> values,
            String name,
            double fallback,
            double minimum,
            double maximum,
            boolean inclusiveMinimum,
            String location) {
        if (!values.containsKey(name)) {
            return fallback;
        }
        Object value = values.get(name);
        if (!(value instanceof BigDecimal decimal)) {
            throw value(location + '/' + name, "number");
        }
        double result = decimal.doubleValue();
        if (!Double.isFinite(result)
                || (inclusiveMinimum ? result < minimum : result <= minimum)
                || result > maximum) {
            throw value(location + '/' + name, "range");
        }
        return result == 0 ? 0 : result;
    }

    private static int integral(
            Map<String, Object> values,
            String name,
            int fallback,
            int minimum,
            int maximum,
            String location) {
        if (!values.containsKey(name)) {
            return fallback;
        }
        try {
            int result = ((BigDecimal) values.get(name)).intValueExact();
            if (result < minimum || result > maximum) {
                throw value(location + '/' + name, "range");
            }
            return result;
        } catch (ArithmeticException | ClassCastException failure) {
            throw value(location + '/' + name, "integer");
        }
    }

    private static PortrayalPredicate firstMatch(
            PortrayalPredicate current, List<PortrayalPredicate> preceding) {
        if (preceding.isEmpty()) {
            return current;
        }
        PortrayalPredicate earlier =
                preceding.size() == 1
                        ? preceding.getFirst()
                        : new PortrayalPredicate.Logical(
                                PortrayalLogicalOperator.OR, List.copyOf(preceding));
        PortrayalPredicate notEarlier =
                new PortrayalPredicate.Logical(PortrayalLogicalOperator.NOT, List.of(earlier));
        return new PortrayalPredicate.Logical(
                PortrayalLogicalOperator.AND, List.of(notEarlier, current));
    }

    private static double[] pair(
            Map<String, Object> values,
            String name,
            double minimum,
            double maximum,
            String location) {
        if (!values.containsKey(name)) {
            return new double[] {0, 0};
        }
        Object value = values.get(name);
        if (!(value instanceof List<?> pair)
                || pair.size() != 2
                || !(pair.get(0) instanceof BigDecimal x)
                || !(pair.get(1) instanceof BigDecimal y)) {
            throw value(location + '/' + name, "pair");
        }
        double xValue = x.doubleValue();
        double yValue = y.doubleValue();
        if (!Double.isFinite(xValue)
                || !Double.isFinite(yValue)
                || xValue < minimum
                || xValue > maximum
                || yValue < minimum
                || yValue > maximum) {
            throw value(location + '/' + name, "pairRange");
        }
        return new double[] {xValue, yValue};
    }

    private static Rgba color(
            Map<String, Object> values, String name, Rgba fallback, String location) {
        if (!values.containsKey(name)) {
            return fallback;
        }
        Object value = values.get(name);
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
            throw value(location + '/' + name, "color");
        }
    }

    private static String literalName(Object value, String location) {
        if (!(value instanceof String name) || name.isBlank() || !name.equals(name.strip())) {
            throw expressionType(location, "iconName");
        }
        return name;
    }

    private static int catalogReferences(MapLibreSymbolSpec.IconExpression expression) {
        return switch (expression) {
            case MapLibreSymbolSpec.IconExpression.Literal ignored -> 1;
            case MapLibreSymbolSpec.IconExpression.Attribute ignored -> 0;
            case MapLibreSymbolSpec.IconExpression.Match match -> match.rules().size() + 1;
            case MapLibreSymbolSpec.IconExpression.Case conditional ->
                    conditional.rules().size() + 1;
        };
    }

    private static ThematicValue thematic(Object value, String location) {
        if (value == AttributeNull.INSTANCE) {
            return ThematicValue.nullValue();
        }
        if (value instanceof String text) {
            return ThematicValue.text(text);
        }
        if (value instanceof Boolean logical) {
            return ThematicValue.logical(logical);
        }
        if (value instanceof BigDecimal number) {
            return ThematicValue.numeric(number);
        }
        throw expressionType(location, "category");
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
                || !(input.get(1) instanceof String attribute)
                || attribute.isBlank()) {
            throw expressionType(location, "directGet");
        }
        return attribute;
    }

    private static void members(Map<String, Object> values, Set<String> accepted, String location) {
        for (String name : values.keySet()) {
            if (name.endsWith("-transition") || !accepted.contains(name)) {
                throw unsupported(location + "/property", "property");
            }
        }
    }

    private static MapLibreReadException value(String location, String reason) {
        return MapLibreStyles.failure("MAPLIBRE_VALUE_INVALID", location, Map.of("reason", reason));
    }

    private static MapLibreReadException expressionType(String location, String reason) {
        return MapLibreStyles.failure(
                "MAPLIBRE_EXPRESSION_TYPE", location, Map.of("reason", reason));
    }

    private static MapLibreReadException unsupported(String location, String reason) {
        return MapLibreStyles.failure(
                "MAPLIBRE_PROPERTY_UNSUPPORTED", location, Map.of("reason", reason));
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
}
