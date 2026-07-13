package io.github.mundanej.map.example.symbols;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolUnit;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Immutable enum coverage declared by one gallery case. */
record GalleryCoverage(
        Set<BuiltInMarker> markers,
        Set<SymbolAnchor> anchors,
        Set<SymbolUnit> units,
        Set<SymbolRotationMode> rotationModes,
        Set<RasterInterpolation> interpolations,
        Set<HatchPattern> hatchPatterns) {
    GalleryCoverage {
        markers = immutable(markers);
        anchors = immutable(anchors);
        units = immutable(units);
        rotationModes = immutable(rotationModes);
        interpolations = immutable(interpolations);
        hatchPatterns = immutable(hatchPatterns);
    }

    static GalleryCoverage none() {
        return new GalleryCoverage(Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of());
    }

    private static <E extends Enum<E>> Set<E> immutable(Set<E> values) {
        if (values.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }
}
