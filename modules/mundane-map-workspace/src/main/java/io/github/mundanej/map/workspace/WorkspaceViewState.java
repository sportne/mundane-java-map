package io.github.mundanej.map.workspace;

import java.util.Set;

/**
 * Immutable portable viewport state independent of component dimensions.
 *
 * @param mapCrsKey exact Level 1 map-coordinate CRS key
 * @param displayCrsKey exact Level 1 display CRS key
 * @param centerX display-space viewport center x
 * @param centerY display-space viewport center y
 * @param unitsPerPixel positive display units per logical screen pixel
 */
public record WorkspaceViewState(
        String mapCrsKey,
        String displayCrsKey,
        double centerX,
        double centerY,
        double unitsPerPixel) {
    private static final Set<String> CRS_KEYS = Set.of("EPSG:4326", "EPSG:3857");

    /** Validates the exact Level 1 CRS and finite viewport profile. */
    public WorkspaceViewState {
        WorkspaceText.text(mapCrsKey, "mapCrsKey", 9, true);
        WorkspaceText.text(displayCrsKey, "displayCrsKey", 9, true);
        if (!CRS_KEYS.contains(mapCrsKey) || !CRS_KEYS.contains(displayCrsKey)) {
            throw new IllegalArgumentException("workspace CRS keys must be EPSG:4326 or EPSG:3857");
        }
        centerX = WorkspaceText.finite(centerX, "centerX");
        centerY = WorkspaceText.finite(centerY, "centerY");
        unitsPerPixel = WorkspaceText.positive(unitsPerPixel, "unitsPerPixel");
    }
}
