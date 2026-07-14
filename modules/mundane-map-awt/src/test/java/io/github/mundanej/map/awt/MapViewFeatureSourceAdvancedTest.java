package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.MapHit;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.event.MouseEvent;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.BufferedImageOp;
import java.awt.image.ImageObserver;
import java.awt.image.RenderedImage;
import java.awt.image.renderable.RenderableImage;
import java.text.AttributedCharacterIterator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewFeatureSourceAdvancedTest {
    private static final int SIZE = 100;

    @Test
    void cancellationWinsOnlyThePublishedCurrentOperationAndDoesNotLeakForward() throws Exception {
        BlockingSource source = new BlockingSource();
        AtomicReference<MapView> viewReference = new AtomicReference<>();
        AtomicReference<MapLayerBinding> bindingReference = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = configuredView();
                    MapLayerBinding binding = binding("blocking", source, plainLine());
                    view.setLayerBindings(List.of(binding));
                    viewReference.set(view);
                    bindingReference.set(binding);
                });

        CountDownLatch finished = new CountDownLatch(1);
        AtomicReference<Throwable> unexpected = new AtomicReference<>();
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        viewReference.get().hitTest(50.0, 50.0, 0.0);
                    } catch (Throwable failure) {
                        unexpected.set(failure);
                    } finally {
                        finished.countDown();
                    }
                });
        assertTrue(source.entered.await(5, TimeUnit.SECONDS));
        assertTrue(bindingReference.get().cancelCurrentOperation());
        assertTrue(bindingReference.get().cancelCurrentOperation());
        source.release.countDown();
        assertTrue(finished.await(5, TimeUnit.SECONDS));
        assertEquals(null, unexpected.get());

        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = viewReference.get();
                    assertEquals(
                            "SOURCE_CANCELLED",
                            view.sourceReports().get("blocking").entries().getLast().code());
                    assertFalse(bindingReference.get().cancelCurrentOperation());
                    source.block = false;
                    assertTrue(view.hitTest(50.0, 50.0, 0.0).topmost().isEmpty());
                    assertTrue(view.sourceReports().isEmpty());
                    assertFalse(bindingReference.get().cancelCurrentOperation());
                    view.close();
                });
    }

    @Test
    void successfulTerminalArbitrationRejectsALateCancelAndFutureTokenStartsClean() {
        EmptySource source = new EmptySource("arbitration");
        MapLayerBinding binding = binding("arbitration", source, plainLine());
        Object owner = new Object();
        binding.claim(owner);
        MapLayerBinding.Operation first = binding.beginOperation();
        assertEquals(MapLayerBinding.Phase.SUCCEEDED, first.finish(true));
        assertFalse(binding.cancelCurrentOperation());
        binding.endOperation(first);
        MapLayerBinding.Operation second = binding.beginOperation();
        assertFalse(second.token().isCancellationRequested());
        assertEquals(MapLayerBinding.Phase.SUCCEEDED, second.finish(true));
        binding.endOperation(second);
        binding.release(owner);
    }

    @Test
    void failedTopSourceFallsThroughToLowerHoverAndRecoveryRequiresANewMove() throws Exception {
        FlakyPointSource source = new FlakyPointSource();
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = configuredView();
                    Feature lower =
                            new Feature(
                                    "lower",
                                    "",
                                    new PointGeometry(new Coordinate(0.0, 0.0)),
                                    Map.of(),
                                    marker(Rgba.rgb(120, 80, 30), 7.0));
                    view.setLayerBindings(
                            List.of(
                                    MapLayerBinding.snapshot(
                                            new InMemoryLayer(
                                                    "lower-layer", "lower", List.of(lower))),
                                    binding("flaky", source, plainLine())));

                    move(view, 50, 50);
                    assertEquals("point", view.hover().orElseThrow().featureId());
                    source.fail = true;
                    paint(view);
                    assertEquals("lower", view.hover().orElseThrow().featureId());

                    source.fail = false;
                    paint(view);
                    assertEquals("lower", view.hover().orElseThrow().featureId());
                    move(view, 50, 50);
                    assertEquals("point", view.hover().orElseThrow().featureId());
                    view.close();
                });
    }

    @Test
    void paintDisposesChildGraphicsBeforeReportingAndKeepsCaptureFailurePrimary() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = configuredView();
                    view.setLayerBindings(
                            List.of(
                                    binding("reporting", new ReportingSource(), plainLine()),
                                    binding("runtime", new RuntimeFailureSource(), plainLine())));
                    BufferedImage image =
                            new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
                    TrackingGraphics2D graphics = TrackingGraphics2D.root(image.createGraphics());
                    view.addMapSourceReportListener(
                            ignored -> {
                                assertTrue(graphics.childDisposed());
                                throw new IllegalStateException("listener");
                            });

                    try {
                        IllegalStateException failure =
                                assertThrows(
                                        IllegalStateException.class,
                                        () -> view.paintComponent(graphics));
                        assertEquals("runtime", failure.getMessage());
                        assertEquals(1, failure.getSuppressed().length);
                        assertEquals("listener", failure.getSuppressed()[0].getMessage());
                        assertTrue(graphics.childDisposed());
                    } finally {
                        graphics.dispose();
                    }
                });
    }

    @Test
    void ownedBindingsCloseInReverseOrderAndAggregateEveryFailure() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    List<String> order = new ArrayList<>();
                    ClosingSource first = new ClosingSource("first", order, true);
                    ClosingSource second = new ClosingSource("second", order, false);
                    ClosingSource third = new ClosingSource("third", order, true);
                    MapView view = configuredView();
                    view.setLayerBindings(
                            List.of(
                                    ownedBinding("first", first),
                                    ownedBinding("second", second),
                                    ownedBinding("third", third)));

                    SourceException failure =
                            assertThrows(
                                    SourceException.class, () -> view.setLayerBindings(List.of()));
                    assertEquals("TEST_CLOSE_THIRD", failure.terminal().code());
                    assertEquals(List.of("third", "second", "first"), order);
                    assertEquals(1, failure.getSuppressed().length);
                    assertEquals(
                            "TEST_CLOSE_FIRST",
                            ((SourceException) failure.getSuppressed()[0]).terminal().code());
                    assertTrue(view.layerBindings().isEmpty());
                    assertTrue(view.sourceReports().isEmpty());
                });
    }

    @Test
    void multipartLinesMarkEveryPartAndMultipolygonHolesSurviveSelectionOverlay() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MultiLineStringGeometry lines =
                            MultiLineStringGeometry.ofParts(
                                    List.of(
                                            CoordinateSequence.of(-40.0, -25.0, -10.0, -25.0),
                                            CoordinateSequence.of(10.0, 25.0, 40.0, 25.0)));
                    PolygonGeometry left =
                            new PolygonGeometry(
                                    CoordinateSequence.of(
                                            -40.0, -15.0, -5.0, -15.0, -5.0, 15.0, -40.0, 15.0,
                                            -40.0, -15.0),
                                    List.of(
                                            CoordinateSequence.of(
                                                    -30.0, -5.0, -15.0, -5.0, -15.0, 5.0, -30.0,
                                                    5.0, -30.0, -5.0)));
                    PolygonGeometry right =
                            new PolygonGeometry(
                                    CoordinateSequence.of(
                                            5.0, -15.0, 40.0, -15.0, 40.0, 15.0, 5.0, 15.0, 5.0,
                                            -15.0));
                    MultiPolygonGeometry polygons =
                            MultiPolygonGeometry.ofPolygons(List.of(left, right));
                    InMemoryFeatureSource source =
                            InMemoryFeatureSource.open(
                                    new SourceIdentity("multipart", "multipart"),
                                    List.of(
                                            new FeatureRecord("lines", "", lines, Map.of()),
                                            new FeatureRecord("polygons", "", polygons, Map.of()),
                                            new FeatureRecord(
                                                    "top",
                                                    "",
                                                    new PointGeometry(new Coordinate(-35.0, 0.0)),
                                                    Map.of())),
                                    Optional.empty(),
                                    Optional.of(
                                            CrsMetadata.recognized(
                                                    CrsDefinitions.EPSG_3857,
                                                    Optional.empty(),
                                                    Optional.empty())),
                                    FeatureSourceLimits.LEVEL_1);
                    VectorMarkerSymbol endpoint = marker(Rgba.rgb(220, 30, 30), 9.0);
                    SolidLineSymbol line =
                            SolidLineSymbol.of(
                                    stroke(Rgba.rgb(20, 70, 190), 3.0),
                                    Optional.empty(),
                                    Optional.of(endpoint),
                                    1.0);
                    MapView view = configuredView();
                    view.setLayerBindings(List.of(binding("multipart", source, line)));

                    BufferedImage base = paint(view);
                    assertRed(base, 40, 75);
                    assertRed(base, 90, 25);
                    assertEquals(
                            "lines",
                            view.hitTest(40.0, 75.0, 0.0).topmost().orElseThrow().featureId());
                    assertEquals(
                            "lines",
                            view.hitTest(90.0, 25.0, 0.0).topmost().orElseThrow().featureId());
                    assertGreen(base, 15, 40);
                    assertEquals(Color.WHITE.getRGB(), base.getRGB(30, 50));
                    assertGreen(base, 70, 50);
                    assertEquals(
                            List.of("top", "polygons"),
                            view.hitTest(15.0, 50.0, 0.0).hits().stream()
                                    .map(MapHit::featureId)
                                    .toList());
                    assertTrue(view.hitTest(30.0, 50.0, 0.0).topmost().isEmpty());

                    view.setSelection(new FeatureSelection("multipart", "polygons"));
                    BufferedImage selected = paint(view);
                    assertEquals(Color.WHITE.getRGB(), selected.getRGB(30, 50));
                    assertEquals(
                            "polygons",
                            view.hitTest(70.0, 50.0, 0.0).topmost().orElseThrow().featureId());
                    view.close();
                });
    }

    private static MapView configuredView() {
        MapView view = TestMapViews.identity();
        view.setSize(SIZE, SIZE);
        view.setViewport(new MapViewport(SIZE, SIZE, 0.0, 0.0, 1.0));
        return view;
    }

    private static MapLayerBinding binding(String id, FeatureSource source, SolidLineSymbol line) {
        return MapLayerBinding.borrowedFeature(
                id,
                id,
                source,
                marker(Rgba.rgb(40, 40, 40), 9.0),
                line,
                SolidFillSymbol.of(Rgba.rgb(30, 170, 70), 1.0));
    }

    private static MapLayerBinding ownedBinding(String id, FeatureSource source) {
        SolidLineSymbol line = plainLine();
        return MapLayerBinding.ownedFeature(
                id,
                id,
                source,
                marker(Rgba.rgb(40, 40, 40), 9.0),
                line,
                SolidFillSymbol.of(Rgba.rgb(30, 170, 70), 1.0));
    }

    private static SolidLineSymbol plainLine() {
        return SolidLineSymbol.of(stroke(Rgba.rgb(20, 70, 190), 3.0), 1.0);
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static VectorMarkerSymbol marker(Rgba color, double size) {
        return BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, color, size, 1.0);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
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

    private static void assertRed(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y), true);
        assertTrue(color.getRed() > 150 && color.getGreen() < 100 && color.getBlue() < 100);
    }

    private static void assertGreen(BufferedImage image, int x, int y) {
        Color color = new Color(image.getRGB(x, y), true);
        assertTrue(color.getGreen() > 120 && color.getRed() < 100);
    }

    private abstract static class TestSource implements FeatureSource {
        private final FeatureSourceMetadata metadata;
        private boolean closed;

        private TestSource(String id, Optional<io.github.mundanej.map.api.Envelope> extent) {
            metadata =
                    new FeatureSourceMetadata(
                            new SourceIdentity(id, id),
                            extent,
                            OptionalLong.empty(),
                            Optional.empty(),
                            Optional.of(
                                    CrsMetadata.recognized(
                                            CrsDefinitions.EPSG_3857,
                                            Optional.empty(),
                                            Optional.empty())));
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public FeatureSourceLimits limits() {
            return FeatureSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
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

    private static final class EmptySource extends TestSource {
        private EmptySource(String id) {
            super(id, Optional.empty());
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            return emptyCursor();
        }
    }

    private static final class BlockingSource extends TestSource {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private boolean block = true;

        private BlockingSource() {
            super("blocking-source", Optional.empty());
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            if (!block) {
                return emptyCursor();
            }
            return new FeatureCursor() {
                private boolean closed;

                @Override
                public boolean advance() {
                    entered.countDown();
                    try {
                        if (!release.await(5, TimeUnit.SECONDS)) {
                            throw new AssertionError("Timed out awaiting cancellation release");
                        }
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                    if (cancellation.isCancellationRequested()) {
                        throw sourceFailure(metadata().identity().id(), "SOURCE_CANCELLED");
                    }
                    return false;
                }

                @Override
                public FeatureRecord current() {
                    throw new IllegalStateException("no current record");
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return DiagnosticReport.empty();
                }

                @Override
                public boolean isClosed() {
                    return closed;
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }

    private static final class FlakyPointSource extends TestSource {
        private final FeatureRecord point =
                new FeatureRecord(
                        "point", "", new PointGeometry(new Coordinate(0.0, 0.0)), Map.of());
        private boolean fail;

        private FlakyPointSource() {
            super(
                    "flaky-source",
                    Optional.of(new PointGeometry(new Coordinate(0.0, 0.0)).envelope()));
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            return new FeatureCursor() {
                private int state;
                private boolean closed;

                @Override
                public boolean advance() {
                    if (fail) {
                        throw sourceFailure(metadata().identity().id(), "TEST_QUERY_FAILED");
                    }
                    return state++ == 0;
                }

                @Override
                public FeatureRecord current() {
                    if (state != 1) {
                        throw new IllegalStateException("no current record");
                    }
                    return point;
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return DiagnosticReport.empty();
                }

                @Override
                public boolean isClosed() {
                    return closed;
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }

    private static final class ReportingSource extends TestSource {
        private ReportingSource() {
            super("reporting-source", Optional.empty());
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            SourceDiagnostic warning =
                    new SourceDiagnostic(
                            "TEST_PAINT_WARNING",
                            DiagnosticSeverity.WARNING,
                            metadata().identity().id(),
                            Optional.of(DiagnosticLocation.empty()),
                            "Test paint warning",
                            Map.of());
            return new FeatureCursor() {
                private boolean closed;

                @Override
                public boolean advance() {
                    return false;
                }

                @Override
                public FeatureRecord current() {
                    throw new IllegalStateException("no current record");
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return new DiagnosticReport(List.of(warning), 0);
                }

                @Override
                public boolean isClosed() {
                    return closed;
                }

                @Override
                public void close() {
                    closed = true;
                }
            };
        }
    }

    private static final class RuntimeFailureSource extends TestSource {
        private RuntimeFailureSource() {
            super("runtime-source", Optional.empty());
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            throw new IllegalStateException("runtime");
        }
    }

    private static final class ClosingSource extends TestSource {
        private final String id;
        private final List<String> order;
        private final boolean fail;

        private ClosingSource(String id, List<String> order, boolean fail) {
            super(id + "-source", Optional.empty());
            this.id = id;
            this.order = order;
            this.fail = fail;
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            return emptyCursor();
        }

        @Override
        public void close() {
            order.add(id);
            super.close();
            if (fail) {
                throw sourceFailure(
                        metadata().identity().id(), "TEST_CLOSE_" + id.toUpperCase(Locale.ROOT));
            }
        }
    }

    private static FeatureCursor emptyCursor() {
        return new FeatureCursor() {
            private boolean closed;

            @Override
            public boolean advance() {
                return false;
            }

            @Override
            public FeatureRecord current() {
                throw new IllegalStateException("no current record");
            }

            @Override
            public DiagnosticReport diagnostics() {
                return DiagnosticReport.empty();
            }

            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }

    private static SourceException sourceFailure(String sourceId, String code) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        sourceId,
                        Optional.of(DiagnosticLocation.empty()),
                        "Test source failure",
                        Map.of());
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static final class TrackingGraphics2D extends Graphics2D {
        private final Graphics2D delegate;
        private final AtomicReference<TrackingGraphics2D> child;
        private final boolean recordChild;
        private boolean disposed;

        private TrackingGraphics2D(
                Graphics2D delegate,
                AtomicReference<TrackingGraphics2D> child,
                boolean recordChild) {
            this.delegate = delegate;
            this.child = child;
            this.recordChild = recordChild;
        }

        private static TrackingGraphics2D root(Graphics2D delegate) {
            return new TrackingGraphics2D(delegate, new AtomicReference<>(), true);
        }

        private boolean childDisposed() {
            TrackingGraphics2D created = child.get();
            return created != null && created.disposed;
        }

        @Override
        public Graphics create() {
            TrackingGraphics2D created =
                    new TrackingGraphics2D((Graphics2D) delegate.create(), child, false);
            if (recordChild) {
                child.set(created);
            }
            return created;
        }

        @Override
        public void draw(Shape shape) {
            delegate.draw(shape);
        }

        @Override
        public boolean drawImage(
                Image image, AffineTransform transform, ImageObserver imageObserver) {
            return delegate.drawImage(image, transform, imageObserver);
        }

        @Override
        public void drawImage(BufferedImage image, BufferedImageOp operation, int x, int y) {
            delegate.drawImage(image, operation, x, y);
        }

        @Override
        public void drawRenderedImage(RenderedImage image, AffineTransform transform) {
            delegate.drawRenderedImage(image, transform);
        }

        @Override
        public void drawRenderableImage(RenderableImage image, AffineTransform transform) {
            delegate.drawRenderableImage(image, transform);
        }

        @Override
        public void drawString(String text, int x, int y) {
            delegate.drawString(text, x, y);
        }

        @Override
        public void drawString(String text, float x, float y) {
            delegate.drawString(text, x, y);
        }

        @Override
        public void drawString(AttributedCharacterIterator iterator, int x, int y) {
            delegate.drawString(iterator, x, y);
        }

        @Override
        public void drawString(AttributedCharacterIterator iterator, float x, float y) {
            delegate.drawString(iterator, x, y);
        }

        @Override
        public void drawGlyphVector(GlyphVector glyphVector, float x, float y) {
            delegate.drawGlyphVector(glyphVector, x, y);
        }

        @Override
        public void fill(Shape shape) {
            delegate.fill(shape);
        }

        @Override
        public boolean hit(Rectangle rectangle, Shape shape, boolean onStroke) {
            return delegate.hit(rectangle, shape, onStroke);
        }

        @Override
        public GraphicsConfiguration getDeviceConfiguration() {
            return delegate.getDeviceConfiguration();
        }

        @Override
        public void setComposite(Composite composite) {
            delegate.setComposite(composite);
        }

        @Override
        public void setPaint(Paint paint) {
            delegate.setPaint(paint);
        }

        @Override
        public void setStroke(Stroke stroke) {
            delegate.setStroke(stroke);
        }

        @Override
        public void setRenderingHint(RenderingHints.Key key, Object value) {
            delegate.setRenderingHint(key, value);
        }

        @Override
        public Object getRenderingHint(RenderingHints.Key key) {
            return delegate.getRenderingHint(key);
        }

        @Override
        public void setRenderingHints(Map<?, ?> hints) {
            delegate.setRenderingHints(hints);
        }

        @Override
        public void addRenderingHints(Map<?, ?> hints) {
            delegate.addRenderingHints(hints);
        }

        @Override
        public RenderingHints getRenderingHints() {
            return delegate.getRenderingHints();
        }

        @Override
        public void translate(int x, int y) {
            delegate.translate(x, y);
        }

        @Override
        public void translate(double x, double y) {
            delegate.translate(x, y);
        }

        @Override
        public void rotate(double theta) {
            delegate.rotate(theta);
        }

        @Override
        public void rotate(double theta, double x, double y) {
            delegate.rotate(theta, x, y);
        }

        @Override
        public void scale(double x, double y) {
            delegate.scale(x, y);
        }

        @Override
        public void shear(double x, double y) {
            delegate.shear(x, y);
        }

        @Override
        public void transform(AffineTransform transform) {
            delegate.transform(transform);
        }

        @Override
        public void setTransform(AffineTransform transform) {
            delegate.setTransform(transform);
        }

        @Override
        public AffineTransform getTransform() {
            return delegate.getTransform();
        }

        @Override
        public Paint getPaint() {
            return delegate.getPaint();
        }

        @Override
        public Composite getComposite() {
            return delegate.getComposite();
        }

        @Override
        public void setBackground(Color color) {
            delegate.setBackground(color);
        }

        @Override
        public Color getBackground() {
            return delegate.getBackground();
        }

        @Override
        public Stroke getStroke() {
            return delegate.getStroke();
        }

        @Override
        public void clip(Shape shape) {
            delegate.clip(shape);
        }

        @Override
        public FontRenderContext getFontRenderContext() {
            return delegate.getFontRenderContext();
        }

        @Override
        public Color getColor() {
            return delegate.getColor();
        }

        @Override
        public void setColor(Color color) {
            delegate.setColor(color);
        }

        @Override
        public void setPaintMode() {
            delegate.setPaintMode();
        }

        @Override
        public void setXORMode(Color color) {
            delegate.setXORMode(color);
        }

        @Override
        public Font getFont() {
            return delegate.getFont();
        }

        @Override
        public void setFont(Font font) {
            delegate.setFont(font);
        }

        @Override
        public FontMetrics getFontMetrics(Font font) {
            return delegate.getFontMetrics(font);
        }

        @Override
        public Rectangle getClipBounds() {
            return delegate.getClipBounds();
        }

        @Override
        public void clipRect(int x, int y, int width, int height) {
            delegate.clipRect(x, y, width, height);
        }

        @Override
        public void setClip(int x, int y, int width, int height) {
            delegate.setClip(x, y, width, height);
        }

        @Override
        public Shape getClip() {
            return delegate.getClip();
        }

        @Override
        public void setClip(Shape shape) {
            delegate.setClip(shape);
        }

        @Override
        public void copyArea(int x, int y, int width, int height, int dx, int dy) {
            delegate.copyArea(x, y, width, height, dx, dy);
        }

        @Override
        public void drawLine(int x1, int y1, int x2, int y2) {
            delegate.drawLine(x1, y1, x2, y2);
        }

        @Override
        public void fillRect(int x, int y, int width, int height) {
            delegate.fillRect(x, y, width, height);
        }

        @Override
        public void clearRect(int x, int y, int width, int height) {
            delegate.clearRect(x, y, width, height);
        }

        @Override
        public void drawRoundRect(
                int x, int y, int width, int height, int arcWidth, int arcHeight) {
            delegate.drawRoundRect(x, y, width, height, arcWidth, arcHeight);
        }

        @Override
        public void fillRoundRect(
                int x, int y, int width, int height, int arcWidth, int arcHeight) {
            delegate.fillRoundRect(x, y, width, height, arcWidth, arcHeight);
        }

        @Override
        public void drawOval(int x, int y, int width, int height) {
            delegate.drawOval(x, y, width, height);
        }

        @Override
        public void fillOval(int x, int y, int width, int height) {
            delegate.fillOval(x, y, width, height);
        }

        @Override
        public void drawArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
            delegate.drawArc(x, y, width, height, startAngle, arcAngle);
        }

        @Override
        public void fillArc(int x, int y, int width, int height, int startAngle, int arcAngle) {
            delegate.fillArc(x, y, width, height, startAngle, arcAngle);
        }

        @Override
        public void drawPolyline(int[] xPoints, int[] yPoints, int pointCount) {
            delegate.drawPolyline(xPoints, yPoints, pointCount);
        }

        @Override
        public void drawPolygon(int[] xPoints, int[] yPoints, int pointCount) {
            delegate.drawPolygon(xPoints, yPoints, pointCount);
        }

        @Override
        public void fillPolygon(int[] xPoints, int[] yPoints, int pointCount) {
            delegate.fillPolygon(xPoints, yPoints, pointCount);
        }

        @Override
        public boolean drawImage(Image image, int x, int y, ImageObserver observer) {
            return delegate.drawImage(image, x, y, observer);
        }

        @Override
        public boolean drawImage(
                Image image, int x, int y, int width, int height, ImageObserver observer) {
            return delegate.drawImage(image, x, y, width, height, observer);
        }

        @Override
        public boolean drawImage(
                Image image, int x, int y, Color background, ImageObserver observer) {
            return delegate.drawImage(image, x, y, background, observer);
        }

        @Override
        public boolean drawImage(
                Image image,
                int x,
                int y,
                int width,
                int height,
                Color background,
                ImageObserver observer) {
            return delegate.drawImage(image, x, y, width, height, background, observer);
        }

        @Override
        public boolean drawImage(
                Image image,
                int destinationX1,
                int destinationY1,
                int destinationX2,
                int destinationY2,
                int sourceX1,
                int sourceY1,
                int sourceX2,
                int sourceY2,
                ImageObserver observer) {
            return delegate.drawImage(
                    image,
                    destinationX1,
                    destinationY1,
                    destinationX2,
                    destinationY2,
                    sourceX1,
                    sourceY1,
                    sourceX2,
                    sourceY2,
                    observer);
        }

        @Override
        public boolean drawImage(
                Image image,
                int destinationX1,
                int destinationY1,
                int destinationX2,
                int destinationY2,
                int sourceX1,
                int sourceY1,
                int sourceX2,
                int sourceY2,
                Color background,
                ImageObserver observer) {
            return delegate.drawImage(
                    image,
                    destinationX1,
                    destinationY1,
                    destinationX2,
                    destinationY2,
                    sourceX1,
                    sourceY1,
                    sourceX2,
                    sourceY2,
                    background,
                    observer);
        }

        @Override
        public void dispose() {
            disposed = true;
            delegate.dispose();
        }
    }
}
