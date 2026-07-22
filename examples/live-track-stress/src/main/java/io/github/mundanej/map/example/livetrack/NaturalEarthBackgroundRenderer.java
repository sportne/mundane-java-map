package io.github.mundanej.map.example.livetrack;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.HorizontalWrapPlan;
import io.github.mundanej.map.core.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/** Detached renderer for the immutable projected Natural Earth background. */
final class NaturalEarthBackgroundRenderer implements StaticMapBackgroundCache.Renderer {
    private static final BasicStroke OUTLINE_STROKE =
            new BasicStroke(0.75f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND);

    private final List<FeatureRecord> features;
    private final HorizontalWrap wrap;

    NaturalEarthBackgroundRenderer(List<FeatureRecord> features) {
        this(features, HorizontalWrap.webMercator());
    }

    NaturalEarthBackgroundRenderer(List<FeatureRecord> features, HorizontalWrap wrap) {
        this.features = List.copyOf(Objects.requireNonNull(features, "features"));
        this.wrap = Objects.requireNonNull(wrap, "wrap");
    }

    @Override
    public BufferedImage render(MapViewport viewport) {
        Objects.requireNonNull(viewport, "viewport");
        BufferedImage image =
                new BufferedImage(viewport.width(), viewport.height(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(NaturalEarthChart.OCEAN);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setRenderingHint(
                    RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setStroke(OUTLINE_STROKE);
            Envelope visible = viewport.visibleWorldEnvelope();
            HorizontalWrapPlan plan =
                    wrap.plan(visible.minX(), visible.maxX(), viewport.worldUnitsPerPixel());
            for (FeatureRecord feature : features) {
                if (Thread.currentThread().isInterrupted()) {
                    throw new CancellationException("static background rendering cancelled");
                }
                Geometry geometry = feature.geometry();
                for (long copyIndex = plan.minimumVisibleCopyIndex();
                        copyIndex <= plan.maximumVisibleCopyIndex();
                        copyIndex++) {
                    double offset =
                            wrap.translate(wrap.canonicalMinimumX(), copyIndex)
                                    - wrap.canonicalMinimumX();
                    if (!intersects(visible, geometry.envelope(), offset)) {
                        continue;
                    }
                    Path2D.Double path = path(geometry, viewport, offset);
                    graphics.setColor(color(NaturalEarthChart.LAND));
                    graphics.fill(path);
                    graphics.setColor(color(NaturalEarthChart.OUTLINE));
                    graphics.draw(path);
                }
            }
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static Path2D.Double path(Geometry geometry, MapViewport viewport, double offsetX) {
        Path2D.Double path = new Path2D.Double(Path2D.WIND_EVEN_ODD);
        if (geometry instanceof PolygonGeometry polygon) {
            append(path, polygon.exterior(), 0, polygon.exterior().size(), viewport, offsetX);
            for (CoordinateSequence hole : polygon.holes()) {
                append(path, hole, 0, hole.size(), viewport, offsetX);
            }
            return path;
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        for (int ring = 0; ring < polygons.ringCount(); ring++) {
            append(
                    path,
                    polygons.coordinates(),
                    polygons.ringOffset(ring),
                    polygons.ringOffset(ring + 1),
                    viewport,
                    offsetX);
        }
        return path;
    }

    private static void append(
            Path2D.Double path,
            CoordinateSequence coordinates,
            int start,
            int end,
            MapViewport viewport,
            double offsetX) {
        double units = viewport.worldUnitsPerPixel();
        double x =
                viewport.width() / 2.0
                        + (coordinates.x(start) + offsetX - viewport.centerX()) / units;
        double y = viewport.height() / 2.0 - (coordinates.y(start) - viewport.centerY()) / units;
        path.moveTo(x, y);
        for (int index = start + 1; index < end - 1; index++) {
            x =
                    viewport.width() / 2.0
                            + (coordinates.x(index) + offsetX - viewport.centerX()) / units;
            y = viewport.height() / 2.0 - (coordinates.y(index) - viewport.centerY()) / units;
            path.lineTo(x, y);
        }
        path.closePath();
    }

    private static boolean intersects(Envelope first, Envelope second, double offsetX) {
        return first.minX() <= second.maxX() + offsetX
                && first.maxX() >= second.minX() + offsetX
                && first.minY() <= second.maxY()
                && first.maxY() >= second.minY();
    }

    private static Color color(Rgba value) {
        return new Color(value.red(), value.green(), value.blue(), value.alpha());
    }
}
