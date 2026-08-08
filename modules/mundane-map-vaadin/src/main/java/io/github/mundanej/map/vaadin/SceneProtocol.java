package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.MapViewport;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Package-private encoder for protocol version one. */
final class SceneProtocol {
    static final int VERSION = 1;
    static final Limits DEFAULT_LIMITS =
            new Limits(64, 50_000, 200_000, 2_000_000, 2_000_000, 64L * 1024 * 1024, 4096);
    private static final int ID_CODE_UNITS = 256;

    private final Limits limits;

    SceneProtocol(Limits limits) {
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    void validateViewport(MapViewport viewport) {
        Objects.requireNonNull(viewport, "viewport");
        requireLimit("canvasWidth", viewport.width(), 16_384);
        requireLimit("canvasHeight", viewport.height(), 16_384);
    }

    Result encode(
            List<? extends Layer> sourceLayers,
            Rgba background,
            MapViewport viewport,
            long componentGeneration,
            long sceneGeneration) {
        return encode(sourceLayers, background, viewport, componentGeneration, sceneGeneration, 0);
    }

    Result encode(
            List<? extends Layer> sourceLayers,
            Rgba background,
            MapViewport viewport,
            long componentGeneration,
            long sceneGeneration,
            long viewportGeneration) {
        Objects.requireNonNull(sourceLayers, "sourceLayers");
        Objects.requireNonNull(background, "background");
        validateViewport(viewport);
        requireLimit("layers", sourceLayers.size(), limits.layers());
        Budget budget = new Budget(limits.logicalBytes());
        budget.addNumbers(4);
        budget.addArrayOfNumbers(4);
        budget.addNumbers(5);
        budget.add(Integer.BYTES);
        Set<String> layerIds = new LinkedHashSet<>();
        List<Layer> copies = new ArrayList<>(sourceLayers.size());
        List<Map<String, Object>> encodedLayers = new ArrayList<>(sourceLayers.size());
        Optional<Envelope> envelope = Optional.empty();
        long featureCount = 0;
        long primitiveCount = 0;
        long coordinatePairs = 0;
        long pathCommands = 0;
        for (Layer layer : sourceLayers) {
            Objects.requireNonNull(layer, "layer");
            String layerId = requireText(layer.id(), "layerId", budget);
            if (!layerIds.add(layerId)) {
                throw failure(
                        MundaneMapException.DUPLICATE_ID,
                        "Duplicate layer identity",
                        "identityNamespace",
                        "layer");
            }
            String layerName = requireTextValue(layer.name(), "layerName", budget);
            List<Feature> features =
                    List.copyOf(Objects.requireNonNull(layer.features(), "features"));
            budget.add(Integer.BYTES);
            featureCount =
                    addAndCheck("features", featureCount, features.size(), limits.features());
            Set<String> featureIds = new LinkedHashSet<>();
            List<Feature> featureCopies = new ArrayList<>(features.size());
            List<Map<String, Object>> encodedFeatures = new ArrayList<>(features.size());
            Optional<Envelope> layerEnvelope = Optional.empty();
            for (Feature feature : features) {
                Objects.requireNonNull(feature, "feature");
                String featureId = requireText(feature.id(), "featureId", budget);
                if (!featureIds.add(featureId)) {
                    throw failure(
                            MundaneMapException.DUPLICATE_ID,
                            "Duplicate feature identity in layer",
                            "identityNamespace",
                            "feature");
                }
                String featureName = requireTextValue(feature.name(), "featureName", budget);
                budget.add(Integer.BYTES);
                Feature copy =
                        new Feature(
                                featureId,
                                featureName,
                                feature.geometry(),
                                feature.attributes(),
                                feature.symbol());
                EncodedFeature encoded = encodeFeature(copy, budget);
                primitiveCount =
                        addAndCheck(
                                "primitives",
                                primitiveCount,
                                encoded.primitives(),
                                limits.primitives());
                coordinatePairs =
                        addAndCheck(
                                "coordinatePairs",
                                coordinatePairs,
                                encoded.coordinatePairs(),
                                limits.coordinatePairs());
                pathCommands =
                        addAndCheck(
                                "pathCommands",
                                pathCommands,
                                encoded.pathCommands(),
                                limits.pathCommands());
                featureCopies.add(copy);
                encodedFeatures.add(encoded.value());
                layerEnvelope = union(layerEnvelope, copy.geometry().envelope());
            }
            Layer copy = new SnapshotLayer(layerId, layerName, featureCopies, layerEnvelope);
            copies.add(copy);
            encodedLayers.add(
                    immutableMap(
                            "id", layerId,
                            "name", layerName,
                            "features", List.copyOf(encodedFeatures)));
            if (layerEnvelope.isPresent()) {
                envelope = union(envelope, layerEnvelope.orElseThrow());
            }
        }
        Map<String, Object> scene =
                immutableMap(
                        "protocolVersion",
                        VERSION,
                        "componentGeneration",
                        componentGeneration,
                        "sceneGeneration",
                        sceneGeneration,
                        "viewportGeneration",
                        viewportGeneration,
                        "background",
                        color(background),
                        "viewport",
                        viewport(viewport),
                        "layers",
                        List.copyOf(encodedLayers));
        return new Result(List.copyOf(copies), scene, envelope, budget.used());
    }

    private EncodedFeature encodeFeature(Feature feature, Budget budget) {
        Geometry geometry = feature.geometry();
        Map<String, Object> primitive;
        long coordinatePairs;
        long pathCommands = 0;
        if (geometry instanceof PointGeometry point) {
            if (!(feature.symbol() instanceof VectorMarkerSymbol marker)) {
                throw unsupported("point symbol");
            }
            MarkerPlacement placement = marker.placement();
            if (marker.stroke().isPresent()
                    || placement.size().unit() != SymbolUnit.SCREEN_PIXEL
                    || placement.size().width() != placement.size().height()
                    || placement.anchor() != SymbolAnchor.CENTER
                    || placement.offsetX() != 0.0
                    || placement.offsetY() != 0.0
                    || placement.rotationDegrees() != 0.0
                    || placement.rotationMode() != SymbolRotationMode.SCREEN_RELATIVE) {
                throw unsupported("point symbol placement");
            }
            VectorPath path = marker.path();
            budget.add(1);
            budget.addArrayOfNumbers(2);
            budget.add(Integer.BYTES + path.commandCount());
            budget.addArrayOfNumbers(path.ordinateCount());
            budget.addArrayOfNumbers(4);
            budget.addNumbers(2);
            budget.addArrayOfNumbers(4);
            pathCommands = path.commandCount();
            primitive =
                    immutableMap(
                            "kind", "point",
                            "coordinate", List.of(point.coordinate().x(), point.coordinate().y()),
                            "path", path(path),
                            "viewBox",
                                    List.of(
                                            marker.viewBox().minX(),
                                            marker.viewBox().minY(),
                                            marker.viewBox().maxX(),
                                            marker.viewBox().maxY()),
                            "size", placement.size().width(),
                            "fill", color(marker.fill()),
                            "opacity", marker.opacity());
            coordinatePairs = 1;
        } else if (geometry instanceof LineStringGeometry line) {
            if (!(feature.symbol() instanceof SolidLineSymbol symbol)
                    || symbol.startMarker().isPresent()
                    || symbol.endMarker().isPresent()
                    || symbol.stroke().width().unit() != SymbolUnit.SCREEN_PIXEL) {
                throw unsupported("line symbol");
            }
            budget.add(1);
            budget.addArrayOfNumbers(line.coordinates().size() * 2L);
            budget.addArrayOfNumbers(4);
            budget.addNumbers(2);
            primitive =
                    immutableMap(
                            "kind", "line",
                            "coordinates", numbers(line.coordinates()),
                            "stroke", stroke(symbol.stroke()),
                            "opacity", symbol.opacity());
            coordinatePairs = line.coordinates().size();
        } else if (geometry instanceof PolygonGeometry polygon) {
            if (!(feature.symbol() instanceof SolidFillSymbol symbol)) {
                throw unsupported("polygon symbol");
            }
            List<List<Double>> rings = new ArrayList<>(polygon.holes().size() + 1);
            rings.add(numbers(polygon.exterior()));
            coordinatePairs = polygon.exterior().size();
            for (CoordinateSequence hole : polygon.holes()) {
                rings.add(numbers(hole));
                coordinatePairs += hole.size();
            }
            Map<String, Object> outline = immutableMap("present", false);
            budget.add(1);
            if (symbol.outline().isPresent()) {
                Symbol candidate = symbol.outline().orElseThrow();
                if (!(candidate instanceof SolidLineSymbol line)
                        || line.startMarker().isPresent()
                        || line.endMarker().isPresent()
                        || line.stroke().width().unit() != SymbolUnit.SCREEN_PIXEL) {
                    throw unsupported("polygon outline");
                }
                outline =
                        immutableMap(
                                "present",
                                true,
                                "stroke",
                                stroke(line.stroke()),
                                "opacity",
                                line.opacity());
                budget.addArrayOfNumbers(4);
                budget.addNumbers(2);
            }
            budget.add(1);
            budget.add(Integer.BYTES);
            for (List<Double> ring : rings) {
                budget.addArrayOfNumbers(ring.size());
            }
            budget.addArrayOfNumbers(4);
            budget.addNumbers(1);
            primitive =
                    immutableMap(
                            "kind", "polygon",
                            "rings", List.copyOf(rings),
                            "fill", color(symbol.fill()),
                            "outline", outline,
                            "opacity", symbol.opacity());
        } else {
            throw unsupported("geometry");
        }
        return new EncodedFeature(
                immutableMap(
                        "id", feature.id(),
                        "name", feature.name(),
                        "primitives", List.of(primitive)),
                1,
                coordinatePairs,
                pathCommands);
    }

    private static MundaneMapException unsupported(String valueKind) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("scope", "feature");
        context.put("valueKind", valueKind);
        return new MundaneMapException(
                MundaneMapException.UNSUPPORTED_VALUE,
                "Feature is outside the first browser vector profile",
                context);
    }

