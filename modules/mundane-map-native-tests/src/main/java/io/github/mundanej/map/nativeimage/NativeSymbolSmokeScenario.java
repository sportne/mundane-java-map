package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.BuiltInMarkers;
import java.util.List;
import java.util.Objects;

final class NativeSymbolSmokeScenario {
    static final Rgba VECTOR_GREEN = Rgba.rgb(36, 144, 94);
    static final Rgba COMPOSITE_BLUE = Rgba.rgb(40, 90, 210);
    static final Rgba COMPOSITE_YELLOW = Rgba.rgb(245, 190, 30);
    static final Envelope VECTOR_VIEW_BOX = new Envelope(-0.5, -0.5, 0.5, 0.5);

    private final VectorPath vectorPath;
    private final CompositeSymbol composite;
    private final int[] rasterPixels;

    private NativeSymbolSmokeScenario(
            VectorPath vectorPath, CompositeSymbol composite, int[] rasterPixels) {
        this.vectorPath = Objects.requireNonNull(vectorPath, "vectorPath");
        this.composite = Objects.requireNonNull(composite, "composite");
        this.rasterPixels = Objects.requireNonNull(rasterPixels, "rasterPixels").clone();
    }

    static NativeSymbolSmokeScenario standard(int[] rasterPixels) {
        VectorPath path =
                VectorPath.builder()
                        .moveTo(-0.5, 0.5)
                        .lineTo(-0.5, 0.0)
                        .quadraticTo(-0.5, -0.5, 0.0, -0.5)
                        .cubicTo(0.3, -0.5, 0.5, -0.3, 0.5, 0.0)
                        .lineTo(0.5, 0.5)
                        .close()
                        .build();
        CompositeSymbol composite =
                CompositeSymbol.of(
                        List.of(
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.SQUARE, COMPOSITE_BLUE, 28.0, 1.0),
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.DIAMOND, COMPOSITE_YELLOW, 14.0, 1.0)),
                        1.0);
        return new NativeSymbolSmokeScenario(path, composite, rasterPixels);
    }

    NamedSymbolCatalog catalog() {
        VectorMarkerSymbol vector =
                VectorMarkerSymbol.filledScreen(
                        vectorPath, VECTOR_VIEW_BOX, VECTOR_GREEN, 32.0, 1.0);
        RasterIconSymbol raster =
                RasterIconSymbol.screenWidth(
                        4, 2, rasterPixels, 32.0, RasterInterpolation.NEAREST, 1.0);
        return NamedSymbolCatalog.of(
                List.of(
                        new NamedSymbol("vector", vector),
                        new NamedSymbol("composite", composite),
                        new NamedSymbol("raster", raster)));
    }

    NativeSymbolSmokeScenario withVectorPath(VectorPath replacement) {
        return new NativeSymbolSmokeScenario(replacement, composite, rasterPixels);
    }

    NativeSymbolSmokeScenario withComposite(CompositeSymbol replacement) {
        return new NativeSymbolSmokeScenario(vectorPath, replacement, rasterPixels);
    }

    NativeSymbolSmokeScenario withRasterPixels(int[] replacement) {
        return new NativeSymbolSmokeScenario(vectorPath, composite, replacement);
    }

    VectorPath vectorPath() {
        return vectorPath;
    }

    CompositeSymbol composite() {
        return composite;
    }

    int[] rasterPixels() {
        return rasterPixels.clone();
    }
}
