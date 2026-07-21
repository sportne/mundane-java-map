package consumer;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.RasterImages;
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
import io.github.mundanej.map.io.geojson.GeoJsonFiles;
import io.github.mundanej.map.io.geojson.GeoJsonOpenOptions;
import io.github.mundanej.map.io.geojson.GeoJsonWriteLimits;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import io.github.mundanej.map.io.svg.SvgSymbols;
import io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.WorkspaceDocument;
import io.github.mundanej.map.workspace.WorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.WorkspaceFiles;
import io.github.mundanej.map.workspace.WorkspaceLimits;
import io.github.mundanej.map.workspace.WorkspaceLocalPathBranch;
import io.github.mundanej.map.workspace.WorkspaceLocalPathProfile;
import io.github.mundanej.map.workspace.WorkspaceOpenContext;
import io.github.mundanej.map.workspace.WorkspaceOpener;
import io.github.mundanej.map.workspace.WorkspaceRelativePath;
import io.github.mundanej.map.workspace.WorkspaceSession;
import io.github.mundanej.map.workspace.WorkspaceSourceReference;
import io.github.mundanej.map.workspace.WorkspaceSourceRegistry;
import io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry;
import io.github.mundanej.map.workspace.WorkspaceSymbolReferences;
import io.github.mundanej.map.workspace.WorkspaceViewState;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Public-artifact-only consumer smoke for the published map modules. */
public final class MundaneMapConsumerSmoke {
    private MundaneMapConsumerSmoke() {}

    /** Runs the isolated downstream assertions. */
    public static void main(String[] arguments) throws Exception {
        require(Runtime.version().feature() == 21, "consumer must run on Java 21");
        CrsRegistry registry = CrsRegistry.level1();
        SymbolRendererRegistry renderers = SymbolRendererRegistry.builderWithBuiltIns().build();
        var decoders = AwtRasterDecoders.level1();
        renderVector(registry, renderers);
        testSvg();
        Path directory = Files.createTempDirectory("mundane-map-consumer-");
        try {
            testGeoJson(directory, registry);
            testShapefile(directory);
            testMalformedShapefile(directory);
            testImages(directory, decoders);
            testDted(directory);
            testGeoTiff();
            testWorkspace(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                paths.sorted(Comparator.reverseOrder()).forEach(MundaneMapConsumerSmoke::delete);
            }
        }
        require(!Files.exists(directory), "consumer temporary directory leaked");
        System.out.println("mundane-map consumer smoke: OK");
    }

    private static void testSvg() {
        Symbol symbol =
                SvgSymbols.parse(
                        new SourceIdentity("consumer-svg", "Consumer SVG"),
                        "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 10 10\"><path d=\"M1 9 L5 1 L9 9 Z\" fill=\"#1478dc\" fill-rule=\"evenodd\"/></svg>"
                                .getBytes(java.nio.charset.StandardCharsets.UTF_8),
                        io.github.mundanej.map.api.MarkerPlacement.centeredScreen(18));
        require(symbol.role() == io.github.mundanej.map.api.SymbolRole.MARKER, "SVG role changed");
    }

