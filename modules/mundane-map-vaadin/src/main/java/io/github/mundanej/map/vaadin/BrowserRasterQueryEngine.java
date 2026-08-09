package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.core.ElevationRasterization;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.RasterGridWindows;
import io.github.mundanej.map.core.RasterRequestAccounting;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Serialized JDK-only planning and capture for browser RGBA windows. */
final class BrowserRasterQueryEngine {
    Result query(
            List<RasterSourceBinding> rasters,
            List<ElevationSourceBinding> elevations,
            MapViewport viewport,
            CrsDefinition displayCrs,
            CancellationToken cancellation) {
        Objects.requireNonNull(rasters, "rasters");
        Objects.requireNonNull(elevations, "elevations");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(displayCrs, "displayCrs");
        Objects.requireNonNull(cancellation, "cancellation");
        List<BrowserRasterWindow> windows = new ArrayList<>();
        LinkedHashMap<String, DiagnosticReport> reports = new LinkedHashMap<>();
        for (RasterSourceBinding binding : rasters) {
            if (cancellation.isCancellationRequested()) {
                return Result.cancelledResult();
            }
            BindingResult result = queryRaster(binding, viewport, displayCrs, cancellation);
            if (result.cancelled()) {
                return Result.cancelledResult();
            }
            result.window().ifPresent(windows::add);
            retainReport(reports, binding.id(), result.report());
        }
        for (ElevationSourceBinding binding : elevations) {
            if (cancellation.isCancellationRequested()) {
                return Result.cancelledResult();
            }
            BindingResult result = queryElevation(binding, viewport, displayCrs, cancellation);
            if (result.cancelled()) {
                return Result.cancelledResult();
            }
            result.window().ifPresent(windows::add);
            retainReport(reports, binding.id(), result.report());
        }
        return new Result(List.copyOf(windows), Collections.unmodifiableMap(reports), false);
    }

    private static BindingResult queryRaster(
            RasterSourceBinding binding,
            MapViewport viewport,
            CrsDefinition displayCrs,
            CancellationToken cancellation) {
        RasterSourceMetadata metadata = binding.source().metadata();
        DiagnosticReport opening = binding.source().openingDiagnostics();
        try {
            requireCrs(metadata.crs().flatMap(value -> value.definition()), displayCrs);
            RasterGridPlacement placement = metadata.gridPlacement().orElseThrow();
            if (binding.options().opacity() == 0.0) {
                return new BindingResult(Optional.empty(), opening, false);
            }
            Optional<RasterWindow> visible =
                    RasterGridWindows.visibleWindow(metadata, viewport.visibleWorldEnvelope());
            if (visible.isEmpty()) {
                return new BindingResult(Optional.empty(), opening, false);
            }
            RasterWindow window = visible.orElseThrow();
            RasterGridWindows.OutputSize output =
                    RasterGridWindows.outputSize(metadata, window, viewport);
            RasterRequest request =
                    new RasterRequest(
                            window,
                            output.width(),
                            output.height(),
                            binding.options().interpolation(),
                            binding.tighterLimits());
            RasterRequestAccounting accounting =
                    new RasterRequestAccounting(
                            metadata.identity().id(), binding.effectiveLimits(), cancellation);
            accounting.checkpoint();
            accounting.validateWindow(metadata, window);
            accounting.chargeSourcePixels(
                    Math.multiplyExact((long) window.width(), window.height()));
            long outputPixels = accounting.validateOutput(output.width(), output.height());
            accounting.chargeIntermediateBytes(Math.multiplyExact(outputPixels, 8L));
            accounting.chargePublishedBytes(Math.multiplyExact(outputPixels, 4L));
            RasterRead read = binding.source().read(request, cancellation);
            requireRead(request, read);
            accounting.checkpoint();
            return new BindingResult(
                    Optional.of(
                            new BrowserRasterWindow(
                                    binding.id(),
                                    binding.name(),
                                    read.pixels(),
                                    RasterGridWindows.mapBounds(metadata, window),
                                    RasterGridWindows.mapBounds(metadata, window),
                                    Optional.of(placement),
                                    window,
                                    binding.options())),
                    merge(opening, read.diagnostics()),
                    false);
        } catch (SourceException exception) {
            if (cancelled(cancellation, exception)) {
                return BindingResult.cancelledResult();
            }
            return new BindingResult(Optional.empty(), merge(opening, exception.report()), false);
        } catch (RuntimeException exception) {
            if (cancellation.isCancellationRequested()) {
                return BindingResult.cancelledResult();
            }
            return new BindingResult(
                    Optional.empty(),
                    merge(opening, terminal(metadata.identity().id(), failureCode(exception))),
                    false);
        }
    }

