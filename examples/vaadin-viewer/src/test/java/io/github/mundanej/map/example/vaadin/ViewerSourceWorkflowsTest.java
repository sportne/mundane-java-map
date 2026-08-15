package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.FeatureSourceMetadata;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.io.shapefile.ShapefileOpenOptions;
import io.github.mundanej.map.io.shapefile.Shapefiles;
import io.github.mundanej.map.vaadin.MundaneMap;
import io.github.mundanej.map.workspace.WorkspaceSession;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ViewerSourceWorkflowsTest {
    private static final Path FIXTURES =
            Path.of(System.getProperty("mundane.viewer.fixtures")).toAbsolutePath();

    @Test
    void checkedAndCallerSelectedSourcesOpenThroughExistingBoundaries() {
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows = direct(map, ViewerSourceWorkflows.Openers.production());

        assertTrue(workflows.openShapefile(shapefile()).toCompletableFuture().join().opened());
        assertEquals(List.of("opened-shapefile"), ids(workflows));
        workflows.setVisible("opened-shapefile", false);
        assertFalse(workflows.layers().getFirst().visible());
        workflows.setVisible("opened-shapefile", true);
        assertTrue(workflows.layers().getFirst().visible());
        map.setHorizontalWrap(HorizontalWrap.webMercator());
        workflows.setWrapEnabled(true);
        workflows.clear();
        assertTrue(workflows.openShapefile(shapefile()).toCompletableFuture().join().opened());
        workflows.setWrapEnabled(false);
        map.clearHorizontalWrap();

        assertTrue(workflows.openRaster(raster()).toCompletableFuture().join().opened());
        assertEquals(List.of("opened-raster"), ids(workflows));
        assertEquals(108_000, map.viewport().centerX());
        assertEquals(192_000, map.viewport().centerY());

        map.setViewport(new io.github.mundanej.map.core.MapViewport(800, 600, 0, 0, 1));
        assertTrue(workflows.fit(48));
        assertEquals(108_000, map.viewport().centerX());
        assertEquals(192_000, map.viewport().centerY());

        assertTrue(workflows.openElevation(elevation()).toCompletableFuture().join().opened());
        assertEquals(List.of("opened-elevation"), ids(workflows));
        assertEquals(1_750, map.viewport().centerX());
        assertEquals(1_250, map.viewport().centerY());

        assertTrue(workflows.openWorkspace(workspace()).toCompletableFuture().join().opened());
        assertEquals(List.of("workspace-areas", "workspace-outline"), ids(workflows));
        workflows.move("workspace-outline", -1);
        assertEquals(List.of("workspace-outline", "workspace-areas"), ids(workflows));

        workflows.close();
        map.close();
    }

    @Test
    void replacementAndCloseReleaseEachOwnedSourceExactlyOnce() {
        AtomicReference<CountingFeatureSource> first = new AtomicReference<>();
        AtomicReference<CountingFeatureSource> second = new AtomicReference<>();
        AtomicInteger opens = new AtomicInteger();
        ViewerSourceWorkflows.Openers production = ViewerSourceWorkflows.Openers.production();
        ViewerSourceWorkflows.Openers counting =
                new ViewerSourceWorkflows.Openers() {
                    @Override
                    public FeatureSource shapefile(Path path, CancellationToken cancellation) {
                        CountingFeatureSource source =
                                new CountingFeatureSource(
                                        Shapefiles.open(
                                                new SourceIdentity("counted", "Counted source"),
                                                path,
                                                ShapefileOpenOptions.defaults(),
                                                cancellation));
                        if (opens.getAndIncrement() == 0) {
                            first.set(source);
                        } else {
                            second.set(source);
                        }
                        return source;
                    }

                    @Override
                    public RasterSource raster(Path path, CancellationToken cancellation) {
                        return production.raster(path, cancellation);
                    }

                    @Override
                    public ElevationSource elevation(Path path, CancellationToken cancellation) {
                        return production.elevation(path, cancellation);
                    }

                    @Override
                    public WorkspaceSession workspace(Path path, CancellationToken cancellation) {
                        return production.workspace(path, cancellation);
                    }
                };
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows = direct(map, counting);

        workflows.openShapefile(shapefile()).toCompletableFuture().join();
        workflows.openShapefile(shapefile()).toCompletableFuture().join();
        awaitCloseCount(first.get(), 1);
        assertEquals(0, second.get().closeCount());

        workflows.close();
        workflows.close();
        map.close();
        awaitCloseCount(first.get(), 1);
        awaitCloseCount(second.get(), 1);
    }

    @Test
    void supersedingOpenCancelsPendingWorkAndClosesItsCandidate() {
        Queue<Runnable> operations = new ArrayDeque<>();
        Executor queued = operations::add;
        AtomicReference<CountingFeatureSource> stale = new AtomicReference<>();
        ViewerSourceWorkflows.Openers production = ViewerSourceWorkflows.Openers.production();
        ViewerSourceWorkflows.Openers ignoresCancellation =
                new ViewerSourceWorkflows.Openers() {
                    @Override
                    public FeatureSource shapefile(Path path, CancellationToken cancellation) {
                        CountingFeatureSource source =
                                new CountingFeatureSource(
                                        Shapefiles.open(
                                                new SourceIdentity("stale", "Stale source"),
                                                path,
                                                ShapefileOpenOptions.defaults(),
                                                CancellationToken.none()));
                        stale.set(source);
                        return source;
                    }

                    @Override
                    public RasterSource raster(Path path, CancellationToken cancellation) {
                        return production.raster(path, cancellation);
                    }

                    @Override
                    public ElevationSource elevation(Path path, CancellationToken cancellation) {
                        return production.elevation(path, cancellation);
                    }

                    @Override
                    public WorkspaceSession workspace(Path path, CancellationToken cancellation) {
                        return production.workspace(path, cancellation);
                    }
                };
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows =
                new ViewerSourceWorkflows(
                        map, queued, Runnable::run, ignoresCancellation, () -> {});

        CompletableFuture<ViewerSourceWorkflows.OpenResult> first =
                workflows.openShapefile(shapefile()).toCompletableFuture();
        CompletableFuture<ViewerSourceWorkflows.OpenResult> second =
                workflows.openRaster(raster()).toCompletableFuture();
        operations.remove().run();
        operations.remove().run();

        assertEquals("SOURCE_OPEN_CANCELLED", first.join().diagnosticCode());
        awaitCloseCount(stale.get(), 1);
        assertTrue(second.join().opened());
        workflows.close();
        map.close();
    }

    @Test
    void clearRetiresWorkspaceOnlyAfterItsOwnedBindingsRelease() {
        AtomicReference<WorkspaceSession> opened = new AtomicReference<>();
        ViewerSourceWorkflows.Openers production = ViewerSourceWorkflows.Openers.production();
        ViewerSourceWorkflows.Openers capturing =
                new ViewerSourceWorkflows.Openers() {
                    @Override
                    public FeatureSource shapefile(Path path, CancellationToken cancellation) {
                        return production.shapefile(path, cancellation);
                    }

                    @Override
                    public RasterSource raster(Path path, CancellationToken cancellation) {
                        return production.raster(path, cancellation);
                    }

                    @Override
                    public ElevationSource elevation(Path path, CancellationToken cancellation) {
                        return production.elevation(path, cancellation);
                    }

                    @Override
                    public WorkspaceSession workspace(Path path, CancellationToken cancellation) {
                        WorkspaceSession session = production.workspace(path, cancellation);
                        opened.set(session);
                        return session;
                    }
                };
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows = direct(map, capturing);
        workflows.openWorkspace(workspace()).toCompletableFuture().join();

        workflows.clear();
        workflows.close();
        map.close();

        awaitClosed(opened.get());
    }

    @Test
    void rejectedSubmissionAndDispatchAlwaysSettleTheReturnedStage() {
        MundaneMap submissionMap = new MundaneMap();
        ViewerSourceWorkflows rejectedSubmission =
                new ViewerSourceWorkflows(
                        submissionMap,
                        ignored -> {
                            throw new RejectedExecutionException("closed");
                        },
                        Runnable::run,
                        ViewerSourceWorkflows.Openers.production(),
                        () -> {});
        ViewerSourceWorkflows.OpenResult submission =
                rejectedSubmission.openShapefile(shapefile()).toCompletableFuture().join();
        assertFalse(submission.opened());
        assertFalse(rejectedSubmission.busy());
        rejectedSubmission.close();
        submissionMap.close();

        MundaneMap dispatchMap = new MundaneMap();
        ViewerSourceWorkflows rejectedDispatch =
                new ViewerSourceWorkflows(
                        dispatchMap,
                        Runnable::run,
                        ignored -> {
                            throw new RejectedExecutionException("detached");
                        },
                        ViewerSourceWorkflows.Openers.production(),
                        () -> {});
        ViewerSourceWorkflows.OpenResult dispatch =
                rejectedDispatch.openShapefile(shapefile()).toCompletableFuture().join();
        assertFalse(dispatch.opened());
        assertFalse(rejectedDispatch.busy());
        rejectedDispatch.close();
        dispatchMap.close();
    }

    @Test
    void ownedExecutorShutdownDrainsAndSettlesEveryQueuedOpen() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ViewerSourceWorkflows.Openers production = ViewerSourceWorkflows.Openers.production();
        ViewerSourceWorkflows.Openers blocking =
                new ViewerSourceWorkflows.Openers() {
                    @Override
                    public FeatureSource shapefile(Path path, CancellationToken cancellation) {
                        entered.countDown();
                        try {
                            release.await();
                        } catch (InterruptedException failure) {
                            Thread.currentThread().interrupt();
                        }
                        return production.shapefile(path, cancellation);
                    }

                    @Override
                    public RasterSource raster(Path path, CancellationToken cancellation) {
                        return production.raster(path, cancellation);
                    }

                    @Override
                    public ElevationSource elevation(Path path, CancellationToken cancellation) {
                        return production.elevation(path, cancellation);
                    }

                    @Override
                    public WorkspaceSession workspace(Path path, CancellationToken cancellation) {
                        return production.workspace(path, cancellation);
                    }
                };
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows =
                new ViewerSourceWorkflows(
                        map,
                        Executors.newSingleThreadExecutor(),
                        Runnable::run,
                        blocking,
                        () -> {},
                        true);
        CompletableFuture<ViewerSourceWorkflows.OpenResult> first =
                workflows.openShapefile(shapefile()).toCompletableFuture();
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        CompletableFuture<ViewerSourceWorkflows.OpenResult> second =
                workflows.openRaster(raster()).toCompletableFuture();

        workflows.close();
        release.countDown();

        assertEquals("SOURCE_OPEN_CANCELLED", first.get(5, TimeUnit.SECONDS).diagnosticCode());
        assertEquals("SOURCE_OPEN_CANCELLED", second.get(5, TimeUnit.SECONDS).diagnosticCode());
        map.close();
    }

    @Test
    void workspaceInputLimitFailsWithStableBoundedDiagnostic(@TempDir Path temporary)
            throws Exception {
        Path oversized = temporary.resolve("oversized.mmap.xml");
        java.nio.file.Files.write(oversized, new byte[1_048_577]);
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows = direct(map, ViewerSourceWorkflows.Openers.production());

        ViewerSourceWorkflows.OpenResult result =
                workflows.openWorkspace(oversized).toCompletableFuture().join();

        assertFalse(result.opened());
        assertTrue(result.diagnosticCode().contains("LIMIT"));
        assertFalse(result.diagnosticCode().contains(oversized.toString()));
        workflows.close();
        map.close();
    }

    @Test
    void blankWorkspaceDisplayNameUsesStableLayerIdentity(@TempDir Path temporary)
            throws Exception {
        Path copied = temporary.resolve("workspace");
        java.nio.file.Files.createDirectories(copied.resolve("data"));
        try (var files = java.nio.file.Files.list(FIXTURES.resolve("workspace/data"))) {
            for (Path source : files.toList()) {
                java.nio.file.Files.copy(
                        source, copied.resolve("data").resolve(source.getFileName()));
            }
        }
        String xml =
                java.nio.file.Files.readString(workspace())
                        .replace("name=\"Workspace areas\"", "name=\"\"");
        Path blankName = copied.resolve("blank-name.mmap.xml");
        java.nio.file.Files.writeString(blankName, xml);
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows = direct(map, ViewerSourceWorkflows.Openers.production());

        assertTrue(workflows.openWorkspace(blankName).toCompletableFuture().join().opened());
        assertEquals("workspace-areas", workflows.layers().getFirst().name());

        workflows.close();
        map.close();
    }

    @Test
    void hostileLocalFailuresExposeOnlyStableCodes() {
        MundaneMap map = new MundaneMap();
        ViewerSourceWorkflows workflows = direct(map, ViewerSourceWorkflows.Openers.production());
        Path secret = FIXTURES.resolve("private-customer-name.shp");

        ViewerSourceWorkflows.OpenResult result =
                workflows.openShapefile(secret).toCompletableFuture().join();

        assertFalse(result.opened());
        assertFalse(result.diagnosticCode().contains("private-customer-name"));
        assertTrue(result.diagnosticCode().matches("[A-Z][A-Z0-9_]+"));
        workflows.close();
        map.close();
    }

    private static ViewerSourceWorkflows direct(
            MundaneMap map, ViewerSourceWorkflows.Openers openers) {
        return new ViewerSourceWorkflows(map, Runnable::run, Runnable::run, openers, () -> {});
    }

    private static List<String> ids(ViewerSourceWorkflows workflows) {
        return workflows.layers().stream().map(ViewerSourceWorkflows.SourceLayer::id).toList();
    }

    private static Path shapefile() {
        return FIXTURES.resolve("shapefile/generated-polygon-hole-windows1252-3857.shp");
    }

    private static Path raster() {
        return FIXTURES.resolve("geotiff/gdal-gray-tile-deflate-3857.tif");
    }

    private static Path elevation() {
        return FIXTURES.resolve("geotiff/gdal-float32-tile-deflate-3857.tif");
    }

    private static Path workspace() {
        return FIXTURES.resolve("workspace/example.mmap.xml");
    }

    private static void awaitCloseCount(CountingFeatureSource source, int expected) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (source.closeCount() != expected && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        }
        assertEquals(expected, source.closeCount());
    }

    private static void awaitClosed(WorkspaceSession session) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!session.isClosed() && System.nanoTime() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(failure);
            }
        }
        assertTrue(session.isClosed());
    }

    private static final class CountingFeatureSource implements FeatureSource {
        private final FeatureSource delegate;
        private final AtomicInteger closes = new AtomicInteger();

        CountingFeatureSource(FeatureSource delegate) {
            this.delegate = delegate;
        }

        int closeCount() {
            return closes.get();
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
            if (closes.getAndIncrement() == 0) {
                delegate.close();
            }
        }
    }
}
