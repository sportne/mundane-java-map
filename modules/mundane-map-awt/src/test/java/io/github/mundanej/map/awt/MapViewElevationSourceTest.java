package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.PackedElevationGrid;
import java.awt.Color;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MapViewElevationSourceTest {
    @Test
    void rendersThroughNormalLayerOrderAndClipsToExactSampleDomain() throws Exception {
        CountingSource source = source(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        AtomicReference<BufferedImage> rendered = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setBackground(Color.WHITE);
                    view.setViewport(new MapViewport(100, 100, 1, 1, 0.04));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedElevation(
                                            "terrain", "Terrain", source, solidStyle())));
                    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                    view.paint(image.createGraphics());
                    rendered.set(image);
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> view.setSelection(new FeatureSelection("terrain", "sample")));
                    assertTrue(view.hitTest(50, 50, 5).hits().isEmpty());
                    view.close();
                });
        assertTrue(source.samples.get() > 0);
        assertEquals(0xffff0000, rendered.get().getRGB(50, 50));
        assertNotEquals(0xffff0000, rendered.get().getRGB(15, 50));
        assertFalse(source.isClosed());
        source.close();
    }

    @Test
    void fitDoesNotSampleAndOpacityZeroPreservesTheSource() throws Exception {
        CountingSource source = source(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedElevation(
                                            "terrain", "Terrain", source, solidStyle())));
                    view.fitToData(5);
                    assertEquals(0, source.samples.get());
                    view.setRasterRenderOptions(
                            "terrain", new RasterRenderOptions(RasterInterpolation.BILINEAR, 0));
                    view.paint(
                            new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                                    .createGraphics());
                    assertEquals(0, source.samples.get());
                    view.close();
                });
        assertFalse(source.isClosed());
        source.close();
    }

    @Test
    void replacesStyleAndOptionsOnlyOnEdtAndValidatesUnitsTransactionally() throws Exception {
        CountingSource source = source(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        MapLayerBinding binding =
                MapLayerBinding.borrowedElevation("terrain", "Terrain", source, solidStyle());
        AtomicReference<MapView> view = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView created = view(CrsDefinitions.EPSG_3857);
                    created.setLayerBindings(List.of(binding));
                    created.setElevationRasterStyle(
                            "terrain", solidStyle().withNoDataColor(Rgba.rgb(1, 2, 3)));
                    created.setRasterRenderOptions(
                            "terrain", new RasterRenderOptions(RasterInterpolation.BILINEAR, 0.5));
                    assertEquals(List.of(binding), created.layerBindings());
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    created.setElevationRasterStyle(
                                            "terrain", style(ElevationUnit.US_SURVEY_FOOT)));
                    assertEquals(List.of(binding), created.layerBindings());
                    view.set(created);
                });
        assertThrows(
                IllegalStateException.class,
                () -> view.get().setElevationRasterStyle("terrain", solidStyle()));
        EventQueue.invokeAndWait(view.get()::close);
        source.close();
    }

    @Test
    void validatesUnitAndCrsBeforeAttachment() throws Exception {
        CountingSource source = source(CrsDefinitions.EPSG_4326, ElevationUnit.METRE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MapLayerBinding.borrowedElevation(
                                "bad", "Bad", source, style(ElevationUnit.INTERNATIONAL_FOOT)));
        AtomicReference<Throwable> failure = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    try {
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.borrowedElevation(
                                                "terrain", "Terrain", source, solidStyle())));
                    } catch (Throwable caught) {
                        failure.set(caught);
                    } finally {
                        view.close();
                    }
                });
        assertTrue(failure.get() instanceof CrsException);
        assertEquals(
                "CRS_RASTER_WARP_UNSUPPORTED", ((CrsException) failure.get()).problem().code());
        assertFalse(source.isClosed());
        source.close();
    }

    @Test
    void rejectsUnknownAndFabricatedRecognizedCrsDefinitions() throws Exception {
        CrsMetadata unknown = CrsMetadata.unknown(Optional.of("LOCAL:TERRAIN"), Optional.empty());
        CountingSource unknownSource = source("unknown", unknown, ElevationUnit.METRE);
        CrsDefinition standard = CrsDefinitions.EPSG_3857;
        CrsDefinition fabricated =
                new CrsDefinition(
                        standard.canonicalIdentifier(),
                        standard.kind(),
                        standard.xAxis(),
                        standard.yAxis(),
                        new Envelope(-10, -10, 10, 10));
        CountingSource fabricatedSource =
                source(
                        "fabricated",
                        CrsMetadata.recognized(
                                fabricated, Optional.of("EPSG:3857"), Optional.empty()),
                        ElevationUnit.METRE);
        for (var candidate :
                List.of(
                        new Object[] {unknownSource, "CRS_DEFINITION_UNKNOWN"},
                        new Object[] {fabricatedSource, "CRS_DEFINITION_MISMATCH"})) {
            AtomicReference<Throwable> failure = new AtomicReference<>();
            EventQueue.invokeAndWait(
                    () -> {
                        MapView view = view(CrsDefinitions.EPSG_3857);
                        try {
                            view.setLayerBindings(
                                    List.of(
                                            MapLayerBinding.borrowedElevation(
                                                    "terrain",
                                                    "Terrain",
                                                    (ElevationSource) candidate[0],
                                                    solidStyle())));
                        } catch (Throwable caught) {
                            failure.set(caught);
                        } finally {
                            view.close();
                        }
                    });
            assertEquals(candidate[1], ((CrsException) failure.get()).problem().code());
        }
        unknownSource.close();
        fabricatedSource.close();
    }

    @Test
    void hostLivePathPreflightRejectsSevenBytesBeforeSamplingAndPublishesReport() throws Exception {
        CountingSource source = source(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        RasterRequestLimits limits = new RasterRequestLimits(100, 100, 100, 7, 100, 8);
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setViewport(new MapViewport(100, 100, 1, 1, 0.01));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedElevation(
                                            "terrain",
                                            "Terrain",
                                            source,
                                            solidStyle(),
                                            RasterRenderOptions.defaults(),
                                            limits)));
                    view.paint(
                            new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                                    .createGraphics());
                    assertEquals(0, source.samples.get());
                    assertEquals(
                            "SOURCE_LIMIT_EXCEEDED",
                            view.sourceReports().get("terrain").entries().getLast().code());
                    view.close();
                });
        source.close();
    }

    @Test
    void hostLivePathAcceptsEightAndNineIntermediateAndFourAndFivePublishedBytes()
            throws Exception {
        for (long intermediate : new long[] {8, 9}) {
            CountingSource source =
                    source(
                            "intermediate-" + intermediate,
                            CrsDefinitions.EPSG_3857,
                            ElevationUnit.METRE);
            EventQueue.invokeAndWait(
                    () -> {
                        MapView view = view(CrsDefinitions.EPSG_3857);
                        view.setViewport(new MapViewport(100, 100, 1, 1, 0.01));
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.borrowedElevation(
                                                "terrain",
                                                "Terrain",
                                                source,
                                                solidStyle(),
                                                RasterRenderOptions.defaults(),
                                                new RasterRequestLimits(
                                                        100, 100, 100, intermediate, 4, 8))));
                        view.paint(
                                new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                                        .createGraphics());
                        assertTrue(source.samples.get() > 0);
                        assertTrue(view.sourceReports().isEmpty());
                        view.close();
                    });
            source.close();
        }
        CountingSource rejected =
                source("published-3", CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setViewport(new MapViewport(100, 100, 1, 1, 0.01));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedElevation(
                                            "terrain",
                                            "Terrain",
                                            rejected,
                                            solidStyle(),
                                            RasterRenderOptions.defaults(),
                                            new RasterRequestLimits(100, 100, 100, 100, 3, 8))));
                    view.paint(
                            new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                                    .createGraphics());
                    assertEquals(0, rejected.samples.get());
                    assertEquals(
                            "SOURCE_LIMIT_EXCEEDED",
                            view.sourceReports().get("terrain").entries().getLast().code());
                    view.close();
                });
        rejected.close();
        for (long published : new long[] {4, 5}) {
            CountingSource source =
                    source("published-" + published, CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
            EventQueue.invokeAndWait(
                    () -> {
                        MapView view = view(CrsDefinitions.EPSG_3857);
                        view.setViewport(new MapViewport(100, 100, 1, 1, 0.01));
                        view.setLayerBindings(
                                List.of(
                                        MapLayerBinding.borrowedElevation(
                                                "terrain",
                                                "Terrain",
                                                source,
                                                solidStyle(),
                                                RasterRenderOptions.defaults(),
                                                new RasterRequestLimits(
                                                        100, 100, 100, 8, published, 8))));
                        view.paint(
                                new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB)
                                        .createGraphics());
                        assertTrue(source.samples.get() > 0);
                        view.close();
                    });
            source.close();
        }
    }

    @Test
    void ownedAndBorrowedSourcesFollowExistingReverseCloseRules() throws Exception {
        CountingSource borrowed = source(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        CountingSource owned = source("owned", CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedElevation(
                                            "borrowed", "Borrowed", borrowed, solidStyle()),
                                    MapLayerBinding.ownedElevation(
                                            "owned", "Owned", owned, solidStyle())));
                    view.close();
                });
        assertFalse(borrowed.isClosed());
        assertTrue(owned.isClosed());
        borrowed.close();
    }

    @Test
    void cancellationPublishesNoPartialImageAndAFollowingPaintRecoversTheReport() throws Exception {
        CancellingOnceSource source =
                new CancellingOnceSource(
                        source("cancel-once", CrsDefinitions.EPSG_3857, ElevationUnit.METRE));
        AtomicReference<MapLayerBinding> binding = new AtomicReference<>();
        AtomicReference<BufferedImage> first = new AtomicReference<>();
        AtomicReference<BufferedImage> second = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setBackground(Color.WHITE);
                    view.setViewport(new MapViewport(100, 100, 1, 1, 0.01));
                    MapLayerBinding installed =
                            MapLayerBinding.borrowedElevation(
                                    "terrain", "Terrain", source, solidStyle());
                    binding.set(installed);
                    source.cancel = installed::cancelCurrentOperation;
                    view.setLayerBindings(List.of(installed));
                    first.set(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB));
                    view.paint(first.get().createGraphics());
                    assertEquals(
                            "SOURCE_CANCELLED",
                            view.sourceReports().get("terrain").entries().getLast().code());
                    second.set(new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB));
                    view.paint(second.get().createGraphics());
                    assertTrue(view.sourceReports().isEmpty());
                    view.close();
                });
        assertNotEquals(0xffff0000, first.get().getRGB(50, 50));
        assertEquals(0xffff0000, second.get().getRGB(50, 50));
        assertFalse(source.isClosed());
        source.close();
    }

    @Test
    void argbConversionAndFinalOperationArbitrationHonorCancellation() {
        RgbaPixelBuffer.Builder pixels = RgbaPixelBuffer.builder(100, 100);
        for (int row = 0; row < 100; row++) {
            for (int column = 0; column < 100; column++) {
                pixels.setRgba(column, row, 0xff0000ff);
            }
        }
        AtomicInteger conversionPolls = new AtomicInteger();
        assertThrows(
                IllegalStateException.class,
                () ->
                        AwtRgbaPixels.toBufferedImage(
                                pixels.build(),
                                () -> {
                                    if (conversionPolls.incrementAndGet() == 4) {
                                        throw new IllegalStateException("cancelled");
                                    }
                                }));
        assertEquals(4, conversionPolls.get());

        MapLayerBinding.Operation operation = new MapLayerBinding.Operation();
        assertTrue(operation.cancel());
        assertEquals(MapLayerBinding.Phase.CANCELLED, operation.finish(true));
    }

    @Test
    void aFailedElevationLayerDoesNotPreventAFollowingLayerFromRendering() throws Exception {
        CountingSource failed = source("failed", CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        CountingSource visible = source("visible", CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        AtomicReference<BufferedImage> rendered = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setViewport(new MapViewport(100, 100, 1, 1, 0.01));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.borrowedElevation(
                                            "failed",
                                            "Failed",
                                            failed,
                                            solidStyle(),
                                            RasterRenderOptions.defaults(),
                                            new RasterRequestLimits(100, 100, 100, 7, 100, 8)),
                                    MapLayerBinding.borrowedElevation(
                                            "visible", "Visible", visible, blueStyle())));
                    BufferedImage image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                    view.paint(image.createGraphics());
                    rendered.set(image);
                    assertTrue(view.sourceReports().containsKey("failed"));
                    view.close();
                });
        assertEquals(0xff0000ff, rendered.get().getRGB(50, 50));
        failed.close();
        visible.close();
    }

    @Test
    void ownedElevationCloseFailuresKeepReversePrimaryAndSuppressedOrder() throws Exception {
        List<String> order = new ArrayList<>();
        RuntimeException firstFailure = new RuntimeException("first");
        RuntimeException secondFailure = new RuntimeException("second");
        CloseFailSource first =
                new CloseFailSource(
                        source("first", CrsDefinitions.EPSG_3857, ElevationUnit.METRE),
                        "first",
                        order,
                        firstFailure);
        CloseFailSource second =
                new CloseFailSource(
                        source("second", CrsDefinitions.EPSG_3857, ElevationUnit.METRE),
                        "second",
                        order,
                        secondFailure);
        AtomicReference<Throwable> observed = new AtomicReference<>();
        EventQueue.invokeAndWait(
                () -> {
                    MapView view = view(CrsDefinitions.EPSG_3857);
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.ownedElevation(
                                            "first", "First", first, solidStyle()),
                                    MapLayerBinding.ownedElevation(
                                            "second", "Second", second, solidStyle())));
                    try {
                        view.close();
                    } catch (Throwable failure) {
                        observed.set(failure);
                    }
                });
        assertEquals(List.of("second", "first"), order);
        assertTrue(observed.get() == secondFailure);
        assertEquals(List.of(firstFailure), List.of(observed.get().getSuppressed()));
    }

    private static MapView view(io.github.mundanej.map.api.CrsDefinition crs) {
        MapView view = new MapView(CrsRegistry.level1(), crs, crs);
        view.setSize(100, 100);
        return view;
    }

    private static ElevationRasterStyle solidStyle() {
        return style(ElevationUnit.METRE);
    }

    private static ElevationRasterStyle blueStyle() {
        return ElevationRasterStyle.of(
                new ElevationColorRamp(
                        ElevationUnit.METRE,
                        List.of(
                                new ElevationColorStop(0, Rgba.rgb(0, 0, 255)),
                                new ElevationColorStop(1, Rgba.rgb(0, 0, 255)))));
    }

    private static ElevationRasterStyle style(ElevationUnit unit) {
        return ElevationRasterStyle.of(
                new ElevationColorRamp(
                        unit,
                        List.of(
                                new ElevationColorStop(0, Rgba.rgb(255, 0, 0)),
                                new ElevationColorStop(1, Rgba.rgb(255, 0, 0)))));
    }

    private static CountingSource source(
            io.github.mundanej.map.api.CrsDefinition crs, ElevationUnit unit) {
        return source("elevation", crs, unit);
    }

    private static CountingSource source(
            String id, io.github.mundanej.map.api.CrsDefinition crs, ElevationUnit unit) {
        return source(
                id,
                CrsMetadata.recognized(
                        crs, Optional.of(crs.canonicalIdentifier()), Optional.empty()),
                unit);
    }

    private static CountingSource source(String id, CrsMetadata crs, ElevationUnit unit) {
        ElevationSourceMetadata metadata =
                new ElevationSourceMetadata(
                        new SourceIdentity(id, id), 3, 3, new Envelope(0, 0, 2, 2), crs, unit);
        return new CountingSource(
                PackedElevationGrid.copyOf(metadata, new double[9], new BitSet()));
    }

    private static final class CancellingOnceSource implements ElevationSource {
        private final ElevationSource delegate;
        private Runnable cancel;

        private CancellingOnceSource(ElevationSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public ElevationSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public ElevationSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public OptionalDouble sample(int column, int row) {
            if (cancel != null) {
                Runnable action = cancel;
                cancel = null;
                action.run();
            }
            return delegate.sample(column, row);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class CloseFailSource implements ElevationSource {
        private final ElevationSource delegate;
        private final String name;
        private final List<String> order;
        private final RuntimeException failure;

        private CloseFailSource(
                ElevationSource delegate,
                String name,
                List<String> order,
                RuntimeException failure) {
            this.delegate = delegate;
            this.name = name;
            this.order = order;
            this.failure = failure;
        }

        @Override
        public ElevationSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public ElevationSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public OptionalDouble sample(int column, int row) {
            return delegate.sample(column, row);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            order.add(name);
            delegate.close();
            throw failure;
        }
    }

    private static final class CountingSource implements ElevationSource {
        private final ElevationSource delegate;
        private final AtomicInteger samples = new AtomicInteger();

        private CountingSource(ElevationSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public ElevationSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public ElevationSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public OptionalDouble sample(int column, int row) {
            samples.incrementAndGet();
            return delegate.sample(column, row);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }
}
