package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CrsAxisDirection;
import io.github.mundanej.map.api.CrsEllipsoid;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.WktCrsAxis;
import io.github.mundanej.map.api.WktCrsDefinition;
import io.github.mundanej.map.api.WktCrsKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Bounded parser and canonical writer for the declared WKT2:2019 CRS subset. */
@SuppressWarnings("StringConcatToTextBlock")
public final class Wkt2 {
    /** Maximum accepted WKT characters. */
    public static final int MAXIMUM_CHARACTERS = 16_384;

    /** Maximum nested bracket depth. */
    public static final int MAXIMUM_DEPTH = 32;

    /** Maximum syntax nodes and scalar values. */
    public static final int MAXIMUM_VALUES = 4_096;

    private static final Set<String> SUPPORTED_KEYWORDS =
            Set.of(
                    "GEOGCRS",
                    "GEODCRS",
                    "PROJCRS",
                    "VERTCRS",
                    "COMPOUNDCRS",
                    "BASEGEOGCRS",
                    "BASEGEODCRS",
                    "DATUM",
                    "GEODETICDATUM",
                    "VDATUM",
                    "VERTICALDATUM",
                    "ELLIPSOID",
                    "PRIMEM",
                    "CS",
                    "AXIS",
                    "ORDER",
                    "ANGLEUNIT",
                    "LENGTHUNIT",
                    "SCALEUNIT",
                    "CONVERSION",
                    "METHOD",
                    "PARAMETER",
                    "ID",
                    "SCOPE",
                    "AREA",
                    "BBOX",
                    "USAGE",
                    "REMARK");

    private Wkt2() {}

    /**
     * Parses one complete WKT2:2019 definition under fixed syntax limits.
     *
     * <p>The supported roots are {@code GEOGCRS}/{@code GEODCRS}, {@code PROJCRS}, {@code VERTCRS},
     * and {@code COMPOUNDCRS}. Datum, ellipsoid, prime-meridian, CS, AXIS/ORDER, angle/length/scale
     * unit, conversion method/parameter, authority ID, usage, and remark nodes are accepted.
     * Unsupported keywords fail explicitly; no WKT1 guessing occurs.
     *
     * @param wkt complete WKT2 text
     * @return immutable semantic definition
     * @throws CrsException for a stable input-limit, syntax, or unsupported-profile failure
     */
    public static WktCrsDefinition parse(String wkt) {
        Objects.requireNonNull(wkt, "wkt");
        if (wkt.length() > MAXIMUM_CHARACTERS) {
            throw failure("CRS_WKT_INPUT_LIMIT", "WKT2 input exceeds its character limit");
        }
        try {
            Parser parser = new Parser(wkt);
            Node root = parser.parse();
            validateKeywords(root);
            return semantic(root);
        } catch (LimitFailure failure) {
            throw failure("CRS_WKT_INPUT_LIMIT", "WKT2 input exceeds its structural limits");
        } catch (ProfileFailure failure) {
            throw failure(
                    "CRS_WKT_PROFILE_UNSUPPORTED",
                    "WKT2 construct is outside the supported profile");
        } catch (SyntaxFailure | IllegalArgumentException failure) {
            throw failure("CRS_WKT_SYNTAX_INVALID", "WKT2 syntax or value is invalid");
        }
    }

    /**
     * Writes one semantic definition in deterministic canonical WKT2:2019 form.
     *
     * <p>Axis order/direction and SI conversion factors are retained. Parameters are emitted in
     * sorted name order using radians, metres, or unity as appropriate. Parsing the result produces
     * an equal definition.
     *
     * @param definition immutable semantic definition
     * @return canonical WKT2 text
     */
    public static String write(WktCrsDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        StringBuilder output = new StringBuilder();
        writeDefinition(definition, output);
        if (output.length() > MAXIMUM_CHARACTERS) {
            throw failure(
                    "CRS_WKT_INPUT_LIMIT", "Canonical WKT2 output exceeds its character limit");
        }
        return output.toString();
    }

    private static WktCrsDefinition semantic(Node root) {
        return switch (root.keyword) {
            case "GEOGCRS", "GEODCRS" -> geographic(root);
            case "PROJCRS" -> projected(root);
            case "VERTCRS" -> vertical(root);
            case "COMPOUNDCRS" -> compound(root);
            default -> throw new ProfileFailure();
        };
    }