    private static BindingResult queryElevation(
            ElevationSourceBinding binding,
            MapViewport viewport,
            CrsDefinition displayCrs,
            CancellationToken cancellation) {
        ElevationSourceMetadata metadata = binding.source().metadata();
        DiagnosticReport opening = binding.source().openingDiagnostics();
        try {
            requireCrs(metadata.crs().definition(), displayCrs);
            if (binding.options().opacity() == 0.0) {
                return new BindingResult(Optional.empty(), opening, false);
            }
            Optional<ElevationRasterization.Plan> planned =
                    ElevationRasterization.plan(
                            metadata,
                            viewport.visibleWorldEnvelope(),
                            viewport.worldUnitsPerPixel(),
                            binding.options().interpolation(),
                            binding.requestLimits());
            if (planned.isEmpty()) {
                return new BindingResult(Optional.empty(), opening, false);
            }
            ElevationRasterization.Plan plan = planned.orElseThrow();
            RasterRead read =
                    ElevationRasterization.rasterize(
                            binding.source(), plan, binding.style(), cancellation);
            requireRead(plan.request(), read);
            return new BindingResult(
                    Optional.of(
                            new BrowserRasterWindow(
                                    binding.id(),
                                    binding.name(),
                                    read.pixels(),
                                    plan.imageMapBounds(),
                                    plan.clipMapBounds(),
                                    Optional.empty(),
                                    plan.request().sourceWindow(),
                                    binding.options())),
                    merge(opening, read.diagnostics()),
                    false);
        } catch (SourceException exception) {
            if (cancelled(cancellation, exception)) {
                return BindingResult.cancelledResult();
            }
            return new BindingResult(Optional.empty(), merge(opening, exception.report()), false);
        } catch (RuntimeException exception) {
            if (cancellation.isCancellationRequested()) {
                return BindingResult.cancelledResult();
            }
            return new BindingResult(
                    Optional.empty(),
                    merge(opening, terminal(metadata.identity().id(), failureCode(exception))),
                    false);
        }
    }

    private static void requireCrs(Optional<CrsDefinition> source, CrsDefinition display) {
        if (source.isEmpty() || !source.orElseThrow().equals(display)) {
            throw new IllegalArgumentException("Raster CRS must exactly equal the display CRS");
        }
    }

    private static void requireRead(RasterRequest request, RasterRead read) {
        Objects.requireNonNull(read, "read");
        if (!read.sourceWindow().equals(request.sourceWindow())
                || read.pixels().width() != request.outputWidth()
                || read.pixels().height() != request.outputHeight()) {
            throw new IllegalStateException("Raster source returned a mismatched window");
        }
    }

    private static boolean cancelled(CancellationToken token, SourceException exception) {
        return token.isCancellationRequested()
                || exception.terminal().code().equals("SOURCE_CANCELLED");
    }

    private static String failureCode(RuntimeException exception) {
        return exception instanceof IllegalArgumentException
                ? "RASTER_CONFIGURATION_UNSUPPORTED"
                : "SOURCE_QUERY_FAILED";
    }

    private static DiagnosticReport terminal(String sourceId, String code) {
        return new DiagnosticReport(
                List.of(
                        new SourceDiagnostic(
                                code,
                                DiagnosticSeverity.ERROR,
                                sourceId,
                                Optional.of(DiagnosticLocation.empty()),
                                code.equals("RASTER_CONFIGURATION_UNSUPPORTED")
                                        ? "Raster source placement or CRS is unsupported"
                                        : "Raster source query failed",
                                Map.of("phase", "browser-raster"))),
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

    private static void retainReport(
            Map<String, DiagnosticReport> reports, String id, DiagnosticReport report) {
        if (!report.entries().isEmpty() || report.omittedWarningCount() != 0) {
            reports.put(id, report);
        }
    }

    record Result(
            List<BrowserRasterWindow> windows,
            Map<String, DiagnosticReport> reports,
            boolean cancelled) {
        Result {
            windows = List.copyOf(windows);
            reports = Collections.unmodifiableMap(new LinkedHashMap<>(reports));
        }

        static Result cancelledResult() {
            return new Result(List.of(), Map.of(), true);
        }
    }

    private record BindingResult(
            Optional<BrowserRasterWindow> window, DiagnosticReport report, boolean cancelled) {
        private static BindingResult cancelledResult() {
            return new BindingResult(Optional.empty(), DiagnosticReport.empty(), true);
        }
    }
}
