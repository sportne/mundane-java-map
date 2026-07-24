package io.github.mundanej.map.io.maplibre.style;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.AttributeValueConversion;
import io.github.mundanej.map.api.CategoricalSymbolRule;
import io.github.mundanej.map.api.CategoricalSymbolSelector;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FilteredSymbolSelector;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PortrayalRule;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RulePortrayalPlan;
import io.github.mundanej.map.api.ScaleInterval;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolSelector;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.ThematicValue;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.FeaturePortrayalResolver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicit all-or-nothing binder for detached styles and caller-owned feature sources. */
public final class MapLibreStyleBinder {
    private MapLibreStyleBinder() {}

    /**
     * Resolves every ordinary layer source before publishing an immutable borrowed binding.
     *
     * <p>A style containing a visible symbol layer requires the overload that supplies an explicit
     * caller-owned catalog.
     *
     * @param style detached style
     * @param registry exact explicit source registry
     * @return declaration-ordered binding
     * @throws MapLibreBindException when a source is missing or closed, or a symbol icon cannot be
     *     resolved from the empty catalog
     */
    public static MapLibreStyleBinding bind(MapLibreStyle style, MapLibreSourceRegistry registry) {
        return bind(style, registry, NamedSymbolCatalog.of(List.of()));
    }

    /**
     * Resolves every layer source and symbol icon before publishing an immutable borrowed binding.
     *
     * @param style detached style
     * @param registry exact explicit source registry
     * @param catalog exact caller-owned symbol catalog
     * @return declaration-ordered binding
     * @throws MapLibreBindException when a source or icon is unresolved, closed, or incompatible
     */
    public static MapLibreStyleBinding bind(
            MapLibreStyle style, MapLibreSourceRegistry registry, NamedSymbolCatalog catalog) {
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(catalog, "catalog");
        List<IndexedLayer> candidates =
                java.util.stream.IntStream.range(0, style.layers().size())
                        .mapToObj(index -> new IndexedLayer(index, style.layers().get(index)))
                        .filter(indexed -> indexed.layer().portrayal().isPresent())
                        .toList();

        ArrayList<FeatureSource> resolvedSources = new ArrayList<>(candidates.size());
        for (IndexedLayer indexed : candidates) {
            String location = "/layers/" + indexed.index() + "/source";
            FeatureSource source =
                    registry.find(indexed.layer().source())
                            .orElseThrow(
                                    () ->
                                            failure(
                                                    "MAPLIBRE_SOURCE_UNRESOLVED",
                                                    location,
                                                    "missing"));
            if (source.isClosed()) {
                throw failure("MAPLIBRE_SOURCE_UNRESOLVED", location, "closed");
            }
            resolvedSources.add(source);
        }

        ReferenceBudget referenceBudget = new ReferenceBudget();
        ArrayList<FeaturePortrayal> portrayals = new ArrayList<>(candidates.size());
        for (IndexedLayer indexed : candidates) {
            FeaturePortrayal portrayal;
            try {
                portrayal =
                        indexed.layer().type() == MapLibreLayerType.SYMBOL
                                ? materialize(
                                        deferredSpec(indexed.layer(), indexed.index()),
                                        catalog,
                                        indexed.index(),
                                        referenceBudget)
                                : indexed.layer().portrayal().orElseThrow();
            } catch (MapLibreBindException failure) {
                throw failure;
            } catch (IllegalArgumentException failure) {
                throw failure(
                        "MAPLIBRE_RENDERER_UNAVAILABLE",
                        "/layers/" + indexed.index() + "/layout/icon-image",
                        "invalidCatalogIcon");
            }
            portrayals.add(portrayal);
        }

        ArrayList<MapLibreBoundLayer> bound = new ArrayList<>(candidates.size());
        java.util.IdentityHashMap<FeatureSource, SingularPointFeatureSource> pointSources =
                new java.util.IdentityHashMap<>();
        for (int index = 0; index < candidates.size(); index++) {
            MapLibreLayer layer = candidates.get(index).layer();
            FeaturePortrayal portrayal = portrayals.get(index);
            List<String> required =
                    FeaturePortrayalResolver.compile(portrayal).requiredConfigurationAttributes();
            AttributeSelection attributes =
                    required.isEmpty()
                            ? AttributeSelection.NONE
                            : AttributeSelection.only(required);
            FeatureSource source =
                    layer.type() == MapLibreLayerType.SYMBOL
                            ? pointSources.computeIfAbsent(
                                    resolvedSources.get(index), SingularPointFeatureSource::new)
                            : resolvedSources.get(index);
            bound.add(
                    new MapLibreBoundLayer(
                            layer.id(),
                            source,
                            Optional.of(portrayal),
                            attributes,
                            layer.minimumZoom(),
                            layer.maximumZoom()));
        }
        return new MapLibreStyleBinding(bound);
    }

