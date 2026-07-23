package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.api.VectorExportSnapshotLimits;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.geojson.GeoJsonFiles;
import io.github.mundanej.map.io.geojson.GeoJsonOpenOptions;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapViewDatelineVectorTest {
    private static final HorizontalWrap WRAP = HorizontalWrap.webMercator();

    @Test
    void geographicLinePolygonAndHoleRenderOnTheShortSeamAdjacentPath() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    ListSource source =
                            new ListSource(
                                    List.of(
                                            record(
                                                    "line",
                                                    new LineStringGeometry(
                                                            sequence(170.0, -15.0, -170.0, 15.0))),
                                            record(
                                                    "polygon",
                                                    new PolygonGeometry(
                                                            sequence(
                                                                    170.0, -12.0, -170.0, -12.0,
                                                                    -170.0, 12.0, 170.0, 12.0,
                                                                    170.0, -12.0),
                                                            List.of(
                                                                    sequence(
                                                                            173.0, -4.0, 178.0,
                                                                            -4.0, 178.0, 4.0, 173.0,
                                                                            4.0, 173.0, -4.0))))));
                    MapView view = wrappedView(source, 400, 240);

                    BufferedImage image = paint(view, 400, 240);
                    assertTrue(
                            hasGreenNear(image, 135, 75, 265, 165),
                            () -> view.sourceReports().toString());
                    assertTrue(hasColorNear(image, new Color(30, 70, 190), 180, 80, 220, 160));
                    assertEquals(2, view.hitTest(200.0, 70.0, 35.0).size());
                    assertTrue(view.sourceReports().isEmpty());
                    view.close();
                    source.close();
                });
    }

    @Test
    void repeatedVectorExportUsesVisibleFragmentsAndProjectedSourcesStayLiteral() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    ListSource geographic =
                            new ListSource(
                                    List.of(
                                            record(
                                                    "line",
                                                    new LineStringGeometry(
                                                            sequence(170.0, 0.0, -170.0, 0.0)))));
                    MapView wrapped = wrappedView(geographic, 400, 160);
                    VectorExportSnapshot first = wrapped.captureVectorExportSnapshot();
                    VectorExportSnapshot second = wrapped.captureVectorExportSnapshot();

                    assertEquals(first, second);
                    assertEquals(2, first.primitives().size());
                    for (VectorExportSnapshot.Primitive primitive : first.primitives()) {
                        LineStringGeometry line =
                                assertInstanceOf(
                                        LineStringGeometry.class, primitive.screenGeometry());
                        assertTrue(line.envelope().minX() >= 0.0);
                        assertTrue(line.envelope().maxX() <= 400.0);
                    }
                    wrapped.close();
                    geographic.close();

                    ListSource projected =
                            new ListSource(
                                    "projected",
                                    CrsDefinitions.EPSG_3857,
                                    List.of(
                                            record(
                                                    "literal",
                                                    new LineStringGeometry(
                                                            sequence(
                                                                    -WebMercatorProjection
                                                                                    .WORLD_LIMIT
                                                                            + 1_000_000.0,
                                                                    0.0,
                                                                    WebMercatorProjection
                                                                                    .WORLD_LIMIT
                                                                            - 1_000_000.0,
                                                                    0.0)))));
                    MapView literal = wrappedView(projected, 400, 160);
                    VectorExportSnapshot literalExport = literal.captureVectorExportSnapshot();
                    assertFalse(literalExport.primitives().isEmpty());
                    assertEquals(2, literalExport.primitives().size());
                    literalExport
                            .primitives()
                            .forEach(
                                    primitive ->
                                            assertInstanceOf(
                                                    LineStringGeometry.class,
                                                    primitive.screenGeometry()));
                    literal.close();
                    projected.close();
                });
    }

    @Test
    void wrappedVectorExportAppliesAggregateSnapshotLimitsWithoutReturningPartialOutput()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    ListSource source =
                            new ListSource(
                                    List.of(
                                            record(
                                                    "line",
                                                    new LineStringGeometry(
                                                            sequence(170.0, 0.0, -170.0, 0.0)))));
                    MapView view = wrappedView(source, 400, 160);

                    VectorExportSnapshotException failure =
                            assertThrows(
                                    VectorExportSnapshotException.class,
                                    () ->
                                            view.captureVectorExportSnapshot(
                                                    VectorExportSnapshotLimits.defaults()
                                                            .withMaximumFeatures(1),
                                                    CancellationToken.none()));

                    assertEquals("VECTOR_EXPORT_SNAPSHOT_LIMIT_EXCEEDED", failure.problem().code());
                    assertEquals("features", failure.problem().context().get("limit"));
                    assertEquals(2, view.captureVectorExportSnapshot().primitives().size());
                    view.close();
                    source.close();
                });
    }

    @Test
    void geoJsonRemainsLiteralByDefaultAndWrapIsAnExplicitPresentationChoice() throws Exception {
        byte[] bytes =
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":"route","properties":{},
                   "geometry":{"type":"LineString","coordinates":[[170,0],[-170,0]]}},
                  {"type":"Feature","id":"offscreen-hostile","properties":{},
                   "geometry":{"type":"Polygon","coordinates":[[
                     [170,50],[-170,50],[-170,54],[174,54],[174,66],
                     [-170,66],[-170,70],[170,70],[170,50]]]}}
                ]}
                """
                        .getBytes(StandardCharsets.UTF_8);
        FeatureSource literalSource =
                GeoJsonFiles.open(
                        bytes,
                        new SourceIdentity("geojson-literal", "GeoJSON literal"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        FeatureSource wrappedSource =
                GeoJsonFiles.open(
                        bytes,
                        new SourceIdentity("geojson-wrapped", "GeoJSON wrapped"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        MapView literal = ordinaryView(literalSource, 400, 160);
                        VectorExportSnapshot literalSnapshot =
                                literal.captureVectorExportSnapshot();
                        assertEquals(1, literalSnapshot.primitives().size());
                        assertInstanceOf(
                                LineStringGeometry.class,
                                literalSnapshot.primitives().getFirst().screenGeometry());

                        MapView wrapped = narrowSeamView(wrappedSource, 400, 160);
                        VectorExportSnapshot wrappedSnapshot =
                                wrapped.captureVectorExportSnapshot();
                        assertEquals(2, wrappedSnapshot.primitives().size());
                        wrappedSnapshot
                                .primitives()
                                .forEach(
                                        primitive ->
                                                assertInstanceOf(
                                                        LineStringGeometry.class,
                                                        primitive.screenGeometry()));
                        assertTrue(wrapped.sourceReports().isEmpty());
                        literal.close();
                        wrapped.close();
                    });
        } finally {
            literalSource.close();
            wrappedSource.close();
        }
    }

    @Test
    void shapefilePolylineUsesTheSameExplicitGeographicSeamPath(@TempDir Path temporary)
            throws Exception {
        Path path = temporary.resolve("route.shp");
        Files.write(path, polylineShapefile(170.0, 0.0, -170.0, 0.0));
        FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity("shape-route", "Shape route"),
                        path,
                        ShapefileOpenOptions.defaults().withCrsOverride(CrsDefinitions.EPSG_4326));
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        MapView view = narrowSeamView(source, 400, 160);
                        VectorExportSnapshot snapshot = view.captureVectorExportSnapshot();
                        assertEquals(2, snapshot.primitives().size());
                        assertTrue(view.sourceReports().isEmpty());
                        view.close();
                    });
        } finally {
            source.close();
        }
    }

    @Test
    void seamEdgesDoNotInventPolygonOutlinesOrLineEndpointMarkers() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    var endpoint =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.ARROW, Rgba.rgb(210, 35, 45), 14.0, 1.0);
                    var endpointLine =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(30, 70, 190),
                                            new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL)),
                                    Optional.of(endpoint),
                                    Optional.of(endpoint),
                                    1.0);
                    var compositeLine =
                            CompositeSymbol.of(
                                    List.of(
                                            SolidLineSymbol.of(
                                                    new SymbolStroke(
                                                            Rgba.rgb(220, 225, 235),
                                                            new SymbolLength(
                                                                    8.0, SymbolUnit.SCREEN_PIXEL)),
                                                    1.0),
                                            endpointLine),
                                    1.0);
                    var fill =
                            SolidFillSymbol.of(
                                    Rgba.rgb(45, 125, 80), Optional.of(endpointLine), 0.85);
                    ListSource source =
                            new ListSource(
                                    List.of(
                                            record(
                                                    "line",
                                                    new LineStringGeometry(
                                                            sequence(170.0, 8.0, -170.0, 8.0))),
                                            record(
                                                    "polygon",
                                                    new PolygonGeometry(
                                                            sequence(
                                                                    170.0, -15.0, -170.0, -15.0,
                                                                    -170.0, 5.0, 170.0, 5.0, 170.0,
                                                                    -15.0)))));
                    MapView view = wrappedView(source, 400, 240, compositeLine, fill);
                    BufferedImage image = paint(view, 400, 240);

                    assertTrue(hasColorNear(image, new Color(210, 35, 45), 95, 30, 125, 65));
                    assertTrue(hasColorNear(image, new Color(210, 35, 45), 275, 30, 305, 65));
                    assertFalse(hasColorNear(image, new Color(210, 35, 45), 190, 30, 210, 65));
                    assertTrue(hasGreenNear(image, 195, 125, 205, 145));
                    assertFalse(hasColorNear(image, new Color(30, 70, 190), 195, 125, 205, 145));
                    view.close();
                    source.close();
                });
    }

    @Test
    void extractedPolygonOutlineRetainsParentOpacityAndNeverUsesRingEndpointMarkers()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    Symbol endpoint =
                            BuiltInMarkers.filledScreen(
                                    BuiltInMarker.ARROW, Rgba.rgb(230, 30, 40), 16.0, 1.0);
                    SolidLineSymbol outline =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(230, 30, 40),
                                            new SymbolLength(5.0, SymbolUnit.SCREEN_PIXEL)),
                                    Optional.of(endpoint),
                                    Optional.of(endpoint),
                                    1.0);
                    SolidFillSymbol fill =
                            SolidFillSymbol.of(Rgba.TRANSPARENT, Optional.of(outline), 0.25);
                    ListSource source =
                            new ListSource(
                                    List.of(
                                            record(
                                                    "polygon",
                                                    new PolygonGeometry(
                                                            sequence(
                                                                    170.0, -10.0, -170.0, -10.0,
                                                                    -170.0, 10.0, 170.0, 10.0,
                                                                    170.0, -10.0)))));
                    MapView view = wrappedView(source, 400, 240, outline, fill);
                    VectorExportSnapshot snapshot = view.captureVectorExportSnapshot();
                    List<Symbol> boundarySymbols =
                            snapshot.primitives().stream()
                                    .map(VectorExportSnapshot.Primitive::symbol)
                                    .filter(symbol -> symbol.role() == SymbolRole.LINE)
                                    .toList();

                    assertFalse(boundarySymbols.isEmpty());
                    boundarySymbols.forEach(
                            symbol -> {
                                assertEquals(0.25, effectiveLineOpacity(symbol));
                                assertFalse(hasEndpointMarker(symbol));
                            });

                    BufferedImage image = paint(view, 400, 240);
                    Coordinate map =
                            new WebMercatorProjection().project(new Coordinate(170.0, 0.0));
                    Coordinate screen = view.mapToScreen(map).orElseThrow();
                    Color painted = mostRedNear(image, (int) screen.x(), (int) screen.y(), 3);
                    int redExcess = painted.getRed() - painted.getGreen();
                    assertTrue(
                            redExcess >= 35 && redExcess <= 85 && painted.getGreen() >= 140,
                            "outline blend was " + painted);
                    view.close();
                    source.close();
                });
    }

    @Test
    void aggregateFragmentLimitFailsTheWrappedRecordAtomically() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    FeatureQueryLimits oneFeature =
                            new FeatureQueryLimits(10, 1, 100, 100, 10_000, 100_000, 10);
                    ListSource source =
                            new ListSource(
                                    "fragment-limit",
                                    CrsDefinitions.EPSG_4326,
                                    List.of(
                                            record(
                                                    "line",
                                                    new LineStringGeometry(
                                                            sequence(170.0, 0.0, -170.0, 0.0)))),
                                    oneFeature);
                    MapView view = wrappedView(source, 400, 160);
                    BufferedImage image = paint(view, 400, 160);

                    assertFalse(hasColorNear(image, new Color(30, 70, 190), 0, 0, 400, 160));
                    var terminal = view.sourceReports().get("global").entries().getLast();
                    assertEquals("SOURCE_LIMIT_EXCEEDED", terminal.code());
                    assertEquals("worldWrap", terminal.context().get("scope"));
                    assertEquals("features", terminal.context().get("limit"));
                    view.close();
                    source.close();
                });
    }

    private static MapView wrappedView(FeatureSource source, int width, int height) {
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(30, 70, 190),
                                new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        var fill = SolidFillSymbol.of(Rgba.rgb(45, 125, 80), Optional.of(line), 0.85);
        return wrappedView(source, width, height, line, fill);
    }

    private static MapView narrowSeamView(FeatureSource source, int width, int height) {
        MapView view = wrappedView(source, width, height);
        double center = new WebMercatorProjection().project(new Coordinate(179.0, 0.0)).x();
        view.setViewport(new MapViewport(width, height, center, 0.0, 1_000.0));
        return view;
    }

    private static MapView wrappedView(
            FeatureSource source,
            int width,
            int height,
            io.github.mundanej.map.api.Symbol line,
            io.github.mundanej.map.api.Symbol fill) {
        MapView view = ordinaryView(source, width, height, line, fill);
        MapLayerBinding binding = view.layerBindings().getFirst();
        view.setLayerBindings(List.of());
        binding.setHorizontalWrapMode(HorizontalWrapMode.REPEAT_X);
        view.setHorizontalWrap(WRAP);
        view.setLayerBindings(List.of(binding));
        return view;
    }

    private static MapView ordinaryView(FeatureSource source, int width, int height) {
        var line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(30, 70, 190),
                                new SymbolLength(4.0, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        var fill = SolidFillSymbol.of(Rgba.rgb(45, 125, 80), Optional.of(line), 0.85);
        return ordinaryView(source, width, height, line, fill);
    }

    private static MapView ordinaryView(
            FeatureSource source,
            int width,
            int height,
            io.github.mundanej.map.api.Symbol line,
            io.github.mundanej.map.api.Symbol fill) {
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(30, 70, 190), 8.0, 1.0);
        MapLayerBinding binding =
                MapLayerBinding.borrowedFeature(
                        "global", "Global", source, FeaturePortrayal.fixed(marker, line, fill));
        MapView view = TestMapViews.identity();
        view.setSize(width, height);
        view.setViewport(
                new MapViewport(width, height, WebMercatorProjection.WORLD_LIMIT, 0.0, 12_000.0));
        view.setLayerBindings(List.of(binding));
        return view;
    }

    private static FeatureRecord record(String id, io.github.mundanej.map.api.Geometry geometry) {
        return new FeatureRecord(id, id, geometry, Map.of());
    }

    private static CoordinateSequence sequence(double... ordinates) {
        return CoordinateSequence.of(ordinates);
    }

    private static byte[] polylineShapefile(double... xy) {
        byte[] record = polylineRecord(xy);
        int total = 108 + record.length;
        ByteBuffer output = ByteBuffer.allocate(total);
        output.order(ByteOrder.BIG_ENDIAN).putInt(9994);
        for (int index = 0; index < 5; index++) {
            output.putInt(0);
        }
        output.putInt(total / 2);
        output.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(1000)
                .putInt(3)
                .putDouble(-170.0)
                .putDouble(0.0)
                .putDouble(170.0)
                .putDouble(0.0);
        for (int index = 0; index < 4; index++) {
            output.putLong(0x7ff8000000000000L);
        }
        output.order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(record.length / 2);
        output.put(record);
        return output.array();
    }

    private static byte[] polylineRecord(double[] xy) {
        ByteBuffer record = ByteBuffer.allocate(48 + xy.length * 8).order(ByteOrder.LITTLE_ENDIAN);
        record.putInt(3)
                .putDouble(-170.0)
                .putDouble(0.0)
                .putDouble(170.0)
                .putDouble(0.0)
                .putInt(1)
                .putInt(xy.length / 2)
                .putInt(0);
        for (double value : xy) {
            record.putDouble(value);
        }
        return record.array();
    }

    private static BufferedImage paint(MapView view, int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static boolean hasColorNear(
            BufferedImage image, Color expected, int minX, int minY, int maxX, int maxY) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (Math.abs(actual.getRed() - expected.getRed()) <= 10
                        && Math.abs(actual.getGreen() - expected.getGreen()) <= 10
                        && Math.abs(actual.getBlue() - expected.getBlue()) <= 10) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasGreenNear(
            BufferedImage image, int minX, int minY, int maxX, int maxY) {
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x < maxX; x++) {
                Color actual = new Color(image.getRGB(x, y), true);
                if (actual.getGreen() > actual.getRed() + 25
                        && actual.getGreen() > actual.getBlue() + 20) {
                    return true;
                }
            }
        }
        return false;
    }

    private static double effectiveLineOpacity(Symbol symbol) {
        if (symbol instanceof SolidLineSymbol line) {
            return line.opacity();
        }
        CompositeSymbol composite = assertInstanceOf(CompositeSymbol.class, symbol);
        assertEquals(1, composite.children().size());
        return composite.opacity() * effectiveLineOpacity(composite.children().getFirst());
    }

    private static boolean hasEndpointMarker(Symbol symbol) {
        if (symbol instanceof SolidLineSymbol line) {
            return line.startMarker().isPresent() || line.endMarker().isPresent();
        }
        CompositeSymbol composite = assertInstanceOf(CompositeSymbol.class, symbol);
        return composite.children().stream().anyMatch(MapViewDatelineVectorTest::hasEndpointMarker);
    }

    private static Color mostRedNear(BufferedImage image, int centerX, int centerY, int radius) {
        Color result = new Color(image.getRGB(centerX, centerY), true);
        for (int y = Math.max(0, centerY - radius);
                y <= Math.min(image.getHeight() - 1, centerY + radius);
                y++) {
            for (int x = Math.max(0, centerX - radius);
                    x <= Math.min(image.getWidth() - 1, centerX + radius);
                    x++) {
                Color candidate = new Color(image.getRGB(x, y), true);
                if (candidate.getRed() - candidate.getGreen()
                        > result.getRed() - result.getGreen()) {
                    result = candidate;
                }
            }
        }
        return result;
    }

    private static final class ListSource implements FeatureSource {
        private final FeatureSourceMetadata metadata;
        private final List<FeatureRecord> records;
        private final FeatureSourceLimits limits;
        private boolean closed;

        private ListSource(List<FeatureRecord> records) {
            this("geographic", CrsDefinitions.EPSG_4326, records);
        }

        private ListSource(
                String id,
                io.github.mundanej.map.api.CrsDefinition crs,
                List<FeatureRecord> records) {
            this(id, crs, records, FeatureQueryLimits.LEVEL_1);
        }

        private ListSource(
                String id,
                io.github.mundanej.map.api.CrsDefinition crs,
                List<FeatureRecord> records,
                FeatureQueryLimits limits) {
            this.records = List.copyOf(records);
            this.limits = new FeatureSourceLimits(limits);
            this.metadata =
                    new FeatureSourceMetadata(
                            new SourceIdentity(id, id),
                            Optional.of(crs.coordinateDomain()),
                            OptionalLong.of(records.size()),
                            Optional.empty(),
                            Optional.of(
                                    CrsMetadata.recognized(
                                            crs, Optional.empty(), Optional.empty())));
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public FeatureSourceLimits limits() {
            return limits;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            if (closed) {
                throw new IllegalStateException("closed");
            }
            return new FeatureCursor() {
                private int index;
                private boolean cursorClosed;

                @Override
                public boolean advance() {
                    if (cursorClosed || index >= records.size()) {
                        return false;
                    }
                    index++;
                    return true;
                }

                @Override
                public FeatureRecord current() {
                    return records.get(index - 1);
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return DiagnosticReport.empty();
                }

                @Override
                public boolean isClosed() {
                    return cursorClosed;
                }

                @Override
                public void close() {
                    cursorClosed = true;
                }
            };
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
