package io.github.mundanej.map.example.vaadin;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CancellationSource;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureQueryLimits;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import io.github.mundanej.map.vaadin.BrowserHorizontalWrapMode;
import io.github.mundanej.map.vaadin.BrowserRasterOptions;
import io.github.mundanej.map.vaadin.ElevationSourceBinding;
import io.github.mundanej.map.vaadin.FeatureSourceBinding;
import io.github.mundanej.map.vaadin.MundaneMap;
import io.github.mundanej.map.vaadin.RasterSourceBinding;
import io.github.mundanej.map.workspace.OpenedWorkspaceFeatureLayer;
import io.github.mundanej.map.workspace.OpenedWorkspaceLayer;
import io.github.mundanej.map.workspace.OpenedWorkspaceRasterLayer;
import io.github.mundanej.map.workspace.WorkspaceException;
import io.github.mundanej.map.workspace.WorkspaceFile;
import io.github.mundanej.map.workspace.WorkspaceFiles;
import io.github.mundanej.map.workspace.WorkspaceLimits;
import io.github.mundanej.map.workspace.WorkspaceLocalPathBranch;
import io.github.mundanej.map.workspace.WorkspaceLocalPathProfile;
import io.github.mundanej.map.workspace.WorkspaceOpenContext;
import io.github.mundanej.map.workspace.WorkspaceOpener;
import io.github.mundanej.map.workspace.WorkspaceRasterLayer;
import io.github.mundanej.map.workspace.WorkspaceSession;
import io.github.mundanej.map.workspace.WorkspaceSourceRegistry;
import io.github.mundanej.map.workspace.WorkspaceSymbolCatalogRegistry;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Cancellable server-local source workflows owned by one viewer route. */
final class ViewerSourceWorkflows implements AutoCloseable {
    static final String SHAPEFILE_OPENER = "viewer.shapefile.v1";
    static final String CATALOG_ID = "viewer.default";
    private static final FeatureQueryLimits FEATURE_LIMITS =
            new FeatureQueryLimits(100_000, 25_000, 1_000_000, 250_000, 2_000_000, 32_000_000, 64);
    private static final RasterRequestLimits RASTER_LIMITS =
            new RasterRequestLimits(16_777_216, 4096, 8_388_608, 134_217_728, 134_217_728, 64);
    private static final WorkspaceLimits WORKSPACE_LIMITS =
            new WorkspaceLimits(1_048_576, 8_388_608, 8, 4096, 16_384, 128, 1024, 262_144);

    enum Kind {
        FEATURE,
        RASTER,
        ELEVATION
    }

    record SourceLayer(String id, String name, Kind kind, boolean visible) {}

    record OpenResult(boolean opened, String diagnosticCode) {
        static OpenResult success() {
            return new OpenResult(true, "SOURCE_OPENED");
        }

        static OpenResult failure(String code) {
            return new OpenResult(false, code);
        }
    }

    private final MundaneMap map;
    private final Executor executor;
    private final Consumer<Runnable> dispatcher;
    private final Openers openers;
    private final Runnable changed;
    private final ExecutorService ownedExecutor;
    private Active active = Active.empty();
    private volatile CancellationSource pendingCancellation;
    private final AtomicLong generation = new AtomicLong();
    private volatile String diagnosticCode = "NO_SOURCE_DIAGNOSTICS";
    private volatile boolean busy;
    private volatile boolean closed;
    private boolean wrapEnabled;

    ViewerSourceWorkflows(MundaneMap map, Consumer<Runnable> dispatcher, Runnable changed) {
        this(map, createExecutor(), dispatcher, Openers.production(), changed, true);
    }

    ViewerSourceWorkflows(
            MundaneMap map,
            Executor executor,
            Consumer<Runnable> dispatcher,
            Openers openers,
            Runnable changed) {
        this(map, executor, dispatcher, openers, changed, false);
    }

    ViewerSourceWorkflows(
            MundaneMap map,
            Executor executor,
            Consumer<Runnable> dispatcher,
            Openers openers,
            Runnable changed,
            boolean ownsExecutor) {
        this.map = Objects.requireNonNull(map, "map");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.openers = Objects.requireNonNull(openers, "openers");
        this.changed = Objects.requireNonNull(changed, "changed");
        ownedExecutor = ownsExecutor ? (ExecutorService) executor : null;
    }

