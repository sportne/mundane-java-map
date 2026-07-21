package io.github.mundanej.map.api;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * An immutable, toolkit-neutral picture of one logical-screen vector viewport.
 *
 * <p>The value contains detached screen geometry and portrayal only. It retains no live map view,
 * source, cursor, registry, attribute map, AWT value, path, or callback.
 */
@SuppressWarnings("deprecation")
public final class VectorExportSnapshot {
    private final int widthPixels;
    private final int heightPixels;
    private final Rgba background;
    private final ViewFrame viewFrame;
    private final int layerCount;
    private final List<Primitive> primitives;
    private final List<Label> labels;

    private VectorExportSnapshot(
            int widthPixels,
            int heightPixels,
            Rgba background,
            ViewFrame viewFrame,
            int layerCount,
            List<Primitive> primitives,
            List<Label> labels) {
        this.widthPixels = widthPixels;
        this.heightPixels = heightPixels;
        this.background = background;
        this.viewFrame = viewFrame;
        this.layerCount = layerCount;
        this.primitives = primitives;
        this.labels = labels;
    }

    /**
     * Creates a bounded snapshot using default limits and no cancellation.
     *
     * @param widthPixels positive logical page width
     * @param heightPixels positive logical page height
     * @param background page background color
     * @param viewFrame projected-map-to-screen similarity frame
     * @param layerCount non-negative source layer count, including empty layers
     * @param primitives vector primitives in ascending paint order
     * @param labels placed labels in ascending ordinary paint order
     * @return immutable detached snapshot
     */
    public static VectorExportSnapshot of(
            int widthPixels,
            int heightPixels,
            Rgba background,
            ViewFrame viewFrame,
            int layerCount,
            List<Primitive> primitives,
            List<Label> labels) {
        return of(
                widthPixels,
                heightPixels,
                background,
                viewFrame,
                layerCount,
                primitives,
                labels,
                VectorExportSnapshotLimits.defaults(),
                CancellationToken.none());
    }

    /**
     * Creates a bounded snapshot using explicit limits and no cancellation.
     *
     * @param widthPixels positive logical page width
     * @param heightPixels positive logical page height
     * @param background page background color
     * @param viewFrame projected-map-to-screen similarity frame
     * @param layerCount non-negative source layer count, including empty layers
     * @param primitives vector primitives in ascending paint order
     * @param labels placed labels in ascending ordinary paint order
     * @param limits detached-snapshot limits
     * @return immutable detached snapshot
     */
    public static VectorExportSnapshot of(
            int widthPixels,
            int heightPixels,
            Rgba background,
            ViewFrame viewFrame,
            int layerCount,
            List<Primitive> primitives,
            List<Label> labels,
            VectorExportSnapshotLimits limits) {
        return of(
                widthPixels,
                heightPixels,
                background,
                viewFrame,
                layerCount,
                primitives,
                labels,
                limits,
                CancellationToken.none());
    }

