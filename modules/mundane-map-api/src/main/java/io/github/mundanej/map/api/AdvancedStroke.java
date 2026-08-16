package io.github.mundanej.map.api;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable advanced stroke with cap, join, dash, offset, and optional graphic paint.
 *
 * @param color base stroke color
 * @param width positive width and unit
 * @param cap endpoint cap
 * @param join segment join
 * @param dashArray positive dash/gap lengths in width units, at most 64 entries
 * @param dashOffset finite offset into the dash cycle in width units
 * @param perpendicularOffset finite signed offset in width units
 * @param graphicStroke optional repeated marker paint
 */
public record AdvancedStroke(
        Rgba color,
        SymbolLength width,
        Cap cap,
        Join join,
        List<Double> dashArray,
        double dashOffset,
        double perpendicularOffset,
        Optional<GraphicPaint> graphicStroke) {
    /** Stroke endpoint caps. */
    public enum Cap {
        /** Flat cap at the endpoint. */
        BUTT,
        /** Semicircular cap centered on the endpoint. */
        ROUND,
        /** Square cap extending half a width. */
        SQUARE
    }

    /** Stroke segment joins. */
    public enum Join {
        /** Mitered join subject to renderer miter limits. */
        MITER,
        /** Rounded join. */
        ROUND,
        /** Beveled join. */
        BEVEL
    }

    /** Creates and validates an advanced stroke. */
    public AdvancedStroke {
        Objects.requireNonNull(color, "color");
        Objects.requireNonNull(width, "width");
        Objects.requireNonNull(cap, "cap");
        Objects.requireNonNull(join, "join");
        dashArray = List.copyOf(Objects.requireNonNull(dashArray, "dashArray"));
        if (dashArray.size() > 64) {
            throw new IllegalArgumentException("dashArray must contain at most 64 entries");
        }
        for (Double dash : dashArray) {
            if (dash == null || !Double.isFinite(dash) || dash <= 0) {
                throw new IllegalArgumentException("dashArray entries must be finite and positive");
            }
        }
        if (!Double.isFinite(dashOffset) || !Double.isFinite(perpendicularOffset)) {
            throw new IllegalArgumentException("stroke offsets must be finite");
        }
        graphicStroke =
                Objects.requireNonNull(graphicStroke, "graphicStroke").map(Objects::requireNonNull);
        dashOffset = dashOffset == 0.0 ? 0.0 : dashOffset;
        perpendicularOffset = perpendicularOffset == 0.0 ? 0.0 : perpendicularOffset;
    }

    /**
     * Creates a solid advanced stroke retaining explicit cap and join defaults.
     *
     * @param color stroke color
     * @param width positive width
     * @return solid butt-cap miter-join stroke
     */
    public static AdvancedStroke solid(Rgba color, SymbolLength width) {
        return new AdvancedStroke(
                color, width, Cap.BUTT, Join.MITER, List.of(), 0, 0, Optional.empty());
    }
}
