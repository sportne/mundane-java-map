package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PointLabelTexts;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.GreedyPointLabelPlacement;
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
import java.util.function.Predicate;

/** Package-private encoder for protocol version one. */
final class SceneProtocol {
    static final int VERSION = 1;
    static final Limits DEFAULT_LIMITS =
            new Limits(64, 50_000, 200_000, 2_000_000, 2_000_000, 64L * 1024 * 1024, 4096);
    private static final int ID_CODE_UNITS = 256;
    private static final int MAX_SYMBOL_DEPTH = 64;

    private final Limits limits;

    @FunctionalInterface
    interface IconResources {
        /** Resolver that rejects every raster icon. */
        IconResources NONE =
                ignored -> {
                    throw unsupported("raster icon");
                };

        /** Returns one staged same-origin resource URI. */
        String uri(RasterIconSymbol icon);
    }

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
        return encode(
                sourceLayers,
                background,
                viewport,
                componentGeneration,
                sceneGeneration,
                viewportGeneration,
                IconResources.NONE);
    }

    Result encode(
            List<? extends Layer> sourceLayers,
            Rgba background,
            MapViewport viewport,
            long componentGeneration,
            long sceneGeneration,
            long viewportGeneration,
            IconResources iconResources) {
        Objects.requireNonNull(sourceLayers, "sourceLayers");
        Objects.requireNonNull(background, "background");
        Objects.requireNonNull(iconResources, "iconResources");
        validateViewport(viewport);
        requireLimit("layers", sourceLayers.size(), limits.layers());
        Budget budget = new Budget(limits.logicalBytes());
        budget.addNumbers(4);
        budget.addArrayOfNumbers(4);
        budget.addNumbers(5);
        budget.add(Integer.BYTES);
        budget.add(Integer.BYTES);
        budget.add(Integer.BYTES);
        Set<String> layerIds = new LinkedHashSet<>();
        List<Layer> copies = new ArrayList<>(sourceLayers.size());
        List<Map<String, Object>> encodedLayers = new ArrayList<>(sourceLayers.size());
        Optional<Envelope> envelope = Optional.empty();
        long featureCount = 0;
        long primitiveCount = 0;
        long coordinatePairs = 0;
        long pathCommands = 0;
        long labelCandidates = 0;
        long labelPositions = 0;
        long labelCodePoints = 0;
        List<SceneLabelCandidate> retainedLabelCandidates = new ArrayList<>();
        List<Map<String, Object>> encodedLabelCandidates = new ArrayList<>();
        for (int layerIndex = 0; layerIndex < sourceLayers.size(); layerIndex++) {
            Layer layer = sourceLayers.get(layerIndex);
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
            List<String> logicalFeatureIds = new ArrayList<>(features.size());
            List<Long> copyIndices = new ArrayList<>(features.size());
            List<Map<String, Object>> encodedFeatures = new ArrayList<>(features.size());
            Optional<Envelope> layerEnvelope = Optional.empty();
            for (int featureIndex = 0; featureIndex < features.size(); featureIndex++) {
                Feature feature = features.get(featureIndex);
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
                String logicalFeatureId =
                        requireText(
                                BrowserLogicalLayer.logicalFeatureId(layer, featureIndex),
                                "logicalFeatureId",
                                budget);
                long copyIndex = BrowserLogicalLayer.copyIndex(layer, featureIndex);
                if (copyIndex < -io.github.mundanej.map.core.HorizontalWrap.COPY_INDEX_HARD_MAXIMUM
                        || copyIndex
                                > io.github.mundanej.map.core.HorizontalWrap
                                        .COPY_INDEX_HARD_MAXIMUM) {
                    throw failure(
                            MundaneMapException.LIMIT_EXCEEDED,
                            "Browser wrap copy index exceeds the closed profile",
                            "limit",
                            "copyIndex");
                }
                budget.add(Integer.BYTES);
                budget.addNumbers(1);
                Feature copy =
                        new Feature(
                                featureId,
                                featureName,
                                feature.geometry(),
                                feature.attributes(),
                                feature.symbol());
                EncodedFeature encoded =
                        encodeFeature(copy, logicalFeatureId, copyIndex, budget, iconResources);
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
                logicalFeatureIds.add(logicalFeatureId);
                copyIndices.add(copyIndex);
                encodedFeatures.add(encoded.value());
                layerEnvelope = union(layerEnvelope, copy.geometry().envelope());
            }
            Optional<Envelope> declaredEnvelope =
                    Objects.requireNonNull(layer.envelope(), "layer envelope");
            if (declaredEnvelope.isPresent()) {
                layerEnvelope = union(layerEnvelope, declaredEnvelope.orElseThrow());
            }
            List<BrowserLabelCandidate> layerLabels =
                    layer instanceof BrowserLabelLayer labelLayer
                            ? List.copyOf(labelLayer.browserLabelCandidates())
                            : List.of();
            List<BrowserLabelCandidate> retainedLayerLabels = new ArrayList<>(layerLabels.size());
            for (BrowserLabelCandidate candidate : layerLabels) {
                Objects.requireNonNull(candidate, "label candidate");
                if (!candidate.layerId().equals(layerId)
                        || candidate.featureIndex() >= featureCopies.size()
                        || !featureCopies
                                .get(candidate.featureIndex())
                                .id()
                                .equals(candidate.featureId())
                        || !(featureCopies.get(candidate.featureIndex()).geometry()
                                instanceof PointGeometry)
                        || !((PointGeometry) featureCopies.get(candidate.featureIndex()).geometry())
                                .coordinate()
                                .equals(candidate.mapAnchor())
                        || !featureCopies
                                .get(candidate.featureIndex())
                                .symbol()
                                .equals(candidate.marker())) {
                    throw failure(
                            MundaneMapException.UNSUPPORTED_VALUE,
                            "Point-label candidate does not match its feature",
                            "valueKind",
                            "point label");
                }
                int codePoints;
                try {
                    codePoints = PointLabelTexts.requireSupported(candidate.text());
                } catch (PointLabelTexts.ValidationException exception) {
                    String code =
                            exception.reason() == PointLabelTexts.FailureReason.TOO_LONG
                                    ? "LABEL_TEXT_LIMIT_EXCEEDED"
                                    : exception.reason() == PointLabelTexts.FailureReason.MULTILINE
                                            ? "LABEL_TEXT_MULTILINE_UNSUPPORTED"
                                            : MundaneMapException.UNSUPPORTED_VALUE;
                    throw failure(
                            code, "Point-label text is unsupported", "valueKind", "labelText");
                }
                labelCandidates =
                        addAndCheck(
                                "labelRequests",
                                labelCandidates,
                                1,
                                GreedyPointLabelPlacement.MAXIMUM_REQUESTS);
                labelPositions =
                        addAndCheck(
                                "labelCandidates",
                                labelPositions,
                                candidate.profile().positions().size(),
                                GreedyPointLabelPlacement.MAXIMUM_CANDIDATES);
                labelCodePoints =
                        addAndCheck("labelCodePoints", labelCodePoints, codePoints, 262_144);
                SceneLabelCandidate retained =
                        new SceneLabelCandidate(
                                candidate,
                                layerIndex,
                                Math.toIntExact(retainedLabelCandidates.size()));
                retainedLabelCandidates.add(retained);
                retainedLayerLabels.add(candidate);
                String text = requireTextValue(candidate.text(), "labelText", budget);
                budget.addNumbers(2);
                budget.add(2);
                encodedLabelCandidates.add(
                        immutableMap(
                                "ordinal",
                                retained.ordinaryPaintOrdinal(),
                                "text",
                                text,
                                "fontFamily",
                                "SANS_SERIF",
                                "weight",
                                candidate.profile().style().weight().name(),
                                "sizePixels",
                                candidate.profile().style().sizePixels()));
            }
            Layer copy =
                    new SnapshotLayer(
                            layerId,
                            layerName,
                            featureCopies,
                            layerEnvelope,
                            retainedLayerLabels,
                            logicalFeatureIds,
                            copyIndices);
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
                        "rasters",
                        List.of(),
                        "labelCandidates",
                        List.copyOf(encodedLabelCandidates),
                        "layers",
                        List.copyOf(encodedLayers));
        return new Result(
                List.copyOf(copies),
                scene,
                envelope,
                budget.used(),
                List.copyOf(retainedLabelCandidates));
    }

    Result withRasterWindows(
            Result vectorResult,
            List<BrowserRasterWindow> windows,
            List<Map<String, Object>> encodedWindows,
            Set<String> basemapLayerIds) {
        Objects.requireNonNull(vectorResult, "vectorResult");
        Objects.requireNonNull(windows, "windows");
        Objects.requireNonNull(encodedWindows, "encodedWindows");
        Objects.requireNonNull(basemapLayerIds, "basemapLayerIds");
        if (windows.size() != encodedWindows.size()) {
            throw new IllegalArgumentException("Raster windows and encodings must have equal size");
        }
        requireLimit(
                "layers",
                Math.addExact(vectorResult.layers().size(), windows.size()),
                limits.layers());
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        for (Layer layer : vectorResult.layers()) {
            identities.add(layer.id());
        }
        long logicalBytes = Math.addExact(vectorResult.logicalBytes(), Integer.BYTES);
        requireLimit("logicalBytes", logicalBytes, limits.logicalBytes());
        Optional<Envelope> envelope = vectorResult.envelope();
        for (int index = 0; index < windows.size(); index++) {
            BrowserRasterWindow window = windows.get(index);
            if (!identities.add(window.displayId())) {
                throw failure(
                        MundaneMapException.DUPLICATE_ID,
                        "Duplicate layer identity",
                        "identityNamespace",
                        "layer");
            }
            logicalBytes =
                    Math.addExact(
                            logicalBytes, rasterLogicalBytes(window, encodedWindows.get(index)));
            requireLimit("logicalBytes", logicalBytes, limits.logicalBytes());
            envelope = union(envelope, window.imageMapBounds());
        }
        LinkedHashMap<String, Object> scene = new LinkedHashMap<>(vectorResult.scene());
        scene.put("rasters", List.copyOf(encodedWindows));
        List<String> retainedBasemaps =
                vectorResult.layers().stream()
                        .map(Layer::id)
                        .filter(basemapLayerIds::contains)
                        .toList();
        for (String id : retainedBasemaps) {
            logicalBytes = Math.addExact(logicalBytes, textLogicalBytes(id));
            requireLimit("logicalBytes", logicalBytes, limits.logicalBytes());
        }
        scene.put("basemapLayerIds", retainedBasemaps);
        return new Result(
                vectorResult.layers(),
                Collections.unmodifiableMap(scene),
                envelope,
                logicalBytes,
                vectorResult.labelCandidates());
    }

    private static long textLogicalBytes(String value) {
        return (long) Integer.BYTES
                + value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
    }

    private static long rasterLogicalBytes(
            BrowserRasterWindow window, Map<String, Object> encoded) {
        String resource = (String) encoded.get("resource");
        long bytes = Integer.BYTES;
        bytes =
                Math.addExact(
                        bytes,
                        Integer.BYTES + window.displayId().getBytes(StandardCharsets.UTF_8).length);
        bytes =
                Math.addExact(
                        bytes,
                        Integer.BYTES
                                + window.bindingName().getBytes(StandardCharsets.UTF_8).length);
        bytes =
                Math.addExact(
                        bytes, Integer.BYTES + resource.getBytes(StandardCharsets.UTF_8).length);
        bytes = Math.addExact(bytes, 4L * Integer.BYTES);
        bytes = Math.addExact(bytes, Long.BYTES);
        bytes =
                Math.addExact(
                        bytes,
                        Integer.BYTES + window.bindingId().getBytes(StandardCharsets.UTF_8).length);
        bytes = Math.addExact(bytes, 19L * Double.BYTES);
        return bytes;
    }

    private EncodedFeature encodeFeature(
            Feature feature,
            String logicalFeatureId,
            long copyIndex,
            Budget budget,
            IconResources iconResources) {
        PrimitiveAccumulator target = new PrimitiveAccumulator(budget, limits);
        Geometry geometry = feature.geometry();
        Symbol symbol = feature.symbol();
        if (geometry instanceof PointGeometry point) {
            requireRole(symbol, SymbolRole.MARKER, "point symbol");
            encodeMarkers(
                    symbol,
                    List.of(point.coordinate()),
                    1.0,
                    Optional.empty(),
                    0,
                    target,
                    iconResources);
        } else if (geometry instanceof MultiPointGeometry points) {
            requireRole(symbol, SymbolRole.MARKER, "point symbol");
            List<Coordinate> coordinates = new ArrayList<>(points.coordinates().size());
            for (int index = 0; index < points.coordinates().size(); index++) {
                coordinates.add(points.coordinates().coordinate(index));
            }
            encodeMarkers(symbol, coordinates, 1.0, Optional.empty(), 0, target, iconResources);
        } else if (geometry instanceof LineStringGeometry line) {
            requireRole(symbol, SymbolRole.LINE, "line symbol");
            encodeLines(
                    symbol,
                    List.of(line.coordinates()),
                    feature.id(),
                    1.0,
                    false,
                    0,
                    target,
                    iconResources);
        } else if (geometry instanceof MultiLineStringGeometry lines) {
            requireRole(symbol, SymbolRole.LINE, "line symbol");
            List<CoordinateSequence> parts = new ArrayList<>(lines.partCount());
            for (int part = 0; part < lines.partCount(); part++) {
                parts.add(
                        slice(
                                lines.coordinates(),
                                lines.partOffset(part),
                                lines.partOffset(part + 1)));
            }
            encodeLines(symbol, parts, feature.id(), 1.0, false, 0, target, iconResources);
        } else if (geometry instanceof PolygonGeometry polygon) {
            requireRole(symbol, SymbolRole.FILL, "polygon symbol");
            encodeFills(
                    symbol, List.of(rings(polygon)), feature.id(), 1.0, 0, target, iconResources);
        } else if (geometry instanceof MultiPolygonGeometry polygons) {
            requireRole(symbol, SymbolRole.FILL, "polygon symbol");
            List<List<CoordinateSequence>> components = new ArrayList<>(polygons.polygonCount());
            for (int polygon = 0; polygon < polygons.polygonCount(); polygon++) {
                List<CoordinateSequence> rings = new ArrayList<>();
                int firstRing = polygons.polygonRingOffset(polygon);
                int ringLimit = polygons.polygonRingOffset(polygon + 1);
                for (int ring = firstRing; ring < ringLimit; ring++) {
                    rings.add(
                            slice(
                                    polygons.coordinates(),
                                    polygons.ringOffset(ring),
                                    polygons.ringOffset(ring + 1)));
                }
                components.add(List.copyOf(rings));
            }
            encodeFills(symbol, components, feature.id(), 1.0, 0, target, iconResources);
        } else {
            throw unsupported("geometry");
        }
        return new EncodedFeature(
                immutableMap(
                        "id", feature.id(),
                        "logicalId", logicalFeatureId,
                        "copyIndex", copyIndex,
                        "name", feature.name(),
                        "primitives", List.copyOf(target.primitives)),
                target.primitives.size(),
                target.coordinatePairs,
                target.pathCommands);
    }

    static Symbol requireBuiltInSymbol(
            Symbol symbol, SymbolRole role, String scope, String valueKind) {
        return requirePortrayalSymbol(symbol, role, ignored -> false, scope, valueKind);
    }

    static Symbol requirePortrayalSymbol(
            Symbol symbol,
            SymbolRole role,
            Predicate<RasterIconSymbol> authorizedIcon,
            String scope) {
        return requirePortrayalSymbol(symbol, role, authorizedIcon, scope, roleValueKind(role));
    }

    private static Symbol requirePortrayalSymbol(
            Symbol symbol,
            SymbolRole role,
            Predicate<RasterIconSymbol> authorizedIcon,
            String scope,
            String valueKind) {
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(authorizedIcon, "authorizedIcon");
        if (symbol.role() != role) {
            throw unsupported(valueKind, scope);
        }
        switch (role) {
            case MARKER -> validateMarkerTree(symbol, 0, scope, authorizedIcon);
            case LINE -> validateLineTree(symbol, 0, scope, authorizedIcon);
            case FILL -> validateFillTree(symbol, 0, scope, authorizedIcon);
            default -> throw unsupported(valueKind, scope);
        }
        return symbol;
    }

    private static void validateMarkerTree(
            Symbol symbol, int depth, String scope, Predicate<RasterIconSymbol> authorizedIcon) {
        requireDepth(depth);
        if (symbol instanceof CompositeSymbol composite) {
            requireRole(composite, SymbolRole.MARKER, "marker composite");
            for (Symbol child : composite.children()) {
                validateMarkerTree(child, depth + 1, scope, authorizedIcon);
            }
        } else if (symbol instanceof RasterIconSymbol icon) {
            if (!authorizedIcon.test(icon)) {
                throw unsupported("unauthorized raster icon", scope);
            }
        } else if (!(symbol instanceof VectorMarkerSymbol)) {
            throw unsupported("marker symbol", scope);
        }
    }

    private static void validateLineTree(
            Symbol symbol, int depth, String scope, Predicate<RasterIconSymbol> authorizedIcon) {
        requireDepth(depth);
        if (symbol instanceof CompositeSymbol composite) {
            requireRole(composite, SymbolRole.LINE, "line composite");
            for (Symbol child : composite.children()) {
                validateLineTree(child, depth + 1, scope, authorizedIcon);
            }
        } else if (symbol instanceof SolidLineSymbol line) {
            line.startMarker()
                    .ifPresent(
                            marker -> validateMarkerTree(marker, depth + 1, scope, authorizedIcon));
            line.endMarker()
                    .ifPresent(
                            marker -> validateMarkerTree(marker, depth + 1, scope, authorizedIcon));
        } else {
            throw unsupported("line symbol", scope);
        }
    }

    private static void validateFillTree(
            Symbol symbol, int depth, String scope, Predicate<RasterIconSymbol> authorizedIcon) {
        requireDepth(depth);
        if (symbol instanceof CompositeSymbol composite) {
            requireRole(composite, SymbolRole.FILL, "fill composite");
            for (Symbol child : composite.children()) {
                validateFillTree(child, depth + 1, scope, authorizedIcon);
            }
        } else if (symbol instanceof SolidFillSymbol fill) {
            fill.outline()
                    .ifPresent(
                            outline -> validateLineTree(outline, depth + 1, scope, authorizedIcon));
        } else if (symbol instanceof HatchFillSymbol hatch) {
            hatch.outline()
                    .ifPresent(
                            outline -> validateLineTree(outline, depth + 1, scope, authorizedIcon));
        } else {
            throw unsupported("fill symbol", scope);
        }
    }

    private static void encodeMarkers(
            Symbol symbol,
            List<Coordinate> coordinates,
            double inheritedOpacity,
            Optional<Double> endpointBearing,
            int depth,
            PrimitiveAccumulator target,
            IconResources iconResources) {
        requireDepth(depth);
        if (symbol instanceof CompositeSymbol composite) {
            requireRole(composite, SymbolRole.MARKER, "marker composite");
            for (Symbol child : composite.children()) {
                encodeMarkers(
                        child,
                        coordinates,
                        inheritedOpacity * composite.opacity(),
                        endpointBearing,
                        depth + 1,
                        target,
                        iconResources);
            }
            return;
        }
        if (symbol instanceof RasterIconSymbol icon) {
            for (Coordinate coordinate : coordinates) {
                target.addMarker(
                        iconPrimitive(
                                coordinate,
                                icon,
                                inheritedOpacity * icon.opacity(),
                                endpointBearing,
                                iconResources.uri(icon),
                                target.budget),
                        0);
            }
            return;
        }
        if (!(symbol instanceof VectorMarkerSymbol marker)) {
            throw unsupported("marker symbol");
        }
        for (Coordinate coordinate : coordinates) {
            target.addMarker(
                    pointPrimitive(
                            coordinate,
                            marker,
                            inheritedOpacity * marker.opacity(),
                            endpointBearing,
                            target.budget),
                    marker.path().commandCount());
        }
    }

    private static void encodeLines(
            Symbol symbol,
            List<CoordinateSequence> parts,
            String featureId,
            double inheritedOpacity,
            boolean closedRing,
            int depth,
            PrimitiveAccumulator target,
            IconResources iconResources) {
        requireDepth(depth);
        if (symbol instanceof CompositeSymbol composite) {
            requireRole(composite, SymbolRole.LINE, "line composite");
            for (Symbol child : composite.children()) {
                encodeLines(
                        child,
                        parts,
                        featureId,
                        inheritedOpacity * composite.opacity(),
                        closedRing,
                        depth + 1,
                        target,
                        iconResources);
            }
            return;
        }
        if (!(symbol instanceof SolidLineSymbol line)) {
            throw unsupported("line symbol");
        }
        double opacity = inheritedOpacity * line.opacity();
        for (int partIndex = 0; partIndex < parts.size(); partIndex++) {
            CoordinateSequence part = parts.get(partIndex);
            EndpointBearings bearings = endpointBearings(part, featureId, partIndex);
            if (bearings.start().isEmpty() && bearings.end().isEmpty()) {
                continue;
            }
            target.addCoordinates(
                    linePrimitive(part, line.stroke(), opacity, target.budget), part.size());
            if (!closedRing && (line.startMarker().isPresent() || line.endMarker().isPresent())) {
                if (line.startMarker().isPresent() && bearings.start().isPresent()) {
                    encodeMarkers(
                            line.startMarker().orElseThrow(),
                            List.of(part.coordinate(0)),
                            opacity,
                            bearings.start(),
                            depth + 1,
                            target,
                            iconResources);
                }
                if (line.endMarker().isPresent() && bearings.end().isPresent()) {
                    encodeMarkers(
                            line.endMarker().orElseThrow(),
                            List.of(part.coordinate(part.size() - 1)),
                            opacity,
                            bearings.end(),
                            depth + 1,
                            target,
                            iconResources);
                }
            }
        }
    }

    private static void encodeFills(
            Symbol symbol,
            List<List<CoordinateSequence>> polygons,
            String featureId,
            double inheritedOpacity,
            int depth,
            PrimitiveAccumulator target,
            IconResources iconResources) {
        requireDepth(depth);
        if (symbol instanceof CompositeSymbol composite) {
            requireRole(composite, SymbolRole.FILL, "fill composite");
            for (Symbol child : composite.children()) {
                encodeFills(
                        child,
                        polygons,
                        featureId,
                        inheritedOpacity * composite.opacity(),
                        depth + 1,
                        target,
                        iconResources);
            }
            return;
        }
        if (symbol instanceof SolidFillSymbol fill) {
            double opacity = inheritedOpacity * fill.opacity();
            for (List<CoordinateSequence> polygon : polygons) {
                target.addPolygon(
                        polygonPrimitive(polygon, fill.fill(), opacity, target.budget), polygon);
                fill.outline()
                        .ifPresent(
                                outline ->
                                        encodeLines(
                                                outline,
                                                polygon,
                                                featureId,
                                                opacity,
                                                true,
                                                depth + 1,
                                                target,
                                                iconResources));
            }
            return;
        }
        if (symbol instanceof HatchFillSymbol hatch) {
            double opacity = inheritedOpacity * hatch.opacity();
            for (List<CoordinateSequence> polygon : polygons) {
                target.addPolygon(hatchPrimitive(polygon, hatch, opacity, target.budget), polygon);
                hatch.outline()
                        .ifPresent(
                                outline ->
                                        encodeLines(
                                                outline,
                                                polygon,
                                                featureId,
                                                opacity,
                                                true,
                                                depth + 1,
                                                target,
                                                iconResources));
            }
            return;
        }
        throw unsupported("fill symbol");
    }

    private static Map<String, Object> pointPrimitive(
            Coordinate point,
            VectorMarkerSymbol marker,
            double opacity,
            Optional<Double> endpointBearing,
            Budget budget) {
        MarkerPlacement placement = marker.placement();
        VectorPath path = marker.path();
        budget.add(1);
        budget.addArrayOfNumbers(2);
        budget.add(Integer.BYTES + path.commandCount());
        budget.addArrayOfNumbers(path.ordinateCount());
        budget.addArrayOfNumbers(4);
        budget.addArrayOfNumbers(2);
        budget.addArrayOfNumbers(2);
        budget.addNumbers(2);
        budget.add(3);
        budget.addArrayOfNumbers(4);
        budget.add(1);
        if (marker.stroke().isPresent()) {
            budget.addArrayOfNumbers(4);
            budget.addNumbers(1);
            budget.add(1);
        }
        budget.add(1);
        if (endpointBearing.isPresent()) {
            budget.addNumbers(1);
        }
        return immutableMap(
                "kind", "point",
                "coordinate", List.of(point.x(), point.y()),
                "path", path(path),
                "viewBox",
                        List.of(
                                marker.viewBox().minX(),
                                marker.viewBox().minY(),
                                marker.viewBox().maxX(),
                                marker.viewBox().maxY()),
                "size", List.of(placement.size().width(), placement.size().height()),
                "unit", placement.size().unit().name(),
                "anchor", placement.anchor().name(),
                "offset", List.of(placement.offsetX(), placement.offsetY()),
                "rotationDegrees", placement.rotationDegrees(),
                "rotationMode", placement.rotationMode().name(),
                "fill", color(marker.fill()),
                "stroke", optionalStroke(marker.stroke()),
                "endpointBearing", optionalNumber(endpointBearing),
                "opacity", opacity);
    }

    private static Map<String, Object> iconPrimitive(
            Coordinate point,
            RasterIconSymbol icon,
            double opacity,
            Optional<Double> endpointBearing,
            String resource,
            Budget budget) {
        MarkerPlacement placement = icon.placement();
        budget.add(1);
        budget.addArrayOfNumbers(2);
        budget.add(Integer.BYTES + resource.getBytes(StandardCharsets.UTF_8).length);
        budget.addNumbers(2);
        budget.addArrayOfNumbers(2);
        budget.addArrayOfNumbers(2);
        budget.addNumbers(2);
        budget.add(5);
        if (endpointBearing.isPresent()) {
            budget.addNumbers(1);
        }
        return immutableMap(
                "kind", "icon",
                "coordinate", List.of(point.x(), point.y()),
                "resource", resource,
                "intrinsicWidth", icon.width(),
                "intrinsicHeight", icon.height(),
                "size", List.of(placement.size().width(), placement.size().height()),
                "unit", placement.size().unit().name(),
                "anchor", placement.anchor().name(),
                "offset", List.of(placement.offsetX(), placement.offsetY()),
                "rotationDegrees", placement.rotationDegrees(),
                "rotationMode", placement.rotationMode().name(),
                "interpolation", icon.interpolation().name(),
                "endpointBearing", optionalNumber(endpointBearing),
                "opacity", opacity);
    }

    private static Map<String, Object> linePrimitive(
            CoordinateSequence coordinates, SymbolStroke stroke, double opacity, Budget budget) {
        budget.add(1);
        budget.addArrayOfNumbers(coordinates.size() * 2L);
        budget.addArrayOfNumbers(4);
        budget.addNumbers(2);
        budget.add(1);
        return immutableMap(
                "kind",
                "line",
                "coordinates",
                numbers(coordinates),
                "stroke",
                stroke(stroke),
                "opacity",
                opacity);
    }

    private static Map<String, Object> polygonPrimitive(
            List<CoordinateSequence> sourceRings, Rgba fill, double opacity, Budget budget) {
        List<List<Double>> rings = sourceRings.stream().map(SceneProtocol::numbers).toList();
        budget.add(1);
        budget.add(Integer.BYTES);
        for (List<Double> ring : rings) {
            budget.addArrayOfNumbers(ring.size());
        }
        budget.addArrayOfNumbers(4);
        budget.addNumbers(1);
        return immutableMap(
                "kind",
                "polygon",
                "rings",
                List.copyOf(rings),
                "fill",
                color(fill),
                "opacity",
                opacity);
    }

    private static Map<String, Object> hatchPrimitive(
            List<CoordinateSequence> sourceRings,
            HatchFillSymbol hatch,
            double opacity,
            Budget budget) {
        List<List<Double>> rings = sourceRings.stream().map(SceneProtocol::numbers).toList();
        budget.add(1);
        budget.add(Integer.BYTES);
        for (List<Double> ring : rings) {
            budget.addArrayOfNumbers(ring.size());
        }
        budget.addArrayOfNumbers(4);
        budget.addNumbers(4);
        budget.add(3);
        return immutableMap(
                "kind", "hatch",
                "rings", List.copyOf(rings),
                "pattern", hatch.pattern().name(),
                "stroke", stroke(hatch.stroke()),
                "spacing", hatch.spacing().value(),
                "spacingUnit", hatch.spacing().unit().name(),
                "rotationMode", hatch.rotationMode().name(),
                "maxSegments", hatch.maxSegments(),
                "opacity", opacity);
    }

    private static List<CoordinateSequence> rings(PolygonGeometry polygon) {
        List<CoordinateSequence> result = new ArrayList<>(polygon.holes().size() + 1);
        result.add(polygon.exterior());
        result.addAll(polygon.holes());
        return List.copyOf(result);
    }

    private static EndpointBearings endpointBearings(
            CoordinateSequence coordinates, String featureId, int partIndex) {
        Optional<Double> start = Optional.empty();
        double firstX = coordinates.x(0);
        double firstY = coordinates.y(0);
        for (int index = 1; index < coordinates.size(); index++) {
            if (coordinates.x(index) != firstX || coordinates.y(index) != firstY) {
                start =
                        Optional.of(
                                bearing(
                                        firstX - coordinates.x(index),
                                        coordinates.y(index) - firstY,
                                        featureId,
                                        partIndex,
                                        "start"));
                break;
            }
        }
        Optional<Double> end = Optional.empty();
        int last = coordinates.size() - 1;
        double lastX = coordinates.x(last);
        double lastY = coordinates.y(last);
        for (int index = last - 1; index >= 0; index--) {
            if (coordinates.x(index) != lastX || coordinates.y(index) != lastY) {
                end =
                        Optional.of(
                                bearing(
                                        lastX - coordinates.x(index),
                                        coordinates.y(index) - lastY,
                                        featureId,
                                        partIndex,
                                        "end"));
                break;
            }
        }
        return new EndpointBearings(start, end);
    }

    private static double bearing(
            double deltaX, double deltaScreenY, String featureId, int partIndex, String endpoint) {
        double result = StrictMath.toDegrees(StrictMath.atan2(deltaScreenY, deltaX));
        if (!Double.isFinite(result)) {
            throw new MundaneMapException(
                    MundaneMapException.NON_FINITE_VALUE,
                    "Endpoint bearing is non-finite",
                    Map.of(
                            "featureId", featureId,
                            "partIndex", Integer.toString(partIndex),
                            "endpoint", endpoint));
        }
        result %= 360.0;
        if (result < 0.0) {
            result += 360.0;
        }
        return result == 0.0 ? 0.0 : result;
    }

    private static void requireRole(Symbol symbol, SymbolRole role, String valueKind) {
        Objects.requireNonNull(symbol, "symbol");
        if (symbol.role() != role) {
            throw unsupported(valueKind);
        }
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_SYMBOL_DEPTH) {
            throw limitFailure("symbolDepth", depth, MAX_SYMBOL_DEPTH);
        }
    }

    static void requireSymbolDepth(int depth) {
        requireDepth(depth);
    }

    static MundaneMapException unsupportedBindingValue(String valueKind) {
        return unsupported(valueKind, "binding");
    }

    private static String roleValueKind(SymbolRole role) {
        return switch (role) {
            case MARKER -> "marker symbol";
            case LINE -> "line symbol";
            case FILL -> "fill symbol";
            case LEGACY_GEOMETRY -> "legacy symbol";
        };
    }

    private static Map<String, Object> optionalStroke(Optional<SymbolStroke> stroke) {
        return stroke.<Map<String, Object>>map(
                        value -> immutableMap("present", true, "value", stroke(value)))
                .orElseGet(() -> immutableMap("present", false));
    }

    private static Map<String, Object> optionalNumber(Optional<Double> value) {
        return value.<Map<String, Object>>map(
                        number -> immutableMap("present", true, "value", number))
                .orElseGet(() -> immutableMap("present", false));
    }

    private static CoordinateSequence slice(CoordinateSequence source, int start, int end) {
        double[] packed = new double[Math.multiplyExact(end - start, 2)];
        int target = 0;
        for (int index = start; index < end; index++) {
            packed[target++] = source.x(index);
            packed[target++] = source.y(index);
        }
        return CoordinateSequence.of(packed);
    }

    private static MundaneMapException unsupported(String valueKind) {
        return unsupported(valueKind, "feature");
    }

    private static MundaneMapException unsupported(String valueKind, String scope) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put("scope", scope);
        context.put("valueKind", valueKind);
        return new MundaneMapException(
                MundaneMapException.UNSUPPORTED_VALUE,
                "Feature is outside the bounded browser vector profile",
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
        return immutableMap(
                "color", color(stroke.color()),
                "width", stroke.width().value(),
                "unit", stroke.width().unit().name());
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
            long logicalBytes,
            List<SceneLabelCandidate> labelCandidates) {
        Result {
            labelCandidates = List.copyOf(labelCandidates);
        }
    }

    private record EncodedFeature(
            Map<String, Object> value, long primitives, long coordinatePairs, long pathCommands) {}

    private record EndpointBearings(Optional<Double> start, Optional<Double> end) {}

    private record SnapshotLayer(
            String id,
            String name,
            List<Feature> features,
            Optional<Envelope> envelope,
            List<BrowserLabelCandidate> browserLabelCandidates,
            List<String> logicalFeatureIds,
            List<Long> copyIndices)
            implements Layer, BrowserLabelLayer, BrowserLogicalLayer {
        private SnapshotLayer {
            features = List.copyOf(features);
            Objects.requireNonNull(envelope, "envelope");
            browserLabelCandidates = List.copyOf(browserLabelCandidates);
            logicalFeatureIds = List.copyOf(logicalFeatureIds);
            copyIndices = List.copyOf(copyIndices);
        }

        @Override
        public String logicalFeatureId(int featureIndex) {
            return logicalFeatureIds.get(featureIndex);
        }

        @Override
        public long copyIndex(int featureIndex) {
            return copyIndices.get(featureIndex);
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

    private static final class PrimitiveAccumulator {
        private final Budget budget;
        private final Limits limits;
        private final List<Map<String, Object>> primitives = new ArrayList<>();
        private long coordinatePairs;
        private long pathCommands;

        private PrimitiveAccumulator(Budget budget, Limits limits) {
            this.budget = budget;
            this.limits = limits;
        }

        private void addMarker(Map<String, Object> primitive, int commands) {
            primitives.add(primitive);
            checkPrimitiveCount();
            coordinatePairs =
                    addAndCheck("coordinatePairs", coordinatePairs, 1, limits.coordinatePairs());
            pathCommands =
                    addAndCheck("pathCommands", pathCommands, commands, limits.pathCommands());
        }

        private void addCoordinates(Map<String, Object> primitive, int pairs) {
            primitives.add(primitive);
            checkPrimitiveCount();
            coordinatePairs =
                    addAndCheck(
                            "coordinatePairs", coordinatePairs, pairs, limits.coordinatePairs());
        }

        private void addPolygon(Map<String, Object> primitive, List<CoordinateSequence> rings) {
            primitives.add(primitive);
            checkPrimitiveCount();
            for (CoordinateSequence ring : rings) {
                coordinatePairs =
                        addAndCheck(
                                "coordinatePairs",
                                coordinatePairs,
                                ring.size(),
                                limits.coordinatePairs());
            }
        }

        private void checkPrimitiveCount() {
            requireLimit("primitives", primitives.size(), limits.primitives());
        }
    }
}
