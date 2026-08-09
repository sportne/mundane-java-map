package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeatureCursor;
import io.github.mundanej.map.api.FeatureQuery;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.Geometry;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MultiLineStringGeometry;
import io.github.mundanej.map.api.MultiPointGeometry;
import io.github.mundanej.map.api.MultiPolygonGeometry;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.PortrayalEvaluationContext;
import io.github.mundanej.map.api.PortrayalGeometryType;
import io.github.mundanej.map.api.ResolvedFeaturePortrayal;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.FeatureQueryAccounting;
import io.github.mundanej.map.core.GeographicSeamSplitter;
import io.github.mundanej.map.core.GreedyPointLabelPlacement;
import io.github.mundanej.map.core.HorizontalInterval;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.HorizontalWrapException;
import io.github.mundanej.map.core.HorizontalWrapPlan;
import io.github.mundanej.map.core.HorizontalWrapProblem;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.QueryEnvelopeStatus;
import io.github.mundanej.map.core.QueryEnvelopeTransform;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Package-private serialized feature-query and transformation engine. */
final class FeatureSourceQueryEngine {
    Result query(
            List<RequestBinding> bindings,
            MapViewport viewport,
            CrsRegistry registry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            CancellationToken cancellation) {
        return query(
                bindings, viewport, registry, mapCrs, displayCrs, Optional.empty(), cancellation);
    }

    Result query(
            List<RequestBinding> bindings,
            MapViewport viewport,
            CrsRegistry registry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            Optional<HorizontalWrap> horizontalWrap,
            CancellationToken cancellation) {
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(mapCrs, "mapCrs");
        Objects.requireNonNull(displayCrs, "displayCrs");
        Objects.requireNonNull(horizontalWrap, "horizontalWrap");
        Objects.requireNonNull(cancellation, "cancellation");
        CrsOperation displayToMap = registry.operation(displayCrs, mapCrs);
        CrsOperation mapToDisplay = registry.operation(mapCrs, displayCrs);
        List<Layer> layers = new ArrayList<>();
        LinkedHashMap<String, DiagnosticReport> reports = new LinkedHashMap<>();
        for (RequestBinding requested : bindings) {
            if (cancellation.isCancellationRequested()) {
                return Result.cancelledResult();
            }
            FeatureSourceBinding binding = requested.binding();
            if (!requested.visible()) {
                layers.add(new QueryLayer(binding.id(), binding.name(), List.of()));
                DiagnosticReport opening = binding.source().openingDiagnostics();
                if (!opening.entries().isEmpty() || opening.omittedWarningCount() != 0) {
                    reports.put(binding.id(), opening);
                }
                continue;
            }
            BindingResult result =
                    binding.horizontalWrapMode() == BrowserHorizontalWrapMode.REPEAT_X
                            ? queryWrappedBinding(
                                    binding,
                                    viewport,
                                    registry,
                                    mapCrs,
                                    displayCrs,
                                    displayToMap,
                                    mapToDisplay,
                                    horizontalWrap.orElseThrow(),
                                    cancellation)
                            : queryBinding(
                                    binding,
                                    viewport,
                                    registry,
                                    mapCrs,
                                    displayCrs,
                                    displayToMap,
                                    mapToDisplay,
                                    cancellation);
            if (result.cancelled()) {
                return Result.cancelledResult();
            }
            layers.add(result.layer());
            if (!result.report().entries().isEmpty()
                    || result.report().omittedWarningCount() != 0) {
                reports.put(binding.id(), result.report());
            }
        }
        return new Result(List.copyOf(layers), Collections.unmodifiableMap(reports), false);
    }

