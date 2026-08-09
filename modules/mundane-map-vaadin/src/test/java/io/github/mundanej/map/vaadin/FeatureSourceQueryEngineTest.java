package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
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
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.GraduatedSymbolSelector;
import io.github.mundanej.map.api.GraduatedSymbolStep;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.LabelTextStyle;
import io.github.mundanej.map.api.LabelWeight;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelAnchorBasis;
import io.github.mundanej.map.api.PointLabelPosition;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.PortrayalOperand;
import io.github.mundanej.map.api.PortrayalPredicate;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.ResolutionRange;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.ScaleInterval;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.MapViewport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class FeatureSourceQueryEngineTest {
    @Test
    void sharedWrapTranslationCoversEveryMultipartGeometryFamilyAndProfileMismatch() {
        List<Geometry> geometries =
                List.of(
                        new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                        MultiLineStringGeometry.ofParts(
                                List.of(
                                        CoordinateSequence.of(0, 0, 1, 1),
                                        CoordinateSequence.of(2, 2, 3, 3))),
                        MultiPolygonGeometry.ofPolygons(List.of(polygon(0), polygon(10))));
        for (Geometry geometry : geometries) {
            Geometry translated = BrowserWrapSupport.translate(geometry, 12);
            assertEquals(geometry.envelope().minX() + 12, translated.envelope().minX());
            assertEquals(geometry.envelope().maxX() + 12, translated.envelope().maxX());
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BrowserWrapSupport.validate(
                                HorizontalWrap.webMercator(),
                                CrsDefinitions.EPSG_4326,
                                new MapViewport(10, 10, 0, 0, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        BrowserWrapSupport.translate(
                                new PointGeometry(new Coordinate(0, 0)), Double.POSITIVE_INFINITY));
    }

    @Test
    void explicitWrapRepeatsVisualCopiesWhileSceneAndHitsRetainLogicalIdentity() {
        RecordingSource source =
                source(
                        "wrapped",
                        List.of(
                                new FeatureRecord(
                                        "logical",
                                        "Logical",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of())),
                        recognized3857());
        PointLabelProfile label =
                new PointLabelProfile(
                        FeatureName.INSTANCE,
                        new LabelTextStyle(Rgba.rgb(20, 30, 40), LabelWeight.NORMAL, 12),
                        List.of(PointLabelPosition.E),
                        0,
                        0,
                        0,
                        0,
                        0,
                        ResolutionRange.ALL,
                        PointLabelAnchorBasis.FEATURE_POINT);
        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(
                        "wrapped",
                        "wrapped",
                        source,
                        FeaturePortrayal.fixed(
                                        marker(),
                                        SolidLineSymbol.of(
                                                new SymbolStroke(
                                                        Rgba.rgb(20, 30, 40),
                                                        new SymbolLength(
                                                                2, SymbolUnit.SCREEN_PIXEL)),
                                                1),
                                        SolidFillSymbol.of(Rgba.rgb(40, 50, 60), 1))
                                .withPointLabel(label),
                        Optional.empty());
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        MapViewport viewport = new MapViewport(300, 100, 0, 0, wrap.period() / 100.0);

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());

        Layer layer = result.layers().getFirst();
        assertEquals(3, layer.features().size());
        assertEquals(
                List.of(-1L, 0L, 1L),
                java.util.stream.IntStream.range(0, 3)
                        .mapToObj(index -> BrowserLogicalLayer.copyIndex(layer, index))
                        .toList());
        assertTrue(
                java.util.stream.IntStream.range(0, 3)
                        .allMatch(
                                index ->
                                        BrowserLogicalLayer.logicalFeatureId(layer, index)
                                                .equals("logical")));
        assertEquals(3, ((BrowserLabelLayer) layer).browserLabelCandidates().size());

        SceneProtocol.Result scene =
                new SceneProtocol(SceneProtocol.DEFAULT_LIMITS)
                        .encode(List.of(layer), Rgba.rgb(255, 255, 255), viewport, 1, 2);
        Layer snapshot = scene.layers().getFirst();
        for (int index = 0; index < snapshot.features().size(); index++) {
            PointGeometry point =
                    assertInstanceOf(
                            PointGeometry.class, snapshot.features().get(index).geometry());
            Coordinate screen = viewport.worldToScreen(point.coordinate());
            assertEquals(
                    "logical",
                    BrowserSceneHits.hitTest(List.of(snapshot), viewport, screen.x(), screen.y(), 1)
                            .topmost()
                            .orElseThrow()
                            .featureId());
            BrowserSceneHits.VisualHit visual =
                    BrowserSceneHits.topmostVisual(
                                    List.of(snapshot), viewport, screen.x(), screen.y(), 1)
                            .orElseThrow();
            assertEquals(BrowserLogicalLayer.copyIndex(snapshot, index), visual.copyIndex());
            assertEquals(point, visual.feature().geometry());
        }
        assertEquals(
                snapshot.features(),
                BrowserLogicalLayer.matchingFeatures(List.of(snapshot), "wrapped", "logical"));
        List<?> encodedFeatures =
                (List<?>)
                        ((Map<?, ?>) ((List<?>) scene.scene().get("layers")).getFirst())
                                .get("features");
        assertEquals("logical", ((Map<?, ?>) encodedFeatures.get(1)).get("logicalId"));
        assertEquals(0L, ((Map<?, ?>) encodedFeatures.get(1)).get("copyIndex"));

        FeatureSourceBinding local = binding("local", source, AttributeSelection.NONE, false);
        FeatureSourceQueryEngine.Result isolated =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(local, true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());
        assertEquals(1, isolated.layers().getFirst().features().size());

        FeatureSourceQueryEngine.Result beyondProfile =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                new MapViewport(
                                        10,
                                        10,
                                        wrap.period()
                                                * (HorizontalWrap.COPY_INDEX_HARD_MAXIMUM + 1L),
                                        0,
                                        1),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());
        assertTrue(beyondProfile.layers().getFirst().features().isEmpty());
        assertEquals(
                "WORLD_WRAP_PRECISION_EXCEEDED",
                beyondProfile.reports().get("wrapped").entries().getLast().code());
    }

    @Test
    void aggregateWrappedOutputLimitUsesTheWorldWrapDiagnosticContract() {
        RecordingSource source =
                source(
                        "wrapped-limit-source",
                        List.of(record("logical", new PointGeometry(new Coordinate(0, 0)))),
                        recognized3857());
        io.github.mundanej.map.api.FeatureQueryLimits level =
                io.github.mundanej.map.api.FeatureQueryLimits.LEVEL_1;
        io.github.mundanej.map.api.FeatureQueryLimits twoRecords =
                new io.github.mundanej.map.api.FeatureQueryLimits(
                        level.recordsExamined(),
                        2,
                        level.coordinatesReturned(),
                        level.attributeValuesReturned(),
                        level.decodedTextCharactersReturned(),
                        level.ownedPayloadBytes(),
                        level.retainedWarnings());
        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(
                        "wrapped-limit",
                        "Wrapped limit",
                        source,
                        marker(),
                        SolidLineSymbol.of(
                                new SymbolStroke(
                                        Rgba.rgb(20, 30, 40),
                                        new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                                1),
                        SolidFillSymbol.of(Rgba.rgb(40, 50, 60), 1),
                        AttributeSelection.NONE,
                        Optional.of(twoRecords));
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        HorizontalWrap wrap = HorizontalWrap.webMercator();

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                new MapViewport(300, 10, 0, 0, wrap.period() / 100.0),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());

        assertTrue(result.layers().getFirst().features().isEmpty());
        SourceDiagnostic terminal = result.reports().get("wrapped-limit").entries().getLast();
        assertEquals("SOURCE_LIMIT_EXCEEDED", terminal.code());
        assertEquals("worldWrap", terminal.context().get("scope"));
        assertEquals("features", terminal.context().get("limit"));
        source.close();
        binding.close();
    }

    @Test
    void aggregateWrappedLabelsFailBeforeTheClosedPlacementCeiling() {
        List<FeatureRecord> records =
                java.util.stream.IntStream.range(0, 513)
                        .mapToObj(
                                index ->
                                        new FeatureRecord(
                                                "point-" + index,
                                                "Point",
                                                new PointGeometry(new Coordinate(0, 0)),
                                                Map.of()))
                        .toList();
        RecordingSource source = source("wrapped-label-limit-source", records, recognized3857());
        PointLabelProfile label =
                new PointLabelProfile(
                        FeatureName.INSTANCE,
                        new LabelTextStyle(Rgba.rgb(20, 30, 40), LabelWeight.NORMAL, 12),
                        List.of(PointLabelPosition.E),
                        0,
                        0,
                        0,
                        0,
                        0,
                        ResolutionRange.ALL,
                        PointLabelAnchorBasis.FEATURE_POINT);
        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(
                        "wrapped-label-limit",
                        "Wrapped label limit",
                        source,
                        FeaturePortrayal.fixed(
                                        marker(),
                                        SolidLineSymbol.of(
                                                new SymbolStroke(
                                                        Rgba.rgb(20, 30, 40),
                                                        new SymbolLength(
                                                                2, SymbolUnit.SCREEN_PIXEL)),
                                                1),
                                        SolidFillSymbol.of(Rgba.rgb(40, 50, 60), 1))
                                .withPointLabel(label),
                        Optional.empty());
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        HorizontalWrap wrap = HorizontalWrap.webMercator();

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                new MapViewport(799, 10, wrap.period() / 2, 0, wrap.period() / 100),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());

        assertTrue(result.layers().getFirst().features().isEmpty());
        SourceDiagnostic terminal = result.reports().get("wrapped-label-limit").entries().getLast();
        assertEquals("SOURCE_LIMIT_EXCEEDED", terminal.code());
        assertEquals("worldWrap", terminal.context().get("scope"));
        assertEquals("labels", terminal.context().get("limit"));
        source.close();
        binding.close();
    }

    @Test
    void geographicSeamSplitsLinesAndPolygonsWithoutInventingEndpointMarkers() {
        VectorMarkerSymbol endpoint = marker(Rgba.rgb(220, 30, 40));
        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 30, 40), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        Optional.of(endpoint),
                        Optional.of(endpoint),
                        1);
        PolygonGeometry polygon =
                new PolygonGeometry(
                        CoordinateSequence.of(170, -10, -170, -10, -170, 10, 170, 10, 170, -10),
                        List.of(CoordinateSequence.of(172, -5, 178, -5, 178, 5, 172, 5, 172, -5)));
        RecordingSource source =
                source(
                        "seam",
                        List.of(
                                new FeatureRecord(
                                        "line",
                                        "Line",
                                        new LineStringGeometry(
                                                CoordinateSequence.of(170, 0, -170, 0)),
                                        Map.of()),
                                new FeatureRecord("polygon", "Polygon", polygon, Map.of())),
                        geographicCrs());
        SymbolStroke hatchStroke =
                new SymbolStroke(
                        Rgba.rgb(60, 70, 80), new SymbolLength(1, SymbolUnit.SCREEN_PIXEL));
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.FORWARD_DIAGONAL,
                        hatchStroke,
                        new SymbolLength(8, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.MAP_RELATIVE,
                        Optional.of(line),
                        1,
                        8_192);
        CompositeSymbol fill =
                CompositeSymbol.of(
                        List.of(
                                hatch,
                                SolidFillSymbol.of(Rgba.rgb(40, 50, 60), Optional.of(line), 0.5)),
                        0.75);
        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(
                        "seam",
                        "Seam",
                        source,
                        FeaturePortrayal.fixed(marker(), line, fill),
                        Optional.empty());
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        MapViewport viewport =
                new MapViewport(400, 200, wrap.canonicalMaximumX(), 0, wrap.period() / 7_200.0);

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_4326,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());

        List<Feature> lineCopies =
                java.util.stream.IntStream.range(0, result.layers().getFirst().features().size())
                        .filter(
                                index ->
                                        BrowserLogicalLayer.logicalFeatureId(
                                                        result.layers().getFirst(), index)
                                                .equals("line"))
                        .mapToObj(index -> result.layers().getFirst().features().get(index))
                        .toList();
        assertEquals(
                2,
                lineCopies.size(),
                () -> result.reports() + " " + result.layers().getFirst().features());
        assertEquals(
                1,
                lineCopies.stream()
                        .map(feature -> assertInstanceOf(SolidLineSymbol.class, feature.symbol()))
                        .filter(symbol -> symbol.startMarker().isPresent())
                        .count());
        assertEquals(
                1,
                lineCopies.stream()
                        .map(feature -> assertInstanceOf(SolidLineSymbol.class, feature.symbol()))
                        .filter(symbol -> symbol.endMarker().isPresent())
                        .count());
        List<Geometry> polygonCopies =
                java.util.stream.IntStream.range(0, result.layers().getFirst().features().size())
                        .filter(
                                index ->
                                        BrowserLogicalLayer.logicalFeatureId(
                                                        result.layers().getFirst(), index)
                                                .equals("polygon"))
                        .mapToObj(index -> result.layers().getFirst().features().get(index))
                        .map(Feature::geometry)
                        .toList();
        assertTrue(polygonCopies.stream().anyMatch(PolygonGeometry.class::isInstance));
        assertTrue(
                polygonCopies.stream()
                        .anyMatch(
                                geometry ->
                                        geometry instanceof LineStringGeometry
                                                || geometry instanceof MultiLineStringGeometry));
    }

    @Test
    void conflictingSplitQueryIdentityFailsWithTheStableSourceCode() {
        SplitConflictSource source = new SplitConflictSource();
        FeatureSourceBinding binding =
                binding("split-conflict", source, AttributeSelection.NONE, false);
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        HorizontalWrap wrap = HorizontalWrap.webMercator();

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                new MapViewport(
                                        100,
                                        20,
                                        wrap.canonicalMaximumX(),
                                        0,
                                        wrap.period() / 1_000.0),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());

        assertTrue(result.layers().getFirst().features().isEmpty());
        assertEquals(
                "SOURCE_DUPLICATE_FEATURE_ID",
                result.reports().get("split-conflict").entries().getLast().code());
        source.close();
        binding.close();
    }

    @Test
    void ambiguousGeographicHoleFailsTheWholeRepeatedBindingWithStableDiagnostics() {
        PolygonGeometry ambiguous =
                new PolygonGeometry(
                        CoordinateSequence.of(170, -10, -170, -10, -170, 10, 170, 10, 170, -10),
                        List.of(
                                CoordinateSequence.of(
                                        175, -5, -175, -5, -175, 5, 175, 5, 175, -5)));
        RecordingSource source =
                source("ambiguous", List.of(record("ambiguous", ambiguous)), geographicCrs());
        FeatureSourceBinding binding = binding("ambiguous", source, AttributeSelection.NONE, false);
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        HorizontalWrap wrap = HorizontalWrap.webMercator();

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                new MapViewport(
                                        400,
                                        200,
                                        wrap.canonicalMaximumX(),
                                        0,
                                        wrap.period() / 720.0),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_4326,
                                CrsDefinitions.EPSG_3857,
                                Optional.of(wrap),
                                CancellationToken.none());

        assertTrue(result.layers().getFirst().features().isEmpty());
        assertEquals(
                "WORLD_WRAP_GEOMETRY_UNSUPPORTED",
                result.reports().get("ambiguous").entries().getLast().code());
        assertEquals(
                "ambiguousHole",
                result.reports().get("ambiguous").entries().getLast().context().get("reason"));
    }

    @Test
    void evaluatesExactProjectionZoomScaleAndOmissionWithTheCoreResolver() {
        VectorMarkerSymbol selected = marker(Rgba.rgb(210, 30, 30));
        RecordingSource zoomSource =
                source(
                        "zoom",
                        List.of(
                                new FeatureRecord(
                                        "zoom",
                                        "zoom",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of("ignored", 9L))),
                        recognized3857());
        FeaturePortrayal zoomPortrayal =
                FeaturePortrayal.markers(
                        GraduatedSymbolSelector.zoom(
                                List.of(new GraduatedSymbolStep(BigDecimal.valueOf(5), selected)),
                                Optional.empty(),
                                Optional.empty()));
        FeatureSourceBinding zoomBinding =
                FeatureSourceBinding.borrowed(
                        "zoom", "Zoom", zoomSource, zoomPortrayal, Optional.empty());
        double zoomFiveUnits = CrsDefinitions.EPSG_3857.coordinateDomain().width() / (512.0 * 32.0);

        FeatureSourceQueryEngine.Result visible =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                zoomBinding, true)),
                                new MapViewport(100, 100, 0, 0, zoomFiveUnits),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertEquals(selected, visible.layers().getFirst().features().getFirst().symbol());
        assertEquals(AttributeSelection.NONE, zoomSource.lastQuery.attributes());

        FeatureSourceQueryEngine.Result omitted =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                zoomBinding, true)),
                                new MapViewport(100, 100, 0, 0, zoomFiveUnits * 2),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertTrue(omitted.layers().getFirst().features().isEmpty());
        assertTrue(omitted.layers().getFirst().envelope().isPresent());
        SceneProtocol.Result omittedScene =
                new SceneProtocol(SceneProtocol.DEFAULT_LIMITS)
                        .encode(
                                omitted.layers(),
                                Rgba.rgb(255, 255, 255),
                                new MapViewport(100, 100, 0, 0, zoomFiveUnits * 2),
                                1,
                                2);
        Map<?, ?> omittedLayer =
                (Map<?, ?>) ((List<?>) omittedScene.scene().get("layers")).getFirst();
        assertTrue(((List<?>) omittedLayer.get("features")).isEmpty());
        assertTrue(omittedScene.envelope().isPresent());

        RecordingSource scaleSource =
                source(
                        "scale",
                        List.of(
                                new FeatureRecord(
                                        "scale",
                                        "scale",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of(
                                                "nullable",
                                                io.github.mundanej.map.api.AttributeNull.INSTANCE,
                                                "ignored",
                                                "value"))),
                        recognized3857());
        PortrayalRule scaleRule =
                new PortrayalRule(
                        Optional.empty(),
                        new ScaleInterval(OptionalDouble.of(3_000), OptionalDouble.of(4_000)),
                        Optional.of(
                                new PortrayalPredicate.IsNull(
                                        new PortrayalOperand.Property("nullable"))),
                        false,
                        List.of(selected),
                        List.of(),
                        List.of());
        FeatureSourceBinding scaleBinding =
                FeatureSourceBinding.borrowed(
                        "scale",
                        "Scale",
                        scaleSource,
                        new RulePortrayalPlan(List.of(scaleRule)).portrayal(),
                        Optional.empty());
        FeatureSourceQueryEngine.Result scaleVisible =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                scaleBinding, true)),
                                new MapViewport(100, 100, 0, 0, 1),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertEquals(selected, scaleVisible.layers().getFirst().features().getFirst().symbol());
        assertEquals(
                AttributeSelection.only(List.of("nullable")), scaleSource.lastQuery.attributes());
    }

    @Test
    void queriesAndTransformsEveryGeometryFamilyInSourceOrder() {
        PolygonGeometry polygon = polygon(0);
        List<FeatureRecord> records =
                List.of(
                        record("point", new PointGeometry(new Coordinate(0, 0))),
                        record(
                                "multipoint",
                                new MultiPointGeometry(CoordinateSequence.of(0, 0, 1, 1))),
                        record("line", new LineStringGeometry(CoordinateSequence.of(0, 0, 2, 2))),
                        record(
                                "multiline",
                                MultiLineStringGeometry.ofParts(
                                        List.of(
                                                CoordinateSequence.of(0, 0, 1, 1),
                                                CoordinateSequence.of(2, 2, 3, 3)))),
                        record("polygon", polygon),
                        record(
                                "multipolygon",
                                MultiPolygonGeometry.ofPolygons(List.of(polygon, polygon(4)))));
        RecordingSource source = source("families", records, recognized3857());
        FeatureSourceBinding binding = binding("families", source, AttributeSelection.NONE, false);

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                new MapViewport(100, 100, 0, 0, 1),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());

        assertFalse(result.cancelled());
        assertTrue(result.reports().isEmpty());
        assertEquals(
                List.of("point", "multipoint", "line", "multiline", "polygon", "multipolygon"),
                result.layers().getFirst().features().stream()
                        .map(feature -> feature.id())
                        .toList());
        assertInstanceOf(
                MultiPolygonGeometry.class,
                result.layers().getFirst().features().getLast().geometry());
        PolygonGeometry transformedPolygon =
                (PolygonGeometry) result.layers().getFirst().features().get(4).geometry();
        assertEquals(1, transformedPolygon.holes().size());
        MultiPolygonGeometry transformedMultiPolygon =
                (MultiPolygonGeometry) result.layers().getFirst().features().getLast().geometry();
        assertEquals(4, transformedMultiPolygon.ringCount());
        assertEquals(AttributeSelection.NONE, source.lastQuery.attributes());
        assertEquals(1, source.maximumLiveCursors);
        assertEquals(0, source.liveCursors);
    }

    @Test
    void convertsDisplayEnvelopeToSourceCrsAndReportsMissingMetadata() {
        RecordingSource geographic =
                source(
                        "geographic",
                        List.of(record("origin", new PointGeometry(new Coordinate(0, 0)))),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.of("EPSG:4326"),
                                        Optional.empty())));
        FeatureSourceBinding binding =
                binding(
                        "geographic",
                        geographic,
                        AttributeSelection.only(List.of("needed")),
                        false);
        MapViewport viewport = new MapViewport(200, 100, 0, 0, 1000);

        FeatureSourceQueryEngine.Result transformed =
                engine().query(
                                List.of(new FeatureSourceQueryEngine.RequestBinding(binding, true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());

        Envelope expected =
                CrsRegistry.level1()
                        .operation(CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_4326)
                        .transformQueryEnvelope(viewport.visibleWorldEnvelope())
                        .transformedEnvelope()
                        .orElseThrow();
        assertEquals(expected, geographic.lastQuery.sourceBounds().orElseThrow());
        assertEquals(AttributeSelection.only(List.of("needed")), geographic.lastQuery.attributes());
        PointGeometry origin =
                (PointGeometry) transformed.layers().getFirst().features().getFirst().geometry();
        assertEquals(0, origin.coordinate().x(), 1.0e-9);
        assertEquals(0, origin.coordinate().y(), 1.0e-9);

        RecordingSource missing = source("missing", List.of(), Optional.empty());
        FeatureSourceQueryEngine.Result failed =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                binding(
                                                        "missing",
                                                        missing,
                                                        AttributeSelection.NONE,
                                                        false),
                                                true)),
                                viewport,
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertEquals(
                "CRS_METADATA_MISSING", failed.reports().get("missing").entries().getLast().code());
    }

    @Test
    void skipsInvisibleBindingsWithoutOpeningACursor() {
        RecordingSource source = source("hidden", List.of(), recognized3857());
        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                binding(
                                                        "hidden",
                                                        source,
                                                        AttributeSelection.NONE,
                                                        false),
                                                false)),
                                MapViewport.initial(10, 10),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertTrue(result.layers().getFirst().features().isEmpty());
        assertEquals(0, source.openedCursors);
    }

    @Test
    void reportsUnknownUnavailableAndStrictDomainOperations() {
        RecordingSource unknown =
                source(
                        "unknown",
                        List.of(),
                        Optional.of(
                                CrsMetadata.unknown(
                                        Optional.of("LOCAL:UNKNOWN"), Optional.empty())));
        FeatureSourceQueryEngine.Result unknownResult =
                query(
                        unknown,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        new MapViewport(10, 10, 0, 0, 1));
        assertEquals(
                "CRS_DEFINITION_UNKNOWN",
                unknownResult.reports().get("unknown").entries().getLast().code());

        CrsRegistry definitionsOnly =
                CrsRegistry.builder()
                        .registerDefinition(CrsDefinitions.EPSG_4326, List.of())
                        .registerDefinition(CrsDefinitions.EPSG_3857, List.of())
                        .build();
        RecordingSource unsupported =
                source(
                        "unsupported",
                        List.of(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.of("EPSG:4326"),
                                        Optional.empty())));
        FeatureSourceQueryEngine.Result unsupportedResult =
                query(
                        unsupported,
                        definitionsOnly,
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        new MapViewport(10, 10, 0, 0, 1));
        assertEquals(
                "CRS_TRANSFORM_UNAVAILABLE",
                unsupportedResult.reports().get("unsupported").entries().getLast().code());

        RecordingSource geographic = source("geographic-domain", List.of(), geographicCrs());
        FeatureSourceQueryEngine.Result clipped =
                query(
                        geographic,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_4326,
                        CrsDefinitions.EPSG_4326,
                        new MapViewport(10, 10, 0, 85, 2));
        assertEquals(
                "CRS_QUERY_ENVELOPE_CLIPPED",
                clipped.reports().get("geographic-domain").entries().getLast().code());
        FeatureSourceQueryEngine.Result outside =
                query(
                        geographic,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_4326,
                        CrsDefinitions.EPSG_4326,
                        new MapViewport(10, 10, 0, 200, 1));
        assertEquals(
                "CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN",
                outside.reports().get("geographic-domain").entries().getLast().code());
        assertEquals(1, geographic.openedCursors);
    }

    @Test
    void cancelsDuringOneLargeGeometryTransformation() {
        double[] packed = new double[200];
        for (int index = 0; index < packed.length; index += 2) {
            packed[index] = index;
            packed[index + 1] = index;
        }
        StaticSource source =
                new StaticSource(
                        "cancellation",
                        record("large", new LineStringGeometry(CoordinateSequence.of(packed))),
                        DiagnosticReport.empty(),
                        false);
        AtomicInteger checks = new AtomicInteger();

        FeatureSourceQueryEngine.Result result =
                engine().query(
                                List.of(
                                        new FeatureSourceQueryEngine.RequestBinding(
                                                binding(
                                                        "cancellation",
                                                        source,
                                                        AttributeSelection.NONE,
                                                        false),
                                                true)),
                                new MapViewport(100, 100, 0, 0, 10),
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_3857,
                                () -> checks.incrementAndGet() > 4);

        assertTrue(result.cancelled());
        assertEquals(5, checks.get());
        assertTrue(source.cursorClosed);
    }

    @Test
    void retainsOpeningWarningsWhenUnexpectedCursorFailureOccurs() {
        DiagnosticReport opening =
                new DiagnosticReport(
                        List.of(
                                new SourceDiagnostic(
                                        "SOURCE_OPENING_WARNING",
                                        DiagnosticSeverity.WARNING,
                                        "runtime",
                                        Optional.of(DiagnosticLocation.empty()),
                                        "Opening warning",
                                        Map.of())),
                        0);
        StaticSource source =
                new StaticSource(
                        "runtime",
                        record("unused", new PointGeometry(new Coordinate(0, 0))),
                        opening,
                        true);

        FeatureSourceQueryEngine.Result result =
                query(
                        source,
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_3857,
                        CrsDefinitions.EPSG_3857,
                        new MapViewport(10, 10, 0, 0, 1));

        assertEquals(
                List.of("SOURCE_OPENING_WARNING", "SOURCE_QUERY_FAILED"),
                result.reports().get("runtime").entries().stream()
                        .map(SourceDiagnostic::code)
                        .toList());
    }

    private static FeatureSourceQueryEngine engine() {
        return new FeatureSourceQueryEngine();
    }

    private static Optional<CrsMetadata> recognized3857() {
        return Optional.of(
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty()));
    }

    private static Optional<CrsMetadata> geographicCrs() {
        return Optional.of(
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_4326, Optional.of("EPSG:4326"), Optional.empty()));
    }

    private static FeatureSourceQueryEngine.Result query(
            FeatureSource source,
            CrsRegistry registry,
            io.github.mundanej.map.api.CrsDefinition mapCrs,
            io.github.mundanej.map.api.CrsDefinition displayCrs,
            MapViewport viewport) {
        return engine().query(
                        List.of(
                                new FeatureSourceQueryEngine.RequestBinding(
                                        binding(
                                                source.metadata().identity().id(),
                                                source,
                                                AttributeSelection.NONE,
                                                false),
                                        true)),
                        viewport,
                        registry,
                        mapCrs,
                        displayCrs,
                        CancellationToken.none());
    }

    private static FeatureRecord record(String id, Geometry geometry) {
        return new FeatureRecord(id, id, geometry, Map.of("needed", "value", "ignored", 2L));
    }

    private static PolygonGeometry polygon(double offset) {
        return new PolygonGeometry(
                CoordinateSequence.of(
                        offset,
                        offset,
                        offset + 4,
                        offset,
                        offset + 4,
                        offset + 4,
                        offset,
                        offset + 4,
                        offset,
                        offset),
                List.of(
                        CoordinateSequence.of(
                                offset + 1,
                                offset + 1,
                                offset + 2,
                                offset + 1,
                                offset + 2,
                                offset + 2,
                                offset + 1,
                                offset + 2,
                                offset + 1,
                                offset + 1)));
    }

    private static FeatureSourceBinding binding(
            String id, FeatureSource source, AttributeSelection attributes, boolean owned) {
        BindingFactory factory =
                owned ? FeatureSourceBinding::owned : FeatureSourceBinding::borrowed;
        return factory.create(
                id,
                id,
                source,
                marker(),
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(20, 30, 40), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1),
                SolidFillSymbol.of(Rgba.rgb(40, 50, 60), 1),
                attributes,
                Optional.empty());
    }

    private static VectorMarkerSymbol marker() {
        return marker(Rgba.rgb(10, 20, 30));
    }

    private static VectorMarkerSymbol marker(Rgba fill) {
        VectorPath path =
                VectorPath.builder().moveTo(0, 0).lineTo(1, 0).lineTo(0, 1).close().build();
        return VectorMarkerSymbol.filledScreen(path, new Envelope(0, 0, 1, 1), fill, 8, 1);
    }

    @FunctionalInterface
    private interface BindingFactory {
        FeatureSourceBinding create(
                String id,
                String name,
                FeatureSource source,
                VectorMarkerSymbol marker,
                SolidLineSymbol line,
                SolidFillSymbol fill,
                AttributeSelection attributes,
                Optional<io.github.mundanej.map.api.FeatureQueryLimits> tighterLimits);
    }

    private static final class SplitConflictSource implements FeatureSource {
        private final InMemoryFeatureSource delegate =
                InMemoryFeatureSource.open(
                        new SourceIdentity("split-conflict-source", "Split conflict source"),
                        List.of(),
                        Optional.empty(),
                        recognized3857(),
                        FeatureSourceLimits.LEVEL_1);
        private int cursors;

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
            return DiagnosticReport.empty();
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            int ordinal = cursors++;
            FeatureRecord record =
                    new FeatureRecord(
                            "same-id",
                            "Same ID",
                            new PointGeometry(new Coordinate(ordinal, 0)),
                            Map.of());
            return new FeatureCursor() {
                private boolean returned;
                private boolean closed;

                @Override
                public boolean advance() {
                    if (closed || returned) {
                        return false;
                    }
                    returned = true;
                    return true;
                }

                @Override
                public FeatureRecord current() {
                    if (!returned || closed) {
                        throw new IllegalStateException("cursor is not positioned");
                    }
                    return record;
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

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class RecordingSource implements FeatureSource {
        private final InMemoryFeatureSource delegate;
        private FeatureQuery lastQuery;
        private int liveCursors;
        private int maximumLiveCursors;
        private int openedCursors;

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
            openedCursors++;
            liveCursors++;
            maximumLiveCursors = Math.max(maximumLiveCursors, liveCursors);
            FeatureCursor cursor = delegate.openCursor(query, cancellation);
            return new FeatureCursor() {
                private boolean closed;

                @Override
                public boolean advance() {
                    return cursor.advance();
                }

                @Override
                public FeatureRecord current() {
                    return cursor.current();
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return cursor.diagnostics();
                }

                @Override
                public boolean isClosed() {
                    return closed;
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        cursor.close();
                        liveCursors--;
                    }
                }
            };
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

    private static final class StaticSource implements FeatureSource {
        private final InMemoryFeatureSource metadataDelegate;
        private final FeatureRecord record;
        private final DiagnosticReport opening;
        private final boolean failOnOpen;
        private boolean closed;
        private boolean cursorClosed;

        private StaticSource(
                String id, FeatureRecord record, DiagnosticReport opening, boolean failOnOpen) {
            metadataDelegate =
                    InMemoryFeatureSource.open(
                            new SourceIdentity(id, id),
                            List.of(),
                            Optional.empty(),
                            recognized3857(),
                            FeatureSourceLimits.LEVEL_1);
            this.record = record;
            this.opening = opening;
            this.failOnOpen = failOnOpen;
        }

        @Override
        public FeatureSourceMetadata metadata() {
            return metadataDelegate.metadata();
        }

        @Override
        public FeatureSourceLimits limits() {
            return FeatureSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return opening;
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            if (failOnOpen) {
                throw new IllegalStateException("deliberate cursor failure");
            }
            return new FeatureCursor() {
                private boolean advanced;

                @Override
                public boolean advance() {
                    if (advanced) {
                        return false;
                    }
                    advanced = true;
                    return true;
                }

                @Override
                public FeatureRecord current() {
                    if (!advanced) {
                        throw new IllegalStateException("cursor is not positioned");
                    }
                    return record;
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
            metadataDelegate.close();
        }
    }

    private static RecordingSource source(
            String id, List<FeatureRecord> records, Optional<CrsMetadata> crs) {
        return new RecordingSource(
                InMemoryFeatureSource.open(
                        new SourceIdentity(id, id),
                        records,
                        Optional.empty(),
                        crs,
                        FeatureSourceLimits.LEVEL_1));
    }
}
