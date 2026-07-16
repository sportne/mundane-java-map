package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsKind;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationHillshade;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/** Stateless bounded planning and RGBA rasterization for regularly sampled elevation grids. */
public final class ElevationRasterization {
    private static final double EARTH_RADIUS_METRES = 6_371_008.8;
    private static final int CHECKPOINT_MASK = 4095;

    private ElevationRasterization() {}

    /**
     * Plans one post-support window intersecting visible display bounds in the same CRS.
     *
     * @param metadata immutable elevation-grid metadata
     * @param visibleDisplayBounds finite display bounds in the source CRS
     * @param displayUnitsPerPixel finite positive display scale
     * @param interpolation rendered-color resampling mode
     * @param effectiveLimits complete effective raster-request limits
     * @return empty for no positive-area intersection, otherwise a fully preflighted plan
     */
    public static Optional<Plan> plan(
            ElevationSourceMetadata metadata,
            Envelope visibleDisplayBounds,
            double displayUnitsPerPixel,
            RasterInterpolation interpolation,
            RasterRequestLimits effectiveLimits) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(visibleDisplayBounds, "visibleDisplayBounds");
        Objects.requireNonNull(interpolation, "interpolation");
        Objects.requireNonNull(effectiveLimits, "effectiveLimits");
        if (!Double.isFinite(displayUnitsPerPixel) || displayUnitsPerPixel <= 0.0) {
            throw new IllegalArgumentException("displayUnitsPerPixel must be finite and positive");
        }
        Optional<Envelope> visible =
                positiveIntersection(metadata.sampleBounds(), visibleDisplayBounds);
        if (visible.isEmpty()) {
            return Optional.empty();
        }
        Envelope clippedVisible = visible.orElseThrow();
        int firstColumn = firstColumn(metadata, clippedVisible.minX());
        int lastColumn = lastColumn(metadata, clippedVisible.maxX());
        int firstRow = firstRow(metadata, clippedVisible.maxY());
        int lastRow = lastRow(metadata, clippedVisible.minY());
        if (interpolation == RasterInterpolation.BILINEAR) {
            firstColumn = Math.max(0, firstColumn - 1);
            lastColumn = Math.min(metadata.columnCount() - 1, lastColumn + 1);
            firstRow = Math.max(0, firstRow - 1);
            lastRow = Math.min(metadata.rowCount() - 1, lastRow + 1);
        }
        RasterWindow window =
                new RasterWindow(
                        firstColumn,
                        firstRow,
                        lastColumn - firstColumn + 1,
                        lastRow - firstRow + 1);
        Envelope imageBounds = supportBounds(metadata, window);
        Envelope clipBounds =
                positiveIntersection(imageBounds, metadata.sampleBounds()).orElseThrow();
        int outputWidth = outputSize(imageBounds.width(), displayUnitsPerPixel, window.width());
        int outputHeight = outputSize(imageBounds.height(), displayUnitsPerPixel, window.height());
        RasterRequest request =
                new RasterRequest(
                        window, outputWidth, outputHeight, interpolation, Optional.empty());
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(
                        metadata.identity().id(), effectiveLimits, CancellationToken.none());
        accounting.validateWindow(metadata.columnCount(), metadata.rowCount(), window);
        long outputPixels = accounting.validateOutput(outputWidth, outputHeight);
        accounting.chargeSourcePixels(Math.multiplyExact((long) window.width(), window.height()));
        accounting.chargeIntermediateBytes(Math.multiplyExact(outputPixels, 4L));
        accounting.chargePublishedBytes(Math.multiplyExact(outputPixels, 4L));
        RasterResampling.validatePlan(
                window.width(), window.height(), outputWidth, outputHeight, interpolation);
        return Optional.of(new Plan(metadata, request, effectiveLimits, imageBounds, clipBounds));
    }

    /**
     * Colorizes and optionally shades one preflighted plan without retaining derived state.
     *
     * @param source open elevation source matching the plan metadata exactly
     * @param plan factory-created plan
     * @param style immutable colorization style with the source's exact unit
     * @param cancellation operation-local cooperative cancellation token
     * @return complete immutable RGBA read
     */
    public static RasterRead rasterize(
            ElevationSource source,
            Plan plan,
            ElevationRasterStyle style,
            CancellationToken cancellation) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(plan, "plan");
        Objects.requireNonNull(style, "style");
        Objects.requireNonNull(cancellation, "cancellation");
        if (!source.metadata().equals(plan.metadata)) {
            throw new IllegalArgumentException("source metadata must equal plan metadata");
        }
        if (source.isClosed()) {
            throw new IllegalStateException("source is closed");
        }
        if (style.colorRamp().unit() != plan.metadata.elevationUnit()) {
            throw new IllegalArgumentException("color-ramp unit must equal source elevation unit");
        }
        CrsDefinition crs =
                style.hillshade()
                        .map(
                                ignored ->
                                        plan.metadata
                                                .crs()
                                                .definition()
                                                .orElseThrow(
                                                        () ->
                                                                new IllegalArgumentException(
                                                                        "hillshade requires a recognized CRS")))
                        .orElse(null);
        RasterRequestAccounting accounting =
                new RasterRequestAccounting(
                        plan.metadata.identity().id(), plan.effectiveLimits, cancellation);
        accounting.checkpoint();
        if (style.hillshade().isPresent()) {
            preflightHillshade(plan.metadata, Objects.requireNonNull(crs, "crs"));
        }
        RasterWindow window = plan.request.sourceWindow();
        accounting.validateWindow(plan.metadata.columnCount(), plan.metadata.rowCount(), window);
        RasterWindow chargedWindow =
                style.hillshade().isPresent() ? expandByOne(plan.metadata, window) : window;
        accounting.chargeSourcePixels(
                Math.multiplyExact((long) chargedWindow.width(), chargedWindow.height()));
        long outputPixels =
                accounting.validateOutput(plan.request.outputWidth(), plan.request.outputHeight());
        accounting.chargeIntermediateBytes(Math.multiplyExact(outputPixels, 4L));
        accounting.chargePublishedBytes(Math.multiplyExact(outputPixels, 4L));
        int taps =
                (plan.request.interpolation() == RasterInterpolation.NEAREST ? 1 : 4)
                        * (style.hillshade().isPresent() ? 5 : 1);
        long derivedWork = Math.multiplyExact(outputPixels, taps);
        if (derivedWork < outputPixels) {
            throw new ArithmeticException("Elevation rasterization work overflow");
        }
        RasterResampling.validatePlan(
                window.width(),
                window.height(),
                plan.request.outputWidth(),
                plan.request.outputHeight(),
                plan.request.interpolation());

        accounting.checkpoint();
        RgbaPixelBuffer.Builder output =
                RgbaPixelBuffer.builder(plan.request.outputWidth(), plan.request.outputHeight());
        SampleAccess samples = new SampleAccess(source, accounting);
        for (int row = 0; row < plan.request.outputHeight(); row++) {
            accounting.checkpoint();
            for (int column = 0; column < plan.request.outputWidth(); column++) {
                int rgba =
                        plan.request.interpolation() == RasterInterpolation.NEAREST
                                ? nearestColor(plan, style, crs, samples, column, row)
                                : bilinearColor(plan, style, crs, samples, column, row);
                output.setRgba(column, row, rgba);
            }
        }
        accounting.checkpoint();
        RgbaPixelBuffer pixels = output.build();
        accounting.checkpoint();
        DiagnosticReport diagnostics = source.openingDiagnostics();
        return new RasterRead(window, pixels, diagnostics);
    }

    private static int nearestColor(
            Plan plan,
            ElevationRasterStyle style,
            CrsDefinition crs,
            SampleAccess samples,
            int outputColumn,
            int outputRow) {
        RasterWindow window = plan.request.sourceWindow();
        int column =
                window.column()
                        + RasterResampling.nearestIndex(
                                outputColumn, window.width(), plan.request.outputWidth());
        int row =
                window.row()
                        + RasterResampling.nearestIndex(
                                outputRow, window.height(), plan.request.outputHeight());
        return virtualColor(plan.metadata, style, crs, samples, column, row);
    }

    private static int bilinearColor(
            Plan plan,
            ElevationRasterStyle style,
            CrsDefinition crs,
            SampleAccess samples,
            int outputColumn,
            int outputRow) {
        RasterWindow window = plan.request.sourceWindow();
        RasterResampling.AxisWeights x =
                RasterResampling.bilinearAxis(
                        outputColumn, window.width(), plan.request.outputWidth());
        RasterResampling.AxisWeights y =
                RasterResampling.bilinearAxis(
                        outputRow, window.height(), plan.request.outputHeight());
        int west = window.column() + x.lowerIndex();
        int east = window.column() + x.upperIndex();
        int north = window.row() + y.lowerIndex();
        int south = window.row() + y.upperIndex();
        return RasterResampling.bilinearRgba(
                virtualColor(plan.metadata, style, crs, samples, west, north),
                virtualColor(plan.metadata, style, crs, samples, east, north),
                virtualColor(plan.metadata, style, crs, samples, west, south),
                virtualColor(plan.metadata, style, crs, samples, east, south),
                x,
                y);
    }

    private static int virtualColor(
            ElevationSourceMetadata metadata,
            ElevationRasterStyle style,
            CrsDefinition crs,
            SampleAccess samples,
            int column,
            int row) {
        OptionalDouble sample = samples.read(column, row);
        if (sample.isEmpty()) {
            return pack(style.noDataColor());
        }
        double elevation = sample.orElseThrow();
        Rgba rampColor = style.colorRamp().colorAt(elevation);
        if (style.hillshade().isEmpty()) {
            return pack(rampColor);
        }
        double illumination =
                illumination(
                        metadata,
                        Objects.requireNonNull(crs, "crs"),
                        style.hillshade().orElseThrow(),
                        samples,
                        column,
                        row,
                        elevation);
        return pack(
                new Rgba(
                        shade(rampColor.red(), illumination),
                        shade(rampColor.green(), illumination),
                        shade(rampColor.blue(), illumination),
                        rampColor.alpha()));
    }

    private static double illumination(
            ElevationSourceMetadata metadata,
            CrsDefinition crs,
            ElevationHillshade settings,
            SampleAccess samples,
            int column,
            int row,
            double center) {
        OptionalDouble west = column > 0 ? samples.read(column - 1, row) : OptionalDouble.empty();
        OptionalDouble east =
                column + 1 < metadata.columnCount()
                        ? samples.read(column + 1, row)
                        : OptionalDouble.empty();
        OptionalDouble north = row > 0 ? samples.read(column, row - 1) : OptionalDouble.empty();
        OptionalDouble south =
                row + 1 < metadata.rowCount()
                        ? samples.read(column, row + 1)
                        : OptionalDouble.empty();
        double eastRise = orientedDifference(east, west, center);
        double northRise = orientedDifference(north, south, center);
        eastRise = saturatingMultiply(eastRise, metadata.elevationUnit().metresPerUnit());
        eastRise = saturatingMultiply(eastRise, settings.verticalExaggeration());
        northRise = saturatingMultiply(northRise, metadata.elevationUnit().metresPerUnit());
        northRise = saturatingMultiply(northRise, settings.verticalExaggeration());
        double eastDistance;
        double northDistance;
        if (crs.kind() == CrsKind.PROJECTED) {
            eastDistance = metadata.columnSpacing();
            northDistance = metadata.rowSpacing();
        } else if (crs.kind() == CrsKind.GEOGRAPHIC) {
            double latitude = metadata.sampleCoordinate(column, row).y();
            northDistance = EARTH_RADIUS_METRES * StrictMath.toRadians(metadata.rowSpacing());
            if (latitude == 90.0 || latitude == -90.0) {
                eastRise = 0.0;
                eastDistance = 1.0;
            } else {
                eastDistance =
                        EARTH_RADIUS_METRES
                                * StrictMath.cos(StrictMath.toRadians(latitude))
                                * StrictMath.toRadians(metadata.columnSpacing());
            }
        } else {
            throw new IllegalArgumentException("hillshade requires geographic or projected CRS");
        }
        double gEast = saturatingDivide(eastRise, eastDistance);
        double gNorth = saturatingDivide(northRise, northDistance);
        double scale = Math.max(1.0, Math.max(Math.abs(gEast), Math.abs(gNorth)));
        double nx = -gEast / scale;
        double ny = -gNorth / scale;
        double nz = 1.0 / scale;
        double length = StrictMath.hypot(StrictMath.hypot(nx, ny), nz);
        nx /= length;
        ny /= length;
        nz /= length;
        double azimuth = StrictMath.toRadians(settings.azimuthDegrees());
        double altitude = StrictMath.toRadians(settings.altitudeDegrees());
        double cosAltitude = StrictMath.cos(altitude);
        double lightEast = cosAltitude * StrictMath.sin(azimuth);
        double lightNorth = cosAltitude * StrictMath.cos(azimuth);
        double lightUp = StrictMath.sin(altitude);
        return Math.clamp(nx * lightEast + ny * lightNorth + nz * lightUp, 0.0, 1.0);
    }

    private static void preflightHillshade(ElevationSourceMetadata metadata, CrsDefinition crs) {
        Envelope bounds = metadata.sampleBounds();
        Envelope domain = crs.coordinateDomain();
        if (bounds.minX() < domain.minX()
                || bounds.maxX() > domain.maxX()
                || bounds.minY() < domain.minY()
                || bounds.maxY() > domain.maxY()) {
            throw new IllegalArgumentException(
                    "hillshade sample bounds must lie inside the recognized CRS domain");
        }
        if (crs.kind() == CrsKind.PROJECTED) {
            requireHillshadeDistance(metadata.columnSpacing());
            requireHillshadeDistance(metadata.rowSpacing());
            return;
        }
        if (crs.kind() != CrsKind.GEOGRAPHIC) {
            throw new IllegalArgumentException("hillshade requires geographic or projected CRS");
        }
        if (bounds.minY() < -90.0 || bounds.maxY() > 90.0) {
            throw new IllegalArgumentException("geographic hillshade latitude must be in [-90,90]");
        }
        requireHillshadeDistance(EARTH_RADIUS_METRES * StrictMath.toRadians(metadata.rowSpacing()));
        int northRow = bounds.maxY() == 90.0 ? 1 : 0;
        int southRow = bounds.minY() == -90.0 ? metadata.rowCount() - 2 : metadata.rowCount() - 1;
        double northLatitude = metadata.sampleCoordinate(0, northRow).y();
        double southLatitude = metadata.sampleCoordinate(0, southRow).y();
        double limitingLatitude =
                StrictMath.abs(northLatitude) >= StrictMath.abs(southLatitude)
                        ? northLatitude
                        : southLatitude;
        if (limitingLatitude != -90.0 && limitingLatitude != 90.0) {
            requireHillshadeDistance(
                    EARTH_RADIUS_METRES
                            * StrictMath.cos(StrictMath.toRadians(limitingLatitude))
                            * StrictMath.toRadians(metadata.columnSpacing()));
        }
    }

    private static void requireHillshadeDistance(double value) {
        if (!(value > 0.0) || !Double.isFinite(value)) {
            throw new IllegalArgumentException("hillshade distance must be finite and positive");
        }
    }

    private static double orientedDifference(
            OptionalDouble positive, OptionalDouble negative, double center) {
        if (positive.isPresent() && negative.isPresent()) {
            return difference(positive.orElseThrow(), negative.orElseThrow(), true);
        }
        if (positive.isPresent()) {
            return difference(positive.orElseThrow(), center, false);
        }
        if (negative.isPresent()) {
            return difference(center, negative.orElseThrow(), false);
        }
        return 0.0;
    }

    private static double difference(double positive, double negative, boolean central) {
        double result;
        if (Math.copySign(1.0, positive) == Math.copySign(1.0, negative)) {
            result = positive - negative;
            if (central) {
                result /= 2.0;
            }
        } else {
            double half = positive / 2.0 - negative / 2.0;
            if (central) {
                result = half;
            } else if (Math.abs(half) > Double.MAX_VALUE / 2.0) {
                result = Math.copySign(Double.MAX_VALUE, half);
            } else {
                result = half * 2.0;
            }
        }
        return result == 0.0 ? 0.0 : result;
    }

    private static double saturatingMultiply(double value, double positiveFactor) {
        if (value == 0.0) {
            return 0.0;
        }
        if (Math.abs(value) > Double.MAX_VALUE / positiveFactor) {
            return Math.copySign(Double.MAX_VALUE, value);
        }
        return value * positiveFactor;
    }

    private static double saturatingDivide(double value, double positiveDistance) {
        if (!(positiveDistance > 0.0) || !Double.isFinite(positiveDistance)) {
            throw new IllegalArgumentException("hillshade distance must be finite and positive");
        }
        if (value == 0.0) {
            return 0.0;
        }
        if (positiveDistance < 1.0 && Math.abs(value) > Double.MAX_VALUE * positiveDistance) {
            return Math.copySign(Double.MAX_VALUE, value);
        }
        return value / positiveDistance;
    }

    private static int shade(int channel, double illumination) {
        return (int) StrictMath.floor(channel * illumination + 0.5);
    }

    private static int pack(Rgba color) {
        return (color.red() << 24) | (color.green() << 16) | (color.blue() << 8) | color.alpha();
    }

    private static RasterWindow expandByOne(ElevationSourceMetadata metadata, RasterWindow window) {
        int column = Math.max(0, window.column() - 1);
        int row = Math.max(0, window.row() - 1);
        int endColumn = Math.min(metadata.columnCount(), Math.toIntExact(window.endColumn()) + 1);
        int endRow = Math.min(metadata.rowCount(), Math.toIntExact(window.endRow()) + 1);
        return new RasterWindow(column, row, endColumn - column, endRow - row);
    }

    private static int firstColumn(ElevationSourceMetadata metadata, double minimumX) {
        int low = 0;
        int high = metadata.columnCount() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (rightEdge(metadata, middle) > minimumX) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static int lastColumn(ElevationSourceMetadata metadata, double maximumX) {
        int low = 0;
        int high = metadata.columnCount() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (leftEdge(metadata, middle) < maximumX) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static int firstRow(ElevationSourceMetadata metadata, double maximumY) {
        int low = 0;
        int high = metadata.rowCount() - 1;
        while (low < high) {
            int middle = (low + high) >>> 1;
            if (bottomEdge(metadata, middle) < maximumY) {
                high = middle;
            } else {
                low = middle + 1;
            }
        }
        return low;
    }

    private static int lastRow(ElevationSourceMetadata metadata, double minimumY) {
        int low = 0;
        int high = metadata.rowCount() - 1;
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            if (topEdge(metadata, middle) > minimumY) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        return low;
    }

    private static Envelope supportBounds(ElevationSourceMetadata metadata, RasterWindow window) {
        int lastColumn = Math.toIntExact(window.endColumn()) - 1;
        int lastRow = Math.toIntExact(window.endRow()) - 1;
        return new Envelope(
                leftEdge(metadata, window.column()),
                bottomEdge(metadata, lastRow),
                rightEdge(metadata, lastColumn),
                topEdge(metadata, window.row()));
    }

    private static double leftEdge(ElevationSourceMetadata metadata, int column) {
        if (column == 0) {
            return offsetEdge(metadata.sampleBounds().minX(), -metadata.columnSpacing() / 2.0);
        }
        return midpoint(
                metadata.sampleCoordinate(column - 1, 0).x(),
                metadata.sampleCoordinate(column, 0).x());
    }

    private static double rightEdge(ElevationSourceMetadata metadata, int column) {
        if (column == metadata.columnCount() - 1) {
            return offsetEdge(metadata.sampleBounds().maxX(), metadata.columnSpacing() / 2.0);
        }
        return midpoint(
                metadata.sampleCoordinate(column, 0).x(),
                metadata.sampleCoordinate(column + 1, 0).x());
    }

    private static double topEdge(ElevationSourceMetadata metadata, int row) {
        if (row == 0) {
            return offsetEdge(metadata.sampleBounds().maxY(), metadata.rowSpacing() / 2.0);
        }
        return midpoint(
                metadata.sampleCoordinate(0, row - 1).y(), metadata.sampleCoordinate(0, row).y());
    }

    private static double bottomEdge(ElevationSourceMetadata metadata, int row) {
        if (row == metadata.rowCount() - 1) {
            return offsetEdge(metadata.sampleBounds().minY(), -metadata.rowSpacing() / 2.0);
        }
        return midpoint(
                metadata.sampleCoordinate(0, row).y(), metadata.sampleCoordinate(0, row + 1).y());
    }

    private static double midpoint(double first, double second) {
        double result = first / 2.0 + second / 2.0;
        if (!Double.isFinite(result)
                || Double.doubleToLongBits(result) == Double.doubleToLongBits(first)
                || Double.doubleToLongBits(result) == Double.doubleToLongBits(second)) {
            throw new IllegalArgumentException("Post-support midpoint must be finite and distinct");
        }
        return result;
    }

    private static double offsetEdge(double bound, double offset) {
        double value = bound + offset;
        if (!Double.isFinite(value)
                || Double.doubleToLongBits(value) == Double.doubleToLongBits(bound)) {
            throw new IllegalArgumentException(
                    "Post-support edge must be finite and distinct from its post");
        }
        return value;
    }

    private static int outputSize(double span, double unitsPerPixel, int sourceSize) {
        double requested = StrictMath.ceil(span / unitsPerPixel);
        if (!Double.isFinite(requested) || requested >= sourceSize) {
            return sourceSize;
        }
        return Math.max(1, (int) requested);
    }

    private static Optional<Envelope> positiveIntersection(Envelope first, Envelope second) {
        double minX = Math.max(first.minX(), second.minX());
        double minY = Math.max(first.minY(), second.minY());
        double maxX = Math.min(first.maxX(), second.maxX());
        double maxY = Math.min(first.maxY(), second.maxY());
        return maxX > minX && maxY > minY
                ? Optional.of(new Envelope(minX, minY, maxX, maxY))
                : Optional.empty();
    }

    /** Immutable factory-only elevation rasterization plan. */
    public static final class Plan {
        private final ElevationSourceMetadata metadata;
        private final RasterRequest request;
        private final RasterRequestLimits effectiveLimits;
        private final Envelope imageMapBounds;
        private final Envelope clipMapBounds;

        private Plan(
                ElevationSourceMetadata metadata,
                RasterRequest request,
                RasterRequestLimits effectiveLimits,
                Envelope imageMapBounds,
                Envelope clipMapBounds) {
            this.metadata = metadata;
            this.request = request;
            this.effectiveLimits = effectiveLimits;
            this.imageMapBounds = imageMapBounds;
            this.clipMapBounds = clipMapBounds;
        }

        /**
         * Returns the immutable source-metadata snapshot.
         *
         * @return exact planned metadata
         */
        public ElevationSourceMetadata metadata() {
            return metadata;
        }

        /**
         * Returns the strict post window and derived output shape.
         *
         * @return immutable raster request with no tighter-limit override
         */
        public RasterRequest request() {
            return request;
        }

        /**
         * Returns the complete effective limits used during planning.
         *
         * @return exact effective request limits
         */
        public RasterRequestLimits effectiveLimits() {
            return effectiveLimits;
        }

        /**
         * Returns the support bounds represented by the generated image.
         *
         * @return image-placement bounds in the source CRS
         */
        public Envelope imageMapBounds() {
            return imageMapBounds;
        }

        /**
         * Returns the exact positive terrain-domain clip bounds.
         *
         * @return clip bounds restricted to sample-center metadata bounds
         */
        public Envelope clipMapBounds() {
            return clipMapBounds;
        }
    }

    private static final class SampleAccess {
        private final ElevationSource source;
        private final RasterRequestAccounting accounting;
        private int accesses;

        private SampleAccess(ElevationSource source, RasterRequestAccounting accounting) {
            this.source = source;
            this.accounting = accounting;
        }

        private OptionalDouble read(int column, int row) {
            if ((accesses++ & CHECKPOINT_MASK) == 0) {
                accounting.checkpoint();
            }
            OptionalDouble value = Objects.requireNonNull(source.sample(column, row), "sample");
            if (value.isPresent() && !Double.isFinite(value.orElseThrow())) {
                throw new IllegalStateException("Elevation source returned a non-finite sample");
            }
            return value;
        }
    }
}
