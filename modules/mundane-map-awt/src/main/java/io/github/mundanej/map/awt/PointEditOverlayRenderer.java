package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.MapViewport;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.util.Optional;

/** Package-private renderer for non-authoritative point-edit previews. */
final class PointEditOverlayRenderer {
    private static final Color MOVE = new Color(35, 95, 210, 190);
    private static final Color SNAPPED = new Color(35, 155, 75, 230);
    private static final Color UNSNAPPED = new Color(225, 125, 25, 230);

    private PointEditOverlayRenderer() {}

    static void render(
            Graphics2D graphics,
            PointEditController.Preview preview,
            CrsOperation mapToDisplay,
            MapViewport viewport,
            Optional<HorizontalWrap> horizontalWrap) {
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(
                    RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Coordinate candidate =
                    screen(
                            preview.candidate(),
                            mapToDisplay,
                            viewport,
                            horizontalWrap,
                            preview.referenceDisplayX());
            if (preview.original().isPresent()) {
                Coordinate original =
                        screen(
                                preview.original().orElseThrow(),
                                mapToDisplay,
                                viewport,
                                horizontalWrap,
                                preview.referenceDisplayX());
                copy.setColor(MOVE);
                copy.setStroke(
                        new BasicStroke(
                                2f,
                                BasicStroke.CAP_ROUND,
                                BasicStroke.JOIN_ROUND,
                                10f,
                                new float[] {5f, 4f},
                                0f));
                copy.draw(
                        new Line2D.Double(
                                original.x(), original.y(), candidate.x(), candidate.y()));
            }
            copy.setColor(preview.snapped() ? SNAPPED : UNSNAPPED);
            copy.setStroke(new BasicStroke(2f));
            copy.draw(new Ellipse2D.Double(candidate.x() - 6, candidate.y() - 6, 12, 12));
            copy.draw(
                    new Line2D.Double(
                            candidate.x() - 8, candidate.y(), candidate.x() + 8, candidate.y()));
            copy.draw(
                    new Line2D.Double(
                            candidate.x(), candidate.y() - 8, candidate.x(), candidate.y() + 8));
        } catch (CrsException ignored) {
            // A preview is transient; an unrepresentable point is omitted without hiding content.
        } finally {
            copy.dispose();
        }
    }

    private static Coordinate screen(
            Coordinate coordinate,
            CrsOperation operation,
            MapViewport viewport,
            Optional<HorizontalWrap> horizontalWrap,
            double referenceDisplayX) {
        Coordinate world = operation.transform(coordinate);
        if (horizontalWrap.isPresent()) {
            world =
                    new Coordinate(
                            horizontalWrap
                                    .orElseThrow()
                                    .nearestEquivalent(world.x(), referenceDisplayX),
                            world.y());
        }
        return viewport.worldToScreen(world);
    }
}
