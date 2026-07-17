package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationHillshade;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.ElevationValue;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.RasterRenderOptions;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.ElevationQueries;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.RepaintManager;
import javax.swing.SwingUtilities;

/** Assertion-bearing DTED scenario shared by JVM tests and the native executable. */
final class NativeDtedSmokeScenario {
    static final String VALID_SOURCE_ID = "native-dted";
    static final String MALFORMED_SOURCE_ID = "native-dted-malformed";
    static final Envelope SAMPLE_BOUNDS = new Envelope(-1, -81, 0, -80);
    static final int IMAGE_SIZE = 144;
    static final ElevationRasterStyle UNSHADED_STYLE =
            ElevationRasterStyle.of(
                            new ElevationColorRamp(
                                    ElevationUnit.METRE,
                                    List.of(
                                            new ElevationColorStop(
                                                    -500, new Rgba(24, 64, 180, 255)),
                                            new ElevationColorStop(
                                                    1_000, new Rgba(40, 170, 90, 255)),
                                            new ElevationColorStop(
                                                    2_500, new Rgba(230, 120, 35, 255)))))
                    .withNoDataColor(Rgba.TRANSPARENT);
    private static final RasterRenderOptions RENDER_OPTIONS =
            new RasterRenderOptions(RasterInterpolation.NEAREST, 1.0);

    private NativeDtedSmokeScenario() {}

    static Result run(NativeDtedPaths paths) {
        require(!SwingUtilities.isEventDispatchThread(), "dted-cleanup", "source work ran on EDT");
        ElevationSource source =
                DtedFiles.open(
                        new SourceIdentity(VALID_SOURCE_ID, "Native DTED fixture"),
                        paths.valid(),
                        DtedOpenOptions.defaults());
        ElevationSourceMetadata metadata = source.metadata();
        ElevationValue nearest;
        ElevationValue bilinear;
        RenderResult rendering;
        try {
            assertMetadataAndSamples(source);
            nearest = assertNearestQuery(source);
            bilinear = assertBilinearQuery(source);
            rendering = NativeShapefileSmokeScenario.onEdt(() -> renderOwned(source));
        } finally {
            if (!source.isClosed()) {
                source.close();
            }
        }
        require(source.isClosed(), "dted-cleanup", "owned source remained open");
        DiagnosticReport malformed = assertMalformed(paths.truncated());
        return new Result(metadata, nearest, bilinear, rendering, malformed, source.isClosed());
    }

    static DiagnosticReport assertMalformed(java.nio.file.Path path) {
        SourceException failure;
        try {
            try (ElevationSource accepted =
                    DtedFiles.open(
                            new SourceIdentity(MALFORMED_SOURCE_ID, "Malformed native DTED"),
                            path,
                            DtedOpenOptions.defaults())) {
                require(!accepted.isClosed(), "dted-diagnostic", "accepted source opened closed");
                throw new IllegalStateException("dted-diagnostic: truncated file was accepted");
            }
        } catch (SourceException expected) {
            failure = expected;
        }
        var terminal = failure.terminal();
        require(
                terminal.code().equals("DTED_FILE_LENGTH_MISMATCH")
                        && terminal.severity() == DiagnosticSeverity.ERROR
                        && terminal.sourceId().equals(MALFORMED_SOURCE_ID),
                "dted-diagnostic",
                "terminal identity changed");
        var location = terminal.location().orElseThrow();
        require(
                location.component().equals(Optional.of("dted"))
                        && location.recordNumber().isEmpty()
                        && location.partIndex().isEmpty()
                        && location.fieldIndex().isEmpty()
                        && location.fieldName().isEmpty()
                        && location.byteOffset().isEmpty(),
                "dted-diagnostic",
                "terminal location changed");
        require(
                terminal.context().equals(Map.of("actualBytes", "8761", "expectedBytes", "8762")),
                "dted-diagnostic",
                "terminal context changed");
        require(
                failure.report().entries().equals(List.of(terminal))
                        && failure.report().omittedWarningCount() == 0,
                "dted-diagnostic",
                "terminal report changed");
        return failure.report();
    }

    private static void assertMetadataAndSamples(ElevationSource source) {
        ElevationSourceMetadata metadata = source.metadata();
        require(
                metadata.columnCount() == 21
                        && metadata.rowCount() == 121
                        && metadata.sampleBounds().equals(SAMPLE_BOUNDS),
                "dted-metadata",
                "grid metadata changed");
        require(
                metadata.crs().canonicalIdentifier().equals(Optional.of("EPSG:4326"))
                        && metadata.elevationUnit() == ElevationUnit.METRE
                        && source.openingDiagnostics().equals(DiagnosticReport.empty()),
                "dted-metadata",
                "CRS, unit, or diagnostics changed");
        require(
                metadata.sampleCoordinate(0, 0).equals(new Coordinate(-1, -80))
                        && metadata.sampleCoordinate(20, 120).equals(new Coordinate(0, -81)),
                "dted-metadata",
                "sample orientation changed");
        requireSample(source, 0, 0, 1_500, "north-west");
        requireSample(source, 20, 0, 2_500, "north-east");
        requireSample(source, 0, 120, -500, "south-west");
        requireSample(source, 20, 120, 500, "south-east");
        requireSample(source, 10, 60, 1_000, "center");
    }