    synchronized CompletionStage<OpenResult> openShapefile(Path path) {
        return open(path, (checked, token) -> Active.feature(openers.shapefile(checked, token)));
    }

    synchronized CompletionStage<OpenResult> openRaster(Path path) {
        return open(path, (checked, token) -> Active.raster(openers.raster(checked, token)));
    }

    synchronized CompletionStage<OpenResult> openElevation(Path path) {
        return open(path, (checked, token) -> Active.elevation(openers.elevation(checked, token)));
    }

    synchronized CompletionStage<OpenResult> openWorkspace(Path path) {
        return open(path, (checked, token) -> Active.workspace(openers.workspace(checked, token)));
    }

    List<SourceLayer> layers() {
        return active.layers();
    }

    String diagnosticCode() {
        return diagnosticCode;
    }

    boolean busy() {
        return busy;
    }

    synchronized CompletionStage<OpenResult> rejectInvalidPath() {
        requireOpen();
        cancelPending();
        diagnosticCode = "SOURCE_PATH_INVALID";
        notifyChanged();
        return CompletableFuture.completedFuture(OpenResult.failure(diagnosticCode));
    }

    synchronized void setVisible(String id, boolean visible) {
        requireOpen();
        Active candidate = active.withVisibility(id, visible);
        installBindings(candidate);
        active = candidate;
        notifyChanged();
    }

    synchronized void setWrapEnabled(boolean enabled) {
        requireOpen();
        Active candidate = active.withWrap(enabled);
        try {
            installBindings(candidate);
            active = candidate;
            wrapEnabled = enabled;
        } catch (RuntimeException | Error failure) {
            candidate.closeBindingsOnly(failure);
            throw failure;
        }
        notifyChanged();
    }

    synchronized void move(String id, int delta) {
        requireOpen();
        active = active.moved(id, delta);
        installBindings(active);
        notifyChanged();
    }

