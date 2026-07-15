package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.SyntheticRasterSource;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JComponent;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewRasterSourceTest {
    private static final int SIZE = 100;
    private static final Envelope PROJECTED_BOUNDS = new Envelope(-100.0, -100.0, 100.0, 100.0);

    @Test
    void paintReadsOnlyTheVisibleWindowAndPreservesRgbaOrientationAndAlpha() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source = projectedSource("paint", 4, 4, PROJECTED_BOUNDS);
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(
                            List.of(MapLayerBinding.borrowedRaster("raster", "raster", source)));

                    BufferedImage image = paint(view);

                    assertEquals(new RasterWindow(1, 1, 2, 2), source.lastRequest.sourceWindow());
                    assertEquals(2, source.lastRequest.outputWidth());
                    assertEquals(2, source.lastRequest.outputHeight());
                    assertColorNear(new Color(255, 127, 127), new Color(image.getRGB(25, 25)), 2);
                    assertColorNear(Color.GREEN, new Color(image.getRGB(75, 25)), 1);
                    assertColorNear(Color.BLUE, new Color(image.getRGB(25, 75)), 1);
                    assertColorNear(Color.WHITE, new Color(image.getRGB(75, 75)), 1);
                    assertEquals(1, source.readCount);
                    view.close();
                    assertFalse(source.isClosed());
                });
    }

    @Test
    void immutableOptionsUpdatePresentationWithoutReplacingOrClosingTheSource() throws Exception {
        assertEquals(
                RasterRenderOptions.defaults(),
                new RasterRenderOptions(RasterInterpolation.NEAREST, 1));
        assertEquals(
                new RasterRenderOptions(RasterInterpolation.NEAREST, 0.0),
                new RasterRenderOptions(RasterInterpolation.NEAREST, -0.0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterRenderOptions(RasterInterpolation.NEAREST, Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterRenderOptions(RasterInterpolation.NEAREST, 1.01));

        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source = projectedSource("options", 1, 1, PROJECTED_BOUNDS);
                    source.solidRgba = 0xff00_00ff;
                    source.warning = true;
                    MapLayerBinding binding =
                            MapLayerBinding.borrowedRaster("raster", "raster", source);
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(List.of(binding));

                    BufferedImage full = paint(view);
                    assertColorNear(Color.RED, new Color(full.getRGB(50, 50)), 1);
                    assertEquals(1, source.readCount);
                    assertTrue(view.sourceReports().containsKey("raster"));

                    source.solidRgba = 0xff00_0080;
                    RepaintManager previous = RepaintManager.currentManager(view);
                    RecordingRepaintManager repaintManager = new RecordingRepaintManager();
                    RepaintManager.setCurrentManager(repaintManager);
                    try {
                        view.setRasterRenderOptions(
                                "raster",
                                new RasterRenderOptions(RasterInterpolation.BILINEAR, 0.5));
                    } finally {
                        RepaintManager.setCurrentManager(previous);
                    }
                    assertTrue(repaintManager.dirtyRegions > 0);
                    BufferedImage half = paint(view);
                    assertColorNear(new Color(255, 191, 191), new Color(half.getRGB(50, 50)), 2);
                    assertEquals(RasterInterpolation.BILINEAR, source.lastRequest.interpolation());
                    assertEquals(2, source.readCount);

                    view.setRasterRenderOptions(
                            "raster", new RasterRenderOptions(RasterInterpolation.NEAREST, 1.0));
                    BufferedImage translucentFull = paint(view);
                    assertColorNear(
                            new Color(255, 127, 127), new Color(translucentFull.getRGB(50, 50)), 2);
                    assertEquals(3, source.readCount);

                    view.setRasterRenderOptions(
                            "raster", new RasterRenderOptions(RasterInterpolation.NEAREST, 0.0));
                    BufferedImage zero = paint(view);
                    assertEquals(Color.WHITE, new Color(zero.getRGB(50, 50)));
                    assertEquals(3, source.readCount);
                    assertTrue(view.sourceReports().containsKey("raster"));
                    assertEquals(List.of(binding), view.layerBindings());
                    assertFalse(source.isClosed());
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    view.setRasterRenderOptions(
                                            "missing", RasterRenderOptions.defaults()));
                    view.close();
                    assertFalse(source.isClosed());
                });
    }

    @Test
    void productionSyntheticSourceRendersThroughTheAwtWindowSlice() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    SyntheticRasterSource source =
                            SyntheticRasterSource.open(
                                    new SourceIdentity("synthetic", "synthetic"),
                                    2,
                                    2,
                                    new Envelope(-1, -1, 1, 1),
                                    recognized(CrsDefinitions.EPSG_3857).orElseThrow());
                    MapView view = configuredProjectedView();
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 0.02));
                    view.setLayerBindings(
                            List.of(MapLayerBinding.borrowedRaster("raster", "raster", source)));

                    BufferedImage image = paint(view);

                    assertEquals(0xff00_0000, image.getRGB(25, 25));
                    assertEquals(0xff01_0001, image.getRGB(75, 25));
                    assertEquals(0xff00_0101, image.getRGB(25, 75));
                    assertEquals(0xff01_0100, image.getRGB(75, 75));
                    view.close();
                    assertFalse(source.isClosed());
                });
    }

    @Test
    void affineRasterPaintsItsParallelogramAndLeavesEnvelopeExteriorUnpainted() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RasterGridPlacement placement =
                            RasterGridPlacement.affine(
                                    RasterAffineTransform.of(20, 0, 10, -20, -10, 10));
                    RasterSourceMetadata metadata =
                            RasterSourceMetadata.withPlacement(
                                    new SourceIdentity("affine", "affine"),
                                    2,
                                    2,
                                    placement,
                                    recognized(CrsDefinitions.EPSG_3857));
                    TestRasterSource source =
                            new TestRasterSource(metadata, RasterSourceLimits.LEVEL_1);
                    source.solidRgba = 0xff00_00ff;
                    MapView view = configuredProjectedView();
                    view.setViewport(new MapViewport(SIZE, SIZE, 5, 0, 0.6));
                    view.setLayerBindings(
                            List.of(MapLayerBinding.borrowedRaster("affine", "affine", source)));

                    view.fitToData(0);
                    assertEquals(0, source.readCount);
                    BufferedImage image = paint(view);

                    Coordinate center = view.viewport().worldToScreen(new Coordinate(5, 0));
                    assertColorNear(
                            Color.RED,
                            new Color(image.getRGB((int) center.x(), (int) center.y())),
                            1);
                    Coordinate envelopeOnly = view.viewport().worldToScreen(new Coordinate(30, 15));
                    assertColorNear(
                            Color.WHITE,
                            new Color(image.getRGB((int) envelopeOnly.x(), (int) envelopeOnly.y())),
                            1);
                    assertEquals(1, source.readCount);
                    view.close();
                });
    }

    @Test
    void partiallyVisibleShearedRasterSelectsDensityOutputAndFinalNearestColors() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RasterGridPlacement placement =
                            RasterGridPlacement.affine(
                                    RasterAffineTransform.of(2, 0.5, 1, -2, 0, 0));
                    RasterSourceMetadata metadata =
                            RasterSourceMetadata.withPlacement(
                                    new SourceIdentity("partial-affine", "partial-affine"),
                                    100,
                                    80,
                                    placement,
                                    recognized(CrsDefinitions.EPSG_3857));
                    TestRasterSource source =
                            new TestRasterSource(metadata, RasterSourceLimits.LEVEL_1);
                    source.splitOutput = true;
                    MapView view = TestMapViews.identity();
                    view.setSize(40, 30);
                    var transform = placement.affineTransform().orElseThrow();
                    Coordinate center = transform.gridToMap(50, 40);
                    view.setViewport(new MapViewport(40, 30, center.x(), center.y(), 3));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedRaster(
                                            "partial",
                                            "partial",
                                            source,
                                            new RasterRenderOptions(
                                                    RasterInterpolation.BILINEAR, 1))));

                    BufferedImage image = paint(view, 40, 30);

                    assertTrue(source.lastRequest.sourceWindow().width() < 100);
                    assertTrue(source.lastRequest.sourceWindow().height() < 80);
                    assertTrue(
                            source.lastRequest.outputWidth()
                                    < source.lastRequest.sourceWindow().width());
                    assertTrue(
                            source.lastRequest.outputHeight()
                                    < source.lastRequest.sourceWindow().height());
                    assertEquals(RasterInterpolation.BILINEAR, source.lastRequest.interpolation());
                    for (int row = 0; row < image.getHeight(); row++) {
                        for (int column = 0; column < image.getWidth(); column++) {
                            int argb = image.getRGB(column, row);
                            assertTrue(
                                    argb == Color.WHITE.getRGB()
                                            || argb == Color.RED.getRGB()
                                            || argb == Color.BLUE.getRGB(),
                                    () ->
                                            "unexpected double-filtered pixel "
                                                    + Integer.toHexString(argb));
                        }
                    }
                    view.close();
                });
    }

    @Test
    void mixedRasterAndSnapshotBindingsPaintInDeclaredOrder() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source = projectedSource("order", 1, 1, PROJECTED_BOUNDS);
                    source.solidRgba = 0xff00_00ff;
                    MapLayerBinding raster =
                            MapLayerBinding.borrowedRaster("raster", "raster", source);
                    MapLayerBinding vector = MapLayerBinding.snapshot(pointLayer());
                    MapView view = configuredProjectedView();

                    view.setLayerBindings(List.of(raster, vector));
                    BufferedImage vectorAbove = paint(view);
                    assertColorNear(Color.BLUE, new Color(vectorAbove.getRGB(50, 50)), 2);
                    assertColorNear(Color.RED, new Color(vectorAbove.getRGB(10, 10)), 1);

                    view.setLayerBindings(List.of(vector, raster));
                    BufferedImage rasterAbove = paint(view);
                    assertColorNear(Color.RED, new Color(rasterAbove.getRGB(50, 50)), 1);
                    view.close();
                });
    }

    @Test
    void failedRasterDoesNotPreventALaterSnapshotFromPainting() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source = projectedSource("failed", 1, 1, PROJECTED_BOUNDS);
                    source.failureCode = "TEST_RASTER_FAILED";
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedRaster("raster", "raster", source),
                                    MapLayerBinding.snapshot(pointLayer())));

                    BufferedImage image = paint(view);

                    assertColorNear(Color.BLUE, new Color(image.getRGB(50, 50)), 2);
                    assertEquals(
                            "TEST_RASTER_FAILED",
                            view.sourceReports().get("raster").entries().getLast().code());
                    view.close();
                });
    }

    @Test
    void rasterInteractionAndFitNeverReadAndSelectionCannotTargetRaster() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source =
                            projectedSource("interaction", 2, 2, new Envelope(-10, -20, 10, 20));
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(
                            List.of(MapLayerBinding.borrowedRaster("raster", "raster", source)));

                    view.fitToData(0.0);
                    assertEquals(0.4, view.viewport().worldUnitsPerPixel());
                    assertTrue(view.hitTest(50, 50, 4).hits().isEmpty());
                    assertTrue(view.selection().isEmpty());
                    assertTrue(view.hover().isEmpty());
                    move(view, 50, 50);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> view.setSelection(new FeatureSelection("raster", "pixel")));
                    assertEquals(0, source.readCount);
                    view.close();
                });
    }

    @Test
    void rasterAttachmentRejectsMissingBoundsAndEveryUnsupportedCrsState() {
        MapView view = configuredProjectedView();
        TestRasterSource missingBounds =
                source(
                        "missing-bounds",
                        1,
                        1,
                        Optional.empty(),
                        recognized(CrsDefinitions.EPSG_3857),
                        RasterSourceLimits.LEVEL_1);
        SourceException boundsFailure =
                assertThrows(
                        SourceException.class,
                        () ->
                                view.setLayerBindings(
                                        List.of(
                                                MapLayerBinding.borrowedRaster(
                                                        "missing-bounds",
                                                        "missing-bounds",
                                                        missingBounds))));
        assertEquals("RASTER_MAP_BOUNDS_MISSING", boundsFailure.terminal().code());
        assertEquals(Optional.of(DiagnosticLocation.empty()), boundsFailure.terminal().location());
        assertTrue(boundsFailure.terminal().context().isEmpty());

        assertCrsFailure(
                view,
                source(
                        "missing-crs",
                        1,
                        1,
                        Optional.of(new Envelope(-1, -1, 1, 1)),
                        Optional.empty(),
                        RasterSourceLimits.LEVEL_1),
                "CRS_METADATA_MISSING");
        assertCrsFailure(
                view,
                source(
                        "unknown-crs",
                        1,
                        1,
                        Optional.of(new Envelope(-1, -1, 1, 1)),
                        Optional.of(CrsMetadata.unknown(Optional.of("LOCAL"), Optional.empty())),
                        RasterSourceLimits.LEVEL_1),
                "CRS_DEFINITION_UNKNOWN");
        CrsDefinition canonical = CrsDefinitions.EPSG_3857;
        CrsDefinition fabricated =
                new CrsDefinition(
                        canonical.canonicalIdentifier(),
                        canonical.kind(),
                        canonical.xAxis(),
                        canonical.yAxis(),
                        new Envelope(-1, -1, 1, 1));
        assertCrsFailure(
                view,
                source(
                        "fabricated",
                        1,
                        1,
                        Optional.of(new Envelope(-1, -1, 1, 1)),
                        recognized(fabricated),
                        RasterSourceLimits.LEVEL_1),
                "CRS_DEFINITION_MISMATCH");
        assertCrsFailure(
                view,
                source(
                        "warp",
                        1,
                        1,
                        Optional.of(new Envelope(-1, -1, 1, 1)),
                        recognized(CrsDefinitions.EPSG_4326),
                        RasterSourceLimits.LEVEL_1),
                "CRS_RASTER_WARP_UNSUPPORTED");
        view.close();
    }

    @Test
    void matchingGeographicRasterPaintsWithoutWarp() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source =
                            source(
                                    "geographic",
                                    2,
                                    2,
                                    Optional.of(new Envelope(-1, -1, 1, 1)),
                                    recognized(CrsDefinitions.EPSG_4326),
                                    RasterSourceLimits.LEVEL_1);
                    MapView view =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_4326);
                    view.setSize(SIZE, SIZE);
                    view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 0.02));
                    view.setLayerBindings(
                            List.of(MapLayerBinding.borrowedRaster("geo", "geo", source)));
                    paint(view);
                    assertEquals(1, source.readCount);
                    assertEquals(new RasterWindow(0, 0, 2, 2), source.lastRequest.sourceWindow());
                    view.close();
                });
    }

    @Test
    void successfulWarningsPublishAndAnEmptyIntersectionClearsThemWithoutARead() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source = projectedSource("warning", 1, 1, PROJECTED_BOUNDS);
                    source.warning = true;
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(
                            List.of(MapLayerBinding.borrowedRaster("raster", "raster", source)));

                    paint(view);
                    assertEquals(
                            "TEST_RASTER_WARNING",
                            view.sourceReports().get("raster").entries().getFirst().code());
                    view.setViewport(new MapViewport(SIZE, SIZE, 1_000, 1_000, 1));
                    paint(view);
                    assertEquals(1, source.readCount);
                    assertTrue(view.sourceReports().isEmpty());
                    view.close();
                });
    }

    @Test
    void combinedAwtAllocationIsRejectedBeforeTheSourceRead() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    RasterRequestLimits limits = new RasterRequestLimits(1, 1, 1, 7, 4, 8);
                    TestRasterSource source =
                            source(
                                    "bounded",
                                    1,
                                    1,
                                    Optional.of(new Envelope(-1, -1, 1, 1)),
                                    recognized(CrsDefinitions.EPSG_3857),
                                    new RasterSourceLimits(limits));
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(
                            List.of(MapLayerBinding.borrowedRaster("raster", "raster", source)));

                    paint(view);

                    assertEquals(0, source.readCount);
                    SourceDiagnostic terminal =
                            view.sourceReports().get("raster").entries().getLast();
                    assertEquals("SOURCE_LIMIT_EXCEEDED", terminal.code());
                    assertEquals("decodedIntermediateBytes", terminal.context().get("limit"));
                    assertEquals("8", terminal.context().get("requested"));
                    view.close();
                });
    }

    @Test
    void cancellationDiscardsPixelsAndPreservesWarningsBeforeOneTerminalDiagnostic()
            throws Exception {
        BlockingRasterSource source = new BlockingRasterSource();
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<MapLayerBinding> bindingReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = configuredProjectedView();
                    MapLayerBinding binding =
                            MapLayerBinding.borrowedRaster("raster", "raster", source);
                    view.setLayerBindings(List.of(binding));
                    viewReference.set(view);
                    bindingReference.set(binding);
                });
        CountDownLatch finished = new CountDownLatch(1);
        SwingUtilities.invokeLater(
                () -> {
                    paint(viewReference.get());
                    finished.countDown();
                });
        assertTrue(source.entered.await(5, TimeUnit.SECONDS));
        assertTrue(bindingReference.get().cancelCurrentOperation());
        source.release.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(
                () -> {
                    List<String> codes =
                            viewReference.get().sourceReports().get("raster").entries().stream()
                                    .map(SourceDiagnostic::code)
                                    .toList();
                    assertEquals(List.of("TEST_RASTER_WARNING", "SOURCE_CANCELLED"), codes);
                    assertFalse(bindingReference.get().cancelCurrentOperation());
                    viewReference.get().close();
                });
    }

    @Test
    void successfulPublicationWinsAgainstALateCancellation() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source = projectedSource("success", 1, 1, PROJECTED_BOUNDS);
                    source.warning = true;
                    MapLayerBinding binding =
                            MapLayerBinding.borrowedRaster("raster", "raster", source);
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(List.of(binding));

                    paint(view);

                    assertFalse(binding.cancelCurrentOperation());
                    assertEquals(
                            List.of("TEST_RASTER_WARNING"),
                            view.sourceReports().get("raster").entries().stream()
                                    .map(SourceDiagnostic::code)
                                    .toList());
                    view.close();
                });
    }

    @Test
    void ordinaryFailureWinsAgainstALateCancellation() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource source = projectedSource("failure", 1, 1, PROJECTED_BOUNDS);
                    source.failureCode = "TEST_RASTER_FAILED";
                    MapLayerBinding binding =
                            MapLayerBinding.borrowedRaster("raster", "raster", source);
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(List.of(binding));

                    paint(view);

                    assertFalse(binding.cancelCurrentOperation());
                    assertEquals(
                            List.of("TEST_RASTER_FAILED"),
                            view.sourceReports().get("raster").entries().stream()
                                    .map(SourceDiagnostic::code)
                                    .toList());
                    view.close();
                });
    }

    @Test
    void cancellationWinsOverAnOrdinaryReadFailureAndPublishesOneTerminalCode() throws Exception {
        BlockingFailureRasterSource source = new BlockingFailureRasterSource();
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<MapLayerBinding> bindingReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = configuredProjectedView();
                    MapLayerBinding binding =
                            MapLayerBinding.borrowedRaster("raster", "raster", source);
                    view.setLayerBindings(List.of(binding));
                    viewReference.set(view);
                    bindingReference.set(binding);
                });
        CountDownLatch finished = new CountDownLatch(1);
        SwingUtilities.invokeLater(
                () -> {
                    paint(viewReference.get());
                    finished.countDown();
                });
        assertTrue(source.entered.await(5, TimeUnit.SECONDS));
        assertTrue(bindingReference.get().cancelCurrentOperation());
        source.release.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(
                () -> {
                    List<SourceDiagnostic> entries =
                            viewReference.get().sourceReports().get("raster").entries();
                    assertEquals(
                            List.of("TEST_RASTER_WARNING", "SOURCE_CANCELLED"),
                            entries.stream().map(SourceDiagnostic::code).toList());
                    assertEquals(
                            1,
                            entries.stream()
                                    .filter(
                                            diagnostic ->
                                                    diagnostic.severity()
                                                            == DiagnosticSeverity.ERROR)
                                    .count());
                    viewReference.get().close();
                });
    }

    @Test
    void rejectedReplacementLeavesOwnedRasterInstalledAndSuccessfulReplacementClosesIt()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    TestRasterSource first = projectedSource("first", 1, 1, PROJECTED_BOUNDS);
                    TestRasterSource next = projectedSource("next", 1, 1, PROJECTED_BOUNDS);
                    MapLayerBinding installed =
                            MapLayerBinding.ownedRaster("first", "first", first);
                    MapLayerBinding replacement =
                            MapLayerBinding.borrowedRaster("next", "next", next);
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(List.of(installed));

                    assertThrows(
                            IllegalArgumentException.class,
                            () -> view.setLayerBindings(List.of(installed, installed)));
                    assertEquals(List.of(installed), view.layerBindings());
                    assertFalse(first.isClosed());

                    view.setLayerBindings(List.of(replacement));
                    assertTrue(first.isClosed());
                    assertEquals(1, first.closeCount);
                    assertFalse(next.isClosed());
                    view.close();
                    assertFalse(next.isClosed());
                });
    }

    @Test
    void ownedRasterSourcesCloseOnceInReverseOrderWhileBorrowedSourcesRemainOpen()
            throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<String> closeOrder = new ArrayList<>();
                    TestRasterSource borrowed = projectedSource("borrowed", 1, 1, PROJECTED_BOUNDS);
                    borrowed.closeOrder = closeOrder;
                    TestRasterSource first = projectedSource("first", 1, 1, PROJECTED_BOUNDS);
                    first.closeOrder = closeOrder;
                    TestRasterSource second = projectedSource("second", 1, 1, PROJECTED_BOUNDS);
                    second.closeOrder = closeOrder;
                    MapView view = configuredProjectedView();
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedRaster(
                                            "borrowed", "borrowed", borrowed),
                                    MapLayerBinding.ownedRaster("first", "first", first),
                                    MapLayerBinding.ownedRaster("second", "second", second)));

                    view.close();

                    assertEquals(List.of("second", "first"), closeOrder);
                    assertFalse(borrowed.isClosed());
                    assertTrue(first.isClosed());
                    assertTrue(second.isClosed());
                    assertEquals(1, first.closeCount);
                    assertEquals(1, second.closeCount);
                    view.close();
                    assertEquals(1, first.closeCount);
                    assertEquals(1, second.closeCount);
                });
    }

    private static MapView configuredProjectedView() {
        MapView view = TestMapViews.identity();
        view.setSize(SIZE, SIZE);
        view.setViewport(new MapViewport(SIZE, SIZE, 0, 0, 1));
        return view;
    }

    @SuppressWarnings("deprecation")
    private static InMemoryLayer pointLayer() {
        Feature point =
                new Feature(
                        "point",
                        "point",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        FeatureStyle.point(Rgba.rgb(0, 0, 255), 30));
        return new InMemoryLayer("vector", "vector", List.of(point));
    }

    private static BufferedImage paint(MapView view) {
        return paint(view, SIZE, SIZE);
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

    private static void move(MapView view, int x, int y) {
        view.dispatchEvent(
                new MouseEvent(
                        view, MouseEvent.MOUSE_MOVED, 1L, 0, x, y, 0, false, MouseEvent.NOBUTTON));
    }

    private static void assertColorNear(Color expected, Color actual, int tolerance) {
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= tolerance);
        assertTrue(Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance);
        assertTrue(Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance);
    }

    private static void assertCrsFailure(MapView view, TestRasterSource source, String code) {
        CrsException failure =
                assertThrows(
                        CrsException.class,
                        () ->
                                view.setLayerBindings(
                                        List.of(
                                                MapLayerBinding.borrowedRaster(
                                                        source.metadata().identity().id(),
                                                        source.metadata().identity().displayName(),
                                                        source))));
        assertEquals(code, failure.problem().code());
        assertTrue(view.layerBindings().isEmpty());
    }

    private static TestRasterSource projectedSource(
            String id, int width, int height, Envelope bounds) {
        return source(
                id,
                width,
                height,
                Optional.of(bounds),
                recognized(CrsDefinitions.EPSG_3857),
                RasterSourceLimits.LEVEL_1);
    }

    private static TestRasterSource source(
            String id,
            int width,
            int height,
            Optional<Envelope> bounds,
            Optional<CrsMetadata> crs,
            RasterSourceLimits limits) {
        return new TestRasterSource(
                new RasterSourceMetadata(new SourceIdentity(id, id), width, height, bounds, crs),
                limits);
    }

    private static Optional<CrsMetadata> recognized(CrsDefinition definition) {
        return Optional.of(CrsMetadata.recognized(definition, Optional.empty(), Optional.empty()));
    }

    private static DiagnosticReport warning(String sourceId) {
        SourceDiagnostic warning =
                new SourceDiagnostic(
                        "TEST_RASTER_WARNING",
                        DiagnosticSeverity.WARNING,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        "Test raster warning",
                        Map.of());
        return new DiagnosticReport(List.of(warning), 0);
    }

    private static class TestRasterSource implements RasterSource {
        private final RasterSourceMetadata metadata;
        private final RasterSourceLimits limits;
        private int readCount;
        private int closeCount;
        private RasterRequest lastRequest;
        private boolean warning;
        private String failureCode;
        private Integer solidRgba;
        private boolean splitOutput;
        private boolean closed;
        private List<String> closeOrder;

        private TestRasterSource(RasterSourceMetadata metadata, RasterSourceLimits limits) {
            this.metadata = metadata;
            this.limits = limits;
        }

        @Override
        public RasterSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public RasterSourceLimits limits() {
            return limits;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            if (closed) {
                throw new IllegalStateException("source is closed");
            }
            readCount++;
            lastRequest = request;
            if (failureCode != null) {
                throw failure(metadata.identity().id(), failureCode, false);
            }
            RgbaPixelBuffer.Builder pixels =
                    RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
            for (int row = 0; row < request.outputHeight(); row++) {
                for (int column = 0; column < request.outputWidth(); column++) {
                    int absoluteColumn = request.sourceWindow().column() + column;
                    int absoluteRow = request.sourceWindow().row() + row;
                    pixels.setRgba(
                            column,
                            row,
                            solidRgba != null
                                    ? solidRgba
                                    : splitOutput
                                            ? column < request.outputWidth() / 2
                                                    ? 0xff00_00ff
                                                    : 0x0000_ffff
                                            : testPixel(absoluteColumn, absoluteRow));
                }
            }
            return new RasterRead(
                    request.sourceWindow(),
                    pixels.build(),
                    warning ? warning(metadata.identity().id()) : DiagnosticReport.empty());
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;
            closeCount++;
            if (closeOrder != null) {
                closeOrder.add(metadata.identity().id());
            }
        }

        private static int testPixel(int column, int row) {
            if (column == 1 && row == 1) {
                return 0xff00_0080;
            }
            if (column == 2 && row == 1) {
                return 0x00ff_00ff;
            }
            if (column == 1 && row == 2) {
                return 0x0000_ffff;
            }
            return 0xffff_ffff;
        }
    }

    private static final class RecordingRepaintManager extends RepaintManager {
        private int dirtyRegions;

        @Override
        public void addDirtyRegion(JComponent component, int x, int y, int width, int height) {
            dirtyRegions++;
            super.addDirtyRegion(component, x, y, width, height);
        }
    }

    private static final class BlockingRasterSource extends TestRasterSource {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingRasterSource() {
            super(
                    new RasterSourceMetadata(
                            new SourceIdentity("blocking", "blocking"),
                            1,
                            1,
                            Optional.of(new Envelope(-1, -1, 1, 1)),
                            recognized(CrsDefinitions.EPSG_3857)),
                    RasterSourceLimits.LEVEL_1);
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
            return new RasterRead(
                    request.sourceWindow(),
                    RgbaPixelBuffer.copyOf(1, 1, new int[] {0xff00_00ff}),
                    warning(metadata().identity().id()));
        }
    }

    private static final class BlockingFailureRasterSource extends TestRasterSource {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        private BlockingFailureRasterSource() {
            super(
                    new RasterSourceMetadata(
                            new SourceIdentity("blocking-failure", "blocking-failure"),
                            1,
                            1,
                            Optional.of(new Envelope(-1, -1, 1, 1)),
                            recognized(CrsDefinitions.EPSG_3857)),
                    RasterSourceLimits.LEVEL_1);
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            entered.countDown();
            try {
                assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
            throw failure(metadata().identity().id(), "TEST_RASTER_FAILED", true);
        }
    }

    private static SourceException failure(String sourceId, String code, boolean includeWarning) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        "Test raster failure",
                        Map.of());
        List<SourceDiagnostic> entries = new ArrayList<>();
        if (includeWarning) {
            entries.add(warning(sourceId).entries().getFirst());
        }
        entries.add(terminal);
        return new SourceException(new DiagnosticReport(entries, 0), terminal);
    }
}
