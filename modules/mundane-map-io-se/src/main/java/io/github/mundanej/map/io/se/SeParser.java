package io.github.mundanej.map.io.se;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.FillSymbol;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PortrayalComparison;
import io.github.mundanej.map.api.PortrayalLogicalOperator;
import io.github.mundanej.map.api.PortrayalOperand;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.ScaleInterval;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLResolver;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class SeParser {
    private static final String SE = "http://www.opengis.net/se";
    private static final String OGC = "http://www.opengis.net/ogc";
    private static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    private static final String XLINK = "http://www.w3.org/1999/xlink";
    private static final String PIXEL_UOM = "http://www.opengeospatial.org/se/units/pixel";
    private static final String CATALOG_FORMAT = "application/vnd.mundane-map.symbol";

    private final String source;
    private final byte[] bytes;

    private final NamedSymbolCatalog catalog;

    private final SeReadOptions options;
    private final SeReadLimits limits;
    private final SeOwnedBudget budget;
    private final Deque<String> path = new ArrayDeque<>();
    private XMLStreamReader reader;
    private int depth;
    private int elements;
    private int attributes;
    private int textCharacters;
    private int rules;
    private int predicates;
    private int symbolizers;
    private int catalogReferences;
    private int outputSymbols;
    private int events;

    SeParser(
            String source,
            byte[] bytes,
            NamedSymbolCatalog catalog,
            SeReadOptions options,
            SeOwnedBudget budget) {
        this.source = source;
        this.bytes = bytes;
        this.catalog = catalog;
        this.options = options;
        this.budget = budget;
        limits = options.limits();
    }

    SeFeatureStyle parse() {
        String xml = decode();
        validateLexicalSecurity(xml);
        XMLInputFactory factory = secureFactory();
        try {
            reader = factory.createXMLStreamReader(new StringReader(xml));
            validateDeclaration();
            moveToRoot();
            SeFeatureStyle result = parseFeatureTypeStyle();
            finishDocument();
            return result;
        } catch (SeReadException failure) {
            throw failure;
        } catch (XMLStreamException failure) {
            throw failure("SE_XML_SYNTAX", currentPath(), "malformed");
        } finally {
            closeReader();
        }
    }

    private void validateLexicalSecurity(String xml) {
        int cursor = 0;
        while (cursor < xml.length()) {
            if (xml.startsWith("<!--", cursor)) {
                int end = xml.indexOf("-->", cursor + 4);
                if (end < 0) {
                    return;
                }
                cursor = end + 3;
                continue;
            }
            if (xml.startsWith("<![CDATA[", cursor)) {
                throw failure("SE_XML_SECURITY", "/", "cdata");
            }
            if (xml.regionMatches(true, cursor, "<!DOCTYPE", 0, "<!DOCTYPE".length())
                    || xml.regionMatches(true, cursor, "<!ENTITY", 0, "<!ENTITY".length())
                    || xml.charAt(cursor) == '&') {
                throw failure("SE_XML_SECURITY", "/", "referenceOrDoctype");
            }
            cursor++;
        }
    }

    private SeFeatureStyle parseFeatureTypeStyle() throws XMLStreamException {
        requireElement(SE, "FeatureTypeStyle", "SE_ROOT_UNSUPPORTED");
        requireAttributes(
                Map.of(
                        new QName("", "version"), AttributeRule.optionalAny(),
                        new QName(XSI, "schemaLocation"), AttributeRule.optionalAny()));
        String version = attribute("", "version");
        if (version != null && !"1.1.0".equals(version)) {
            throw failure("SE_VERSION_UNSUPPORTED", currentPath(), version);
        }

        Optional<String> name = Optional.empty();
        SeDescription description = SeDescription.empty();
        Optional<String> featureTypeName = Optional.empty();
        List<String> semanticTypes = new ArrayList<>();
        List<ParsedRule> parsedRules = new ArrayList<>();
        int phase = 0;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            String local = reader.getLocalName();
            if (!SE.equals(reader.getNamespaceURI())) {
                throw failure("SE_NAMESPACE_UNSUPPORTED", currentPath(), namespace());
            }
            switch (local) {
                case "Name" -> {
                    requirePhase(phase <= 0, "Name");
                    phase = 1;
                    name = Optional.of(readMetadataText("Name"));
                }
                case "Description" -> {
                    requirePhase(phase <= 1, "Description");
                    phase = 2;
                    description = parseDescription();
                }
                case "FeatureTypeName" -> {
                    requirePhase(phase <= 2, "FeatureTypeName");
                    phase = 3;
                    featureTypeName = Optional.of(readMetadataText("FeatureTypeName"));
                }
                case "SemanticTypeIdentifier" -> {
                    requirePhase(phase <= 3, "SemanticTypeIdentifier");
                    phase = 3;
                    semanticTypes.add(readMetadataText("SemanticTypeIdentifier"));
                }
                case "Rule" -> {
                    requirePhase(phase <= 4, "Rule");
                    phase = 4;
                    if (++rules > limits.maximumRules()) {
                        throw limit("rules", rules, limits.maximumRules());
                    }
                    parsedRules.add(parseRule());
                }
                default -> throw unsupportedElement(local);
            }
        }
        if (parsedRules.isEmpty()) {
            throw failure("SE_ELEMENT_UNSUPPORTED", currentPath(), "missingRule");
        }
        budget.charge(512, currentPath());
        List<PortrayalRule> portrayalRules =
                parsedRules.stream().map(ParsedRule::portrayal).toList();
        RulePortrayalPlan plan;
        try {
            plan = new RulePortrayalPlan(portrayalRules);
        } catch (IllegalArgumentException failure) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "rulePlan");
        }
        return new SeFeatureStyle(
                name,
                description,
                featureTypeName,
                semanticTypes,
                parsedRules.stream().map(ParsedRule::metadata).toList(),
                plan.portrayal());
    }

    private ParsedRule parseRule() throws XMLStreamException {
        requireElement(SE, "Rule", "SE_ELEMENT_UNSUPPORTED");
        requireNoAttributes();
        Optional<String> name = Optional.empty();
        SeDescription description = SeDescription.empty();
        Optional<PortrayalPredicate> predicate = Optional.empty();
        boolean elseRule = false;
        OptionalDouble minimumScale = OptionalDouble.empty();
        OptionalDouble maximumScale = OptionalDouble.empty();
        List<Symbol> markers = new ArrayList<>();
        List<Symbol> lines = new ArrayList<>();
        List<Symbol> fills = new ArrayList<>();
        int phase = 0;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            String namespace = reader.getNamespaceURI();
            String local = reader.getLocalName();
            if (OGC.equals(namespace) && "Filter".equals(local)) {
                requirePhase(phase <= 2 && predicate.isEmpty() && !elseRule, local);
                phase = 3;
                predicate = Optional.of(parseFilter());
                continue;
            }
            if (!SE.equals(namespace)) {
                throw failure("SE_NAMESPACE_UNSUPPORTED", currentPath(), namespace());
            }
            switch (local) {
                case "Name" -> {
                    requirePhase(phase <= 0, local);
                    phase = 1;
                    name = Optional.of(readMetadataText(local));
                }
                case "Description" -> {
                    requirePhase(phase <= 1, local);
                    phase = 2;
                    description = parseDescription();
                }
                case "ElseFilter" -> {
                    requirePhase(phase <= 2 && predicate.isEmpty() && !elseRule, local);
                    phase = 3;
                    parseElseFilter();
                    elseRule = true;
                }
                case "MinScaleDenominator" -> {
                    requirePhase(phase <= 3, local);
                    phase = 4;
                    minimumScale =
                            OptionalDouble.of(decimalElement(local, 0.0, Double.MAX_VALUE, false));
                }
                case "MaxScaleDenominator" -> {
                    requirePhase(phase <= 4, local);
                    phase = 5;
                    maximumScale =
                            OptionalDouble.of(decimalElement(local, 0.0, Double.MAX_VALUE, false));
                }
                case "PointSymbolizer" -> {
                    requirePhase(phase <= 5, local);
                    phase = 5;
                    if (++symbolizers > limits.maximumSymbolizers()) {
                        throw limit("symbolizers", symbolizers, limits.maximumSymbolizers());
                    }
                    markers.add(parsePointSymbolizer());
                }
                case "LineSymbolizer" -> {
                    requirePhase(phase <= 5, local);
                    phase = 5;
                    countSymbolizer();
                    lines.add(parseLineSymbolizer());
                }
                case "PolygonSymbolizer" -> {
                    requirePhase(phase <= 5, local);
                    phase = 5;
                    countSymbolizer();
                    fills.add(parsePolygonSymbolizer());
                }
                case "TextSymbolizer", "RasterSymbolizer" ->
                        throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), local);
                default -> throw unsupportedElement(local);
            }
        }
        if (markers.isEmpty() && lines.isEmpty() && fills.isEmpty()) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "missingSymbolizer");
        }
        budget.charge(128, currentPath());
        ScaleInterval scale;
        try {
            scale = new ScaleInterval(minimumScale, maximumScale);
        } catch (IllegalArgumentException failure) {
            throw failure("SE_VALUE_INVALID", currentPath(), "scaleInterval");
        }
        PortrayalRule portrayal =
                new PortrayalRule(name, scale, predicate, elseRule, markers, lines, fills);
        return new ParsedRule(new SeRuleMetadata(name, description), portrayal);
    }

    private PortrayalPredicate parseFilter() throws XMLStreamException {
        requireElement(OGC, "Filter", "SE_FILTER_UNSUPPORTED");
        requireNoAttributes();
        int event = nextChild();
        if (event == XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "missingPredicate");
        }
        PortrayalPredicate result = parsePredicate(1);
        if (nextChild() != XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "multiplePredicates");
        }
        return result;
    }

    private PortrayalPredicate parsePredicate(int predicateDepth) throws XMLStreamException {
        if (++predicates > limits.maximumPredicates()) {
            throw limit("predicates", predicates, limits.maximumPredicates());
        }
        if (predicateDepth > limits.maximumPredicateDepth()) {
            throw limit("predicateDepth", predicateDepth, limits.maximumPredicateDepth());
        }
        budget.charge(128, currentPath());
        requireNamespace(OGC);
        String local = reader.getLocalName();
        return switch (local) {
            case "PropertyIsEqualTo" -> parseComparison(PortrayalComparison.EQUAL);
            case "PropertyIsNotEqualTo" -> parseComparison(PortrayalComparison.NOT_EQUAL);
            case "PropertyIsLessThan" -> parseComparison(PortrayalComparison.LESS_THAN);
            case "PropertyIsLessThanOrEqualTo" ->
                    parseComparison(PortrayalComparison.LESS_THAN_OR_EQUAL);
            case "PropertyIsGreaterThan" -> parseComparison(PortrayalComparison.GREATER_THAN);
            case "PropertyIsGreaterThanOrEqualTo" ->
                    parseComparison(PortrayalComparison.GREATER_THAN_OR_EQUAL);
            case "PropertyIsBetween" -> parseBetween();
            case "PropertyIsNull" -> parseIsNull();
            case "And" -> parseLogical(PortrayalLogicalOperator.AND, predicateDepth);
            case "Or" -> parseLogical(PortrayalLogicalOperator.OR, predicateDepth);
            case "Not" -> parseLogical(PortrayalLogicalOperator.NOT, predicateDepth);
            default -> throw failure("SE_FILTER_UNSUPPORTED", currentPath(), local);
        };
    }

    private PortrayalPredicate parseComparison(PortrayalComparison operation)
            throws XMLStreamException {
        requireNoAttributes();
        PortrayalOperand left = nextOperand();
        PortrayalOperand right = nextOperand();
        requirePredicateEnd();
        try {
            return new PortrayalPredicate.Comparison(operation, left, right);
        } catch (IllegalArgumentException failure) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "propertyRequired");
        }
    }

    private PortrayalPredicate parseBetween() throws XMLStreamException {
        requireNoAttributes();
        PortrayalOperand first = nextOperand();
        if (!(first instanceof PortrayalOperand.Property property)) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "betweenPropertyRequired");
        }
        PortrayalOperand lower = parseBoundary("LowerBoundary");
        PortrayalOperand upper = parseBoundary("UpperBoundary");
        requirePredicateEnd();
        return new PortrayalPredicate.Between(property, lower, upper);
    }

    private PortrayalPredicate parseIsNull() throws XMLStreamException {
        requireNoAttributes();
        PortrayalOperand operand = nextOperand();
        requirePredicateEnd();
        if (!(operand instanceof PortrayalOperand.Property property)) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "nullPropertyRequired");
        }
        return new PortrayalPredicate.IsNull(property);
    }

    private PortrayalPredicate parseLogical(PortrayalLogicalOperator operation, int predicateDepth)
            throws XMLStreamException {
        requireNoAttributes();
        List<PortrayalPredicate> children = new ArrayList<>();
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            children.add(parsePredicate(predicateDepth + 1));
        }
        try {
            return new PortrayalPredicate.Logical(operation, children);
        } catch (IllegalArgumentException failure) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "logicalArity");
        }
    }

    private PortrayalOperand parseBoundary(String expected) throws XMLStreamException {
        if (nextChild() == XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "missing:" + expected);
        }
        requireElement(OGC, expected, "SE_FILTER_UNSUPPORTED");
        requireNoAttributes();
        PortrayalOperand operand = nextOperand();
        if (nextChild() != XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "boundaryArity");
        }
        return operand;
    }

    private PortrayalOperand nextOperand() throws XMLStreamException {
        if (nextChild() == XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "missingOperand");
        }
        requireNamespace(OGC);
        String local = reader.getLocalName();
        try {
            return switch (local) {
                case "PropertyName" -> new PortrayalOperand.Property(readOgcText(local, true));
                case "Literal" -> new PortrayalOperand.Literal(readOgcText(local, false));
                default -> throw failure("SE_FILTER_UNSUPPORTED", currentPath(), local);
            };
        } catch (IllegalArgumentException failure) {
            throw failure("SE_VALUE_INVALID", currentPath(), "operand");
        }
    }

    private String readOgcText(String element, boolean stripped) throws XMLStreamException {
        requireElement(OGC, element, "SE_FILTER_UNSUPPORTED");
        requireNoAttributes();
        String value = readTextBody();
        if (!stripped) {
            return value;
        }
        String result = value.strip();
        if (result.isBlank()) {
            throw failure("SE_VALUE_INVALID", currentPath(), "blankProperty");
        }
        return result;
    }

    private void requirePredicateEnd() throws XMLStreamException {
        if (nextChild() != XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "operandArity");
        }
    }

    private void parseElseFilter() throws XMLStreamException {
        requireElement(SE, "ElseFilter", "SE_FILTER_UNSUPPORTED");
        requireNoAttributes();
        if (nextChild() != XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_FILTER_UNSUPPORTED", currentPath(), "elseContent");
        }
    }

    private Symbol parsePointSymbolizer() throws XMLStreamException {
        requireElement(SE, "PointSymbolizer", "SE_SYMBOLIZER_UNSUPPORTED");
        requireAttributes(Map.of(new QName("", "uom"), AttributeRule.optionalAny()));
        String uom = attribute("", "uom");
        if (uom != null && !PIXEL_UOM.equals(uom)) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "uom");
        }
        int phase = 0;
        boolean graphic = false;
        Symbol marker = null;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            requireNamespace(SE);
            switch (reader.getLocalName()) {
                case "Name" -> {
                    requirePhase(phase <= 0, "Name");
                    phase = 1;
                    readMetadataText("Name");
                }
                case "Description" -> {
                    requirePhase(phase <= 1, "Description");
                    phase = 2;
                    parseDescription();
                }
                case "Geometry" ->
                        throw failure(
                                "SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "geometryExpression");
                case "Graphic" -> {
                    requirePhase(phase <= 3 && !graphic, "Graphic");
                    phase = 3;
                    graphic = true;
                    marker = parseGraphic();
                }
                default -> throw unsupportedElement(reader.getLocalName());
            }
        }
        if (!graphic) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "missingGraphic");
        }
        return marker;
    }

    private Symbol parseGraphic() throws XMLStreamException {
        requireElement(SE, "Graphic", "SE_SYMBOLIZER_UNSUPPORTED");
        requireNoAttributes();
        Mark mark = null;
        Symbol external = null;
        double opacity = 1.0;
        double size = 6.0;
        double rotation = 0.0;
        SymbolAnchor anchor = SymbolAnchor.CENTER;
        double offsetX = 0.0;
        double offsetY = 0.0;
        int phase = 0;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            requireNamespace(SE);
            String local = reader.getLocalName();
            switch (local) {
                case "Mark" -> {
                    requirePhase(phase <= 0 && mark == null && external == null, local);
                    phase = 1;
                    mark = parseMark();
                }
                case "ExternalGraphic" -> {
                    requirePhase(phase <= 0 && mark == null && external == null, local);
                    phase = 1;
                    external = parseExternalGraphic();
                }
                case "Opacity" -> {
                    requirePhase(phase <= 1 && (mark != null || external != null), local);
                    phase = 2;
                    opacity = decimalElement(local, 0.0, 1.0, false);
                }
                case "Size" -> {
                    rejectExternalPlacement(external, local);
                    requirePhase(phase <= 2 && mark != null, local);
                    phase = 3;
                    size = decimalElement(local, 0.0, Double.MAX_VALUE, true);
                }
                case "Rotation" -> {
                    rejectExternalPlacement(external, local);
                    requirePhase(phase <= 3 && mark != null, local);
                    phase = 4;
                    rotation = decimalElement(local, -Double.MAX_VALUE, Double.MAX_VALUE, false);
                }
                case "AnchorPoint" -> {
                    rejectExternalPlacement(external, local);
                    requirePhase(phase <= 4 && mark != null, local);
                    phase = 5;
                    anchor = parseAnchor();
                }
                case "Displacement" -> {
                    rejectExternalPlacement(external, local);
                    requirePhase(phase <= 5 && mark != null, local);
                    phase = 6;
                    Offset offset = parseDisplacement();
                    offsetX = offset.x();
                    offsetY = -offset.y();
                }
                default -> throw unsupportedElement(local);
            }
        }
        if (mark == null && external == null) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "missingGraphic");
        }
        if (external != null) {
            countOutputSymbol();
            if (Double.compare(opacity, 1.0) == 0) {
                return external;
            }
            countOutputSymbol();
            budget.charge(128, currentPath());
            return CompositeSymbol.of(List.of(external), opacity);
        }
        countOutputSymbol();
        budget.charge(1_024, currentPath());
        MarkerPlacement placement =
                new MarkerPlacement(
                        SymbolSize.square(size, SymbolUnit.SCREEN_PIXEL),
                        anchor,
                        offsetX,
                        offsetY,
                        rotation,
                        SymbolRotationMode.SCREEN_RELATIVE);
        return VectorMarkerSymbol.of(
                BuiltInMarkers.path(mark.shape()),
                BuiltInMarkers.viewBox(),
                mark.fill(),
                mark.stroke(),
                placement,
                opacity);
    }

    private void rejectExternalPlacement(Symbol external, String element) {
        if (external != null) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "externalGraphic" + element);
        }
    }

    private Symbol parseExternalGraphic() throws XMLStreamException {
        requireElement(SE, "ExternalGraphic", "SE_SYMBOLIZER_UNSUPPORTED");
        requireNoAttributes();
        if (++catalogReferences > limits.maximumCatalogReferences()) {
            throw limit("catalogReferences", catalogReferences, limits.maximumCatalogReferences());
        }
        if (nextChild() == XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_RESOURCE_UNRESOLVED", currentPath(), "missingOnlineResource");
        }
        requireElement(SE, "OnlineResource", "SE_SYMBOLIZER_UNSUPPORTED");
        requireAttributes(
                Map.of(
                        new QName(XLINK, "type"), AttributeRule.requiredExact("simple"),
                        new QName(XLINK, "href"), AttributeRule.requiredAny()));
        String key = attribute(XLINK, "href");
        if (key == null || !key.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")) {
            throw failure("SE_RESOURCE_UNRESOLVED", currentPath(), "catalogKey");
        }
        if (nextChild() != XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "onlineResourceContent");
        }
        if (nextChild() == XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "missingFormat");
        }
        String format = readValueText("Format");
        if (!CATALOG_FORMAT.equals(format)) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "format");
        }
        if (nextChild() != XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "externalGraphicContent");
        }
        Symbol resolved;
        try {
            resolved = catalog.require(key);
        } catch (SymbolException failure) {
            throw failure("SE_RESOURCE_UNRESOLVED", currentPath(), "catalogMissing");
        }
        if (resolved.role() != io.github.mundanej.map.api.SymbolRole.MARKER
                || (!(resolved instanceof MarkerSymbol)
                        && !(resolved instanceof CompositeSymbol))) {
            throw failure("SE_RESOURCE_UNRESOLVED", currentPath(), "catalogRole");
        }
        return resolved;
    }

    private LineSymbol parseLineSymbolizer() throws XMLStreamException {
        requireSymbolizerStart("LineSymbolizer");
        SolidLineSymbol line = null;
        int phase = 0;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            requireNamespace(SE);
            String local = reader.getLocalName();
            switch (local) {
                case "Name" -> {
                    requirePhase(phase <= 0, local);
                    phase = 1;
                    readMetadataText(local);
                }
                case "Description" -> {
                    requirePhase(phase <= 1, local);
                    phase = 2;
                    parseDescription();
                }
                case "Geometry" ->
                        throw failure(
                                "SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "geometryExpression");
                case "Stroke" -> {
                    requirePhase(phase <= 3 && line == null, local);
                    phase = 3;
                    Paint paint = parseParameters(false, true);
                    countOutputSymbol();
                    budget.charge(256, currentPath());
                    line =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            paint.color(),
                                            new SymbolLength(
                                                    paint.width(), SymbolUnit.SCREEN_PIXEL)),
                                    1.0);
                }
                default -> throw unsupportedElement(local);
            }
        }
        if (line == null) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "missingStroke");
        }
        return line;
    }

    private FillSymbol parsePolygonSymbolizer() throws XMLStreamException {
        requireSymbolizerStart("PolygonSymbolizer");
        Optional<Rgba> fill = Optional.empty();
        Optional<Symbol> outline = Optional.empty();
        boolean fillSeen = false;
        boolean strokeSeen = false;
        int phase = 0;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            requireNamespace(SE);
            String local = reader.getLocalName();
            switch (local) {
                case "Name" -> {
                    requirePhase(phase <= 0, local);
                    phase = 1;
                    readMetadataText(local);
                }
                case "Description" -> {
                    requirePhase(phase <= 1, local);
                    phase = 2;
                    parseDescription();
                }
                case "Geometry" ->
                        throw failure(
                                "SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "geometryExpression");
                case "Fill" -> {
                    requirePhase(phase <= 3 && !fillSeen, local);
                    phase = 3;
                    fillSeen = true;
                    fill = Optional.of(parseParameters(true, false).color());
                }
                case "Stroke" -> {
                    requirePhase(phase <= 4 && !strokeSeen, local);
                    phase = 4;
                    strokeSeen = true;
                    Paint paint = parseParameters(false, false);
                    countOutputSymbol();
                    budget.charge(192, currentPath());
                    outline =
                            Optional.of(
                                    SolidLineSymbol.of(
                                            new SymbolStroke(
                                                    paint.color(),
                                                    new SymbolLength(
                                                            paint.width(),
                                                            SymbolUnit.SCREEN_PIXEL)),
                                            1.0));
                }
                default -> throw unsupportedElement(local);
            }
        }
        if (!fillSeen && !strokeSeen) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "missingFillAndStroke");
        }
        countOutputSymbol();
        budget.charge(192, currentPath());
        return SolidFillSymbol.of(fill.orElse(new Rgba(0, 0, 0, 0)), outline, 1.0);
    }

    private void requireSymbolizerStart(String local) {
        requireElement(SE, local, "SE_SYMBOLIZER_UNSUPPORTED");
        requireAttributes(Map.of(new QName("", "uom"), AttributeRule.optionalAny()));
        String uom = attribute("", "uom");
        if (uom != null && !PIXEL_UOM.equals(uom)) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "uom");
        }
    }

    private Mark parseMark() throws XMLStreamException {
        requireElement(SE, "Mark", "SE_SYMBOLIZER_UNSUPPORTED");
        requireNoAttributes();
        BuiltInMarker shape = BuiltInMarker.SQUARE;
        Rgba fill = new Rgba(128, 128, 128, 255);
        Optional<SymbolStroke> stroke = Optional.empty();
        int phase = 0;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            requireNamespace(SE);
            String local = reader.getLocalName();
            switch (local) {
                case "WellKnownName" -> {
                    requirePhase(phase <= 0, local);
                    phase = 1;
                    shape = wellKnown(readValueText(local));
                }
                case "Fill" -> {
                    requirePhase(phase <= 1, local);
                    phase = 2;
                    Paint paint = parseParameters(true, false);
                    fill = paint.color();
                }
                case "Stroke" -> {
                    requirePhase(phase <= 2, local);
                    phase = 3;
                    Paint paint = parseParameters(false, false);
                    stroke =
                            Optional.of(
                                    new SymbolStroke(
                                            paint.color(),
                                            new SymbolLength(
                                                    paint.width(), SymbolUnit.SCREEN_PIXEL)));
                }
                default -> throw unsupportedElement(local);
            }
        }
        return new Mark(shape, fill, stroke);
    }

    private Paint parseParameters(boolean fill, boolean requireColor) throws XMLStreamException {
        String parent = fill ? "Fill" : "Stroke";
        requireElement(SE, parent, "SE_SYMBOLIZER_UNSUPPORTED");
        requireNoAttributes();
        Rgba color = fill ? new Rgba(128, 128, 128, 255) : new Rgba(0, 0, 0, 255);
        double opacity = 1.0;
        double width = 1.0;
        boolean colorSeen = false;
        Map<String, Boolean> seen = new HashMap<>();
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            requireElement(SE, "SvgParameter", "SE_SYMBOLIZER_UNSUPPORTED");
            requireAttributes(Map.of(new QName("", "name"), AttributeRule.requiredAny()));
            String name = attribute("", "name");
            boolean accepted =
                    fill
                            ? "fill".equals(name) || "fill-opacity".equals(name)
                            : "stroke".equals(name)
                                    || "stroke-opacity".equals(name)
                                    || "stroke-width".equals(name);
            if (!accepted || seen.put(name, Boolean.TRUE) != null) {
                throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), name);
            }
            String value = readTextBody();
            switch (name) {
                case "fill", "stroke" -> {
                    color = color(value, opacity);
                    colorSeen = true;
                }
                case "fill-opacity", "stroke-opacity" -> {
                    opacity = decimalValue(value, 0.0, 1.0, false);
                    color = withOpacity(color, opacity);
                }
                case "stroke-width" -> width = decimalValue(value, 0.0, Double.MAX_VALUE, true);
                default -> throw new AssertionError(name);
            }
        }
        if (requireColor && !colorSeen) {
            throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), "missingStrokeColor");
        }
        return new Paint(withOpacity(color, opacity), width);
    }

    private void countSymbolizer() {
        if (++symbolizers > limits.maximumSymbolizers()) {
            throw limit("symbolizers", symbolizers, limits.maximumSymbolizers());
        }
    }

    private void countOutputSymbol() {
        if (++outputSymbols > limits.maximumOutputSymbols()) {
            throw limit("outputSymbols", outputSymbols, limits.maximumOutputSymbols());
        }
    }

    private SeDescription parseDescription() throws XMLStreamException {
        requireElement(SE, "Description", "SE_ELEMENT_UNSUPPORTED");
        requireNoAttributes();
        Optional<String> title = Optional.empty();
        Optional<String> abstractText = Optional.empty();
        int phase = 0;
        while (true) {
            int event = nextChild();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            requireNamespace(SE);
            switch (reader.getLocalName()) {
                case "Title" -> {
                    requirePhase(phase <= 0, "Title");
                    phase = 1;
                    title = Optional.of(readMetadataText("Title"));
                }
                case "Abstract" -> {
                    requirePhase(phase <= 1, "Abstract");
                    phase = 2;
                    abstractText = Optional.of(readMetadataText("Abstract"));
                }
                default -> throw unsupportedElement(reader.getLocalName());
            }
        }
        return new SeDescription(title, abstractText);
    }

    private SymbolAnchor parseAnchor() throws XMLStreamException {
        requireElement(SE, "AnchorPoint", "SE_SYMBOLIZER_UNSUPPORTED");
        requireNoAttributes();
        double x = requiredCoordinate("AnchorPointX");
        double y = requiredCoordinate("AnchorPointY");
        requireEnd();
        int horizontal = anchorOrdinal(x);
        int vertical = anchorOrdinal(y);
        return switch (vertical * 3 + horizontal) {
            case 0 -> SymbolAnchor.SOUTH_WEST;
            case 1 -> SymbolAnchor.SOUTH;
            case 2 -> SymbolAnchor.SOUTH_EAST;
            case 3 -> SymbolAnchor.WEST;
            case 4 -> SymbolAnchor.CENTER;
            case 5 -> SymbolAnchor.EAST;
            case 6 -> SymbolAnchor.NORTH_WEST;
            case 7 -> SymbolAnchor.NORTH;
            case 8 -> SymbolAnchor.NORTH_EAST;
            default -> throw new AssertionError();
        };
    }

    private Offset parseDisplacement() throws XMLStreamException {
        requireElement(SE, "Displacement", "SE_SYMBOLIZER_UNSUPPORTED");
        requireNoAttributes();
        double x = requiredFinite("DisplacementX");
        double y = requiredFinite("DisplacementY");
        requireEnd();
        return new Offset(x, y);
    }

    private double requiredCoordinate(String name) throws XMLStreamException {
        double value = requiredFinite(name);
        if (Double.compare(value, 0.0) != 0
                && Double.compare(value, 0.5) != 0
                && Double.compare(value, 1.0) != 0) {
            throw failure("SE_VALUE_INVALID", currentPath(), name);
        }
        return value;
    }

    private double requiredFinite(String name) throws XMLStreamException {
        int event = nextChild();
        if (event != XMLStreamConstants.START_ELEMENT
                || !SE.equals(reader.getNamespaceURI())
                || !name.equals(reader.getLocalName())) {
            throw failure("SE_ELEMENT_UNSUPPORTED", currentPath(), "expected" + name);
        }
        return decimalElement(name, -Double.MAX_VALUE, Double.MAX_VALUE, false);
    }

    private void requireEnd() throws XMLStreamException {
        if (nextChild() != XMLStreamConstants.END_ELEMENT) {
            throw failure("SE_ELEMENT_UNSUPPORTED", currentPath(), "unexpectedChild");
        }
    }

    private int anchorOrdinal(double value) {
        if (Double.compare(value, 0.0) == 0) {
            return 0;
        }
        return Double.compare(value, 0.5) == 0 ? 1 : 2;
    }

    private BuiltInMarker wellKnown(String value) {
        return switch (value) {
            case "square" -> BuiltInMarker.SQUARE;
            case "circle" -> BuiltInMarker.CIRCLE;
            case "triangle" -> BuiltInMarker.TRIANGLE;
            case "star" -> BuiltInMarker.STAR;
            case "cross" -> BuiltInMarker.CROSS;
            case "x" -> BuiltInMarker.X;
            default -> throw failure("SE_SYMBOLIZER_UNSUPPORTED", currentPath(), value);
        };
    }

    private Rgba color(String value, double opacity) {
        if (!value.matches("#[0-9A-Fa-f]{6}")) {
            throw failure("SE_VALUE_INVALID", currentPath(), "color");
        }
        return new Rgba(
                Integer.parseInt(value.substring(1, 3), 16),
                Integer.parseInt(value.substring(3, 5), 16),
                Integer.parseInt(value.substring(5, 7), 16),
                alpha(opacity));
    }

    private static Rgba withOpacity(Rgba color, double opacity) {
        return new Rgba(color.red(), color.green(), color.blue(), alpha(opacity));
    }

    private static int alpha(double opacity) {
        return (int) StrictMath.round(opacity * 255.0);
    }

    private double decimalElement(
            String element, double minimum, double maximum, boolean exclusiveMinimum)
            throws XMLStreamException {
        return decimalValue(readValueText(element), minimum, maximum, exclusiveMinimum);
    }

    private double decimalValue(
            String value, double minimum, double maximum, boolean exclusiveMinimum) {
        try {
            BigDecimal exact = new BigDecimal(value);
            double parsed = exact.doubleValue();
            if (!Double.isFinite(parsed)
                    || (exclusiveMinimum ? parsed <= minimum : parsed < minimum)
                    || parsed > maximum) {
                throw failure("SE_VALUE_INVALID", currentPath(), "numberRange");
            }
            return parsed == 0.0 ? 0.0 : parsed;
        } catch (NumberFormatException failure) {
            throw failure("SE_VALUE_INVALID", currentPath(), "number");
        }
    }

    private String readMetadataText(String element) throws XMLStreamException {
        String value = readValueText(element).strip();
        if (value.isBlank()) {
            throw failure("SE_VALUE_INVALID", currentPath(), "blankText");
        }
        return value;
    }

    private String readValueText(String element) throws XMLStreamException {
        requireElement(SE, element, "SE_ELEMENT_UNSUPPORTED");
        requireNoAttributes();
        return readTextBody();
    }

    private String readTextBody() throws XMLStreamException {
        StringBuilder text = new StringBuilder();
        while (reader.hasNext()) {
            int event = nextEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                leaveElement();
                budget.charge((long) text.length() * 2L + 24L, currentPath());
                String result = text.toString();
                if (result.length() > limits.maximumValueCharacters()) {
                    throw limit(
                            "valueCharacters", result.length(), limits.maximumValueCharacters());
                }
                return result;
            }
            if (event == XMLStreamConstants.CHARACTERS) {
                String part = reader.getText();
                budget.charge((long) part.length() * 2L + 24L, currentPath());
                textCharacters += part.length();
                if (textCharacters > limits.maximumAggregateTextCharacters()) {
                    throw limit(
                            "textCharacters",
                            textCharacters,
                            limits.maximumAggregateTextCharacters());
                }
                if ((long) text.length() + part.length() > limits.maximumValueCharacters()) {
                    throw limit(
                            "valueCharacters",
                            (long) text.length() + part.length(),
                            limits.maximumValueCharacters());
                }
                text.append(part);
                continue;
            }
            if (event == XMLStreamConstants.COMMENT) {
                continue;
            }
            if (event == XMLStreamConstants.CDATA) {
                throw failure("SE_XML_SECURITY", currentPath(), "cdata");
            }
            throw failure("SE_ELEMENT_UNSUPPORTED", currentPath(), "nestedContent");
        }
        throw failure("SE_XML_SYNTAX", currentPath(), "truncated");
    }

    private int nextChild() throws XMLStreamException {
        while (reader.hasNext()) {
            int event = nextEvent();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    enterElement();
                    return event;
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    leaveElement();
                    return event;
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE -> {
                    if (!reader.isWhiteSpace()) {
                        throw failure("SE_ELEMENT_UNSUPPORTED", currentPath(), "mixedContent");
                    }
                }
                case XMLStreamConstants.COMMENT -> {}
                case XMLStreamConstants.CDATA ->
                        throw failure("SE_XML_SECURITY", currentPath(), "cdata");
                default -> rejectEvent(event);
            }
        }
        throw failure("SE_XML_SYNTAX", currentPath(), "truncated");
    }

    private void moveToRoot() throws XMLStreamException {
        while (reader.hasNext()) {
            int event = nextEvent();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    enterElement();
                    return;
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE -> {
                    if (!reader.isWhiteSpace()) {
                        throw failure("SE_ROOT_UNSUPPORTED", "/", "leadingText");
                    }
                }
                case XMLStreamConstants.COMMENT, XMLStreamConstants.START_DOCUMENT -> {}
                default -> rejectEvent(event);
            }
        }
        throw failure("SE_ROOT_UNSUPPORTED", "/", "missingRoot");
    }

    private void finishDocument() throws XMLStreamException {
        while (reader.hasNext()) {
            int event = nextEvent();
            switch (event) {
                case XMLStreamConstants.END_DOCUMENT, XMLStreamConstants.COMMENT -> {}
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.SPACE -> {
                    if (!reader.isWhiteSpace()) {
                        throw failure("SE_ROOT_UNSUPPORTED", "/", "trailingText");
                    }
                }
                default -> throw failure("SE_ROOT_UNSUPPORTED", "/", "multipleRoots");
            }
        }
    }

    private int nextEvent() throws XMLStreamException {
        int event = reader.next();
        if ((++events & 255) == 0) {
            checkCancelled();
        }
        return event;
    }

    private void enterElement() {
        path.addLast(reader.getLocalName());
        if (++depth > limits.maximumElementDepth()) {
            throw limit("elementDepth", depth, limits.maximumElementDepth());
        }
        if (++elements > limits.maximumElements()) {
            throw limit("elements", elements, limits.maximumElements());
        }
        attributes += reader.getAttributeCount();
        if (attributes > limits.maximumAttributes()) {
            throw limit("attributes", attributes, limits.maximumAttributes());
        }
    }

    private void leaveElement() {
        depth--;
        path.removeLast();
    }

    private void validateDeclaration() {
        String version = reader.getVersion();
        String encoding = reader.getCharacterEncodingScheme();
        if ((version != null && !"1.0".equals(version))
                || (encoding != null && !"UTF-8".equalsIgnoreCase(encoding))) {
            throw failure("SE_XML_SYNTAX", "/", "declaration");
        }
    }

    private String decode() {
        int offset =
                bytes.length >= 3
                                && (bytes[0] & 0xff) == 0xef
                                && (bytes[1] & 0xff) == 0xbb
                                && (bytes[2] & 0xff) == 0xbf
                        ? 3
                        : 0;
        try {
            budget.charge((long) (bytes.length - offset) * 2L + 64L, "/");
            String decoded =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(ByteBuffer.wrap(bytes, offset, bytes.length - offset))
                            .toString();
            return decoded;
        } catch (CharacterCodingException failure) {
            throw failure("SE_XML_SYNTAX", "/", "utf8");
        }
    }

    private XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        requireProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        requireProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        requireProperty(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        requireProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
        requireProperty(factory, XMLInputFactory.IS_VALIDATING, false);
        requireProperty(factory, XMLInputFactory.IS_COALESCING, false);
        requireProperty(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        requireProperty(factory, XMLConstants.USE_CATALOG, false);
        XMLResolver reject =
                (publicId, systemId, baseUri, namespace) -> {
                    throw new XMLStreamException("external resolution disabled");
                };
        factory.setXMLResolver(reject);
        return factory;
    }

    private void requireProperty(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
            if (!value.equals(factory.getProperty(name))) {
                throw failure("SE_XML_SECURITY", "/", "ineffectiveParserProperty");
            }
        } catch (IllegalArgumentException failure) {
            throw failure("SE_XML_SECURITY", "/", "unsupportedParserProperty");
        }
    }

    private void rejectEvent(int event) {
        String reason =
                switch (event) {
                    case XMLStreamConstants.DTD -> "doctype";
                    case XMLStreamConstants.ENTITY_REFERENCE -> "entity";
                    case XMLStreamConstants.PROCESSING_INSTRUCTION -> "processingInstruction";
                    case XMLStreamConstants.CDATA -> "cdata";
                    default -> "unexpectedEvent";
                };
        throw failure("SE_XML_SECURITY", currentPath(), reason);
    }

    private void requireElement(String namespace, String local, String code) {
        if (!local.equals(reader.getLocalName())) {
            throw failure(code, currentPath(), reader.getLocalName());
        }
        if (!namespace.equals(reader.getNamespaceURI())) {
            throw failure("SE_NAMESPACE_UNSUPPORTED", currentPath(), namespace());
        }
    }

    private void requireNamespace(String namespace) {
        if (!namespace.equals(reader.getNamespaceURI())) {
            throw failure("SE_NAMESPACE_UNSUPPORTED", currentPath(), namespace());
        }
    }

    private void requireNoAttributes() {
        requireAttributes(Map.of());
    }

    private void requireAttributes(Map<QName, AttributeRule> allowed) {
        Map<QName, Boolean> seen = new HashMap<>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            QName name = reader.getAttributeName(index);
            AttributeRule rule = allowed.get(name);
            if (rule == null || seen.put(name, Boolean.TRUE) != null) {
                throw failure("SE_ELEMENT_UNSUPPORTED", currentPath(), "attribute:" + name);
            }
            String value = reader.getAttributeValue(index);
            if (rule.exact() != null && !rule.exact().equals(value)) {
                throw failure("SE_VALUE_INVALID", currentPath(), "attribute:" + name);
            }
        }
        allowed.forEach(
                (name, rule) -> {
                    if (rule.required() && !seen.containsKey(name)) {
                        throw failure(
                                "SE_ELEMENT_UNSUPPORTED",
                                currentPath(),
                                "missingAttribute:" + name);
                    }
                });
    }

    private String attribute(String namespace, String local) {
        return reader.getAttributeValue(namespace, local);
    }

    private void requirePhase(boolean condition, String element) {
        if (!condition) {
            throw failure("SE_ELEMENT_UNSUPPORTED", currentPath(), "order:" + element);
        }
    }

    private SeReadException unsupportedElement(String element) {
        return failure("SE_ELEMENT_UNSUPPORTED", currentPath(), element);
    }

    private SeReadException limit(String name, long observed, long maximum) {
        return SeFailures.limit(source, name, observed, maximum, currentPath());
    }

    private SeReadException failure(String code, String location, String reason) {
        return SeFailures.failure(source, code, location, reason);
    }

    private void checkCancelled() {
        if (options.cancellation().isCancellationRequested()) {
            throw SeFailures.cancelled(source);
        }
    }

    private String currentPath() {
        return path.isEmpty() ? "/" : "/" + String.join("/", path);
    }

    private String namespace() {
        String namespace = reader.getNamespaceURI();
        return namespace == null || namespace.isEmpty() ? "(empty)" : namespace;
    }

    private void closeReader() {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException ignored) {
            // The complete input is memory-owned; reader close releases no external authority.
        }
    }

    private record ParsedRule(SeRuleMetadata metadata, PortrayalRule portrayal) {}

    private record Mark(BuiltInMarker shape, Rgba fill, Optional<SymbolStroke> stroke) {}

    private record Paint(Rgba color, double width) {}

    private record Offset(double x, double y) {}

    private record AttributeRule(boolean required, String exact) {
        static AttributeRule optionalAny() {
            return new AttributeRule(false, null);
        }

        static AttributeRule requiredAny() {
            return new AttributeRule(true, null);
        }

        static AttributeRule requiredExact(String value) {
            return new AttributeRule(true, value);
        }
    }
}
