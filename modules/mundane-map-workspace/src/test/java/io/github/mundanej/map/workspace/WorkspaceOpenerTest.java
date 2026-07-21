package io.github.mundanej.map.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceOpenerTest {
    private static final SourceIdentity FEATURE_ID = new SourceIdentity("feature", "Feature");
    private static final SourceIdentity RASTER_ID = new SourceIdentity("raster", "Raster");
    private static final WorkspaceLocalPathProfile FEATURE_PROFILE =
            new WorkspaceLocalPathProfile(
                    List.of(new WorkspaceLocalPathBranch(".vec", List.of(".idx"))));
    private static final WorkspaceLocalPathProfile RASTER_PROFILE =
            new WorkspaceLocalPathProfile(List.of(new WorkspaceLocalPathBranch(".img", List.of())));

    @TempDir Path temporaryDirectory;

    @Test
    void validatesFinitePathProfilesAndSingleUseExactRegistries() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkspaceLocalPathBranch("vec", List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new WorkspaceLocalPathBranch(".vec", List.of(".idx", ".idx")));
        assertThrows(
                IllegalArgumentException.class, () -> new WorkspaceLocalPathProfile(List.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new WorkspaceLocalPathProfile(
                                List.of(
                                        new WorkspaceLocalPathBranch(".vec", List.of()),
                                        new WorkspaceLocalPathBranch(".VEC", List.of()))));

        WorkspaceSourceRegistry.Builder sources = WorkspaceSourceRegistry.builder();
        sources.registerFeature("application.feature.v1", FEATURE_PROFILE, this::unexpectedFeature);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        sources.registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                this::unexpectedFeature));
        sources.build();
        assertThrows(IllegalStateException.class, sources::build);

        WorkspaceSymbolCatalogRegistry.Builder catalogs =
                WorkspaceSymbolCatalogRegistry.builder().register("application.symbols", catalog());
        assertThrows(
                IllegalArgumentException.class,
                () -> catalogs.register("application.symbols", catalog()));
        catalogs.build();
        assertThrows(IllegalStateException.class, catalogs::build);

        WorkspaceLocalPathProfile ascii =
                new WorkspaceLocalPathProfile(
                        List.of(new WorkspaceLocalPathBranch(".shp", List.of())));
        assertEquals(".shp", ascii.requireBranch("map.SHP", 0).primarySuffix());
        WorkspaceException longS =
                assertThrows(WorkspaceException.class, () -> ascii.requireBranch("map.ſhp", 0));
        assertProblem(longS, "WORKSPACE_PATH_INVALID", "reason", "suffix", "layerIndex", "0");
        WorkspaceLocalPathProfile kelvin =
                new WorkspaceLocalPathProfile(
                        List.of(new WorkspaceLocalPathBranch(".kml", List.of())));
        assertThrows(WorkspaceException.class, () -> kelvin.requireBranch("map.Kml", 0));
    }

    @Test
    void opensAllLayersAndSessionClosesExactlyOnceInReverseOrder() throws IOException {
        create("data/feature.vec");
        create("data/feature.idx");
        create("data/raster.img");
        Path expectedFeature = temporaryDirectory.resolve("data/feature.vec").toRealPath();
        List<String> events = new ArrayList<>();
        TestFeatureSource feature = new TestFeatureSource(FEATURE_ID, "feature", events, null);
        TestRasterSource raster = new TestRasterSource(RASTER_ID, "raster", events, null);
        WorkspaceOpenContext context =
                context(
                        WorkspaceSourceRegistry.builder()
                                .registerFeature(
                                        "application.feature.v1",
                                        FEATURE_PROFILE,
                                        (identity, path, cancellation) -> {
                                            assertEquals(expectedFeature, path);
                                            events.add("open-feature");
                                            return feature;
                                        })
                                .registerRaster(
                                        "application.raster.v1",
                                        RASTER_PROFILE,
                                        (identity, path, cancellation) -> {
                                            events.add("open-raster");
                                            return raster;
                                        })
                                .build(),
                        catalog());

        WorkspaceSession session =
                WorkspaceOpener.open(
                        file(List.of(featureLayer(), rasterLayer())),
                        context,
                        CancellationToken.none());

        assertEquals(List.of("open-feature", "open-raster"), events);
        assertSame(feature, ((OpenedWorkspaceFeatureLayer) session.layers().getFirst()).source());
        assertSame(raster, ((OpenedWorkspaceRasterLayer) session.layers().getLast()).source());
        assertEquals(CrsDefinitions.EPSG_4326, session.mapCrs());
        assertEquals(CrsDefinitions.EPSG_3857, session.displayCrs());
        assertThrows(UnsupportedOperationException.class, () -> session.layers().clear());
        session.close();
        session.close();
        assertEquals(
                List.of("open-feature", "open-raster", "close-raster", "close-feature"), events);
        assertTrue(session.isClosed());
    }

    @Test
    void completesReferencePreflightBeforeAnySourceIo() throws IOException {
        create("data/feature.vec");
        create("data/raster.img");
        AtomicInteger opens = new AtomicInteger();
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, cancellation) -> {
                                    opens.incrementAndGet();
                                    return new TestFeatureSource(
                                            identity, "feature", new ArrayList<>(), null);
                                })
                        .build();

        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer(), rasterLayer())),
                                        context(sources, catalog()),
                                        CancellationToken.none()));

        assertProblem(
                failure,
                "WORKSPACE_SOURCE_OPENER_UNREGISTERED",
                "kind",
                "RASTER",
                "layerIndex",
                "1");
        assertEquals(0, opens.get());
    }

    @Test
    void rejectsKindCatalogSymbolAndCrsMismatchesBeforeSourceIo() throws IOException {
        create("data/feature.vec");
        AtomicInteger opens = new AtomicInteger();
        WorkspaceSourceRegistry wrongKind =
                WorkspaceSourceRegistry.builder()
                        .registerRaster(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, cancellation) -> {
                                    opens.incrementAndGet();
                                    return new TestRasterSource(
                                            identity, "raster", new ArrayList<>(), null);
                                })
                        .build();
        WorkspaceException kind =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(wrongKind, catalog()),
                                        CancellationToken.none()));
        assertEquals("WORKSPACE_SOURCE_KIND_MISMATCH", kind.problem().code());

        WorkspaceSourceRegistry sources = featureSources(opens, null);
        WorkspaceOpenContext unregisteredCatalog =
                new WorkspaceOpenContext(
                        CrsRegistry.level1(),
                        sources,
                        WorkspaceSymbolCatalogRegistry.builder().build());
        WorkspaceException catalogUnregistered =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        unregisteredCatalog,
                                        CancellationToken.none()));
        assertProblem(
                catalogUnregistered, "WORKSPACE_SYMBOL_CATALOG_UNREGISTERED", "layerIndex", "0");

        WorkspaceException catalogMissing =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(sources, NamedSymbolCatalog.of(List.of())),
                                        CancellationToken.none()));
        assertEquals("WORKSPACE_SYMBOL_NOT_FOUND", catalogMissing.problem().code());

        NamedSymbolCatalog wrongRoles =
                NamedSymbolCatalog.of(
                        List.of(
                                new NamedSymbol("marker", line()),
                                new NamedSymbol("line", line()),
                                new NamedSymbol("fill", fill())));
        WorkspaceException role =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(sources, wrongRoles),
                                        CancellationToken.none()));
        assertProblem(role, "WORKSPACE_SYMBOL_ROLE_MISMATCH", "role", "marker", "layerIndex", "0");

        WorkspaceOpenContext noCrs =
                new WorkspaceOpenContext(
                        CrsRegistry.builder().build(), sources, registeredCatalog(catalog()));
        WorkspaceException crs =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        noCrs,
                                        CancellationToken.none()));
        assertProblem(crs, "WORKSPACE_CRS_UNREGISTERED", "field", "mapCrs");

        CrsDefinition canonical = CrsDefinitions.EPSG_4326;
        CrsDefinition fabricated =
                new CrsDefinition(
                        canonical.canonicalIdentifier(),
                        canonical.kind(),
                        canonical.xAxis(),
                        canonical.yAxis(),
                        new Envelope(-179, -90, 180, 90));
        CrsRegistry fabricatedRegistry =
                CrsRegistry.builder()
                        .registerDefinition(fabricated, List.of())
                        .registerDefinition(CrsDefinitions.EPSG_3857, List.of())
                        .build();
        WorkspaceOpenContext fabricatedContext =
                new WorkspaceOpenContext(fabricatedRegistry, sources, registeredCatalog(catalog()));
        WorkspaceException mismatch =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        fabricatedContext,
                                        CancellationToken.none()));
        assertProblem(
                mismatch,
                "WORKSPACE_VALUE_INVALID",
                "field",
                "mapCrs",
                "reason",
                "definitionMismatch");
        assertEquals(0, opens.get());
    }

    @Test
    void preflightsEveryPathAndAllowsMissingSidecars() throws IOException {
        create("data/feature.vec");
        AtomicInteger opens = new AtomicInteger();
        WorkspaceSourceRegistry sources = featureSources(opens, null);

        try (WorkspaceSession session =
                WorkspaceOpener.open(
                        file(List.of(featureLayer())),
                        context(sources, catalog()),
                        CancellationToken.none())) {
            assertEquals(1, opens.get());
            assertFalse(session.isClosed());
        }

        WorkspaceFeatureLayer wrongSuffix =
                featureLayer(new WorkspaceRelativePath("data/feature.bad"));
        WorkspaceException suffix =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(wrongSuffix)),
                                        context(sources, catalog()),
                                        CancellationToken.none()));
        assertProblem(suffix, "WORKSPACE_PATH_INVALID", "reason", "suffix", "layerIndex", "0");

        WorkspaceFeatureLayer missing = featureLayer(new WorkspaceRelativePath("data/missing.vec"));
        WorkspaceException absent =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(missing)),
                                        context(sources, catalog()),
                                        CancellationToken.none()));
        assertProblem(absent, "WORKSPACE_RESOURCE_MISSING", "kind", "primary", "layerIndex", "0");
    }

    @Test
    void rejectsSymlinksDirectoriesAndIdentityRacesBeforeOpening() throws IOException {
        create("data/real.vec");
        Path link = temporaryDirectory.resolve("data/link.vec");
        Files.createSymbolicLink(link, Path.of("real.vec"));
        AtomicInteger opens = new AtomicInteger();
        WorkspaceSourceRegistry sources = featureSources(opens, null);

        WorkspaceException symlink =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(
                                                List.of(
                                                        featureLayer(
                                                                new WorkspaceRelativePath(
                                                                        "data/link.vec")))),
                                        context(sources, catalog()),
                                        CancellationToken.none()));
        assertProblem(symlink, "WORKSPACE_PATH_INVALID", "reason", "symlink", "layerIndex", "0");

        Files.createDirectories(temporaryDirectory.resolve("data/directory.vec"));
        WorkspaceException directory =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(
                                                List.of(
                                                        featureLayer(
                                                                new WorkspaceRelativePath(
                                                                        "data/directory.vec")))),
                                        context(sources, catalog()),
                                        CancellationToken.none()));
        assertProblem(
                directory, "WORKSPACE_PATH_INVALID", "reason", "wrongKind", "layerIndex", "0");

        WorkspacePathAccess changing = new ChangingIdentityPathAccess();
        WorkspaceException race =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(
                                                List.of(
                                                        featureLayer(
                                                                new WorkspaceRelativePath(
                                                                        "data/real.vec")))),
                                        context(sources, catalog()),
                                        CancellationToken.none(),
                                        changing));
        assertProblem(race, "WORKSPACE_PATH_INVALID", "reason", "identity", "layerIndex", "0");
        assertEquals(0, opens.get());
    }

    @Test
    void rejectsUnsafeExistingSidecarsBeforeOpening() throws IOException {
        create("data/feature.vec");
        Path sidecar = temporaryDirectory.resolve("data/feature.idx");
        Files.createSymbolicLink(sidecar, Path.of("feature.vec"));
        AtomicInteger opens = new AtomicInteger();
        WorkspaceSourceRegistry sources = featureSources(opens, null);
        WorkspaceException symlink =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(sources, catalog()),
                                        CancellationToken.none()));
        assertProblem(symlink, "WORKSPACE_PATH_INVALID", "reason", "symlink", "layerIndex", "0");

        Files.delete(sidecar);
        Files.createDirectory(sidecar);
        WorkspaceException wrongKind =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(sources, catalog()),
                                        CancellationToken.none()));
        assertProblem(
                wrongKind, "WORKSPACE_PATH_INVALID", "reason", "wrongKind", "layerIndex", "0");

        Files.delete(sidecar);
        create("data/feature.idx");
        WorkspaceException escape =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(sources, catalog()),
                                        CancellationToken.none(),
                                        new SidecarEscapePathAccess(sidecar)));
        assertProblem(escape, "WORKSPACE_PATH_INVALID", "reason", "escape", "layerIndex", "0");
        assertEquals(0, opens.get());
    }

    @Test
    void identityMismatchClosesCurrentAndEarlierSourcesInReverseOrder() throws IOException {
        create("data/first.vec");
        create("data/second.vec");
        List<String> events = new ArrayList<>();
        AtomicInteger call = new AtomicInteger();
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, cancellation) -> {
                                    int index = call.getAndIncrement();
                                    events.add("open-" + index);
                                    SourceIdentity actual =
                                            index == 0 ? identity : new SourceIdentity("wrong", "");
                                    return new TestFeatureSource(
                                            actual, Integer.toString(index), events, null);
                                })
                        .build();
        WorkspaceFeatureLayer first = featureLayer("first", "data/first.vec", FEATURE_ID);
        WorkspaceFeatureLayer second = featureLayer("second", "data/second.vec", RASTER_ID);

        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(first, second)),
                                        context(sources, catalog()),
                                        CancellationToken.none()));

        assertProblem(failure, "WORKSPACE_SOURCE_IDENTITY_MISMATCH", "layerIndex", "1");
        assertEquals(List.of("open-0", "open-1", "close-1", "close-0"), events);
    }

    @Test
    void mapsSourceFailuresAndRetainsTheirReports() throws IOException {
        create("data/feature.vec");
        SourceException ordinary = sourceFailure("SOURCE_READ_FAILED");
        WorkspaceSourceRegistry failed = featureSources(new AtomicInteger(), ordinary);

        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(failed, catalog()),
                                        CancellationToken.none()));
        assertProblem(
                failure, "WORKSPACE_SOURCE_OPEN_FAILED", "kind", "FEATURE", "layerIndex", "0");
        assertSame(ordinary, failure.getCause());
        assertEquals(ordinary.report(), failure.sourceReport().orElseThrow());

        SourceException cancelled = sourceFailure("SOURCE_CANCELLED");
        WorkspaceException cancellation =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(
                                                featureSources(new AtomicInteger(), cancelled),
                                                catalog()),
                                        CancellationToken.none()));
        assertProblem(
                cancellation, "WORKSPACE_CANCELLED", "phase", "sourceOpen", "layerIndex", "0");
        assertEquals(cancelled.report(), cancellation.sourceReport().orElseThrow());

        create("data/raster.img");
        WorkspaceSourceRegistry rasterFailed =
                WorkspaceSourceRegistry.builder()
                        .registerRaster(
                                "application.raster.v1",
                                RASTER_PROFILE,
                                (identity, path, token) -> {
                                    throw ordinary;
                                })
                        .build();
        WorkspaceException raster =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(rasterLayer())),
                                        context(rasterFailed, catalog()),
                                        CancellationToken.none()));
        assertProblem(raster, "WORKSPACE_SOURCE_OPEN_FAILED", "kind", "RASTER", "layerIndex", "0");
    }

    @Test
    void retainsSuccessfulSourceWarningsThroughAndAfterSessionClose() throws IOException {
        create("data/feature.vec");
        SourceDiagnostic warning =
                new SourceDiagnostic(
                        "SOURCE_FIXTURE_WARNING",
                        DiagnosticSeverity.WARNING,
                        FEATURE_ID.id(),
                        Optional.empty(),
                        "warning",
                        Map.of());
        DiagnosticReport report = new DiagnosticReport(List.of(warning), 0);
        TestFeatureSource source =
                new TestFeatureSource(FEATURE_ID, "feature", new ArrayList<>(), report, null);
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, token) -> source)
                        .build();
        WorkspaceSession session =
                WorkspaceOpener.open(
                        file(List.of(featureLayer())),
                        context(sources, catalog()),
                        CancellationToken.none());
        OpenedWorkspaceFeatureLayer layer =
                (OpenedWorkspaceFeatureLayer) session.layers().getFirst();
        assertSame(report, layer.source().openingDiagnostics());
        session.close();
        assertSame(report, layer.source().openingDiagnostics());
    }

    @Test
    void reportsCancellationAtEveryOpeningPhaseAndCleansPostOpenState() throws IOException {
        create("data/feature.vec");
        WorkspaceOpenContext context =
                context(featureSources(new AtomicInteger(), null), catalog());
        assertCancellation(context, 1, "preflight", false);
        assertCancellation(context, 3, "preflight", true);
        assertCancellation(context, 5, "path", true);
        assertCancellation(context, 7, "sourceOpen", true);

        AtomicBoolean cancelled = new AtomicBoolean();
        List<String> events = new ArrayList<>();
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, token) -> {
                                    cancelled.set(true);
                                    return new TestFeatureSource(identity, "current", events, null);
                                })
                        .build();
        WorkspaceException postOpen =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(sources, catalog()),
                                        cancelled::get));
        assertProblem(postOpen, "WORKSPACE_CANCELLED", "phase", "sourceOpen", "layerIndex", "0");
        assertEquals(List.of("close-current"), events);

        CountingCancellation publish = new CountingCancellation(9);
        WorkspaceException atPublish =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context(
                                                featureSources(new AtomicInteger(), null),
                                                catalog()),
                                        publish));
        assertProblem(atPublish, "WORKSPACE_CANCELLED", "phase", "publish");
    }

    @Test
    void preservesProgrammerAndCloseFailureOrdering() throws IOException {
        create("data/first.vec");
        create("data/second.vec");
        List<String> events = new ArrayList<>();
        RuntimeException cleanup = new IllegalStateException("cleanup");
        AtomicInteger calls = new AtomicInteger();
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, cancellation) -> {
                                    if (calls.getAndIncrement() == 0) {
                                        return new TestFeatureSource(
                                                identity, "first", events, cleanup);
                                    }
                                    throw new UnsupportedOperationException("programmer");
                                })
                        .build();
        RuntimeException failure =
                assertThrows(
                        UnsupportedOperationException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(
                                                List.of(
                                                        featureLayer(
                                                                "first",
                                                                "data/first.vec",
                                                                FEATURE_ID),
                                                        featureLayer(
                                                                "second",
                                                                "data/second.vec",
                                                                RASTER_ID))),
                                        context(sources, catalog()),
                                        CancellationToken.none()));
        assertEquals("programmer", failure.getMessage());
        assertEquals(List.of(cleanup), List.of(failure.getSuppressed()));

        List<String> errorEvents = new ArrayList<>();
        AtomicInteger errorCalls = new AtomicInteger();
        WorkspaceSourceRegistry errorSources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, cancellation) -> {
                                    if (errorCalls.getAndIncrement() == 0) {
                                        return new TestFeatureSource(
                                                identity, "first", errorEvents, null);
                                    }
                                    throw new AssertionError("programmer-error");
                                })
                        .build();
        AssertionError error =
                assertThrows(
                        AssertionError.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(
                                                List.of(
                                                        featureLayer(
                                                                "first",
                                                                "data/first.vec",
                                                                FEATURE_ID),
                                                        featureLayer(
                                                                "second",
                                                                "data/second.vec",
                                                                RASTER_ID))),
                                        context(errorSources, catalog()),
                                        CancellationToken.none()));
        assertEquals("programmer-error", error.getMessage());
        assertEquals(List.of("close-first"), errorEvents);

        RuntimeException currentCleanup = new IllegalStateException("current-cleanup");
        AssertionError earlierCleanup = new AssertionError("earlier-cleanup");
        AtomicInteger mismatchCalls = new AtomicInteger();
        WorkspaceSourceRegistry mismatchSources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, cancellation) -> {
                                    if (mismatchCalls.getAndIncrement() == 0) {
                                        return new TestFeatureSource(
                                                identity,
                                                "earlier",
                                                new ArrayList<>(),
                                                earlierCleanup);
                                    }
                                    return new TestFeatureSource(
                                            new SourceIdentity("wrong", ""),
                                            "current",
                                            new ArrayList<>(),
                                            currentCleanup);
                                })
                        .build();
        WorkspaceException mismatch =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(
                                                List.of(
                                                        featureLayer(
                                                                "first",
                                                                "data/first.vec",
                                                                FEATURE_ID),
                                                        featureLayer(
                                                                "second",
                                                                "data/second.vec",
                                                                RASTER_ID))),
                                        context(mismatchSources, catalog()),
                                        CancellationToken.none()));
        assertEquals(List.of(currentCleanup, earlierCleanup), List.of(mismatch.getSuppressed()));

        AssertionError shared = new AssertionError("shared");
        AtomicInteger sharedCalls = new AtomicInteger();
        List<String> sharedEvents = new ArrayList<>();
        WorkspaceSourceRegistry sharedSources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                "application.feature.v1",
                                FEATURE_PROFILE,
                                (identity, path, cancellation) -> {
                                    if (sharedCalls.getAndIncrement() == 0) {
                                        return new TestFeatureSource(
                                                identity, "shared", sharedEvents, shared);
                                    }
                                    throw shared;
                                })
                        .build();
        AssertionError sharedTransaction =
                assertThrows(
                        AssertionError.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(
                                                List.of(
                                                        featureLayer(
                                                                "first",
                                                                "data/first.vec",
                                                                FEATURE_ID),
                                                        featureLayer(
                                                                "second",
                                                                "data/second.vec",
                                                                RASTER_ID))),
                                        context(sharedSources, catalog()),
                                        CancellationToken.none()));
        assertSame(shared, sharedTransaction);
        assertEquals(List.of("close-shared"), sharedEvents);
        assertEquals(0, shared.getSuppressed().length);

        AssertionError rasterClose = new AssertionError("raster-close");
        RuntimeException featureClose = new IllegalStateException("feature-close");
        WorkspaceSession session =
                new WorkspaceSession(
                        document(List.of()),
                        CrsDefinitions.EPSG_4326,
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new OpenedWorkspaceFeatureLayer(
                                        featureLayer(),
                                        new TestFeatureSource(
                                                FEATURE_ID, "feature", events, featureClose),
                                        marker(),
                                        line(),
                                        fill()),
                                new OpenedWorkspaceRasterLayer(
                                        rasterLayer(),
                                        new TestRasterSource(
                                                RASTER_ID, "raster", events, rasterClose))));
        AssertionError close = assertThrows(AssertionError.class, session::close);
        assertSame(rasterClose, close);
        assertEquals(List.of(featureClose), List.of(close.getSuppressed()));
        session.close();

        List<String> sharedSessionEvents = new ArrayList<>();
        WorkspaceSession sharedSession =
                new WorkspaceSession(
                        document(List.of()),
                        CrsDefinitions.EPSG_4326,
                        CrsDefinitions.EPSG_3857,
                        List.of(
                                new OpenedWorkspaceFeatureLayer(
                                        featureLayer(),
                                        new TestFeatureSource(
                                                FEATURE_ID,
                                                "feature-shared",
                                                sharedSessionEvents,
                                                shared),
                                        marker(),
                                        line(),
                                        fill()),
                                new OpenedWorkspaceRasterLayer(
                                        rasterLayer(),
                                        new TestRasterSource(
                                                RASTER_ID,
                                                "raster-shared",
                                                sharedSessionEvents,
                                                shared))));
        assertSame(shared, assertThrows(AssertionError.class, sharedSession::close));
        assertEquals(List.of("close-raster-shared", "close-feature-shared"), sharedSessionEvents);
        assertEquals(0, shared.getSuppressed().length);
    }

    private void assertCancellation(
            WorkspaceOpenContext context, int call, String phase, boolean indexed) {
        WorkspaceException failure =
                assertThrows(
                        WorkspaceException.class,
                        () ->
                                WorkspaceOpener.open(
                                        file(List.of(featureLayer())),
                                        context,
                                        new CountingCancellation(call)));
        if (indexed) {
            assertProblem(failure, "WORKSPACE_CANCELLED", "phase", phase, "layerIndex", "0");
        } else {
            assertProblem(failure, "WORKSPACE_CANCELLED", "phase", phase);
        }
    }

    private WorkspaceOpenContext context(
            WorkspaceSourceRegistry sources, NamedSymbolCatalog symbols) {
        return new WorkspaceOpenContext(CrsRegistry.level1(), sources, registeredCatalog(symbols));
    }

    private WorkspaceSymbolCatalogRegistry registeredCatalog(NamedSymbolCatalog symbols) {
        return WorkspaceSymbolCatalogRegistry.builder()
                .register("application.symbols", symbols)
                .build();
    }

    private WorkspaceSourceRegistry featureSources(AtomicInteger opens, SourceException failure) {
        return WorkspaceSourceRegistry.builder()
                .registerFeature(
                        "application.feature.v1",
                        FEATURE_PROFILE,
                        (identity, path, cancellation) -> {
                            opens.incrementAndGet();
                            if (failure != null) {
                                throw failure;
                            }
                            return new TestFeatureSource(
                                    identity, "feature", new ArrayList<>(), null);
                        })
                .build();
    }

    private WorkspaceFile file(List<WorkspaceLayerDefinition> layers) {
        return new WorkspaceFile(document(layers), temporaryDirectory.toAbsolutePath().normalize());
    }

    private static WorkspaceDocument document(List<WorkspaceLayerDefinition> layers) {
        return new WorkspaceDocument(
                new WorkspaceViewState("EPSG:4326", "EPSG:3857", 0, 0, 1), layers);
    }

    private static WorkspaceFeatureLayer featureLayer() {
        return featureLayer(new WorkspaceRelativePath("data/feature.vec"));
    }

    private static WorkspaceFeatureLayer featureLayer(WorkspaceRelativePath path) {
        return new WorkspaceFeatureLayer(
                "feature",
                "Feature",
                new WorkspaceSourceReference("application.feature.v1", FEATURE_ID, path),
                new WorkspaceSymbolReferences("application.symbols", "marker", "line", "fill"));
    }

    private static WorkspaceFeatureLayer featureLayer(
            String layerId, String path, SourceIdentity identity) {
        return new WorkspaceFeatureLayer(
                layerId,
                layerId,
                new WorkspaceSourceReference(
                        "application.feature.v1", identity, new WorkspaceRelativePath(path)),
                new WorkspaceSymbolReferences("application.symbols", "marker", "line", "fill"));
    }

    private static WorkspaceRasterLayer rasterLayer() {
        return new WorkspaceRasterLayer(
                "raster",
                "Raster",
                new WorkspaceSourceReference(
                        "application.raster.v1",
                        RASTER_ID,
                        new WorkspaceRelativePath("data/raster.img")),
                RasterInterpolation.BILINEAR,
                0.75);
    }

    private Path create(String relative) throws IOException {
        Path file = temporaryDirectory.resolve(relative);
        Files.createDirectories(Objects.requireNonNull(file.getParent()));
        return Files.writeString(file, "fixture");
    }

    private static NamedSymbolCatalog catalog() {
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol("marker", marker()),
                        new NamedSymbol("line", line()),
                        new NamedSymbol("fill", fill())));
    }

    private static io.github.mundanej.map.api.Symbol marker() {
        return BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, Rgba.rgb(10, 20, 30), 8, 1);
    }

    private static io.github.mundanej.map.api.Symbol line() {
        return SolidLineSymbol.of(
                new SymbolStroke(
                        Rgba.rgb(30, 40, 50), new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                1);
    }

    private static io.github.mundanej.map.api.Symbol fill() {
        return SolidFillSymbol.of(Rgba.rgb(50, 60, 70), 1);
    }

    private FeatureSource unexpectedFeature(
            SourceIdentity identity, Path path, CancellationToken cancellation) {
        throw new AssertionError("unexpected opener invocation");
    }

    private static SourceException sourceFailure(String code) {
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        "feature",
                        Optional.empty(),
                        "failure",
                        Map.of());
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal);
    }

    private static void assertProblem(
            WorkspaceException failure, String code, String... contextPairs) {
        assertEquals(code, failure.problem().code());
        java.util.LinkedHashMap<String, String> expected = new java.util.LinkedHashMap<>();
        for (int index = 0; index < contextPairs.length; index += 2) {
            expected.put(contextPairs[index], contextPairs[index + 1]);
        }
        assertEquals(expected, failure.problem().context());
    }

    private static final class CountingCancellation implements CancellationToken {
        private final int cancelAt;
        private int calls;

        private CountingCancellation(int cancelAt) {
            this.cancelAt = cancelAt;
        }

        @Override
        public boolean isCancellationRequested() {
            return ++calls >= cancelAt;
        }
    }

    private static final class SidecarEscapePathAccess implements WorkspacePathAccess {
        private final Path sidecar;

        private SidecarEscapePathAccess(Path sidecar) {
            this.sidecar = sidecar;
        }

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            return WorkspacePathAccess.JDK.attributes(path);
        }

        @Override
        public Path realPath(Path path) throws IOException {
            if (path.equals(sidecar)) {
                return Objects.requireNonNull(sidecar.getRoot())
                        .resolve("outside-workspace-sidecar");
            }
            return WorkspacePathAccess.JDK.realPath(path);
        }
    }

    private static final class ChangingIdentityPathAccess implements WorkspacePathAccess {
        private int reads;

        @Override
        public BasicFileAttributes attributes(Path path) throws IOException {
            BasicFileAttributes delegate = WorkspacePathAccess.JDK.attributes(path);
            if (delegate == null || ++reads == 1) {
                return delegate;
            }
            return new DelegatingAttributes(delegate) {
                @Override
                public Object fileKey() {
                    return "changed";
                }
            };
        }

        @Override
        public Path realPath(Path path) throws IOException {
            return WorkspacePathAccess.JDK.realPath(path);
        }
    }

    private static class DelegatingAttributes implements BasicFileAttributes {
        private final BasicFileAttributes delegate;

        private DelegatingAttributes(BasicFileAttributes delegate) {
            this.delegate = delegate;
        }

        @Override
        public java.nio.file.attribute.FileTime lastModifiedTime() {
            return delegate.lastModifiedTime();
        }

        @Override
        public java.nio.file.attribute.FileTime lastAccessTime() {
            return delegate.lastAccessTime();
        }

        @Override
        public java.nio.file.attribute.FileTime creationTime() {
            return delegate.creationTime();
        }

        @Override
        public boolean isRegularFile() {
            return delegate.isRegularFile();
        }

        @Override
        public boolean isDirectory() {
            return delegate.isDirectory();
        }

        @Override
        public boolean isSymbolicLink() {
            return delegate.isSymbolicLink();
        }

        @Override
        public boolean isOther() {
            return delegate.isOther();
        }

        @Override
        public long size() {
            return delegate.size();
        }

        @Override
        public Object fileKey() {
            return delegate.fileKey();
        }
    }

    private static final class TestFeatureSource implements FeatureSource {
        private final FeatureSourceMetadata metadata;
        private final String name;
        private final List<String> events;
        private final DiagnosticReport diagnostics;
        private final Throwable closeFailure;
        private boolean closed;

        private TestFeatureSource(
                SourceIdentity identity, String name, List<String> events, Throwable closeFailure) {
            this(identity, name, events, DiagnosticReport.empty(), closeFailure);
        }

        private TestFeatureSource(
                SourceIdentity identity,
                String name,
                List<String> events,
                DiagnosticReport diagnostics,
                Throwable closeFailure) {
            metadata =
                    new FeatureSourceMetadata(
                            identity,
                            Optional.empty(),
                            OptionalLong.empty(),
                            Optional.empty(),
                            Optional.empty());
            this.name = name;
            this.events = events;
            this.diagnostics = diagnostics;
            this.closeFailure = closeFailure;
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
            return diagnostics;
        }

        @Override
        public FeatureCursor openCursor(FeatureQuery query, CancellationToken cancellation) {
            throw new AssertionError("unused");
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
            events.add("close-" + name);
            if (closeFailure != null) {
                throwUnchecked(closeFailure);
            }
        }
    }

    private static final class TestRasterSource implements RasterSource {
        private final RasterSourceMetadata metadata;
        private final String name;
        private final List<String> events;
        private final Throwable closeFailure;
        private boolean closed;

        private TestRasterSource(
                SourceIdentity identity, String name, List<String> events, Throwable closeFailure) {
            metadata = new RasterSourceMetadata(identity, 1, 1, Optional.empty(), Optional.empty());
            this.name = name;
            this.events = events;
            this.closeFailure = closeFailure;
        }

        @Override
        public RasterSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public RasterSourceLimits limits() {
            return RasterSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            throw new AssertionError("unused");
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
            events.add("close-" + name);
            if (closeFailure != null) {
                throwUnchecked(closeFailure);
            }
        }
    }

    private static void throwUnchecked(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw (Error) failure;
    }
}
