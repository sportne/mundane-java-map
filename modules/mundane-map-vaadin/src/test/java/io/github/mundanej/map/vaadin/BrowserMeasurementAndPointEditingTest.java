package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureEditCause;
import io.github.mundanej.map.api.FeatureEditEvent;
import io.github.mundanej.map.api.FeatureEditHistoryLimits;
import io.github.mundanej.map.api.FeatureEditLimits;
import io.github.mundanej.map.api.FeatureEditSnapshot;
import io.github.mundanej.map.api.FeatureEditStatus;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MeasurementPhase;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointFeatureDraft;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.ScaleInterval;
import io.github.mundanej.map.api.SnapFeature;
import io.github.mundanej.map.api.SnapLimits;
import io.github.mundanej.map.api.SnapReferenceLayer;
import io.github.mundanej.map.api.SnapReferenceSet;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.DistanceStrategies;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class BrowserMeasurementAndPointEditingTest {
    @Test
    void measuresPlanarPreviewCompletionUndoAndCancelThroughBrowserEvents() {
        MundaneMap map = configuredMap();
        BrowserMeasurementTool tool =
                new BrowserMeasurementTool(
                        map, DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
        map.setActiveTool(tool);

        interaction(map, 0, "CLICK", 50, 50, 1, 0, 1, "");
        interaction(map, 1, "MOVE", 53, 46, 0, 0, 0, "");
        assertEquals(MeasurementPhase.MEASURING, tool.state().phase());
        assertEquals(5.0, tool.state().displayedDistance().metres());
        assertEquals(new Coordinate(3, 4), tool.state().preview().orElseThrow());

        interaction(map, 2, "CLICK", 53, 46, 1, 0, 1, "");
        interaction(map, 3, "CLICK", 53, 46, 1, 0, 2, "");
        assertEquals(MeasurementPhase.COMPLETE, tool.state().phase());
        assertEquals(5.0, tool.state().committedDistance().metres());

        map.acceptMapCommand(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                4,
                "DELETE_BACKWARD");
        assertEquals(1, tool.state().vertexCount());
        interaction(map, 5, "CANCEL", 53, 46, 0, 0, 0, "USER_CANCEL");
        assertEquals(MeasurementPhase.EMPTY, tool.state().phase());
    }

    @Test
    void wrappedBrowserMeasurementCommitsCanonicalCoordinateAndRetainsVisualCopy() {
        MundaneMap map = configuredMap();
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        map.setHorizontalWrap(wrap);
        map.setViewport(new MapViewport(100, 100, wrap.period(), 0, 1));
        BrowserMeasurementTool tool =
                new BrowserMeasurementTool(
                        map, DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
        map.setActiveTool(tool);

        interaction(map, 0, "CLICK", 50, 50, 1, 0, 1, "");
        interaction(map, 1, "MOVE", 50, 50, 0, 0, 0, "");

        assertEquals(new Coordinate(0, 0), tool.state().vertex(0));
        MultiPointGeometry visual =
                (MultiPointGeometry)
                        tool.overlayLayers().getFirst().features().getFirst().geometry();
        assertEquals(wrap.period(), visual.coordinates().x(0), 0.000001);
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        interaction(map, 2, "MOVE", 50, 50, 0, 0, 0, "");
        LineStringGeometry movedPreview =
                (LineStringGeometry)
                        tool.overlayLayers().stream()
                                .flatMap(layer -> layer.features().stream())
                                .map(Feature::geometry)
                                .filter(LineStringGeometry.class::isInstance)
                                .findFirst()
                                .orElseThrow();
        assertEquals(0.0, movedPreview.coordinates().x(1), 0.000001);
        map.close();
    }

    @Test
    void repeatingEditBindingAutomaticallyUsesTheHostCanonicalWrapProfile() {
        MundaneMap map = configuredMap();
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        map.setHorizontalWrap(wrap);
        map.setViewport(new MapViewport(100, 100, wrap.period(), 0, 1));
        FeatureEditBinding binding = binding("wrapped-edit", List.of());
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        FeatureEditBinding unrelated = binding("unrelated-wrapped-edit", List.of());
        unrelated.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        map.setFeatureEditBindings(List.of(binding, unrelated));
        BrowserPointEditController editor = new BrowserPointEditController(map, binding);
        map.setActiveTool(editor);
        editor.create(new PointFeatureDraft("created", "Created", Map.of()));

        interaction(map, 0, "MOVE", 50, 50, 0, 0, 0, "");
        Feature preview = editor.overlayLayers().getFirst().features().getFirst();
        assertEquals(
                wrap.period(), ((PointGeometry) preview.geometry()).coordinate().x(), 0.000001);
        interaction(map, 1, "CLICK", 50, 50, 1, 0, 1, "");
        assertEquals(new Coordinate(0, 0), point(binding, "created"));
        map.close();
        unrelated.close();
    }

    @Test
    void wrappedEditableOutputUsesTheConfiguredEditFeatureLimit() {
        MundaneMap map = configuredMap();
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        map.setHorizontalWrap(wrap);
        map.setViewport(new MapViewport(300, 20, 0, 0, wrap.period() / 100));
        FeatureEditBinding binding =
                FeatureEditBinding.open(
                        "low-limit-edit",
                        "Low limit edit",
                        new FeatureEditSnapshot(
                                0, CrsDefinitions.EPSG_3857, List.of(record("one", 0, 0))),
                        FeatureEditLimits.DEFAULT.withMaximumFeatures(1),
                        FeatureEditHistoryLimits.DEFAULT,
                        FeaturePortrayal.markers(
                                new FixedSymbolSelector(
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.CIRCLE,
                                                Rgba.rgb(40, 80, 180),
                                                12,
                                                1))),
                        NamedSymbolCatalog.of(List.of()));
        binding.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);

        io.github.mundanej.map.api.FeatureEditConfigurationException failure =
                assertThrows(
                        io.github.mundanej.map.api.FeatureEditConfigurationException.class,
                        () -> map.setFeatureEditBindings(List.of(binding)));

        assertEquals("EDIT_FEATURE_LIMIT_EXCEEDED", failure.problem().code());
        assertEquals("1", failure.problem().context().get("maximum"));
        assertEquals("2", failure.problem().context().get("actual"));
        binding.close();
        map.close();
    }

    @Test
    void localEditTargetAcceptsDeclaredRepeatingExternalSnapLayer() {
        MundaneMap map = configuredMap();
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        map.setHorizontalWrap(wrap);
        map.setViewport(new MapViewport(100, 100, wrap.period(), 0, 1));
        FeatureEditBinding target = binding("local-target", List.of());
        FeatureEditBinding reference =
                binding("repeating-reference", List.of(record("snap", 20, 0)));
        reference.setHorizontalWrapMode(BrowserHorizontalWrapMode.REPEAT_X);
        map.setFeatureEditBindings(List.of(target, reference));
        SnapFeature snap = new SnapFeature("snap", new PointGeometry(new Coordinate(20, 0)));
        SnapReferenceSet references =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(new SnapReferenceLayer("repeating-reference", List.of(snap))));

        BrowserPointEditController editor =
                new BrowserPointEditController(
                        map, target, references, BrowserPointEditController.BROWSER_SNAP_LIMITS, 8);
        editor.create(new PointFeatureDraft("created", "Created", Map.of()));
        map.setActiveTool(editor);
        interaction(map, 0, "MOVE", 70, 50, 0, 0, 0, "");
        interaction(map, 1, "CLICK", 70, 50, 1, 0, 1, "");

        assertEquals(new Coordinate(20, 0), point(target, "created"));
        map.close();
        target.close();
        reference.close();
    }

    @Test
    void measuresRecognizedGeographicDatelineWithTheExistingStrategy() {
        MundaneMap map = new MundaneMap();
        map.setCoordinateReferenceSystems(
                CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_4326);
        BrowserMeasurementTool tool =
                new BrowserMeasurementTool(
                        map, DistanceStrategies.epsg4326GreatCircle(CrsDefinitions.EPSG_4326));
        TestToolContext context = new TestToolContext(CrsDefinitions.EPSG_4326);
        tool.onActivate(context);
        tool.onMapToolEvent(
                toolEvent(1, MapToolEvent.Type.CLICK, new Coordinate(179, 0), 1), context);
        tool.onMapToolEvent(
                toolEvent(2, MapToolEvent.Type.CLICK, new Coordinate(-179, 0), 1), context);

        double expected =
                tool.distanceStrategy()
                        .distance(new Coordinate(179, 0), new Coordinate(-179, 0))
                        .metres();
        assertEquals(expected, tool.state().committedDistance().metres());
        assertTrue(expected < 250_000);
    }

    @Test
    void createsSnapsMovesDeletesAndReplaysImmutableEditSnapshots() {
        MundaneMap map = configuredMap();
        map.setSnapshotLayers(List.of(referenceLayer()));
        FeatureEditBinding binding = binding("editable", List.of(record("origin", 0, 0)));
        List<FeatureEditEvent> events = new CopyOnWriteArrayList<>();
        binding.addFeatureEditListener(events::add);
        map.setFeatureEditBindings(List.of(binding));
        SnapReferenceSet references =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "reference",
                                        List.of(
                                                new SnapFeature(
                                                        "snap",
                                                        new PointGeometry(
                                                                new Coordinate(20, 0)))))));
        BrowserPointEditController editor =
                new BrowserPointEditController(
                        map,
                        binding,
                        references,
                        BrowserPointEditController.BROWSER_SNAP_LIMITS,
                        BrowserPointEditController.DEFAULT_SNAP_TOLERANCE_PIXELS);
        map.setActiveTool(editor);

        editor.create(new PointFeatureDraft("created", "Created", Map.of("kind", "test")));
        interaction(map, 0, "MOVE", 71, 50, 0, 0, 0, "");
        assertTrue(editor.preview().orElseThrow().snapped());
        interaction(map, 1, "CLICK", 71, 50, 1, 0, 1, "");
        assertEquals(new Coordinate(20, 0), point(binding, "created"));
        assertEquals(new FeatureSelection("editable", "created"), map.selection().orElseThrow());

        editor.moveSelected();
        interaction(map, 2, "PRESS", 70, 50, 1, 1, 1, "");
        interaction(map, 3, "DRAG", 80, 50, 0, 1, 0, "");
        interaction(map, 4, "RELEASE", 80, 50, 1, 0, 1, "");
        assertEquals(new Coordinate(30, 0), point(binding, "created"));

        assertEquals(FeatureEditStatus.APPLIED, editor.deleteSelected().status());
        assertFalse(has(binding, "created"));
        assertEquals(FeatureEditStatus.APPLIED, editor.undo().status());
        assertEquals(new Coordinate(30, 0), point(binding, "created"));
        assertEquals(FeatureEditStatus.APPLIED, editor.redo().status());
        assertFalse(has(binding, "created"));

        command(map, 6, "UNDO");
        assertTrue(has(binding, "created"));
        command(map, 7, "REDO");
        assertFalse(has(binding, "created"));
        assertEquals(
                List.of(
                        FeatureEditCause.COMMIT,
                        FeatureEditCause.COMMIT,
                        FeatureEditCause.COMMIT,
                        FeatureEditCause.UNDO,
                        FeatureEditCause.REDO,
                        FeatureEditCause.UNDO,
                        FeatureEditCause.REDO),
                events.stream().map(FeatureEditEvent::cause).toList());
        assertEquals(
                List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L),
                events.stream().map(event -> event.current().revision()).toList());
    }

    @Test
    void rejectsStaleGestureAndCleansBindingAndPreviewLifecycle() {
        MundaneMap map = configuredMap();
        FeatureEditBinding binding = binding("editable", List.of(record("point", 0, 0)));
        map.setFeatureEditBindings(List.of(binding));
        map.setSelection(new FeatureSelection("editable", "point"));
        BrowserPointEditController editor = new BrowserPointEditController(map, binding);
        editor.moveSelected();
        map.setActiveTool(editor);

        interaction(map, 0, "PRESS", 50, 50, 1, 1, 1, "");
        assertTrue(editor.preview().isPresent());
        interaction(map, 1, "CANCEL", 50, 50, 0, 1, 0, "FOCUS_LOST");
        assertTrue(editor.preview().isEmpty());
        interaction(map, 2, "PRESS", 50, 50, 1, 1, 1, "");
        assertTrue(editor.preview().isPresent());
        map.setViewport(new MapViewport(100, 100, 1, 0, 1));
        interaction(map, 3, "RELEASE", 50, 50, 1, 0, 1, "");
        assertTrue(editor.preview().isEmpty());
        assertEquals(new Coordinate(0, 0), point(binding, "point"));

        map.clearActiveTool();
        assertTrue(editor.preview().isEmpty());
        map.setFeatureEditBindings(List.of());
        binding.close();
        assertTrue(binding.isClosed());
        assertThrows(IllegalStateException.class, editor::undo);
    }

    @Test
    void enforcesEditableBindingIdentityCrsAndTransactionalRedraw() {
        MundaneMap map = configuredMap();
        FeatureEditBinding binding = binding("editable", List.of());
        map.setFeatureEditBindings(List.of(binding));
        long generation = map.sceneGenerationForTest();
        binding.apply(
                new io.github.mundanej.map.api.FeatureEditTransaction(
                        0,
                        "Create",
                        List.of(
                                new io.github.mundanej.map.api.CreateFeature(
                                        new PointFeatureDraft("new", "New", Map.of())
                                                .at(new Coordinate(1, 2))))));
        assertTrue(map.sceneGenerationForTest() > generation);
        assertTrue(map.featureForEditing("editable", "new").isPresent());

        FeatureEditBinding duplicate = binding("editable", List.of());
        assertThrows(
                MundaneMapException.class,
                () -> map.setFeatureEditBindings(List.of(binding, duplicate)));

        MundaneMap geographic = configuredMap();
        FeatureEditBinding wrongCrs = binding("geographic", CrsDefinitions.EPSG_4326, List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> geographic.setFeatureEditBindings(List.of(wrongCrs)));
    }

    @Test
    void validatesBindingControllerAndBoundedSnapContracts() {
        MundaneMap map = configuredMap();
        map.setSnapshotLayers(List.of(referenceLayer()));
        assertThrows(IllegalArgumentException.class, () -> binding("x".repeat(257), List.of()));
        assertThrows(IllegalArgumentException.class, () -> binding("bad\nidentity", List.of()));
        FeatureEditBinding binding = binding("editable", List.of(record("point", 0, 0)));
        assertEquals("editable", binding.name());
        assertFalse(binding.isClosed());
        map.setFeatureEditBindings(List.of(binding));
        assertThrows(IllegalStateException.class, binding::close);
        assertThrows(
                IllegalStateException.class,
                () -> configuredMap().setFeatureEditBindings(List.of(binding)));
        assertFalse(binding.authorizes(null));
        assertTrue(binding.portrayal().reachableSymbols().size() == 1);

        BrowserPointEditController editor = new BrowserPointEditController(map, binding);
        assertEquals(BrowserPointEditController.Mode.NONE, editor.mode());
        assertTrue(editor.belongsTo(map));
        assertThrows(IllegalArgumentException.class, () -> configuredMap().setActiveTool(editor));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BrowserPointEditController(
                                map,
                                binding,
                                new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()),
                                new SnapLimits(257, 4096, 4096, 4096),
                                8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BrowserPointEditController(
                                map,
                                binding,
                                new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()),
                                new SnapLimits(256, 4097, 4096, 4096),
                                8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BrowserPointEditController(
                                map,
                                binding,
                                new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()),
                                new SnapLimits(256, 4096, 4097, 4096),
                                8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BrowserPointEditController(
                                map,
                                binding,
                                new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()),
                                new SnapLimits(256, 4096, 4096, 4097),
                                8));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BrowserPointEditController(
                                map,
                                binding,
                                new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()),
                                BrowserPointEditController.BROWSER_SNAP_LIMITS,
                                8,
                                Optional.empty(),
                                Set.of("missing")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new BrowserPointEditController(
                                map,
                                binding,
                                new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()),
                                BrowserPointEditController.BROWSER_SNAP_LIMITS,
                                8,
                                Optional.of(HorizontalWrap.webMercator()),
                                Set.of("missing")));
        map.setActiveTool(editor);
        assertEquals(FeatureEditStatus.REJECTED, editor.deleteSelected().status());
        assertEquals(
                "empty",
                editor.lastResult().orElseThrow().problem().orElseThrow().context().get("reason"));

        editor.create(new PointFeatureDraft("created", "Created", Map.of()));
        assertEquals(BrowserPointEditController.Mode.CREATE, editor.mode());
        editor.clearMode();
        assertEquals(BrowserPointEditController.Mode.NONE, editor.mode());
        editor.moveSelected();
        assertEquals(BrowserPointEditController.Mode.MOVE_SELECTED, editor.mode());

        SnapReferenceSet references =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "reference",
                                        List.of(
                                                new SnapFeature(
                                                        "snap",
                                                        new PointGeometry(
                                                                new Coordinate(20, 0)))))));
        map.clearActiveTool();
        BrowserPointEditController limited =
                new BrowserPointEditController(
                        map,
                        binding,
                        references,
                        BrowserPointEditController.BROWSER_SNAP_LIMITS.withMaximumFeatures(2),
                        8);
        limited.create(new PointFeatureDraft("limited", "Limited", Map.of()));
        map.setActiveTool(limited);
        interaction(map, 0, "CLICK", 70, 50, 1, 0, 1, "");
        assertEquals(
                "EDIT_SNAP_LIMIT_EXCEEDED",
                limited.lastResult().orElseThrow().problem().orElseThrow().code());
        assertFalse(has(binding, "limited"));
        assertTrue(limited.preview().isEmpty());
        map.setSnapshotLayers(List.of());
        interaction(map, 1, "MOVE", 70, 50, 0, 0, 0, "");
        var unavailable = limited.lastResult().orElseThrow().problem().orElseThrow();
        assertEquals("EDIT_SNAP_REFERENCE_UNAVAILABLE", unavailable.code());
        assertEquals(Map.of("layerIndex", "0"), unavailable.context());

        map.clearActiveTool();
        map.setFeatureEditBindings(List.of());
        binding.close();
        binding.close();
        assertTrue(binding.isClosed());
        assertThrows(
                IllegalStateException.class, () -> map.setFeatureEditBindings(List.of(binding)));
    }

    @Test
    void ownsEditLaneAuthorizesFirstRasterAndCleansItOnComponentClose() throws Exception {
        RasterIconSymbol icon =
                RasterIconSymbol.nativeScreenSize(
                        1, 1, new int[] {0x102030ff}, RasterInterpolation.NEAREST, 1);
        FeatureEditBinding binding =
                FeatureEditBinding.open(
                        "editable",
                        "Editable",
                        new FeatureEditSnapshot(
                                0, CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0))),
                        FeatureEditLimits.DEFAULT,
                        FeatureEditHistoryLimits.DEFAULT,
                        FeaturePortrayal.markers(new FixedSymbolSelector(icon)),
                        NamedSymbolCatalog.of(List.of(new NamedSymbol("icon", icon))));
        var requestThreads = Executors.newSingleThreadExecutor();
        AtomicInteger registrations = new AtomicInteger();
        try {
            assertEquals(0, requestThreads.submit(() -> binding.snapshot().revision()).get());
            MundaneMap map =
                    new MundaneMap(
                            System::nanoTime,
                            Runnable::run,
                            Runnable::run,
                            Runnable::run,
                            new MundaneMap.IconSessionAccess() {
                                @Override
                                public IconResourceBatch.Registrar resourceRegistrar(
                                        MundaneMap ignored) {
                                    return bytes -> {
                                        if (registrations.incrementAndGet() == 3) {
                                            throw new IllegalStateException(
                                                    "fixture registration failure");
                                        }
                                        return new IconResourceBatch.RegisteredResource(
                                                "./resource/edit-icon", () -> {});
                                    };
                                }

                                @Override
                                public com.vaadin.flow.shared.Registration addDestroyListener(
                                        MundaneMap ignored, Runnable listener) {
                                    return () -> {};
                                }
                            });
            map.setViewport(new MapViewport(100, 100, 0, 0, 1));
            requestThreads.submit(() -> map.setFeatureEditBindings(List.of(binding))).get();
            assertTrue(map.featureForEditing("editable", "point").isPresent());
            requestThreads.submit(() -> map.setFeatureSourceBindings(List.of())).get();
            assertTrue(map.featureForEditing("editable", "point").isPresent());
            MapViewport beforeFailure = map.viewport();
            long generationBeforeFailure = map.viewportGenerationForTest();
            assertThrows(
                    IllegalStateException.class,
                    () -> map.setViewport(new MapViewport(100, 100, 1, 0, 2)));
            assertEquals(beforeFailure, map.viewport());
            assertEquals(generationBeforeFailure, map.viewportGenerationForTest());
            requestThreads.submit(map::close).get();
            assertTrue(binding.isClosed());
        } finally {
            requestThreads.shutdownNow();
        }
    }

    @Test
    void canonicalizesWrappedCreateAndPlacesPreviewInThePointerCopy() {
        MundaneMap map = configuredMap();
        HorizontalWrap wrap = HorizontalWrap.webMercator();
        map.setViewport(new MapViewport(100, 100, wrap.period(), 0, 1));
        FeatureEditBinding binding = binding("editable", List.of());
        map.setFeatureEditBindings(List.of(binding));
        BrowserPointEditController editor =
                new BrowserPointEditController(
                        map,
                        binding,
                        new SnapReferenceSet(CrsDefinitions.EPSG_3857, List.of()),
                        BrowserPointEditController.BROWSER_SNAP_LIMITS,
                        8,
                        Optional.of(wrap),
                        Set.of());
        map.setActiveTool(editor);
        editor.create(new PointFeatureDraft("wrapped", "Wrapped", Map.of()));
        interaction(map, 0, "MOVE", 50, 50, 0, 0, 0, "");
        Feature previewFeature = editor.overlayLayers().getFirst().features().getFirst();
        assertEquals(
                wrap.period(),
                ((PointGeometry) previewFeature.geometry()).coordinate().x(),
                0.000001);
        interaction(map, 1, "CLICK", 50, 50, 1, 0, 1, "");
        assertEquals(0.0, point(binding, "wrapped").x(), 0.000001);

        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        editor.moveSelected();
        interaction(map, 2, "PRESS", 50, 50, 1, 1, 1, "");
        interaction(map, 3, "DRAG", 51, 50, 0, 1, 0, "");
        assertTrue(
                editor.overlayLayers().getFirst().features().stream()
                        .anyMatch(feature -> feature.geometry() instanceof LineStringGeometry));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        map.setCoordinateReferenceSystems(
                                CrsRegistry.level1(),
                                CrsDefinitions.EPSG_3857,
                                CrsDefinitions.EPSG_4326));
        assertEquals(CrsDefinitions.EPSG_3857, map.displayCrs());
        interaction(map, 4, "CANCEL", 51, 50, 0, 1, 0, "USER_CANCEL");
        editor.create(new PointFeatureDraft("too-far", "Too far", Map.of()));
        map.setViewport(
                new MapViewport(
                        100,
                        100,
                        wrap.period() * (HorizontalWrap.COPY_INDEX_HARD_MAXIMUM + 1L),
                        0,
                        1));
        interaction(map, 5, "MOVE", 50, 50, 0, 0, 0, "");
        assertEquals(
                "EDIT_WRAP_UNAVAILABLE",
                editor.lastResult().orElseThrow().problem().orElseThrow().code());
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        interaction(
                map,
                6,
                "MOVE",
                wrap.period() * (HorizontalWrap.COPY_INDEX_HARD_MAXIMUM + 1L),
                50,
                0,
                0,
                0,
                "");
        assertEquals(
                "EDIT_WRAP_UNAVAILABLE",
                editor.lastResult().orElseThrow().problem().orElseThrow().code());
    }

    @Test
    void restagesScaleDependentEditablePortrayalOnViewportChange() {
        var marker =
                BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(40, 80, 180), 12, 1);
        FeaturePortrayal portrayal =
                new RulePortrayalPlan(
                                List.of(
                                        new PortrayalRule(
                                                Optional.empty(),
                                                new ScaleInterval(
                                                        OptionalDouble.empty(),
                                                        OptionalDouble.of(10_000)),
                                                Optional.empty(),
                                                false,
                                                List.of(marker),
                                                List.of(),
                                                List.of())))
                        .portrayal();
        FeatureEditBinding binding =
                FeatureEditBinding.open(
                        "editable",
                        "Editable",
                        new FeatureEditSnapshot(
                                0, CrsDefinitions.EPSG_3857, List.of(record("point", 0, 0))),
                        portrayal);
        MundaneMap map = configuredMap();
        map.setFeatureEditBindings(List.of(binding));
        assertTrue(map.featureForEditing("editable", "point").isPresent());

        map.setViewport(new MapViewport(100, 100, 0, 0, 10));

        assertTrue(map.featureForEditing("editable", "point").isEmpty());
    }

    @Test
    void snapsDeterministicPointTiesAndSegmentsThroughBrowserCoordinates() {
        MundaneMap map = configuredMap();
        map.setSnapshotLayers(List.of(referenceLayer()));
        FeatureEditBinding binding = binding("editable", List.of());
        map.setFeatureEditBindings(List.of(binding));
        SnapReferenceSet ties =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "reference",
                                        List.of(
                                                new SnapFeature(
                                                        "west",
                                                        new PointGeometry(new Coordinate(-1, 0))),
                                                new SnapFeature(
                                                        "east",
                                                        new PointGeometry(
                                                                new Coordinate(1, 0)))))));
        BrowserPointEditController editor =
                new BrowserPointEditController(
                        map, binding, ties, BrowserPointEditController.BROWSER_SNAP_LIMITS, 8);
        map.setActiveTool(editor);
        editor.create(new PointFeatureDraft("tie", "Tie", Map.of()));
        interaction(map, 0, "CLICK", 50, 50, 1, 0, 1, "");
        assertEquals(new Coordinate(1, 0), point(binding, "tie"));

        map.clearActiveTool();
        SnapReferenceSet segments =
                new SnapReferenceSet(
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new SnapReferenceLayer(
                                        "reference",
                                        List.of(
                                                new SnapFeature(
                                                        "line",
                                                        new LineStringGeometry(
                                                                io.github.mundanej.map.api
                                                                        .CoordinateSequence.of(
                                                                        -10, 5, 10, 5)))))));
        BrowserPointEditController segmentEditor =
                new BrowserPointEditController(
                        map, binding, segments, BrowserPointEditController.BROWSER_SNAP_LIMITS, 8);
        map.setActiveTool(segmentEditor);
        segmentEditor.create(new PointFeatureDraft("segment", "Segment", Map.of()));
        interaction(map, 1, "CLICK", 50, 44, 1, 0, 1, "");
        assertEquals(new Coordinate(0, 5), point(binding, "segment"));
    }

    @Test
    void terminalSourceFailureCancelsMeasurementAndCreatePreview() {
        AtomicBoolean failing = new AtomicBoolean();
        InMemoryFeatureSource delegate =
                InMemoryFeatureSource.open(
                        new SourceIdentity("source", "Source"),
                        List.of(record("source-point", 0, 0)),
                        Optional.empty(),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty())),
                        FeatureSourceLimits.LEVEL_1);
        FeatureSource source =
                new FeatureSource() {
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
                    public FeatureCursor openCursor(
                            FeatureQuery query, CancellationToken cancellation) {
                        if (failing.get()) {
                            throw new IllegalStateException("fixture source failure");
                        }
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
                };
        MundaneMap map =
                new MundaneMap(System::nanoTime, Runnable::run, Runnable::run, Runnable::run);
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        FeatureSourceBinding sourceBinding =
                FeatureSourceBinding.borrowed(
                        "source",
                        "Source",
                        source,
                        FeaturePortrayal.markers(
                                new FixedSymbolSelector(
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.CIRCLE, Rgba.rgb(80, 80, 80), 8, 1))),
                        Optional.empty());
        map.setFeatureSourceBindings(List.of(sourceBinding));
        BrowserMeasurementTool measurement =
                new BrowserMeasurementTool(
                        map, DistanceStrategies.planarMetres(CrsDefinitions.EPSG_3857));
        map.setActiveTool(measurement);
        interaction(map, 0, "CLICK", 50, 50, 1, 0, 1, "");
        interaction(map, 1, "MOVE", 51, 50, 0, 0, 0, "");
        assertEquals(MeasurementPhase.MEASURING, measurement.state().phase());
        failing.set(true);
        map.setFeatureSourceVisible("source", true);
        assertEquals(MeasurementPhase.EMPTY, measurement.state().phase());

        map.clearActiveTool();
        failing.set(false);
        map.setFeatureSourceVisible("source", true);
        FeatureEditBinding editBinding = binding("editable", List.of());
        map.setFeatureEditBindings(List.of(editBinding));
        BrowserPointEditController editor = new BrowserPointEditController(map, editBinding);
        editor.create(new PointFeatureDraft("new", "New", Map.of()));
        map.setActiveTool(editor);
        interaction(map, 2, "MOVE", 52, 50, 0, 0, 0, "");
        assertTrue(editor.preview().isPresent());
        failing.set(true);
        map.setFeatureSourceVisible("source", true);
        assertTrue(editor.preview().isEmpty());
    }

    private static MundaneMap configuredMap() {
        MundaneMap map = new MundaneMap();
        map.setViewport(new MapViewport(100, 100, 0, 0, 1));
        return map;
    }

    private static FeatureEditBinding binding(String id, List<FeatureRecord> records) {
        return binding(id, CrsDefinitions.EPSG_3857, records);
    }

    private static FeatureEditBinding binding(
            String id, io.github.mundanej.map.api.CrsDefinition crs, List<FeatureRecord> records) {
        return FeatureEditBinding.open(
                id,
                id,
                new io.github.mundanej.map.api.FeatureEditSnapshot(0, crs, records),
                FeaturePortrayal.markers(
                        new FixedSymbolSelector(
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.CIRCLE, Rgba.rgb(40, 80, 180), 12, 1))));
    }

    private static FeatureRecord record(String id, double x, double y) {
        return new FeatureRecord(id, id, new PointGeometry(new Coordinate(x, y)), Map.of());
    }

    private static InMemoryLayer referenceLayer() {
        return new InMemoryLayer(
                "reference",
                "Reference",
                List.of(
                        new Feature(
                                "snap",
                                "Snap",
                                new PointGeometry(new Coordinate(20, 0)),
                                Map.of(),
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.CIRCLE, Rgba.rgb(100, 100, 100), 8, 1))));
    }

    private static Coordinate point(FeatureEditBinding binding, String id) {
        return ((PointGeometry)
                        binding.snapshot().records().stream()
                                .filter(record -> record.id().equals(id))
                                .findFirst()
                                .orElseThrow()
                                .geometry())
                .coordinate();
    }

    private static boolean has(FeatureEditBinding binding, String id) {
        return binding.snapshot().records().stream().anyMatch(record -> record.id().equals(id));
    }

    private static Map<String, Object> interaction(
            MundaneMap map,
            int sequence,
            String type,
            double x,
            double y,
            int button,
            int buttons,
            int clicks,
            String reason) {
        return map.acceptMapInteraction(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                sequence,
                type,
                x,
                y,
                button,
                buttons,
                0,
                clicks,
                0,
                false,
                reason);
    }

    private static void command(MundaneMap map, int sequence, String command) {
        map.acceptMapCommand(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                sequence,
                command);
    }

    private static MapToolEvent toolEvent(
            long sequence, MapToolEvent.Type type, Coordinate coordinate, int clicks) {
        return new MapToolEvent(
                sequence,
                type,
                0,
                0,
                Optional.of(coordinate),
                io.github.mundanej.map.api.MapPointerButton.PRIMARY,
                Set.of(),
                Set.of(),
                clicks,
                0,
                false,
                Optional.empty());
    }

    private static final class TestToolContext
            implements io.github.mundanej.map.api.MapToolContext {
        private final io.github.mundanej.map.api.CrsDefinition crs;

        private TestToolContext(io.github.mundanej.map.api.CrsDefinition crs) {
            this.crs = crs;
        }

        @Override
        public io.github.mundanej.map.api.CrsDefinition mapCrs() {
            return crs;
        }

        @Override
        public io.github.mundanej.map.api.CrsDefinition displayCrs() {
            return crs;
        }

        @Override
        public Optional<Coordinate> mapToScreen(Coordinate coordinate) {
            return Optional.of(coordinate);
        }

        @Override
        public Optional<Coordinate> screenToMap(double screenX, double screenY) {
            return Optional.of(new Coordinate(screenX, screenY));
        }

        @Override
        public void requestRepaint() {}
    }
}
