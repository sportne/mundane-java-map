package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureName;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelAnchorBasis;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.TextAttribute;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.api.VectorExportSnapshotLimits;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class BrowserLabelAndVectorCaptureTest {
    private static final MapViewport VIEWPORT = new MapViewport(800, 600, 0, 0, 2);

    @Test
    void queriesExactVisibleLabelProjectionAndEncodesTheClosedFontProfile() {
        RecordingSource source =
                new RecordingSource(
                        source(
                                "projection",
                                List.of(
                                        new FeatureRecord(
                                                "point",
                                                "Point",
                                                new PointGeometry(new Coordinate(20, 10)),
                                                Map.of("label", "Alpha")))));
        PointLabelProfile profile =
                profile(
                        new TextAttribute("label"),
                        0,
                        PointLabelPosition.NE,
                        new ResolutionRange(0.5, 2));
        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(
                        "labels",
                        "Labels",
                        source,
                        FeaturePortrayal.markers(new FixedSymbolSelector(marker()))
                                .withPointLabel(profile),
                        Optional.empty());
        assertEquals(AttributeSelection.only(List.of("label")), binding.attributes());

        FeatureSourceQueryEngine engine = new FeatureSourceQueryEngine();
        FeatureSourceQueryEngine.Result hidden =
                query(engine, binding, new MapViewport(800, 600, 0, 0, 4));
        assertEquals(AttributeSelection.NONE, source.lastQuery.attributes());
        assertTrue(
                ((BrowserLabelLayer) hidden.layers().getFirst())
                        .browserLabelCandidates()
                        .isEmpty());

        FeatureSourceQueryEngine.Result visible = query(engine, binding, VIEWPORT);
        assertEquals(AttributeSelection.only(List.of("label")), source.lastQuery.attributes());
        BrowserLabelCandidate selected =
                ((BrowserLabelLayer) visible.layers().getFirst())
                        .browserLabelCandidates()
                        .getFirst();
        assertEquals("Alpha", selected.text());
        assertEquals(new Coordinate(20, 10), selected.mapAnchor());

        SceneProtocol.Result encoded =
                new SceneProtocol(SceneProtocol.DEFAULT_LIMITS)
                        .encode(visible.layers(), Rgba.rgb(255, 255, 255), VIEWPORT, 1, 2, 3);
        Map<?, ?> candidate =
                (Map<?, ?>) ((List<?>) encoded.scene().get("labelCandidates")).getFirst();
        assertEquals(
                Map.of(
                        "ordinal", 0,
                        "text", "Alpha",
                        "fontFamily", "SANS_SERIF",
                        "weight", "BOLD",
                        "sizePixels", 14.0),
                candidate);
        assertEquals(1, encoded.labelCandidates().size());
        binding.close();
    }

    @Test
    void placesCollisionsByPriorityThenTopmostPaintOrderAndRejectsBadMetrics() {
        SceneLabelCandidate first = candidate("first", 0, 0, 0);
        SceneLabelCandidate second = candidate("second", 1, 0, 0);
        double[] equalMetrics = {28, 0, -9, 28, 3, 28, 0, -9, 28, 3};

        List<PlacedPointLabel> tied =
                BrowserLabelPlacement.place(List.of(first, second), equalMetrics, VIEWPORT);
        assertEquals(List.of("second"), tied.stream().map(PlacedPointLabel::featureId).toList());

        SceneLabelCandidate higherPriority = candidate("first", 0, 10, 0);
        List<PlacedPointLabel> prioritized =
                BrowserLabelPlacement.place(
                        List.of(higherPriority, second), equalMetrics, VIEWPORT);
        assertEquals(
                List.of("first"), prioritized.stream().map(PlacedPointLabel::featureId).toList());

        MundaneMapException count =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                BrowserLabelPlacement.place(
                                        List.of(first), new double[] {1}, VIEWPORT));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, count.code());
        MundaneMapException nonFinite =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                BrowserLabelPlacement.place(
                                        List.of(first),
                                        new double[] {Double.NaN, 0, -1, 1, 0},
                                        VIEWPORT));
        assertEquals(MundaneMapException.NON_FINITE_VALUE, nonFinite.code());
        MundaneMapException magnitude =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                BrowserLabelPlacement.place(
                                        List.of(first),
                                        new double[] {1_000_001, 0, -1, 1, 0},
                                        VIEWPORT));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, magnitude.code());

        MundaneMapException inverted =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                BrowserLabelPlacement.place(
                                        List.of(first), new double[] {1, 2, -1, 1, 0}, VIEWPORT));
        assertEquals(MundaneMapException.UNSUPPORTED_VALUE, inverted.code());

        SceneLabelCandidate overflowing = candidate("overflow", 0, 0, Double.MAX_VALUE);
        MundaneMapException anchor =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                BrowserLabelPlacement.place(
                                        List.of(overflowing),
                                        new double[] {1, 0, -1, 1, 0},
                                        new MapViewport(800, 600, -Double.MAX_VALUE, 0, 2)));
        assertEquals(MundaneMapException.NON_FINITE_VALUE, anchor.code());
    }

    @Test
    void measuresMarkerBoundsForEverySupportedMarkerShape() {
        PointLabelProfile profile =
                new PointLabelProfile(
                        FeatureName.INSTANCE,
                        new LabelTextStyle(Rgba.rgb(20, 30, 40), LabelWeight.NORMAL, 12),
                        List.of(PointLabelPosition.E),
                        2,
                        0,
                        0,
                        0,
                        0,
                        ResolutionRange.ALL,
                        PointLabelAnchorBasis.MARKER_BOUNDS);
        RasterIconSymbol icon =
                RasterIconSymbol.of(
                        1,
                        1,
                        new int[] {0x102030ff},
                        io.github.mundanej.map.api.MarkerPlacement.centeredScreen(16),
                        RasterInterpolation.NEAREST,
                        1);
        List<SceneLabelCandidate> candidates =
                List.of(
                        markerCandidate("vector", marker(), profile, -200, 0),
                        markerCandidate("raster", icon, profile, 0, 1),
                        markerCandidate(
                                "composite",
                                CompositeSymbol.of(List.of(marker(), marker()), 1),
                                profile,
                                200,
                                2));
        List<PlacedPointLabel> placed =
                BrowserLabelPlacement.place(
                        candidates,
                        new double[] {
                            10, 0, -8, 10, 2,
                            10, 0, -8, 10, 2,
                            10, 0, -8, 10, 2
                        },
                        VIEWPORT);
        assertEquals(
                List.of("vector", "raster", "composite"),
                placed.stream().map(PlacedPointLabel::featureId).toList());
    }

    @Test
    void enforcesCandidateLimitAndCandidateFeatureAgreementBeforePublication() {
        Feature feature =
                new Feature(
                        "point",
                        "Point",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        marker());
        BrowserLabelCandidate candidate =
                new BrowserLabelCandidate(
                        "labels",
                        "point",
                        new Coordinate(0, 0),
                        marker(),
                        "A",
                        profile(
                                FeatureName.INSTANCE,
                                0,
                                PointLabelPosition.CENTER,
                                ResolutionRange.ALL),
                        0);
        List<BrowserLabelCandidate> tooMany = new ArrayList<>();
        for (int index = 0; index < 4_097; index++) {
            tooMany.add(candidate);
        }
        SceneProtocol protocol = new SceneProtocol(SceneProtocol.DEFAULT_LIMITS);
        MundaneMapException limit =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                protocol.encode(
                                        List.of(
                                                new LabelLayer(
                                                        "labels",
                                                        "Labels",
                                                        List.of(feature),
                                                        tooMany)),
                                        Rgba.rgb(255, 255, 255),
                                        VIEWPORT,
                                        1,
                                        2,
                                        3));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, limit.code());
        assertEquals("labelRequests", limit.context().get("limit"));

        BrowserLabelCandidate displaced =
                new BrowserLabelCandidate(
                        "labels",
                        "point",
                        new Coordinate(1, 0),
                        marker(),
                        "A",
                        candidate.profile(),
                        0);
        MundaneMapException mismatch =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                protocol.encode(
                                        List.of(
                                                new LabelLayer(
                                                        "labels",
                                                        "Labels",
                                                        List.of(feature),
                                                        List.of(displaced))),
                                        Rgba.rgb(255, 255, 255),
                                        VIEWPORT,
                                        1,
                                        2,
                                        3));
        assertEquals(MundaneMapException.UNSUPPORTED_VALUE, mismatch.code());
    }

    @Test
    void capturesOnlyTheAcknowledgedImmutableGenerationAndRetainsPaintOrder() {
        MundaneMap map = mapWithLabelSource();
        VectorExportSnapshotException pending =
                assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);
        assertEquals("VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID", pending.problem().code());

        long component = map.componentGenerationForTest();
        long scene = map.sceneGenerationForTest();
        long viewport = map.viewportGenerationForTest();
        map.acceptLabelMeasurements(
                1,
                (double) component,
                (double) scene,
                (double) viewport,
                new double[] {35, 0, -9, 35, 3});
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);
        map.acceptPlacedLabels(1, (double) component, (double) scene, (double) viewport);

        VectorExportSnapshot snapshot = map.captureVectorExportSnapshot();
        assertEquals(800, snapshot.widthPixels());
        assertEquals(600, snapshot.heightPixels());
        assertEquals(2, snapshot.layerCount());
        assertEquals(
                List.of(0, 1),
                snapshot.primitives().stream()
                        .map(VectorExportSnapshot.Primitive::layerIndex)
                        .toList());
        assertEquals(
                new Coordinate(410, 295),
                ((PointGeometry) snapshot.primitives().getLast().screenGeometry()).coordinate());
        assertEquals(0.5, snapshot.viewFrame().screenPixelsPerMapUnit());
        assertEquals(new Coordinate(400, 300), snapshot.viewFrame().mapOriginScreen());
        assertEquals(
                List.of("Alpha"),
                snapshot.labels().stream().map(VectorExportSnapshot.Label::text).toList());
        assertEquals(snapshot, map.captureVectorExportSnapshot());

        CancellationSource cancellation = new CancellationSource();
        cancellation.cancel();
        VectorExportSnapshotException cancelled =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                map.captureVectorExportSnapshot(
                                        VectorExportSnapshotLimits.defaults(),
                                        cancellation.token()));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_CANCELLED", cancelled.problem().code());
        VectorExportSnapshotException limited =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                map.captureVectorExportSnapshot(
                                        VectorExportSnapshotLimits.defaults()
                                                .withMaximumFeatures(1)));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_LIMIT_EXCEEDED", limited.problem().code());
        assertEquals(snapshot, map.captureVectorExportSnapshot());
        map.close();
    }

    @Test
    void capturesAnAcknowledgedVectorOnlySceneWithoutAHostMeasurement() {
        MundaneMap map =
                new MundaneMap(System::nanoTime, Runnable::run, Runnable::run, Runnable::run);
        map.setViewport(VIEWPORT);
        map.setSnapshotLayers(
                List.of(
                        new InMemoryLayer(
                                "points",
                                "Points",
                                List.of(
                                        new Feature(
                                                "point",
                                                "Point",
                                                new PointGeometry(new Coordinate(0, 0)),
                                                Map.of(),
                                                marker())))));
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);
        map.acceptPlacedLabels(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest());
        VectorExportSnapshot snapshot = map.captureVectorExportSnapshot();
        assertEquals(1, snapshot.primitives().size());
        assertTrue(snapshot.labels().isEmpty());
        map.close();
    }

    @Test
    void staleMalformedReplacementDisableDetachAndCloseCannotPublishCaptureState() {
        MundaneMap map = mapWithLabelSource();
        acceptCurrent(map);
        long staleScene = map.sceneGenerationForTest();
        long staleViewport = map.viewportGenerationForTest();

        map.acceptClientFailure(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                "deliberate paint failure");
        assertEquals(MundaneMapException.CLIENT_FAILURE, map.diagnostic().orElseThrow().code());
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);

        map.setViewport(new MapViewport(800, 600, 10, 0, 2));
        map.acceptLabelMeasurements(
                1,
                (double) map.componentGenerationForTest(),
                (double) staleScene,
                (double) staleViewport,
                new double[] {35, 0, -9, 35, 3});
        assertEquals(MundaneMapException.STALE_GENERATION, map.diagnostic().orElseThrow().code());
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);

        long scene = map.sceneGenerationForTest();
        long viewport = map.viewportGenerationForTest();
        map.acceptLabelMeasurements(
                1,
                (double) map.componentGenerationForTest(),
                (double) scene,
                (double) viewport,
                new double[] {Double.NaN, 0, -9, 35, 3});
        assertEquals(MundaneMapException.NON_FINITE_VALUE, map.diagnostic().orElseThrow().code());
        acceptCurrent(map);
        assertFalse(map.captureVectorExportSnapshot().labels().isEmpty());

        map.setBackground(Rgba.rgb(240, 240, 240));
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);
        acceptCurrent(map);
        long beforeDisableViewport = map.viewportGenerationForTest();
        map.setEnabled(false);
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);
        map.setEnabled(true);
        map.acceptLabelMeasurements(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) beforeDisableViewport,
                new double[] {35, 0, -9, 35, 3});
        assertEquals(MundaneMapException.STALE_GENERATION, map.diagnostic().orElseThrow().code());
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);
        acceptCurrent(map);
        map.onDetach(new com.vaadin.flow.component.DetachEvent(map));
        assertThrows(VectorExportSnapshotException.class, map::captureVectorExportSnapshot);
        map.close();
        assertEquals(
                MundaneMapException.CLOSED,
                assertThrows(MundaneMapException.class, map::captureVectorExportSnapshot).code());
    }

    @Test
    void nonRepresentableRasterCaptureFailsAtomicallyWithExistingDiagnostic() {
        RasterIconSymbol icon =
                RasterIconSymbol.of(
                        1,
                        1,
                        new int[] {0x102030ff},
                        io.github.mundanej.map.api.MarkerPlacement.centeredScreen(16),
                        RasterInterpolation.NEAREST,
                        1);
        Layer layer =
                new InMemoryLayer(
                        "icons",
                        "Icons",
                        List.of(
                                new Feature(
                                        "icon",
                                        "Icon",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of(),
                                        icon)));
        VectorExportSnapshotException failure =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                BrowserVectorCapture.capture(
                                        List.of(layer),
                                        List.of(),
                                        VIEWPORT,
                                        Rgba.rgb(255, 255, 255),
                                        VectorExportSnapshotLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals("VECTOR_EXPORT_SYMBOL_UNSUPPORTED", failure.problem().code());
        assertThrows(
                VectorExportSnapshotException.class,
                () ->
                        BrowserVectorCapture.capture(
                                List.of(layer),
                                List.of(),
                                VIEWPORT,
                                Rgba.rgb(255, 255, 255),
                                VectorExportSnapshotLimits.defaults(),
                                CancellationToken.none()));
    }

    @Test
    void capturesAllSixGeometryFamiliesAndNormalizesTransformFailures() {
        PolygonGeometry polygon =
                new PolygonGeometry(CoordinateSequence.of(0, 0, 4, 0, 4, 4, 0, 0), List.of());
        List<Feature> features =
                List.of(
                        new Feature(
                                "point",
                                "Point",
                                new PointGeometry(new Coordinate(0, 0)),
                                Map.of(),
                                marker()),
                        new Feature(
                                "multipoint",
                                "Multi point",
                                new MultiPointGeometry(CoordinateSequence.of(0, 0, 2, 2)),
                                Map.of(),
                                marker()),
                        new Feature(
                                "line",
                                "Line",
                                new LineStringGeometry(CoordinateSequence.of(0, 0, 2, 2)),
                                Map.of(),
                                line()),
                        new Feature(
                                "multiline",
                                "Multi line",
                                MultiLineStringGeometry.ofParts(
                                        List.of(
                                                CoordinateSequence.of(0, 0, 2, 2),
                                                CoordinateSequence.of(3, 3, 4, 4))),
                                Map.of(),
                                line()),
                        new Feature(
                                "polygon",
                                "Polygon",
                                polygon,
                                Map.of(),
                                SolidFillSymbol.of(Rgba.rgb(5, 6, 7), 1)),
                        new Feature(
                                "multipolygon",
                                "Multi polygon",
                                MultiPolygonGeometry.ofPolygons(List.of(polygon)),
                                Map.of(),
                                SolidFillSymbol.of(Rgba.rgb(5, 6, 7), 1)));
        VectorExportSnapshot snapshot =
                BrowserVectorCapture.capture(
                        List.of(new InMemoryLayer("all", "All", features)),
                        List.of(),
                        VIEWPORT,
                        Rgba.rgb(255, 255, 255),
                        VectorExportSnapshotLimits.defaults(),
                        CancellationToken.none());
        assertEquals(
                List.of(
                        PointGeometry.class,
                        MultiPointGeometry.class,
                        LineStringGeometry.class,
                        MultiLineStringGeometry.class,
                        PolygonGeometry.class,
                        MultiPolygonGeometry.class),
                snapshot.primitives().stream()
                        .map(primitive -> primitive.screenGeometry().getClass())
                        .toList());

        Layer overflow =
                new InMemoryLayer(
                        "overflow",
                        "Overflow",
                        List.of(
                                new Feature(
                                        "point",
                                        "Point",
                                        new PointGeometry(new Coordinate(Double.MAX_VALUE, 0)),
                                        Map.of(),
                                        marker())));
        VectorExportSnapshotException invalid =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                BrowserVectorCapture.capture(
                                        List.of(overflow),
                                        List.of(),
                                        new MapViewport(800, 600, -Double.MAX_VALUE, 0, 2),
                                        Rgba.rgb(255, 255, 255),
                                        VectorExportSnapshotLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_VALUE_INVALID", invalid.problem().code());
        assertEquals("screenGeometry", invalid.problem().context().get("field"));

        VectorExportSnapshotException ownedBytes =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                BrowserVectorCapture.capture(
                                        List.of(new InMemoryLayer("all", "All", features)),
                                        List.of(),
                                        VIEWPORT,
                                        Rgba.rgb(255, 255, 255),
                                        VectorExportSnapshotLimits.defaults()
                                                .withMaximumOwnedBytes(129),
                                        CancellationToken.none()));
        assertEquals("ownedBytes", ownedBytes.problem().context().get("limit"));
    }

    @Test
    void observesCancellationInsideOneCoordinateSequence() {
        double[] packed = new double[400];
        for (int index = 0; index < packed.length; index += 2) {
            packed[index] = index;
            packed[index + 1] = index / 2.0;
        }
        Layer layer =
                new InMemoryLayer(
                        "line",
                        "Line",
                        List.of(
                                new Feature(
                                        "line",
                                        "Line",
                                        new LineStringGeometry(CoordinateSequence.of(packed)),
                                        Map.of(),
                                        line())));
        AtomicInteger polls = new AtomicInteger();
        CancellationToken cancellation = () -> polls.incrementAndGet() > 30;
        VectorExportSnapshotException failure =
                assertThrows(
                        VectorExportSnapshotException.class,
                        () ->
                                BrowserVectorCapture.capture(
                                        List.of(layer),
                                        List.of(),
                                        VIEWPORT,
                                        Rgba.rgb(255, 255, 255),
                                        VectorExportSnapshotLimits.defaults(),
                                        cancellation));
        assertEquals("VECTOR_EXPORT_SNAPSHOT_CANCELLED", failure.problem().code());
        assertTrue(polls.get() < 200);
    }

    private static FeatureSourceQueryEngine.Result query(
            FeatureSourceQueryEngine engine, FeatureSourceBinding binding, MapViewport viewport) {
        return engine.query(
                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                viewport,
                CrsRegistry.level1(),
                CrsDefinitions.EPSG_3857,
                CrsDefinitions.EPSG_3857,
                CancellationToken.none());
    }

    private static SceneLabelCandidate candidate(
            String featureId, int ordinal, int priority, double anchorX) {
        PointLabelProfile profile =
                profile(
                        FeatureName.INSTANCE,
                        priority,
                        PointLabelPosition.CENTER,
                        ResolutionRange.ALL);
        return new SceneLabelCandidate(
                new BrowserLabelCandidate(
                        "labels",
                        featureId,
                        new Coordinate(anchorX, 0),
                        marker(),
                        featureId,
                        profile,
                        ordinal),
                0,
                ordinal);
    }

    private static SceneLabelCandidate markerCandidate(
            String featureId,
            io.github.mundanej.map.api.Symbol symbol,
            PointLabelProfile profile,
            double anchorX,
            int ordinal) {
        return new SceneLabelCandidate(
                new BrowserLabelCandidate(
                        "labels",
                        featureId,
                        new Coordinate(anchorX, 0),
                        symbol,
                        featureId,
                        profile,
                        ordinal),
                0,
                ordinal);
    }

    private static PointLabelProfile profile(
            io.github.mundanej.map.api.LabelTextSource source,
            int priority,
            PointLabelPosition position,
            ResolutionRange range) {
        return new PointLabelProfile(
                source,
                new LabelTextStyle(Rgba.rgb(20, 30, 40), LabelWeight.BOLD, 14),
                List.of(position),
                0,
                0,
                0,
                0,
                priority,
                range,
                PointLabelAnchorBasis.FEATURE_POINT);
    }

    private static MundaneMap mapWithLabelSource() {
        MundaneMap map =
                new MundaneMap(System::nanoTime, Runnable::run, Runnable::run, Runnable::run);
        map.setViewport(VIEWPORT);
        map.setSnapshotLayers(
                List.of(
                        new InMemoryLayer(
                                "line",
                                "Line",
                                List.of(
                                        new Feature(
                                                "line",
                                                "Line",
                                                new LineStringGeometry(
                                                        CoordinateSequence.of(
                                                                new double[] {-10, 0, 10, 0})),
                                                Map.of(),
                                                SolidLineSymbol.of(
                                                        new SymbolStroke(
                                                                Rgba.rgb(1, 2, 3),
                                                                new SymbolLength(
                                                                        2,
                                                                        SymbolUnit.SCREEN_PIXEL)),
                                                        1))))));
        FeaturePortrayal portrayal =
                FeaturePortrayal.markers(new FixedSymbolSelector(marker()))
                        .withPointLabel(
                                profile(
                                        new TextAttribute("label"),
                                        0,
                                        PointLabelPosition.NE,
                                        ResolutionRange.ALL));
        map.setFeatureSourceBindings(
                List.of(
                        FeatureSourceBinding.owned(
                                "labels",
                                "Labels",
                                source(
                                        "capture",
                                        List.of(
                                                new FeatureRecord(
                                                        "point",
                                                        "Point",
                                                        new PointGeometry(new Coordinate(20, 10)),
                                                        Map.of("label", "Alpha")))),
                                portrayal,
                                Optional.empty())));
        return map;
    }

    private static void acceptCurrent(MundaneMap map) {
        long component = map.componentGenerationForTest();
        long scene = map.sceneGenerationForTest();
        long viewport = map.viewportGenerationForTest();
        map.acceptLabelMeasurements(
                1,
                (double) component,
                (double) scene,
                (double) viewport,
                new double[] {35, 0, -9, 35, 3});
        map.acceptPlacedLabels(1, (double) component, (double) scene, (double) viewport);
    }

    private static InMemoryFeatureSource source(String id, List<FeatureRecord> records) {
        return InMemoryFeatureSource.open(
                new SourceIdentity(id, id),
                records,
                Optional.empty(),
                Optional.of(
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857,
                                Optional.of("EPSG:3857"),
                                Optional.empty())),
                FeatureSourceLimits.LEVEL_1);
    }

    private static VectorMarkerSymbol marker() {
        VectorPath path =
                VectorPath.builder().moveTo(0, 0).lineTo(1, 0).lineTo(0, 1).close().build();
        return VectorMarkerSymbol.filledScreen(
                path, new Envelope(0, 0, 1, 1), Rgba.rgb(10, 20, 30), 8, 1);
    }

    private static SolidLineSymbol line() {
        return SolidLineSymbol.of(
                new SymbolStroke(
                        Rgba.rgb(40, 50, 60), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                1);
    }

    private record LabelLayer(
            String id,
            String name,
            List<Feature> features,
            List<BrowserLabelCandidate> browserLabelCandidates)
            implements Layer, BrowserLabelLayer {
        private LabelLayer {
            features = List.copyOf(features);
            browserLabelCandidates = List.copyOf(browserLabelCandidates);
        }

        @Override
        public Optional<Envelope> envelope() {
            return features.stream()
                    .map(feature -> feature.geometry().envelope())
                    .reduce(Envelope::union);
        }
    }

    private static final class RecordingSource implements FeatureSource {
        private final InMemoryFeatureSource delegate;
        private FeatureQuery lastQuery;

        private RecordingSource(InMemoryFeatureSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public FeatureSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            lastQuery = query;
            return delegate.openCursor(query, cancellation);
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
