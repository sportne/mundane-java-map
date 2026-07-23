package io.github.mundanej.map.io.gpx;

import io.github.mundanej.map.api.AttributeField;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeSchema;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayInputStream;
import java.time.DateTimeException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

final class GpxParser {
    static final AttributeSchema SCHEMA =
            new AttributeSchema(
                    List.of(
                            new AttributeField("gpxKind", AttributeType.TEXT, false),
                            new AttributeField("trackIndex", AttributeType.INTEGER, true),
                            new AttributeField("segmentIndex", AttributeType.INTEGER, true),
                            new AttributeField("elevationMetres", AttributeType.FLOATING, true),
                            new AttributeField("time", AttributeType.TEXT, true),
                            new AttributeField("comment", AttributeType.TEXT, true),
                            new AttributeField("description", AttributeType.TEXT, true),
                            new AttributeField("source", AttributeType.TEXT, true),
                            new AttributeField("symbol", AttributeType.TEXT, true),
                            new AttributeField("type", AttributeType.TEXT, true),
                            new AttributeField("trackNumber", AttributeType.INTEGER, true)));

    private static final String GPX = "http://www.topografix.com/GPX/1/1";
    private static final String XSI = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
    private static final QName VERSION = new QName("", "version");
    private static final QName CREATOR = new QName("", "creator");
    private static final QName SCHEMA_LOCATION = new QName(XSI, "schemaLocation");
    private static final Pattern DECIMAL =
            Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");
    private static final Pattern NON_NEGATIVE_INTEGER = Pattern.compile("[0-9]+");
    private static final Pattern TIME =
            Pattern.compile(
                    "[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}"
                            + "(?:\\.[0-9]{1,9})?(?:Z|[+-][0-9]{2}:[0-9]{2})");
    private static final Map<String, Integer> WAYPOINT_ORDER =
            Map.ofEntries(
                    Map.entry("ele", 1),
                    Map.entry("time", 2),
                    Map.entry("magvar", 3),
                    Map.entry("geoidheight", 4),
                    Map.entry("name", 5),
                    Map.entry("cmt", 6),
                    Map.entry("desc", 7),
                    Map.entry("src", 8),
                    Map.entry("link", 9),
                    Map.entry("sym", 10),
                    Map.entry("type", 11),
                    Map.entry("fix", 12),
                    Map.entry("sat", 13),
                    Map.entry("hdop", 14),
                    Map.entry("vdop", 15),
                    Map.entry("pdop", 16),
                    Map.entry("ageofdgpsdata", 17),
                    Map.entry("dgpsid", 18),
                    Map.entry("extensions", 19));
    private static final Set<String> RETAINED_WAYPOINT_FIELDS =
            Set.of("ele", "time", "name", "cmt", "desc", "src", "sym", "type");
    private static final Map<String, Integer> TRACK_ORDER =
            Map.ofEntries(
                    Map.entry("name", 1),
                    Map.entry("cmt", 2),
                    Map.entry("desc", 3),
                    Map.entry("src", 4),
                    Map.entry("link", 5),
                    Map.entry("number", 6),
                    Map.entry("type", 7),
                    Map.entry("extensions", 8),
                    Map.entry("trkseg", 9));

    private final byte[] bytes;
    private final GpxLimits limits;
    private final CancellationToken cancellation;
    private final GpxDiagnostics diagnostics;
    private final List<FeatureRecord> records = new ArrayList<>();
    private XMLStreamReader reader;
    private int depth;
    private int events;
    private int elements;
    private int attributes;
    private int namespaces;
    private int textCharacters;
    private int physicalFeatures;
    private int coordinates;
    private int parts;
    private int currentPointIndex = -1;
    private long ownedBytes;
    private long currentRecord;

