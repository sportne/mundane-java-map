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
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

final class MundaneMapFeatureSourceTest {
    @Test
    void publishesSerializedQueriesVisibilityAndStableSourceOrder() {
        ArrayDeque<Runnable> queries = new ArrayDeque<>();
        ArrayDeque<Runnable> completions = new ArrayDeque<>();
        MundaneMap map = map(queries, completions);
        CountingSource first = source("first", true);
        CountingSource second = source("second", true);
        FeatureSourceBinding firstBinding = binding("one", first, false);
        FeatureSourceBinding secondBinding = binding("two", second, false);
        List<io.github.mundanej.map.api.MapSourceReportEvent> reportEvents = new ArrayList<>();
        map.addSourceReportListener(reportEvents::add);

        map.setFeatureSourceBindings(List.of(firstBinding, secondBinding));
        assertEquals(
                List.of("one", "two"),
                map.featureSourceBindings().stream().map(FeatureSourceBinding::id).toList());
        runNext(queries);
        runNext(completions);

        List<?> layers = (List<?>) map.encodedSceneForTest().get("layers");
        assertEquals(
                List.of("one", "two"),
                layers.stream().map(value -> ((Map<?, ?>) value).get("id")).toList());
        assertEquals(1, first.openedCursors);
        assertEquals(1, second.openedCursors);
        assertEquals(1, first.maximumLiveCursors);

        map.setFeatureSourceVisible("one", false);
        assertFalse(map.isFeatureSourceVisible("one"));
        runNext(queries);
        runNext(completions);
        Map<?, ?> hidden =
                (Map<?, ?>) ((List<?>) map.encodedSceneForTest().get("layers")).getFirst();
        assertTrue(((List<?>) hidden.get("features")).isEmpty());
        assertEquals(1, first.openedCursors);
        assertEquals(List.of(), reportEvents);

        map.setFeatureSourceBindings(List.of(secondBinding));
        runNext(queries); // borrowed binding release
        runNext(queries); // replacement query
        runNext(completions);
        assertFalse(first.isClosed());
        map.close();
        runNext(queries);
        assertFalse(second.isClosed());
    }

    @Test
    void supersedesQueuedGenerationsAndQueriesSettledViewportBounds() {
        ArrayDeque<Runnable> queries = new ArrayDeque<>();
        ArrayDeque<Runnable> completions = new ArrayDeque<>();
        MundaneMap map = map(queries, completions);
        CountingSource source = source("source", true);
        map.setFeatureSourceBindings(List.of(binding("source-layer", source, false)));
        map.setViewport(new MapViewport(100, 80, 20, 30, 2));
        assertEquals(2, queries.size());

        runNext(queries);
        runNext(completions);
        assertEquals(0, source.openedCursors);
        runNext(queries);
        assertEquals(
                Optional.of(new Envelope(-80, -50, 120, 110)), source.lastQuery.sourceBounds());
        long sceneBeforeStaleCompletion = map.sceneGenerationForTest();
        map.setViewport(new MapViewport(100, 80, 40, 50, 1));
        runNext(completions);
        assertEquals(sceneBeforeStaleCompletion, map.sceneGenerationForTest());
        runNext(queries);
        runNext(completions);
        assertEquals(Optional.of(new Envelope(-10, 10, 90, 90)), source.lastQuery.sourceBounds());

        map.acceptSettledViewport(
                1,
                (double) map.componentGenerationForTest(),
                (double) map.sceneGenerationForTest(),
                (double) map.viewportGenerationForTest(),
                0,
                100,
                80,
                60,
                70,
                1);
        assertEquals(1, queries.size());
        runNext(queries);
        runNext(completions);
        assertEquals(Optional.of(new Envelope(10, 30, 110, 110)), source.lastQuery.sourceBounds());
        map.close();
    }