    /**
     * Creates a bounded all-or-nothing snapshot using explicit limits and cancellation.
     *
     * @param widthPixels positive logical page width
     * @param heightPixels positive logical page height
     * @param background page background color
     * @param viewFrame projected-map-to-screen similarity frame
     * @param layerCount non-negative source layer count, including empty layers
     * @param primitives vector primitives in ascending paint order
     * @param labels placed labels in ascending ordinary paint order
     * @param limits detached-snapshot limits
     * @param cancellation cancellation signal polled before publication
     * @return immutable detached snapshot
     */
    public static VectorExportSnapshot of(
            int widthPixels,
            int heightPixels,
            Rgba background,
            ViewFrame viewFrame,
            int layerCount,
            List<Primitive> primitives,
            List<Label> labels,
            VectorExportSnapshotLimits limits,
            CancellationToken cancellation) {
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(viewFrame, "viewFrame");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(cancellation);
        requirePageAxis(widthPixels, limits);
        requirePageAxis(heightPixels, limits);
        if (layerCount < 0) {
            throw new IllegalArgumentException("layerCount must be non-negative");
        }
        checkLimit("layers", limits.maximumLayers(), layerCount);

        List<Primitive> primitiveCopy =
                List.copyOf(Objects.requireNonNull(primitives, "primitives"));
        List<Label> labelCopy = List.copyOf(Objects.requireNonNull(labels, "labels"));
        checkLimit("features", limits.maximumFeatures(), primitiveCopy.size());
        checkLimit("labels", limits.maximumLabels(), labelCopy.size());

        long coordinates = 0;
        long symbolNodes = 0;
        long labelCodePoints = 0;
        long ownedBytes = 128;
        int previousLayer = -1;
        int previousFeature = -1;
        for (Primitive primitive : primitiveCopy) {
            checkCancelled(cancellation);
            Objects.requireNonNull(primitive, "primitive");
            if (primitive.layerIndex() >= layerCount) {
                throw new IllegalArgumentException("primitive layerIndex must be below layerCount");
            }
            if (primitive.layerIndex() < previousLayer
                    || (primitive.layerIndex() == previousLayer
                            && primitive.featureIndex() <= previousFeature)) {
                throw new IllegalArgumentException(
                        "primitives must have unique ascending layer and feature indices");
            }
            previousLayer = primitive.layerIndex();
            previousFeature = primitive.featureIndex();
            requireRole(primitive);
            coordinates = add(coordinates, coordinateCount(primitive.screenGeometry()));
            checkLimit("coordinates", limits.maximumCoordinates(), coordinates);
            SymbolInventory inventory = validateSymbolTree(primitive);
            symbolNodes = add(symbolNodes, inventory.nodes());
            checkLimit("symbolNodes", limits.maximumSymbolNodes(), symbolNodes);
            checkLimit("compositeDepth", limits.maximumCompositeDepth(), inventory.depth());
            ownedBytes = add(ownedBytes, add(72, geometryBytes(primitive.screenGeometry())));
            ownedBytes = add(ownedBytes, inventory.bytes());
            checkLimit("ownedBytes", limits.maximumOwnedBytes(), ownedBytes);
        }

        int previousOrdinal = -1;
        for (int index = 0; index < labelCopy.size(); index++) {
            checkCancelled(cancellation);
            Label label = Objects.requireNonNull(labelCopy.get(index), "label");
            if (label.ordinaryPaintOrdinal() <= previousOrdinal) {
                throw new IllegalArgumentException(
                        "labels must have unique ascending ordinaryPaintOrdinal values");
            }
            previousOrdinal = label.ordinaryPaintOrdinal();
            validateLabel(label, index);
            labelCodePoints =
                    add(labelCodePoints, label.text().codePointCount(0, label.text().length()));
            checkLimit("labelCodePoints", limits.maximumLabelCodePoints(), labelCodePoints);
            ownedBytes = add(ownedBytes, add(72, multiply(label.text().length(), 2)));
            checkLimit("ownedBytes", limits.maximumOwnedBytes(), ownedBytes);
        }
        checkCancelled(cancellation);
        return new VectorExportSnapshot(
                widthPixels,
                heightPixels,
                background,
                viewFrame,
                layerCount,
                primitiveCopy,
                labelCopy);
    }

    /**
     * Returns the logical page width.
     *
     * @return positive width in logical screen pixels
     */
    public int widthPixels() {
        return widthPixels;
    }

    /**
     * Returns the logical page height.
     *
     * @return positive height in logical screen pixels
     */
    public int heightPixels() {
        return heightPixels;
    }

    /**
     * Returns the page background color.
     *
     * @return immutable background color
     */
    public Rgba background() {
        return background;
    }

    /**
     * Returns the projected-map-to-screen frame.
     *
     * @return immutable similarity frame
     */
    public ViewFrame viewFrame() {
        return viewFrame;
    }

    /**
     * Returns the number of source layers represented, including empty layers.
     *
     * @return non-negative layer count
     */
    public int layerCount() {
        return layerCount;
    }

    /**
     * Returns immutable primitives in ordinary paint order.
     *
     * @return unmodifiable ordered primitive list
     */
    public List<Primitive> primitives() {
        return List.copyOf(primitives);
    }

    /**
     * Returns immutable placed labels in ordinary paint order.
     *
     * @return unmodifiable ordered label list
     */
    public List<Label> labels() {
        return List.copyOf(labels);
    }

    private static void requirePageAxis(int value, VectorExportSnapshotLimits limits) {
        if (value <= 0) {
            throw new IllegalArgumentException("page dimensions must be positive");
        }
        checkLimit("pageAxis", limits.maximumPageAxis(), value);
    }

