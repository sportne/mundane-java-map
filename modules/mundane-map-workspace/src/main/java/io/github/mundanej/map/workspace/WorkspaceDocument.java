package io.github.mundanej.map.workspace;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable ordered portable workspace document.
 *
 * @param view component-independent viewport state
 * @param layers ordered layer definitions in paint order
 */
public record WorkspaceDocument(WorkspaceViewState view, List<WorkspaceLayerDefinition> layers) {
    /** Defensively copies layers and validates identity and model-size bounds. */
    public WorkspaceDocument {
        Objects.requireNonNull(view, "view");
        layers = List.copyOf(Objects.requireNonNull(layers, "layers"));
        if (layers.size() > WorkspaceText.MAX_LAYERS) {
            throw new IllegalArgumentException("workspace has more than 4096 layers");
        }
        Set<String> ids = new HashSet<>();
        for (WorkspaceLayerDefinition layer : layers) {
            Objects.requireNonNull(layer, "layer");
            if (!ids.add(layer.id())) {
                throw new IllegalArgumentException("workspace layer IDs must be unique");
            }
        }
        long bytes = logicalModelBytes(view, layers);
        if (bytes > WorkspaceText.MAX_MODEL_BYTES) {
            throw new IllegalArgumentException("workspace exceeds the fixed model-size ceiling");
        }
    }

    static long logicalModelBytes(WorkspaceViewState view, List<WorkspaceLayerDefinition> layers) {
        long bytes = 128L;
        bytes = WorkspaceText.add(bytes, WorkspaceText.stringBytes(view.mapCrsKey()));
        bytes = WorkspaceText.add(bytes, WorkspaceText.stringBytes(view.displayCrsKey()));
        bytes = WorkspaceText.add(bytes, Math.multiplyExact((long) layers.size(), 8L));
        for (WorkspaceLayerDefinition layer : layers) {
            bytes = WorkspaceText.add(bytes, 256L);
            bytes = WorkspaceText.add(bytes, WorkspaceText.stringBytes(layer.id()));
            bytes = WorkspaceText.add(bytes, WorkspaceText.stringBytes(layer.name()));
            WorkspaceSourceReference source = layer.source();
            bytes = WorkspaceText.add(bytes, WorkspaceText.stringBytes(source.openerId()));
            bytes = WorkspaceText.add(bytes, WorkspaceText.stringBytes(source.identity().id()));
            bytes =
                    WorkspaceText.add(
                            bytes, WorkspaceText.stringBytes(source.identity().displayName()));
            bytes = WorkspaceText.add(bytes, WorkspaceText.stringBytes(source.path().value()));
            if (layer instanceof WorkspaceFeatureLayer feature) {
                bytes = WorkspaceText.add(bytes, 64L);
                bytes =
                        WorkspaceText.add(
                                bytes, WorkspaceText.stringBytes(feature.symbols().catalogId()));
                bytes =
                        WorkspaceText.add(
                                bytes, WorkspaceText.stringBytes(feature.symbols().markerName()));
                bytes =
                        WorkspaceText.add(
                                bytes, WorkspaceText.stringBytes(feature.symbols().lineName()));
                bytes =
                        WorkspaceText.add(
                                bytes, WorkspaceText.stringBytes(feature.symbols().fillName()));
            }
        }
        return bytes;
    }
}