    private static MapLibreSymbolSpec deferredSpec(MapLibreLayer layer, int layerIndex) {
        String location = "/layers/" + layerIndex + "/layout/icon-image";
        if (layer.portrayal().isEmpty()
                || layer.portrayal().orElseThrow().marker().isEmpty()
                || !(layer.portrayal().orElseThrow().marker().orElseThrow()
                        instanceof FixedSymbolSelector fixed)
                || !(fixed.symbol() instanceof MapLibreDeferredSymbol deferred)) {
            throw failure("MAPLIBRE_RENDERER_UNAVAILABLE", location, "detachedSymbol");
        }
        return deferred.spec();
    }

    private static FeaturePortrayal materialize(
            MapLibreSymbolSpec spec,
            NamedSymbolCatalog catalog,
            int layerIndex,
            ReferenceBudget referenceBudget) {
        String location = "/layers/" + layerIndex + "/layout/icon-image";
        int references =
                spec.icon() instanceof MapLibreSymbolSpec.IconExpression.Attribute
                        ? catalog.size()
                        : spec.catalogReferences();
        referenceBudget.reserve(references, location, spec.maximumCatalogReferences());
        SymbolSelector selector =
                switch (spec.icon()) {
                    case MapLibreSymbolSpec.IconExpression.Literal literal ->
                            new FixedSymbolSelector(icon(catalog, literal.name(), spec, location));
                    case MapLibreSymbolSpec.IconExpression.Attribute attribute ->
                            dynamicCatalog(catalog, attribute, spec, location);
                    case MapLibreSymbolSpec.IconExpression.Match match ->
                            match(catalog, match, spec, location);
                    case MapLibreSymbolSpec.IconExpression.Case conditional ->
                            conditional(catalog, conditional, spec, location);
                };
        if (spec.filter().isPresent()) {
            selector = new FilteredSymbolSelector(spec.filter().orElseThrow(), selector);
        }
        return new FeaturePortrayal(
                Optional.of(selector), Optional.empty(), Optional.empty(), spec.pointLabel());
    }

    private static SymbolSelector dynamicCatalog(
            NamedSymbolCatalog catalog,
            MapLibreSymbolSpec.IconExpression.Attribute attribute,
            MapLibreSymbolSpec spec,
            String location) {
        ArrayList<CategoricalSymbolRule> rules = new ArrayList<>(catalog.size());
        for (NamedSymbol entry : catalog) {
            rules.add(
                    new CategoricalSymbolRule(
                            ThematicValue.text(entry.name()),
                            icon(catalog, entry.name(), spec, location)));
        }
        if (rules.isEmpty()) {
            throw failure("MAPLIBRE_ICON_UNRESOLVED", location, "emptyCatalog");
        }
        AttributeValueConversion conversion =
                attribute.stringify()
                        ? AttributeValueConversion.TO_STRING
                        : AttributeValueConversion.IDENTITY;
        return CategoricalSymbolSelector.expressionInput(
                attribute.attribute(), rules, Optional.empty(), conversion);
    }

    private static SymbolSelector match(
            NamedSymbolCatalog catalog,
            MapLibreSymbolSpec.IconExpression.Match match,
            MapLibreSymbolSpec spec,
            String location) {
        ArrayList<CategoricalSymbolRule> rules = new ArrayList<>(match.rules().size());
        for (MapLibreSymbolSpec.MatchRule rule : match.rules()) {
            rules.add(
                    new CategoricalSymbolRule(
                            rule.value(), icon(catalog, rule.iconName(), spec, location)));
        }
        return CategoricalSymbolSelector.expressionInput(
                match.attribute(),
                rules,
                Optional.of(icon(catalog, match.fallback(), spec, location)),
                AttributeValueConversion.IDENTITY);
    }

