package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.MapPointerListener;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.ProjectionEnvelopes;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * A lightweight Swing map component for projected vector features.
 *
 * <p>Mutation and listener callbacks follow the normal Swing event-dispatch-thread contract. Swing
 * serialization is inherited for framework compatibility and is not a persistence format.
 */
@SuppressWarnings("serial")
public final class MapView extends JComponent {
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_WIDTH = 800;
    private static final int DEFAULT_HEIGHT = 600;
    private static final double ZOOM_STEP = 1.2;

    private final Projection projection;
    private final List<MapPointerListener> pointerListeners = new ArrayList<>();
    private List<Layer> layers = List.of();
    private MapViewport viewport = MapViewport.initial(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Point dragAnchor;

    /** Creates an empty view using the supplied source-to-world projection. */
    public MapView(Projection projection) {
        this.projection = Objects.requireNonNull(projection, "projection");
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        installInteraction();
    }

    /** Returns the projection used by this view. */
    public Projection projection() {
        return projection;
    }

    /** Replaces the ordered layer snapshot and repaints the component. */
    public void setLayers(List<Layer> layers) {
        this.layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        repaint();
    }

    /** Returns the ordered layer snapshot. */
    public List<Layer> layers() {
        return layers;
    }

    /** Returns the current viewport, resized to the component's current dimensions. */
    public MapViewport viewport() {
        synchronizeViewportSize();
        return viewport;
    }

    /** Replaces the viewport state and repaints the component. */
    public void setViewport(MapViewport viewport) {
        this.viewport = Objects.requireNonNull(viewport, "viewport");
        repaint();
    }

    /** Fits all non-empty layers using the requested screen-pixel padding. */
    public void fitToData(double paddingPixels) {
        Envelope projected = null;
        for (Layer layer : layers) {
            if (layer.envelope().isPresent()) {
                Envelope next =
                        ProjectionEnvelopes.project(projection, layer.envelope().orElseThrow());
                projected = projected == null ? next : projected.union(next);
            }
        }
        if (projected != null) {
            Dimension size = effectiveSize();
            viewport = MapViewport.fit(size.width, size.height, projected, paddingPixels);
            repaint();
        }
    }

    /** Converts a screen location into source map coordinates. */
    public Coordinate screenToMap(double screenX, double screenY) {
        return projection.unproject(viewport().screenToWorld(screenX, screenY));
    }

    /** Adds a pointer listener. Duplicate instances receive duplicate callbacks. */
    public void addMapPointerListener(MapPointerListener listener) {
        pointerListeners.add(Objects.requireNonNull(listener, "listener"));
    }

    /** Removes one matching pointer-listener instance. */
    public void removeMapPointerListener(MapPointerListener listener) {
        for (int index = 0; index < pointerListeners.size(); index++) {
            if (pointerListeners.get(index) == listener) {
                pointerListeners.remove(index);
                return;
            }
        }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        synchronizeViewportSize();
        Graphics2D graphics2D = (Graphics2D) graphics.create();
        try {
            if (isOpaque()) {
                graphics2D.setColor(getBackground());
                graphics2D.fillRect(0, 0, getWidth(), getHeight());
            }
            graphics2D.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            for (Layer layer : layers) {
                for (Feature feature : layer.features()) {
                    renderFeature(graphics2D, feature);
                }
            }
        } finally {
            graphics2D.dispose();
        }
    }

    private void renderFeature(Graphics2D graphics, Feature feature) {
        Geometry geometry = feature.geometry();
        FeatureStyle style = feature.style();
        graphics.setStroke(
                new BasicStroke(
                        (float) style.strokeWidth(),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));

        if (geometry instanceof PointGeometry point) {
            renderPoint(graphics, point, feature);
        } else if (geometry instanceof LineStringGeometry line) {
            if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
                Path2D path = path(line.coordinates(), false);
                graphics.setColor(color(style.stroke()));
                graphics.draw(path);
            }
        } else if (geometry instanceof PolygonGeometry polygon) {
            renderPolygon(graphics, polygon, style);
        }
    }

