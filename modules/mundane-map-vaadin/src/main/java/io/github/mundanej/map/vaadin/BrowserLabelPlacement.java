package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.LabelPlacementException;
import io.github.mundanej.map.api.PlacedPointLabel;
import io.github.mundanej.map.api.PointLabelAnchorBasis;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.ScreenBox;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.GreedyPointLabelPlacement;
import io.github.mundanej.map.core.MapScreenBasis;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.PointLabelPlacementRequest;
import io.github.mundanej.map.core.SymbolTransforms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Validates browser metrics and delegates deterministic placement to the existing core pass. */
final class BrowserLabelPlacement {
    static final int METRIC_VALUES = 5;
    private static final double MAXIMUM_METRIC_MAGNITUDE = 1_000_000.0;

    private BrowserLabelPlacement() {}

    static List<PlacedPointLabel> place(
            List<SceneLabelCandidate> candidates, double[] metrics, MapViewport viewport) {
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(viewport, "viewport");
        if (metrics.length != Math.multiplyExact(candidates.size(), METRIC_VALUES)) {
            throw failure(
                    MundaneMapException.LIMIT_EXCEEDED,
                    "Browser label metric count does not match the pending candidates",
                    "limit",
                    "labelMetrics");
        }
        MapScreenBasis basis =
                MapScreenBasis.of(
                        new Coordinate(1.0 / viewport.worldUnitsPerPixel(), 0),
                        new Coordinate(0, -1.0 / viewport.worldUnitsPerPixel()));
        List<PointLabelPlacementRequest> requests = new ArrayList<>(candidates.size());
        for (int index = 0; index < candidates.size(); index++) {
            SceneLabelCandidate retained = candidates.get(index);
            BrowserLabelCandidate candidate = retained.candidate();
            int offset = index * METRIC_VALUES;
            double advance = metric(metrics[offset], "advance");
            double minimumX = metric(metrics[offset + 1], "visualBounds");
            double minimumY = metric(metrics[offset + 2], "visualBounds");
            double maximumX = metric(metrics[offset + 3], "visualBounds");
            double maximumY = metric(metrics[offset + 4], "visualBounds");
            if (advance < 0.0 || maximumX < minimumX || maximumY < minimumY) {
                throw failure(
                        MundaneMapException.UNSUPPORTED_VALUE,
                        "Browser label metrics are outside the closed profile",
                        "valueKind",
                        "labelMetrics");
            }
            Coordinate anchor = screen(candidate.mapAnchor(), viewport);
            ScreenBox anchorBounds =
                    candidate.profile().anchorBasis() == PointLabelAnchorBasis.FEATURE_POINT
                            ? new ScreenBox(anchor.x(), anchor.y(), anchor.x(), anchor.y())
                            : markerBounds(candidate.marker(), anchor, basis);
            requests.add(
                    new PointLabelPlacementRequest(
                            candidate.layerId(),
                            candidate.featureId(),
                            candidate.text(),
                            candidate.profile().style(),
                            anchorBounds,
                            new ScreenBox(minimumX, minimumY, maximumX, maximumY),
                            advance,
                            candidate.profile(),
                            retained.layerIndex(),
                            candidate.featureIndex(),
                            retained.ordinaryPaintOrdinal()));
        }
        try {
            return GreedyPointLabelPlacement.place(
                    new ScreenBox(0, 0, viewport.width(), viewport.height()), requests);
        } catch (LabelPlacementException exception) {
            throw new MundaneMapException(
                    exception.problem().code(),
                    exception.problem().message(),
                    exception.problem().context());
        }
    }

    private static ScreenBox markerBounds(Symbol symbol, Coordinate anchor, MapScreenBasis basis) {
        Envelope bounds;
        if (symbol instanceof VectorMarkerSymbol marker) {
            bounds =
                    SymbolTransforms.marker(marker.viewBox(), marker.placement(), anchor, basis)
                            .nominalScreenBounds();
        } else if (symbol instanceof RasterIconSymbol icon) {
            bounds =
                    SymbolTransforms.marker(
                                    new Envelope(0, 0, icon.width(), icon.height()),
                                    icon.placement(),
                                    anchor,
                                    basis)
                            .nominalScreenBounds();
        } else {
            CompositeSymbol composite = (CompositeSymbol) symbol;
            Envelope aggregate = null;
            for (Symbol child : composite.children()) {
                ScreenBox childBounds = markerBounds(child, anchor, basis);
                Envelope envelope =
                        new Envelope(
                                childBounds.minX(),
                                childBounds.minY(),
                                childBounds.maxX(),
                                childBounds.maxY());
                aggregate = aggregate == null ? envelope : aggregate.union(envelope);
            }
            bounds = aggregate == null ? Envelope.at(anchor) : aggregate;
        }
        return new ScreenBox(bounds.minX(), bounds.minY(), bounds.maxX(), bounds.maxY());
    }

    private static Coordinate screen(Coordinate coordinate, MapViewport viewport) {
        try {
            return new Coordinate(
                    viewport.width() / 2.0
                            + (coordinate.x() - viewport.centerX()) / viewport.worldUnitsPerPixel(),
                    viewport.height() / 2.0
                            - (coordinate.y() - viewport.centerY())
                                    / viewport.worldUnitsPerPixel());
        } catch (IllegalArgumentException exception) {
            throw failure(
                    MundaneMapException.NON_FINITE_VALUE,
                    "Point-label anchor is not finite in the browser viewport",
                    "quantity",
                    "labelAnchor");
        }
    }

    private static double metric(double value, String quantity) {
        if (!Double.isFinite(value)) {
            throw failure(
                    MundaneMapException.NON_FINITE_VALUE,
                    "Browser label metrics are non-finite",
                    "quantity",
                    quantity);
        }
        if (StrictMath.abs(value) > MAXIMUM_METRIC_MAGNITUDE) {
            throw failure(
                    MundaneMapException.LIMIT_EXCEEDED,
                    "Browser label metric magnitude exceeds the closed limit",
                    "limit",
                    "labelMetricMagnitude");
        }
        return value == 0.0 ? 0.0 : value;
    }

    private static MundaneMapException failure(
            String code, String message, String contextName, String contextValue) {
        return new MundaneMapException(code, message, Map.of(contextName, contextValue));
    }
}
