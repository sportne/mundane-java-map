package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.page.Push;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.communication.PushMode;
import com.vaadin.flow.spring.SpringServlet;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MeasurementPhase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ConfigurableApplicationContext;

final class VaadinViewerApplicationTest {
    @Test
    void applicationContextStartsWithoutNetworkMapData() {
        assertEquals(
                PushMode.AUTOMATIC,
                VaadinViewerApplication.class.getAnnotation(Push.class).value());
        SpringApplication application = VaadinViewerApplication.application();
        application.setDefaultProperties(
                java.util.Map.of(
                        "server.address", "127.0.0.1",
                        "server.port", "0",
                        "vaadin.productionMode", "true"));
        try (ConfigurableApplicationContext context = application.run()) {
            assertNotNull(context.getBean(VaadinViewerApplication.class));
            assertEquals(
                    "16MB",
                    context.getEnvironment().getProperty("spring.servlet.multipart.max-file-size"));
            assertEquals(
                    "33MB",
                    context.getEnvironment()
                            .getProperty("spring.servlet.multipart.max-request-size"));
            ServletRegistrationBean<?> vaadin =
                    context.getBeansOfType(ServletRegistrationBean.class).values().stream()
                            .filter(
                                    registration ->
                                            registration.getServlet() instanceof SpringServlet)
                            .findFirst()
                            .orElseThrow();
            assertEquals(16L * 1024 * 1024, vaadin.getMultipartConfig().getMaxFileSize());
            assertEquals(33L * 1024 * 1024, vaadin.getMultipartConfig().getMaxRequestSize());
            assertEquals(0, vaadin.getMultipartConfig().getFileSizeThreshold());
        }
    }

    @Test
    void rootRouteOwnsResponsiveMapShellAndClosesOnDetachExactlyOnce() {
        assertEquals("", ViewerRoute.class.getAnnotation(Route.class).value());
        ViewerRoute route = new ViewerRoute(new ViewerSessionRegistry());
        assertEquals("main", route.getElement().getTag());
        assertTrue(route.getElement().getClassList().contains("viewer-root"));
        assertTrue(route.getElement().getTextRecursively().contains("No basemap"));
        assertTrue(route.getElement().getTextRecursively().contains("Upload and open"));
        assertTrue(route.getElement().getTextRecursively().contains("Download SVG"));
        assertFalse(route.session().isClosed());
        Path uploadRoot = route.session().uploads().root();

        route.onDetach(new DetachEvent(route));
        route.close();

        assertTrue(route.session().isClosed());
        assertFalse(Files.exists(uploadRoot));
    }

    @Test
    void exactAttachedSessionDestructionClosesRouteAndRemovesRegistration() {
        AtomicReference<Runnable> destroy = new AtomicReference<>();
        AtomicInteger removals = new AtomicInteger();
        ViewerRoute route =
                new ViewerRoute(
                        (ignored, listener) -> {
                            destroy.set(listener);
                            return removals::incrementAndGet;
                        });

        route.onAttach(new AttachEvent(route, true));
        destroy.get().run();
        destroy.get().run();

        assertTrue(route.session().isClosed());
        assertEquals(1, removals.get());
    }

    @Test
    void detachReattachOfClosedRouteDoesNotReregisterSessionCleanup() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger removals = new AtomicInteger();
        ViewerRoute route =
                new ViewerRoute(
                        (ignored, listener) -> {
                            registrations.incrementAndGet();
                            return removals::incrementAndGet;
                        });

        route.onAttach(new AttachEvent(route, true));
        route.onDetach(new DetachEvent(route));
        route.onAttach(new AttachEvent(route, false));
        route.onDetach(new DetachEvent(route));

