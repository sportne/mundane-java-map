package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.router.Route;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.FeatureSelection;
import io.github.mundanej.map.api.MapPointerButton;
import io.github.mundanej.map.api.MapToolContext;
import io.github.mundanej.map.api.MapToolEvent;
import io.github.mundanej.map.api.MeasurementPhase;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

final class VaadinViewerApplicationTest {
    @Test
    void applicationContextStartsWithoutNetworkMapData() {
        SpringApplication application = VaadinViewerApplication.application();
        application.setDefaultProperties(
                java.util.Map.of(
                        "server.address", "127.0.0.1",
                        "server.port", "0",
                        "vaadin.productionMode", "true"));
        try (ConfigurableApplicationContext context = application.run()) {
            assertNotNull(context.getBean(VaadinViewerApplication.class));
        }
    }

    @Test
    void rootRouteOwnsResponsiveMapShellAndClosesOnDetachExactlyOnce() {
        assertEquals("", ViewerRoute.class.getAnnotation(Route.class).value());
        ViewerRoute route = new ViewerRoute();
        assertEquals("main", route.getElement().getTag());
        assertTrue(route.getElement().getClassList().contains("viewer-root"));
        assertTrue(route.getElement().getTextRecursively().contains("No basemap"));
        assertFalse(route.session().isClosed());

        route.onDetach(new DetachEvent(route));
        route.close();

        assertTrue(route.session().isClosed());
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
}