    private static void requireRole(Primitive primitive) {
        SymbolRole expected;
        if (primitive.screenGeometry() instanceof PointGeometry
                || primitive.screenGeometry() instanceof MultiPointGeometry) {
            expected = SymbolRole.MARKER;
        } else if (primitive.screenGeometry() instanceof LineStringGeometry
                || primitive.screenGeometry() instanceof MultiLineStringGeometry) {
            expected = SymbolRole.LINE;
        } else if (primitive.screenGeometry() instanceof PolygonGeometry
                || primitive.screenGeometry() instanceof MultiPolygonGeometry) {
            expected = SymbolRole.FILL;
        } else {
            throw new IllegalArgumentException("Unsupported vector-export geometry");
        }
        if (primitive.symbol().role() != expected) {
            throw new IllegalArgumentException("primitive symbol role does not match geometry");
        }
    }

    private static SymbolInventory validateSymbolTree(Primitive primitive) {
        ArrayDeque<SymbolFrame> pending = new ArrayDeque<>();
        pending.push(new SymbolFrame(primitive.symbol(), primitive.symbol().role(), 1));
        long nodes = 0;
        long maximumDepth = 0;
        long bytes = 0;
        while (!pending.isEmpty()) {
            SymbolFrame frame = pending.pop();
            Symbol symbol = frame.symbol();
            int ordinal = Math.toIntExact(nodes);
            nodes = add(nodes, 1);
            maximumDepth = Math.max(maximumDepth, frame.depth());
            bytes = add(bytes, 64);
            if (symbol.role() != frame.expectedRole()) {
                throw unsupported(primitive, ordinal, "wrongDescendant");
            }
            if (symbol.getClass() == VectorMarkerSymbol.class) {
                VectorMarkerSymbol marker = (VectorMarkerSymbol) symbol;
                bytes =
                        add(
                                bytes,
                                add(
                                        32,
                                        add(
                                                marker.path().commandCount(),
                                                multiply(marker.path().ordinateCount(), 8))));
                continue;
            }
            if (symbol.getClass() == SolidLineSymbol.class) {
                SolidLineSymbol line = (SolidLineSymbol) symbol;
                line.endMarker()
                        .ifPresent(
                                marker ->
                                        pending.push(
                                                new SymbolFrame(
                                                        marker,
                                                        SymbolRole.MARKER,
                                                        frame.depth() + 1)));
                line.startMarker()
                        .ifPresent(
                                marker ->
                                        pending.push(
                                                new SymbolFrame(
                                                        marker,
                                                        SymbolRole.MARKER,
                                                        frame.depth() + 1)));
                continue;
            }
            if (symbol.getClass() == SolidFillSymbol.class) {
                SolidFillSymbol fill = (SolidFillSymbol) symbol;
                fill.outline()
                        .ifPresent(
                                outline ->
                                        pending.push(
                                                new SymbolFrame(
                                                        outline,
                                                        SymbolRole.LINE,
                                                        frame.depth() + 1)));
                continue;
            }
            if (symbol.getClass() == HatchFillSymbol.class) {
                HatchFillSymbol fill = (HatchFillSymbol) symbol;
                fill.outline()
                        .ifPresent(
                                outline ->
                                        pending.push(
                                                new SymbolFrame(
                                                        outline,
                                                        SymbolRole.LINE,
                                                        frame.depth() + 1)));
                continue;
            }
            if (symbol.getClass() == CompositeSymbol.class) {
                List<Symbol> children = ((CompositeSymbol) symbol).children();
                for (int index = children.size() - 1; index >= 0; index--) {
                    pending.push(
                            new SymbolFrame(
                                    children.get(index), frame.expectedRole(), frame.depth() + 1));
                }
                continue;
            }
            String kind =
                    symbol instanceof RasterIconSymbol
                            ? "rasterIcon"
                            : symbol instanceof FeatureStyle ? "legacy" : "custom";
            throw unsupported(primitive, ordinal, kind);
        }
        return new SymbolInventory(nodes, maximumDepth, bytes);
    }

