package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.DistanceResult;
import io.github.mundanej.map.api.MeasurementState;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Package-private fixed presentation for an active measurement tool. */
final class MeasurementOverlayRenderer {
    private static final Color CASING = Color.WHITE;
    private static final Color LINE = new Color(190, 35, 55);
    private static final Color TEXT_BACKGROUND = new Color(255, 255, 255, 220);
    private static final BasicStroke CASING_STROKE =
            new BasicStroke(5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke LINE_STROKE =
            new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
    private static final BasicStroke PREVIEW_STROKE =
            new BasicStroke(
                    2f,
                    BasicStroke.CAP_ROUND,
                    BasicStroke.JOIN_ROUND,
                    10f,
                    new float[] {6f, 5f},
                    0f);
    private static final double CLIP_ALLOWANCE = 6.0;
    private static final double VERTEX_RADIUS = 4.0;

    private MeasurementOverlayRenderer() {}

    static void render(
            Graphics2D source,
            MeasurementState state,
            CrsOperation mapToDisplay,
            MapViewport viewport,
            int width,
            int height) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(mapToDisplay, "mapToDisplay");
        Objects.requireNonNull(viewport, "viewport");
        if (state.vertexCount() == 0 || width <= 0 || height <= 0) {
            return;
        }
        Graphics2D graphics = (Graphics2D) source.create();
        try {
            graphics.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Optional<Coordinate> previous = screen(state.vertex(0), mapToDisplay, viewport);
            for (int index = 1; index < state.vertexCount(); index++) {
                Optional<Coordinate> current = screen(state.vertex(index), mapToDisplay, viewport);
                if (previous.isPresent() && current.isPresent()) {
                    drawSegment(
                            graphics,
                            previous.orElseThrow(),
                            current.orElseThrow(),
                            false,
                            width,
                            height);
                }
                previous = current;
            }
            Optional<Coordinate> previewScreen =
                    state.preview().flatMap(value -> screen(value, mapToDisplay, viewport));
            Optional<Coordinate> lastScreen =
                    screen(state.vertex(state.vertexCount() - 1), mapToDisplay, viewport);
            if (lastScreen.isPresent() && previewScreen.isPresent()) {
                drawSegment(
                        graphics,
                        lastScreen.orElseThrow(),
                        previewScreen.orElseThrow(),
                        true,
                        width,
                        height);
            }
            for (int index = 0; index < state.vertexCount(); index++) {
                screen(state.vertex(index), mapToDisplay, viewport)
                        .ifPresent(point -> drawVertex(graphics, point, width, height));
            }
            drawCurrentSegmentLabel(graphics, state, mapToDisplay, viewport, width, height);
            drawBadge(graphics, format(state.displayedDistance()), 8, 8, width, height);
        } finally {
            graphics.dispose();
        }
    }