    private static SymbolSelector conditional(
            NamedSymbolCatalog catalog,
            MapLibreSymbolSpec.IconExpression.Case conditional,
            MapLibreSymbolSpec spec,
            String location) {
        ArrayList<PortrayalRule> rules = new ArrayList<>(conditional.rules().size() + 1);
        for (MapLibreSymbolSpec.CaseRule rule : conditional.rules()) {
            rules.add(
                    new PortrayalRule(
                            Optional.empty(),
                            ScaleInterval.ALL,
                            Optional.of(rule.predicate()),
                            false,
                            List.of(icon(catalog, rule.iconName(), spec, location)),
                            List.of(),
                            List.of()));
        }
        rules.add(
                new PortrayalRule(
                        Optional.empty(),
                        ScaleInterval.ALL,
                        Optional.empty(),
                        true,
                        List.of(icon(catalog, conditional.fallback(), spec, location)),
                        List.of(),
                        List.of()));
        return new RulePortrayalPlan(rules).portrayal().marker().orElseThrow();
    }

    private static Symbol icon(
            NamedSymbolCatalog catalog, String name, MapLibreSymbolSpec spec, String location) {
        Symbol source =
                catalog.find(name)
                        .orElseThrow(
                                () -> failure("MAPLIBRE_ICON_UNRESOLVED", location, "missing"));
        if (source instanceof VectorMarkerSymbol vector) {
            requireScreenPlacement(vector.placement(), location);
            return VectorMarkerSymbol.of(
                    vector.path(),
                    vector.viewBox(),
                    vector.fill(),
                    vector.stroke(),
                    placement(
                            vector.placement().size().width(),
                            vector.placement().size().height(),
                            spec),
                    vector.opacity() * spec.opacity());
        }
        if (source instanceof RasterIconSymbol raster) {
            requireScreenPlacement(raster.placement(), location);
            return RasterIconSymbol.of(
                    raster.width(),
                    raster.height(),
                    raster.toRgbaArray(),
                    placement(
                            raster.placement().size().width(),
                            raster.placement().size().height(),
                            spec),
                    raster.interpolation(),
                    raster.opacity() * spec.opacity());
        }
        throw failure("MAPLIBRE_RENDERER_UNAVAILABLE", location, "iconType");
    }

    private static void requireScreenPlacement(MarkerPlacement placement, String location) {
        if (placement.size().unit() != SymbolUnit.SCREEN_PIXEL) {
            throw failure("MAPLIBRE_RENDERER_UNAVAILABLE", location, "mapUnitIcon");
        }
    }

    private static MarkerPlacement placement(
            double intrinsicWidth, double intrinsicHeight, MapLibreSymbolSpec spec) {
        return new MarkerPlacement(
                new SymbolSize(
                        intrinsicWidth * spec.size(),
                        intrinsicHeight * spec.size(),
                        SymbolUnit.SCREEN_PIXEL),
                spec.anchor(),
                spec.offsetX(),
                spec.offsetY(),
                spec.rotationDegrees(),
                spec.rotationMode());
    }

    private static MapLibreBindException failure(String code, String location, String reason) {
        return new MapLibreBindException(
                new MapLibreProblem(code, "bind", location, Map.of("reason", reason)));
    }

    private static MapLibreBindException limit(String location, long actual, long maximum) {
        return new MapLibreBindException(
                new MapLibreProblem(
                        "MAPLIBRE_LIMIT_EXCEEDED",
                        "bind",
                        location,
                        Map.of(
                                "limit", "catalogReferences",
                                "actual", Long.toString(actual),
                                "maximum", Long.toString(maximum))));
    }

    private static final class ReferenceBudget {
        private int maximum = -1;
        private int actual;

        private void reserve(int increment, String location, int declaredMaximum) {
            if (maximum < 0) {
                maximum = declaredMaximum;
            }
            if (declaredMaximum != maximum) {
                throw failure("MAPLIBRE_RENDERER_UNAVAILABLE", location, "limitMismatch");
            }
            long attempted = (long) actual + increment;
            if (attempted > maximum) {
                throw limit(location, attempted, maximum);
            }
            actual = (int) attempted;
        }
    }

    private record IndexedLayer(int index, MapLibreLayer layer) {}
}
