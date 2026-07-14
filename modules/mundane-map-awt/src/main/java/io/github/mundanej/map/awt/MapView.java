package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureStyle;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MapPointerEvent;
import io.github.mundanej.map.api.MapPointerListener;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Projection;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.HatchLayouts;
import io.github.mundanej.map.core.HatchSegments;
import io.github.mundanej.map.core.LineEndpointBearings;
import io.github.mundanej.map.core.LineTangents;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.MarkerTransform;
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
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
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

    private final CrsRegistry crsRegistry;
    private final CrsDefinition mapCrs;
    private final CrsDefinition displayCrs;
    private final CrsOperation mapToDisplay;
    private final CrsOperation displayToMap;
    private final SymbolRendererRegistry symbolRenderers;
    private final List<MapPointerListener> pointerListeners = new ArrayList<>();
    private List<Layer> layers = List.of();
    private MapViewport viewport = MapViewport.initial(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    private Point dragAnchor;

    /** Creates an empty view using the supplied source-to-world projection. */
    public MapView(Projection projection) {
        this(projection, SymbolRendererRegistry.builtIn());
    }

    /** Creates an empty view using an explicit immutable symbol renderer registry. */
    public MapView(Projection projection, SymbolRendererRegistry symbolRenderers) {
        this(configuration(projection), symbolRenderers);
    }

    /** Creates an empty view with explicit map and display coordinate reference systems. */
    public MapView(CrsRegistry crsRegistry, CrsDefinition mapCrs, CrsDefinition displayCrs) {
        this(crsRegistry, mapCrs, displayCrs, SymbolRendererRegistry.builtIn());
    }

    /** Creates an explicitly configured view and symbol-renderer registry. */
    public MapView(
            CrsRegistry crsRegistry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            SymbolRendererRegistry symbolRenderers) {
        this(
                new CoordinateConfiguration(
                        Objects.requireNonNull(crsRegistry, "crsRegistry"),
                        Objects.requireNonNull(mapCrs, "mapCrs"),
                        Objects.requireNonNull(displayCrs, "displayCrs")),
                symbolRenderers);
    }

    private MapView(CoordinateConfiguration configuration, SymbolRendererRegistry symbolRenderers) {
        this.crsRegistry = configuration.registry();
        this.mapCrs = configuration.mapCrs();
        this.displayCrs = configuration.displayCrs();
        this.mapToDisplay = crsRegistry.operation(mapCrs, displayCrs);
        this.displayToMap = crsRegistry.operation(displayCrs, mapCrs);
        this.symbolRenderers = Objects.requireNonNull(symbolRenderers, "symbolRenderers");
        setOpaque(true);
        setBackground(Color.WHITE);
        setPreferredSize(new Dimension(DEFAULT_WIDTH, DEFAULT_HEIGHT));
        installInteraction();
    }

    /** Returns the map-coordinate CRS used by legacy layers and pointer values. */
    public CrsDefinition mapCrs() {
        return mapCrs;
    }

    /** Returns the world/display CRS used by the viewport. */
    public CrsDefinition displayCrs() {
        return displayCrs;
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
                        mapToDisplay.transformEnvelopeStrict(layer.envelope().orElseThrow());
                projected = projected == null ? next : projected.union(next);
            }
        }
        if (projected != null) {
            Dimension size = effectiveSize();
            viewport = MapViewport.fit(size.width, size.height, projected, paddingPixels);
            repaint();
        }
    }

    /** Converts a screen location into map coordinates when it lies in the inverse domain. */
    public Optional<Coordinate> screenToMap(double screenX, double screenY) {
        if (!Double.isFinite(screenX) || !Double.isFinite(screenY)) {
            throw new IllegalArgumentException("Screen coordinates must be finite");
        }
        Coordinate world;
        try {
            world = viewport().screenToWorld(screenX, screenY);
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        try {
            return Optional.of(displayToMap.transform(world));
        } catch (CrsException exception) {
            if (isExpectedConversionMiss(exception)) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    /** Converts a map coordinate into screen coordinates when it is representable. */
    public Optional<Coordinate> mapToScreen(Coordinate coordinate) {
        Objects.requireNonNull(coordinate, "coordinate");
        Coordinate world;
        try {
            world = mapToDisplay.transform(coordinate);
        } catch (CrsException exception) {
            if (isExpectedConversionMiss(exception)) {
                return Optional.empty();
            }
            throw exception;
        }
        try {
            return Optional.of(viewport().worldToScreen(world));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
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
        Symbol symbol = feature.symbol();
        if (!(symbol instanceof FeatureStyle)
                && symbol.role() != geometryRole(feature.geometry())) {
            throw roleMismatch(feature, new SymbolSnapshot(symbol.role(), symbol.rendererKey()));
        }
        Optional<Coordinate> markerAnchor =
                feature.geometry() instanceof PointGeometry point
                        ? Optional.of(toScreen(point.coordinate()))
                        : Optional.empty();
        AwtSymbolRenderContext context =
                context(
                        graphics,
                        symbol.role(),
                        feature.id(),
                        feature.geometry(),
                        feature.geometry(),
                        1.0,
                        false,
                        OptionalDouble.empty(),
                        markerAnchor);
        SymbolRenderResult result = dispatch(symbol, context);
        if (feature.geometry() instanceof PointGeometry) {
            Envelope bounds = result.nominalMarkerBounds().orElseThrow();
            renderPointLabel(graphics, feature.name(), rectangle(bounds));
        }
    }

    private AwtSymbolRenderContext context(
            Graphics2D graphics,
            SymbolRole role,
            String featureId,
            Geometry featureGeometry,
            Geometry renderGeometry,
            double inheritedOpacity,
            boolean closedRing,
            OptionalDouble endpointBearing,
            Optional<Coordinate> markerAnchor) {
        return new AwtSymbolRenderContext(
                graphics,
                role,
                featureId,
                featureGeometry,
                renderGeometry,
                mapToDisplay,
                viewport(),
                inheritedOpacity,
                closedRing,
                endpointBearing,
                markerAnchor,
                screenBasis(),
                this);
    }

    private SymbolRenderResult dispatch(Symbol symbol, AwtSymbolRenderContext context) {
        SymbolSnapshot snapshot = new SymbolSnapshot(symbol.role(), symbol.rendererKey());
        AwtSymbolRenderer renderer = symbolRenderers.find(snapshot.role(), snapshot.rendererKey());
        if (renderer == null) {
            throw rendererNotRegistered(snapshot);
        }
        if (!renderer.supports(symbol)) {
            throw rendererValueMismatch(snapshot);
        }
        SymbolRenderResult result = renderer.render(symbol, context);
        boolean markerResult = result != null && result.nominalMarkerBounds().isPresent();
        boolean valid =
                result != null
                        && (context.role() == SymbolRole.LEGACY_GEOMETRY
                                ? markerResult
                                        == (context.renderGeometry() instanceof PointGeometry)
                                : markerResult == (context.role() == SymbolRole.MARKER));
        if (!valid) {
            throw rendererFailure(
                    SymbolException.RENDERER_INVALID_RESULT,
                    "Renderer returned an incompatible result",
                    snapshot);
        }
        return result;
    }

    SymbolRenderResult renderChild(Symbol child, AwtSymbolRenderContext parent, double multiplier) {
        AwtSymbolRenderContext childContext =
                context(
                        parent.parentGraphics(),
                        parent.role(),
                        parent.featureId(),
                        parent.featureGeometry(),
                        parent.renderGeometry(),
                        parent.inheritedOpacity() * multiplier,
                        parent.closedRing(),
                        parent.endpointBearingDegrees(),
                        parent.markerAnchorScreen());
        return dispatch(child, childContext);
    }

    SymbolRenderResult renderBuiltIn(Symbol value, AwtSymbolRenderContext context) {
        if (value instanceof CompositeSymbol composite) {
            SymbolRenderResult result = SymbolRenderResult.none();
            for (Symbol child : composite.children()) {
                result = result.union(context.renderChild(child, composite.opacity()));
            }
            return result;
        }
        if (value instanceof FeatureStyle style) {
            Rectangle2D bounds =
                    renderLegacyFeature(context.parentGraphics(), context.renderGeometry(), style);
            return bounds == null
                    ? SymbolRenderResult.none()
                    : SymbolRenderResult.markerBounds(envelope(bounds));
        }
        if (value instanceof VectorMarkerSymbol marker) {
            Coordinate anchor = context.markerAnchorScreen().orElseThrow();
            Rectangle2D bounds =
                    renderMarkerSymbol(
                            context.parentGraphics(),
                            marker,
                            new SymbolSnapshot(marker.role(), marker.rendererKey()),
                            anchor,
                            context.mapScreenBasis(),
                            context.inheritedOpacity(),
                            context.endpointBearingDegrees());
            return SymbolRenderResult.markerBounds(envelope(bounds));
        }
        if (value instanceof RasterIconSymbol icon) {
            return renderRasterIcon(icon, context);
        }
        if (value instanceof SolidLineSymbol line
                && context.renderGeometry() instanceof LineStringGeometry) {
            renderRegisteredLine(line, context);
            return SymbolRenderResult.none();
        }
        if ((value instanceof SolidFillSymbol || value instanceof HatchFillSymbol)
                && context.renderGeometry() instanceof PolygonGeometry) {
            renderRegisteredFill(value, context);
            return SymbolRenderResult.none();
        }
        throw rendererValueMismatch(new SymbolSnapshot(value.role(), value.rendererKey()));
    }

    private SymbolRenderResult renderRasterIcon(
            RasterIconSymbol icon, AwtSymbolRenderContext context) {
        Envelope viewBox = new Envelope(0.0, 0.0, icon.width(), icon.height());
        MarkerTransform transform =
                context.endpointBearingDegrees().isPresent()
                        ? SymbolTransforms.markerAtScreenBearing(
                                viewBox,
                                icon.placement(),
                                context.markerAnchorScreen().orElseThrow(),
                                context.mapScreenBasis(),
                                context.endpointBearingDegrees().orElseThrow())
                        : SymbolTransforms.marker(
                                viewBox,
                                icon.placement(),
                                context.markerAnchorScreen().orElseThrow(),
                                context.mapScreenBasis());
        double opacity = context.inheritedOpacity() * icon.opacity();
        if (opacity > 0.0) {
            BufferedImage image =
                    new BufferedImage(icon.width(), icon.height(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < icon.height(); y++) {
                for (int x = 0; x < icon.width(); x++) {
                    int rgba = icon.rgbaAt(x, y);
                    image.setRGB(x, y, (rgba << 24) | ((rgba >>> 8) & 0x00ff_ffff));
                }
            }
            Graphics2D child = context.createGraphics();
            try {
                child.setComposite(
                        AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
                child.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        icon.interpolation() == RasterInterpolation.NEAREST
                                ? RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
                                : RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                child.drawImage(
                        image,
                        new AffineTransform(
                                transform.m00(),
                                transform.m10(),
                                transform.m01(),
                                transform.m11(),
                                transform.m02(),
                                transform.m12()),
                        null);
            } finally {
                child.dispose();
            }
        }
        return SymbolRenderResult.markerBounds(transform.nominalScreenBounds());
    }

    private Rectangle2D renderLegacyFeature(
            Graphics2D graphics, Geometry geometry, FeatureStyle style) {
        graphics.setStroke(
                new BasicStroke(
                        (float) style.strokeWidth(),
                        BasicStroke.CAP_ROUND,
                        BasicStroke.JOIN_ROUND));

        if (geometry instanceof PointGeometry point) {
            return renderLegacyPoint(graphics, point, style);
        } else if (geometry instanceof LineStringGeometry line) {
            if (style.stroke().alpha() > 0 && style.strokeWidth() > 0.0) {
                Path2D path = path(line.coordinates(), false);
                graphics.setColor(color(style.stroke()));
                graphics.draw(path);
            }
        } else if (geometry instanceof PolygonGeometry polygon) {
            renderPolygon(graphics, polygon, style);
        }
        return null;
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

    private Rectangle2D renderMarkerSymbol(
            Graphics2D graphics,
            Symbol symbol,
            SymbolSnapshot symbolSnapshot,
            Coordinate featureScreen,
            MapScreenBasis basis,
            double inheritedOpacity,
            OptionalDouble outwardBearing) {
        if (symbolSnapshot.role() != SymbolRole.MARKER) {
            throw rendererValueMismatch(symbolSnapshot);
        }
        if (!VectorMarkerSymbol.RENDERER_KEY.equals(symbolSnapshot.rendererKey())) {
            throw rendererNotRegistered(symbolSnapshot);
        }
        if (!(symbol instanceof VectorMarkerSymbol marker)) {
            throw rendererValueMismatch(symbolSnapshot);
        }
        MarkerTransform markerTransform =
                outwardBearing.isPresent()
                        ? SymbolTransforms.markerAtScreenBearing(
                                marker.viewBox(),
                                marker.placement(),
                                featureScreen,
                                basis,
                                outwardBearing.orElseThrow())
                        : SymbolTransforms.marker(
                                marker.viewBox(), marker.placement(), featureScreen, basis);
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
                1.0,
                OptionalDouble.empty());
    }

    private void renderRegisteredLine(SolidLineSymbol line, AwtSymbolRenderContext context) {
        LineStringGeometry geometry = (LineStringGeometry) context.renderGeometry();
        CoordinateSequence screen = toScreen(geometry.coordinates());
        LineEndpointBearings bearings =
                LineTangents.outwardScreenBearings(screen, context.featureId(), 0);
        if (bearings.startBearingDegrees().isEmpty() && bearings.endBearingDegrees().isEmpty()) {
            return;
        }
        double opacity = context.inheritedOpacity() * line.opacity();
        paintLinePart(
                context.parentGraphics(), screen, line.stroke(), context.mapScreenBasis(), opacity);
        if (context.closedRing()) {
            return;
        }
        if (line.startMarker().isPresent() && bearings.startBearingDegrees().isPresent()) {
            dispatchEndpoint(
                    line.startMarker().orElseThrow(),
                    context,
                    opacity,
                    geometry.coordinates().coordinate(0),
                    screen.coordinate(0),
                    bearings.startBearingDegrees().orElseThrow());
        }
        if (line.endMarker().isPresent() && bearings.endBearingDegrees().isPresent()) {
            int last = geometry.coordinates().size() - 1;
            dispatchEndpoint(
                    line.endMarker().orElseThrow(),
                    context,
                    opacity,
                    geometry.coordinates().coordinate(last),
                    screen.coordinate(last),
                    bearings.endBearingDegrees().orElseThrow());
        }
    }

    private void dispatchEndpoint(
            Symbol marker,
            AwtSymbolRenderContext owner,
            double inheritedOpacity,
            Coordinate sourceEndpoint,
            Coordinate screenEndpoint,
            double bearing) {
        dispatch(
                marker,
                context(
                        owner.parentGraphics(),
                        SymbolRole.MARKER,
                        owner.featureId(),
                        owner.featureGeometry(),
                        new PointGeometry(sourceEndpoint),
                        inheritedOpacity,
                        false,
                        OptionalDouble.of(bearing),
                        Optional.of(screenEndpoint)));
    }

    private static void paintLinePart(
            Graphics2D graphics,
            CoordinateSequence part,
            SymbolStroke stroke,
            MapScreenBasis basis,
            double opacity) {
        if (opacity == 0.0 || stroke.color().alpha() == 0) {
            return;
        }
        Graphics2D child = (Graphics2D) graphics.create();
        try {
            child.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
            child.setStroke(screenStroke(stroke, basis));
            child.setColor(color(stroke.color()));
            child.draw(screenPath(part, false));
        } finally {
            child.dispose();
        }
    }

    private void renderRegisteredFill(Symbol symbol, AwtSymbolRenderContext context) {
        PolygonGeometry geometry = (PolygonGeometry) context.renderGeometry();
        ScreenPolygon polygon = screenPolygon(geometry);
        if (symbol instanceof SolidFillSymbol fill) {
            double opacity = context.inheritedOpacity() * fill.opacity();
            if (opacity > 0.0 && fill.fill().alpha() > 0) {
                Graphics2D child = context.createGraphics();
                try {
                    child.setComposite(
                            AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
                    child.setColor(color(fill.fill()));
                    child.fill(polygon.path());
                } finally {
                    child.dispose();
                }
            }
            fill.outline()
                    .ifPresent(
                            outline ->
                                    renderRegisteredOutline(outline, geometry, context, opacity));
        } else if (symbol instanceof HatchFillSymbol hatch) {
            double opacity = context.inheritedOpacity() * hatch.opacity();
            paintHatch(
                    context.parentGraphics(),
                    hatch,
                    polygon,
                    context.mapScreenBasis(),
                    context.featureId(),
                    opacity);
            hatch.outline()
                    .ifPresent(
                            outline ->
                                    renderRegisteredOutline(outline, geometry, context, opacity));
        }
    }

    private void renderRegisteredOutline(
            Symbol outline,
            PolygonGeometry polygon,
            AwtSymbolRenderContext owner,
            double inheritedOpacity) {
        List<CoordinateSequence> rings = new ArrayList<>();
        rings.add(polygon.exterior());
        rings.addAll(polygon.holes());
        for (CoordinateSequence ring : rings) {
            dispatch(
                    outline,
                    context(
                            owner.parentGraphics(),
                            SymbolRole.LINE,
                            owner.featureId(),
                            owner.featureGeometry(),
                            new LineStringGeometry(ring),
                            inheritedOpacity,
                            true,
                            OptionalDouble.empty(),
                            Optional.empty()));
        }
    }

    private void paintHatch(
            Graphics2D graphics,
            HatchFillSymbol hatch,
            ScreenPolygon polygon,
            MapScreenBasis basis,
            String featureId,
            double opacity) {
        if (opacity == 0.0 || hatch.stroke().color().alpha() == 0) {
            return;
        }
        Rectangle2D work =
                polygon.path()
                        .getBounds2D()
                        .createIntersection(
                                new Rectangle2D.Double(0.0, 0.0, getWidth(), getHeight()));
        if (graphics.getClip() != null) {
            work = work.createIntersection(graphics.getClip().getBounds2D());
        }
        if (work.isEmpty()) {
            return;
        }
        Envelope bounds =
                new Envelope(work.getMinX(), work.getMinY(), work.getMaxX(), work.getMaxY());
        boolean mapRelative =
                hatch.rotationMode() == io.github.mundanej.map.api.SymbolRotationMode.MAP_RELATIVE;
        Coordinate origin =
                mapRelative
                        ? viewport().worldToScreen(new Coordinate(0.0, 0.0))
                        : new Coordinate(0.0, 0.0);
        double bearing = mapRelative ? basis.xAxisScreenBearingDegrees() : 0.0;
        double spacing = SymbolTransforms.screenLength(hatch.spacing(), basis);
        HatchSegments segments =
                HatchLayouts.cover(
                        hatch.pattern(),
                        bounds,
                        origin,
                        bearing,
                        spacing,
                        hatch.maxSegments(),
                        featureId);
        Graphics2D child = (Graphics2D) graphics.create();
        try {
            child.clip(polygon.path());
            child.setComposite(
                    AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) opacity));
            child.setStroke(screenStroke(hatch.stroke(), basis));
            child.setColor(color(hatch.stroke().color()));
            Path2D lines = new Path2D.Double();
            for (int index = 0; index < segments.segmentCount(); index++) {
                lines.moveTo(segments.x1(index), segments.y1(index));
                lines.lineTo(segments.x2(index), segments.y2(index));
            }
            child.draw(lines);
        } finally {
            child.dispose();
        }
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

    private CoordinateSequence toScreen(CoordinateSequence source) {
        double[] ordinates = new double[source.size() * 2];
        for (int index = 0; index < source.size(); index++) {
            Coordinate screen = toScreen(source.coordinate(index));
            ordinates[index * 2] = screen.x();
            ordinates[index * 2 + 1] = screen.y();
        }
        return CoordinateSequence.of(ordinates);
    }

    private ScreenPolygon screenPolygon(PolygonGeometry polygon) {
        List<CoordinateSequence> rings = new ArrayList<>();
        rings.add(toScreen(polygon.exterior()));
        for (CoordinateSequence hole : polygon.holes()) {
            rings.add(toScreen(hole));
        }
        Path2D screenPath = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        for (CoordinateSequence ring : rings) {
            appendScreen(screenPath, ring, true);
        }
        return new ScreenPolygon(screenPath, List.copyOf(rings));
    }

    private MapScreenBasis screenBasis() {
        double screenPixelsPerMapUnit = 1.0 / viewport().worldUnitsPerPixel();
        return MapScreenBasis.of(
                new Coordinate(screenPixelsPerMapUnit, 0.0),
                new Coordinate(0.0, -screenPixelsPerMapUnit));
    }

    private static Path2D screenPath(CoordinateSequence coordinates, boolean close) {
        Path2D result = new Path2D.Double();
        appendScreen(result, coordinates, close);
        return result;
    }

    private static void appendScreen(Path2D path, CoordinateSequence coordinates, boolean close) {
        path.moveTo(coordinates.x(0), coordinates.y(0));
        for (int index = 1; index < coordinates.size(); index++) {
            path.lineTo(coordinates.x(index), coordinates.y(index));
        }
        if (close) {
            path.closePath();
        }
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
        return viewport.worldToScreen(mapToDisplay.transform(source));
    }

    private static Color color(Rgba color) {
        return new Color(color.red(), color.green(), color.blue(), color.alpha());
    }

    private static BasicStroke screenStroke(SymbolStroke stroke, MapScreenBasis basis) {
        double width = SymbolTransforms.screenLength(stroke.width(), basis);
        float floatWidth = (float) width;
        if (!Float.isFinite(floatWidth) || floatWidth <= 0.0f) {
            throw transformFailure("symbol-screen-stroke-width");
        }
        return new BasicStroke(floatWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    }

    private static Rectangle2D rectangle(Envelope bounds) {
        return new Rectangle2D.Double(
                bounds.minX(), bounds.minY(), bounds.width(), bounds.height());
    }

    private static Envelope envelope(Rectangle2D bounds) {
        return new Envelope(bounds.getMinX(), bounds.getMinY(), bounds.getMaxX(), bounds.getMaxY());
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

    private record ScreenPolygon(Path2D path, List<CoordinateSequence> rings) {}

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

    private static CoordinateConfiguration configuration(Projection projection) {
        Projection required = Objects.requireNonNull(projection, "projection");
        CrsRegistry registry =
                CrsRegistry.builder()
                        .registerDefinition(required.sourceCrs(), List.of())
                        .registerDefinition(required.targetCrs(), List.of())
                        .registerProjection(required)
                        .build();
        return new CoordinateConfiguration(registry, required.sourceCrs(), required.targetCrs());
    }

    private static boolean isExpectedConversionMiss(CrsException exception) {
        String code = exception.problem().code();
        return code.equals("CRS_COORDINATE_OUT_OF_DOMAIN")
                || code.equals("CRS_TRANSFORM_NON_FINITE");
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

    private record CoordinateConfiguration(
            CrsRegistry registry, CrsDefinition mapCrs, CrsDefinition displayCrs) {}
}
