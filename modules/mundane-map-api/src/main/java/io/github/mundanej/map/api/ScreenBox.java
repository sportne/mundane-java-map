package io.github.mundanej.map.api;

/**
 * Finite half-open logical-screen rectangle with ordered edges.
 *
 * @param minX inclusive minimum logical-screen x ordinate
 * @param minY inclusive minimum logical-screen y ordinate
 * @param maxX exclusive maximum logical-screen x ordinate
 * @param maxY exclusive maximum logical-screen y ordinate
 */
public record ScreenBox(double minX, double minY, double maxX, double maxY) {
    /** Validates finite non-decreasing edges. */
    public ScreenBox {
        if (!Double.isFinite(minX)
                || !Double.isFinite(minY)
                || !Double.isFinite(maxX)
                || !Double.isFinite(maxY)
                || maxX < minX
                || maxY < minY) {
            throw new IllegalArgumentException("screen-box edges must be finite and ordered");
        }
    }

    /**
     * Returns this box translated by finite logical-pixel offsets.
     *
     * @param x horizontal translation
     * @param y vertical translation
     * @return translated box
     */
    public ScreenBox translated(double x, double y) {
        if (!Double.isFinite(x) || !Double.isFinite(y)) {
            throw new IllegalArgumentException("translation must be finite");
        }
        return new ScreenBox(minX + x, minY + y, maxX + x, maxY + y);
    }

    /**
     * Returns this box expanded equally on every side.
     *
     * @param padding finite non-negative padding
     * @return expanded box
     */
    public ScreenBox expanded(double padding) {
        if (!Double.isFinite(padding) || padding < 0.0) {
            throw new IllegalArgumentException("padding must be finite and non-negative");
        }
        return new ScreenBox(minX - padding, minY - padding, maxX + padding, maxY + padding);
    }
}
