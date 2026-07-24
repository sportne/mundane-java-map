package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.SymbolRendererKey;
import java.util.Objects;

/**
 * Internal detached marker carrying a symbol declaration until explicit catalog binding.
 *
 * <p>The renderer key is intentionally unavailable to built-in renderer registries. Callers must
 * bind the containing style before rendering.
 */
final class MapLibreDeferredSymbol implements MarkerSymbol {
    private static final SymbolRendererKey RENDERER_KEY =
            new SymbolRendererKey("io.github.mundanej.map.maplibre.deferred-symbol");

    private final MapLibreSymbolSpec spec;

    MapLibreDeferredSymbol(MapLibreSymbolSpec spec) {
        this.spec = Objects.requireNonNull(spec, "spec");
    }

    MapLibreSymbolSpec spec() {
        return spec;
    }

    @Override
    public SymbolRendererKey rendererKey() {
        return RENDERER_KEY;
    }

    @Override
    public double opacity() {
        return 1;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof MapLibreDeferredSymbol deferred && spec.equals(deferred.spec);
    }

    @Override
    public int hashCode() {
        return spec.hashCode();
    }

    @Override
    public String toString() {
        return "MapLibreDeferredSymbol[spec=" + spec + ']';
    }
}
