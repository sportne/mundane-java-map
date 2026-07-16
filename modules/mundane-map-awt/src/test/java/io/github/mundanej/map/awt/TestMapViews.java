package io.github.mundanej.map.awt;

import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;

/** Canonical identity-view fixtures shared by AWT tests. */
final class TestMapViews {
    private TestMapViews() {}

    static MapView identity() {
        return new MapView(
                CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
    }

    static MapView identity(SymbolRendererRegistry renderers) {
        return new MapView(
                CrsRegistry.level1(),
                CrsDefinitions.EPSG_3857,
                CrsDefinitions.EPSG_3857,
                renderers);
    }

    static MapView identity(ScreenGeometryOptimizationMode optimizationMode) {
        return identity(SymbolRendererRegistry.builtIn(), optimizationMode);
    }

    static MapView identity(
            SymbolRendererRegistry renderers, ScreenGeometryOptimizationMode optimizationMode) {
        return new MapView(
                CrsRegistry.level1(),
                CrsDefinitions.EPSG_3857,
                CrsDefinitions.EPSG_3857,
                renderers,
                optimizationMode);
    }
}