    @Test
    void reportsCrsFailureAndClosesOnlyRemovedOwnedSourcesExactlyOnce() {
        ArrayDeque<Runnable> queries = new ArrayDeque<>();
        ArrayDeque<Runnable> completions = new ArrayDeque<>();
        MundaneMap map = map(queries, completions);
        CountingSource missingCrs = source("missing", false);
        CountingSource owned = source("owned", true);
        CountingSource borrowed = source("borrowed", true);
        FeatureSourceBinding missing = binding("missing-layer", missingCrs, false);
        FeatureSourceBinding ownedBinding = binding("owned-layer", owned, true);
        FeatureSourceBinding borrowedBinding = binding("borrowed-layer", borrowed, false);

        map.setFeatureSourceBindings(List.of(missing, ownedBinding, borrowedBinding));
        runNext(queries);
        runNext(completions);
        assertEquals(
                "CRS_METADATA_MISSING",
                map.sourceReports().get("missing-layer").entries().getLast().code());

        map.setFeatureSourceBindings(List.of(borrowedBinding));
        runNext(queries); // releases removed bindings after prior cursor completion
        runNext(queries); // current query
        runNext(completions);
        assertEquals(1, owned.closeCount);
        assertEquals(0, borrowed.closeCount);
        assertEquals(Map.of(), map.sourceReports());

        map.close();
        runNext(queries);
        assertEquals(1, owned.closeCount);
        assertEquals(0, borrowed.closeCount);
        assertTrue(ownedBinding.isClosed());
        ownedBinding.close();
        assertEquals(1, owned.closeCount);
    }

    @Test
    void rejectsDuplicateSourcesAndUnknownVisibilityAtomically() {
        ArrayDeque<Runnable> queries = new ArrayDeque<>();
        MundaneMap map = map(queries, new ArrayDeque<>());
        CountingSource source = source("shared", true);
        FeatureSourceBinding first = binding("first", source, false);
        FeatureSourceBinding second = binding("second", source, false);
        assertThrows(
                IllegalArgumentException.class,
                () -> map.setFeatureSourceBindings(List.of(first, second)));
        assertTrue(map.featureSourceBindings().isEmpty());
        map.setFeatureSourceBindings(List.of(first));
        assertThrows(
                MundaneMapException.class,
                () ->
                        map.setSnapshotLayers(
                                List.of(new InMemoryLayer("first", "Duplicate", List.of()))));
        assertTrue(map.snapshotLayers().isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> map.setFeatureSourceBindings(List.of(binding("replacement", source, false))));
        assertEquals(List.of(first), map.featureSourceBindings());
        assertThrows(
                IllegalArgumentException.class, () -> map.setFeatureSourceVisible("missing", true));
        map.close();
    }

    @Test
    void exposesExplicitCrsConfigurationAtomically() {
        MundaneMap map = map(new ArrayDeque<>(), new ArrayDeque<>());
        map.setCoordinateReferenceSystems(
                CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_3857);
        assertEquals(CrsDefinitions.EPSG_4326, map.mapCrs());
        assertEquals(CrsDefinitions.EPSG_3857, map.displayCrs());

        CrsRegistry definitionsOnly =
                CrsRegistry.builder()
                        .registerDefinition(CrsDefinitions.EPSG_4326, List.of())
                        .registerDefinition(CrsDefinitions.EPSG_3857, List.of())
                        .build();
        assertThrows(
                io.github.mundanej.map.api.CrsException.class,
                () ->
                        map.setCoordinateReferenceSystems(
                                definitionsOnly,
                                CrsDefinitions.EPSG_4326,
                                CrsDefinitions.EPSG_3857));
        assertEquals(CrsDefinitions.EPSG_4326, map.mapCrs());
        assertEquals(CrsDefinitions.EPSG_3857, map.displayCrs());
        map.close();
    }

