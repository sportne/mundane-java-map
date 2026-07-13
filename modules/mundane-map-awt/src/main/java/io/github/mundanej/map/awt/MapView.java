package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CompositeSymbol;
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
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.MarkerTransform;
import io.github.mundanej.map.core.ProjectionEnvelopes;
import io.github.mundanej.map.core.SymbolTransforms;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * A lightweight Swing map component for projected vector features.
 *
 * <p>Mutation and listener callbacks follow the normal Swing event-dispatch-thread contract. Swing
 * serialization is inherited for framework compatibility and is not a persistence format.
 */
@SuppressWarnings({"deprecation", "serial"})
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
        Symbol symbol = feature.symbol();
        if (symbol instanceof FeatureStyle style) {
            renderLegacyFeature(graphics, geometry, feature, style);
            return;
        }
        SymbolSnapshot symbolSnapshot = new SymbolSnapshot(symbol.role(), symbol.rendererKey());
        if (geometry instanceof PointGeometry point) {
            if (!(symbol instanceof MarkerSymbol || symbol instanceof CompositeSymbol)
                    || symbolSnapshot.role() != SymbolRole.MARKER) {
                throw roleMismatch(feature, symbolSnapshot);
            }
            Rectangle2D bounds = renderMarker(graphics, point, symbol, symbolSnapshot);
            renderPointLabel(graphics, feature.name(), bounds);
            return;
        }
        if (symbolSnapshot.role() != geometryRole(geometry)) {
            throw roleMismatch(feature, symbolSnapshot);
        }
        throw rendererNotRegistered(symbolSnapshot);
    }

    private void renderLegacyFeature(
            Graphics2D graphics, Geometry geometry, Feature feature, FeatureStyle style) {
        graphics.setStroke(
                new BasicStroke(
                        (float) style.strokeWidth(),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));

        if (geometry instanceof PointGeometry point) {
            Rectangle2D bounds = renderLegacyPoint(graphics, point, style);
            renderPointLabel(graphics, feature.name(), bounds);
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

    private Rectangle2D renderLegacyPoint(
            Graphics2D graphics, PointGeometry point, FeatureStyle style) {
        Coordinate screen = toScreen(point.coordinate());
        double diameter = style.pointDiameter();
        double radius = diameter / 2.0;
        Ellipse2D marker =
                new Ellipse2D.Double(screen.x() - radius, screen.y() - radius, diameter, diameter);
        if (style.fill().alpha() > 0) {
            graphics.setColor(color(style.fill()));
            graphics.fill(marker);
        }
        if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
            graphics.setColor(color(style.stroke()));
            graphics.draw(marker);
        }
        return marker.getBounds2D();
    }

    private Rectangle2D renderMarker(
            Graphics2D graphics,
            PointGeometry point,
            Symbol symbol,
            SymbolSnapshot symbolSnapshot) {
        Coordinate projected = projection.project(point.coordinate());
        MapViewport viewportSnapshot = viewport();
        Coordinate screen = viewportSnapshot.worldToScreen(projected);
        Coordinate xScreen =
                viewportSnapshot.worldToScreen(new Coordinate(projected.x() + 1.0, projected.y()));
        Coordinate yScreen =
                viewportSnapshot.worldToScreen(new Coordinate(projected.x(), projected.y() + 1.0));
        MapScreenBasis basis =
                MapScreenBasis.of(
                        new Coordinate(xScreen.x() - screen.x(), xScreen.y() - screen.y()),
                        new Coordinate(yScreen.x() - screen.x(), yScreen.y() - screen.y()));
        return renderMarkerSymbol(graphics, symbol, symbolSnapshot, screen, basis, 1.0);
    }

    private Rectangle2D renderMarkerSymbol(
            Graphics2D graphics,
            Symbol symbol,
            SymbolSnapshot symbolSnapshot,
            Coordinate featureScreen,
            MapScreenBasis basis,
            double inheritedOpacity) {
        if (symbolSnapshot.role() != SymbolRole.MARKER) {
            throw rendererValueMismatch(symbolSnapshot);
        }
        if (CompositeSymbol.RENDERER_KEY.equals(symbolSnapshot.rendererKey())) {
            if (!(symbol instanceof CompositeSymbol composite)) {
                throw rendererValueMismatch(symbolSnapshot);
            }
            double childOpacity = inheritedOpacity * composite.opacity();
            Rectangle2D result = null;
            for (Symbol child : composite.children()) {
                SymbolSnapshot childSnapshot =
                        new SymbolSnapshot(child.role(), child.rendererKey());
                Rectangle2D childBounds =
                        renderMarkerSymbol(
                                graphics, child, childSnapshot, featureScreen, basis, childOpacity);
                result = result == null ? childBounds : result.createUnion(childBounds);
            }
            return result;
        }
        if (!VectorMarkerSymbol.RENDERER_KEY.equals(symbolSnapshot.rendererKey())) {
            throw rendererNotRegistered(symbolSnapshot);
        }
        if (!(symbol instanceof VectorMarkerSymbol marker)) {
            throw rendererValueMismatch(symbolSnapshot);
        }
        MarkerTransform markerTransform =
                SymbolTransforms.marker(marker.viewBox(), marker.placement(), featureScreen, basis);
        Rectangle2D nominal = rectangle(markerTransform.nominalScreenBounds());
        double effectiveOpacity = inheritedOpacity * marker.opacity();
        if (effectiveOpacity == 0.0
                || (marker.fill().alpha() == 0
                        && marker.stroke().map(SymbolStroke::color).orElse(Rgba.TRANSPARENT).alpha()
                                == 0)) {
            return nominal;
        }
        VectorPath2D.Converted converted = VectorPath2D.convert(marker.path());
        AffineTransform transform =
                new AffineTransform(
                        markerTransform.m00(),
                        markerTransform.m10(),
                        markerTransform.m01(),
                        markerTransform.m11(),
                        markerTransform.m02(),
                        markerTransform.m12());
        Graphics2D childGraphics = (Graphics2D) graphics.create();
        try {
            childGraphics.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) effectiveOpacity));
            if (marker.fill().alpha() > 0) {
                Shape fill = transform.createTransformedShape(converted.fillPath());
                childGraphics.setColor(color(marker.fill()));
                childGraphics.fill(fill);
            }
            if (marker.stroke().isPresent() && marker.stroke().orElseThrow().color().alpha() > 0) {
                SymbolStroke stroke = marker.stroke().orElseThrow();
                double width = SymbolTransforms.screenLength(stroke.width(), basis);
                float floatWidth = (float) width;
                if (!Float.isFinite(floatWidth) || floatWidth <= 0.0f) {
                    throw transformFailure("symbol-screen-stroke-width");
                }
                childGraphics.setStroke(
                        new BasicStroke(floatWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                childGraphics.setColor(color(stroke.color()));
                Shape strokePath = transform.createTransformedShape(converted.strokePath());
                childGraphics.draw(strokePath);
            }
        } finally {
            childGraphics.dispose();
        }
        return nominal;
    }

    Rectangle2D renderMarkerSymbol(
            Graphics2D graphics, Symbol symbol, Coordinate featureScreen, MapScreenBasis basis) {
        Objects.requireNonNull(symbol, "symbol");
        return renderMarkerSymbol(
                graphics,
                symbol,
                new SymbolSnapshot(symbol.role(), symbol.rendererKey()),
                featureScreen,
                basis,
                1.0);
    }

    private static void renderPointLabel(
            Graphics2D graphics, String featureName, Rectangle2D nominalBounds) {
        if (featureName.isBlank()) {
            return;
        }
        graphics.setColor(new Color(32, 32, 32));
        Point2D baseline = pointLabelBaseline(nominalBounds);
        graphics.drawString(featureName, (float) baseline.getX(), (float) baseline.getY());
    }

    static Point2D pointLabelBaseline(Rectangle2D nominalBounds) {
        Objects.requireNonNull(nominalBounds, "nominalBounds");
        return new Point2D.Double(nominalBounds.getMaxX() + 4.0, nominalBounds.getMinY() - 2.0);
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

    private static Rectangle2D rectangle(Envelope bounds) {
        return new Rectangle2D.Double(
                bounds.minX(), bounds.minY(), bounds.width(), bounds.height());
    }

    private static SymbolRole geometryRole(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return SymbolRole.MARKER;
        }
        if (geometry instanceof LineStringGeometry) {
            return SymbolRole.LINE;
        }
        if (geometry instanceof PolygonGeometry) {
            return SymbolRole.FILL;
        }
        return null;
    }

    private static SymbolException transformFailure(String quantity) {
        return new SymbolException(
                SymbolException.TRANSFORM_NON_FINITE,
                "Symbol transform produced a non-finite value",
                Map.of("quantity", quantity));
    }

    private static SymbolException roleMismatch(Feature feature, SymbolSnapshot symbolSnapshot) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("featureId", feature.id());
        context.put("geometryKind", geometryKind(feature.geometry()));
        context.put("symbolRole", roleText(symbolSnapshot.role()));
        return new SymbolException(
                SymbolException.ROLE_MISMATCH,
                "Symbol role does not match feature geometry",
                context);
    }

    private static SymbolException rendererNotRegistered(SymbolSnapshot symbolSnapshot) {
        return rendererFailure(
                SymbolException.RENDERER_NOT_REGISTERED,
                "No renderer is registered for symbol",
                symbolSnapshot);
    }

    private static SymbolException rendererValueMismatch(SymbolSnapshot symbolSnapshot) {
        return rendererFailure(
                SymbolException.RENDERER_VALUE_MISMATCH,
                "Renderer does not support symbol value",
                symbolSnapshot);
    }

    private static SymbolException rendererFailure(
            String code, String message, SymbolSnapshot symbolSnapshot) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("role", roleText(symbolSnapshot.role()));
        context.put("key", keyText(symbolSnapshot.rendererKey()));
        return new SymbolException(code, message, context);
    }

    private static String geometryKind(Geometry geometry) {
        if (geometry instanceof PointGeometry) {
            return "POINT";
        }
        if (geometry instanceof LineStringGeometry) {
            return "LINE_STRING";
        }
        if (geometry instanceof PolygonGeometry) {
            return "POLYGON";
        }
        return "UNSUPPORTED";
    }

    private static String roleText(SymbolRole role) {
        return role == null ? "null" : role.name();
    }

    private static String keyText(SymbolRendererKey key) {
        return key == null ? "null" : key.value();
    }

    private record SymbolSnapshot(SymbolRole role, SymbolRendererKey rendererKey) {}

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
