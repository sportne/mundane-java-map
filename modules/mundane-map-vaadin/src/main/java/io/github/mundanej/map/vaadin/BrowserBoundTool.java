package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.Layer;
import java.util.List;

/** Package-private host and overlay contract for browser-specific toolkit-neutral tools. */
interface BrowserBoundTool {
    boolean belongsTo(MundaneMap candidate);

    List<Layer> overlayLayers();
}