    private static ElevationValue assertNearestQuery(ElevationSource source) {
        ElevationValue value =
                ElevationQueries.query(
                                source,
                                CrsDefinitions.EPSG_4326,
                                new Coordinate(-0.5, -80.5),
                                ElevationQueryMode.NEAREST)
                        .orElseThrow();
        require(
                value.equals(new ElevationValue(1_000, ElevationUnit.METRE)),
                "dted-query",
                "nearest result changed");
        return value;
    }

    private static ElevationValue assertBilinearQuery(ElevationSource source) {
        ElevationSourceMetadata metadata = source.metadata();
        Coordinate first = metadata.sampleCoordinate(7, 37);
        Coordinate second = metadata.sampleCoordinate(8, 38);
        Coordinate midpoint =
                new Coordinate((first.x() + second.x()) / 2.0, (first.y() + second.y()) / 2.0);
        ElevationValue value =
                ElevationQueries.query(
                                source,
                                CrsDefinitions.EPSG_4326,
                                midpoint,
                                ElevationQueryMode.BILINEAR)
                        .orElseThrow();
        double expected = 1_250.5;
        double tolerance = Math.max(1.0e-12, Math.abs(expected) * 1.0e-12);
        require(
                value.unit() == ElevationUnit.METRE
                        && Math.abs(value.value() - expected) <= tolerance
                        && Math.abs(directMidpoint(source) - expected) <= tolerance,
                "dted-query",
                "bilinear result changed");
        return value;
    }

    private static double directMidpoint(ElevationSource source) {
        double north =
                (source.sample(7, 37).orElseThrow() + source.sample(8, 37).orElseThrow()) / 2.0;
        double south =
                (source.sample(7, 38).orElseThrow() + source.sample(8, 38).orElseThrow()) / 2.0;
        return (north + south) / 2.0;
    }

    private static RenderResult renderOwned(ElevationSource source) {
        require(SwingUtilities.isEventDispatchThread(), "dted-render", "paint was off EDT");
        MapView view =
                new MapView(
                        CrsRegistry.level1(),
                        CrsDefinitions.EPSG_4326,
                        CrsDefinitions.EPSG_4326,
                        SymbolRendererRegistry.builtIn());
        MapLayerBinding binding =
                MapLayerBinding.ownedElevation(
                        "native-dted",
                        "Native DTED",
                        source,
                        UNSHADED_STYLE,
                        RENDER_OPTIONS,
                        RasterRequestLimits.LEVEL_1);
        return NativeShapefileSmokeScenario.withRenderOwnership(
                () -> {
                    view.setDoubleBuffered(false);
                    view.setSize(IMAGE_SIZE, IMAGE_SIZE);
                    view.setLayerBindings(List.of(binding));
                    view.setViewport(
                            new MapViewport(IMAGE_SIZE, IMAGE_SIZE, -0.5, -80.5, 1.0 / 120.0));
                    BufferedImage unshaded = paint(view);
                    assertUnshaded(unshaded);
                    view.setElevationRasterStyle(
                            "native-dted",
                            UNSHADED_STYLE.withHillshade(ElevationHillshade.defaults()));
                    BufferedImage shaded = paint(view);
                    assertHillshade(unshaded, shaded);
                    return new RenderResult(
                            countNonWhite(unshaded),
                            countNonWhite(shaded),
                            luminance(unshaded),
                            luminance(shaded));
                },
                () -> view.layerBindings().stream().anyMatch(value -> value == binding),
                binding::close,
                view::close);
    }