        assertEquals(1, registrations.get());
        assertEquals(1, removals.get());
    }

    @Test
    void applicationRegistryClosesLiveRoutesExactlyOnce() {
        ViewerSessionRegistry registry = new ViewerSessionRegistry();
        ViewerRoute route = new ViewerRoute(registry, (ignored, listener) -> () -> {});

        registry.close();
        registry.close();
        route.close();

        assertTrue(route.session().isClosed());
    }

    @Test
    void closedApplicationRegistryRejectsAndClosesLateSession() {
        ViewerSessionRegistry registry = new ViewerSessionRegistry();
        ViewerSession late = new ViewerSession();
        registry.close();

        assertThrows(IllegalStateException.class, () -> registry.register(late));

        assertTrue(late.isClosed());
    }

    @Test
    void applicationRegistryRemovesClosedSessionsAndAggregatesShutdownFailures() {
        ViewerSessionRegistry registry = new ViewerSessionRegistry();
        ViewerSession removed = new ViewerSession();
        ViewerSession failing = new ViewerSession();
        ViewerSession alsoFailing = new ViewerSession();
        AtomicInteger calls = new AtomicInteger();
        registry.register(removed, calls::incrementAndGet).remove();
        registry.register(
                failing,
                () -> {
                    calls.incrementAndGet();
                    throw new IllegalStateException("first");
                });
        registry.register(
                alsoFailing,
                () -> {
                    calls.incrementAndGet();
                    throw new IllegalArgumentException("second");
                });

        RuntimeException failure = assertThrows(RuntimeException.class, registry::close);

        assertEquals(1, failure.getSuppressed().length);
        assertEquals(2, calls.get());
        removed.close();
        failing.close();
        alsoFailing.close();
    }

    @Test
    void routeFixtureControlsOpenEverySupportedServerBoundary() {
        ViewerRoute route = new ViewerRoute(new ViewerSessionRegistry());

        route.session().setWrapEnabled(true);
        assertTrue(
                route.openFixture(ViewerRoute.SourceKind.SHAPEFILE)
                        .toCompletableFuture()
                        .join()
                        .opened());
        route.session().setWrapEnabled(false);
        for (ViewerRoute.SourceKind kind : ViewerRoute.SourceKind.values()) {
            if (kind == ViewerRoute.SourceKind.SHAPEFILE) {
                continue;
            }
            assertTrue(route.openFixture(kind).toCompletableFuture().join().opened());
            assertFalse(route.session().sourceLayers().isEmpty());
        }

        route.close();
    }

    @Test
    void typedNativePathIsSynchronizedAndInvalidSyntaxUsesStableDiagnostic() {
        ViewerRoute route = new ViewerRoute(new ViewerSessionRegistry());
        route.synchronizeSourcePath(new String(new char[] {0, 'x'}));

        ViewerSourceWorkflows.OpenResult result =
                route.openPath(ViewerRoute.SourceKind.SHAPEFILE).toCompletableFuture().join();

        assertEquals("SOURCE_PATH_INVALID", result.diagnosticCode());
        assertEquals("SOURCE_PATH_INVALID", route.session().diagnosticText());
        route.close();
    }

    @Test
    void detachAndApplicationStopCloseRoutesWithLiveSources() {
        ViewerSessionRegistry detachRegistry = new ViewerSessionRegistry();
        ViewerRoute detached = new ViewerRoute(detachRegistry);
        assertTrue(
                detached.openFixture(ViewerRoute.SourceKind.SHAPEFILE)
                        .toCompletableFuture()
                        .join()
                        .opened());
        detached.onDetach(new DetachEvent(detached));
        assertTrue(detached.session().isClosed());

        ViewerSessionRegistry stopRegistry = new ViewerSessionRegistry();
        ViewerRoute stopped = new ViewerRoute(stopRegistry);
        assertTrue(
                stopped.openFixture(ViewerRoute.SourceKind.WORKSPACE)
                        .toCompletableFuture()
                        .join()
                        .opened());
        stopRegistry.close();
        assertTrue(stopped.session().isClosed());
        stopped.close();
    }

    @Test
    void concurrentDetachAndApplicationStopSerializeExactCleanup() throws Exception {
        ViewerSessionRegistry registry = new ViewerSessionRegistry();
        ViewerRoute route = new ViewerRoute(registry);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread detach =
                new Thread(
                        () -> {
                            await(start);
                            try {
                                route.close();
                            } catch (RuntimeException | Error thrown) {
                                failure.compareAndSet(null, thrown);
                            }
                        });
        Thread stop =
                new Thread(
                        () -> {
                            await(start);
                            try {
                                registry.close();
                            } catch (RuntimeException | Error thrown) {
                                failure.compareAndSet(null, thrown);
                            }
                        });
        detach.start();
        stop.start();
        start.countDown();
        detach.join();
        stop.join();

        assertEquals(null, failure.get());
        assertTrue(route.session().isClosed());
    }

    @Test
    void inMemoryLayersControlsToolsAndDiagnosticsAreWired() {
        ViewerSession session = new ViewerSession();
        assertEquals(
                List.of("study-area", "route"),
                session.layers().stream().map(layer -> layer.id()).toList());
        assertEquals(2, session.editSnapshot().records().size());
        assertEquals("No source diagnostics", session.diagnosticText());
        assertTrue(session.map().fitToContents(32));

        session.setLayerVisible("route", false);
        assertFalse(session.isLayerVisible("route"));
        session.moveLayer("route", -1);
        assertEquals("route", session.layers().getFirst().id());
        session.zoom(0.5);
        session.setWrapEnabled(true);
        assertTrue(session.wrapEnabled());
        session.setWrapEnabled(false);

        session.measure();
        assertEquals(ViewerSession.ToolMode.MEASURE, session.toolMode());
        session.createPoint();
        assertEquals(ViewerSession.ToolMode.CREATE_POINT, session.toolMode());
        session.movePoint();
        assertEquals(ViewerSession.ToolMode.MOVE_POINT, session.toolMode());
        session.navigate();
        assertEquals(ViewerSession.ToolMode.NAVIGATE, session.toolMode());
        assertDoesNotThrow(session::undo);
        assertDoesNotThrow(session::redo);

        session.close();
        assertThrows(IllegalStateException.class, session::fit);
    }

    @Test
    void selectionInspectorTracksCurrentLogicalIdentity() {
        ViewerSession session = new ViewerSession();
        session.map().setSelection(new FeatureSelection("editable-points", "point-a"));
        assertEquals("editable-points / point-a", session.selectionText());
        session.map().clearSelection();
        assertEquals("Nothing selected", session.selectionText());
        session.close();
    }

    @Test
    void consumedMeasurementSnapshotsRefreshStatusAndCoordinates() {
        ViewerSession session = new ViewerSession();
        AtomicInteger refreshes = new AtomicInteger();
        session.addObserver(refreshes::incrementAndGet);
        session.measure();
        session.measurementTool()
                .onMapToolEvent(
                        new MapToolEvent(
                                1,
                                MapToolEvent.Type.CLICK,
                                60,
                                40,
                                Optional.of(new Coordinate(12, 34)),
                                MapPointerButton.PRIMARY,
                                Set.of(),
                                Set.of(),
                                1,
                                0,
                                false,
                                Optional.empty()),
                        context(session));

        assertEquals(MeasurementPhase.MEASURING, session.measurementState().phase());
        assertEquals("x 12.00, y 34.00", session.coordinateText());
        assertTrue(refreshes.get() >= 2);
        session.close();
    }

    private static MapToolContext context(ViewerSession session) {
        return new MapToolContext() {
            @Override
            public io.github.mundanej.map.api.CrsDefinition mapCrs() {
                return session.map().mapCrs();
            }

            @Override
            public io.github.mundanej.map.api.CrsDefinition displayCrs() {
                return session.map().displayCrs();
            }

            @Override
            public Optional<Coordinate> mapToScreen(Coordinate coordinate) {
                return session.map().mapToScreen(coordinate);
            }

            @Override
            public Optional<Coordinate> screenToMap(double screenX, double screenY) {
                return session.map().screenToMap(screenX, screenY);
            }

            @Override
            public void requestRepaint() {}
        };
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("test interrupted", failure);
        }
    }
}