    private void renderPoint(Graphics2D graphics, PointGeometry point, Feature feature) {
        Coordinate screen = toScreen(point.coordinate());
        double diameter = feature.style().pointDiameter();
        double radius = diameter / 2.0;
        Ellipse2D marker =
                new Ellipse2D.Double(screen.x() - radius, screen.y() - radius, diameter, diameter);
        if (feature.style().fill().alpha() > 0) {
            graphics.setColor(color(feature.style().fill()));
            graphics.fill(marker);
        }
        if (feature.style().stroke().alpha() > 0 && feature.style().strokeWidth() > 0.0) {
            graphics.setColor(color(feature.style().stroke()));
            graphics.draw(marker);
        }
        if (!feature.name().isBlank()) {
            graphics.setColor(new Color(32, 32, 32));
            graphics.drawString(
                    feature.name(),
                    (float) (screen.x() + radius + 4.0),
                    (float) (screen.y() - radius - 2.0));
        }
    }

    private void renderPolygon(Graphics2D graphics, PolygonGeometry polygon, FeatureStyle style) {
        Path2D path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        append(path, polygon.exterior(), true);
        for (CoordinateSequence hole : polygon.holes()) {
            append(path, hole, true);
        }
        if (style.fill().alpha() > 0) {
            graphics.setColor(color(style.fill()));
            graphics.fill(path);
        }
        if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
            graphics.setColor(color(style.stroke()));
            graphics.draw(path);
        }
    }

    private Path2D path(CoordinateSequence coordinates, boolean close) {
        Path2D path = new Path2D.Double();
        append(path, coordinates, close);
        return path;
    }

    private void append(Path2D path, CoordinateSequence coordinates, boolean close) {
        Coordinate first = toScreen(coordinates.coordinate(0));
        path.moveTo(first.x(), first.y());
        for (int index = 1; index < coordinates.size(); index++) {
            Coordinate screen = toScreen(coordinates.coordinate(index));
            path.lineTo(screen.x(), screen.y());
        }
        if (close) {
            path.closePath();
        }
    }

    private Coordinate toScreen(Coordinate source) {
        return viewport.worldToScreen(projection.project(source));
    }

    private static Color color(Rgba color) {
        return new Color(color.red(), color.green(), color.blue(), color.alpha());
    }

    private void installInteraction() {
        addMouseListener(
                new MouseAdapter() {
                    @Override
                    public void mousePressed(MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) {
                            dragAnchor = event.getPoint();
                        }
                    }

                    @Override
                    public void mouseReleased(MouseEvent event) {
                        if (SwingUtilities.isLeftMouseButton(event)) {
                            dragAnchor = null;
                        }
                    }

                    @Override
                    public void mouseClicked(MouseEvent event) {
                        firePointer(MapPointerEvent.Type.CLICKED, event.getX(), event.getY());
                    }
                });
        addMouseMotionListener(
                new MouseMotionAdapter() {
                    @Override
                    public void mouseDragged(MouseEvent event) {
                        if (dragAnchor != null) {
                            double deltaX = event.getX() - dragAnchor.x;
                            double deltaY = event.getY() - dragAnchor.y;
                            viewport = viewport().panByPixels(deltaX, deltaY);
                            dragAnchor = event.getPoint();
                            repaint();
                        }
                    }

                    @Override
                    public void mouseMoved(MouseEvent event) {
                        firePointer(MapPointerEvent.Type.MOVED, event.getX(), event.getY());
                    }
                });
        addMouseWheelListener(this::zoom);
    }

    private void zoom(MouseWheelEvent event) {
        double factor = Math.pow(ZOOM_STEP, -event.getPreciseWheelRotation());
        viewport = viewport().zoomAt(event.getX(), event.getY(), factor);
        repaint();
    }

    private void firePointer(MapPointerEvent.Type type, double screenX, double screenY) {
        MapPointerEvent event =
                new MapPointerEvent(type, screenX, screenY, screenToMap(screenX, screenY));
        List<MapPointerListener> snapshot = List.copyOf(pointerListeners);
        for (MapPointerListener listener : snapshot) {
            listener.onMapPointerEvent(event);
        }
    }

    private void synchronizeViewportSize() {
        Dimension size = effectiveSize();
        if (viewport.width() != size.width || viewport.height() != size.height) {
            viewport = viewport.resized(size.width, size.height);
        }
    }

    private Dimension effectiveSize() {
        int width = getWidth() > 0 ? getWidth() : getPreferredSize().width;
        int height = getHeight() > 0 ? getHeight() : getPreferredSize().height;
        return new Dimension(Math.max(1, width), Math.max(1, height));
    }
}