    @Test
    void enforcesAggregateConfiguredLayerLimitBeforeQuerying() {
        ArrayDeque<Runnable> queries = new ArrayDeque<>();
        MundaneMap map = map(queries, new ArrayDeque<>());
        List<io.github.mundanej.map.api.Layer> snapshots = new ArrayList<>();
        for (int index = 0; index < SceneProtocol.DEFAULT_LIMITS.layers(); index++) {
            snapshots.add(new InMemoryLayer("snapshot-" + index, "Snapshot", List.of()));
        }
        map.setSnapshotLayers(snapshots);
        FeatureSourceBinding excess = binding("source", source("source", true), false);
        MundaneMapException failure =
                assertThrows(
                        MundaneMapException.class,
                        () -> map.setFeatureSourceBindings(List.of(excess)));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, failure.code());
        assertTrue(map.featureSourceBindings().isEmpty());
        assertTrue(queries.isEmpty());
        map.close();
    }

    @Test
    void claimsSharedSourceIdentityAcrossComponentsUntilSerializedRelease() {
        ArrayDeque<Runnable> firstQueries = new ArrayDeque<>();
        ArrayDeque<Runnable> firstCompletions = new ArrayDeque<>();
        ArrayDeque<Runnable> secondQueries = new ArrayDeque<>();
        CountingSource shared = source("shared-components", true);
        MundaneMap firstMap = map(firstQueries, firstCompletions);
        MundaneMap secondMap = map(secondQueries, new ArrayDeque<>());
        FeatureSourceBinding firstBinding = binding("first", shared, false);
        FeatureSourceBinding secondBinding = binding("second", shared, false);
        firstMap.setFeatureSourceBindings(List.of(firstBinding));

        assertThrows(
                IllegalStateException.class,
                () -> secondMap.setFeatureSourceBindings(List.of(secondBinding)));
        assertTrue(secondMap.featureSourceBindings().isEmpty());
        firstMap.close();
        while (!firstQueries.isEmpty()) {
            runNext(firstQueries);
        }

        secondMap.setFeatureSourceBindings(List.of(secondBinding));
        assertEquals(List.of(secondBinding), secondMap.featureSourceBindings());
        secondMap.close();
        while (!secondQueries.isEmpty()) {
            runNext(secondQueries);
        }
        assertFalse(shared.isClosed());
    }

    @Test
    void rejectsClaimThatRacesWithOwnedSourceClose() throws Exception {
        ArrayDeque<Runnable> firstQueries = new ArrayDeque<>();
        ArrayDeque<Runnable> firstCompletions = new ArrayDeque<>();
        MundaneMap firstMap = map(firstQueries, firstCompletions);
        MundaneMap secondMap = map(new ArrayDeque<>(), new ArrayDeque<>());
        BlockingClosedCheckSource source =
                new BlockingClosedCheckSource(source("claim-close-race", true));
        FeatureSourceBinding firstBinding = binding("first", source, true);
        FeatureSourceBinding secondBinding = binding("second", source, false);
        firstMap.setFeatureSourceBindings(List.of(firstBinding));
        runNext(firstQueries);
        runNext(firstCompletions);
        firstMap.setFeatureSourceBindings(List.of());
        source.armClosedCheck();

        CompletableFuture<Throwable> attach =
                CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                secondMap.setFeatureSourceBindings(List.of(secondBinding));
                                return null;
                            } catch (RuntimeException | Error failure) {
                                return failure;
                            }
                        });
        assertTrue(source.closedCheckEntered.await(5, TimeUnit.SECONDS));
        CompletableFuture<Void> release = CompletableFuture.runAsync(() -> runNext(firstQueries));
        source.continueClosedCheck.countDown();

        assertInstanceOf(IllegalStateException.class, attach.get(5, TimeUnit.SECONDS));
        release.get(5, TimeUnit.SECONDS);
        assertTrue(secondMap.featureSourceBindings().isEmpty());
        assertTrue(source.isClosed());
        firstMap.close();
        secondMap.close();
    }

    @Test
    void completesPublicationAndOwnedCleanupBeforePropagatingListenerFailure() {
        ArrayDeque<Runnable> queries = new ArrayDeque<>();
        ArrayDeque<Runnable> completions = new ArrayDeque<>();
        MundaneMap map = map(queries, completions);
        CountingSource owned = source("listener-owned", false);
        map.setFeatureSourceBindings(List.of(binding("owned", owned, true)));
        runNext(queries);
        runNext(completions);
        assertFalse(map.sourceReports().isEmpty());
        map.addSourceReportListener(
                event -> {
                    throw new IllegalStateException("deliberate listener failure");
                });

        assertThrows(IllegalStateException.class, () -> map.setFeatureSourceBindings(List.of()));
        assertTrue(map.featureSourceBindings().isEmpty());
        assertTrue(((List<?>) map.encodedSceneForTest().get("layers")).isEmpty());
        runNext(queries);
        assertEquals(1, owned.closeCount);
        map.close();
    }

    @Test
    void releasesEveryRemovedBindingBeforeRethrowingFirstCleanupFailure() {
        ArrayDeque<Runnable> queries = new ArrayDeque<>();
        ArrayDeque<Runnable> completions = new ArrayDeque<>();
        MundaneMap map = map(queries, completions);
        CountingSource failingDelegate = source("failing-close", true);
        FailingCloseSource failing = new FailingCloseSource(failingDelegate);
        CountingSource succeeding = source("succeeding-close", true);
        map.setFeatureSourceBindings(
                List.of(
                        binding("failing", failing, true),
                        binding("succeeding", succeeding, true)));
        runNext(queries);
        runNext(completions);
        map.setFeatureSourceBindings(List.of());

        IllegalStateException failure =
                assertThrows(IllegalStateException.class, () -> runNext(queries));
        assertEquals("deliberate close failure", failure.getMessage());
        assertTrue(failing.isClosed());
        assertEquals(1, succeeding.closeCount);
        map.close();
    }

    @Test
    void enforcesBindingValidationOwnershipAndLifecycle() {
        CountingSource borrowedSource = source("binding-borrowed", true);
        FeatureSourceBinding borrowed = binding("binding", borrowedSource, false);
        assertEquals("binding", borrowed.id());
        assertEquals("binding", borrowed.name());
        assertEquals(borrowedSource, borrowed.source());
        assertEquals(AttributeSelection.NONE, borrowed.attributes());
        assertEquals(Optional.empty(), borrowed.tighterLimits());
        assertFalse(borrowed.owned());
        assertFalse(borrowed.isClosed());
        borrowed.close();
        borrowed.close();
        assertTrue(borrowed.isClosed());
        assertFalse(borrowedSource.isClosed());

        assertThrows(IllegalArgumentException.class, () -> binding(" ", borrowedSource, false));
        FeatureQueryLimits tooLoose =
                new FeatureQueryLimits(
                        FeatureQueryLimits.LEVEL_1.recordsExamined() + 1,
                        FeatureQueryLimits.LEVEL_1.recordsReturned(),
                        FeatureQueryLimits.LEVEL_1.coordinatesReturned(),
                        FeatureQueryLimits.LEVEL_1.attributeValuesReturned(),
                        FeatureQueryLimits.LEVEL_1.decodedTextCharactersReturned(),
                        FeatureQueryLimits.LEVEL_1.ownedPayloadBytes(),
                        FeatureQueryLimits.LEVEL_1.retainedWarnings());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        FeatureSourceBinding.borrowed(
                                "limits",
                                "limits",
                                borrowedSource,
                                marker(),
                                line(),
                                SolidFillSymbol.of(Rgba.rgb(70, 80, 90), 1),
                                AttributeSelection.NONE,
                                Optional.of(tooLoose)));

        CountingSource ownedSource = source("binding-owned", true);
        FeatureSourceBinding owned = binding("owned", ownedSource, true);
        MundaneMap owner = map(new ArrayDeque<>(), new ArrayDeque<>());
        MundaneMap other = map(new ArrayDeque<>(), new ArrayDeque<>());
        assertTrue(owned.owned());
        owned.attach(owner);
        assertThrows(IllegalStateException.class, () -> owned.attach(owner));
        assertThrows(IllegalStateException.class, owned::close);
        assertThrows(IllegalStateException.class, () -> owned.attach(other));
        owned.release(other);
        owned.detach(other);
        owned.detach(owner);
        owned.close();
        owned.close();
        assertTrue(owned.isClosed());
        assertEquals(1, ownedSource.closeCount);
        assertThrows(IllegalStateException.class, () -> binding("closed", ownedSource, false));
        owner.close();
        other.close();
    }

    @Test
    void rejectsCustomNestedAndOverDepthBindingSymbolsBeforeSourceIo() {
        CountingSource source = source("closed-profile", true);
        MarkerSymbol customMarker =
                new MarkerSymbol() {
                    @Override
                    public SymbolRendererKey rendererKey() {
                        return new SymbolRendererKey("example.custom-marker");
                    }

                    @Override
                    public double opacity() {
                        return 1;
                    }
                };
        LineSymbol customLine =
                new LineSymbol() {
                    @Override
                    public SymbolRendererKey rendererKey() {
                        return new SymbolRendererKey("example.custom-line");
                    }

                    @Override
                    public double opacity() {
                        return 1;
                    }
                };

        MundaneMapException wrongRole =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                FeatureSourceBinding.borrowed(
                                        "wrong-role",
                                        "Wrong role",
                                        source,
                                        line(),
                                        line(),
                                        SolidFillSymbol.of(Rgba.rgb(70, 80, 90), 1),
                                        AttributeSelection.NONE,
                                        Optional.empty()));
        assertEquals("binding", wrongRole.context().get("scope"));

        MundaneMapException direct =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                FeatureSourceBinding.borrowed(
                                        "custom",
                                        "Custom",
                                        source,
                                        customMarker,
                                        line(),
                                        SolidFillSymbol.of(Rgba.rgb(70, 80, 90), 1),
                                        AttributeSelection.NONE,
                                        Optional.empty()));
        assertEquals(MundaneMapException.UNSUPPORTED_VALUE, direct.code());
        assertEquals("binding", direct.context().get("scope"));

        SolidLineSymbol nestedEndpoint =
                SolidLineSymbol.of(
                        line().stroke(),
                        Optional.of(CompositeSymbol.of(List.of(customMarker), 1)),
                        Optional.empty(),
                        1);
        MundaneMapException endpoint =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                FeatureSourceBinding.borrowed(
                                        "endpoint",
                                        "Endpoint",
                                        source,
                                        marker(),
                                        nestedEndpoint,
                                        SolidFillSymbol.of(Rgba.rgb(70, 80, 90), 1),
                                        AttributeSelection.NONE,
                                        Optional.empty()));
        assertEquals("marker symbol", endpoint.context().get("valueKind"));

        MundaneMapException outline =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                FeatureSourceBinding.borrowed(
                                        "outline",
                                        "Outline",
                                        source,
                                        marker(),
                                        line(),
                                        SolidFillSymbol.of(
                                                Rgba.rgb(70, 80, 90), Optional.of(customLine), 1),
                                        AttributeSelection.NONE,
                                        Optional.empty()));
        assertEquals("line symbol", outline.context().get("valueKind"));

        Symbol nested = marker();
        for (int depth = 0; depth <= 64; depth++) {
            nested = CompositeSymbol.of(List.of(nested), 1);
        }
        Symbol overDepth = nested;
        MundaneMapException depth =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                FeatureSourceBinding.borrowed(
                                        "deep",
                                        "Deep",
                                        source,
                                        overDepth,
                                        line(),
                                        SolidFillSymbol.of(Rgba.rgb(70, 80, 90), 1),
                                        AttributeSelection.NONE,
                                        Optional.empty()));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, depth.code());
        assertEquals("symbolDepth", depth.context().get("limit"));
        assertEquals(0, source.openedCursors);
    }

    private static MundaneMap map(ArrayDeque<Runnable> queries, ArrayDeque<Runnable> completions) {
        MundaneMap map =
                new MundaneMap(System::nanoTime, Runnable::run, queries::add, completions::add);
        map.setViewport(new MapViewport(800, 600, 0, 0, 1));
        return map;
    }

    private static void runNext(ArrayDeque<Runnable> tasks) {
        tasks.removeFirst().run();
    }

    private static FeatureSourceBinding binding(String id, FeatureSource source, boolean owned) {
        if (owned) {
            return FeatureSourceBinding.owned(
                    id,
                    id,
                    source,
                    marker(),
                    line(),
                    SolidFillSymbol.of(Rgba.rgb(70, 80, 90), 1),
                    AttributeSelection.NONE,
                    Optional.empty());
        }
        return FeatureSourceBinding.borrowed(
                id,
                id,
                source,
                marker(),
                line(),
                SolidFillSymbol.of(Rgba.rgb(70, 80, 90), 1),
                AttributeSelection.NONE,
                Optional.empty());
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

    private static CountingSource source(String id, boolean recognizedCrs) {
        Optional<CrsMetadata> crs =
                recognizedCrs
                        ? Optional.of(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_3857,
                                        Optional.of("EPSG:3857"),
                                        Optional.empty()))
                        : Optional.empty();
        InMemoryFeatureSource delegate =
                InMemoryFeatureSource.open(
                        new SourceIdentity(id, id),
                        List.of(
                                new FeatureRecord(
                                        "feature-" + id,
                                        id,
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of())),
                        Optional.empty(),
                        crs,
                        FeatureSourceLimits.LEVEL_1);
        return new CountingSource(delegate);
    }

    private static final class CountingSource implements FeatureSource {
        private final InMemoryFeatureSource delegate;
        private int openedCursors;
        private int liveCursors;
        private int maximumLiveCursors;
        private int closeCount;
        private FeatureQuery lastQuery;

        private CountingSource(InMemoryFeatureSource delegate) {
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
            FeatureCursor delegateCursor = delegate.openCursor(query, cancellation);
            return new FeatureCursor() {
                private boolean closed;

                @Override
                public boolean advance() {
                    return delegateCursor.advance();
                }

                @Override
                public FeatureRecord current() {
                    return delegateCursor.current();
                }

                @Override
                public DiagnosticReport diagnostics() {
                    return delegateCursor.diagnostics();
                }

                @Override
                public boolean isClosed() {
                    return closed;
                }

                @Override
                public void close() {
                    if (!closed) {
                        closed = true;
                        delegateCursor.close();
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
            if (!delegate.isClosed()) {
                closeCount++;
            }
            delegate.close();
        }
    }

    private static final class BlockingClosedCheckSource implements FeatureSource {
        private final CountingSource delegate;
        private final CountDownLatch closedCheckEntered = new CountDownLatch(1);
        private final CountDownLatch continueClosedCheck = new CountDownLatch(1);
        private volatile boolean blockClosedCheck;

        private BlockingClosedCheckSource(CountingSource delegate) {
            this.delegate = delegate;
        }

        private void armClosedCheck() {
            blockClosedCheck = true;
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
            return delegate.openCursor(query, cancellation);
        }

        @Override
        public boolean isClosed() {
            boolean closed = delegate.isClosed();
            if (blockClosedCheck) {
                closedCheckEntered.countDown();
                try {
                    assertTrue(continueClosedCheck.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("closed check interrupted", exception);
                }
            }
            return closed;
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class FailingCloseSource implements FeatureSource {
        private final CountingSource delegate;

        private FailingCloseSource(CountingSource delegate) {
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
            return delegate.openCursor(query, cancellation);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
            throw new IllegalStateException("deliberate close failure");
        }
    }
}
