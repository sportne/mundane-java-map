package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class MapViewCrsTest {
    @Test
    void explicitIdentityAndProjectionConvenienceExposeExactEndpoints() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView identity =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_4326);
                    identity.setSize(360, 180);
                    identity.setViewport(new MapViewport(360, 180, 0.0, 0.0, 1.0));
                    assertEquals(CrsDefinitions.EPSG_4326, identity.mapCrs());
                    assertEquals(CrsDefinitions.EPSG_4326, identity.displayCrs());
                    assertEquals(
                            Optional.of(new Coordinate(-180.0, 90.0)),
                            identity.screenToMap(0.0, 0.0));
                    assertTrue(identity.screenToMap(-1.0, 0.0).isEmpty());

                    MapView projected = new MapView(new WebMercatorProjection());
                    assertEquals(CrsDefinitions.EPSG_4326, projected.mapCrs());
                    assertEquals(CrsDefinitions.EPSG_3857, projected.displayCrs());
                    assertTrue(projected.mapToScreen(new Coordinate(0.0, 0.0)).isPresent());
                    assertTrue(projected.mapToScreen(new Coordinate(0.0, 90.0)).isEmpty());
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> projected.screenToMap(Double.NaN, 0.0));
                    MapView faulty = new MapView(new FailingProjection());
                    assertThrows(
                            IllegalArgumentException.class,
                            () -> faulty.mapToScreen(new Coordinate(0.0, 0.0)));
                });
    }

    @Test
    void outsideDomainPointerEventsRemainObservableAndNavigationStillRuns() throws Exception {
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView geographic =
                            new MapView(
                                    CrsRegistry.level1(),
                                    CrsDefinitions.EPSG_4326,
                                    CrsDefinitions.EPSG_4326);
                    assertEveryOutsideEdgeIsObservable(
                            geographic, new MapViewport(360, 180, 0.0, 0.0, 1.0));

                    double projectedUnits = WebMercatorProjection.WORLD_LIMIT / 200.0;
                    MapView projected = TestMapViews.identity();
                    assertEveryOutsideEdgeIsObservable(
                            projected, new MapViewport(400, 400, 0.0, 0.0, projectedUnits));

                    MapView view = new MapView(new WebMercatorProjection());
                    assertEveryOutsideEdgeIsObservable(
                            view, new MapViewport(400, 400, 0.0, 0.0, projectedUnits));

                    double before = view.viewport().worldUnitsPerPixel();
                    view.dispatchEvent(
                            new MouseWheelEvent(
                                    view,
                                    MouseEvent.MOUSE_WHEEL,
                                    System.currentTimeMillis(),
                                    0,
                                    0,
                                    0,
                                    0,
                                    false,
                                    MouseWheelEvent.WHEEL_UNIT_SCROLL,
                                    1,
                                    -1));
                    assertTrue(view.viewport().worldUnitsPerPixel() < before);

                    double centerBefore = view.viewport().centerX();
                    dispatch(view, MouseEvent.MOUSE_PRESSED, 0, 200, MouseEvent.BUTTON1);
                    dispatch(view, MouseEvent.MOUSE_DRAGGED, 10, 200, MouseEvent.NOBUTTON);
                    dispatch(view, MouseEvent.MOUSE_RELEASED, 10, 200, MouseEvent.BUTTON1);
                    assertTrue(view.viewport().centerX() < centerBefore);
                });
    }

    private static void assertEveryOutsideEdgeIsObservable(MapView view, MapViewport viewport) {
        view.setSize(viewport.width(), viewport.height());
        view.setViewport(viewport);
        List<MapPointerEvent> events = new ArrayList<>();
        view.addMapPointerListener(events::add);
        int[][] samples = {
            {-1, viewport.height() / 2},
            {viewport.width() + 1, viewport.height() / 2},
            {viewport.width() / 2, -1},
            {viewport.width() / 2, viewport.height() + 1}
        };
        for (int[] sample : samples) {
            dispatch(view, MouseEvent.MOUSE_MOVED, sample[0], sample[1], MouseEvent.NOBUTTON);
            dispatch(view, MouseEvent.MOUSE_CLICKED, sample[0], sample[1], MouseEvent.BUTTON1);
        }
        assertEquals(samples.length * 2, events.size());
        assertTrue(events.stream().allMatch(event -> event.mapCoordinate().isEmpty()));
        assertTrue(events.stream().allMatch(event -> Double.isFinite(event.screenX())));
        assertTrue(events.stream().allMatch(event -> Double.isFinite(event.screenY())));
    }

    private static void dispatch(MapView view, int type, int x, int y, int button) {
        int modifiers = type == MouseEvent.MOUSE_DRAGGED ? InputEvent.BUTTON1_DOWN_MASK : 0;
        view.dispatchEvent(
                new MouseEvent(
                        view, type, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    private static final class FailingProjection implements Projection {
        private final WebMercatorProjection delegate = new WebMercatorProjection();

        @Override
        public CrsDefinition sourceCrs() {
            return delegate.sourceCrs();
        }

        @Override
        public CrsDefinition targetCrs() {
            return delegate.targetCrs();
        }

        @Override
        public Envelope sourceDomain() {
            return delegate.sourceDomain();
        }

        @Override
        public Envelope targetDomain() {
            return delegate.targetDomain();
        }

        @Override
        public Coordinate project(Coordinate source) {
            throw new IllegalArgumentException("projection bug");
        }

        @Override
        public Coordinate unproject(Coordinate target) {
            return delegate.unproject(target);
        }

        @Override
        public Envelope projectEnvelope(Envelope source) {
            return delegate.projectEnvelope(source);
        }

        @Override
        public Envelope unprojectEnvelope(Envelope target) {
            return delegate.unprojectEnvelope(target);
        }
    }
}
