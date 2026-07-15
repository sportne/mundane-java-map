package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.AttributeType;
import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.MultiPolygonGeometry;
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
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Assertion-bearing Shapefile scenario shared by JVM tests and the native executable. */
final class NativeShapefileSmokeScenario {
    static final String VALID_SOURCE_ID = "native-shapefile-valid";
    static final String MALFORMED_SOURCE_ID = "native-shapefile-malformed";
    static final Envelope EXTENT = new Envelope(0.0, 0.0, 80.0, 40.0);
    static final FeatureQueryLimits QUERY_LIMITS =
            new FeatureQueryLimits(4, 4, 256, 16, 256, 65_536, 8);
    static final Rgba FILL = new Rgba(42, 132, 96, 255);
    static final Rgba OUTLINE = new Rgba(18, 54, 40, 255);
    private static final String WEB_MERCATOR_PRJ =
            "PROJCS[\"WGS_1984_Web_Mercator_Auxiliary_Sphere\",GEOGCS[\"GCS_WGS_1984\","
                    + "DATUM[\"D_WGS_1984\",SPHEROID[\"WGS_1984\",6378137.0,"
                    + "298.257223563]],PRIMEM[\"Greenwich\",0.0],UNIT[\"Degree\","
                    + "0.0174532925199433]],PROJECTION[\"Mercator_Auxiliary_Sphere\"],"
                    + "PARAMETER[\"False_Easting\",0],PARAMETER[\"False_Northing\",0],"
                    + "PARAMETER[\"Central_Meridian\",0],PARAMETER[\"Standard_Parallel_1\",0],"
                    + "PARAMETER[\"Auxiliary_Sphere_Type\",0],UNIT[\"Meter\",1]]";
    private static final int IMAGE_WIDTH = 256;
    private static final int IMAGE_HEIGHT = 160;

    private NativeShapefileSmokeScenario() {}

    static Result run() {
        if (SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("shapefile-smoke: source work must run off the EDT");
        }
        try (NativeShapefileWorkspace workspace = NativeShapefileWorkspace.open()) {
            Result valid = runValid(workspace);
            assertMalformed(workspace);
            return valid;
        }
    }

