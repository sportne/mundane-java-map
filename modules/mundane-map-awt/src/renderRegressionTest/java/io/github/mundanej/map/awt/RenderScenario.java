package io.github.mundanej.map.awt;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

record RenderScenario(
        String id,
        int width,
        int height,
        Supplier<MapView> viewFactory,
        Consumer<MapView> postSizeSetup,
        Optional<RenderClip> inheritedClip,
        List<RenderInvariant> invariants) {
    RenderScenario {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(viewFactory, "viewFactory");
        Objects.requireNonNull(postSizeSetup, "postSizeSetup");
        Objects.requireNonNull(inheritedClip, "inheritedClip");
        Objects.requireNonNull(invariants, "invariants");
        if (id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("scenario dimensions must be positive");
        }
        invariants = List.copyOf(invariants);
        if (invariants.isEmpty()) {
            throw new IllegalArgumentException("scenario invariants must not be empty");
        }
    }
}
