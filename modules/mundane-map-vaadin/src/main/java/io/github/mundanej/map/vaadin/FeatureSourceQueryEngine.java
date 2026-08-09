package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CancellationToken;
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
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsOperation;
import io.github.mundanej.map.core.CrsRegistry;
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
        Objects.requireNonNull(bindings, "bindings");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(mapCrs, "mapCrs");
        Objects.requireNonNull(displayCrs, "displayCrs");
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
                    queryBinding(
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
            List<BrowserLabelCandidate> browserLabelCandidates)
            implements Layer, BrowserLabelLayer {
        private QueryLayer {
            features = List.copyOf(features);
            Objects.requireNonNull(envelope, "envelope");
            browserLabelCandidates = List.copyOf(browserLabelCandidates);
        }

        private QueryLayer(String id, String name, List<Feature> features) {
            this(id, name, features, featureEnvelope(features), List.of());
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