    GpxParser(
            byte[] bytes,
            SourceIdentity identity,
            GpxLimits limits,
            CancellationToken cancellation) {
        this.bytes = bytes;
        this.limits = limits;
        this.cancellation = cancellation;
        diagnostics = new GpxDiagnostics(identity.id(), limits.retainedWarnings());
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
            if (event == XMLStreamConstants.CHARACTERS && !reader.isWhiteSpace()) {
                throw xmlFailure("syntax", null);
            }
        }
    }

    private void parseRoot() throws XMLStreamException {
        if (!"gpx".equals(reader.getLocalName()) || !GPX.equals(reader.getNamespaceURI())) {
            throw xmlFailure("namespace", null);
        }
        requireAttributes(Set.of(VERSION, CREATOR, SCHEMA_LOCATION));
        if (!"1.1".equals(reader.getAttributeValue("", "version"))) {
            throw xmlFailure("cardinality", null);
        }
        String creator = reader.getAttributeValue("", "creator");
        if (creator == null) {
            throw valueFailure("creator", "missing");
        }
        validateText("creator", creator);

        int phase = 0;
        int waypointOrdinal = 0;
        int trackOrdinal = 0;
        boolean metadataSeen = false;
        boolean extensionsSeen = false;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            if (!GPX.equals(reader.getNamespaceURI())) {
                throw profileFailure("foreignElement");
            }
            String local = reader.getLocalName();
            switch (local) {
                case "metadata" -> {
                    if (metadataSeen || phase > 0) {
                        throw xmlFailure(metadataSeen ? "cardinality" : "order", null);
                    }
                    metadataSeen = true;
                    parseMetadata();
                }
                case "wpt" -> {
                    if (phase > 1) {
                        throw xmlFailure("order", null);
                    }
                    phase = 1;
                    records.add(parseWaypoint(++waypointOrdinal));
                }
                case "rte" -> throw profileFailure("route");
                case "trk" -> {
                    if (phase > 3) {
                        throw xmlFailure("order", null);
                    }
                    phase = 3;
                    parseTrack(++trackOrdinal);
                }
                case "extensions" -> {
                    if (extensionsSeen) {
                        throw xmlFailure("cardinality", null);
                    }
                    extensionsSeen = true;
                    phase = 4;
                    diagnostics.warning("GPX_EXTENSION_IGNORED", Map.of("scope", "root"), 0);
                    skipSubtree();
                }
                default -> throw profileFailure("coreElement");
            }
        }
    }

    private void parseMetadata() throws XMLStreamException {
        requireAttributes(Set.of());
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
            if (!GPX.equals(reader.getNamespaceURI())) {
                throw profileFailure("foreignElement");
            }
            if ("extensions".equals(reader.getLocalName())) {
                diagnostics.warning("GPX_EXTENSION_IGNORED", Map.of("scope", "metadata"), 0);
            } else {
                diagnostics.warning("GPX_FIELD_IGNORED", Map.of("scope", "metadata"), 0);
            }
            skipSubtree();
        }
    }

    private FeatureRecord parseWaypoint(int ordinal) throws XMLStreamException {
        currentRecord = chargePhysicalFeature();
        requireAttributes(Set.of(new QName("", "lat"), new QName("", "lon")));
        String latitude = reader.getAttributeValue("", "lat");
        String longitude = reader.getAttributeValue("", "lon");
        if (latitude == null) {
            throw valueFailure("latitude", "missing");
        }
        if (longitude == null) {
            throw valueFailure("longitude", "missing");
        }
        double y = coordinate("latitude", latitude, -90, 90, true);
        double x = coordinate("longitude", longitude, -180, 180, false);
        chargeCoordinate();

        String name = "";
        Object elevation = AttributeNull.INSTANCE;
        Object time = AttributeNull.INSTANCE;
        Object comment = AttributeNull.INSTANCE;
        Object description = AttributeNull.INSTANCE;
        Object source = AttributeNull.INSTANCE;
        Object symbol = AttributeNull.INSTANCE;
        Object type = AttributeNull.INSTANCE;
        Set<String> seen = new java.util.HashSet<>();
        int phase = 0;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            if (!GPX.equals(reader.getNamespaceURI())) {
                throw profileFailure("foreignElement");
            }
            String local = reader.getLocalName();
            Integer rank = WAYPOINT_ORDER.get(local);
            if (rank == null) {
                throw profileFailure("coreElement");
            }
            if (rank < phase) {
                throw xmlFailure("order", null);
            }
            phase = rank;
            if (!"link".equals(local) && !seen.add(local)) {
                throw valueFailure(fieldName(local), "duplicate");
            }
            if ("extensions".equals(local)) {
                diagnostics.warning(
                        "GPX_EXTENSION_IGNORED", Map.of("scope", "waypoint"), currentRecord);
                skipSubtree();
                continue;
            }
            if (!RETAINED_WAYPOINT_FIELDS.contains(local)) {
                diagnostics.warning(
                        "GPX_FIELD_IGNORED", Map.of("scope", "waypoint"), currentRecord);
                skipSubtree();
                continue;
            }
            requireAttributes(Set.of());
            String value = readScalar();
            switch (local) {
                case "ele" -> elevation = elevation(value);
                case "time" -> time = time(value);
                case "name" -> name = validateText("name", value);
                case "cmt" -> comment = validateText("comment", value);
                case "desc" -> description = validateText("description", value);
                case "src" -> source = validateText("source", value);
                case "sym" -> symbol = validateText("symbol", value);
                case "type" -> type = validateText("type", value);
                default -> throw new IllegalStateException("Unexpected retained GPX field");
            }
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("gpxKind", "waypoint");
        values.put("trackIndex", AttributeNull.INSTANCE);
        values.put("segmentIndex", AttributeNull.INSTANCE);
        values.put("elevationMetres", elevation);
        values.put("time", time);
        values.put("comment", comment);
        values.put("description", description);
        values.put("source", source);
        values.put("symbol", symbol);
        values.put("type", type);
        values.put("trackNumber", AttributeNull.INSTANCE);
        chargeOwned(320L + 2L * name.length());
        currentRecord = 0;
        return new FeatureRecord(
                "gpx:wpt:" + ordinal, name, new PointGeometry(new Coordinate(x, y)), values);
    }

    private void parseTrack(int trackOrdinal) throws XMLStreamException {
        requireAttributes(Set.of());
        String name = "";
        Object comment = AttributeNull.INSTANCE;
        Object description = AttributeNull.INSTANCE;
        Object source = AttributeNull.INSTANCE;
        Object type = AttributeNull.INSTANCE;
        Object trackNumber = AttributeNull.INSTANCE;
        Set<String> seen = new java.util.HashSet<>();
        int phase = 0;
        int segmentOrdinal = 0;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                return;
            }
            if (!GPX.equals(reader.getNamespaceURI())) {
                throw profileFailure("foreignElement");
            }
            String local = reader.getLocalName();
            Integer rank = TRACK_ORDER.get(local);
            if (rank == null) {
                throw profileFailure("coreElement");
            }
            if (rank < phase) {
                throw xmlFailure("order", null);
            }
            phase = rank;
            if (!"link".equals(local) && !"trkseg".equals(local) && !seen.add(local)) {
                throw valueFailure(trackFieldName(local), "duplicate");
            }
            switch (local) {
                case "name" -> {
                    requireAttributes(Set.of());
                    name = validateText("name", readScalar());
                }
                case "cmt" -> {
                    requireAttributes(Set.of());
                    comment = validateText("comment", readScalar());
                }
                case "desc" -> {
                    requireAttributes(Set.of());
                    description = validateText("description", readScalar());
                }
                case "src" -> {
                    requireAttributes(Set.of());
                    source = validateText("source", readScalar());
                }
                case "type" -> {
                    requireAttributes(Set.of());
                    type = validateText("type", readScalar());
                }
                case "number" -> {
                    requireAttributes(Set.of());
                    trackNumber = trackNumber(readScalar());
                }
                case "link" -> {
                    diagnostics.warning("GPX_FIELD_IGNORED", Map.of("scope", "track"), 0);
                    skipSubtree();
                }
                case "extensions" -> {
                    diagnostics.warning("GPX_EXTENSION_IGNORED", Map.of("scope", "track"), 0);
                    skipSubtree();
                }
                case "trkseg" ->
                        parseTrackSegment(
                                trackOrdinal,
                                ++segmentOrdinal,
                                name,
                                comment,
                                description,
                                source,
                                type,
                                trackNumber);
                default -> throw new IllegalStateException("Unexpected GPX track field");
            }
        }
    }

    private void parseTrackSegment(
            int trackOrdinal,
            int segmentOrdinal,
            String name,
            Object comment,
            Object description,
            Object source,
            Object type,
            Object trackNumber)
            throws XMLStreamException {
        currentRecord = chargePhysicalFeature();
        chargePart();
        requireAttributes(Set.of());
        PackedCoordinates packed = new PackedCoordinates();
        boolean extensionsSeen = false;
        int pointIndex = 0;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                break;
            }
            if (!GPX.equals(reader.getNamespaceURI())) {
                throw profileFailure("foreignElement");
            }
            switch (reader.getLocalName()) {
                case "trkpt" -> {
                    if (extensionsSeen) {
                        throw xmlFailure("order", null);
                    }
                    parseTrackPoint(packed, pointIndex++);
                }
                case "extensions" -> {
                    if (extensionsSeen) {
                        throw xmlFailure("cardinality", null);
                    }
                    extensionsSeen = true;
                    diagnostics.warning(
                            "GPX_EXTENSION_IGNORED", Map.of("scope", "segment"), currentRecord);
                    skipSubtree();
                }
                default -> throw profileFailure("coreElement");
            }
        }
        int pointCount = packed.size();
        if (pointCount < 2) {
            diagnostics.warning(
                    "GPX_TRACK_SEGMENT_SKIPPED",
                    Map.of("reason", pointCount == 0 ? "empty" : "singlePoint"),
                    currentRecord);
            currentRecord = 0;
            return;
        }
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        values.put("gpxKind", "trackSegment");
        values.put("trackIndex", (long) trackOrdinal);
        values.put("segmentIndex", (long) segmentOrdinal);
        values.put("elevationMetres", AttributeNull.INSTANCE);
        values.put("time", AttributeNull.INSTANCE);
        values.put("comment", comment);
        values.put("description", description);
        values.put("source", source);
        values.put("symbol", AttributeNull.INSTANCE);
        values.put("type", type);
        values.put("trackNumber", trackNumber);
        chargeOwned(320L + 2L * name.length());
        records.add(
                new FeatureRecord(
                        "gpx:trk:" + trackOrdinal + ":seg:" + segmentOrdinal,
                        name,
                        new LineStringGeometry(CoordinateSequence.of(packed.toArray())),
                        values));
        currentRecord = 0;
    }

    private void parseTrackPoint(PackedCoordinates packed, int pointIndex)
            throws XMLStreamException {
        currentPointIndex = pointIndex;
        requireAttributes(Set.of(new QName("", "lat"), new QName("", "lon")));
        String latitude = reader.getAttributeValue("", "lat");
        String longitude = reader.getAttributeValue("", "lon");
        if (latitude == null) {
            throw valueFailure("latitude", "missing");
        }
        if (longitude == null) {
            throw valueFailure("longitude", "missing");
        }
        double y = coordinate("latitude", latitude, -90, 90, true);
        double x = coordinate("longitude", longitude, -180, 180, false);
        if (pointIndex >= limits.maximumCoordinatesPerSegment()) {
            throw limit(
                    "geometryCoordinates",
                    (long) pointIndex + 1,
                    limits.maximumCoordinatesPerSegment());
        }
        chargeCoordinate();
        packed.add(x, y);

        Set<String> seen = new java.util.HashSet<>();
        int phase = 0;
        while (true) {
            int event = nextChildEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                currentPointIndex = -1;
                return;
            }
            if (!GPX.equals(reader.getNamespaceURI())) {
                throw profileFailure("foreignElement");
            }
            String local = reader.getLocalName();
            Integer rank = WAYPOINT_ORDER.get(local);
            if (rank == null) {
                throw profileFailure("coreElement");
            }
            if (rank < phase) {
                throw xmlFailure("order", null);
            }
            phase = rank;
            if (!"link".equals(local) && !seen.add(local)) {
                throw valueFailure(fieldName(local), "duplicate");
            }
            if ("extensions".equals(local)) {
                diagnostics.warning(
                        "GPX_EXTENSION_IGNORED", Map.of("scope", "trackPoint"), currentRecord);
                skipSubtree();
            } else if ("ele".equals(local)) {
                requireAttributes(Set.of());
                elevation(readScalar());
                diagnostics.warning(
                        "GPX_TRACK_POINT_DATA_IGNORED",
                        Map.of("field", "elevation"),
                        currentRecord);
            } else if ("time".equals(local)) {
                requireAttributes(Set.of());
                time(readScalar());
                diagnostics.warning(
                        "GPX_TRACK_POINT_DATA_IGNORED", Map.of("field", "time"), currentRecord);
            } else {
                diagnostics.warning(
                        "GPX_TRACK_POINT_DATA_IGNORED", Map.of("field", "other"), currentRecord);
                skipSubtree();
            }
        }
    }

    private long trackNumber(String value) {
        String token = value.strip();
        if (token.length() > limits.maximumNumberCharacters()) {
            throw limit("numberCharacters", token.length(), limits.maximumNumberCharacters());
        }
        if (!NON_NEGATIVE_INTEGER.matcher(token).matches()) {
            throw valueFailure("trackNumber", "syntax");
        }
        try {
            return Long.parseLong(token);
        } catch (NumberFormatException failure) {
            throw valueFailure("trackNumber", "range");
        }
    }

    private void finishDocument() throws XMLStreamException {
        while (reader.hasNext()) {
            int event = nextEvent();
            if (event == XMLStreamConstants.END_DOCUMENT) {
                return;
            }
            if (event == XMLStreamConstants.CHARACTERS && reader.isWhiteSpace()) {
                continue;
            }
            if (event == XMLStreamConstants.COMMENT
                    || event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                continue;
            }
            throw xmlFailure("trailingContent", null);
        }
    }

    private int nextChildEvent() throws XMLStreamException {
        while (true) {
            int event = nextEvent();
            if (event == XMLStreamConstants.START_ELEMENT
                    || event == XMLStreamConstants.END_ELEMENT) {
                return event;
            }
            if (event == XMLStreamConstants.CHARACTERS && reader.isWhiteSpace()) {
                continue;
            }
            if (event == XMLStreamConstants.COMMENT
                    || event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                continue;
            }
            throw xmlFailure("syntax", null);
        }
    }

    private String readScalar() throws XMLStreamException {
        StringBuilder value = new StringBuilder();
        while (true) {
            int event = nextEvent();
            if (event == XMLStreamConstants.END_ELEMENT) {
                String result = value.toString();
                chargeOwned(2L * result.length());
                return result;
            }
            if (event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) {
                if ((long) value.length() + reader.getTextLength()
                        > limits.maximumScalarCharacters()) {
                    throw limit(
                            "scalarCharacters",
                            (long) value.length() + reader.getTextLength(),
                            limits.maximumScalarCharacters());
                }
                value.append(reader.getText());
                continue;
            }
            if (event == XMLStreamConstants.COMMENT
                    || event == XMLStreamConstants.PROCESSING_INSTRUCTION) {
                continue;
            }
            throw xmlFailure("cardinality", null);
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

    private int nextEvent() throws XMLStreamException {
        checkCancelled();
        int event = reader.next();
        chargeEvent(event);
        checkCancelled();
        return event;
    }

    private void chargeEvent(int event) {
        if (++events > limits.maximumXmlEvents()) {
            throw limit("xmlEvents", events, limits.maximumXmlEvents());
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
                namespaces = Math.addExact(namespaces, reader.getNamespaceCount());
                if (namespaces > limits.maximumNamespaceDeclarations()) {
                    throw limit(
                            "namespaceDeclarations",
                            namespaces,
                            limits.maximumNamespaceDeclarations());
                }
                for (int index = 0; index < reader.getAttributeCount(); index++) {
                    chargeText(reader.getAttributeValue(index).length());
                }
                for (int index = 0; index < reader.getNamespaceCount(); index++) {
                    String namespace = reader.getNamespaceURI(index);
                    chargeText(namespace == null ? 0 : namespace.length());
                }
            }
            case XMLStreamConstants.END_ELEMENT -> depth--;
            case XMLStreamConstants.CHARACTERS,
                    XMLStreamConstants.CDATA,
                    XMLStreamConstants.COMMENT ->
                    chargeText(reader.getTextLength());
            case XMLStreamConstants.PROCESSING_INSTRUCTION -> {
                chargeText(length(reader.getPITarget()));
                chargeText(length(reader.getPIData()));
            }
            case XMLStreamConstants.DTD -> throw xmlFailure("doctype", null);
            case XMLStreamConstants.ENTITY_REFERENCE -> throw xmlFailure("entity", null);
            default -> {
                // Other ordinary document events need only their structural-event charge.
            }
        }
    }

    private void chargeText(int count) {
        textCharacters = Math.addExact(textCharacters, count);
        if (textCharacters > limits.maximumTextCharacters()) {
            throw limit("textCharacters", textCharacters, limits.maximumTextCharacters());
        }
    }

    private long chargePhysicalFeature() {
        if (++physicalFeatures > limits.maximumPhysicalFeatures()) {
            throw limit("features", physicalFeatures, limits.maximumPhysicalFeatures());
        }
        chargeOwned(8);
        return physicalFeatures;
    }

    private void chargeCoordinate() {
        if (++coordinates > limits.maximumTotalCoordinates()) {
            throw limit("coordinates", coordinates, limits.maximumTotalCoordinates());
        }
        chargeOwned(16);
    }

    private void chargePart() {
        if (++parts > limits.maximumParts()) {
            throw limit("parts", parts, limits.maximumParts());
        }
        chargeOwned(4);
    }

    private void chargeOwned(long count) {
        ownedBytes = Math.addExact(ownedBytes, count);
        if (ownedBytes > limits.maximumOwnedBytes()) {
            throw limit("ownedBytes", ownedBytes, limits.maximumOwnedBytes());
        }
    }

    private void requireAttributes(Set<QName> allowed) {
        for (int index = 0; index < reader.getAttributeCount(); index++) {
            if (!allowed.contains(reader.getAttributeName(index))) {
                throw profileFailure("attribute");
            }
        }
    }

    private double coordinate(
            String field, String token, double minimum, double maximum, boolean maximumInclusive) {
        double value = decimal(field, token);
        if (value < minimum || value > maximum || (!maximumInclusive && value == maximum)) {
            throw valueFailure(field, "range");
        }
        return value == 0 ? 0 : value;
    }

    private double elevation(String value) {
        return decimal("elevation", value);
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

    private String time(String value) {
        String token = value.strip();
        if (!TIME.matcher(token).matches()) {
            throw valueFailure("time", "syntax");
        }
        try {
            return OffsetDateTime.parse(token).toInstant().toString();
        } catch (DateTimeException failure) {
            throw valueFailure("time", "range");
        }
    }

    private String validateText(String field, String value) {
        if (value.isBlank()) {
            throw valueFailure(field, "syntax");
        }
        if (value.length() > limits.maximumScalarCharacters()) {
            throw valueFailure(field, "length");
        }
        return value;
    }

    private int validateEncoding() {
        int offset = 0;
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            offset = 3;
            diagnostics.warning("GPX_UTF8_BOM_IGNORED", Map.of(), 0);
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
        java.util.regex.Matcher version =
                Pattern.compile("version\\s*=\\s*(['\"])([^'\"]+)\\1", Pattern.CASE_INSENSITIVE)
                        .matcher(declaration);
        if (version.find() && !"1.0".equals(version.group(2))) {
            throw encodingFailure("xmlVersion");
        }
        java.util.regex.Matcher encoding =
                Pattern.compile("encoding\\s*=\\s*(['\"])([^'\"]+)\\1", Pattern.CASE_INSENSITIVE)
                        .matcher(declaration);
        if (encoding.find() && !"utf-8".equalsIgnoreCase(encoding.group(2))) {
            throw encodingFailure("declaredEncoding");
        }
    }

    private boolean hasUnsupportedBom() {
        return bytes.length >= 2
                && (((bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xfe)
                        || ((bytes[0] & 0xff) == 0xfe && (bytes[1] & 0xff) == 0xff)
                        || (bytes.length >= 4
                                && (((bytes[0] & 0xff) == 0x00
                                                && (bytes[1] & 0xff) == 0x00
                                                && (bytes[2] & 0xff) == 0xfe
                                                && (bytes[3] & 0xff) == 0xff)
                                        || ((bytes[0] & 0xff) == 0xff
                                                && (bytes[1] & 0xff) == 0xfe
                                                && (bytes[2] & 0xff) == 0x00
                                                && (bytes[3] & 0xff) == 0x00))));
    }

    private XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
        requireProperty(factory, XMLInputFactory.IS_NAMESPACE_AWARE, true);
        requireProperty(factory, XMLInputFactory.IS_COALESCING, false);
        requireProperty(factory, XMLInputFactory.IS_VALIDATING, false);
        requireProperty(factory, XMLInputFactory.SUPPORT_DTD, false);
        requireProperty(factory, XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        requireProperty(factory, XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        requireProperty(factory, XMLConstants.ACCESS_EXTERNAL_DTD, "");
        requireProperty(factory, XMLConstants.USE_CATALOG, false);
        factory.setXMLResolver(
                (publicId, systemId, baseUri, namespace) -> {
                    throw new XMLStreamException("GPX external resolution disabled");
                });
        factory.setXMLReporter(
                (message, errorType, relatedInformation, location) -> {
                    throw new XMLStreamException("GPX parser report rejected");
                });
        return factory;
    }

    private static void requireProperty(XMLInputFactory factory, String name, Object value) {
        try {
            factory.setProperty(name, value);
            if (!value.equals(factory.getProperty(name))) {
                throw new IllegalStateException("Required GPX XML parser property was ineffective");
            }
        } catch (IllegalArgumentException failure) {
            throw new IllegalStateException(
                    "Required GPX XML parser property is unsupported", failure);
        }
    }

    private void closeReader() {
        if (reader == null) {
            return;
        }
        try {
            reader.close();
        } catch (XMLStreamException failure) {
            // The byte snapshot owns no live external resource.
        }
    }

    private void checkCancelled() {
        if (cancellation.isCancellationRequested()) {
            throw diagnostics.failure(
                    "SOURCE_CANCELLED",
                    Map.of("operation", "gpx-open"),
                    currentRecord,
                    "GPX operation was cancelled",
                    null);
        }
    }

    private SourceException limit(String limit, long requested, long maximum) {
        return diagnostics.failure(
                "SOURCE_LIMIT_EXCEEDED",
                pointContext(
                        Map.of(
                                "scope",
                                "gpxOpen",
                                "limit",
                                limit,
                                "requested",
                                Long.toString(requested),
                                "maximum",
                                Long.toString(maximum))),
                currentRecord,
                "GPX opening limit exceeded",
                null);
    }

    private SourceException encodingFailure(String reason) {
        return diagnostics.failure(
                "GPX_ENCODING_INVALID",
                Map.of("reason", reason),
                currentRecord,
                "GPX encoding is outside the supported profile",
                null);
    }

    private SourceException xmlFailure(String reason, Throwable cause) {
        return diagnostics.failure(
                "GPX_XML_INVALID",
                pointContext(Map.of("reason", reason)),
                currentRecord,
                "GPX XML is invalid",
                cause);
    }

    private SourceException profileFailure(String construct) {
        return diagnostics.failure(
                "GPX_PROFILE_UNSUPPORTED",
                pointContext(Map.of("construct", construct)),
                currentRecord,
                "GPX construct is outside the supported profile",
                null);
    }

    private SourceException valueFailure(String field, String reason) {
        return diagnostics.failure(
                "GPX_VALUE_INVALID",
                pointContext(Map.of("field", field, "reason", reason)),
                currentRecord,
                "GPX value is invalid",
                null);
    }

    private Map<String, String> pointContext(Map<String, String> base) {
        if (currentPointIndex < 0) {
            return base;
        }
        LinkedHashMap<String, String> context = new LinkedHashMap<>(base);
        context.put("pointIndex", Integer.toString(currentPointIndex));
        return Map.copyOf(context);
    }

    private static String fieldName(String local) {
        return switch (local) {
            case "ele" -> "elevation";
            case "cmt" -> "comment";
            case "desc" -> "description";
            case "src" -> "source";
            case "sym" -> "symbol";
            default -> local;
        };
    }

    private static String trackFieldName(String local) {
        return switch (local) {
            case "cmt" -> "comment";
            case "desc" -> "description";
            case "src" -> "source";
            case "number" -> "trackNumber";
            default -> local;
        };
    }

    private static int length(String value) {
        return value == null ? 0 : value.length();
    }

    private static boolean validXmlCodePoint(int codePoint) {
        return codePoint == 0x9
                || codePoint == 0xa
                || codePoint == 0xd
                || (codePoint >= 0x20 && codePoint <= 0xd7ff)
                || (codePoint >= 0xe000 && codePoint <= 0xfffd)
                || (codePoint >= 0x10000 && codePoint <= 0x10ffff);
    }

    private static final class PackedCoordinates {
        private double[] ordinates = new double[16];
        private int size;

        void add(double x, double y) {
            int required = Math.addExact(size, 2);
            if (required > ordinates.length) {
                ordinates =
                        java.util.Arrays.copyOf(
                                ordinates, Math.max(required, Math.multiplyExact(size, 2)));
            }
            ordinates[size] = x;
            ordinates[size + 1] = y;
            size = required;
        }

        int size() {
            return size / 2;
        }

        double[] toArray() {
            return java.util.Arrays.copyOf(ordinates, size);
        }
    }

    record Opening(List<FeatureRecord> records, DiagnosticReport diagnostics) {}
}
