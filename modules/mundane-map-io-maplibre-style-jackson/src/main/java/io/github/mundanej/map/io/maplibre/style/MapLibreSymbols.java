package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
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
            boolean renderingRequired) {
        return switch (type) {
            case CIRCLE -> circle(paint, location + "/paint");
            case LINE -> line(layout, paint, location, renderingRequired);
            case FILL -> fill(paint, location + "/paint");
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