    private static WktCrsDefinition geographic(Node node) {
        Node datum = requiredEither(node, "DATUM", "GEODETICDATUM");
        CrsEllipsoid ellipsoid = ellipsoid(required(datum, "ELLIPSOID"));
        return new WktCrsDefinition(
                text(node, 0),
                WktCrsKind.GEOGRAPHIC,
                identifier(node),
                Optional.of(text(datum, 0)),
                Optional.of(ellipsoid),
                axes(node, "ANGLEUNIT"),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    private static WktCrsDefinition projected(Node node) {
        Node base = requiredEither(node, "BASEGEOGCRS", "BASEGEODCRS");
        Node datum = requiredEither(base, "DATUM", "GEODETICDATUM");
        Node conversion = required(node, "CONVERSION");
        Node method = required(conversion, "METHOD");
        TreeMap<String, Double> parameters = new TreeMap<>();
        for (Node parameter : children(conversion, "PARAMETER")) {
            String name = text(parameter, 0);
            double value = number(parameter, 1) * unitFactor(parameter);
            if (parameters.put(name, value) != null) {
                throw new SyntaxFailure();
            }
        }
        return new WktCrsDefinition(
                text(node, 0),
                WktCrsKind.PROJECTED,
                identifier(node),
                Optional.of(text(datum, 0)),
                Optional.of(ellipsoid(required(datum, "ELLIPSOID"))),
                axes(node, "LENGTHUNIT"),
                identifier(base),
                Optional.of(text(method, 0)),
                parameters,
                List.of());
    }

    private static WktCrsDefinition vertical(Node node) {
        Node datum = requiredEither(node, "VDATUM", "VERTICALDATUM");
        return new WktCrsDefinition(
                text(node, 0),
                WktCrsKind.VERTICAL,
                identifier(node),
                Optional.of(text(datum, 0)),
                Optional.empty(),
                axes(node, "LENGTHUNIT"),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                List.of());
    }

    private static WktCrsDefinition compound(Node node) {
        List<WktCrsDefinition> components = new ArrayList<>();
        for (Value value : node.values) {
            if (value instanceof Node child && isRoot(child.keyword)) {
                components.add(semantic(child));
            }
        }
        if (components.size() < 2) {
            throw new SyntaxFailure();
        }
        return new WktCrsDefinition(
                text(node, 0),
                WktCrsKind.COMPOUND,
                identifier(node),
                Optional.empty(),
                Optional.empty(),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Map.of(),
                components);
    }

    private static List<WktCrsAxis> axes(Node node, String defaultUnitKeyword) {
        Node defaultUnit = optional(node, defaultUnitKeyword).orElse(null);
        List<WktCrsAxis> result = new ArrayList<>();
        for (Node axis : children(node, "AXIS")) {
            String axisName = text(axis, 0);
            String abbreviation = abbreviation(axisName);
            CrsAxisDirection direction = direction(atom(axis, 1));
            int order = Math.toIntExact(Math.round(number(required(axis, "ORDER"), 0)));
            Node unit =
                    optional(axis, "ANGLEUNIT")
                            .or(() -> optional(axis, "LENGTHUNIT"))
                            .orElse(defaultUnit);
            if (unit == null) {
                throw new SyntaxFailure();
            }
            result.add(
                    new WktCrsAxis(
                            axisName,
                            abbreviation,
                            direction,
                            order,
                            text(unit, 0),
                            number(unit, 1)));
        }
        result.sort(java.util.Comparator.comparingInt(WktCrsAxis::order));
        if (result.isEmpty()) {
            throw new SyntaxFailure();
        }
        return List.copyOf(result);
    }

    private static String abbreviation(String axisName) {
        int open = axisName.lastIndexOf('(');
        int close = axisName.endsWith(")") ? axisName.length() - 1 : -1;
        if (open >= 0 && close > open + 1) {
            return axisName.substring(open + 1, close);
        }
        return axisName.substring(0, 1);
    }

    private static CrsAxisDirection direction(String value) {
        try {
            return CrsAxisDirection.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new ProfileFailure();
        }
    }

    private static CrsEllipsoid ellipsoid(Node node) {
        return new CrsEllipsoid(text(node, 0), number(node, 1), number(node, 2));
    }

    private static double unitFactor(Node parameter) {
        for (String keyword : List.of("ANGLEUNIT", "LENGTHUNIT", "SCALEUNIT")) {
            Optional<Node> unit = optional(parameter, keyword);
            if (unit.isPresent()) {
                return number(unit.orElseThrow(), 1);
            }
        }
        throw new SyntaxFailure();
    }

    private static Optional<String> identifier(Node node) {
        Optional<Node> id = optional(node, "ID");
        if (id.isEmpty()) {
            return Optional.empty();
        }
        Node value = id.orElseThrow();
        String authority = text(value, 0);
        Value code = value.values.size() > 1 ? value.values.get(1) : null;
        String rendered;
        if (code instanceof Text text) {
            rendered = text.value;
        } else if (code instanceof NumberValue number) {
            rendered = integralLexeme(number.lexeme);
        } else {
            throw new SyntaxFailure();
        }
        return Optional.of(authority + ":" + rendered);
    }

    private static String integralLexeme(String lexeme) {
        double value = Double.parseDouble(lexeme);
        long integral = (long) value;
        if (!Double.isFinite(value) || value != integral) {
            throw new SyntaxFailure();
        }
        return Long.toString(integral);
    }

    private static Node requiredEither(Node parent, String first, String second) {
        return optional(parent, first)
                .or(() -> optional(parent, second))
                .orElseThrow(SyntaxFailure::new);
    }

    private static Node required(Node parent, String keyword) {
        return optional(parent, keyword).orElseThrow(SyntaxFailure::new);
    }

    private static Optional<Node> optional(Node parent, String keyword) {
        List<Node> matches = children(parent, keyword);
        if (matches.size() > 1) {
            throw new SyntaxFailure();
        }
        return matches.stream().findFirst();
    }

    private static List<Node> children(Node parent, String keyword) {
        List<Node> result = new ArrayList<>();
        for (Value value : parent.values) {
            if (value instanceof Node node && node.keyword.equals(keyword)) {
                result.add(node);
            }
        }
        return result;
    }

    private static String text(Node node, int index) {
        if (index >= node.values.size() || !(node.values.get(index) instanceof Text text)) {
            throw new SyntaxFailure();
        }
        return text.value;
    }

    private static String atom(Node node, int index) {
        if (index >= node.values.size() || !(node.values.get(index) instanceof Atom atom)) {
            throw new SyntaxFailure();
        }
        return atom.value;
    }

    private static double number(Node node, int index) {
        if (index >= node.values.size()
                || !(node.values.get(index) instanceof NumberValue number)) {
            throw new SyntaxFailure();
        }
        double value = Double.parseDouble(number.lexeme);
        if (!Double.isFinite(value)) {
            throw new SyntaxFailure();
        }
        return value;
    }

    private static void validateKeywords(Node node) {
        if (!SUPPORTED_KEYWORDS.contains(node.keyword)) {
            throw new ProfileFailure();
        }
        for (Value value : node.values) {
            if (value instanceof Node child) {
                validateKeywords(child);
            }
        }
    }

    private static boolean isRoot(String keyword) {
        return keyword.equals("GEOGCRS")
                || keyword.equals("GEODCRS")
                || keyword.equals("PROJCRS")
                || keyword.equals("VERTCRS")
                || keyword.equals("COMPOUNDCRS");
    }

    private static void writeDefinition(WktCrsDefinition definition, StringBuilder output) {
        switch (definition.kind()) {
            case GEOGRAPHIC -> writeGeographic(definition, output);
            case PROJECTED -> writeProjected(definition, output);
            case VERTICAL -> writeVertical(definition, output);
            case COMPOUND -> writeCompound(definition, output);
        }
    }

    private static void writeGeographic(WktCrsDefinition definition, StringBuilder output) {
        output.append("GEOGCRS[");
        quoted(output, definition.name());
        writeDatum(definition, output);
        output.append(",CS[ellipsoidal,").append(definition.axes().size()).append(']');
        writeAxes(definition.axes(), output);
        writeIdentifier(definition.identifier(), output);
        output.append(']');
    }

    private static void writeProjected(WktCrsDefinition definition, StringBuilder output) {
        output.append("PROJCRS[");
        quoted(output, definition.name());
        output.append(",BASEGEOGCRS[");
        quoted(output, definition.baseIdentifier().orElse("unnamed base"));
        writeDatum(definition, output);
        writeIdentifier(definition.baseIdentifier(), output);
        output.append("],CONVERSION[");
        quoted(output, definition.operationMethod().orElseThrow());
        output.append(",METHOD[");
        quoted(output, definition.operationMethod().orElseThrow());
        output.append(']');
        definition.parameters().forEach((name, value) -> writeParameter(output, name, value));
        output.append("],CS[Cartesian,").append(definition.axes().size()).append(']');
        writeAxes(definition.axes(), output);
        writeIdentifier(definition.identifier(), output);
        output.append(']');
    }

    private static void writeVertical(WktCrsDefinition definition, StringBuilder output) {
        output.append("VERTCRS[");
        quoted(output, definition.name());
        output.append(",VDATUM[");
        quoted(output, definition.datumName().orElseThrow());
        output.append("],CS[vertical,").append(definition.axes().size()).append(']');
        writeAxes(definition.axes(), output);
        writeIdentifier(definition.identifier(), output);
        output.append(']');
    }

    private static void writeCompound(WktCrsDefinition definition, StringBuilder output) {
        output.append("COMPOUNDCRS[");
        quoted(output, definition.name());
        for (WktCrsDefinition component : definition.components()) {
            output.append(',');
            writeDefinition(component, output);
        }
        writeIdentifier(definition.identifier(), output);
        output.append(']');
    }

    private static void writeDatum(WktCrsDefinition definition, StringBuilder output) {
        output.append(",DATUM[");
        quoted(output, definition.datumName().orElseThrow());
        CrsEllipsoid ellipsoid = definition.ellipsoid().orElseThrow();
        output.append(",ELLIPSOID[");
        quoted(output, ellipsoid.name());
        output.append(',')
                .append(number(ellipsoid.semiMajorAxis()))
                .append(',')
                .append(number(ellipsoid.inverseFlattening()))
                .append(",LENGTHUNIT[\"metre\",1]]]");
    }

    private static void writeAxes(List<WktCrsAxis> axes, StringBuilder output) {
        for (WktCrsAxis axis : axes) {
            output.append(",AXIS[");
            quoted(output, axis.name());
            output.append(',')
                    .append(axis.direction().name().toLowerCase(Locale.ROOT))
                    .append(",ORDER[")
                    .append(axis.order())
                    .append(']');
            boolean angular =
                    axis.direction() == CrsAxisDirection.EAST
                            || axis.direction() == CrsAxisDirection.WEST
                            || axis.direction() == CrsAxisDirection.NORTH
                            || axis.direction() == CrsAxisDirection.SOUTH;
            output.append(angular && axis.unitToSi() < 0.1 ? ",ANGLEUNIT[" : ",LENGTHUNIT[");
            quoted(output, axis.unitName());
            output.append(',').append(number(axis.unitToSi())).append("]]");
        }
    }

    private static void writeParameter(StringBuilder output, String name, double value) {
        output.append(",PARAMETER[");
        quoted(output, name);
        output.append(',').append(number(value));
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("latitude")
                || lower.contains("longitude")
                || lower.contains("meridian")) {
            output.append(",ANGLEUNIT[\"radian\",1]]");
        } else if (lower.contains("scale")) {
            output.append(",SCALEUNIT[\"unity\",1]]");
        } else {
            output.append(",LENGTHUNIT[\"metre\",1]]");
        }
    }

    private static void writeIdentifier(Optional<String> identifier, StringBuilder output) {
        if (identifier.isEmpty()) {
            return;
        }
        String value = identifier.orElseThrow();
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException(
                    "Canonical CRS identifier must contain authority and code");
        }
        output.append(",ID[");
        quoted(output, value.substring(0, separator));
        output.append(',');
        String code = value.substring(separator + 1);
        if (code.chars().allMatch(Character::isDigit)) {
            output.append(code);
        } else {
            quoted(output, code);
        }
        output.append(']');
    }