    synchronized void clear() {
        requireOpen();
        cancelPending();
        Active previous = active;
        active = Active.empty();
        Throwable primary = null;
        try {
            detachBindings();
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        try {
            previous.close();
        } catch (RuntimeException | Error failure) {
            primary = suppress(primary, failure);
        }
        diagnosticCode = "NO_SOURCE_DIAGNOSTICS";
        notifyChanged();
        throwIfPresent(primary);
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        cancelPending();
        Active previous = active;
        active = Active.empty();
        Throwable primary = null;
        try {
            detachBindings();
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        try {
            previous.close();
        } catch (RuntimeException | Error failure) {
            primary = suppress(primary, failure);
        }
        if (ownedExecutor != null) {
            ownedExecutor.shutdown();
        }
        throwIfPresent(primary);
    }

    private CompletionStage<OpenResult> open(Path path, Loader loader) {
        requireOpen();
        Path checked = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        cancelPending();
        CancellationSource cancellation = new CancellationSource();
        pendingCancellation = cancellation;
        long operation = generation.incrementAndGet();
        busy = true;
        diagnosticCode = "SOURCE_OPENING";
        notifyChanged();
        CompletableFuture<OpenResult> result = new CompletableFuture<>();
        try {
            executor.execute(
                    () -> {
                        Active opened = null;
                        Throwable failure = null;
                        try {
                            opened = loader.load(checked, cancellation.token());
                        } catch (RuntimeException | Error thrown) {
                            failure = thrown;
                        }
                        Active candidate = opened;
                        Throwable terminal = failure;
                        try {
                            dispatcher.accept(
                                    () ->
                                            finish(
                                                    operation,
                                                    cancellation,
                                                    candidate,
                                                    terminal,
                                                    result));
                        } catch (RuntimeException | Error dispatchFailure) {
                            closeCandidate(candidate, dispatchFailure);
                            settleFailedOperation(
                                    operation, cancellation, dispatchFailure, result, false);
                        }
                    });
        } catch (RuntimeException | Error submissionFailure) {
            settleFailedOperation(operation, cancellation, submissionFailure, result, true);
        }
        return result;
    }

    private synchronized void finish(
            long operation,
            CancellationSource cancellation,
            Active candidate,
            Throwable failure,
            CompletableFuture<OpenResult> result) {
        if (closed || operation != generation.get() || cancellation != pendingCancellation) {
            closeCandidate(candidate, null);
            complete(result, OpenResult.failure("SOURCE_OPEN_CANCELLED"));
            return;
        }
        pendingCancellation = null;
        busy = false;
        if (failure != null) {
            diagnosticCode = stableCode(failure);
            notifyChanged();
            complete(result, OpenResult.failure(diagnosticCode));
            return;
        }
        Active prepared = candidate;
        try {
            prepared = Objects.requireNonNull(candidate, "candidate");
            if (wrapEnabled) {
                prepared = prepared.withWrapForReplacement(true);
            }
            replace(prepared);
            diagnosticCode = "SOURCE_OPENED";
            notifyChanged();
            complete(result, OpenResult.success());
        } catch (RuntimeException | Error installFailure) {
            closeCandidate(prepared, installFailure);
            diagnosticCode = stableCode(installFailure);
            notifyChanged();
            complete(result, OpenResult.failure(diagnosticCode));
        }
    }

    private void replace(Active candidate) {
        detachBindings();
        Active previous = active;
        active = Active.empty();
        previous.close();
        try {
            installBindings(candidate);
            active = candidate;
        } catch (RuntimeException | Error failure) {
            detachBindingsSuppressing(failure);
            throw failure;
        }
    }

    private void installBindings(Active value) {
        map.setFeatureSourceBindings(value.visibleFeatures());
        map.setRasterSourceBindings(value.visibleRasters());
        map.setElevationSourceBindings(value.visibleElevations());
    }

    private void detachBindings() {
        Throwable primary = null;
        try {
            map.setFeatureSourceBindings(List.of());
        } catch (RuntimeException | Error failure) {
            primary = failure;
        }
        try {
            map.setRasterSourceBindings(List.of());
        } catch (RuntimeException | Error failure) {
            primary = suppress(primary, failure);
        }
        try {
            map.setElevationSourceBindings(List.of());
        } catch (RuntimeException | Error failure) {
            primary = suppress(primary, failure);
        }
        throwIfPresent(primary);
    }

    private void detachBindingsSuppressing(Throwable primary) {
        try {
            detachBindings();
        } catch (RuntimeException | Error cleanup) {
            primary.addSuppressed(cleanup);
        }
    }

    private void cancelPending() {
        generation.incrementAndGet();
        if (pendingCancellation != null) {
            pendingCancellation.cancel();
            pendingCancellation = null;
        }
        busy = false;
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException("source workflows are closed");
        }
    }

    private synchronized void settleFailedOperation(
            long operation,
            CancellationSource cancellation,
            Throwable failure,
            CompletableFuture<OpenResult> result,
            boolean notify) {
        if (operation == generation.get() && cancellation == pendingCancellation) {
            pendingCancellation = null;
            busy = false;
            diagnosticCode = stableCode(failure);
            if (notify) {
                notifyChanged();
            }
        }
        complete(result, OpenResult.failure(stableCode(failure)));
    }

    private static Throwable closeCandidate(Active candidate, Throwable primary) {
        if (candidate != null) {
            try {
                candidate.close();
            } catch (RuntimeException | Error cleanup) {
                return suppress(primary, cleanup);
            }
        }
        return primary;
    }

    private static void complete(CompletableFuture<OpenResult> result, OpenResult completion) {
        result.complete(completion);
    }

    private void notifyChanged() {
        try {
            changed.run();
        } catch (RuntimeException | Error ignored) {
            // Observation is best effort and never changes committed source ownership.
        }
    }

    private static String stableCode(Throwable failure) {
        if (failure instanceof SourceException source) {
            return source.terminal().code();
        }
        if (failure instanceof WorkspaceException workspace) {
            return workspace.problem().code();
        }
        if (failure instanceof IllegalArgumentException) {
            return "SOURCE_PATH_INVALID";
        }
        return "SOURCE_OPEN_FAILED";
    }

    private static Throwable suppress(Throwable primary, Throwable cleanup) {
        if (primary == null) {
            return cleanup;
        }
        if (primary != cleanup) {
            primary.addSuppressed(cleanup);
        }
        return primary;
    }

    private static void throwIfPresent(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    private static ExecutorService createExecutor() {
        return Executors.newSingleThreadExecutor(
                runnable -> {
                    Thread thread = new Thread(runnable, "vaadin-viewer-source-open");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    private static FeatureSourceBinding featureBinding(
            String id, String name, FeatureSource source, FeaturePortrayal portrayal) {
        return FeatureSourceBinding.owned(id, name, source, portrayal, Optional.of(FEATURE_LIMITS));
    }

    private static FeatureSourceBinding leasedFeatureBinding(
            String id, String name, SerializedFeature shared, FeaturePortrayal portrayal) {
        FeatureSource lease = shared.lease();
        try {
            return featureBinding(id, name, lease, portrayal);
        } catch (RuntimeException | Error failure) {
            lease.close();
            throw failure;
        }
    }

    private static RasterSourceBinding rasterBinding(
            String id, String name, RasterSource source, BrowserRasterOptions options) {
        return RasterSourceBinding.owned(id, name, source, options, Optional.of(RASTER_LIMITS));
    }

    private static RasterSourceBinding leasedRasterBinding(
            String id, String name, SerializedRaster shared, BrowserRasterOptions options) {
        RasterSource lease = shared.lease();
        try {
            return rasterBinding(id, name, lease, options);
        } catch (RuntimeException | Error failure) {
            lease.close();
            throw failure;
        }
    }

    private static ElevationSourceBinding elevationBinding(ElevationSource source) {
        ElevationUnit unit = source.metadata().elevationUnit();
        ElevationRasterStyle style =
                ElevationRasterStyle.of(
                        new ElevationColorRamp(
                                unit,
                                List.of(
                                        new ElevationColorStop(-12_000, Rgba.rgb(12, 48, 96)),
                                        new ElevationColorStop(0, Rgba.rgb(42, 120, 62)),
                                        new ElevationColorStop(9_000, Rgba.rgb(248, 248, 248)))));
        return ElevationSourceBinding.owned(
                "opened-elevation",
                "Opened elevation",
                source,
                style,
                BrowserRasterOptions.defaults(),
                RASTER_LIMITS);
    }

    private static ElevationSourceBinding leasedElevationBinding(SerializedElevation shared) {
        ElevationSource lease = shared.lease();
        try {
            return elevationBinding(lease);
        } catch (RuntimeException | Error failure) {
            lease.close();
            throw failure;
        }
    }

    private static FeaturePortrayal portrayal() {
        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(25, 70, 135),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1);
        return FeaturePortrayal.fixed(
                BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, Rgba.rgb(220, 70, 35), 12, 1),
                line,
                SolidFillSymbol.of(new Rgba(35, 125, 205, 90), Optional.of(line), 1));
    }

    private static NamedSymbolCatalog catalog() {
        SolidLineSymbol boundary =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.rgb(25, 70, 135),
                                new SymbolLength(2, SymbolUnit.SCREEN_PIXEL)),
                        1);
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol(
                                "point",
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, Rgba.rgb(220, 70, 35), 12, 1)),
                        new NamedSymbol("boundary", boundary),
                        new NamedSymbol(
                                "area",
                                SolidFillSymbol.of(
                                        new Rgba(35, 125, 205, 90), Optional.of(boundary), 1))));
    }

    private static WorkspaceOpenContext workspaceContext() {
        WorkspaceLocalPathProfile shape =
                new WorkspaceLocalPathProfile(
                        List.of(
                                new WorkspaceLocalPathBranch(
                                        ".shp",
                                        List.of(
                                                ".shx", ".SHX", ".dbf", ".DBF", ".cpg", ".CPG",
                                                ".prj", ".PRJ"))));
        WorkspaceSourceRegistry sources =
                WorkspaceSourceRegistry.builder()
                        .registerFeature(
                                SHAPEFILE_OPENER,
                                shape,
                                (identity, path, cancellation) ->
                                        Shapefiles.open(
                                                identity,
                                                path,
                                                ShapefileOpenOptions.defaults(),
                                                cancellation))
                        .build();
        return new WorkspaceOpenContext(
                CrsRegistry.level1(),
                sources,
                WorkspaceSymbolCatalogRegistry.builder().register(CATALOG_ID, catalog()).build());
    }

    @FunctionalInterface
    private interface Loader {
        Active load(Path path, CancellationToken cancellation);
    }

    interface Openers {
        FeatureSource shapefile(Path path, CancellationToken cancellation);

        RasterSource raster(Path path, CancellationToken cancellation);

        ElevationSource elevation(Path path, CancellationToken cancellation);

        WorkspaceSession workspace(Path path, CancellationToken cancellation);

        static Openers production() {
            return new Openers() {
                @Override
                public FeatureSource shapefile(Path path, CancellationToken cancellation) {
                    return Shapefiles.open(
                            new SourceIdentity("viewer-shapefile", "Opened shapefile"),
                            path,
                            ShapefileOpenOptions.defaults(),
                            cancellation);
                }

                @Override
                public RasterSource raster(Path path, CancellationToken cancellation) {
                    return GeoTiffFiles.openRaster(
                            new SourceIdentity("viewer-raster", "Opened GeoTIFF"),
                            path,
                            GeoTiffRasterOptions.defaults(),
                            cancellation);
                }

                @Override
                public ElevationSource elevation(Path path, CancellationToken cancellation) {
                    return GeoTiffFiles.openElevation(
                            new SourceIdentity("viewer-elevation", "Opened elevation"),
                            path,
                            GeoTiffElevationOptions.of(ElevationUnit.METRE),
                            cancellation);
                }

                @Override
                public WorkspaceSession workspace(Path path, CancellationToken cancellation) {
                    WorkspaceFile file = WorkspaceFiles.read(path, WORKSPACE_LIMITS);
                    return WorkspaceOpener.open(file, workspaceContext(), cancellation);
                }
            };
        }
    }

    private static final class Active implements AutoCloseable {
        private final List<Entry> entries;
        private final SharedLifetime lifetime;
        private boolean retired;

        private Active(List<Entry> entries, SharedLifetime lifetime) {
            this.entries = List.copyOf(entries);
            this.lifetime = Objects.requireNonNull(lifetime, "lifetime");
        }

        static Active empty() {
            return new Active(List.of(), SharedLifetime.empty());
        }

        static Active feature(FeatureSource source) {
            SharedLifetime lifetime = new SharedLifetime(source);
            SerializedFeature shared = new SerializedFeature(source, lifetime);
            List<Entry> entries = new ArrayList<>();
            try {
                entries.add(
                        Entry.feature(
                                "opened-shapefile",
                                "Opened shapefile",
                                () ->
                                        leasedFeatureBinding(
                                                "opened-shapefile",
                                                "Opened shapefile",
                                                shared,
                                                portrayal())));
                return new Active(entries, lifetime);
            } catch (RuntimeException | Error failure) {
                throw constructionFailure(entries, lifetime, failure);
            }
        }

        static Active raster(RasterSource source) {
            SharedLifetime lifetime = new SharedLifetime(source);
            SerializedRaster shared = new SerializedRaster(source, lifetime);
            List<Entry> entries = new ArrayList<>();
            try {
                entries.add(
                        Entry.raster(
                                "opened-raster",
                                "Opened GeoTIFF",
                                () ->
                                        leasedRasterBinding(
                                                "opened-raster",
                                                "Opened GeoTIFF",
                                                shared,
                                                BrowserRasterOptions.defaults())));
                return new Active(entries, lifetime);
            } catch (RuntimeException | Error failure) {
                throw constructionFailure(entries, lifetime, failure);
            }
        }

        static Active elevation(ElevationSource source) {
            SharedLifetime lifetime = new SharedLifetime(source);
            SerializedElevation shared = new SerializedElevation(source, lifetime);
            List<Entry> entries = new ArrayList<>();
            try {
                entries.add(
                        Entry.elevation(
                                "opened-elevation",
                                "Opened elevation",
                                () -> leasedElevationBinding(shared)));
                return new Active(entries, lifetime);
            } catch (RuntimeException | Error failure) {
                throw constructionFailure(entries, lifetime, failure);
            }
        }

        static Active workspace(WorkspaceSession workspace) {
            SharedLifetime lifetime = new SharedLifetime(workspace);
            List<Entry> entries = new ArrayList<>();
            try {
                for (OpenedWorkspaceLayer layer : workspace.layers()) {
                    if (layer instanceof OpenedWorkspaceFeatureLayer feature) {
                        SerializedFeature shared =
                                new SerializedFeature(feature.source(), lifetime);
                        String name =
                                displayName(feature.definition().id(), feature.definition().name());
                        entries.add(
                                Entry.feature(
                                        feature.definition().id(),
                                        name,
                                        () ->
                                                leasedFeatureBinding(
                                                        feature.definition().id(),
                                                        name,
                                                        shared,
                                                        FeaturePortrayal.fixed(
                                                                feature.marker(),
                                                                feature.line(),
                                                                feature.fill()))));
                    } else {
                        OpenedWorkspaceRasterLayer raster = (OpenedWorkspaceRasterLayer) layer;
                        WorkspaceRasterLayer definition = raster.definition();
                        SerializedRaster shared = new SerializedRaster(raster.source(), lifetime);
                        String name = displayName(definition.id(), definition.name());
                        entries.add(
                                Entry.raster(
                                        definition.id(),
                                        name,
                                        () ->
                                                leasedRasterBinding(
                                                        definition.id(),
                                                        name,
                                                        shared,
                                                        new BrowserRasterOptions(
                                                                definition.interpolation(),
                                                                definition.opacity()))));
                    }
                }
                return new Active(entries, lifetime);
            } catch (RuntimeException | Error failure) {
                throw constructionFailure(entries, lifetime, failure);
            }
        }

        private static String displayName(String id, String name) {
            return name.isBlank() ? id : name;
        }

        private static RuntimeException constructionFailure(
                List<Entry> entries, SharedLifetime lifetime, Throwable failure) {
            try {
                new Active(entries, lifetime).close();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            if (failure instanceof RuntimeException runtime) {
                return runtime;
            }
            throw (Error) failure;
        }

        List<SourceLayer> layers() {
            return entries.stream().map(Entry::layer).toList();
        }

        List<FeatureSourceBinding> visibleFeatures() {
            return entries.stream().map(Entry::feature).flatMap(Optional::stream).toList();
        }

        List<RasterSourceBinding> visibleRasters() {
            return entries.stream().map(Entry::raster).flatMap(Optional::stream).toList();
        }

        List<ElevationSourceBinding> visibleElevations() {
            return entries.stream().map(Entry::elevation).flatMap(Optional::stream).toList();
        }

        Active withVisibility(String id, boolean visible) {
            boolean found = false;
            List<Entry> copy = new ArrayList<>(entries.size());
            for (Entry entry : entries) {
                if (entry.id().equals(id)) {
                    found = true;
                    copy.add(entry.withVisible(visible));
                } else {
                    copy.add(entry);
                }
            }
            if (!found) {
                throw new IllegalArgumentException("unknown source layer");
            }
            return new Active(copy, lifetime);
        }

        Active withWrap(boolean enabled) {
            return new Active(
                    entries.stream().map(entry -> entry.withWrap(enabled)).toList(), lifetime);
        }

        Active withWrapForReplacement(boolean enabled) {
            Active replacement = withWrap(enabled);
            closeBindingsOnly(null);
            return replacement;
        }

        Active moved(String id, int delta) {
            List<Entry> copy = new ArrayList<>(entries);
            int index = -1;
            for (int candidate = 0; candidate < copy.size(); candidate++) {
                if (copy.get(candidate).id().equals(id)) {
                    index = candidate;
                    break;
                }
            }
            if (index < 0) {
                throw new IllegalArgumentException("unknown source layer");
            }
            int target = Math.max(0, Math.min(copy.size() - 1, index + delta));
            if (target != index) {
                copy.add(target, copy.remove(index));
            }
            return new Active(copy, lifetime);
        }

        @Override
        public void close() {
            if (retired) {
                return;
            }
            retired = true;
            Throwable primary = null;
            for (Entry entry : entries) {
                primary = entry.closeIfUnattached(primary);
            }
            try {
                lifetime.retire();
            } catch (RuntimeException | Error failure) {
                primary = suppress(primary, failure);
            }
            throwIfPresent(primary);
        }

        void closeBindingsOnly(Throwable primary) {
            Throwable failure = primary;
            for (Entry entry : entries) {
                failure = entry.closeIfUnattached(failure);
            }
            if (failure != null && failure != primary) {
                throwIfPresent(failure);
            }
        }
    }

    private static final class Entry {
        private final String id;
        private final String name;
        private final Kind kind;
        private final Supplier<Object> factory;
        private final Object binding;
        private final boolean wrapped;

        private Entry(
                String id,
                String name,
                Kind kind,
                Supplier<Object> factory,
                Object binding,
                boolean wrapped) {
            this.id = id;
            this.name = name;
            this.kind = kind;
            this.factory = factory;
            this.binding = binding;
            this.wrapped = wrapped;
        }

        static Entry feature(
                String id, String name, Supplier<FeatureSourceBinding> bindingFactory) {
            Supplier<Object> factory = bindingFactory::get;
            return new Entry(id, name, Kind.FEATURE, factory, factory.get(), false);
        }

        static Entry raster(String id, String name, Supplier<RasterSourceBinding> bindingFactory) {
            Supplier<Object> factory = bindingFactory::get;
            return new Entry(id, name, Kind.RASTER, factory, factory.get(), false);
        }

        static Entry elevation(
                String id, String name, Supplier<ElevationSourceBinding> bindingFactory) {
            Supplier<Object> factory = bindingFactory::get;
            return new Entry(id, name, Kind.ELEVATION, factory, factory.get(), false);
        }

        String id() {
            return id;
        }

        SourceLayer layer() {
            return new SourceLayer(id, name, kind, binding != null);
        }

        Optional<FeatureSourceBinding> feature() {
            return kind == Kind.FEATURE
                    ? Optional.ofNullable((FeatureSourceBinding) binding)
                    : Optional.empty();
        }

        Optional<RasterSourceBinding> raster() {
            return kind == Kind.RASTER
                    ? Optional.ofNullable((RasterSourceBinding) binding)
                    : Optional.empty();
        }

        Optional<ElevationSourceBinding> elevation() {
            return kind == Kind.ELEVATION
                    ? Optional.ofNullable((ElevationSourceBinding) binding)
                    : Optional.empty();
        }

        Entry withVisible(boolean visible) {
            if (visible == (binding != null)) {
                return this;
            }
            return new Entry(
                    id,
                    name,
                    kind,
                    factory,
                    visible ? configured(factory.get(), wrapped) : null,
                    wrapped);
        }

        Entry withWrap(boolean enabled) {
            if (wrapped == enabled) {
                return this;
            }
            return new Entry(
                    id,
                    name,
                    kind,
                    factory,
                    binding == null ? null : configured(factory.get(), enabled),
                    enabled);
        }

        private Object configured(Object candidate, boolean enabled) {
            BrowserHorizontalWrapMode mode =
                    enabled ? BrowserHorizontalWrapMode.REPEAT_X : BrowserHorizontalWrapMode.NONE;
            switch (kind) {
                case FEATURE -> ((FeatureSourceBinding) candidate).setHorizontalWrapMode(mode);
                case RASTER -> ((RasterSourceBinding) candidate).setHorizontalWrapMode(mode);
                case ELEVATION -> ((ElevationSourceBinding) candidate).setHorizontalWrapMode(mode);
            }
            return candidate;
        }

        Throwable closeIfUnattached(Throwable primary) {
            if (binding == null) {
                return primary;
            }
            try {
                switch (kind) {
                    case FEATURE -> ((FeatureSourceBinding) binding).close();
                    case RASTER -> ((RasterSourceBinding) binding).close();
                    case ELEVATION -> ((ElevationSourceBinding) binding).close();
                }
            } catch (IllegalStateException attached) {
                return primary;
            } catch (RuntimeException | Error failure) {
                return suppress(primary, failure);
            }
            return primary;
        }
    }

    private static final class SharedLifetime {
        private final AutoCloseable owner;
        private int leases;
        private boolean retired;
        private boolean closed;

        SharedLifetime(AutoCloseable owner) {
            this.owner = Objects.requireNonNull(owner, "owner");
        }

        static SharedLifetime empty() {
            return new SharedLifetime(() -> {});
        }

        synchronized void acquire() {
            if (retired) {
                throw new IllegalStateException("source owner is retired");
            }
            leases = Math.incrementExact(leases);
        }

        synchronized void release() {
            if (leases > 0) {
                leases--;
                closeIfReady();
            }
        }

        synchronized void retire() {
            retired = true;
            closeIfReady();
        }

        private void closeIfReady() {
            if (!retired || leases != 0 || closed) {
                return;
            }
            closed = true;
            try {
                owner.close();
            } catch (RuntimeException | Error failure) {
                throw failure;
            } catch (Exception failure) {
                throw new IllegalStateException("source owner cleanup failed", failure);
            }
        }
    }

    private static final class SerializedFeature {
        private final FeatureSource delegate;
        private final SharedLifetime lifetime;
        private final Semaphore gate = new Semaphore(1, true);

        SerializedFeature(FeatureSource delegate, SharedLifetime lifetime) {
            this.delegate = delegate;
            this.lifetime = lifetime;
        }

        FeatureSource lease() {
            lifetime.acquire();
            return new FeatureSource() {
                private boolean closed;

                @Override
                public FeatureSourceMetadata metadata() {
                    return delegate.metadata();
                }

                @Override
                public io.github.mundanej.map.api.FeatureSourceLimits limits() {
                    return delegate.limits();
                }

                @Override
                public DiagnosticReport openingDiagnostics() {
                    return delegate.openingDiagnostics();
                }

                @Override
                public FeatureCursor openCursor(
                        FeatureQuery query, CancellationToken cancellation) {
                    requireOpen();
                    gate.acquireUninterruptibly();
                    boolean handedOff = false;
                    try {
                        LockedCursor result =
                                new LockedCursor(delegate.openCursor(query, cancellation), gate);
                        handedOff = true;
                        return result;
                    } finally {
                        if (!handedOff) {
                            gate.release();
                        }
                    }
                }

                @Override
                public synchronized boolean isClosed() {
                    return closed;
                }

                @Override
                public synchronized void close() {
                    if (!closed) {
                        closed = true;
                        lifetime.release();
                    }
                }

                private synchronized void requireOpen() {
                    if (closed) {
                        throw new IllegalStateException("source lease is closed");
                    }
                }
            };
        }
    }

    private static final class LockedCursor implements FeatureCursor {
        private final FeatureCursor delegate;
        private final Semaphore gate;
        private volatile boolean closed;

        LockedCursor(FeatureCursor delegate, Semaphore gate) {
            this.delegate = delegate;
            this.gate = gate;
        }

        @Override
        public boolean advance() {
            return delegate.advance();
        }

        @Override
        public io.github.mundanej.map.api.FeatureRecord current() {
            return delegate.current();
        }

        @Override
        public DiagnosticReport diagnostics() {
            return delegate.diagnostics();
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            if (!closed) {
                closed = true;
                try {
                    delegate.close();
                } finally {
                    gate.release();
                }
            }
        }
    }

    private static final class SerializedRaster {
        private final RasterSource delegate;
        private final SharedLifetime lifetime;
        private final ReentrantLock lock = new ReentrantLock();

        SerializedRaster(RasterSource delegate, SharedLifetime lifetime) {
            this.delegate = delegate;
            this.lifetime = lifetime;
        }

        RasterSource lease() {
            lifetime.acquire();
            return new RasterSource() {
                private boolean closed;

                @Override
                public RasterSourceMetadata metadata() {
                    return delegate.metadata();
                }

                @Override
                public RasterSourceLimits limits() {
                    return delegate.limits();
                }

                @Override
                public DiagnosticReport openingDiagnostics() {
                    return delegate.openingDiagnostics();
                }

                @Override
                public RasterRead read(RasterRequest request, CancellationToken cancellation) {
                    requireOpen();
                    lock.lock();
                    try {
                        return delegate.read(request, cancellation);
                    } finally {
                        lock.unlock();
                    }
                }

                @Override
                public synchronized boolean isClosed() {
                    return closed;
                }

                @Override
                public synchronized void close() {
                    if (!closed) {
                        closed = true;
                        lifetime.release();
                    }
                }

                private synchronized void requireOpen() {
                    if (closed) {
                        throw new IllegalStateException("source lease is closed");
                    }
                }
            };
        }
    }

    private static final class SerializedElevation {
        private final ElevationSource delegate;
        private final SharedLifetime lifetime;
        private final ReentrantLock lock = new ReentrantLock();

        SerializedElevation(ElevationSource delegate, SharedLifetime lifetime) {
            this.delegate = delegate;
            this.lifetime = lifetime;
        }

        ElevationSource lease() {
            lifetime.acquire();
            return new ElevationSource() {
                private boolean closed;

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
                    requireOpen();
                    lock.lock();
                    try {
                        return delegate.sample(column, row);
                    } finally {
                        lock.unlock();
                    }
                }

                @Override
                public synchronized boolean isClosed() {
                    return closed;
                }

                @Override
                public synchronized void close() {
                    if (!closed) {
                        closed = true;
                        lifetime.release();
                    }
                }

                private synchronized void requireOpen() {
                    if (closed) {
                        throw new IllegalStateException("source lease is closed");
                    }
                }
            };
        }
    }
}