    private static VectorExportSnapshotException unsupported(
            Primitive primitive, int ordinal, String kind) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("layerIndex", Integer.toString(primitive.layerIndex()));
        context.put("featureIndex", Integer.toString(primitive.featureIndex()));
        context.put("symbolOrdinal", Integer.toString(ordinal));
        context.put("kind", kind);
        return failure(
                "VECTOR_EXPORT_SYMBOL_UNSUPPORTED",
                context,
                "The vector-export snapshot contains an unsupported symbol");
    }

    private static long coordinateCount(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return 1;
        }
        if (geometry instanceof MultiPointGeometry points) {
            return points.coordinates().size();
        }
        if (geometry instanceof LineStringGeometry line) {
            return line.coordinates().size();
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return lines.coordinates().size();
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return polygons.coordinates().size();
        }
        PolygonGeometry polygon = (PolygonGeometry) geometry;
        long result = polygon.exterior().size();
        for (CoordinateSequence hole : polygon.holes()) {
            result = add(result, hole.size());
        }
        return result;
    }

    private static Geometry normalizeGeometry(Geometry geometry) {
        if (geometry instanceof PointGeometry point) {
            return new PointGeometry(normalizeCoordinate(point.coordinate()));
        }
        if (geometry instanceof MultiPointGeometry points) {
            return new MultiPointGeometry(normalizeSequence(points.coordinates()));
        }
        if (geometry instanceof LineStringGeometry line) {
            return new LineStringGeometry(normalizeSequence(line.coordinates()));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return MultiLineStringGeometry.of(
                    normalizeSequence(lines.coordinates()), lines.partOffsets());
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return MultiPolygonGeometry.of(
                    normalizeSequence(polygons.coordinates()),
                    polygons.ringOffsets(),
                    polygons.polygonRingOffsets());
        }
        if (geometry instanceof PolygonGeometry polygon) {
            List<CoordinateSequence> holes = new ArrayList<>(polygon.holes().size());
            for (CoordinateSequence hole : polygon.holes()) {
                holes.add(normalizeSequence(hole));
            }
            return new PolygonGeometry(normalizeSequence(polygon.exterior()), holes);
        }
        return geometry;
    }

    private static CoordinateSequence normalizeSequence(CoordinateSequence sequence) {
        double[] ordinates = sequence.toArray();
        for (int index = 0; index < ordinates.length; index++) {
            ordinates[index] = canonicalZero(ordinates[index]);
        }
        return CoordinateSequence.of(ordinates);
    }

    private static Coordinate normalizeCoordinate(Coordinate coordinate) {
        return new Coordinate(canonicalZero(coordinate.x()), canonicalZero(coordinate.y()));
    }

    private static double canonicalZero(double value) {
        return value == 0.0 ? 0.0 : value;
    }

    private static long geometryBytes(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return 80;
        }
        if (geometry instanceof MultiPointGeometry points) {
            return add(96, multiply(points.coordinates().size(), 16));
        }
        if (geometry instanceof LineStringGeometry line) {
            return add(96, multiply(line.coordinates().size(), 16));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return add(
                    104,
                    add(
                            multiply(lines.coordinates().size(), 16),
                            multiply(lines.partCount() + 1L, 4)));
        }
        if (geometry instanceof MultiPolygonGeometry polygons) {
            return add(
                    112,
                    add(
                            multiply(polygons.coordinates().size(), 16),
                            add(
                                    multiply(polygons.ringCount() + 1L, 4),
                                    multiply(polygons.polygonCount() + 1L, 4))));
        }
        PolygonGeometry polygon = (PolygonGeometry) geometry;
        long result = add(96, add(multiply(polygon.exterior().size(), 16), 4));
        for (CoordinateSequence hole : polygon.holes()) {
            result = add(result, add(44, multiply(hole.size(), 16)));
        }
        return result;
    }

    private static void validateLabel(Label label, int index) {
        String text = label.text();
        for (int offset = 0; offset < text.length(); ) {
            int codePoint = text.codePointAt(offset);
            if (!isXmlScalar(codePoint)
                    || codePoint == '\r'
                    || codePoint == '\n'
                    || codePoint == 0x2028
                    || codePoint == 0x2029) {
                LinkedHashMap<String, String> context = new LinkedHashMap<>();
                context.put("field", "labelText");
                context.put("reason", "xmlScalar");
                context.put("labelIndex", Integer.toString(index));
                context.put("ordinaryPaintOrdinal", Integer.toString(label.ordinaryPaintOrdinal()));
                throw failure(
                        "VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID",
                        context,
                        "A vector-export label contains an unsupported XML scalar");
            }
            offset += Character.charCount(codePoint);
        }
    }

    private static boolean isXmlScalar(int codePoint) {
        return codePoint == 0x9
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }

    private static void checkCancelled(CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw failure(
                    "VECTOR_EXPORT_SNAPSHOT_CANCELLED",
                    Map.of(),
                    "Vector-export snapshot construction was cancelled");
        }
    }

    private static void checkLimit(String name, long maximum, long requested) {
        if (requested > maximum) {
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("limit", name);
            context.put("maximum", Long.toString(maximum));
            context.put("requested", Long.toString(requested));
            throw failure(
                    "VECTOR_EXPORT_SNAPSHOT_LIMIT_EXCEEDED",
                    context,
                    "A vector-export snapshot limit was exceeded");
        }
    }

    private static VectorExportSnapshotException failure(
            String code, Map<String, String> context, String message) {
        return new VectorExportSnapshotException(
                message, new VectorExportSnapshotProblem(code, context));
    }

    private static long add(long left, long right) {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    private static long multiply(long left, long right) {
        try {
            return Math.multiplyExact(left, right);
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof VectorExportSnapshot snapshot
                && widthPixels == snapshot.widthPixels
                && heightPixels == snapshot.heightPixels
                && layerCount == snapshot.layerCount
                && background.equals(snapshot.background)
                && viewFrame.equals(snapshot.viewFrame)
                && primitives.equals(snapshot.primitives)
                && labels.equals(snapshot.labels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                widthPixels, heightPixels, background, viewFrame, layerCount, primitives, labels);
    }

    /**
     * Projected-map-unit to logical-screen similarity frame retained by the snapshot.
     *
     * @param screenPixelsPerMapUnit positive uniform scale
     * @param mapXAxisScreenBearingDegrees clockwise screen bearing of projected positive x
     * @param mapOriginScreen logical-screen position of projected map origin
     */
    public record ViewFrame(
            double screenPixelsPerMapUnit,
            double mapXAxisScreenBearingDegrees,
            Coordinate mapOriginScreen) {
        /** Validates and canonicalizes the frame. */
        public ViewFrame {
            Objects.requireNonNull(mapOriginScreen, "mapOriginScreen");
            if (!Double.isFinite(screenPixelsPerMapUnit) || screenPixelsPerMapUnit <= 0.0) {
                throw new IllegalArgumentException(
                        "screenPixelsPerMapUnit must be finite and positive");
            }
            if (!Double.isFinite(mapXAxisScreenBearingDegrees)) {
                throw new IllegalArgumentException("mapXAxisScreenBearingDegrees must be finite");
            }
            double normalized = mapXAxisScreenBearingDegrees % 360.0;
            if (normalized < 0.0) {
                normalized += 360.0;
            }
            mapXAxisScreenBearingDegrees = normalized == 0.0 ? 0.0 : normalized;
            mapOriginScreen = normalizeCoordinate(mapOriginScreen);
        }
    }

    /**
     * One detached geometry and its role-matched supported vector portrayal.
     *
     * @param layerIndex non-negative source layer ordinal
     * @param featureIndex non-negative feature ordinal within the layer
     * @param screenGeometry immutable logical-screen geometry
     * @param symbol immutable role-matched supported symbol
     */
    public record Primitive(
            int layerIndex, int featureIndex, Geometry screenGeometry, Symbol symbol) {
        /** Validates non-negative ordinals and required values. */
        public Primitive {
            if (layerIndex < 0 || featureIndex < 0) {
                throw new IllegalArgumentException("primitive indices must be non-negative");
            }
            screenGeometry =
                    normalizeGeometry(Objects.requireNonNull(screenGeometry, "screenGeometry"));
            Objects.requireNonNull(symbol, "symbol");
        }
    }

    /**
     * One detached point label whose screen position and metrics were already resolved.
     *
     * @param text retained label text
     * @param style immutable logical label style
     * @param baselineX finite logical-screen baseline x
     * @param baselineY finite logical-screen baseline y
     * @param measuredAdvance finite non-negative measured advance
     * @param ordinaryPaintOrdinal non-negative unique label paint ordinal
     */
    public record Label(
            String text,
            LabelTextStyle style,
            double baselineX,
            double baselineY,
            double measuredAdvance,
            int ordinaryPaintOrdinal) {
        /** Validates immutable label values and canonicalizes signed zero. */
        public Label {
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(style, "style");
            if (!Double.isFinite(baselineX)
                    || !Double.isFinite(baselineY)
                    || !Double.isFinite(measuredAdvance)
                    || measuredAdvance < 0.0) {
                throw new IllegalArgumentException("label metrics must be finite and non-negative");
            }
            if (ordinaryPaintOrdinal < 0) {
                throw new IllegalArgumentException("ordinaryPaintOrdinal must be non-negative");
            }
            baselineX = baselineX == 0.0 ? 0.0 : baselineX;
            baselineY = baselineY == 0.0 ? 0.0 : baselineY;
            measuredAdvance = measuredAdvance == 0.0 ? 0.0 : measuredAdvance;
        }
    }

    private record SymbolInventory(long nodes, long depth, long bytes) {}

    private record SymbolFrame(Symbol symbol, SymbolRole expectedRole, int depth) {}
}
