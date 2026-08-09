package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.Layer;
import java.util.ArrayList;
import java.util.List;

/** Internal visual-copy metadata retained beside ordinary immutable layers. */
interface BrowserLogicalLayer extends Layer {
    String logicalFeatureId(int featureIndex);

    long copyIndex(int featureIndex);

    static String logicalFeatureId(Layer layer, int featureIndex) {
        return layer instanceof BrowserLogicalLayer logical
                ? logical.logicalFeatureId(featureIndex)
                : layer.features().get(featureIndex).id();
    }

    static long copyIndex(Layer layer, int featureIndex) {
        return layer instanceof BrowserLogicalLayer logical ? logical.copyIndex(featureIndex) : 0L;
    }

    static List<Feature> matchingFeatures(
            List<? extends Layer> layers, String layerId, String logicalFeatureId) {
        List<Feature> matches = new ArrayList<>();
        for (Layer layer : layers) {
            if (!layer.id().equals(layerId)) {
                continue;
            }
            for (int featureIndex = 0; featureIndex < layer.features().size(); featureIndex++) {
                if (logicalFeatureId(layer, featureIndex).equals(logicalFeatureId)) {
                    matches.add(layer.features().get(featureIndex));
                }
            }
        }
        return List.copyOf(matches);
    }
}
