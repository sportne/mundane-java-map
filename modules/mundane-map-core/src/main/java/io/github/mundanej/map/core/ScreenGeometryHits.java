package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CoordinateSequence;
import java.util.List;
import java.util.Objects;

/** Stateless, allocation-free screen-geometry hit predicates. */
public final class ScreenGeometryHits {
    private ScreenGeometryHits() {}

    /** Returns whether two finite points are within the inclusive radius. */
    public static boolean pointWithin(
            double pointX, double pointY, double queryX, double queryY, double radius) {
        requireFinite(pointX, pointY, queryX, queryY, radius);
        requireRadius(radius);
        return Math.hypot(pointX - queryX, pointY - queryY) <= radius;
    }

    /** Tests the union of round segment capsules in an open or closed sequence. */
    public static boolean polylineWithin(
            CoordinateSequence screen,
            boolean closed,
            double queryX,
            double queryY,
            double radius) {
        Objects.requireNonNull(screen, "screen");
        requireFinite(queryX, queryY, radius);
        requireRadius(radius);
        int segmentCount = closed ? screen.size() : screen.size() - 1;
        for (int index = 0; index < segmentCount; index++) {
            int next = (index + 1) % screen.size();
            if (segmentWithin(
                    screen.x(index),
                    screen.y(index),
                    screen.x(next),
                    screen.y(next),
                    queryX,
                    queryY,
                    radius)) {
                return true;
            }
        }
        return false;
    }

    /** Tests an even-odd filled polygon plus its inclusive tolerance-expanded boundaries. */
    public static boolean filledPolygonWithin(
            CoordinateSequence exterior,
            List<CoordinateSequence> holes,
            double queryX,
            double queryY,
            double tolerance) {
        Objects.requireNonNull(exterior, "exterior");
        List<CoordinateSequence> holeCopy = List.copyOf(Objects.requireNonNull(holes, "holes"));
        requireFinite(queryX, queryY, tolerance);
        requireRadius(tolerance);
        if (polylineWithin(exterior, true, queryX, queryY, tolerance)) {
            return true;
        }
        for (CoordinateSequence hole : holeCopy) {
            if (polylineWithin(hole, true, queryX, queryY, tolerance)) {
                return true;
            }
        }
        boolean inside = ringContains(exterior, queryX, queryY);
        for (CoordinateSequence hole : holeCopy) {
            if (ringContains(hole, queryX, queryY)) {
                inside = !inside;
            }
        }
        return inside;
    }

    /** Tests a convex screen quad and its inclusive tolerance-expanded boundary. */
    public static boolean convexQuadWithin(
            double[] screenXy8, double queryX, double queryY, double tolerance) {
        Objects.requireNonNull(screenXy8, "screenXy8");
        if (screenXy8.length != 8) {
            throw new IllegalArgumentException("screenXy8 must contain exactly eight ordinates");
        }
        requireFinite(queryX, queryY, tolerance);
        requireRadius(tolerance);
        for (double ordinate : screenXy8) {
            if (!Double.isFinite(ordinate)) {
                throw new IllegalArgumentException("Quad ordinates must be finite");
            }
        }
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) % 4;
            if (segmentWithin(
                    screenXy8[index * 2],
                    screenXy8[index * 2 + 1],
                    screenXy8[next * 2],
                    screenXy8[next * 2 + 1],
                    queryX,
                    queryY,
                    tolerance)) {
                return true;
            }
        }
        boolean sign = false;
        boolean signSet = false;
        for (int index = 0; index < 4; index++) {
            int next = (index + 1) % 4;
            double ax = screenXy8[index * 2];
            double ay = screenXy8[index * 2 + 1];
            double bx = screenXy8[next * 2];
            double by = screenXy8[next * 2 + 1];
            double scale = maximumMagnitude(ax, ay, bx, by, queryX, queryY);
            double cross =
                    ((bx / scale) - (ax / scale)) * ((queryY / scale) - (ay / scale))
                            - ((by / scale) - (ay / scale)) * ((queryX / scale) - (ax / scale));
            if (cross != 0.0) {
                boolean current = cross > 0.0;
                if (signSet && current != sign) {
                    return false;
                }
                sign = current;
                signSet = true;
            }
        }
        return signSet;
    }

    private static boolean segmentWithin(
            double ax,
            double ay,
            double bx,
            double by,
            double queryX,
            double queryY,
            double radius) {
        double scale = maximumMagnitude(ax, ay, bx, by, queryX, queryY);
        double normalizedAx = ax / scale;
        double normalizedAy = ay / scale;
        double dx = (bx / scale) - normalizedAx;
        double dy = (by / scale) - normalizedAy;
        double qx = (queryX / scale) - normalizedAx;
        double qy = (queryY / scale) - normalizedAy;
        double lengthSquared = dx * dx + dy * dy;
        double fraction = lengthSquared == 0.0 ? 0.0 : (qx * dx + qy * dy) / lengthSquared;
        fraction = Math.max(0.0, Math.min(1.0, fraction));
        double normalizedRadius = radius / scale;
        double normalizedDistance = Math.hypot(qx - fraction * dx, qy - fraction * dy);
        double roundingSlack =
                Math.max(Math.ulp(normalizedRadius), Math.ulp(normalizedDistance)) * 8.0;
        return normalizedDistance <= normalizedRadius + roundingSlack;
    }

    private static boolean ringContains(CoordinateSequence ring, double queryX, double queryY) {
        boolean inside = false;
        for (int current = 0, previous = ring.size() - 1;
                current < ring.size();
                previous = current++) {
            double currentY = ring.y(current);
            double previousY = ring.y(previous);
            if ((currentY > queryY) != (previousY > queryY)) {
                double scale =
                        maximumMagnitude(
                                ring.x(current),
                                currentY,
                                ring.x(previous),
                                previousY,
                                queryX,
                                queryY);
                double intersection =
                        (ring.x(previous) / scale)
                                + ((queryY / scale) - (previousY / scale))
                                        * ((ring.x(current) / scale) - (ring.x(previous) / scale))
                                        / ((currentY / scale) - (previousY / scale));
                if ((queryX / scale) < intersection) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private static double maximumMagnitude(double... values) {
        double maximum = 1.0;
        for (double value : values) {
            if (!Double.isFinite(value)) {
                throw new IllegalArgumentException("Screen geometry values must be finite");
            }
            maximum = Math.max(maximum, Math.abs(value));
        }
        return maximum;
    }

    private static void requireFinite(double... values) {
        maximumMagnitude(values);
    }

    private static void requireRadius(double radius) {
        if (radius < 0.0) {
            throw new IllegalArgumentException("Radius or tolerance must not be negative");
        }
    }
}
