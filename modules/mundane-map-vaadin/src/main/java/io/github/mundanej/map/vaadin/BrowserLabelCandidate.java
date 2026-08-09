package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.PointLabelProfile;
import io.github.mundanej.map.api.Symbol;
import java.util.Objects;

/** Server-only immutable point-label input retained beside one browser layer snapshot. */
record BrowserLabelCandidate(
        String layerId,
        String featureId,
        Coordinate mapAnchor,
        Symbol marker,
        String text,
        PointLabelProfile profile,
        int featureIndex) {
    BrowserLabelCandidate {
        Objects.requireNonNull(layerId, "layerId");
        Objects.requireNonNull(featureId, "featureId");
        Objects.requireNonNull(mapAnchor, "mapAnchor");
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(profile, "profile");
        if (featureIndex < 0) {
            throw new IllegalArgumentException("featureIndex must be non-negative");
        }
    }
}