    private static void quoted(StringBuilder output, String value) {
        output.append('"').append(value.replace("\"", "\"\"")).append('"');
    }

    private static String number(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canonical WKT number must be positive or finite");
        }
        return Double.toString(value);
    }

    private static CrsException failure(String code, String message) {
        return new CrsException(new CrsProblem(code, message, Map.of()));
    }

    private sealed interface Value permits Node, Text, NumberValue, Atom {}

    private static final class Node implements Value {
        private final String keyword;
        private final List<Value> values;

        private Node(String keyword, List<Value> values) {
            this.keyword = keyword;
            this.values = List.copyOf(values);
        }
    }

    private record Text(String value) implements Value {}

    private record NumberValue(String lexeme) implements Value {}

    private record Atom(String value) implements Value {}

    @SuppressWarnings("serial")
    private static final class SyntaxFailure extends RuntimeException {}

    @SuppressWarnings("serial")
    private static final class ProfileFailure extends RuntimeException {}

    @SuppressWarnings("serial")
    private static final class LimitFailure extends RuntimeException {}

    private static final class Parser {
        private final String input;
        private int position;
        private int values;

        private Parser(String input) {
            this.input = input;
        }

        private Node parse() {
            skipSpace();
            Node result = node(1);
            skipSpace();
            if (position != input.length()) {
                throw new SyntaxFailure();
            }
            return result;
        }

        private Node node(int depth) {
            if (depth > MAXIMUM_DEPTH) {
                throw new LimitFailure();
            }
            String keyword = identifier().toUpperCase(Locale.ROOT);
            skipSpace();
            require('[');
            List<Value> arguments = new ArrayList<>();
            skipSpace();
            if (peek(']')) {
                throw new SyntaxFailure();
            }
            while (true) {
                arguments.add(value(depth));
                count();
                skipSpace();
                if (peek(']')) {
                    position++;
                    break;
                }
                require(',');
                skipSpace();
            }
            return new Node(keyword, arguments);
        }

        private Value value(int depth) {
            skipSpace();
            if (position >= input.length()) {
                throw new SyntaxFailure();
            }
            char current = input.charAt(position);
            if (current == '"') {
                return new Text(string());
            }
            if (current == '+' || current == '-' || current == '.' || Character.isDigit(current)) {
                return new NumberValue(numberLexeme());
            }
            int start = position;
            String identifier = identifier();
            skipSpace();
            if (peek('[')) {
                position = start;
                return node(depth + 1);
            }
            return new Atom(identifier);
        }

        private String identifier() {
            skipSpace();
            int start = position;
            while (position < input.length()) {
                char value = input.charAt(position);
                if (!Character.isLetterOrDigit(value) && value != '_') {
                    break;
                }
                position++;
            }
            if (position == start) {
                throw new SyntaxFailure();
            }
            return input.substring(start, position);
        }

        private String string() {
            require('"');
            StringBuilder result = new StringBuilder();
            while (position < input.length()) {
                char value = input.charAt(position++);
                if (value != '"') {
                    result.append(value);
                    continue;
                }
                if (position < input.length() && input.charAt(position) == '"') {
                    position++;
                    result.append('"');
                    continue;
                }
                if (result.isEmpty() || result.length() > 256) {
                    throw new SyntaxFailure();
                }
                return result.toString();
            }
            throw new SyntaxFailure();
        }

        private String numberLexeme() {
            int start = position;
            while (position < input.length()) {
                char value = input.charAt(position);
                if (!Character.isDigit(value)
                        && value != '+'
                        && value != '-'
                        && value != '.'
                        && value != 'e'
                        && value != 'E') {
                    break;
                }
                position++;
            }
            String result = input.substring(start, position);
            try {
                Double.parseDouble(result);
            } catch (NumberFormatException failure) {
                throw new SyntaxFailure();
            }
            return result;
        }

        private void require(char expected) {
            if (position >= input.length() || input.charAt(position) != expected) {
                throw new SyntaxFailure();
            }
            position++;
        }

        private boolean peek(char expected) {
            return position < input.length() && input.charAt(position) == expected;
        }

        private void skipSpace() {
            while (position < input.length() && Character.isWhitespace(input.charAt(position))) {
                position++;
            }
        }

        private void count() {
            values++;
            if (values > MAXIMUM_VALUES) {
                throw new LimitFailure();
            }
        }
    }
}