    private static Result runValid(NativeShapefileWorkspace workspace) {
        FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity(VALID_SOURCE_ID, "Native Shapefile valid fixture"),
                        workspace.path(NativeShapefileResources.SHP),
                        ShapefileOpenOptions.defaults());
        ValidRun valid =
                withCleanup(
                        () -> {
                            assertMetadata(source.metadata(), source.openingDiagnostics());
                            QueryResult query = query(source);
                            assertRecords(query.records());
                            RenderResult rendering = onEdt(() -> render(source));
                            List<FeatureRecord> records = query.records();
                            return new ValidRun(
                                    new Result(
                                            records.size(),
                                            records.getFirst().id(),
                                            (String) records.getFirst().attributes().get("NAME"),
                                            records.getLast().attributes().get("NOTE")
                                                    == AttributeNull.INSTANCE,
                                            rendering.nonWhitePixels(),
                                            query.diagnostics()),
                                    query.diagnostics());
                        },
                        () -> {
                            if (!source.isClosed()) {
                                source.close();
                            }
                        });
        // The immutable report is intentionally read after both cursor and source ownership close.
        assertCursorWarning(valid.diagnostics());
        return valid.result();
    }

    private static QueryResult query(FeatureSource source) {
        FeatureQuery query =
                new FeatureQuery(
                        Optional.empty(), AttributeSelection.ALL, Optional.of(QUERY_LIMITS));
        List<FeatureRecord> records = new ArrayList<>();
        DiagnosticReport diagnostics;
        try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
            while (cursor.advance()) {
                records.add(cursor.current());
            }
            diagnostics = cursor.diagnostics();
        }
        List<FeatureRecord> retained = List.copyOf(records);
        require(
                retained.getFirst().geometry().envelope().equals(new Envelope(0, 0, 40, 40)),
                "retained record changed after cursor close");
        return new QueryResult(retained, diagnostics);
    }

    static void assertMetadata(FeatureSourceMetadata metadata, DiagnosticReport opening) {
        require(metadata.extent().equals(Optional.of(EXTENT)), "unexpected source extent");
        require(
                metadata.featureCount().equals(OptionalLong.empty()),
                "feature count must be absent");
        var schema = metadata.schema().orElseThrow();
        require(schema.fields().size() == 2, "unexpected schema size");
        require(schema.fields().get(0).name().equals("NAME"), "NAME schema order changed");
        require(schema.fields().get(0).type() == AttributeType.TEXT, "NAME schema type changed");
        require(schema.fields().get(1).name().equals("NOTE"), "NOTE schema order changed");
        require(schema.fields().get(1).type() == AttributeType.TEXT, "NOTE schema type changed");
        var crs = metadata.crs().orElseThrow();
        require(
                crs.canonicalIdentifier().equals(Optional.of("EPSG:3857")),
                "recognized CRS changed");
        require(
                crs.retainedDefinition().equals(Optional.of(WEB_MERCATOR_PRJ)),
                "retained PRJ changed");
        require(opening.equals(DiagnosticReport.empty()), "valid fixture has opening diagnostics");
    }

    static void assertRecords(List<FeatureRecord> records) {
        require(records.size() == 2, "unexpected record count");
        FeatureRecord first = records.get(0);
        require(first.id().equals("record:1"), "first record ID changed");
        require(
                first.attributes().equals(Map.of("NAME", "Café", "NOTE", "valid")),
                "first record attributes changed");
        require(first.geometry() instanceof PolygonGeometry, "first geometry is not a polygon");
        PolygonGeometry polygon = (PolygonGeometry) first.geometry();
        require(
                polygon.exterior().equals(sequence(0, 0, 0, 40, 40, 40, 40, 0, 0, 0)),
                "first shell changed");
        require(
                polygon.holes().equals(List.of(sequence(10, 10, 20, 10, 20, 20, 10, 20, 10, 10))),
                "first hole changed");

        FeatureRecord second = records.get(1);
        require(second.id().equals("record:2"), "second record ID changed");
        require(second.attributes().get("NAME").equals("Second"), "second NAME changed");
        require(
                second.attributes().get("NOTE") == AttributeNull.INSTANCE,
                "undefined byte changed");
        require(
                second.geometry() instanceof MultiPolygonGeometry,
                "second geometry is not a multipolygon");
        MultiPolygonGeometry multipolygon = (MultiPolygonGeometry) second.geometry();
        require(multipolygon.polygonCount() == 2, "multipart polygon count changed");
        require(multipolygon.ringCount() == 2, "multipart ring count changed");
        require(
                java.util.Arrays.equals(multipolygon.ringOffsets(), new int[] {0, 5, 10}),
                "multipart ring fences changed");
        require(
                java.util.Arrays.equals(multipolygon.polygonRingOffsets(), new int[] {0, 1, 2}),
                "multipart polygon fences changed");
        require(
                multipolygon
                        .coordinates()
                        .equals(
                                sequence(
                                        50, 0, 50, 10, 60, 10, 60, 0, 50, 0, 70, 20, 70, 30, 80, 30,
                                        80, 20, 70, 20)),
                "multipart coordinates changed");
    }

    static void assertCursorWarning(DiagnosticReport report) {
        require(report.omittedWarningCount() == 0, "cursor warning was omitted");
        require(report.entries().size() == 1, "unexpected cursor diagnostics");
        SourceDiagnostic warning = report.entries().getFirst();
        require(warning.code().equals("SHAPEFILE_DBF_VALUE_INVALID"), "warning code changed");
        DiagnosticLocation location = warning.location().orElseThrow();
        require(location.component().equals(Optional.of("dbf")), "warning component changed");
        require(location.recordNumber().orElseThrow() == 2, "warning record changed");
        require(location.fieldIndex().orElseThrow() == 1, "warning field changed");
        require(location.fieldName().equals(Optional.of("NOTE")), "warning field name changed");
        require(location.byteOffset().orElseThrow() == 129, "warning offset changed");
        require(warning.context().equals(Map.of("reason", "encoding")), "warning context changed");
    }

    static <T> T onEdt(ThrowingSupplier<T> operation) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch completed = new CountDownLatch(1);
        SwingUtilities.invokeLater(
                () -> {
                    try {
                        result.set(operation.get());
                    } catch (Throwable throwable) {
                        failure.set(throwable);
                    } finally {
                        completed.countDown();
                    }
                });
        boolean interrupted = false;
        while (true) {
            try {
                completed.await();
                break;
            } catch (InterruptedException exception) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
        rethrow(failure.get());
        return result.get();
    }

    private static RenderResult render(FeatureSource source) {
        CrsRegistry registry = CrsRegistry.level1();
        MapView view =
                new MapView(
                        registry,
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        SymbolRendererRegistry.builderWithBuiltIns().build());
        view.setDoubleBuffered(false);
        view.setSize(IMAGE_WIDTH, IMAGE_HEIGHT);
        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(OUTLINE, new SymbolLength(2.0, SymbolUnit.SCREEN_PIXEL)),
                        1.0);
        SolidFillSymbol fill = SolidFillSymbol.of(FILL, Optional.of(line), 1.0);
        MapLayerBinding binding =
                MapLayerBinding.ownedFeature(
                        "native-shapefile",
                        "Native Shapefile",
                        source,
                        BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, FILL, 8.0, 1.0),
                        line,
                        fill);
        return withRenderOwnership(
                () -> {
                    view.setLayerBindings(List.of(binding));
                    view.fitToData(16.0);
                    BufferedImage image =
                            new BufferedImage(
                                    IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_ARGB);
                    RepaintManager manager = RepaintManager.currentManager(view);
                    boolean previous = manager.isDoubleBufferingEnabled();
                    manager.setDoubleBufferingEnabled(false);
                    try {
                        Graphics2D graphics = image.createGraphics();
                        try {
                            graphics.setComposite(AlphaComposite.Src);
                            graphics.setColor(Color.WHITE);
                            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    } finally {
                        manager.setDoubleBufferingEnabled(previous);
                    }
                    int nonWhite = NativeShapefileSmokeAssertions.verify(image, view, EXTENT);
                    return new RenderResult(nonWhite);
                },
                () -> view.layerBindings().stream().anyMatch(value -> value == binding),
                binding::close,
                view::close);
    }

    static <T> T withCleanup(ThrowingSupplier<T> operation, Runnable cleanup) {
        T result = null;
        Throwable failure = null;
        try {
            result = operation.get();
        } catch (Throwable thrown) {
            failure = thrown;
        }
        failure = cleanup(failure, cleanup);
        rethrow(failure);
        return result;
    }

    static <T> T withRenderOwnership(
            ThrowingSupplier<T> operation,
            BooleanSupplier viewOwnsBinding,
            Runnable closeBinding,
            Runnable closeView) {
        T result = null;
        Throwable failure = null;
        try {
            result = operation.get();
        } catch (Throwable thrown) {
            failure = thrown;
        }

        boolean installed = false;
        try {
            installed = viewOwnsBinding.getAsBoolean();
        } catch (Throwable ownershipFailure) {
            failure = suppress(failure, ownershipFailure);
        }
        if (!installed) {
            failure = cleanup(failure, closeBinding);
        }
        failure = cleanup(failure, closeView);
        rethrow(failure);
        return result;
    }

    private static Throwable cleanup(Throwable primary, Runnable cleanup) {
        try {
            cleanup.run();
            return primary;
        } catch (Throwable failure) {
            return suppress(primary, failure);
        }
    }

    private static Throwable suppress(Throwable primary, Throwable secondary) {
        if (primary == null) {
            return secondary;
        }
        if (primary != secondary) {
            primary.addSuppressed(secondary);
        }
        return primary;
    }

    private static void assertMalformed(NativeShapefileWorkspace workspace) {
        ShapefileOpenOptions options =
                ShapefileOpenOptions.defaults()
                        .withCrsOverride(CrsRegistry.level1().resolve("EPSG:3857"));
        try (FeatureSource source =
                Shapefiles.open(
                        new SourceIdentity(MALFORMED_SOURCE_ID, "Native malformed fixture"),
                        workspace.path(NativeShapefileResources.MALFORMED),
                        options)) {
            List<SourceDiagnostic> warnings = source.openingDiagnostics().entries();
            require(warnings.size() == 2, "malformed opening warning count changed");
            require(warnings.get(0).code().equals("SHAPEFILE_SHX_MISSING"), "SHX warning changed");
            require(warnings.get(1).code().equals("SHAPEFILE_DBF_MISSING"), "DBF warning changed");
            FeatureQuery query =
                    new FeatureQuery(
                            Optional.empty(), AttributeSelection.NONE, Optional.of(QUERY_LIMITS));
            try (FeatureCursor cursor = source.openCursor(query, CancellationToken.none())) {
                try {
                    cursor.advance();
                    throw new IllegalStateException(
                            "shapefile-smoke: malformed record was accepted");
                } catch (SourceException expected) {
                    assertMalformedDiagnostic(expected);
                }
            }
        }
    }

    static void assertMalformedDiagnostic(SourceException failure) {
        SourceDiagnostic terminal = failure.terminal();
        require(
                terminal.code().equals("SHAPEFILE_RECORD_LENGTH_INVALID"),
                "malformed diagnostic code changed");
        DiagnosticLocation location = terminal.location().orElseThrow();
        require(location.component().equals(Optional.of("shp")), "malformed component changed");
        require(location.recordNumber().orElseThrow() == 1, "malformed record changed");
        require(location.byteOffset().orElseThrow() == 104, "malformed offset changed");
        require(
                terminal.context()
                        .equals(
                                Map.of(
                                        "declaredBytes", "20",
                                        "reason", "outOfFile",
                                        "remainingBytes", "0")),
                "malformed diagnostic context changed");
        require(failure.report().entries().getLast().equals(terminal), "terminal report changed");
    }

    private static CoordinateSequence sequence(double... ordinates) {
        return CoordinateSequence.of(ordinates);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException("shapefile-smoke: " + message);
        }
    }

    private static void rethrow(Throwable failure) {
        if (failure == null) {
            return;
        }
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        throw new IllegalStateException("shapefile-smoke: render failed", failure);
    }

    record Result(
            int featureCount,
            String firstFeatureId,
            String windows1252Value,
            boolean undefinedByteBecameNull,
            int nonWhitePixels,
            DiagnosticReport retainedDiagnostics) {}

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws Throwable;
    }

    private record RenderResult(int nonWhitePixels) {}

    private record QueryResult(List<FeatureRecord> records, DiagnosticReport diagnostics) {}

    private record ValidRun(Result result, DiagnosticReport diagnostics) {}
}