    private String requireText(String value, String name, Budget budget) {
        String result = requireTextValue(value, name, budget);
        if (result.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        requireLimit(name + "CodeUnits", result.length(), ID_CODE_UNITS);
        return result;
    }

    private String requireTextValue(String value, String name, Budget budget) {
        Objects.requireNonNull(value, name);
        long bytes = value.getBytes(StandardCharsets.UTF_8).length;
        requireLimit(name + "CodeUnits", value.length(), limits.stringCodeUnits());
        budget.add(Integer.BYTES + bytes);
        return value;
    }

    private static List<Double> numbers(CoordinateSequence coordinates) {
        double[] packed = coordinates.toArray();
        List<Double> result = new ArrayList<>(packed.length);
        for (double ordinate : packed) {
            result.add(ordinate);
        }
        return List.copyOf(result);
    }

    private static Map<String, Object> path(VectorPath path) {
        List<String> commands = new ArrayList<>(path.commandCount());
        for (int index = 0; index < path.commandCount(); index++) {
            commands.add(path.commandAt(index).name());
        }
        List<Double> ordinates = new ArrayList<>(path.ordinateCount());
        for (int index = 0; index < path.ordinateCount(); index++) {
            ordinates.add(path.ordinateAt(index));
        }
        return immutableMap("commands", List.copyOf(commands), "ordinates", List.copyOf(ordinates));
    }

    private static Map<String, Object> stroke(SymbolStroke stroke) {
        return immutableMap("color", color(stroke.color()), "width", stroke.width().value());
    }

    private static List<Integer> color(Rgba color) {
        return List.of(color.red(), color.green(), color.blue(), color.alpha());
    }

    private static Map<String, Object> viewport(MapViewport viewport) {
        return immutableMap(
                "width", viewport.width(),
                "height", viewport.height(),
                "centerX", viewport.centerX(),
                "centerY", viewport.centerY(),
                "worldUnitsPerPixel", viewport.worldUnitsPerPixel());
    }

    private static Optional<Envelope> union(Optional<Envelope> aggregate, Envelope next) {
        return Optional.of(aggregate.map(value -> value.union(next)).orElse(next));
    }

    private static long addAndCheck(String name, long current, long addition, long maximum) {
        long result;
        try {
            result = Math.addExact(current, addition);
        } catch (ArithmeticException exception) {
            throw limitFailure(name, Long.MAX_VALUE, maximum);
        }
        requireLimit(name, result, maximum);
        return result;
    }

    private static void requireLimit(String name, long actual, long maximum) {
        if (actual > maximum) {
            throw limitFailure(name, actual, maximum);
        }
    }

    private static MundaneMapException limitFailure(String name, long actual, long maximum) {
        return new MundaneMapException(
                MundaneMapException.LIMIT_EXCEEDED,
                "Browser scene limit exceeded",
                Map.of(
                        "limit",
                        name,
                        "actual",
                        Long.toString(actual),
                        "maximum",
                        Long.toString(maximum)));
    }

    private static MundaneMapException failure(
            String code, String message, String contextName, String contextValue) {
        return new MundaneMapException(code, message, Map.of(contextName, contextValue));
    }

    private static Map<String, Object> immutableMap(Object... entries) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], entries[index + 1]);
        }
        return Collections.unmodifiableMap(result);
    }

    record Limits(
            int layers,
            int features,
            int primitives,
            int coordinatePairs,
            int pathCommands,
            long logicalBytes,
            int stringCodeUnits) {
        Limits {
            if (layers <= 0
                    || features <= 0
                    || primitives <= 0
                    || coordinatePairs <= 0
                    || pathCommands <= 0
                    || logicalBytes <= 0
                    || stringCodeUnits <= 0) {
                throw new IllegalArgumentException("Protocol limits must be positive");
            }
        }
    }

    record Result(
            List<Layer> layers,
            Map<String, Object> scene,
            Optional<Envelope> envelope,
            long logicalBytes) {}

    private record EncodedFeature(
            Map<String, Object> value, long primitives, long coordinatePairs, long pathCommands) {}

    private record SnapshotLayer(
            String id, String name, List<Feature> features, Optional<Envelope> envelope)
            implements Layer {
        private SnapshotLayer {
            features = List.copyOf(features);
            Objects.requireNonNull(envelope, "envelope");
        }
    }

    private static final class Budget {
        private final long maximum;
        private long used;

        private Budget(long maximum) {
            this.maximum = maximum;
        }

        private void addNumbers(long count) {
            add(Math.multiplyExact(count, Double.BYTES));
        }

        private void addArrayOfNumbers(long count) {
            add(Integer.BYTES);
            addNumbers(count);
        }

        private void add(long bytes) {
            used = addAndCheck("logicalBytes", used, bytes, maximum);
        }

        private long used() {
            return used;
        }
    }
}