    private static BufferedImage paint(MapView view) {
        BufferedImage image =
                new BufferedImage(IMAGE_SIZE, IMAGE_SIZE, BufferedImage.TYPE_INT_ARGB);
        RepaintManager manager = RepaintManager.currentManager(view);
        boolean previous = manager.isDoubleBufferingEnabled();
        manager.setDoubleBufferingEnabled(false);
        try {
            Graphics2D graphics = image.createGraphics();
            try {
                graphics.setComposite(AlphaComposite.Src);
                graphics.setColor(Color.WHITE);
                graphics.fillRect(0, 0, IMAGE_SIZE, IMAGE_SIZE);
                graphics.setComposite(AlphaComposite.SrcOver);
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            manager.setDoubleBufferingEnabled(previous);
        }
        return image;
    }

    static void assertUnshaded(BufferedImage image) {
        assertMajority(image, 36, 36, new Rgba(78, 160, 79, 255));
        assertMajority(image, 108, 36, new Rgba(154, 140, 57, 255));
        assertMajority(image, 36, 108, new Rgba(30, 106, 144, 255));
        assertMajority(image, 108, 108, new Rgba(37, 149, 108, 255));
        require(image.getRGB(4, 4) == Color.WHITE.getRGB(), "dted-render", "background changed");
        Bounds bounds = bounds(image);
        require(
                bounds.minX() >= 12
                        && bounds.minY() >= 12
                        && bounds.maxX() <= 132
                        && bounds.maxY() <= 132,
                "dted-render",
                "render escaped sample bounds");
        int count = countNonWhite(image);
        require(count >= 13_000 && count <= 15_000, "dted-render", "nonwhite count changed");
    }

    static void assertHillshade(BufferedImage unshaded, BufferedImage shaded) {
        require(
                bounds(unshaded).equals(bounds(shaded)),
                "dted-hillshade",
                "footprint bounds changed");
        for (int y = 0; y < IMAGE_SIZE; y++) {
            for (int x = 0; x < IMAGE_SIZE; x++) {
                int first = unshaded.getRGB(x, y);
                int second = shaded.getRGB(x, y);
                require(
                        (first >>> 24) == (second >>> 24) && isWhite(first) == isWhite(second),
                        "dted-hillshade",
                        "alpha or footprint changed");
            }
        }
        long firstSum = 0;
        long secondSum = 0;
        for (int y = 24; y <= 120; y++) {
            for (int x = 24; x <= 120; x++) {
                int first = unshaded.getRGB(x, y);
                int second = shaded.getRGB(x, y);
                int firstLuminance = pixelLuminance(first);
                int secondLuminance = pixelLuminance(second);
                require(
                        secondLuminance <= firstLuminance,
                        "dted-hillshade",
                        "hillshade brightened an interior probe");
                firstSum += firstLuminance;
                secondSum += secondLuminance;
            }
        }
        require(
                secondSum * 100 <= firstSum * 95,
                "dted-hillshade",
                "hillshade change was not material");
    }

    private static void assertMajority(
            BufferedImage image, int centerX, int centerY, Rgba expected) {
        int matches = 0;
        for (int x = centerX - 2; x <= centerX + 2; x++) {
            if (colorNear(image.getRGB(x, centerY), expected, 18)) {
                matches++;
            }
        }
        require(matches >= 3, "dted-render", "color probe changed");
    }

    private static boolean colorNear(int actual, Rgba expected, int tolerance) {
        return Math.abs((actual >>> 16 & 0xff) - expected.red()) <= tolerance
                && Math.abs((actual >>> 8 & 0xff) - expected.green()) <= tolerance
                && Math.abs((actual & 0xff) - expected.blue()) <= tolerance
                && Math.abs((actual >>> 24) - expected.alpha()) <= tolerance;
    }

    private static Bounds bounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!isWhite(image.getRGB(x, y))) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        require(maxX >= 0, "dted-render", "render was blank");
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static int countNonWhite(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!isWhite(image.getRGB(x, y))) {
                    count++;
                }
            }
        }
        return count;
    }

    private static long luminance(BufferedImage image) {
        long result = 0;
        for (int y = 24; y <= 120; y++) {
            for (int x = 24; x <= 120; x++) {
                result += pixelLuminance(image.getRGB(x, y));
            }
        }
        return result;
    }

    private static int pixelLuminance(int color) {
        return (color >>> 16 & 0xff) + (color >>> 8 & 0xff) + (color & 0xff);
    }

    private static boolean isWhite(int color) {
        return color == Color.WHITE.getRGB();
    }

    private static void requireSample(
            ElevationSource source, int column, int row, double expected, String description) {
        require(
                source.sample(column, row).orElseThrow() == expected,
                "dted-metadata",
                description + " sample changed");
    }

    private static void require(boolean condition, String invariant, String detail) {
        if (!condition) {
            throw new IllegalStateException(invariant + ": " + detail);
        }
    }

    private record Bounds(int minX, int minY, int maxX, int maxY) {}

    record RenderResult(
            int unshadedNonWhite,
            int shadedNonWhite,
            long unshadedLuminance,
            long shadedLuminance) {}

    record Result(
            ElevationSourceMetadata metadata,
            ElevationValue nearest,
            ElevationValue bilinear,
            RenderResult rendering,
            DiagnosticReport malformed,
            boolean sourceClosed) {}
}
