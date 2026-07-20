package io.github.mundanej.map.workspace;

import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class WorkspaceXmlReader {
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");
    private static final Set<String> KNOWN_ELEMENTS =
            Set.of(
                    "workspace",
                    "view",
                    "layers",
                    "feature-layer",
                    "raster-layer",
                    "source",
                    "symbols");

    private final WorkspaceReadBudget budget;
    private final XMLStreamReader reader;
    private Integer layerIndex;

    private WorkspaceXmlReader(String xml, WorkspaceLimits limits) {
        rejectForbiddenLexicalContent(xml);
        budget = new WorkspaceReadBudget(limits);
        reader = createReader(xml);
    }

    static WorkspaceDocument read(String xml, WorkspaceLimits limits) {
        WorkspaceXmlReader parser = new WorkspaceXmlReader(xml, limits);
        WorkspaceDocument document;
        try {
            document = parser.document();
        } catch (WorkspaceException failure) {
            try {
                parser.close();
            } catch (WorkspaceException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
        parser.close();
        return document;
    }

    private WorkspaceDocument document() {
        declaration();
        nextContent();
        enter("workspace", true, Set.of());
        Map<String, String> root = attributes(Set.of("version"));
        if (!root.get("version").equals("1")) {
            throw WorkspaceFailures.version("version");
        }

        nextContent();
        enter("view", false, Set.of());
        WorkspaceViewState view = parseView();
        requireEnd("view");

        nextContent();
        enter("layers", false, Set.of("view"));
        requireNoAttributes();
        List<WorkspaceLayerDefinition> layers = parseLayers();
        leave("layers");

        leaveParent("workspace", Set.of("view", "layers"));
        int trailing = nextContent();
        if (trailing != XMLStreamConstants.END_DOCUMENT) {
            throw WorkspaceFailures.structure("cardinality", null);
        }
        long modelBytes = WorkspaceDocument.logicalModelBytes(view, layers);
        if (modelBytes > WorkspaceText.MAX_MODEL_BYTES) {
            throw WorkspaceFailures.limit(
                    "operationBytes", modelBytes, WorkspaceText.MAX_MODEL_BYTES);
        }
        return new WorkspaceDocument(view, layers);
    }

    private WorkspaceViewState parseView() {
        Map<String, String> values =
                attributes(
                        Set.of(
                                "map-crs",
                                "display-crs",
                                "center-x",
                                "center-y",
                                "units-per-pixel"));
        String mapCrs = values.get("map-crs");
        String displayCrs = values.get("display-crs");
        if (!mapCrs.equals("EPSG:4326") && !mapCrs.equals("EPSG:3857")) {
            throw WorkspaceFailures.value("mapCrs", "nonCanonical", null);
        }
        if (!displayCrs.equals("EPSG:4326") && !displayCrs.equals("EPSG:3857")) {
            throw WorkspaceFailures.value("displayCrs", "nonCanonical", null);
        }
        double centerX = decimal(values.get("center-x"), "centerX", false, false);
        double centerY = decimal(values.get("center-y"), "centerY", false, false);
        double scale = decimal(values.get("units-per-pixel"), "unitsPerPixel", true, false);
        return new WorkspaceViewState(mapCrs, displayCrs, centerX, centerY, scale);
    }

    private List<WorkspaceLayerDefinition> parseLayers() {
        List<WorkspaceLayerDefinition> layers = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        while (true) {
            int event = nextContent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                if (!reader.getLocalName().equals("layers")) {
                    throw WorkspaceFailures.structure("order", layerIndex);
                }
                layerIndex = null;
                return List.copyOf(layers);
            }
            if (event != XMLStreamConstants.START_ELEMENT) {
                throw WorkspaceFailures.structure("missing", layerIndex);
            }
            String name = reader.getLocalName();
            if (!name.equals("feature-layer") && !name.equals("raster-layer")) {
                layerIndex = null;
                throw unknownElement(name);
            }
            layerIndex = budget.nextLayer();
            WorkspaceLayerDefinition layer =
                    switch (name) {
                        case "feature-layer" -> parseFeatureLayer();
                        case "raster-layer" -> parseRasterLayer();
                        default -> throw new AssertionError("unreachable layer kind");
                    };
            if (!ids.add(layer.id())) {
                throw WorkspaceFailures.value("layerId", "duplicate", layerIndex);
            }
            layers.add(layer);
            layerIndex = null;
        }
    }

    private WorkspaceFeatureLayer parseFeatureLayer() {
        enter("feature-layer", false, Set.of());
        Map<String, String> layer = attributes(Set.of("id", "name"));
        String id = text(layer.get("id"), "layerId", 256, true, false);
        String name = text(layer.get("name"), "layerName", 256, false, false);
        nextContent();
        enter("source", false, Set.of());
        WorkspaceSourceReference source = parseSource();
        requireEnd("source");
        nextContent();
        enter("symbols", false, Set.of("source"));
        WorkspaceSymbolReferences symbols = parseSymbols();
        requireEnd("symbols");
        leaveParent("feature-layer", Set.of("source", "symbols"));
        return new WorkspaceFeatureLayer(id, name, source, symbols);
    }

    private WorkspaceRasterLayer parseRasterLayer() {
        enter("raster-layer", false, Set.of());
        Map<String, String> layer = attributes(Set.of("id", "name", "interpolation", "opacity"));
        String id = text(layer.get("id"), "layerId", 256, true, false);
        String name = text(layer.get("name"), "layerName", 256, false, false);
        RasterInterpolation interpolation;
        try {
            interpolation = RasterInterpolation.valueOf(layer.get("interpolation"));
        } catch (IllegalArgumentException failure) {
            throw WorkspaceFailures.value("interpolation", "grammar", layerIndex);
        }
        double opacity = decimal(layer.get("opacity"), "opacity", false, true);
        nextContent();
        enter("source", false, Set.of());
        WorkspaceSourceReference source = parseSource();
        requireEnd("source");
        leaveParent("raster-layer", Set.of("source"));
        return new WorkspaceRasterLayer(id, name, source, interpolation, opacity);
    }

    private WorkspaceSourceReference parseSource() {
        Map<String, String> source = attributes(Set.of("opener", "id", "name", "path"));
        String opener = text(source.get("opener"), "sourceOpener", 128, true, true);
        String id = text(source.get("id"), "sourceId", 256, true, false);
        String name = text(source.get("name"), "sourceName", 256, false, false);
        String path = text(source.get("path"), "sourcePath", 4_096, true, false);
        WorkspaceRelativePath relativePath;
        try {
            relativePath = new WorkspaceRelativePath(path);
        } catch (IllegalArgumentException failure) {
            throw WorkspaceFailures.value("sourcePath", "grammar", layerIndex);
        }
        return new WorkspaceSourceReference(opener, new SourceIdentity(id, name), relativePath);
    }

    private WorkspaceSymbolReferences parseSymbols() {
        Map<String, String> symbols = attributes(Set.of("catalog", "marker", "line", "fill"));
        return new WorkspaceSymbolReferences(
                text(symbols.get("catalog"), "catalogId", 128, true, true),
                symbolName(symbols.get("marker"), "markerName"),
                symbolName(symbols.get("line"), "lineName"),
                symbolName(symbols.get("fill"), "fillName"));
    }

    private Map<String, String> attributes(Set<String> expected) {
        if (reader.getAttributeCount() != expected.size()) {
            Set<String> actual = new HashSet<>();
            for (int index = 0; index < reader.getAttributeCount(); index++) {
                actual.add(reader.getAttributeLocalName(index));
            }
            if (actual.containsAll(expected)) {
                throw WorkspaceFailures.unknown("attribute", layerIndex);
            }
            throw WorkspaceFailures.structure("missing", layerIndex);
        }
        var values = new LinkedHashMap<String, String>();
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            String namespace = reader.getAttributeNamespace(index);
            String prefix = reader.getAttributePrefix(index);
            if ((namespace != null && !namespace.isEmpty())
                    || (prefix != null && !prefix.isEmpty())) {
                throw WorkspaceFailures.unknown("attribute", layerIndex);
            }
            String name = reader.getAttributeLocalName(index);
            if (!expected.contains(name)) {
                throw WorkspaceFailures.unknown("attribute", layerIndex);
            }
            String value = reader.getAttributeValue(index);
            values.put(name, value);
        }
        return values;
    }

    private void requireNoAttributes() {
        if (reader.getAttributeCount() != 0) {
            throw WorkspaceFailures.unknown("attribute", layerIndex);
        }
    }

    private void enter(String expected, boolean root, Set<String> duplicates) {
        if (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            throw WorkspaceFailures.structure("order", layerIndex);
        }
        String namespace = reader.getNamespaceURI();
        if (!WorkspaceText.NAMESPACE.equals(namespace)) {
            if (root) {
                throw WorkspaceFailures.version("namespace");
            }
            throw WorkspaceFailures.unknown("namespace", layerIndex);
        }
        if (!reader.getLocalName().equals(expected)) {
            String actual = reader.getLocalName();
            if (duplicates.contains(actual)) {
                throw WorkspaceFailures.structure("duplicate", layerIndex);
            }
            throw knownWrongOrderOrUnknown(actual);
        }
        int namespaces = reader.getNamespaceCount();
        budget.enterElement();
        budget.attributes(namespaces + reader.getAttributeCount());
        for (int index = 0; index < namespaces; index++) {
            String namespaceValue = reader.getNamespaceURI(index);
            budget.value(namespaceValue == null ? "" : namespaceValue);
        }
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            budget.value(reader.getAttributeValue(index));
        }
        if (root) {
            if (namespaces != 1 || !WorkspaceText.NAMESPACE.equals(reader.getNamespaceURI(0))) {
                throw WorkspaceFailures.unknown("namespace", layerIndex);
            }
        } else if (namespaces != 0) {
            throw WorkspaceFailures.unknown("namespace", layerIndex);
        }
    }

    private void requireEnd(String expected) {
        nextContent();
        if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
            String actual = reader.getLocalName();
            if (actual.equals(expected)) {
                throw WorkspaceFailures.structure("duplicate", layerIndex);
            }
            throw knownWrongOrderOrUnknown(actual);
        }
        leave(expected);
    }

    private void leaveParent(String expected, Set<String> duplicates) {
        nextContent();
        if (reader.getEventType() == XMLStreamConstants.START_ELEMENT) {
            String actual = reader.getLocalName();
            if (duplicates.contains(actual)) {
                throw WorkspaceFailures.structure("duplicate", layerIndex);
            }
            throw knownWrongOrderOrUnknown(actual);
        }
        leave(expected);
    }

    private void leave(String expected) {
        if (reader.getEventType() != XMLStreamConstants.END_ELEMENT
                || !reader.getLocalName().equals(expected)
                || !WorkspaceText.NAMESPACE.equals(reader.getNamespaceURI())) {
            throw WorkspaceFailures.structure("order", layerIndex);
        }
        budget.leaveElement();
    }

    private int nextContent() {
        try {
            while (reader.hasNext()) {
                int event = reader.next();
                switch (event) {
                    case XMLStreamConstants.SPACE -> {
                        continue;
                    }
                    case XMLStreamConstants.CHARACTERS -> {
                        if (reader.isWhiteSpace()) {
                            continue;
                        }
                        throw WorkspaceFailures.structure("text", layerIndex);
                    }
                    case XMLStreamConstants.COMMENT -> {
                        budget.value(reader.getText());
                        continue;
                    }
                    case XMLStreamConstants.START_ELEMENT,
                            XMLStreamConstants.END_ELEMENT,
                            XMLStreamConstants.END_DOCUMENT -> {
                        return event;
                    }
                    case XMLStreamConstants.DTD, XMLStreamConstants.ENTITY_REFERENCE ->
                            throw WorkspaceFailures.xml("security", null);
                    case XMLStreamConstants.CDATA, XMLStreamConstants.PROCESSING_INSTRUCTION ->
                            throw WorkspaceFailures.xml("content", null);
                    default -> {
                        continue;
                    }
                }
            }
            return XMLStreamConstants.END_DOCUMENT;
        } catch (XMLStreamException failure) {
            throw WorkspaceFailures.xml("wellFormed", failure);
        }
    }

    private void declaration() {
        if (reader.getEventType() != XMLStreamConstants.START_DOCUMENT) {
            throw WorkspaceFailures.xml("declaration", null);
        }
        String version = reader.getVersion();
        String encoding = reader.getCharacterEncodingScheme();
        if ((version != null && !version.equals("1.0"))
                || (encoding != null && !encoding.equalsIgnoreCase("UTF-8"))) {
            throw WorkspaceFailures.xml("declaration", null);
        }
    }

    private double decimal(String token, String field, boolean positive, boolean unitInterval) {
        if (!DECIMAL.matcher(token).matches()) {
            throw WorkspaceFailures.value(field, "grammar", layerIndex);
        }
        double value;
        try {
            value = Double.parseDouble(token);
        } catch (NumberFormatException failure) {
            throw WorkspaceFailures.value(field, "grammar", layerIndex);
        }
        if (!Double.isFinite(value)
                || (positive && value <= 0.0)
                || (unitInterval && (value < 0.0 || value > 1.0))) {
            throw WorkspaceFailures.value(field, "range", layerIndex);
        }
        return value == 0.0 ? 0.0 : value;
    }

    private String text(
            String value, String field, int maximum, boolean nonBlank, boolean openerGrammar) {
        try {
            String checked = WorkspaceText.text(value, field, maximum, nonBlank);
            return openerGrammar ? WorkspaceText.openerId(checked) : checked;
        } catch (IllegalArgumentException failure) {
            String reason = failure.getMessage().contains("XML 1.0") ? "xmlCharacter" : "grammar";
            throw WorkspaceFailures.value(field, reason, layerIndex);
        }
    }

    private String symbolName(String value, String field) {
        text(value, field, 256, true, false);
        try {
            return WorkspaceText.symbolName(value, field);
        } catch (IllegalArgumentException failure) {
            throw WorkspaceFailures.value(field, "nonCanonical", layerIndex);
        }
    }

    private WorkspaceException knownWrongOrderOrUnknown(String name) {
        return KNOWN_ELEMENTS.contains(name)
                ? WorkspaceFailures.structure("order", layerIndex)
                : WorkspaceFailures.unknown("element", layerIndex);
    }

    private WorkspaceException unknownElement(String name) {
        return KNOWN_ELEMENTS.contains(name)
                ? WorkspaceFailures.structure("order", layerIndex)
                : WorkspaceFailures.unknown("element", layerIndex);
    }

    private XMLStreamReader createReader(String xml) {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        set(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
        set(factory, XMLInputFactory.IS_COALESCING, false);
        set(factory, XMLInputFactory.IS_VALIDATING, false);
        set(factory, XMLInputFactory.SUPPORT_DTD, false);
        set(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        set(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        set(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        set(factory, XMLConstants.USE_CATALOG, false);
        factory.setXMLResolver(
                (publicId, systemId, baseUri, namespace) -> {
                    throw new XMLStreamException("external resolution is disabled");
                });
        factory.setXMLReporter(
                (message, errorType, relatedInformation, location) -> {
                    throw new XMLStreamException("XML provider report");
                });
        try {
            return factory.createXMLStreamReader(new StringReader(xml));
        } catch (XMLStreamException failure) {
            throw WorkspaceFailures.xml("wellFormed", failure);
        }
    }

    private static void set(XMLInputFactory factory, String property, Object expected) {
        try {
            factory.setProperty(property, expected);
            Object actual = factory.getProperty(property);
            if (!expected.equals(actual)) {
                throw new IllegalStateException(
                        "XML security property was not retained: " + property);
            }
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Required XML security property is unsupported: " + property, failure);
        }
    }

    private static void rejectForbiddenLexicalContent(String xml) {
        int offset = 0;
        if (xml.startsWith("<?xml")) {
            int declarationEnd = xml.indexOf("?>", 5);
            if (declarationEnd < 0) {
                return;
            }
            offset = declarationEnd + 2;
        }
        for (; offset < xml.length(); ) {
            int comment = xml.indexOf("<!--", offset);
            int cdata = xml.indexOf("<![CDATA[", offset);
            int doctype = xml.indexOf("<!DOCTYPE", offset);
            int instruction = xml.indexOf("<?", offset);
            int next = first(comment, cdata, doctype, instruction);
            if (next < 0) {
                return;
            }
            if (next == cdata) {
                throw WorkspaceFailures.xml("content", null);
            }
            if (next == doctype) {
                throw WorkspaceFailures.xml("security", null);
            }
            if (next == instruction) {
                throw WorkspaceFailures.xml("content", null);
            }
            int commentEnd = xml.indexOf("-->", comment + 4);
            if (commentEnd < 0) {
                return;
            }
            offset = commentEnd + 3;
        }
    }

    private static int first(int... candidates) {
        int result = Integer.MAX_VALUE;
        for (int candidate : candidates) {
            if (candidate >= 0 && candidate < result) {
                result = candidate;
            }
        }
        return result == Integer.MAX_VALUE ? -1 : result;
    }

    private void close() {
        try {
            reader.close();
        } catch (XMLStreamException failure) {
            throw WorkspaceFailures.xml("wellFormed", failure);
        }
    }
}
