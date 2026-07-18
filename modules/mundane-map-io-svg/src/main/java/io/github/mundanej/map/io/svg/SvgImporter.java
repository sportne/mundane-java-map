package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.api.VectorPathCommand;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class SvgImporter {
    private static final String SVG_NAMESPACE = "http://www.w3.org/2000/svg";
    private static final double KAPPA = 4.0 * (StrictMath.sqrt(2.0) - 1.0) / 3.0;

    private final String sourceId;
    private final byte[] bytes;
    private final MarkerPlacement placement;
    private final SvgImportLimits limits;
    private final CancellationToken cancellation;
    private final SvgImportBudget budget;
    private final List<Symbol> output = new ArrayList<>();
    private final Deque<Frame> frames = new ArrayDeque<>();
    private Envelope viewBox;
    private int elementCount;
    private int attributeCount;
    private long attributeCharacters;
    private int transformFunctions;
    private int transformDepth;
    private int xmlEvents;

    SvgImporter(
            String sourceId,
            byte[] bytes,
            MarkerPlacement placement,
            SvgImportLimits limits,
            CancellationToken cancellation,
            long initialOwnedBytes) {
        this.sourceId = sourceId;
        this.bytes = bytes;
        this.placement = placement;
        this.limits = limits;
        this.cancellation = cancellation;
        budget = new SvgImportBudget(sourceId, limits, initialOwnedBytes);
    }

    Symbol importSymbol() {
        String xml = decode();
        checkCancellation();
        if (xml.indexOf('&') >= 0) {
            throw SvgFailures.xml(sourceId, "reference");
        }
        XMLInputFactory factory = secureFactory();
        XMLStreamReader reader;
        try {
            reader = factory.createXMLStreamReader(new ByteArrayInputStream(bytes), "UTF-8");
        } catch (XMLStreamException exception) {
            throw SvgFailures.xml(sourceId, "malformed");
        }
        RuntimeException failure = null;
        try {
            validateXmlDeclaration(reader);
            parse(reader);
        } catch (XMLStreamException exception) {
            failure = SvgFailures.xml(sourceId, "malformed");
        } catch (RuntimeException exception) {
            failure = exception;
        }
        try {
            reader.close();
        } catch (XMLStreamException exception) {
            RuntimeException cleanup = SvgFailures.xml(sourceId, "unexpectedEvent");
            if (failure == null) {
                failure = cleanup;
            } else {
                failure.addSuppressed(cleanup);
            }
        }
        if (failure != null) {
            throw failure;
        }
        if (!frames.isEmpty()) {
            throw SvgFailures.xml(sourceId, "malformed");
        }
        if (output.isEmpty()) {
            throw SvgFailures.failure(
                    sourceId, "SVG_EMPTY_GRAPHIC", "SVG contains no painted graphic", Map.of());
        }
        if (output.size() == 1) {
            return output.get(0);
        }
        budget.chargeOwned((long) output.size() * Long.BYTES);
        checkCancellation();
        return CompositeSymbol.of(output, 1.0);
    }

    private void validateXmlDeclaration(XMLStreamReader reader) {
        String version = reader.getVersion();
        String encoding = reader.getCharacterEncodingScheme();
        if ((version != null && !version.equals("1.0"))
                || (encoding != null && !encoding.equalsIgnoreCase("UTF-8"))) {
            throw SvgFailures.unsupported(sourceId, "xmlDeclaration");
        }
    }

    private String decode() {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            throw SvgFailures.encoding(sourceId, "bom");
        }
        try {
            return StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw SvgFailures.encoding(sourceId, "malformedUtf8");
        }
    }

    private XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        requireProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        requireProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        requireProperty(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        requireProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
        requireProperty(factory, XMLInputFactory.IS_VALIDATING, false);
        requireProperty(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        requireProperty(factory, XMLConstants.USE_CATALOG, false);
        XMLResolver reject =
                (publicId, systemId, baseUri, namespace) -> {
                    throw new XMLStreamException("external resolution disabled");
                };
        factory.setXMLResolver(reject);
        return factory;
    }

    private static void requireProperty(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
            if (!value.equals(factory.getProperty(name))) {
                throw new IllegalStateException("Ineffective secure XML property: " + name);
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported secure XML property: " + name, exception);
        }
    }

    private void parse(XMLStreamReader reader) throws XMLStreamException {
        boolean rootSeen = false;
        boolean rootClosed = false;
        while (reader.hasNext()) {
            int event = reader.next();
            if ((++xmlEvents & 4095) == 0) {
                checkCancellation();
            }
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    if (rootClosed) {
                        throw SvgFailures.xml(sourceId, "unexpectedEvent");
                    }
                    start(reader, rootSeen);
                    rootSeen = true;
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (frames.isEmpty()) {
                        throw SvgFailures.xml(sourceId, "unexpectedEvent");
                    }
                    Frame frame = frames.removeLast();
                    if (frame.hasTransform()) {
                        transformDepth--;
                    }
                    if (frames.isEmpty()) {
                        rootClosed = true;
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (!reader.isWhiteSpace()) {
                        throw SvgFailures.unsupported(sourceId, "characterData");
                    }
                }
                case XMLStreamConstants.COMMENT, XMLStreamConstants.SPACE -> {}
                case XMLStreamConstants.DTD -> throw SvgFailures.xml(sourceId, "doctype");
                case XMLStreamConstants.ENTITY_REFERENCE ->
                        throw SvgFailures.xml(sourceId, "entity");
                case XMLStreamConstants.PROCESSING_INSTRUCTION ->
                        throw SvgFailures.xml(sourceId, "processingInstruction");
                case XMLStreamConstants.START_DOCUMENT, XMLStreamConstants.END_DOCUMENT -> {}
                default -> throw SvgFailures.xml(sourceId, "unexpectedEvent");
            }
        }
        if (!rootSeen || !rootClosed) {
            throw SvgFailures.xml(sourceId, "malformed");
        }
    }

    private void start(XMLStreamReader reader, boolean rootSeen) {
        checkCancellation();
        int depth = frames.size() + 1;
        if (++elementCount > limits.maximumElements()) {
            throw SvgFailures.limit(sourceId, "elements", elementCount, limits.maximumElements());
        }
        if (depth > limits.maximumElementDepth()) {
            throw SvgFailures.limit(sourceId, "elementDepth", depth, limits.maximumElementDepth());
        }
        if (reader.getPrefix() != null && !reader.getPrefix().isEmpty()) {
            throw SvgFailures.unsupported(sourceId, "qualifiedName");
        }
        if (!SVG_NAMESPACE.equals(reader.getNamespaceURI())) {
            throw SvgFailures.unsupported(sourceId, "namespace");
        }
        String name = reader.getLocalName();
        if (!isSupportedElement(name)) {
            throw SvgFailures.unsupported(sourceId, "element");
        }
        if (!rootSeen && !name.equals("svg")) {
            throw SvgFailures.unsupported(sourceId, "root");
        }
        if (rootSeen && name.equals("svg")) {
            throw SvgFailures.unsupported(sourceId, "nestedSvg");
        }
        if (!frames.isEmpty() && !frames.peekLast().container()) {
            throw SvgFailures.unsupported(sourceId, "element");
        }
        if (reader.getNamespaceCount() != (depth == 1 ? 1 : 0)
                || (depth == 1
                        && (reader.getNamespacePrefix(0) != null
                                || !SVG_NAMESPACE.equals(reader.getNamespaceURI(0))))) {
            throw SvgFailures.unsupported(sourceId, "namespace");
        }

        Map<String, String> attributes = attributes(reader, name);
        if (depth == 1) {
            parseRoot(attributes);
        }
        PathShape shape = depth > 1 && !name.equals("g") ? geometry(name, attributes) : null;
        Style inherited = frames.isEmpty() ? Style.defaults() : frames.peekLast().style();
        Style style = resolveStyle(inherited, attributes);
        Affine parent = frames.isEmpty() ? Affine.IDENTITY : frames.peekLast().transform();
        boolean hasTransform = attributes.containsKey("transform");
        Affine local = hasTransform ? parseTransform(attributes.get("transform")) : Affine.IDENTITY;
        Affine transform;
        try {
            transform = parent.multiply(local);
        } catch (IllegalArgumentException exception) {
            throw SvgFailures.value(sourceId, "transform", "overflow");
        }
        validateTransform(transform);
        if (hasTransform && ++transformDepth > limits.maximumTransformAncestorDepth()) {
            throw SvgFailures.limit(
                    sourceId,
                    "transformAncestorDepth",
                    transformDepth,
                    limits.maximumTransformAncestorDepth());
        }

        if (shape != null) {
            emit(shape, attributes, style, transform);
        }
        budget.chargeOwned(128L);
        frames.addLast(
                new Frame(style, transform, hasTransform, name.equals("svg") || name.equals("g")));
    }

    private Map<String, String> attributes(XMLStreamReader reader, String element) {
        Map<String, String> result = new HashMap<>();
        Set<String> allowed = allowedAttributes(element);
        List<String> unsupported = new ArrayList<>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            if ((reader.getAttributePrefix(index) != null
                            && !reader.getAttributePrefix(index).isEmpty())
                    || (reader.getAttributeNamespace(index) != null
                            && !reader.getAttributeNamespace(index).isEmpty())) {
                throw SvgFailures.unsupported(sourceId, "qualifiedName");
            }
            String name = reader.getAttributeLocalName(index);
            String value = reader.getAttributeValue(index);
            budget.chargeOwned(
                    Math.addExact(
                            64L, Math.multiplyExact((long) name.length() + value.length(), 2L)));
            if (++attributeCount > limits.maximumAttributes()) {
                throw SvgFailures.limit(
                        sourceId, "attributes", attributeCount, limits.maximumAttributes());
            }
            if (value.length() > limits.maximumAttributeCharacters()) {
                throw SvgFailures.limit(
                        sourceId,
                        "attributeCharacters",
                        value.length(),
                        limits.maximumAttributeCharacters());
            }
            attributeCharacters = Math.addExact(attributeCharacters, value.length());
            if (attributeCharacters > limits.maximumAggregateAttributeCharacters()) {
                throw SvgFailures.limit(
                        sourceId,
                        "aggregateAttributeCharacters",
                        attributeCharacters,
                        limits.maximumAggregateAttributeCharacters());
            }
            if (!allowed.contains(name)) {
                budget.chargeOwned(16L);
                unsupported.add(name);
            }
            result.put(name, value);
        }
        if (!unsupported.isEmpty()) {
            unsupported.sort(String::compareTo);
            throw SvgFailures.unsupported(sourceId, "attribute");
        }
        return result;
    }

    private static Set<String> allowedAttributes(String element) {
        Set<String> allowed = new HashSet<>();
        allowed.addAll(
                Set.of(
                        "fill",
                        "stroke",
                        "stroke-width",
                        "fill-opacity",
                        "stroke-opacity",
                        "fill-rule",
                        "stroke-linecap",
                        "stroke-linejoin"));
        switch (element) {
            case "svg" -> allowed.addAll(Set.of("viewBox", "version", "preserveAspectRatio"));
            case "g" -> allowed.add("transform");
            case "path" -> allowed.addAll(Set.of("d", "transform", "opacity"));
            case "rect" ->
                    allowed.addAll(Set.of("x", "y", "width", "height", "transform", "opacity"));
            case "circle" -> allowed.addAll(Set.of("cx", "cy", "r", "transform", "opacity"));
            case "ellipse" ->
                    allowed.addAll(Set.of("cx", "cy", "rx", "ry", "transform", "opacity"));
            case "line" -> allowed.addAll(Set.of("x1", "y1", "x2", "y2", "transform", "opacity"));
            case "polyline", "polygon" -> allowed.addAll(Set.of("points", "transform", "opacity"));
            default -> throw new IllegalStateException("unexpected SVG element");
        }
        return allowed;
    }

    private static boolean isSupportedElement(String element) {
        return switch (element) {
            case "svg", "g", "path", "rect", "circle", "ellipse", "line", "polyline", "polygon" ->
                    true;
            default -> false;
        };
    }

    private void parseRoot(Map<String, String> attributes) {
        if (!attributes.containsKey("viewBox")) {
            throw SvgFailures.value(sourceId, "viewBox", "missing");
        }
        double[] values = numbers(attributes.get("viewBox"), "viewBox", 4, 4);
        if (values[2] <= 0 || values[3] <= 0) {
            throw SvgFailures.value(sourceId, "viewBox", "range");
        }
        if (attributes.containsKey("version")
                && !Set.of("1.1", "2.0").contains(attributes.get("version"))) {
            throw SvgFailures.value(sourceId, "version", "range");
        }
        String aspect = attributes.getOrDefault("preserveAspectRatio", "xMidYMid meet");
        if (!aspect.equals("none") && !aspect.equals("xMidYMid meet")) {
            throw SvgFailures.value(sourceId, "preserveAspectRatio", "range");
        }
        double minX = values[0];
        double minY = values[1];
        double width = values[2];
        double height = values[3];
        if (aspect.equals("xMidYMid meet")) {
            double target = placement.size().width() / placement.size().height();
            double source = width / height;
            if (source > target) {
                double expanded = width / target;
                minY -= (expanded - height) / 2.0;
                height = expanded;
            } else if (source < target) {
                double expanded = height * target;
                minX -= (expanded - width) / 2.0;
                width = expanded;
            }
        }
        try {
            viewBox = new Envelope(minX, minY, minX + width, minY + height);
        } catch (IllegalArgumentException exception) {
            throw SvgFailures.value(sourceId, "viewBox", "overflow");
        }
    }

    private Style resolveStyle(Style inherited, Map<String, String> attributes) {
        String fill = attributes.getOrDefault("fill", inherited.fill());
        String stroke = attributes.getOrDefault("stroke", inherited.stroke());
        double strokeWidth =
                number(
                        attributes.getOrDefault(
                                "stroke-width", Double.toString(inherited.strokeWidth())),
                        "stroke-width");
        double fillOpacity =
                opacity(
                        attributes.getOrDefault(
                                "fill-opacity", Double.toString(inherited.fillOpacity())),
                        "fill-opacity");
        double strokeOpacity =
                opacity(
                        attributes.getOrDefault(
                                "stroke-opacity", Double.toString(inherited.strokeOpacity())),
                        "stroke-opacity");
        String fillRule = attributes.getOrDefault("fill-rule", inherited.fillRule());
        String cap = attributes.getOrDefault("stroke-linecap", inherited.cap());
        String join = attributes.getOrDefault("stroke-linejoin", inherited.join());
        validatePaint(fill, "fill");
        validatePaint(stroke, "stroke");
        if (!stroke.equals("none") && strokeWidth <= 0.0) {
            throw SvgFailures.value(sourceId, "stroke-width", "range");
        }
        if (!Set.of("evenodd", "nonzero").contains(fillRule)) {
            throw SvgFailures.value(sourceId, "fill-rule", "range");
        }
        if (!Set.of("round", "butt", "square").contains(cap)) {
            throw SvgFailures.value(sourceId, "stroke-linecap", "range");
        }
        if (!Set.of("round", "miter", "bevel").contains(join)) {
            throw SvgFailures.value(sourceId, "stroke-linejoin", "range");
        }
        return new Style(
                fill, stroke, strokeWidth, fillOpacity, strokeOpacity, fillRule, cap, join);
    }

    private void emit(
            PathShape shape, Map<String, String> attributes, Style style, Affine transform) {
        double shapeOpacity = opacity(attributes.getOrDefault("opacity", "1"), "opacity");
        Rgba resolvedFill =
                style.fill().equals("none")
                        ? Rgba.TRANSPARENT
                        : color(style.fill(), style.fillOpacity());
        Rgba resolvedStroke =
                style.stroke().equals("none")
                        ? Rgba.TRANSPARENT
                        : color(style.stroke(), style.strokeOpacity());
        boolean fillVisible = resolvedFill.alpha() > 0;
        boolean strokeVisible = resolvedStroke.alpha() > 0;
        if (shapeOpacity == 0.0 || (!fillVisible && !strokeVisible)) {
            return;
        }
        if (shape.open() && fillVisible) {
            throw SvgFailures.value(sourceId, shape.field(), "openFill");
        }
        if (fillVisible && shape.requiresEvenOdd() && !style.fillRule().equals("evenodd")) {
            throw SvgFailures.value(sourceId, "fill-rule", "fillRule");
        }
        if (strokeVisible && !style.cap().equals("round")) {
            throw SvgFailures.value(sourceId, "stroke-linecap", "strokeCap");
        }
        if (strokeVisible && !style.join().equals("round")) {
            throw SvgFailures.value(sourceId, "stroke-linejoin", "strokeJoin");
        }
        if (fillVisible && strokeVisible && shapeOpacity != 1.0) {
            throw SvgFailures.value(sourceId, "opacity", "paintComposition");
        }
        SvgPathData transformed;
        try {
            transformed = transform(shape.data(), transform);
        } catch (IllegalArgumentException exception) {
            throw SvgFailures.value(sourceId, "transform", "overflow");
        }
        VectorPath path;
        try {
            budget.chargeOwned(
                    (long) transformed.commands().length * Long.BYTES
                            + (long) transformed.ordinates().length * Double.BYTES);
            checkCancellation();
            path = VectorPath.of(transformed.commands(), transformed.ordinates());
        } catch (IllegalArgumentException exception) {
            throw SvgFailures.value(sourceId, shape.field(), "syntax");
        }
        Rgba fill = fillVisible ? resolvedFill : Rgba.TRANSPARENT;
        Optional<SymbolStroke> stroke = Optional.empty();
        if (strokeVisible) {
            double localScale = transform.similarityScale();
            if (!Double.isFinite(localScale)) {
                throw SvgFailures.value(sourceId, "transform", "strokeTransform");
            }
            double xScale = placement.size().width() / viewBox.width();
            double yScale = placement.size().height() / viewBox.height();
            if (!similar(xScale, yScale)) {
                throw SvgFailures.value(sourceId, "transform", "strokeTransform");
            }
            double width = style.strokeWidth() * localScale * xScale;
            if (!Double.isFinite(width) || width <= 0) {
                throw SvgFailures.value(sourceId, "stroke-width", "overflow");
            }
            double halfSourceStroke = style.strokeWidth() * localScale / 2.0;
            if (!Double.isFinite(halfSourceStroke)
                    || !insideExpanded(path.coordinateEnvelope(), viewBox, halfSourceStroke)) {
                throw SvgFailures.value(sourceId, shape.field(), "outsideViewBox");
            }
            stroke =
                    Optional.of(
                            new SymbolStroke(
                                    resolvedStroke,
                                    new SymbolLength(width, placement.size().unit())));
        }
        if (!inside(path.coordinateEnvelope(), viewBox)) {
            throw SvgFailures.value(sourceId, shape.field(), "outsideViewBox");
        }
        if (output.size() + 1 > limits.maximumPaintedOutputPaths()) {
            throw SvgFailures.limit(
                    sourceId,
                    "paintedOutputPaths",
                    output.size() + 1L,
                    limits.maximumPaintedOutputPaths());
        }
        budget.chargeOwned(256L);
        try {
            checkCancellation();
            output.add(VectorMarkerSymbol.of(path, viewBox, fill, stroke, placement, shapeOpacity));
        } catch (IllegalArgumentException exception) {
            throw SvgFailures.value(sourceId, shape.field(), "outsideViewBox");
        }
    }

    private PathShape geometry(String name, Map<String, String> attributes) {
        return switch (name) {
            case "path" -> {
                String d = required(attributes, "d");
                SvgPathData data = SvgPathParser.parse(sourceId, d, limits, budget, cancellation);
                yield new PathShape(data, data.hasOpenSubpath(), true, "d");
            }
            case "line" -> line(attributes);
            case "polyline" -> points(attributes, false);
            case "polygon" -> points(attributes, true);
            case "rect" -> rect(attributes);
            case "circle" -> ellipse(attributes, true);
            case "ellipse" -> ellipse(attributes, false);
            default -> throw new IllegalStateException("unexpected shape");
        };
    }

    private PathShape line(Map<String, String> a) {
        double x1 = requiredNumber(a, "x1");
        double y1 = requiredNumber(a, "y1");
        double x2 = requiredNumber(a, "x2");
        double y2 = requiredNumber(a, "y2");
        return shape(
                new VectorPathCommand[] {VectorPathCommand.MOVE_TO, VectorPathCommand.LINE_TO},
                new double[] {x1, y1, x2, y2},
                1,
                true,
                false,
                "x1");
    }

    private PathShape points(Map<String, String> a, boolean closed) {
        double[] points =
                numbers(required(a, "points"), "points", closed ? 6 : 4, Integer.MAX_VALUE);
        if ((points.length & 1) != 0) {
            throw SvgFailures.value(sourceId, "points", "arity");
        }
        int commandCount = points.length / 2 + (closed ? 1 : 0);
        int segments = points.length / 2 - 1 + (closed ? 1 : 0);
        budget.addCommands(commandCount);
        budget.addSegments(segments);
        budget.chargeOwned((long) commandCount * Long.BYTES + (long) points.length * Double.BYTES);
        checkCancellation();
        VectorPathCommand[] commands = new VectorPathCommand[commandCount];
        commands[0] = VectorPathCommand.MOVE_TO;
        Arrays.fill(commands, 1, points.length / 2, VectorPathCommand.LINE_TO);
        if (closed) {
            commands[commands.length - 1] = VectorPathCommand.CLOSE;
        }
        return shapePrecharged(commands, points, segments, !closed, closed, "points");
    }

    private PathShape rect(Map<String, String> a) {
        double x = optionalNumber(a, "x", 0);
        double y = optionalNumber(a, "y", 0);
        double w = requiredPositive(a, "width");
        double h = requiredPositive(a, "height");
        return shape(
                new VectorPathCommand[] {
                    VectorPathCommand.MOVE_TO,
                    VectorPathCommand.LINE_TO,
                    VectorPathCommand.LINE_TO,
                    VectorPathCommand.LINE_TO,
                    VectorPathCommand.CLOSE
                },
                new double[] {x, y, x + w, y, x + w, y + h, x, y + h},
                4,
                false,
                false,
                "width");
    }

    private PathShape ellipse(Map<String, String> a, boolean circle) {
        double cx = optionalNumber(a, "cx", 0);
        double cy = optionalNumber(a, "cy", 0);
        double rx = circle ? requiredPositive(a, "r") : requiredPositive(a, "rx");
        double ry = circle ? rx : requiredPositive(a, "ry");
        VectorPathCommand[] c = {
            VectorPathCommand.MOVE_TO,
            VectorPathCommand.CUBIC_TO,
            VectorPathCommand.CUBIC_TO,
            VectorPathCommand.CUBIC_TO,
            VectorPathCommand.CUBIC_TO,
            VectorPathCommand.CLOSE
        };
        double[] o = {
            cx + rx,
            cy,
            cx + rx,
            cy + KAPPA * ry,
            cx + KAPPA * rx,
            cy + ry,
            cx,
            cy + ry,
            cx - KAPPA * rx,
            cy + ry,
            cx - rx,
            cy + KAPPA * ry,
            cx - rx,
            cy,
            cx - rx,
            cy - KAPPA * ry,
            cx - KAPPA * rx,
            cy - ry,
            cx,
            cy - ry,
            cx + KAPPA * rx,
            cy - ry,
            cx + rx,
            cy - KAPPA * ry,
            cx + rx,
            cy
        };
        return shape(c, o, 5, false, false, circle ? "r" : "rx");
    }

    private PathShape shape(
            VectorPathCommand[] c,
            double[] o,
            int segments,
            boolean open,
            boolean evenOdd,
            String field) {
        budget.addCommands(c.length);
        budget.addSegments(segments);
        budget.chargeOwned((long) c.length * Long.BYTES + (long) o.length * Double.BYTES);
        return shapePrecharged(c, o, segments, open, evenOdd, field);
    }

    private PathShape shapePrecharged(
            VectorPathCommand[] c,
            double[] o,
            int segments,
            boolean open,
            boolean evenOdd,
            String field) {
        for (double value : o) {
            if (!Double.isFinite(value)) {
                throw SvgFailures.value(sourceId, field, "overflow");
            }
        }
        return new PathShape(new SvgPathData(c, o, segments, open), open, evenOdd, field);
    }

    private SvgPathData transform(SvgPathData data, Affine transform) {
        double[] source = data.ordinates();
        budget.chargeOwned((long) source.length * Double.BYTES);
        double[] result = new double[source.length];
        for (int index = 0; index < source.length; index += 2) {
            double transformedX = transform.applyX(source[index], source[index + 1]);
            double transformedY = transform.applyY(source[index], source[index + 1]);
            if (!Double.isFinite(transformedX) || !Double.isFinite(transformedY)) {
                throw new IllegalArgumentException("non-finite transformed coordinate");
            }
            result[index] = transformedX == 0.0 ? 0.0 : transformedX;
            result[index + 1] = transformedY == 0.0 ? 0.0 : transformedY;
        }
        return new SvgPathData(
                data.commands(), result, data.drawingSegments(), data.hasOpenSubpath());
    }

    private Affine parseTransform(String text) {
        int offset = 0;
        Affine result = Affine.IDENTITY;
        boolean parsedFunction = false;
        while (offset < text.length()) {
            while (offset < text.length() && isWhitespace(text.charAt(offset))) {
                offset++;
            }
            int open = text.indexOf('(', offset);
            int close = open < 0 ? -1 : text.indexOf(')', open + 1);
            if (open <= offset || close < 0) {
                throw SvgFailures.value(sourceId, "transform", "syntax");
            }
            int nameEnd = open;
            while (nameEnd > offset && isWhitespace(text.charAt(nameEnd - 1))) {
                nameEnd--;
            }
            if (nameEnd == offset) {
                throw SvgFailures.value(sourceId, "transform", "syntax");
            }
            budget.chargeOwned(Math.multiplyExact((long) (nameEnd - offset), 2L));
            String name = text.substring(offset, nameEnd);
            int argumentsLength = close - open - 1;
            budget.chargeOwned(Math.multiplyExact((long) argumentsLength, 2L));
            double[] v = numbers(text.substring(open + 1, close), "transform", 1, 6);
            Affine part =
                    switch (name) {
                        case "matrix" ->
                                requireArity(v, 6, new Affine(v[0], v[1], v[2], v[3], v[4], v[5]));
                        case "translate" ->
                                v.length == 1
                                        ? Affine.translate(v[0], 0)
                                        : requireArity(v, 2, Affine.translate(v[0], v[1]));
                        case "scale" ->
                                v.length == 1
                                        ? Affine.scale(v[0], v[0])
                                        : requireArity(v, 2, Affine.scale(v[0], v[1]));
                        case "rotate" -> rotate(v);
                        case "skewX" -> requireArity(v, 1, Affine.skewX(v[0]));
                        case "skewY" -> requireArity(v, 1, Affine.skewY(v[0]));
                        default -> throw SvgFailures.value(sourceId, "transform", "syntax");
                    };
            if (++transformFunctions > limits.maximumTransformFunctions()) {
                throw SvgFailures.limit(
                        sourceId,
                        "transformFunctions",
                        transformFunctions,
                        limits.maximumTransformFunctions());
            }
            if ((transformFunctions & 4095) == 0) {
                checkCancellation();
            }
            parsedFunction = true;
            try {
                result = result.multiply(part);
            } catch (IllegalArgumentException exception) {
                throw SvgFailures.value(sourceId, "transform", "overflow");
            }
            offset = close + 1;
            boolean whitespace = false;
            while (offset < text.length() && isWhitespace(text.charAt(offset))) {
                whitespace = true;
                offset++;
            }
            boolean comma = offset < text.length() && text.charAt(offset) == ',';
            if (comma) {
                offset++;
                while (offset < text.length() && isWhitespace(text.charAt(offset))) {
                    offset++;
                }
            }
            if (offset < text.length()) {
                if ((!whitespace && !comma) || text.charAt(offset) == ',') {
                    throw SvgFailures.value(sourceId, "transform", "syntax");
                }
            } else if (comma) {
                throw SvgFailures.value(sourceId, "transform", "syntax");
            }
        }
        if (!parsedFunction) {
            throw SvgFailures.value(sourceId, "transform", "syntax");
        }
        validateTransform(result);
        return result;
    }

    private void validateTransform(Affine transform) {
        double determinant = transform.determinant();
        if (!transform.finite() || !Double.isFinite(determinant)) {
            throw SvgFailures.value(sourceId, "transform", "overflow");
        }
        if (determinant == 0.0) {
            throw SvgFailures.value(sourceId, "transform", "singular");
        }
    }

    private Affine rotate(double[] v) {
        if (v.length != 1 && v.length != 3) {
            throw SvgFailures.value(sourceId, "transform", "arity");
        }
        Affine rotate = Affine.rotate(v[0]);
        if (v.length == 1) {
            return rotate;
        }
        try {
            return Affine.translate(v[1], v[2])
                    .multiply(rotate)
                    .multiply(Affine.translate(-v[1], -v[2]));
        } catch (IllegalArgumentException exception) {
            throw SvgFailures.value(sourceId, "transform", "overflow");
        }
    }

    private Affine requireArity(double[] v, int expected, Affine result) {
        if (v.length != expected) {
            throw SvgFailures.value(sourceId, "transform", "arity");
        }
        return result;
    }

    private double[] numbers(String text, String field, int minimum, int maximum) {
        SvgNumbers.Cursor cursor = SvgNumbers.cursor(sourceId, text, field, limits, budget);
        int capacity = Math.min(maximum, Math.max(8, minimum));
        budget.chargeOwned((long) capacity * Double.BYTES);
        double[] values = new double[capacity];
        int count = 0;
        while (!cursor.atEnd()) {
            if (count == maximum) {
                throw SvgFailures.value(sourceId, field, "arity");
            }
            if (count == values.length) {
                int expanded = Math.min(maximum, Math.multiplyExact(values.length, 2));
                budget.chargeOwned((long) expanded * Double.BYTES);
                values = Arrays.copyOf(values, expanded);
            }
            values[count++] = cursor.read(field);
            if ((count & 4095) == 0) {
                checkCancellation();
            }
        }
        if (count < minimum) {
            throw SvgFailures.value(sourceId, field, "arity");
        }
        budget.chargeOwned((long) count * Double.BYTES);
        return Arrays.copyOf(values, count);
    }

    private String required(Map<String, String> a, String field) {
        String value = a.get(field);
        if (value == null) {
            throw SvgFailures.value(sourceId, field, "missing");
        }
        if (value.isBlank()) {
            throw SvgFailures.value(sourceId, field, "blank");
        }
        return value;
    }

    private double requiredNumber(Map<String, String> a, String field) {
        return number(required(a, field), field);
    }

    private double requiredPositive(Map<String, String> a, String field) {
        double v = requiredNumber(a, field);
        if (v <= 0) {
            throw SvgFailures.value(sourceId, field, "range");
        }
        return v;
    }

    private double optionalNumber(Map<String, String> a, String field, double fallback) {
        return a.containsKey(field) ? number(a.get(field), field) : fallback;
    }

    private double number(String text, String field) {
        double[] v = numbers(text, field, 1, 1);
        return v[0];
    }

    private double opacity(String text, String field) {
        double v = number(text, field);
        if (v < 0 || v > 1) {
            throw SvgFailures.value(sourceId, field, "range");
        }
        return v;
    }

    private void validatePaint(String value, String field) {
        if (value.equals("none")) {
            return;
        }
        if (value.length() != 7 || value.charAt(0) != '#') {
            throw SvgFailures.unsupported(sourceId, "paint");
        }
        for (int i = 1; i < 7; i++) {
            char character = value.charAt(i);
            if (!((character >= '0' && character <= '9')
                    || (character >= 'A' && character <= 'F')
                    || (character >= 'a' && character <= 'f'))) {
                throw SvgFailures.value(sourceId, field, "syntax");
            }
        }
    }

    private Rgba color(String value, double opacity) {
        int rgb = 0;
        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);
            int digit = character <= '9' ? character - '0' : (character & ~0x20) - 'A' + 10;
            rgb = (rgb << 4) | digit;
        }
        int alpha = (int) StrictMath.round(opacity * 255.0);
        return new Rgba((rgb >>> 16) & 255, (rgb >>> 8) & 255, rgb & 255, alpha);
    }

    private void checkCancellation() {
        if (cancellation.isCancellationRequested()) {
            throw SvgFailures.cancelled(sourceId);
        }
    }

    private static boolean inside(Envelope value, Envelope bounds) {
        return value.minX() >= bounds.minX()
                && value.minY() >= bounds.minY()
                && value.maxX() <= bounds.maxX()
                && value.maxY() <= bounds.maxY();
    }

    private static boolean insideExpanded(Envelope value, Envelope bounds, double expansion) {
        return value.minX() - expansion >= bounds.minX()
                && value.minY() - expansion >= bounds.minY()
                && value.maxX() + expansion <= bounds.maxX()
                && value.maxY() + expansion <= bounds.maxY();
    }

    private static boolean similar(double a, double b) {
        double scale = StrictMath.max(StrictMath.abs(a), StrictMath.abs(b));
        return scale > 0.0 && StrictMath.abs(a - b) <= 1e-12 * scale;
    }

    private static boolean isWhitespace(char value) {
        return value == ' ' || value == '\t' || value == '\r' || value == '\n';
    }

    private record Frame(Style style, Affine transform, boolean hasTransform, boolean container) {}

    private record Style(
            String fill,
            String stroke,
            double strokeWidth,
            double fillOpacity,
            double strokeOpacity,
            String fillRule,
            String cap,
            String join) {
        static Style defaults() {
            return new Style("#000000", "none", 1, 1, 1, "nonzero", "butt", "miter");
        }
    }

    private record PathShape(
            SvgPathData data, boolean open, boolean requiresEvenOdd, String field) {}

    private record Affine(double a, double b, double c, double d, double e, double f) {
        static final Affine IDENTITY = new Affine(1, 0, 0, 1, 0, 0);

        static Affine translate(double x, double y) {
            return new Affine(1, 0, 0, 1, x, y);
        }

        static Affine scale(double x, double y) {
            return new Affine(x, 0, 0, y, 0, 0);
        }

        static Affine rotate(double degrees) {
            double r = StrictMath.toRadians(degrees);
            double cos = StrictMath.cos(r);
            double sin = StrictMath.sin(r);
            return new Affine(cos, sin, -sin, cos, 0, 0);
        }

        static Affine skewX(double degrees) {
            return new Affine(1, 0, StrictMath.tan(StrictMath.toRadians(degrees)), 1, 0, 0);
        }

        static Affine skewY(double degrees) {
            return new Affine(1, StrictMath.tan(StrictMath.toRadians(degrees)), 0, 1, 0, 0);
        }

        Affine multiply(Affine o) {
            Affine r =
                    new Affine(
                            a * o.a + c * o.b,
                            b * o.a + d * o.b,
                            a * o.c + c * o.d,
                            b * o.c + d * o.d,
                            a * o.e + c * o.f + e,
                            b * o.e + d * o.f + f);
            if (!r.finite()) {
                throw new IllegalArgumentException("non-finite affine product");
            }
            return r;
        }

        double applyX(double x, double y) {
            return a * x + c * y + e;
        }

        double applyY(double x, double y) {
            return b * x + d * y + f;
        }

        boolean finite() {
            return Double.isFinite(a)
                    && Double.isFinite(b)
                    && Double.isFinite(c)
                    && Double.isFinite(d)
                    && Double.isFinite(e)
                    && Double.isFinite(f);
        }

        double determinant() {
            return a * d - b * c;
        }

        double similarityScale() {
            double first = StrictMath.hypot(a, b);
            double second = StrictMath.hypot(c, d);
            if (!Double.isFinite(first)
                    || !Double.isFinite(second)
                    || first <= 0.0
                    || second <= 0.0
                    || !similar(first, second)) {
                return Double.NaN;
            }
            double normalizedDot = (a / first) * (c / second) + (b / first) * (d / second);
            return Double.isFinite(normalizedDot) && StrictMath.abs(normalizedDot) <= 1e-12
                    ? first
                    : Double.NaN;
        }
    }
}