    private static void testGeoJson(Path directory, CrsRegistry registry) throws Exception {
        byte[] document =
                """
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","id":"consumer-point","geometry":{
                    "type":"Point","coordinates":[-77.0365,38.8977]},
                    "properties":{"name":"White House","active":true}}
                ]}
                """
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        FeatureSource source =
                GeoJsonFiles.open(
                        document,
                        new SourceIdentity("consumer-geojson", "Consumer GeoJSON"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        Path written = directory.resolve("consumer.geojson");
        try {
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            try {
                require(cursor.advance(), "GeoJSON source was empty");
                FeatureRecord record = cursor.current();
                require(record.id().equals("string:consumer-point"), "unexpected GeoJSON id");
                require(
                        record.attributes().get("name").equals("White House"),
                        "unexpected GeoJSON property");
                PointGeometry point = (PointGeometry) record.geometry();
                require(
                        point.coordinate().equals(new Coordinate(-77.0365, 38.8977)),
                        "unexpected GeoJSON point");
                require(!cursor.advance(), "GeoJSON source had an extra record");
            } finally {
                cursor.close();
            }
            GeoJsonFiles.write(
                    written,
                    source,
                    GeoJsonWriteLimits.defaults(),
                    CancellationToken.none());
            require(!source.isClosed(), "GeoJSON writer closed its borrowed source");
        } finally {
            source.close();
        }

        FeatureSource reopened =
                GeoJsonFiles.open(
                        written,
                        new SourceIdentity("consumer-geojson-reopened", "Consumer GeoJSON reopened"),
                        GeoJsonOpenOptions.defaults(),
                        CancellationToken.none());
        FeatureCursor reopenedCursor =
                reopened.openCursor(FeatureQuery.all(), CancellationToken.none());
        try {
            require(reopenedCursor.advance(), "reopened GeoJSON source was empty");
            require(
                    reopenedCursor.current().id().equals("string:string:consumer-point"),
                    "reopened GeoJSON id did not apply the documented string prefix");
            require(!reopenedCursor.advance(), "reopened GeoJSON source had an extra record");
        } finally {
            reopenedCursor.close();
        }
        renderGeoJson(reopened, registry);
        require(reopened.isClosed(), "owned GeoJSON render source did not close");
    }

    private static void renderGeoJson(FeatureSource source, CrsRegistry registry) throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    registry,
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_3857);
                    SolidLineSymbol line =
                            SolidLineSymbol.of(
                                    new SymbolStroke(
                                            Rgba.rgb(20, 120, 220),
                                            new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                                    1);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.ownedFeature(
                                            "consumer-geojson",
                                            "Consumer GeoJSON",
                                            source,
                                            BuiltInMarkers.filledScreen(
                                                    BuiltInMarker.DIAMOND,
                                                    Rgba.rgb(20, 120, 220),
                                                    18,
                                                    1),
                                            line,
                                            SolidFillSymbol.of(
                                                    new Rgba(20, 120, 220, 80),
                                                    Optional.of(line),
                                                    1))));
                    try {
                        view.setSize(96, 96);
                        view.fitToData(16);
                        BufferedImage image =
                                new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setColor(java.awt.Color.WHITE);
                            graphics.fillRect(0, 0, 96, 96);
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        int colored = 0;
                        for (int y = 24; y < 72; y++) {
                            for (int x = 24; x < 72; x++) {
                                int rgb = image.getRGB(x, y);
                                int red = (rgb >>> 16) & 0xff;
                                int blue = rgb & 0xff;
                                if (blue > red + 40) colored++;
                            }
                        }
                        require(colored > 20, "reopened GeoJSON did not render");
                    } finally {
                        view.close();
                    }
                });
    }

    private static void renderVector(CrsRegistry registry, SymbolRendererRegistry renderers)
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view =
                            new MapView(
                                    registry,
                                    CrsDefinitions.EPSG_3857,
                                    CrsDefinitions.EPSG_3857,
                                    renderers);
                    try {
                        Feature lower =
                                new Feature(
                                        "consumer-lower",
                                        "LOSER",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of("kind", "red"),
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.SQUARE,
                                                Rgba.rgb(115, 115, 115),
                                                8,
                                                1));
                        Feature upper =
                                new Feature(
                                        "consumer-upper",
                                        "WINNER",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of("kind", "blue"),
                                        lower.symbol());
                        var blueMarker =
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.CIRCLE, Rgba.rgb(20, 120, 220), 18, 1);
                        var redMarker =
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, Rgba.rgb(210, 45, 45), 18, 1);
                        CategoricalSymbolSelector selector =
                                new CategoricalSymbolSelector(
                                        "kind",
                                        List.of(
                                                new CategoricalSymbolRule(
                                                        ThematicValue.text("blue"), blueMarker),
                                                new CategoricalSymbolRule(
                                                        ThematicValue.text("red"), redMarker)),
                                        Optional.empty());
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.portrayedSnapshot(
                                                new InMemoryLayer(
                                                        "consumer-lower",
                                                        "Consumer lower",
                                                        List.of(lower)),
                                                consumerPortrayal(
                                                        selector,
                                                        0,
                                                        Rgba.rgb(180, 180, 20))),
                                        MapLayerBinding.portrayedSnapshot(
                                                new InMemoryLayer(
                                                        "consumer-upper",
                                                        "Consumer upper",
                                                        List.of(upper)),
                                                consumerPortrayal(
                                                        selector,
                                                        10,
                                                        Rgba.rgb(20, 170, 40)))));
                        view.setSize(180, 100);
                        view.fitToData(16);
                        BufferedImage image =
                                new BufferedImage(180, 100, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setColor(java.awt.Color.WHITE);
                            graphics.fillRect(0, 0, 96, 96);
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        int colored = 0;
                        int winnerLabel = 0;
                        int loserLabel = 0;
                        for (int y = 0; y < image.getHeight(); y++) {
                            for (int x = 0; x < image.getWidth(); x++) {
                                int rgb = image.getRGB(x, y);
                                int red = (rgb >>> 16) & 0xff;
                                int green = (rgb >>> 8) & 0xff;
                                int blue = rgb & 0xff;
                                if (blue > red + 40 && blue > green + 40) colored++;
                                if (green > red + 50 && green > blue + 50) winnerLabel++;
                                if (red > blue + 50 && green > blue + 50) loserLabel++;
                            }
                        }
                        require(colored > 20, "vector symbol did not render in the expected region");
                        require(winnerLabel > 10, "priority-winning consumer label did not render");
                        require(loserLabel == 0, "lower-priority consumer label was not omitted");
                    } finally {
                        view.close();
                    }
                });
    }

    private static FeaturePortrayal consumerPortrayal(
            CategoricalSymbolSelector selector, int priority, Rgba labelColor) {
        return FeaturePortrayal.markers(selector)
                .withPointLabel(
                        new PointLabelProfile(
                                FeatureName.INSTANCE,
                                new LabelTextStyle(
                                        labelColor, LabelWeight.NORMAL, 12),
                                List.of(PointLabelPosition.NE),
                                2,
                                0,
                                0,
                                1,
                                priority,
                                ResolutionRange.ALL));
    }

    private static void testShapefile(Path directory) throws Exception {
        Path shape = directory.resolve("point.shp");
        Files.write(shape, pointShp(0.25, 0.75));
        Files.write(directory.resolve("point.shx"), pointShx(0.25, 0.75));
        FeatureRecord retained;
        FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity("consumer-shape", "Consumer shape"),
                        shape,
                        ShapefileOpenOptions.defaults());
        try {
            FeatureCursor cursor = source.openCursor(FeatureQuery.all(), CancellationToken.none());
            try {
                require(cursor.advance(), "point shapefile was empty");
                retained = cursor.current();
                require(!cursor.advance(), "point shapefile had an extra record");
            } finally {
                cursor.close();
                cursor.close();
                require(cursor.isClosed(), "shapefile cursor did not close");
            }
        } finally {
            source.close();
            source.close();
            require(source.isClosed(), "shapefile source did not close");
        }
        PointGeometry point = (PointGeometry) retained.geometry();
        require(retained.id().equals("record:1"), "unexpected feature id");
        require(point.coordinate().equals(new Coordinate(0.25, 0.75)), "unexpected point");
    }

    private static void testMalformedShapefile(Path directory) throws IOException {
        Path malformed = directory.resolve("truncated.shp");
        Files.write(malformed, new byte[99]);
        try {
            Shapefiles.open(
                    new SourceIdentity("malformed", "Malformed"),
                    malformed,
                    ShapefileOpenOptions.defaults());
            throw new IllegalStateException("malformed shapefile was accepted");
        } catch (SourceException expected) {
            var diagnostic = expected.terminal();
            require(diagnostic.code().equals("SHAPEFILE_HEADER_INVALID"), "unexpected SHP code");
            require(
                    diagnostic.location().orElseThrow().component().orElseThrow().equals("shp"),
                    "unexpected SHP component");
            require(
                    diagnostic.location().orElseThrow().byteOffset().orElseThrow() == 0,
                    "unexpected SHP offset");
            require(
                    diagnostic.context()
                            .equals(
                                    Map.of(
                                            "field", "headerSize",
                                            "expectedBytes", "100",
                                            "actualBytes", "99")),
                    "unexpected SHP context");
        }
    }

    private static void testImages(Path directory, io.github.mundanej.map.api.EncodedRasterDecoderRegistry decoders)
            throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xff204080);
        image.setRGB(1, 0, 0xffc03020);
        image.setRGB(0, 1, 0xff20c040);
        image.setRGB(1, 1, 0xffe0d030);
        Path png = directory.resolve("sample.png");
        Path jpeg = directory.resolve("sample.jpg");
        require(ImageIO.write(image, "png", png.toFile()), "PNG writer unavailable");
        BufferedImage jpegImage = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 2; x++) jpegImage.setRGB(x, y, 0xff406080);
        }
        require(ImageIO.write(jpegImage, "jpeg", jpeg.toFile()), "JPEG writer unavailable");
        readImage(png, decoders, true);
        readImage(jpeg, decoders, false);
    }

    private static void readImage(
            Path path,
            io.github.mundanej.map.api.EncodedRasterDecoderRegistry decoders,
            boolean exact)
            throws Exception {
        var source =
                RasterImages.open(
                        path,
                        new SourceIdentity("image-" + path.getFileName(), "Consumer image"),
                        ImageOpenOptions.defaults(),
                        decoders);
        var read =
                source.read(
                        new RasterRequest(new RasterWindow(0, 0, 2, 2), 2, 2, Optional.empty()),
                        CancellationToken.none());
        int sample = read.pixels().rgbaAt(0, 0);
        source.close();
        source.close();
        require(source.isClosed(), "image source did not close");
        require(read.pixels().rgbaAt(0, 0) == sample, "retained raster value changed");
        int red = (sample >>> 24) & 0xff;
        int green = (sample >>> 16) & 0xff;
        int blue = (sample >>> 8) & 0xff;
        if (exact) {
            require(red == 0x20 && green == 0x40 && blue == 0x80, "PNG sample changed");
        } else {
            require(Math.abs(red - 0x40) <= 16
                            && Math.abs(green - 0x60) <= 16
                            && Math.abs(blue - 0x80) <= 16,
                    "JPEG sample outside tolerance");
        }
    }

    private static void testDted(Path directory) throws IOException {
        Path path = directory.resolve("consumer.dt0");
        byte[] fixture = levelZeroDted();
        verifyLevelZeroDtedHeader(fixture);
        Files.write(path, fixture);
        ElevationSource source =
                DtedFiles.open(
                        new SourceIdentity("consumer-dted", "Consumer DTED"),
                        path,
                        DtedOpenOptions.defaults());
        var metadata = source.metadata();
        require(metadata.columnCount() == 21, "unexpected DTED columns");
        require(metadata.rowCount() == 121, "unexpected DTED rows");
        require(metadata.sampleBounds().equals(new io.github.mundanej.map.api.Envelope(0, 80, 1, 81)),
                "unexpected DTED bounds");
        require(source.sample(0, 0).orElseThrow() == 120, "unexpected DTED north sample");
        require(source.sample(0, 120).orElseThrow() == 0, "unexpected DTED south sample");
        require(source.openingDiagnostics().entries().isEmpty(), "unexpected DTED diagnostics");
        source.close();
        source.close();
        require(source.isClosed(), "DTED source did not close");
        require(source.metadata().equals(metadata), "DTED metadata changed after close");
    }

    private static void testGeoTiff() {
        RasterSource source =
                GeoTiffFiles.openRaster(
                        new SourceIdentity("consumer-geotiff", "Consumer GeoTIFF"),
                        geoTiffFixture(),
                        GeoTiffRasterOptions.defaults());
        try {
            require(source.metadata().width() == 4, "unexpected GeoTIFF width");
            require(
                    source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow()
                            .equals("EPSG:4326"),
                    "unexpected GeoTIFF CRS");
            var read =
                    source.read(
                            new RasterRequest(
                                    new RasterWindow(1, 1, 2, 2), 2, 2, Optional.empty()),
                            CancellationToken.none());
            require(read.pixels().rgbaAt(0, 0) == 0x646464ff, "unexpected GeoTIFF sample");
        } finally {
            source.close();
        }
    }

    private static void testWorkspace(Path directory) throws IOException {
        String openerId = "consumer.workspace.feature.v1";
        String catalogId = "consumer.workspace.catalog";
        Path sourcePath = directory.resolve("workspace-source.data");
        Path workspacePath = directory.resolve("consumer.mmap.xml");
        Files.writeString(sourcePath, "fixed consumer source\n");
        WorkspaceDocument document =
                new WorkspaceDocument(
                        new WorkspaceViewState("EPSG:3857", "EPSG:3857", 4, 8, 2),
                        List.of(
                                new WorkspaceFeatureLayer(
                                        "consumer-layer",
                                        "Consumer layer",
                                        new WorkspaceSourceReference(
                                                openerId,
                                                new SourceIdentity(
                                                        "consumer-workspace-source",
                                                        "Consumer workspace source"),
                                                new WorkspaceRelativePath(
                                                        sourcePath.getFileName().toString())),
                                        new WorkspaceSymbolReferences(
                                                catalogId, "marker", "line", "fill"))));
        WorkspaceFiles.write(workspacePath, document, WorkspaceLimits.DEFAULT);
        var firstRead = WorkspaceFiles.read(workspacePath, WorkspaceLimits.DEFAULT);
        require(firstRead.document().equals(document), "workspace read changed the document");
        WorkspaceFiles.write(workspacePath, firstRead.document(), WorkspaceLimits.DEFAULT);
        var reopened = WorkspaceFiles.read(workspacePath, WorkspaceLimits.DEFAULT);

        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(35, 95, 175),
                                new SymbolLength(1, SymbolUnit.SCREEN_PIXEL)),
                        1);
        NamedSymbolCatalog catalog =
                NamedSymbolCatalog.of(
                        List.of(
                                new NamedSymbol(
                                        "marker",
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.CIRCLE,
                                                Rgba.rgb(35, 95, 175),
                                                8,
                                                1)),
                                new NamedSymbol("line", line),
                                new NamedSymbol(
                                        "fill",
                                        SolidFillSymbol.of(
                                                new Rgba(35, 95, 175, 80),
                                                Optional.of(line),
                                                1))));
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                openerId,
                                new WorkspaceLocalPathProfile(
                                        List.of(
                                                new WorkspaceLocalPathBranch(
                                                        ".data", List.of()))),
                                (identity, path, cancellation) ->
                                        InMemoryFeatureSource.open(
                                                identity,
                                                List.of(
                                                        new FeatureRecord(
                                                                "consumer-workspace-feature",
                                                                "",
                                                                new PointGeometry(
                                                                        new Coordinate(4, 8)),
                                                                Map.of())),
                                                Optional.empty(),
                                                Optional.of(
                                                        CrsMetadata.recognized(
                                                                CrsDefinitions.EPSG_3857,
                                                                Optional.of("EPSG:3857"),
                                                                Optional.empty())),
                                                FeatureSourceLimits.LEVEL_1))
                        .build();
        WorkspaceOpenContext context =
                new WorkspaceOpenContext(
                        CrsRegistry.level1(),
                        sources,
                        WorkspaceSymbolCatalogRegistry.builder()
                                .register(catalogId, catalog)
                                .build());
        WorkspaceSession session =
                WorkspaceOpener.open(reopened, context, CancellationToken.none());
        OpenedWorkspaceFeatureLayer opened =
                (OpenedWorkspaceFeatureLayer) session.layers().getFirst();
        require(!opened.source().isClosed(), "workspace source closed before its session");
        session.close();
        session.close();
        require(session.isClosed(), "workspace session did not close");
        require(opened.source().isClosed(), "workspace session did not close its source");
    }

    private static byte[] geoTiffFixture() {
        ByteBuffer bytes = ByteBuffer.allocate(286).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) 13);
        tiffEntry(bytes, 256, 3, 1, 4);
        tiffEntry(bytes, 257, 3, 1, 3);
        tiffEntry(bytes, 258, 3, 1, 8);
        tiffEntry(bytes, 259, 3, 1, 1);
        tiffEntry(bytes, 262, 3, 1, 1);
        tiffEntry(bytes, 273, 4, 1, 274);
        tiffEntry(bytes, 277, 3, 1, 1);
        tiffEntry(bytes, 278, 4, 1, 3);
        tiffEntry(bytes, 279, 4, 1, 12);
        tiffEntry(bytes, 284, 3, 1, 1);
        tiffEntry(bytes, 33550, 12, 3, 170);
        tiffEntry(bytes, 33922, 12, 6, 194);
        tiffEntry(bytes, 34735, 3, 16, 242);
        bytes.putInt(0);
        bytes.position(170).putDouble(1).putDouble(1).putDouble(0);
        bytes.position(194)
                .putDouble(0).putDouble(0).putDouble(0)
                .putDouble(10).putDouble(20).putDouble(0);
        bytes.position(242)
                .putShort((short) 1).putShort((short) 1).putShort((short) 0).putShort((short) 3);
        tiffKey(bytes, 1024, 2);
        tiffKey(bytes, 1025, 1);
        tiffKey(bytes, 2048, 4326);
        bytes.position(274);
        for (int value = 0; value < 12; value++) {
            bytes.put((byte) (value * 20));
        }
        return bytes.array();
    }

    private static void tiffEntry(ByteBuffer bytes, int tag, int type, int count, int value) {
        bytes.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static void tiffKey(ByteBuffer bytes, int key, int value) {
        bytes.putShort((short) key).putShort((short) 0).putShort((short) 1).putShort((short) value);
    }

    private static byte[] levelZeroDted() {
        byte[] bytes = new byte[8_762];
        java.util.Arrays.fill(bytes, (byte) ' ');
        putAscii(bytes, 0, "UHL1");
        putAscii(bytes, 4, "0000000E0800000N18000300NA  U  MUNDANE-CONS002101210");
        putAscii(bytes, 80, "DSI");
        bytes[83] = 'U';
        putAscii(bytes, 139, "DTED0");
        putAscii(bytes, 144, "MUNDANE-CONS   ");
        putAscii(bytes, 167, "01");
        bytes[169] = 'A';
        putAscii(bytes, 170, "0000");
        putAscii(bytes, 174, "0000");
        putAscii(bytes, 178, "0000");
        putAscii(bytes, 182, "MUN     ");
        putAscii(bytes, 206, "PRF89020B");
        putAscii(bytes, 215, "00");
        putAscii(bytes, 217, "0000");
        putAscii(bytes, 221, "MSLWGS84");
        putAscii(bytes, 229, "CONSUMER  ");
        putAscii(bytes, 239, "0000");
        putAscii(bytes, 265, "800000.0N");
        putAscii(bytes, 274, "0000000.0E");
        putAscii(bytes, 284, "800000N");
        putAscii(bytes, 291, "0000000E");
        putAscii(bytes, 299, "810000N");
        putAscii(bytes, 306, "0000000E");
        putAscii(bytes, 314, "810000N");
        putAscii(bytes, 321, "0010000E");
        putAscii(bytes, 329, "800000N");
        putAscii(bytes, 336, "0010000E");
        putAscii(bytes, 344, "0000000.0");
        putAscii(bytes, 353, "0300");
        putAscii(bytes, 357, "1800");
        putAscii(bytes, 361, "0121");
        putAscii(bytes, 365, "0021");
        putAscii(bytes, 369, "00");
        putAscii(bytes, 728, "ACC");
        putAscii(bytes, 731, "NA  NA  NA  NA  ");
        putAscii(bytes, 783, "00");
        int position = 3_428;
        for (int profile = 0; profile < 21; profile++) {
            ByteBuffer record = ByteBuffer.wrap(bytes, position, 254).slice().order(ByteOrder.BIG_ENDIAN);
            record.put((byte) 0xaa);
            record.put((byte) 0).put((byte) 0).put((byte) profile);
            record.putShort((short) profile).putShort((short) 0);
            for (int sample = 0; sample < 121; sample++) {
                record.putShort((short) (profile * 10 + sample));
            }
            long checksum = 0;
            for (int index = 0; index < 250; index++) checksum += bytes[position + index] & 0xffL;
            record.putInt((int) checksum);
            position += 254;
        }
        return bytes;
    }

    private static void verifyLevelZeroDtedHeader(byte[] bytes) {
        require(bytes.length == 8_762, "independent DTED fixture length changed");
        requireAscii(bytes, 0, "UHL1", "UHL sentinel");
        requireAscii(bytes, 80, "DSIU", "DSI sentinel/classification");
        requireAscii(bytes, 139, "DTED0", "DTED series");
        requireAscii(bytes, 167, "01A000000000000", "DSI edition/date profile");
        requireAscii(bytes, 182, "MUN     ", "DSI producer");
        requireAscii(
                bytes,
                206,
                "PRF89020B000000MSLWGS84CONSUMER  0000",
                "DSI product profile");
        requireAscii(
                bytes,
                265,
                "800000.0N0000000.0E800000N0000000E"
                        + "810000N0000000E810000N0010000E"
                        + "800000N0010000E0000000.0030018000121002100",
                "DSI grid profile");
        requireAscii(bytes, 728, "ACCNA  NA  NA  NA  ", "ACC profile");
        requireAscii(bytes, 783, "00", "ACC subregion count");
    }

    private static void requireAscii(byte[] bytes, int offset, String expected, String field) {
        String actual =
                new String(
                        bytes,
                        offset,
                        expected.length(),
                        java.nio.charset.StandardCharsets.US_ASCII);
        require(actual.equals(expected), "independent DTED " + field + " changed");
    }

    private static void putAscii(byte[] bytes, int offset, String value) {
        byte[] encoded = value.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(encoded, 0, bytes, offset, encoded.length);
    }

    private static byte[] pointShp(double x, double y) {
        ByteBuffer bytes = ByteBuffer.allocate(128);
        header(bytes, 64, x, y);
        bytes.order(ByteOrder.BIG_ENDIAN).position(100).putInt(1).putInt(10);
        bytes.order(ByteOrder.LITTLE_ENDIAN).putInt(1).putDouble(x).putDouble(y);
        return bytes.array();
    }

    private static byte[] pointShx(double x, double y) {
        ByteBuffer bytes = ByteBuffer.allocate(108);
        header(bytes, 54, x, y);
        bytes.order(ByteOrder.BIG_ENDIAN).position(100).putInt(50).putInt(10);
        return bytes.array();
    }

    private static void header(ByteBuffer bytes, int words, double x, double y) {
        bytes.order(ByteOrder.BIG_ENDIAN).putInt(0, 9994).putInt(24, words);
        bytes.order(ByteOrder.LITTLE_ENDIAN)
                .putInt(28, 1000)
                .putInt(32, 1)
                .putDouble(36, x)
                .putDouble(44, y)
                .putDouble(52, x)
                .putDouble(60, y);
    }

    private static void delete(Path path) {
        try {
            Files.delete(path);
        } catch (IOException failure) {
            throw new IllegalStateException("consumer cleanup failed", failure);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