    private static BindingResult queryBinding(
            FeatureSourceBinding binding,
            MapViewport viewport,
            CrsRegistry registry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            CrsOperation displayToMap,
            CrsOperation mapToDisplay,
            CancellationToken cancellation) {
        String sourceId = binding.source().metadata().identity().id();
        try {
            CrsOperation sourceToMap =
                    registry.operationFromMetadata(binding.source().metadata().crs(), mapCrs);
            CrsOperation mapToSource = registry.operation(mapCrs, sourceToMap.sourceCrs());
            QueryEnvelopeTransform mapEnvelope =
                    displayToMap.transformQueryEnvelope(viewport.visibleWorldEnvelope());
            if (mapEnvelope.status() == QueryEnvelopeStatus.OUTSIDE) {
                DiagnosticReport report =
                        merge(
                                binding.source().openingDiagnostics(),
                                warning(sourceId, "CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN"));
                return new BindingResult(
                        new QueryLayer(binding.id(), binding.name(), List.of()), report, false);
            }
            QueryEnvelopeTransform sourceEnvelope =
                    mapToSource.transformQueryEnvelope(
                            mapEnvelope.transformedEnvelope().orElseThrow());
            if (sourceEnvelope.status() == QueryEnvelopeStatus.OUTSIDE) {
                DiagnosticReport report =
                        merge(
                                binding.source().openingDiagnostics(),
                                warning(sourceId, "CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN"));
                return new BindingResult(
                        new QueryLayer(binding.id(), binding.name(), List.of()), report, false);
            }
            DiagnosticReport planning =
                    mapEnvelope.status() == QueryEnvelopeStatus.CLIPPED
                                    || sourceEnvelope.status() == QueryEnvelopeStatus.CLIPPED
                            ? warning(sourceId, "CRS_QUERY_ENVELOPE_CLIPPED")
                            : DiagnosticReport.empty();
            FeatureQuery query =
                    new FeatureQuery(
                            sourceEnvelope.transformedEnvelope(),
                            binding.queryAttributes(viewport.worldUnitsPerPixel()),
                            binding.tighterLimits());
            List<Feature> features = new ArrayList<>();
            List<BrowserLabelCandidate> labelCandidates = new ArrayList<>();
            Optional<Envelope> completeEnvelope = Optional.empty();
            PortrayalEvaluationContext portrayalContext = portrayalContext(viewport, displayCrs);
            DiagnosticReport cursorReport;
            try (FeatureCursor cursor = binding.source().openCursor(query, cancellation)) {
                while (cursor.advance()) {
                    if (cancellation.isCancellationRequested()) {
                        return BindingResult.cancelledResult();
                    }
                    FeatureRecord record = cursor.current();
                    Geometry transformed =
                            transformGeometry(
                                    record.geometry(), sourceToMap, mapToDisplay, cancellation);
                    completeEnvelope = union(completeEnvelope, transformed.envelope());
                    Optional<Symbol> selected =
                            symbol(binding, transformed, record.attributes(), portrayalContext);
                    if (selected.isPresent()) {
                        int featureIndex = features.size();
                        features.add(
                                new Feature(
                                        record.id(),
                                        record.name(),
                                        transformed,
                                        record.attributes(),
                                        selected.orElseThrow()));
                        if (transformed instanceof PointGeometry point
                                && binding.portrayal().pointLabel().isPresent()) {
                            binding.portrayal()
                                    .resolveLabelText(
                                            record.name(),
                                            record.attributes(),
                                            viewport.worldUnitsPerPixel())
                                    .ifPresent(
                                            text ->
                                                    labelCandidates.add(
                                                            new BrowserLabelCandidate(
                                                                    binding.id(),
                                                                    record.id(),
                                                                    point.coordinate(),
                                                                    selected.orElseThrow(),
                                                                    text,
                                                                    binding.portrayal()
                                                                            .pointLabel()
                                                                            .orElseThrow(),
                                                                    featureIndex)));
                        }
                    }
                }
                cursorReport = cursor.diagnostics();
            }
            DiagnosticReport report =
                    merge(binding.source().openingDiagnostics(), planning, cursorReport);
            return new BindingResult(
                    new QueryLayer(
                            binding.id(),
                            binding.name(),
                            features,
                            completeEnvelope,
                            labelCandidates),
                    report,
                    false);
        } catch (SourceException exception) {
            if (cancellation.isCancellationRequested()
                    || exception.terminal().code().equals("SOURCE_CANCELLED")) {
                return BindingResult.cancelledResult();
            }
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(binding.source().openingDiagnostics(), exception.report()),
                    false);
        } catch (CrsException exception) {
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(
                            binding.source().openingDiagnostics(),
                            terminal(
                                    sourceId,
                                    exception.problem().code(),
                                    exception.problem().message(),
                                    exception.problem().context())),
                    false);
        } catch (QueryCancelledException exception) {
            return BindingResult.cancelledResult();
        } catch (RuntimeException exception) {
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(
                            binding.source().openingDiagnostics(),
                            terminal(
                                    sourceId,
                                    "SOURCE_QUERY_FAILED",
                                    "Feature source query failed",
                                    Map.of("phase", "browser-binding"))),
                    false);
        }
    }

    private static BindingResult queryWrappedBinding(
            FeatureSourceBinding binding,
            MapViewport viewport,
            CrsRegistry registry,
            CrsDefinition mapCrs,
            CrsDefinition displayCrs,
            CrsOperation displayToMap,
            CrsOperation mapToDisplay,
            HorizontalWrap wrap,
            CancellationToken cancellation) {
        String sourceId = binding.source().metadata().identity().id();
        try {
            HorizontalWrapPlan plan = BrowserWrapSupport.validate(wrap, displayCrs, viewport);
            CrsOperation sourceToMap =
                    registry.operationFromMetadata(binding.source().metadata().crs(), mapCrs);
            CrsOperation mapToSource = registry.operation(mapCrs, sourceToMap.sourceCrs());
            LinkedHashMap<String, FeatureRecord> records = new LinkedHashMap<>();
            FeatureQueryAccounting accounting =
                    new FeatureQueryAccounting(
                            sourceId,
                            binding.tighterLimits()
                                    .orElse(binding.source().limits().queryLimits()));
            DiagnosticReport report = binding.source().openingDiagnostics();
            boolean clipped = false;
            List<HorizontalInterval> queryIntervals =
                    sourceToMap.sourceCrs().equals(CrsDefinitions.EPSG_4326)
                            ? List.of(
                                    new HorizontalInterval(
                                            wrap.canonicalMinimumX(), wrap.canonicalMaximumX()))
                            : plan.canonicalIntervals();
            for (HorizontalInterval interval : queryIntervals) {
                if (cancellation.isCancellationRequested()) {
                    return BindingResult.cancelledResult();
                }
                Envelope displayEnvelope =
                        new Envelope(
                                interval.minimumX(),
                                viewport.visibleWorldEnvelope().minY(),
                                interval.maximumX(),
                                viewport.visibleWorldEnvelope().maxY());
                QueryEnvelopeTransform mapEnvelope =
                        displayToMap.transformQueryEnvelope(displayEnvelope);
                if (mapEnvelope.status() == QueryEnvelopeStatus.OUTSIDE) {
                    continue;
                }
                QueryEnvelopeTransform sourceEnvelope =
                        mapToSource.transformQueryEnvelope(
                                mapEnvelope.transformedEnvelope().orElseThrow());
                if (sourceEnvelope.status() == QueryEnvelopeStatus.OUTSIDE) {
                    continue;
                }
                clipped |=
                        mapEnvelope.status() == QueryEnvelopeStatus.CLIPPED
                                || sourceEnvelope.status() == QueryEnvelopeStatus.CLIPPED;
                FeatureQuery query =
                        new FeatureQuery(
                                sourceEnvelope.transformedEnvelope(),
                                binding.queryAttributes(viewport.worldUnitsPerPixel()),
                                binding.tighterLimits());
                try (FeatureCursor cursor = binding.source().openCursor(query, cancellation)) {
                    while (cursor.advance()) {
                        if (cancellation.isCancellationRequested()) {
                            return BindingResult.cancelledResult();
                        }
                        FeatureRecord record = cursor.current();
                        FeatureRecord previous = records.putIfAbsent(record.id(), record);
                        if (previous == null) {
                            accounting.recordReturned(record, 1, cancellation);
                        } else if (!previous.equals(record)) {
                            throw sourceFailure(
                                    binding.source(),
                                    "SOURCE_DUPLICATE_FEATURE_ID",
                                    "Feature source returned conflicting records for one stable identifier",
                                    Map.of("featureId", record.id()));
                        }
                    }
                    report = merge(report, cursor.diagnostics());
                }
            }
            if (clipped) {
                report = merge(report, warning(sourceId, "CRS_QUERY_ENVELOPE_CLIPPED"));
            }
            if (records.isEmpty()) {
                return new BindingResult(
                        new QueryLayer(binding.id(), binding.name(), List.of()), report, false);
            }
            List<Feature> features = new ArrayList<>();
            List<String> logicalIds = new ArrayList<>();
            List<Long> copyIndices = new ArrayList<>();
            List<BrowserLabelCandidate> labels = new ArrayList<>();
            Optional<Envelope> envelope = Optional.empty();
            PortrayalEvaluationContext context = portrayalContext(viewport, displayCrs);
            int recordIndex = 0;
            int wrappedLabelRequests = 0;
            int wrappedLabelCodePoints = 0;
            for (FeatureRecord record : records.values()) {
                List<GeographicSeamSplitter.Fragment> geometryFragments =
                        wrappedFragments(record.geometry(), sourceToMap, cancellation);
                for (GeographicSeamSplitter.Fragment fragment : geometryFragments) {
                    Geometry canonical =
                            transformGeometry(
                                    fragment.geometry(), sourceToMap, mapToDisplay, cancellation);
                    for (long copy = plan.minimumVisibleCopyIndex();
                            copy <= plan.maximumVisibleCopyIndex();
                            copy++) {
                        long visualCopy = Math.addExact(copy, fragment.worldOffset());
                        Geometry visual =
                                BrowserWrapSupport.translate(
                                        canonical, BrowserWrapSupport.copyOffset(wrap, visualCopy));
                        if (BrowserWrapSupport.intersects(
                                visual.envelope(), viewport.visibleWorldEnvelope())) {
                            envelope = union(envelope, visual.envelope());
                        }
                    }
                }
                Optional<Symbol> selected =
                        symbol(binding, record.geometry(), record.attributes(), context);
                if (selected.isEmpty()) {
                    recordIndex++;
                    continue;
                }
                List<WrappedSymbolFragment> fragments =
                        wrappedSymbolFragments(
                                record.geometry(),
                                sourceToMap,
                                cancellation,
                                geometryFragments,
                                selected.orElseThrow());
                int retainedFragments = 0;
                for (long copy = plan.minimumVisibleCopyIndex();
                        copy <= plan.maximumVisibleCopyIndex();
                        copy++) {
                    for (int fragmentIndex = 0; fragmentIndex < fragments.size(); fragmentIndex++) {
                        WrappedSymbolFragment fragment = fragments.get(fragmentIndex);
                        Geometry canonical =
                                transformGeometry(
                                        fragment.geometry(),
                                        sourceToMap,
                                        mapToDisplay,
                                        cancellation);
                        long visualCopy = Math.addExact(copy, fragment.worldOffset());
                        Geometry visual =
                                BrowserWrapSupport.translate(
                                        canonical, BrowserWrapSupport.copyOffset(wrap, visualCopy));
                        if (!BrowserWrapSupport.intersects(
                                visual.envelope(), viewport.visibleWorldEnvelope())) {
                            continue;
                        }
                        if (features.size() == 200_000) {
                            throw sourceFailure(
                                    binding.source(),
                                    "SOURCE_LIMIT_EXCEEDED",
                                    "Wrapped vector output exceeds its bounded profile",
                                    Map.of(
                                            "scope",
                                            "worldWrap",
                                            "limit",
                                            "features",
                                            "maximum",
                                            "200000"));
                        }
                        if (retainedFragments++ > 0) {
                            recordWrappedOutput(
                                    binding.source(),
                                    accounting,
                                    new FeatureRecord(
                                            record.id(),
                                            record.name(),
                                            fragment.geometry(),
                                            record.attributes()),
                                    cancellation);
                        }
                        int featureIndex = features.size();
                        String displayId =
                                BrowserWrapSupport.displayId(
                                        recordIndex, visualCopy, fragmentIndex);
                        features.add(
                                new Feature(
                                        displayId,
                                        record.name(),
                                        visual,
                                        record.attributes(),
                                        fragment.symbol()));
                        logicalIds.add(record.id());
                        copyIndices.add(visualCopy);
                        envelope = union(envelope, visual.envelope());
                        if (visual instanceof PointGeometry point
                                && binding.portrayal().pointLabel().isPresent()) {
                            binding.portrayal()
                                    .resolveLabelText(
                                            record.name(),
                                            record.attributes(),
                                            viewport.worldUnitsPerPixel())
                                    .ifPresent(
                                            text -> {
                                                labels.add(
                                                        new BrowserLabelCandidate(
                                                                binding.id(),
                                                                displayId,
                                                                point.coordinate(),
                                                                fragment.symbol(),
                                                                text,
                                                                binding.portrayal()
                                                                        .pointLabel()
                                                                        .orElseThrow(),
                                                                featureIndex));
                                            });
                            if (labels.size() > wrappedLabelRequests) {
                                wrappedLabelRequests++;
                                if (wrappedLabelRequests
                                        > GreedyPointLabelPlacement.MAXIMUM_REQUESTS) {
                                    throw wrappedLabelLimitFailure(
                                            binding.source(),
                                            GreedyPointLabelPlacement.MAXIMUM_REQUESTS,
                                            wrappedLabelRequests);
                                }
                                String text = labels.getLast().text();
                                int codePoints = text.codePointCount(0, text.length());
                                if (codePoints > 262_144 - wrappedLabelCodePoints) {
                                    throw wrappedLabelLimitFailure(
                                            binding.source(),
                                            262_144,
                                            Math.addExact(wrappedLabelCodePoints, codePoints));
                                }
                                wrappedLabelCodePoints += codePoints;
                            }
                        }
                    }
                }
                recordIndex++;
            }
            return new BindingResult(
                    new QueryLayer(
                            binding.id(),
                            binding.name(),
                            features,
                            envelope,
                            labels,
                            logicalIds,
                            copyIndices),
                    report,
                    false);
        } catch (HorizontalWrapException exception) {
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(
                            binding.source().openingDiagnostics(),
                            terminal(
                                    sourceId,
                                    exception.problem().code(),
                                    "Horizontal world repetition failed",
                                    exception.problem().context())),
                    false);
        } catch (GeographicSeamSplitter.GeographicSeamException exception) {
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(
                            binding.source().openingDiagnostics(),
                            terminal(
                                    sourceId,
                                    exception.code(),
                                    "Geographic seam splitting failed",
                                    exception.context())),
                    false);
        } catch (SourceException exception) {
            if (cancellation.isCancellationRequested()
                    || exception.terminal().code().equals("SOURCE_CANCELLED")) {
                return BindingResult.cancelledResult();
            }
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(binding.source().openingDiagnostics(), exception.report()),
                    false);
        } catch (CrsException exception) {
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(
                            binding.source().openingDiagnostics(),
                            terminal(
                                    sourceId,
                                    exception.problem().code(),
                                    exception.problem().message(),
                                    exception.problem().context())),
                    false);
        } catch (QueryCancelledException exception) {
            return BindingResult.cancelledResult();
        } catch (RuntimeException exception) {
            if (exception instanceof MundaneMapException mundane) {
                throw mundane;
            }
            return new BindingResult(
                    new QueryLayer(binding.id(), binding.name(), List.of()),
                    merge(
                            binding.source().openingDiagnostics(),
                            terminal(
                                    sourceId,
                                    "SOURCE_QUERY_FAILED",
                                    "Feature source query failed",
                                    Map.of("phase", "browser-wrap"))),
                    false);
        }
    }

    static List<GeographicSeamSplitter.Fragment> wrappedFragments(
            Geometry geometry, CrsOperation sourceToMap, CancellationToken cancellation) {
        if (sourceToMap.sourceCrs().equals(CrsDefinitions.EPSG_4326)) {
            return GeographicSeamSplitter.split(geometry, cancellation).fragments();
        }
        if (sourceToMap.sourceCrs().kind() == io.github.mundanej.map.api.CrsKind.GEOGRAPHIC) {
            throw new HorizontalWrapException(
                    new HorizontalWrapProblem(
                            "WORLD_WRAP_GEOMETRY_UNSUPPORTED", Map.of("reason", "projectedSeam")));
        }
        return List.of(new GeographicSeamSplitter.Fragment(geometry, 0L));
    }

    static List<WrappedSymbolFragment> wrappedSymbolFragments(
            Geometry sourceGeometry,
            CrsOperation sourceToMap,
            CancellationToken cancellation,
            List<GeographicSeamSplitter.Fragment> geometryFragments,
            Symbol symbol) {
        if (role(sourceGeometry) != SymbolRole.FILL
                || !sourceToMap.sourceCrs().equals(CrsDefinitions.EPSG_4326)) {
            return geometryFragments.stream()
                    .map(
                            fragment ->
                                    new WrappedSymbolFragment(
                                            fragment.geometry(),
                                            fragment.worldOffset(),
                                            wrappedFragmentSymbol(symbol, fragment)))
                    .toList();
        }
        List<GeographicSeamSplitter.Fragment> boundaries =
                wrappedFragments(polygonBoundary(sourceGeometry), sourceToMap, cancellation);
        List<WrappedSymbolFragment> result = new ArrayList<>();
        for (PolygonSymbolLayer layer : polygonSymbolLayers(symbol)) {
            for (GeographicSeamSplitter.Fragment fragment : geometryFragments) {
                result.add(
                        new WrappedSymbolFragment(
                                fragment.geometry(), fragment.worldOffset(), layer.fill()));
            }
            if (layer.outline().isPresent()) {
                for (GeographicSeamSplitter.Fragment boundary : boundaries) {
                    result.add(
                            new WrappedSymbolFragment(
                                    boundary.geometry(),
                                    boundary.worldOffset(),
                                    withoutLineEndpointMarkers(layer.outline().orElseThrow())));
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<PolygonSymbolLayer> polygonSymbolLayers(Symbol symbol) {
        if (symbol instanceof CompositeSymbol composite) {
            List<PolygonSymbolLayer> result = new ArrayList<>();
            for (Symbol child : composite.children()) {
                for (PolygonSymbolLayer layer : polygonSymbolLayers(child)) {
                    Symbol fill = CompositeSymbol.of(List.of(layer.fill()), composite.opacity());
                    Optional<Symbol> outline =
                            layer.outline()
                                    .map(
                                            value ->
                                                    CompositeSymbol.of(
                                                            List.of(value), composite.opacity()));
                    result.add(new PolygonSymbolLayer(fill, outline));
                }
            }
            return List.copyOf(result);
        }
        if (symbol instanceof SolidFillSymbol fill) {
            return List.of(
                    new PolygonSymbolLayer(
                            SolidFillSymbol.of(fill.fill(), fill.opacity()),
                            fill.outline()
                                    .map(
                                            outline ->
                                                    CompositeSymbol.of(
                                                            List.of(outline), fill.opacity()))));
        }
        if (symbol instanceof HatchFillSymbol hatch) {
            return List.of(
                    new PolygonSymbolLayer(
                            HatchFillSymbol.of(
                                    hatch.pattern(),
                                    hatch.stroke(),
                                    hatch.spacing(),
                                    hatch.rotationMode(),
                                    Optional.empty(),
                                    hatch.opacity(),
                                    hatch.maxSegments()),
                            hatch.outline()
                                    .map(
                                            outline ->
                                                    CompositeSymbol.of(
                                                            List.of(outline), hatch.opacity()))));
        }
        return List.of(new PolygonSymbolLayer(symbol, Optional.empty()));
    }

    private static Geometry polygonBoundary(Geometry geometry) {
        List<CoordinateSequence> rings = new ArrayList<>();
        if (geometry instanceof PolygonGeometry polygon) {
            rings.add(polygon.exterior());
            rings.addAll(polygon.holes());
        } else {
            MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
            for (int ring = 0; ring < polygons.ringCount(); ring++) {
                rings.add(
                        slice(
                                polygons.coordinates(),
                                polygons.ringOffset(ring),
                                polygons.ringOffset(ring + 1)));
            }
        }
        return rings.size() == 1
                ? new LineStringGeometry(rings.getFirst())
                : MultiLineStringGeometry.ofParts(rings);
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

    private static Symbol withoutLineEndpointMarkers(Symbol symbol) {
        if (symbol instanceof SolidLineSymbol line) {
            return SolidLineSymbol.of(line.stroke(), line.opacity());
        }
        if (symbol instanceof CompositeSymbol composite && composite.role() == SymbolRole.LINE) {
            return CompositeSymbol.of(
                    composite.children().stream()
                            .map(FeatureSourceQueryEngine::withoutLineEndpointMarkers)
                            .toList(),
                    composite.opacity());
        }
        return symbol;
    }

    private static Symbol wrappedFragmentSymbol(
            Symbol symbol, GeographicSeamSplitter.Fragment fragment) {
        if (fragment.retainsLogicalStart() && fragment.retainsLogicalEnd()) {
            return symbol;
        }
        if (symbol instanceof io.github.mundanej.map.api.SolidLineSymbol line) {
            return io.github.mundanej.map.api.SolidLineSymbol.of(
                    line.stroke(),
                    fragment.retainsLogicalStart() ? line.startMarker() : Optional.empty(),
                    fragment.retainsLogicalEnd() ? line.endMarker() : Optional.empty(),
                    line.opacity());
        }
        if (symbol instanceof io.github.mundanej.map.api.CompositeSymbol composite
                && composite.role() == SymbolRole.LINE) {
            return io.github.mundanej.map.api.CompositeSymbol.of(
                    composite.children().stream()
                            .map(child -> wrappedFragmentSymbol(child, fragment))
                            .toList(),
                    composite.opacity());
        }
        return symbol;
    }

    record WrappedSymbolFragment(Geometry geometry, long worldOffset, Symbol symbol) {}

    private record PolygonSymbolLayer(Symbol fill, Optional<Symbol> outline) {}

    private static Optional<Symbol> symbol(
            FeatureSourceBinding binding,
            Geometry geometry,
            Map<String, Object> attributes,
            PortrayalEvaluationContext context) {
        SymbolRole role = role(geometry);
        ResolvedFeaturePortrayal resolved =
                binding.portrayal()
                        .resolveAll(
                                attributes,
                                context.withGeometryType(
                                        PortrayalGeometryType.fromGeometry(geometry)));
        return resolved.forRole(role);
    }

    static SymbolRole role(Geometry geometry) {
        if (geometry instanceof PointGeometry || geometry instanceof MultiPointGeometry) {
            return SymbolRole.MARKER;
        }
        if (geometry instanceof LineStringGeometry || geometry instanceof MultiLineStringGeometry) {
            return SymbolRole.LINE;
        }
        return SymbolRole.FILL;
    }

    static PortrayalEvaluationContext portrayalContext(
            MapViewport viewport, CrsDefinition displayCrs) {
        double denominator = viewport.worldUnitsPerPixel() / 0.00028;
        if (!Double.isFinite(denominator) || denominator < 0.0) {
            throw new IllegalArgumentException("viewport cannot provide a scale denominator");
        }
        if (displayCrs.equals(CrsDefinitions.EPSG_3857)) {
            double zoom =
                    StrictMath.log(
                                    displayCrs.coordinateDomain().width()
                                            / (512.0 * viewport.worldUnitsPerPixel()))
                            / StrictMath.log(2.0);
            if (Double.isFinite(zoom)) {
                return PortrayalEvaluationContext.atScaleAndZoom(denominator, zoom);
            }
        }
        return PortrayalEvaluationContext.atScale(denominator);
    }

    private static Optional<Envelope> union(Optional<Envelope> aggregate, Envelope next) {
        return Optional.of(aggregate.map(value -> value.union(next)).orElse(next));
    }

    static Geometry transformGeometry(
            Geometry geometry,
            CrsOperation sourceToMap,
            CrsOperation mapToDisplay,
            CancellationToken cancellation) {
        if (geometry instanceof PointGeometry point) {
            return new PointGeometry(
                    transform(point.coordinate(), sourceToMap, mapToDisplay, cancellation));
        }
        if (geometry instanceof MultiPointGeometry points) {
            return new MultiPointGeometry(
                    transform(points.coordinates(), sourceToMap, mapToDisplay, cancellation));
        }
        if (geometry instanceof LineStringGeometry line) {
            return new LineStringGeometry(
                    transform(line.coordinates(), sourceToMap, mapToDisplay, cancellation));
        }
        if (geometry instanceof MultiLineStringGeometry lines) {
            return MultiLineStringGeometry.of(
                    transform(lines.coordinates(), sourceToMap, mapToDisplay, cancellation),
                    lines.partOffsets());
        }
        if (geometry instanceof PolygonGeometry polygon) {
            List<CoordinateSequence> holes =
                    polygon.holes().stream()
                            .map(value -> transform(value, sourceToMap, mapToDisplay, cancellation))
                            .toList();
            return new PolygonGeometry(
                    transform(polygon.exterior(), sourceToMap, mapToDisplay, cancellation), holes);
        }
        MultiPolygonGeometry polygons = (MultiPolygonGeometry) geometry;
        return MultiPolygonGeometry.of(
                transform(polygons.coordinates(), sourceToMap, mapToDisplay, cancellation),
                polygons.ringOffsets(),
                polygons.polygonRingOffsets());
    }

    private static CoordinateSequence transform(
            CoordinateSequence source,
            CrsOperation first,
            CrsOperation second,
            CancellationToken cancellation) {
        double[] packed = new double[Math.multiplyExact(source.size(), 2)];
        int target = 0;
        for (int index = 0; index < source.size(); index++) {
            Coordinate transformed =
                    transform(
                            new Coordinate(source.x(index), source.y(index)),
                            first,
                            second,
                            cancellation);
            packed[target++] = transformed.x();
            packed[target++] = transformed.y();
        }
        return CoordinateSequence.of(packed);
    }

    private static Coordinate transform(
            Coordinate source,
            CrsOperation first,
            CrsOperation second,
            CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw new QueryCancelledException();
        }
        return second.transform(first.transform(source));
    }

    private static DiagnosticReport warning(String sourceId, String code) {
        String message =
                code.equals("CRS_QUERY_ENVELOPE_OUTSIDE_DOMAIN")
                        ? "Visible query envelope is outside the CRS operation domain"
                        : "Visible query envelope was clipped by the CRS operation domain";
        return new DiagnosticReport(
                List.of(
                        new SourceDiagnostic(
                                code,
                                DiagnosticSeverity.WARNING,
                                sourceId,
                                Optional.of(DiagnosticLocation.empty()),
                                message,
                                Map.of("operation", "browser-viewport-query"))),
                0);
    }

    private static DiagnosticReport terminal(
            String sourceId, String code, String message, Map<String, String> context) {
        return new DiagnosticReport(
                List.of(
                        new SourceDiagnostic(
                                code,
                                DiagnosticSeverity.ERROR,
                                sourceId,
                                Optional.of(DiagnosticLocation.empty()),
                                message,
                                context)),
                0);
    }

    private static SourceException sourceFailure(
            io.github.mundanej.map.api.FeatureSource source,
            String code,
            String message,
            Map<String, String> context) {
        SourceDiagnostic diagnostic =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        source.metadata().identity().id(),
                        Optional.of(DiagnosticLocation.empty()),
                        message,
                        context);
        DiagnosticReport report = new DiagnosticReport(List.of(diagnostic), 0);
        return new SourceException(report, diagnostic);
    }

    private static SourceException wrappedLabelLimitFailure(
            io.github.mundanej.map.api.FeatureSource source, int maximum, int attempted) {
        return sourceFailure(
                source,
                "SOURCE_LIMIT_EXCEEDED",
                "Wrapped point-label output exceeds its bounded profile",
                Map.of(
                        "scope",
                        "worldWrap",
                        "limit",
                        "labels",
                        "maximum",
                        Integer.toString(maximum),
                        "attempted",
                        Integer.toString(attempted)));
    }

    private static void recordWrappedOutput(
            io.github.mundanej.map.api.FeatureSource source,
            FeatureQueryAccounting accounting,
            FeatureRecord record,
            CancellationToken cancellation) {
        try {
            accounting.recordReturned(record, 1, cancellation);
        } catch (SourceException failure) {
            if (!failure.terminal().code().equals("SOURCE_LIMIT_EXCEEDED")) {
                throw failure;
            }
            String limit =
                    switch (failure.terminal().context().get("limit")) {
                        case "recordsReturned" -> "features";
                        case "coordinatesReturned" -> "coordinates";
                        case "ownedPayloadBytes" -> "ownedBytes";
                        default -> "ownedBytes";
                    };
            LinkedHashMap<String, String> context = new LinkedHashMap<>();
            context.put("scope", "worldWrap");
            context.put("limit", limit);
            copyContext(failure.terminal().context(), context, "requested");
            copyContext(failure.terminal().context(), context, "maximum");
            throw sourceFailure(
                    source,
                    "SOURCE_LIMIT_EXCEEDED",
                    "Wrapped vector output exceeds its bounded profile",
                    context);
        }
    }

    private static void copyContext(
            Map<String, String> source, Map<String, String> target, String key) {
        String value = source.get(key);
        if (value != null) {
            target.put(key, value);
        }
    }

    private static DiagnosticReport merge(DiagnosticReport... reports) {
        List<SourceDiagnostic> entries = new ArrayList<>();
        long omitted = 0;
        for (DiagnosticReport report : reports) {
            omitted = Math.addExact(omitted, report.omittedWarningCount());
            for (SourceDiagnostic entry : report.entries()) {
                if (!entries.isEmpty()
                        && entries.getLast().severity() == DiagnosticSeverity.ERROR) {
                    break;
                }
                entries.add(entry);
            }
        }
        return new DiagnosticReport(entries, omitted);
    }

    record RequestBinding(FeatureSourceBinding binding, boolean visible) {
        RequestBinding {
            Objects.requireNonNull(binding, "binding");
        }
    }

    record Result(List<Layer> layers, Map<String, DiagnosticReport> reports, boolean cancelled) {
        Result {
            layers = List.copyOf(layers);
            reports = Collections.unmodifiableMap(new LinkedHashMap<>(reports));
        }

        static Result cancelledResult() {
            return new Result(List.of(), Map.of(), true);
        }
    }

    private record BindingResult(Layer layer, DiagnosticReport report, boolean cancelled) {
        private static BindingResult cancelledResult() {
            return new BindingResult(
                    new QueryLayer("cancelled", "cancelled", List.of()),
                    DiagnosticReport.empty(),
                    true);
        }
    }

    private record QueryLayer(
            String id,
            String name,
            List<Feature> features,
            Optional<Envelope> envelope,
            List<BrowserLabelCandidate> browserLabelCandidates,
            List<String> logicalFeatureIds,
            List<Long> copyIndices)
            implements Layer, BrowserLabelLayer, BrowserLogicalLayer {
        private QueryLayer {
            features = List.copyOf(features);
            Objects.requireNonNull(envelope, "envelope");
            browserLabelCandidates = List.copyOf(browserLabelCandidates);
            logicalFeatureIds = List.copyOf(logicalFeatureIds);
            copyIndices = List.copyOf(copyIndices);
            if (features.size() != logicalFeatureIds.size()
                    || features.size() != copyIndices.size()) {
                throw new IllegalArgumentException("logical feature metadata must match features");
            }
        }

        private QueryLayer(String id, String name, List<Feature> features) {
            this(
                    id,
                    name,
                    features,
                    featureEnvelope(features),
                    List.of(),
                    features.stream().map(Feature::id).toList(),
                    java.util.Collections.nCopies(features.size(), 0L));
        }

        private QueryLayer(
                String id,
                String name,
                List<Feature> features,
                Optional<Envelope> envelope,
                List<BrowserLabelCandidate> labels) {
            this(
                    id,
                    name,
                    features,
                    envelope,
                    labels,
                    features.stream().map(Feature::id).toList(),
                    java.util.Collections.nCopies(features.size(), 0L));
        }

        @Override
        public String logicalFeatureId(int featureIndex) {
            return logicalFeatureIds.get(featureIndex);
        }

        @Override
        public long copyIndex(int featureIndex) {
            return copyIndices.get(featureIndex);
        }

        private static Optional<Envelope> featureEnvelope(List<Feature> features) {
            Envelope aggregate = null;
            for (Feature feature : features) {
                aggregate =
                        aggregate == null
                                ? feature.geometry().envelope()
                                : aggregate.union(feature.geometry().envelope());
            }
            return Optional.ofNullable(aggregate);
        }
    }

    private static final class QueryCancelledException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private QueryCancelledException() {
            super(null, null, false, false);
        }
    }
}