    static String format(DistanceResult distance) {
        Objects.requireNonNull(distance, "distance");
        boolean kilometres = distance.metres() >= 1_000.0;
        double value = kilometres ? distance.metres() / 1_000.0 : distance.metres();
        int scale = kilometres ? 2 : 1;
        String number =
                BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).toPlainString();
        return String.format(Locale.ROOT, "%s %s", number, kilometres ? "km" : "m");
    }

    private static void drawCurrentSegmentLabel(
            Graphics2D graphics,
            MeasurementState state,
            CrsOperation operation,
            MapViewport viewport,
            int width,
            int height) {
        Optional<DistanceResult> distance = state.previewSegmentDistance();
        Coordinate start;
        Coordinate end;
        if (state.preview().isPresent()) {
            start = state.vertex(state.vertexCount() - 1);
            end = state.preview().orElseThrow();
        } else if (state.vertexCount() >= 2) {
            distance = state.lastCommittedSegmentDistance();
            start = state.vertex(state.vertexCount() - 2);
            end = state.vertex(state.vertexCount() - 1);
        } else {
            return;
        }
        Optional<Coordinate> startScreen = screen(start, operation, viewport);
        Optional<Coordinate> endScreen = screen(end, operation, viewport);
        if (distance.isEmpty() || startScreen.isEmpty() || endScreen.isEmpty()) {
            return;
        }
        double x = (startScreen.orElseThrow().x() + endScreen.orElseThrow().x()) / 2.0;
        double y = (startScreen.orElseThrow().y() + endScreen.orElseThrow().y()) / 2.0;
        drawBadge(graphics, format(distance.orElseThrow()), x + 6, y - 18, width, height);
    }

    private static void drawSegment(
            Graphics2D graphics,
            Coordinate start,
            Coordinate end,
            boolean preview,
            int width,
            int height) {
        Optional<double[]> clipped = clip(start, end, width, height);
        if (clipped.isEmpty()) {
            return;
        }
        double[] segment = clipped.orElseThrow();
        Line2D line = new Line2D.Double(segment[0], segment[1], segment[2], segment[3]);
        graphics.setColor(CASING);
        graphics.setStroke(CASING_STROKE);
        graphics.draw(line);
        graphics.setColor(LINE);
        graphics.setStroke(preview ? PREVIEW_STROKE : LINE_STROKE);
        graphics.draw(line);
    }

    private static void drawVertex(Graphics2D graphics, Coordinate point, int width, int height) {
        if (point.x() < -CLIP_ALLOWANCE
                || point.x() > width + CLIP_ALLOWANCE
                || point.y() < -CLIP_ALLOWANCE
                || point.y() > height + CLIP_ALLOWANCE) {
            return;
        }
        Ellipse2D mark =
                new Ellipse2D.Double(
                        point.x() - VERTEX_RADIUS,
                        point.y() - VERTEX_RADIUS,
                        VERTEX_RADIUS * 2,
                        VERTEX_RADIUS * 2);
        graphics.setColor(Color.WHITE);
        graphics.fill(mark);
        graphics.setStroke(LINE_STROKE);
        graphics.setColor(LINE);
        graphics.draw(mark);
    }

    private static void drawBadge(
            Graphics2D graphics, String text, double x, double y, int width, int height) {
        FontMetrics metrics = graphics.getFontMetrics();
        int padding = 4;
        int badgeWidth = metrics.stringWidth(text) + padding * 2;
        int badgeHeight = metrics.getHeight() + padding * 2;
        int left = (int) StrictMath.round(x);
        int top = (int) StrictMath.round(y);
        if (left >= width || top >= height || left + badgeWidth <= 0 || top + badgeHeight <= 0) {
            return;
        }
        graphics.setColor(TEXT_BACKGROUND);
        graphics.fillRoundRect(left, top, badgeWidth, badgeHeight, 8, 8);
        graphics.setColor(Color.BLACK);
        graphics.drawString(text, left + padding, top + padding + metrics.getAscent());
    }

    private static Optional<Coordinate> screen(
            Coordinate source, CrsOperation operation, MapViewport viewport) {
        try {
            Coordinate point = viewport.worldToScreen(operation.transform(source));
            return Double.isFinite(point.x()) && Double.isFinite(point.y())
                    ? Optional.of(point)
                    : Optional.empty();
        } catch (CrsException exception) {
            String code = exception.problem().code();
            if (code.equals("CRS_COORDINATE_OUT_OF_DOMAIN")
                    || code.equals("CRS_TRANSFORM_NON_FINITE")) {
                return Optional.empty();
            }
            throw exception;
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    static Optional<double[]> clip(Coordinate start, Coordinate end, int width, int height) {
        double minX = -CLIP_ALLOWANCE;
        double minY = -CLIP_ALLOWANCE;
        double maxX = width + CLIP_ALLOWANCE;
        double maxY = height + CLIP_ALLOWANCE;
        double dx = end.x() - start.x();
        double dy = end.y() - start.y();
        if (!Double.isFinite(dx) || !Double.isFinite(dy)) {
            return Optional.empty();
        }
        double[] interval = {0.0, 1.0};
        if (!clipBoundary(-dx, start.x() - minX, interval)
                || !clipBoundary(dx, maxX - start.x(), interval)
                || !clipBoundary(-dy, start.y() - minY, interval)
                || !clipBoundary(dy, maxY - start.y(), interval)) {
            return Optional.empty();
        }
        double[] result = {
            start.x() + interval[0] * dx,
            start.y() + interval[0] * dy,
            start.x() + interval[1] * dx,
            start.y() + interval[1] * dy
        };
        for (double ordinate : result) {
            if (!Double.isFinite(ordinate)) {
                return Optional.empty();
            }
        }
        return Optional.of(result);
    }

    private static boolean clipBoundary(double p, double q, double[] interval) {
        if (p == 0.0) {
            return q >= 0.0;
        }
        double ratio = q / p;
        if (p < 0.0) {
            if (ratio > interval[1]) {
                return false;
            }
            interval[0] = StrictMath.max(interval[0], ratio);
        } else {
            if (ratio < interval[0]) {
                return false;
            }
            interval[1] = StrictMath.min(interval[1], ratio);
        }
        return true;
    }
}
