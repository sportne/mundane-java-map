package io.github.mundanej.map.vaadin;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.core.ElevationRasterization;
import io.github.mundanej.map.core.HorizontalInterval;
import io.github.mundanej.map.core.HorizontalWrap;
import io.github.mundanej.map.core.HorizontalWrapPlan;
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
        return query(rasters, elevations, viewport, displayCrs, Optional.empty(), cancellation);
    }

    Result query(
            List<RasterSourceBinding> rasters,
            List<ElevationSourceBinding> elevations,
            MapViewport viewport,
            CrsDefinition displayCrs,
            Optional<HorizontalWrap> horizontalWrap,
            CancellationToken cancellation) {
        Objects.requireNonNull(rasters, "rasters");
        Objects.requireNonNull(elevations, "elevations");
        Objects.requireNonNull(viewport, "viewport");
        Objects.requireNonNull(displayCrs, "displayCrs");
        Objects.requireNonNull(horizontalWrap, "horizontalWrap");
        Objects.requireNonNull(cancellation, "cancellation");
        List<BrowserRasterWindow> windows = new ArrayList<>();
        LinkedHashMap<String, DiagnosticReport> reports = new LinkedHashMap<>();
        for (int bindingIndex = 0; bindingIndex < rasters.size(); bindingIndex++) {
            RasterSourceBinding binding = rasters.get(bindingIndex);
            if (cancellation.isCancellationRequested()) {
                return Result.cancelledResult();
            }
            BindingWindows result =
                    binding.horizontalWrapMode() == BrowserHorizontalWrapMode.REPEAT_X
                            ? queryWrappedRaster(
                                    binding,
                                    viewport,
                                    displayCrs,
                                    horizontalWrap.orElseThrow(),
                                    bindingIndex,
                                    cancellation)
                            : BindingWindows.from(
                                    queryRaster(
                                            binding,
                                            viewport.visibleWorldEnvelope(),
                                            viewport,
                                            displayCrs,
                                            null,
                                            cancellation));
            if (result.cancelled()) {
                return Result.cancelledResult();
            }
            windows.addAll(result.windows());
            retainReport(reports, binding.id(), result.report());
        }
        for (int elevationIndex = 0; elevationIndex < elevations.size(); elevationIndex++) {
            ElevationSourceBinding binding = elevations.get(elevationIndex);
            if (cancellation.isCancellationRequested()) {
                return Result.cancelledResult();
            }
            BindingWindows result =
                    binding.horizontalWrapMode() == BrowserHorizontalWrapMode.REPEAT_X
                            ? queryWrappedElevation(
                                    binding,
                                    viewport,
                                    displayCrs,
                                    horizontalWrap.orElseThrow(),
                                    rasters.size() + elevationIndex,
                                    cancellation)
                            : BindingWindows.from(
                                    queryElevation(
                                            binding,
                                            viewport.visibleWorldEnvelope(),
                                            viewport,
                                            displayCrs,
                                            null,
                                            cancellation));
            if (result.cancelled()) {
                return Result.cancelledResult();
            }
            windows.addAll(result.windows());
            retainReport(reports, binding.id(), result.report());
        }
        return new Result(List.copyOf(windows), Collections.unmodifiableMap(reports), false);
    }

    private static BindingResult queryRaster(
            RasterSourceBinding binding,
            Envelope visibleEnvelope,
            MapViewport viewport,
            CrsDefinition displayCrs,
            RasterRequestAccounting sharedAccounting,
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
                    RasterGridWindows.visibleWindow(metadata, visibleEnvelope);
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
                    sharedAccounting == null
                            ? new RasterRequestAccounting(
                                    metadata.identity().id(),
                                    binding.effectiveLimits(),
                                    cancellation)
                            : sharedAccounting;
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
                    mergeOperation(opening, read.diagnostics()),
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
            Envelope visibleEnvelope,
            MapViewport viewport,
            CrsDefinition displayCrs,
            RasterRequestAccounting sharedAccounting,
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
                            visibleEnvelope,
                            viewport.worldUnitsPerPixel(),
                            binding.options().interpolation(),
                            binding.requestLimits());
            if (planned.isEmpty()) {
                return new BindingResult(Optional.empty(), opening, false);
            }
            ElevationRasterization.Plan plan = planned.orElseThrow();
            if (sharedAccounting != null) {
                RasterWindow aggregateWindow = plan.request().sourceWindow();
                sharedAccounting.checkpoint();
                sharedAccounting.validateWindow(
                        metadata.columnCount(), metadata.rowCount(), aggregateWindow);
                RasterWindow chargedWindow =
                        binding.style().hillshade().isPresent()
                                ? expandedElevationWindow(metadata, aggregateWindow)
                                : aggregateWindow;
                sharedAccounting.chargeSourcePixels(
                        Math.multiplyExact((long) chargedWindow.width(), chargedWindow.height()));
                long outputPixels =
                        sharedAccounting.validateOutput(
                                plan.request().outputWidth(), plan.request().outputHeight());
                sharedAccounting.chargeIntermediateBytes(Math.multiplyExact(outputPixels, 4L));
                sharedAccounting.chargePublishedBytes(Math.multiplyExact(outputPixels, 4L));
            }
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
                    mergeOperation(opening, read.diagnostics()),
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

    private static BindingWindows queryWrappedRaster(
            RasterSourceBinding binding,
            MapViewport viewport,
            CrsDefinition displayCrs,
            HorizontalWrap wrap,
            int bindingIndex,
            CancellationToken cancellation) {
        BrowserWrapSupport.validateRepeatingRaster(binding.source().metadata(), displayCrs, wrap);
        HorizontalWrapPlan plan = BrowserWrapSupport.validate(wrap, displayCrs, viewport);
        Envelope sourceBounds = binding.source().metadata().mapBounds().orElseThrow();
        List<BrowserRasterWindow> canonical = new ArrayList<>();
        DiagnosticReport opening = binding.source().openingDiagnostics();
        DiagnosticReport report = opening;
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(
                        binding.source().metadata().identity().id(),
                        binding.effectiveLimits(),
                        cancellation);
        for (HorizontalInterval interval : plan.canonicalIntervals()) {
            Envelope canonicalClip =
                    new Envelope(
                            interval.minimumX(),
                            viewport.visibleWorldEnvelope().minY(),
                            interval.maximumX(),
                            viewport.visibleWorldEnvelope().maxY());
            BindingResult result =
                    queryRaster(
                            binding,
                            actualRasterEnvelope(canonicalClip, sourceBounds, wrap),
                            viewport,
                            displayCrs,
                            accounting,
                            cancellation);
            if (result.cancelled()) {
                return BindingWindows.cancelledResult();
            }
            report = merge(report, withoutOpening(result.report(), opening));
            if (terminal(result.report())) {
                return new BindingWindows(List.of(), report, false);
            }
            result.window()
                    .map(window -> canonicalRasterWindow(window, sourceBounds, wrap))
                    .ifPresent(canonical::add);
        }
        return repeatWindows(
                canonical, report, plan, wrap, viewport.visibleWorldEnvelope(), bindingIndex);
    }

    private static BindingWindows queryWrappedElevation(
            ElevationSourceBinding binding,
            MapViewport viewport,
            CrsDefinition displayCrs,
            HorizontalWrap wrap,
            int bindingIndex,
            CancellationToken cancellation) {
        BrowserWrapSupport.validateRepeatingEnvelope(
                binding.source().metadata().sampleBounds(),
                binding.source().metadata().crs().definition(),
                displayCrs,
                wrap);
        HorizontalWrapPlan plan = BrowserWrapSupport.validate(wrap, displayCrs, viewport);
        Envelope sourceBounds = binding.source().metadata().sampleBounds();
        List<BrowserRasterWindow> canonical = new ArrayList<>();
        DiagnosticReport opening = binding.source().openingDiagnostics();
        DiagnosticReport report = opening;
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(
                        binding.source().metadata().identity().id(),
                        binding.requestLimits(),
                        cancellation);
        for (HorizontalInterval interval : plan.canonicalIntervals()) {
            Envelope canonicalClip =
                    new Envelope(
                            interval.minimumX(),
                            viewport.visibleWorldEnvelope().minY(),
                            interval.maximumX(),
                            viewport.visibleWorldEnvelope().maxY());
            BindingResult result =
                    queryElevation(
                            binding,
                            actualRasterEnvelope(canonicalClip, sourceBounds, wrap),
                            viewport,
                            displayCrs,
                            accounting,
                            cancellation);
            if (result.cancelled()) {
                return BindingWindows.cancelledResult();
            }
            report = merge(report, withoutOpening(result.report(), opening));
            if (terminal(result.report())) {
                return new BindingWindows(List.of(), report, false);
            }
            result.window()
                    .map(window -> canonicalRasterWindow(window, sourceBounds, wrap))
                    .ifPresent(canonical::add);
        }
        return repeatWindows(
                canonical, report, plan, wrap, viewport.visibleWorldEnvelope(), bindingIndex);
    }

    private static BindingWindows repeatWindows(
            List<BrowserRasterWindow> canonical,
            DiagnosticReport report,
            HorizontalWrapPlan plan,
            HorizontalWrap wrap,
            Envelope visible,
            int bindingIndex) {
        List<BrowserRasterWindow> repeated = new ArrayList<>();
        for (int fragmentIndex = 0; fragmentIndex < canonical.size(); fragmentIndex++) {
            BrowserRasterWindow window = canonical.get(fragmentIndex);
            for (long copy = plan.minimumVisibleCopyIndex();
                    copy <= plan.maximumVisibleCopyIndex();
                    copy++) {
                double offset = BrowserWrapSupport.copyOffset(wrap, copy);
                Envelope image = BrowserWrapSupport.translate(window.imageMapBounds(), offset);
                Envelope clip = BrowserWrapSupport.translate(window.clipMapBounds(), offset);
                if (!BrowserWrapSupport.intersects(clip, visible)) {
                    continue;
                }
                Optional<RasterGridPlacement> placement =
                        window.placement().map(value -> translatedPlacement(value, image, offset));
                repeated.add(
                        new BrowserRasterWindow(
                                "__raster_" + bindingIndex + "_" + copy + "_" + fragmentIndex,
                                window.bindingId(),
                                window.bindingName(),
                                window.pixels(),
                                image,
                                clip,
                                placement,
                                window.sourceWindow(),
                                window.options(),
                                copy));
            }
        }
        return new BindingWindows(List.copyOf(repeated), report, false);
    }

    private static BrowserRasterWindow canonicalRasterWindow(
            BrowserRasterWindow window, Envelope sourceBounds, HorizontalWrap wrap) {
        Envelope image =
                BrowserWrapSupport.normalizeCanonicalEdges(
                        canonicalRasterEnvelope(window.imageMapBounds(), sourceBounds, wrap), wrap);
        Envelope clip =
                BrowserWrapSupport.normalizeCanonicalEdges(
                        canonicalRasterEnvelope(window.clipMapBounds(), sourceBounds, wrap), wrap);
        Optional<RasterGridPlacement> placement =
                window.placement()
                        .map(
                                value -> {
                                    if (value.kind() == RasterGridPlacement.Kind.AXIS_ALIGNED) {
                                        return RasterGridPlacement.axisAligned(image);
                                    }
                                    RasterAffineTransform transform =
                                            value.affineTransform().orElseThrow();
                                    double factor = wrap.period() / sourceBounds.width();
                                    return RasterGridPlacement.affine(
                                            RasterAffineTransform.of(
                                                    transform.a() * factor,
                                                    transform.d(),
                                                    transform.b(),
                                                    transform.e(),
                                                    wrap.canonicalMinimumX()
                                                            + (transform.c() - sourceBounds.minX())
                                                                    * factor,
                                                    transform.f()));
                                });
        return new BrowserRasterWindow(
                window.bindingId(),
                window.bindingName(),
                window.pixels(),
                image,
                clip,
                placement,
                window.sourceWindow(),
                window.options());
    }

    private static Envelope canonicalRasterEnvelope(
            Envelope actual, Envelope sourceBounds, HorizontalWrap wrap) {
        double factor = wrap.period() / sourceBounds.width();
        double minimumX = wrap.canonicalMinimumX() + (actual.minX() - sourceBounds.minX()) * factor;
        double maximumX = wrap.canonicalMinimumX() + (actual.maxX() - sourceBounds.minX()) * factor;
        if (!Double.isFinite(minimumX) || !Double.isFinite(maximumX)) {
            throw new ArithmeticException("Canonical raster bounds must remain finite");
        }
        return new Envelope(minimumX, actual.minY(), maximumX, actual.maxY());
    }

    private static Envelope actualRasterEnvelope(
            Envelope canonical, Envelope sourceBounds, HorizontalWrap wrap) {
        double factor = sourceBounds.width() / wrap.period();
        double minimumX =
                sourceBounds.minX() + (canonical.minX() - wrap.canonicalMinimumX()) * factor;
        double maximumX =
                sourceBounds.minX() + (canonical.maxX() - wrap.canonicalMinimumX()) * factor;
        if (!Double.isFinite(minimumX) || !Double.isFinite(maximumX)) {
            throw new ArithmeticException("Actual raster request bounds must remain finite");
        }
        return new Envelope(minimumX, canonical.minY(), maximumX, canonical.maxY());
    }

    private static RasterWindow expandedElevationWindow(
            ElevationSourceMetadata metadata, RasterWindow window) {
        int column = Math.max(0, window.column() - 1);
        int row = Math.max(0, window.row() - 1);
        int endColumn = Math.min(metadata.columnCount(), Math.toIntExact(window.endColumn()) + 1);
        int endRow = Math.min(metadata.rowCount(), Math.toIntExact(window.endRow()) + 1);
        return new RasterWindow(column, row, endColumn - column, endRow - row);
    }

    private static boolean terminal(DiagnosticReport report) {
        return !report.entries().isEmpty()
                && report.entries().getLast().severity() == DiagnosticSeverity.ERROR;
    }

    private static RasterGridPlacement translatedPlacement(
            RasterGridPlacement placement, Envelope image, double offset) {
        if (placement.kind() == RasterGridPlacement.Kind.AXIS_ALIGNED) {
            return RasterGridPlacement.axisAligned(image);
        }
        RasterAffineTransform transform = placement.affineTransform().orElseThrow();
        return RasterGridPlacement.affine(
                RasterAffineTransform.of(
                        transform.a(),
                        transform.d(),
                        transform.b(),
                        transform.e(),
                        transform.c() + offset,
                        transform.f()));
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

    private static DiagnosticReport mergeOperation(
            DiagnosticReport opening, DiagnosticReport operation) {
        return opening.equals(operation) ? opening : merge(opening, operation);
    }

    private static DiagnosticReport withoutOpening(
            DiagnosticReport report, DiagnosticReport opening) {
        int prefix = opening.entries().size();
        if (prefix > report.entries().size()
                || !report.entries().subList(0, prefix).equals(opening.entries())
                || report.omittedWarningCount() < opening.omittedWarningCount()) {
            return report;
        }
        return new DiagnosticReport(
                report.entries().subList(prefix, report.entries().size()),
                report.omittedWarningCount() - opening.omittedWarningCount());
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

    private record BindingWindows(
            List<BrowserRasterWindow> windows, DiagnosticReport report, boolean cancelled) {
        private BindingWindows {
            windows = List.copyOf(windows);
            Objects.requireNonNull(report, "report");
        }

        private static BindingWindows from(BindingResult result) {
            return new BindingWindows(
                    result.window().stream().toList(), result.report(), result.cancelled());
        }

        private static BindingWindows cancelledResult() {
            return new BindingWindows(List.of(), DiagnosticReport.empty(), true);
        }
    }
}
