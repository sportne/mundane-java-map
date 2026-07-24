package io.github.mundanej.map.io.kml;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class KmlParser {
    static final AttributeSchema SCHEMA =
            new AttributeSchema(
                    List.of(
                            new AttributeField("kmlId", AttributeType.TEXT, true),
                            new AttributeField("description", AttributeType.TEXT, true),
                            new AttributeField("geometryKind", AttributeType.TEXT, false)));

    private static final String KML = "http://www.opengis.net/kml/2.2";
    private static final String GX = "http://www.google.com/kml/ext/2.2";
    private static final String ATOM = "http://www.w3.org/2005/Atom";
    private static final String XAL = "urn:oasis:names:tc:ciq:xsdschema:xAL:2.0";
    private static final QName ID = new QName("", "id");
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    private static final Set<String> PRESENTATION =
            Set.of(
                    "open",
                    "LookAt",
                    "Camera",
                    "Snippet",
                    "address",
                    "phoneNumber",
                    "Style",
                    "StyleMap",
                    "styleUrl",
                    "ExtendedData");
    private static final Set<String> UNSUPPORTED =
            Set.of(
                    "NetworkLinkControl",
                    "NetworkLink",
                    "GroundOverlay",
                    "PhotoOverlay",
                    "ScreenOverlay",
                    "Model",
                    "Tour",
                    "Update",
                    "Region",
                    "TimeSpan",
                    "TimeStamp",
                    "Schema");

    private final byte[] bytes;
    private final KmlLimits limits;
    private final CancellationToken cancellation;
    private final KmlDiagnostics diagnostics;
    private final List<FeatureRecord> records = new ArrayList<>();
    private XMLStreamReader reader;
    private int depth;
    private int events;
    private int elements;
    private int attributes;
    private int namespaceDeclarations;
    private int textCharacters;
    private int contiguousTextCharacters;
    private int featureDepth;
    private int physicalFeatures;
    private int totalCoordinates;
    private int parts;
    private long currentRecord;
    private long ownedBytes;

    KmlParser(
            byte[] bytes,
            SourceIdentity identity,
            KmlLimits limits,
            CancellationToken cancellation) {
        this.bytes = bytes;
        this.limits = limits;
        this.cancellation = cancellation;
        diagnostics = new KmlDiagnostics(identity.id(), limits.retainedWarnings());
        ownedBytes = bytes.length;
    }

    Opening parse() {
        int offset = validateEncoding();
        XMLInputFactory factory = secureFactory();
        try {
            reader =
                    factory.createXMLStreamReader(
                            new ByteArrayInputStream(bytes, offset, bytes.length - offset));
            if (reader.getVersion() != null && !"1.0".equals(reader.getVersion())) {
                throw encodingFailure("xmlVersion");
            }
            if (reader.getCharacterEncodingScheme() != null
                    && !"UTF-8".equalsIgnoreCase(reader.getCharacterEncodingScheme())) {
                throw encodingFailure("declaredEncoding");
            }
            moveToRoot();
            parseRoot();
            finishDocument();
            checkCancelled();
            return new Opening(List.copyOf(records), diagnostics.report());
        } catch (SourceException failure) {
            throw failure;
        } catch (XMLStreamException failure) {
            throw xmlFailure("syntax", failure);
        } finally {
            closeReader();
        }
    }

    private void moveToRoot() throws XMLStreamException {
        chargeEvent(reader.getEventType());
        while (reader.getEventType() != XMLStreamConstants.START_ELEMENT) {
            int event = nextEvent();
            if (event == XMLStreamConstants.END_DOCUMENT) {
                throw xmlFailure("syntax", null);
            }
            if (isText(event) && !reader.isWhiteSpace()) {
                throw xmlFailure("syntax", null);
            }
        }
    }

    private void parseRoot() throws XMLStreamException {
        requireElement("kml");
        requireAttributes(Set.of());
        int event = nextChildEvent();
        if (event != XMLStreamConstants.START_ELEMENT) {
            throw xmlFailure("cardinality", null);
        }
        requireKmlElement();
        if (UNSUPPORTED.contains(reader.getLocalName())) {
            throw profileFailure(unsupportedContext(reader.getLocalName()));
        }
        if (!isFeature(reader.getLocalName())) {
            throw xmlFailure("cardinality", null);
        }
        parseFeature();
        if (nextChildEvent() != XMLStreamConstants.END_ELEMENT) {
            throw xmlFailure("cardinality", null);
        }
    }

    private void parseFeature() throws XMLStreamException {
        String feature = reader.getLocalName();
        requireElement(feature);
        enterFeature();
        try {
            if ("Placemark".equals(feature)) {
                parsePlacemark();
            } else {
                parseContainer();
            }
        } finally {
            featureDepth--;
        }
    }

    private void parseContainer() throws XMLStreamException {
        requireAttributes(Set.of());
        int stage = 0;
        Set<String> seen = new HashSet<>();
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
            String local = reader.getLocalName();
            String presentation = presentationConstruct();
            String unsupported = unsupportedConstruct();
            if (isFeature(local)) {
                requireKmlElement();
                stage = requireOrder(stage, 200, false);
                parseFeature();
            } else if ("name".equals(local) || "description".equals(local)) {
                requireKmlElement();
                stage = requireOrder(stage, featureRank(local), !seen.add(local));
                parseScalar(local, Set.of());
            } else if ("visibility".equals(local)) {
                requireKmlElement();
                stage = requireOrder(stage, featureRank(local), !seen.add(local));
                parseVisibility();
            } else if (presentation != null) {
                int rank = featureRank(local);
                boolean repeatable = "style".equals(presentation);
                stage = requireOrder(stage, rank, !repeatable && !seen.add(presentationKey()));
                warning("KML_PRESENTATION_IGNORED", Map.of("construct", presentation));
                skipSubtree();
            } else if (unsupported != null) {
                throw profileFailure(unsupported);
            } else {
                requireKmlElement();
                throw profileFailure("foreignElement");
            }
        }
    }

    private void parsePlacemark() throws XMLStreamException {
        currentRecord = chargePhysicalFeature();
        requireAttributes(Set.of(ID));
        String kmlId = reader.getAttributeValue("", "id");
        if (kmlId != null) {
            kmlId = validateId(kmlId);
        }
        String name = "";
        Object description = AttributeNull.INSTANCE;
        Geometry geometry = null;
        String geometryKind = null;
        boolean nameSeen = false;
        boolean descriptionSeen = false;
        int stage = 0;
        Set<String> presentationSeen = new HashSet<>();
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            String local = reader.getLocalName();
            String presentation = presentationConstruct();
            String unsupported = unsupportedConstruct();
            if (unsupported != null) {
                throw profileFailure(unsupported);
            }
            if (presentation != null) {
                int rank = featureRank(local);
                boolean repeatable = "style".equals(presentation);
                stage =
                        requireOrder(
                                stage,
                                rank,
                                !repeatable && !presentationSeen.add(presentationKey()));
                warning("KML_PRESENTATION_IGNORED", Map.of("construct", presentation));
                skipSubtree();
                continue;
            }
            requireKmlElement();
            switch (local) {
                case "name" -> {
                    stage = requireOrder(stage, featureRank(local), false);
                    if (nameSeen) {
                        throw valueFailure("name", "duplicate");
                    }
                    nameSeen = true;
                    name = validateText(parseScalar("name", Set.of()));
                }
                case "description" -> {
                    stage = requireOrder(stage, featureRank(local), false);
                    if (descriptionSeen) {
                        throw valueFailure("description", "duplicate");
                    }
                    descriptionSeen = true;
                    description = validateText(parseScalar("description", Set.of()));
                }
                case "visibility" -> {
                    stage =
                            requireOrder(
                                    stage, featureRank(local), !presentationSeen.add("visibility"));
                    parseVisibility();
                }
                case "Point", "LineString", "Polygon", "MultiGeometry" -> {
                    stage = requireOrder(stage, 200, false);
                    if (geometry != null) {
                        throw valueFailure("coordinates", "cardinality");
                    }
                    GeometryResult result = parseGeometry(local, totalCoordinates);
                    geometry = result.geometry();
                    geometryKind = result.kind();
                }
                default -> throw profileFailure("foreignElement");
            }
        }
        if (geometry == null) {
            warning("KML_PLACEMARK_SKIPPED", Map.of("reason", "noGeometry"));
            currentRecord = 0;
            return;
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("kmlId", kmlId == null ? AttributeNull.INSTANCE : kmlId);
        values.put("description", description);
        values.put("geometryKind", geometryKind);
        chargeOwned(320L + 2L * name.length());
        records.add(new FeatureRecord("kml:placemark:" + physicalFeatures, name, geometry, values));
        currentRecord = 0;
    }

    private GeometryResult parseGeometry(String kind, int geometryCoordinateStart)
            throws XMLStreamException {
        return switch (kind) {
            case "Point" ->
                    new GeometryResult("point", parseSimpleGeometry(kind, geometryCoordinateStart));
            case "LineString" ->
                    new GeometryResult("line", parseSimpleGeometry(kind, geometryCoordinateStart));
            case "Polygon" -> new GeometryResult("polygon", parsePolygon(geometryCoordinateStart));
            case "MultiGeometry" -> parseMultiGeometry(geometryCoordinateStart);
            default -> throw new IllegalStateException("Unexpected supported KML geometry");
        };
    }

    private Geometry parseSimpleGeometry(String kind, int geometryCoordinateStart)
            throws XMLStreamException {
        requireAttributes(Set.of());
        String coordinates = null;
        int stage = 0;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            String local = requireKmlElement();
            switch (local) {
                case "coordinates" -> {
                    if (coordinates != null) {
                        throw valueFailure("coordinates", "duplicate");
                    }
                    int maximumStage = "Point".equals(kind) ? 2 : 3;
                    if (stage > maximumStage) {
                        throw xmlFailure("order", null);
                    }
                    stage = maximumStage + 1;
                    coordinates = parseScalar("coordinates", Set.of());
                }
                case "altitudeMode" -> {
                    int altitudeStage = "Point".equals(kind) ? 2 : 3;
                    if (stage >= altitudeStage) {
                        throw xmlFailure("order", null);
                    }
                    stage = altitudeStage;
                    String mode = parseScalar("coordinates", Set.of()).strip();
                    if (!"clampToGround".equals(mode)) {
                        throw profileFailure("altitudeMode");
                    }
                }
                case "extrude" -> {
                    if (stage > 0) {
                        throw xmlFailure("order", null);
                    }
                    stage = 1;
                    requireFalse(parseScalar("coordinates", Set.of()), "extrude");
                }
                case "tessellate" -> {
                    if ("Point".equals(kind)) {
                        throw profileFailure("tessellate");
                    }
                    if (stage > 1) {
                        throw xmlFailure("order", null);
                    }
                    stage = 2;
                    requireFalse(parseScalar("coordinates", Set.of()), "tessellate");
                }
                default -> throw profileFailure("geometry");
            }
        }
        if (coordinates == null) {
            throw valueFailure("coordinates", "missing");
        }
        CoordinateSequence sequence = parseCoordinates(coordinates, geometryCoordinateStart);
        if ("Point".equals(kind)) {
            if (sequence.size() != 1) {
                throw valueFailure("coordinates", "cardinality");
            }
            return new PointGeometry(sequence.coordinate(0));
        }
        if (sequence.size() < 2) {
            throw valueFailure("coordinates", "cardinality");
        }
        return new LineStringGeometry(sequence);
    }

    private PolygonGeometry parsePolygon(int geometryCoordinateStart) throws XMLStreamException {
        requireAttributes(Set.of());
        CoordinateSequence exterior = null;
        List<CoordinateSequence> holes = new ArrayList<>();
        int stage = 0;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            String local = requireKmlElement();
            switch (local) {
                case "outerBoundaryIs" -> {
                    if (exterior != null) {
                        throw valueFailure("outerRing", "duplicate");
                    }
                    if (stage > 3) {
                        throw xmlFailure("order", null);
                    }
                    stage = 4;
                    exterior = parseBoundary("outerRing", geometryCoordinateStart);
                }
                case "innerBoundaryIs" -> {
                    if (stage < 4) {
                        throw xmlFailure("order", null);
                    }
                    stage = 5;
                    chargeOwned(8);
                    holes.add(parseBoundary("innerRing", geometryCoordinateStart));
                }
                case "altitudeMode" -> {
                    if (stage > 2) {
                        throw xmlFailure("order", null);
                    }
                    stage = 3;
                    String mode = parseScalar("coordinates", Set.of()).strip();
                    if (!"clampToGround".equals(mode)) {
                        throw profileFailure("altitudeMode");
                    }
                }
                case "extrude" -> {
                    if (stage > 0) {
                        throw xmlFailure("order", null);
                    }
                    stage = 1;
                    requireFalse(parseScalar("coordinates", Set.of()), "extrude");
                }
                case "tessellate" -> {
                    if (stage > 1) {
                        throw xmlFailure("order", null);
                    }
                    stage = 2;
                    requireFalse(parseScalar("coordinates", Set.of()), "tessellate");
                }
                default -> throw profileFailure("geometry");
            }
        }
        if (exterior == null) {
            throw valueFailure("outerRing", "missing");
        }
        return new PolygonGeometry(exterior, holes);
    }

    private CoordinateSequence parseBoundary(String field, int geometryCoordinateStart)
            throws XMLStreamException {
        requireAttributes(Set.of());
        if (nextChildEvent() != XMLStreamConstants.START_ELEMENT
                || !"LinearRing".equals(requireKmlElement())) {
            throw valueFailure(field, "missing");
        }
        requireAttributes(Set.of());
        String coordinates = null;
        int stage = 0;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            String local = requireKmlElement();
            switch (local) {
                case "extrude" -> {
                    if (stage > 0) {
                        throw xmlFailure("order", null);
                    }
                    stage = 1;
                    requireFalse(parseScalar("coordinates", Set.of()), "extrude");
                }
                case "tessellate" -> {
                    if (stage > 1) {
                        throw xmlFailure("order", null);
                    }
                    stage = 2;
                    requireFalse(parseScalar("coordinates", Set.of()), "tessellate");
                }
                case "altitudeMode" -> {
                    if (stage > 2) {
                        throw xmlFailure("order", null);
                    }
                    stage = 3;
                    String mode = parseScalar("coordinates", Set.of()).strip();
                    if (!"clampToGround".equals(mode)) {
                        throw profileFailure("altitudeMode");
                    }
                }
                case "coordinates" -> {
                    if (stage > 3 || coordinates != null) {
                        throw valueFailure(field, "cardinality");
                    }
                    stage = 4;
                    coordinates = parseScalar("coordinates", Set.of());
                }
                default -> throw profileFailure("geometry");
            }
        }
        if (nextChildEvent() != XMLStreamConstants.END_ELEMENT) {
            throw valueFailure(field, "cardinality");
        }
        if (coordinates == null) {
            throw valueFailure(field, "missing");
        }
        CoordinateSequence ring = parseCoordinates(coordinates, geometryCoordinateStart);
        if (ring.size() < 4) {
            throw valueFailure(field, "cardinality");
        }
        if (!ring.isClosed()) {
            throw valueFailure(field, "closure");
        }
        return ring;
    }

    private GeometryResult parseMultiGeometry(int geometryCoordinateStart)
            throws XMLStreamException {
        requireAttributes(Set.of());
        List<GeometryResult> components = new ArrayList<>();
        String family = null;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            String local = requireKmlElement();
            if ("MultiGeometry".equals(local)
                    || (!"Point".equals(local)
                            && !"LineString".equals(local)
                            && !"Polygon".equals(local))) {
                throw profileFailure("multiGeometry");
            }
            String encounteredFamily =
                    switch (local) {
                        case "Point" -> "point";
                        case "LineString" -> "line";
                        case "Polygon" -> "polygon";
                        default -> throw new IllegalStateException("Unexpected KML geometry");
                    };
            if (family != null && !family.equals(encounteredFamily)) {
                throw profileFailure("multiGeometry");
            }
            family = encounteredFamily;
            if ("Polygon".equals(local)) {
                chargePart();
            }
            GeometryResult component = parseGeometry(local, geometryCoordinateStart);
            chargeOwned(8);
            components.add(component);
        }
        if (components.isEmpty()) {
            throw profileFailure("multiGeometry");
        }
        return switch (components.get(0).kind()) {
            case "point" ->
                    new GeometryResult(
                            "multipoint", new MultiPointGeometry(packPoints(components)));
            case "line" -> {
                chargeOwned(multiLineFlattenBytes(components));
                yield new GeometryResult(
                        "multiline",
                        MultiLineStringGeometry.ofParts(
                                components.stream()
                                        .map(
                                                component ->
                                                        ((LineStringGeometry) component.geometry())
                                                                .coordinates())
                                        .toList()));
            }
            case "polygon" -> {
                chargeOwned(multiPolygonFlattenBytes(components));
                yield new GeometryResult(
                        "multipolygon",
                        MultiPolygonGeometry.ofPolygons(
                                components.stream()
                                        .map(component -> (PolygonGeometry) component.geometry())
                                        .toList()));
            }
            default -> throw new IllegalStateException("Unexpected KML MultiGeometry family");
        };
    }

    private CoordinateSequence packPoints(List<GeometryResult> components) {
        chargeOwned(32L * components.size());
        double[] ordinates = new double[Math.multiplyExact(components.size(), 2)];
        for (int index = 0; index < components.size(); index++) {
            var coordinate = ((PointGeometry) components.get(index).geometry()).coordinate();
            ordinates[index * 2] = coordinate.x();
            ordinates[index * 2 + 1] = coordinate.y();
        }
        return CoordinateSequence.of(ordinates);
    }

    private static long coordinateCount(List<GeometryResult> components) {
        long coordinates = 0;
        for (GeometryResult component : components) {
            Geometry geometry = component.geometry();
            long componentCoordinates = 0;
            if (geometry instanceof LineStringGeometry line) {
                componentCoordinates = line.coordinates().size();
            } else if (geometry instanceof PolygonGeometry polygon) {
                componentCoordinates = polygon.exterior().size();
                for (CoordinateSequence hole : polygon.holes()) {
                    componentCoordinates = Math.addExact(componentCoordinates, hole.size());
                }
            }
            coordinates = Math.addExact(coordinates, componentCoordinates);
        }
        return coordinates;
    }

    private static long multiLineFlattenBytes(List<GeometryResult> components) {
        long coordinateBytes = Math.multiplyExact(32, coordinateCount(components));
        long fenceBytes = Math.multiplyExact(8L, Math.addExact(components.size(), 1));
        return Math.addExact(coordinateBytes, fenceBytes);
    }

    private static long multiPolygonFlattenBytes(List<GeometryResult> components) {
        long rings = 0;
        for (GeometryResult component : components) {
            PolygonGeometry polygon = (PolygonGeometry) component.geometry();
            rings = Math.addExact(rings, Math.addExact(1, polygon.holes().size()));
        }
        long coordinateBytes = Math.multiplyExact(32, coordinateCount(components));
        long ringFenceBytes = Math.multiplyExact(8, Math.addExact(rings, 1));
        long polygonFenceBytes = Math.multiplyExact(8L, Math.addExact(components.size(), 1));
        return Math.addExact(coordinateBytes, Math.addExact(ringFenceBytes, polygonFenceBytes));
    }

    private CoordinateSequence parseCoordinates(String value, int geometryCoordinateStart) {
        String stripped = value.strip();
        if (stripped.isEmpty()) {
            throw valueFailure("coordinates", "missing");
        }
        java.util.StringTokenizer tokenizer = new java.util.StringTokenizer(stripped);
        int tupleCount = tokenizer.countTokens();
        if (tupleCount > limits.maximumCoordinatesPerGeometry()) {
            throw limit("geometryCoordinates", tupleCount, limits.maximumCoordinatesPerGeometry());
        }
        long geometryCoordinates =
                Math.addExact((long) totalCoordinates - geometryCoordinateStart, tupleCount);
        if (geometryCoordinates > limits.maximumCoordinatesPerGeometry()) {
            throw limit(
                    "geometryCoordinates",
                    geometryCoordinates,
                    limits.maximumCoordinatesPerGeometry());
        }
        chargeGeometry(tupleCount);
        double[] packed = new double[Math.multiplyExact(tupleCount, 2)];
        for (int index = 0; index < tupleCount; index++) {
            checkCancelled();
            String tuple = tokenizer.nextToken();
            int firstComma = tuple.indexOf(',');
            int secondComma = firstComma < 0 ? -1 : tuple.indexOf(',', firstComma + 1);
            if (firstComma <= 0
                    || firstComma == tuple.length() - 1
                    || (secondComma >= 0
                            && (secondComma == firstComma + 1
                                    || secondComma == tuple.length() - 1
                                    || tuple.indexOf(',', secondComma + 1) >= 0))) {
                throw valueFailure("coordinates", "syntax");
            }
            String longitude = tuple.substring(0, firstComma);
            String latitude =
                    tuple.substring(firstComma + 1, secondComma < 0 ? tuple.length() : secondComma);
            double x = coordinate("longitude", longitude, -180, 180);
            double y = coordinate("latitude", latitude, -90, 90);
            if (secondComma >= 0) {
                decimal("altitude", tuple.substring(secondComma + 1));
                warning("KML_ALTITUDE_IGNORED", Map.of());
            }
            packed[index * 2] = x;
            packed[index * 2 + 1] = y;
        }
        return CoordinateSequence.of(packed);
    }

    private void parseVisibility() throws XMLStreamException {
        String visibility = parseScalar("coordinates", Set.of()).strip();
        if (!"1".equals(visibility) && !"true".equals(visibility)) {
            throw profileFailure("visibility");
        }
    }

    private void requireFalse(String value, String construct) {
        String stripped = value.strip();
        if (!"0".equals(stripped) && !"false".equals(stripped)) {
            throw profileFailure(construct);
        }
    }

    private String parseScalar(String field, Set<QName> attributesAllowed)
            throws XMLStreamException {
        requireAttributes(attributesAllowed);
        char[] value = new char[0];
        int length = 0;
        while (true) {
            int event = nextEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                chargeOwned(2L * length);
                String result = new String(value, 0, length);
                releaseOwned(2L * value.length);
                return result;
            }
            if (isText(event)) {
                int textLength = reader.getTextLength();
                if ((long) length + textLength > limits.maximumScalarCharacters()) {
                    throw limit(
                            "scalarCharacters",
                            (long) length + textLength,
                            limits.maximumScalarCharacters());
                }
                int required = Math.addExact(length, textLength);
                if (required > value.length) {
                    chargeOwned(2L * required);
                    char[] grown = java.util.Arrays.copyOf(value, required);
                    releaseOwned(2L * value.length);
                    value = grown;
                }
                System.arraycopy(
                        reader.getTextCharacters(),
                        reader.getTextStart(),
                        value,
                        length,
                        textLength);
                length = required;
            } else if (event != XMLStreamConstants.COMMENT
                    && event != XMLStreamConstants.PROCESSING_INSTRUCTION) {
                throw valueFailure(field, "nestedContent");
            }
        }
    }

    private void skipSubtree() throws XMLStreamException {
        int nested = 1;
        while (nested > 0) {
            int event = nextEvent();
            if (event == XMLStreamConstants.START_ELEMENT) {
                nested++;
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                nested--;
            }
        }
    }

    private void finishDocument() throws XMLStreamException {
        while (reader.hasNext()) {
            int event = nextEvent();
            if (event == XMLStreamConstants.END_DOCUMENT) {
                return;
            }
            if (isText(event) && reader.isWhiteSpace()) {
                continue;
            }
            if (event != XMLStreamConstants.COMMENT
                    && event != XMLStreamConstants.PROCESSING_INSTRUCTION) {
                throw xmlFailure("trailingContent", null);
            }
        }
    }

    private int nextChildEvent() throws XMLStreamException {
        while (true) {
            int event = nextEvent();
            if (event == XMLStreamConstants.START_ELEMENT
                    || event == XMLStreamConstants.END_ELEMENT) {
                return event;
            }
            if (isText(event) && reader.isWhiteSpace()) {
                continue;
            }
            if (event != XMLStreamConstants.COMMENT
                    && event != XMLStreamConstants.PROCESSING_INSTRUCTION) {
                throw xmlFailure("syntax", null);
            }
        }
    }

    private int nextEvent() throws XMLStreamException {
        checkCancelled();
        int event = reader.next();
        chargeEvent(event);
        checkCancelled();
        return event;
    }

    private void chargeEvent(int event) {
        if (isText(event)) {
            contiguousTextCharacters =
                    Math.addExact(contiguousTextCharacters, reader.getTextLength());
            requireScalar(contiguousTextCharacters);
        } else {
            contiguousTextCharacters = 0;
            if (++events > limits.maximumXmlEvents()) {
                throw limit("xmlEvents", events, limits.maximumXmlEvents());
            }
        }
        switch (event) {
            case XMLStreamConstants.START_ELEMENT -> {
                if (++depth > limits.maximumXmlDepth()) {
                    throw limit("xmlDepth", depth, limits.maximumXmlDepth());
                }
                if (++elements > limits.maximumElements()) {
                    throw limit("elements", elements, limits.maximumElements());
                }
                attributes = Math.addExact(attributes, reader.getAttributeCount());
                if (attributes > limits.maximumAttributes()) {
                    throw limit("attributes", attributes, limits.maximumAttributes());
                }
                namespaceDeclarations =
                        Math.addExact(namespaceDeclarations, reader.getNamespaceCount());
                if (namespaceDeclarations > limits.maximumNamespaceDeclarations()) {
                    throw limit(
                            "namespaceDeclarations",
                            namespaceDeclarations,
                            limits.maximumNamespaceDeclarations());
                }
                chargeToken(reader.getLocalName());
                chargeToken(reader.getNamespaceURI());
                chargeToken(reader.getPrefix());
                for (int index = 0; index < reader.getAttributeCount(); index++) {
                    chargeToken(reader.getAttributeLocalName(index));
                    chargeToken(reader.getAttributeNamespace(index));
                    chargeToken(reader.getAttributeValue(index));
                }
                for (int index = 0; index < reader.getNamespaceCount(); index++) {
                    chargeToken(reader.getNamespacePrefix(index));
                    chargeToken(reader.getNamespaceURI(index));
                }
            }
            case XMLStreamConstants.END_ELEMENT -> depth--;
            case XMLStreamConstants.CHARACTERS,
                    XMLStreamConstants.CDATA,
                    XMLStreamConstants.SPACE ->
                    chargeText(reader.getTextLength());
            case XMLStreamConstants.COMMENT -> chargeToken(reader.getText());
            case XMLStreamConstants.PROCESSING_INSTRUCTION -> {
                chargeToken(reader.getPITarget());
                chargeToken(reader.getPIData());
            }
            case XMLStreamConstants.DTD -> throw xmlFailure("doctype", null);
            case XMLStreamConstants.ENTITY_REFERENCE -> throw xmlFailure("entity", null);
            default -> {
                // Other document events need only their event charge.
            }
        }
    }

    private void enterFeature() {
        if (++featureDepth > limits.maximumFeatureDepth()) {
            throw limit("featureDepth", featureDepth, limits.maximumFeatureDepth());
        }
    }

    private long chargePhysicalFeature() {
        if (++physicalFeatures > limits.maximumPhysicalFeatures()) {
            throw limit("features", physicalFeatures, limits.maximumPhysicalFeatures());
        }
        chargeOwned(8);
        return physicalFeatures;
    }

    private void chargeGeometry(int coordinateCount) {
        chargePart();
        long requestedCoordinates = (long) totalCoordinates + coordinateCount;
        if (requestedCoordinates > limits.maximumTotalCoordinates()) {
            throw limit("coordinates", requestedCoordinates, limits.maximumTotalCoordinates());
        }
        long requestedOwned = Math.addExact(ownedBytes, 32L * coordinateCount);
        if (requestedOwned > limits.maximumOwnedBytes()) {
            throw limit("ownedBytes", requestedOwned, limits.maximumOwnedBytes());
        }
        totalCoordinates = Math.toIntExact(requestedCoordinates);
        ownedBytes = requestedOwned;
    }

    private void chargePart() {
        long requestedParts = (long) parts + 1;
        if (requestedParts > limits.maximumParts()) {
            throw limit("parts", requestedParts, limits.maximumParts());
        }
        long requestedOwned = Math.addExact(ownedBytes, 4);
        if (requestedOwned > limits.maximumOwnedBytes()) {
            throw limit("ownedBytes", requestedOwned, limits.maximumOwnedBytes());
        }
        parts++;
        ownedBytes = requestedOwned;
    }

    private void chargeToken(String value) {
        int length = value == null ? 0 : value.length();
        requireScalar(length);
        chargeText(length);
    }

    private void chargeText(int count) {
        textCharacters = Math.addExact(textCharacters, count);
        if (textCharacters > limits.maximumTextCharacters()) {
            throw limit("textCharacters", textCharacters, limits.maximumTextCharacters());
        }
        chargeOwned(2L * count);
    }

    private void requireScalar(long length) {
        if (length > limits.maximumScalarCharacters()) {
            throw limit("scalarCharacters", length, limits.maximumScalarCharacters());
        }
    }

    private void chargeOwned(long count) {
        ownedBytes = Math.addExact(ownedBytes, count);
        if (ownedBytes > limits.maximumOwnedBytes()) {
            throw limit("ownedBytes", ownedBytes, limits.maximumOwnedBytes());
        }
    }

    private void releaseOwned(long count) {
        ownedBytes = Math.subtractExact(ownedBytes, count);
        if (ownedBytes < 0) {
            throw new IllegalStateException("KML owned-byte accounting underflow");
        }
    }

    private void warning(String code, Map<String, String> context) {
        if (diagnostics.canRetainWarning()) {
            chargeOwned(256);
        }
        diagnostics.warning(code, context, currentRecord);
    }

    private String validateText(String value) {
        if (value.length() > limits.maximumScalarCharacters()) {
            throw limit("scalarCharacters", value.length(), limits.maximumScalarCharacters());
        }
        return value;
    }

    private String validateId(String value) {
        if (value.isBlank()) {
            throw valueFailure("id", "syntax");
        }
        return validateText(value);
    }

    private double coordinate(String field, String token, double minimum, double maximum) {
        double value = decimal(field, token);
        if (value < minimum || value > maximum) {
            throw valueFailure(field, "range");
        }
        return value == 0 ? 0 : value;
    }

    private double decimal(String field, String value) {
        String token = value.strip();
        if (token.length() > limits.maximumNumberCharacters()) {
            throw limit("numberCharacters", token.length(), limits.maximumNumberCharacters());
        }
        if (!DECIMAL.matcher(token).matches()) {
            throw valueFailure(field, "syntax");
        }
        try {
            double parsed = Double.parseDouble(token);
            if (!Double.isFinite(parsed)) {
                throw valueFailure(field, "nonFinite");
            }
            return parsed == 0 ? 0 : parsed;
        } catch (NumberFormatException failure) {
            throw valueFailure(field, "nonFinite");
        }
    }

    private int validateEncoding() {
        int offset = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            warning("KML_UTF8_BOM_IGNORED", Map.of());
            offset = 3;
        } else if (hasUnsupportedBom()) {
            throw encodingFailure("bom");
        }
        int index = offset;
        while (index < bytes.length) {
            checkCancelled();
            int first = bytes[index] & 0xff;
            int codePoint;
            int length;
            if (first < 0x80) {
                codePoint = first;
                length = 1;
            } else if (first >= 0xc2 && first <= 0xdf) {
                codePoint = first & 0x1f;
                length = 2;
            } else if (first >= 0xe0 && first <= 0xef) {
                codePoint = first & 0x0f;
                length = 3;
            } else if (first >= 0xf0 && first <= 0xf4) {
                codePoint = first & 0x07;
                length = 4;
            } else {
                throw encodingFailure("utf8");
            }
            if (index + length > bytes.length) {
                throw encodingFailure("utf8");
            }
            for (int part = 1; part < length; part++) {
                int continuation = bytes[index + part] & 0xff;
                if ((continuation & 0xc0) != 0x80) {
                    throw encodingFailure("utf8");
                }
                codePoint = codePoint << 6 | (continuation & 0x3f);
            }
            if ((length == 3
                            && ((first == 0xe0 && (bytes[index + 1] & 0xff) < 0xa0)
                                    || (first == 0xed && (bytes[index + 1] & 0xff) >= 0xa0)))
                    || (length == 4
                            && ((first == 0xf0 && (bytes[index + 1] & 0xff) < 0x90)
                                    || (first == 0xf4 && (bytes[index + 1] & 0xff) >= 0x90)))
                    || !validXmlCodePoint(codePoint)) {
                throw encodingFailure("utf8");
            }
            index += length;
        }
        validateDeclaration(offset);
        return offset;
    }

    private void validateDeclaration(int offset) {
        if (bytes.length - offset < 5
                || bytes[offset] != '<'
                || bytes[offset + 1] != '?'
                || bytes[offset + 2] != 'x'
                || bytes[offset + 3] != 'm'
                || bytes[offset + 4] != 'l') {
            return;
        }
        int end = -1;
        int maximum = Math.min(bytes.length - 1, offset + 512);
        for (int index = offset + 5; index < maximum; index++) {
            if ((bytes[index] & 0xff) > 0x7f) {
                throw encodingFailure("declaredEncoding");
            }
            if (bytes[index] == '?' && bytes[index + 1] == '>') {
                end = index + 2;
                break;
            }
        }
        if (end < 0) {
            return;
        }
        String declaration =
                new String(bytes, offset, end - offset, java.nio.charset.StandardCharsets.US_ASCII);
        Matcher version =
                Pattern.compile("version\\s*=\\s*(['\"])([^'\"]+)\\1", Pattern.CASE_INSENSITIVE)
                        .matcher(declaration);
        if (version.find() && !"1.0".equals(version.group(2))) {
            throw encodingFailure("xmlVersion");
        }
        Matcher encoding =
                Pattern.compile("encoding\\s*=\\s*(['\"])([^'\"]+)\\1", Pattern.CASE_INSENSITIVE)
                        .matcher(declaration);
        if (encoding.find() && !"utf-8".equalsIgnoreCase(encoding.group(2))) {
            throw encodingFailure("declaredEncoding");
        }
    }

    private boolean hasUnsupportedBom() {
        return bytes.length >= 2
                && (((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff)
                        || ((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe)
                        || (bytes.length >= 4
                                && bytes[0] == 0
                                && bytes[1] == 0
                                && (bytes[2] & 0xff) == 0xfe
                                && (bytes[3] & 0xff) == 0xff));
    }

    private XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        configure(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
        configure(factory, XMLInputFactory.IS_COALESCING, false);
        configure(factory, XMLInputFactory.IS_VALIDATING, false);
        configure(factory, XMLInputFactory.SUPPORT_DTD, false);
        configure(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        configure(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        configure(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        configure(factory, XMLConstants.USE_CATALOG, false);
        factory.setXMLResolver(
                (publicId, systemId, baseUri, namespace) -> {
                    throw new XMLStreamException("External KML resources are disabled");
                });
        factory.setXMLReporter(
                (message, errorType, relatedInformation, location) -> {
                    throw new XMLStreamException("KML parser report");
                });
        return factory;
    }

    private static void configure(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
            if (!value.equals(factory.getProperty(name))) {
                throw new IllegalStateException("Required KML parser policy was not retained");
            }
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException("Required KML parser policy is unavailable", failure);
        }
    }

    private void closeReader() {
        if (reader != null) {
            try {
                reader.close();
            } catch (XMLStreamException ignored) {
                // No resource survives eager parsing; a terminal parse error remains primary.
            }
        }
    }

    private void requireElement(String local) {
        if (!local.equals(reader.getLocalName()) || !KML.equals(reader.getNamespaceURI())) {
            throw xmlFailure("namespace", null);
        }
    }

    private String requireKmlElement() {
        String namespace = reader.getNamespaceURI();
        if (GX.equals(namespace)) {
            if ("Tour".equals(reader.getLocalName())) {
                throw profileFailure("tour");
            }
            if ("altitudeMode".equals(reader.getLocalName())) {
                throw profileFailure("altitudeMode");
            }
        }
        if (!KML.equals(namespace)) {
            throw profileFailure("foreignElement");
        }
        return reader.getLocalName();
    }

    private int requireOrder(int previous, int encountered, boolean duplicate) {
        if (encountered < previous) {
            throw xmlFailure("order", null);
        }
        if (duplicate) {
            throw xmlFailure("cardinality", null);
        }
        return Math.max(previous, encountered);
    }

    private String presentationConstruct() {
        String namespace = reader.getNamespaceURI();
        String local = reader.getLocalName();
        if (KML.equals(namespace) && PRESENTATION.contains(local)) {
            return presentationContext(local).get("construct");
        }
        if ((ATOM.equals(namespace) && ("author".equals(local) || "link".equals(local)))
                || (XAL.equals(namespace) && "AddressDetails".equals(local))) {
            return "contact";
        }
        return null;
    }

    private String presentationKey() {
        return reader.getNamespaceURI() + '\u0000' + reader.getLocalName();
    }

    private String unsupportedConstruct() {
        String namespace = reader.getNamespaceURI();
        String local = reader.getLocalName();
        if (KML.equals(namespace) && UNSUPPORTED.contains(local)) {
            return unsupportedContext(local);
        }
        if (GX.equals(namespace) && "Tour".equals(local)) {
            return "tour";
        }
        if (GX.equals(namespace) && "altitudeMode".equals(local)) {
            return "altitudeMode";
        }
        return null;
    }

    private static int featureRank(String local) {
        return switch (local) {
            case "name" -> 10;
            case "visibility" -> 20;
            case "open" -> 30;
            case "author" -> 40;
            case "link" -> 41;
            case "address" -> 42;
            case "AddressDetails" -> 43;
            case "phoneNumber" -> 44;
            case "Snippet" -> 50;
            case "description" -> 60;
            case "LookAt", "Camera" -> 70;
            case "styleUrl" -> 80;
            case "Style", "StyleMap" -> 90;
            case "Region" -> 100;
            case "ExtendedData" -> 110;
            default -> 200;
        };
    }

    private void requireAttributes(Set<QName> allowed) {
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            if (!allowed.contains(reader.getAttributeName(index))) {
                throw profileFailure("attribute");
            }
        }
    }

    private void checkCancelled() {
        if (cancellation.isCancellationRequested()) {
            throw diagnostics.failure(
                    "SOURCE_CANCELLED",
                    Map.of("operation", "kml-open"),
                    currentRecord,
                    "KML operation was cancelled",
                    null);
        }
    }

    private SourceException limit(String limit, long requested, long maximum) {
        return diagnostics.failure(
                "SOURCE_LIMIT_EXCEEDED",
                Map.of(
                        "scope",
                        "kmlOpen",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                currentRecord,
                "KML opening limit was exceeded",
                null);
    }

    private SourceException encodingFailure(String reason) {
        return diagnostics.failure(
                "KML_ENCODING_INVALID",
                Map.of("reason", reason),
                currentRecord,
                "KML encoding is outside the supported profile",
                null);
    }

    private SourceException xmlFailure(String reason, Throwable cause) {
        return diagnostics.failure(
                "KML_XML_INVALID",
                Map.of("reason", reason),
                currentRecord,
                "KML XML is invalid",
                cause);
    }

    private SourceException profileFailure(String construct) {
        return diagnostics.failure(
                "KML_PROFILE_UNSUPPORTED",
                Map.of("construct", construct),
                currentRecord,
                "KML construct is outside the supported profile",
                null);
    }

    private SourceException valueFailure(String field, String reason) {
        return diagnostics.failure(
                "KML_VALUE_INVALID",
                Map.of("field", field, "reason", reason),
                currentRecord,
                "KML value is invalid",
                null);
    }

    private static boolean isFeature(String local) {
        return "Document".equals(local) || "Folder".equals(local) || "Placemark".equals(local);
    }

    private static boolean isText(int event) {
        return event == XMLStreamConstants.CHARACTERS
                || event == XMLStreamConstants.CDATA
                || event == XMLStreamConstants.SPACE;
    }

    private static boolean validXmlCodePoint(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xa
                || codePoint == 0xd
                || (codePoint >= 0x20 && codePoint <= 0xd7ff)
                || (codePoint >= 0xe000 && codePoint <= 0xfffd)
                || (codePoint >= 0x10000 && codePoint <= 0x10ffff);
    }

    private static Map<String, String> presentationContext(String local) {
        return Map.of(
                "construct",
                switch (local) {
                    case "open" -> "open";
                    case "LookAt", "Camera" -> "view";
                    case "Snippet" -> "snippet";
                    case "Style", "StyleMap" -> "style";
                    case "styleUrl" -> "styleUrl";
                    case "ExtendedData" -> "extendedData";
                    default -> "contact";
                });
    }

    private static String unsupportedContext(String local) {
        return switch (local) {
            case "NetworkLink", "NetworkLinkControl" -> "network";
            case "GroundOverlay", "PhotoOverlay", "ScreenOverlay" -> "overlay";
            case "Model" -> "model";
            case "Tour" -> "tour";
            case "Update" -> "update";
            case "Region" -> "region";
            case "TimeSpan", "TimeStamp" -> "time";
            case "Schema" -> "schema";
            default -> "foreignElement";
        };
    }

    record Opening(List<FeatureRecord> records, DiagnosticReport diagnostics) {}

    private record GeometryResult(String kind, Geometry geometry) {}
}
