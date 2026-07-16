package io.github.mundanej.map.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationHillshade;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceLimits;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ElevationRasterizationTest {
    @Test
    void plansExactAndPartialPostSupportWithoutChangingSampleBounds() {
        ElevationSourceMetadata metadata = metadata(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        var full =
                ElevationRasterization.plan(
                                metadata,
                                new Envelope(0, 0, 2, 2),
                                0.01,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        assertEquals(new RasterWindow(0, 0, 3, 3), full.request().sourceWindow());
        assertEquals(new Envelope(-0.5, -0.5, 2.5, 2.5), full.imageMapBounds());
        assertEquals(metadata.sampleBounds(), full.clipMapBounds());
        assertEquals(3, full.request().outputWidth());
        assertTrue(full.request().tighterLimits().isEmpty());

        var center =
                ElevationRasterization.plan(
                                metadata,
                                new Envelope(0.6, 0.6, 1.4, 1.4),
                                1,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        assertEquals(new RasterWindow(1, 1, 1, 1), center.request().sourceWindow());
        assertEquals(new Envelope(0.5, 0.5, 1.5, 1.5), center.imageMapBounds());
        var bilinear =
                ElevationRasterization.plan(
                                metadata,
                                new Envelope(0.6, 0.6, 1.4, 1.4),
                                1,
                                RasterInterpolation.BILINEAR,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        assertEquals(new RasterWindow(0, 0, 3, 3), bilinear.request().sourceWindow());
        assertFalse(
                ElevationRasterization.plan(
                                metadata,
                                new Envelope(2, 0, 3, 1),
                                1,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .isPresent());
    }

    @Test
    void rasterizesNorthToSouthWithExactRampNoDataAndBilinearColor() {
        BitSet noData = new BitSet();
        noData.set(4);
        PackedElevationGrid source =
                PackedElevationGrid.copyOf(
                        metadata(CrsDefinitions.EPSG_3857, ElevationUnit.METRE),
                        new double[] {0, 1, 2, 3, 0, 5, 6, 7, 8},
                        noData);
        ElevationRasterStyle style =
                ElevationRasterStyle.of(
                                new ElevationColorRamp(
                                        ElevationUnit.METRE,
                                        List.of(
                                                new ElevationColorStop(0, Rgba.rgb(0, 0, 0)),
                                                new ElevationColorStop(8, Rgba.rgb(240, 80, 16)))))
                        .withNoDataColor(new Rgba(1, 2, 3, 4));
        var nearest =
                ElevationRasterization.plan(
                                source.metadata(),
                                source.metadata().sampleBounds(),
                                0.01,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        RasterRead read =
                ElevationRasterization.rasterize(source, nearest, style, CancellationToken.none());
        assertEquals(0x000000ff, read.pixels().rgbaAt(0, 0));
        assertEquals(0x01020304, read.pixels().rgbaAt(1, 1));
        assertEquals(0xf05010ff, read.pixels().rgbaAt(2, 2));
        assertEquals(nearest.request().sourceWindow(), read.sourceWindow());

        var bilinear =
                ElevationRasterization.plan(
                                source.metadata(),
                                source.metadata().sampleBounds(),
                                10,
                                RasterInterpolation.BILINEAR,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        assertEquals(1, bilinear.request().outputWidth());
        assertEquals(
                0x01020304,
                ElevationRasterization.rasterize(source, bilinear, style, CancellationToken.none())
                        .pixels()
                        .rgbaAt(0, 0));
    }

    @Test
    void flatProjectedAndGeographicHillshadeUsesAltitudeAndPreservesAlpha() {
        for (var crs : List.of(CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_4326)) {
            PackedElevationGrid source =
                    PackedElevationGrid.copyOf(
                            metadata(crs, ElevationUnit.INTERNATIONAL_FOOT),
                            new double[9],
                            new BitSet());
            ElevationRasterStyle style =
                    new ElevationRasterStyle(
                            new ElevationColorRamp(
                                    ElevationUnit.INTERNATIONAL_FOOT,
                                    List.of(
                                            new ElevationColorStop(-1, new Rgba(100, 80, 60, 40)),
                                            new ElevationColorStop(1, new Rgba(100, 80, 60, 40)))),
                            Rgba.TRANSPARENT,
                            Optional.of(new ElevationHillshade(0, 30, 100)));
            var plan =
                    ElevationRasterization.plan(
                                    source.metadata(),
                                    source.metadata().sampleBounds(),
                                    0.01,
                                    RasterInterpolation.NEAREST,
                                    RasterRequestLimits.LEVEL_1)
                            .orElseThrow();
            assertEquals(
                    0x32281e28,
                    ElevationRasterization.rasterize(source, plan, style, CancellationToken.none())
                            .pixels()
                            .rgbaAt(1, 1));
        }
    }

    @Test
    void enforcesLimitsCancellationMetadataAndStyleUnitBeforePublication() {
        ElevationSourceMetadata metadata = metadata(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        RasterRequestLimits tooSmall = new RasterRequestLimits(8, 8, 8, 100, 100, 1);
        SourceException limit =
                assertThrows(
                        SourceException.class,
                        () ->
                                ElevationRasterization.plan(
                                        metadata,
                                        metadata.sampleBounds(),
                                        0.1,
                                        RasterInterpolation.NEAREST,
                                        tooSmall));
        assertEquals("SOURCE_LIMIT_EXCEEDED", limit.terminal().code());

        PackedElevationGrid source =
                PackedElevationGrid.copyOf(metadata, new double[9], new BitSet());
        var plan =
                ElevationRasterization.plan(
                                metadata,
                                metadata.sampleBounds(),
                                1,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        ElevationRasterStyle style = style(ElevationUnit.METRE);
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () -> ElevationRasterization.rasterize(source, plan, style, () -> true));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ElevationRasterization.rasterize(
                                source,
                                plan,
                                style(ElevationUnit.US_SURVEY_FOOT),
                                CancellationToken.none()));
        PackedElevationGrid other =
                PackedElevationGrid.copyOf(
                        new ElevationSourceMetadata(
                                new SourceIdentity("other", "Other"),
                                3,
                                3,
                                metadata.sampleBounds(),
                                metadata.crs(),
                                metadata.elevationUnit()),
                        new double[9],
                        new BitSet());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ElevationRasterization.rasterize(
                                other, plan, style, CancellationToken.none()));
    }

    @Test
    void planIsFinalAndFactoryOnly() {
        assertTrue(Modifier.isFinal(ElevationRasterization.Plan.class.getModifiers()));
        for (Constructor<?> constructor :
                ElevationRasterization.Plan.class.getDeclaredConstructors()) {
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        }
    }

    @Test
    void planningRejectsCollapsedOuterHalfSpacingBeforeSampling() {
        ElevationSourceMetadata metadata =
                new ElevationSourceMetadata(
                        new SourceIdentity("collapsed", "Collapsed"),
                        2,
                        3,
                        new Envelope(1.0e16, 0, 1.0e16 + 2, 2),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_3857,
                                Optional.of("EPSG:3857"),
                                Optional.empty()),
                        ElevationUnit.METRE);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ElevationRasterization.plan(
                                metadata,
                                metadata.sampleBounds(),
                                1,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1));
    }

    @Test
    void supportSelectionPinsTouchingAndAdjacentFloatingPointBoundaries() {
        ElevationSourceMetadata metadata = metadata(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        Envelope exact = new Envelope(0.5, 0.6, 1.5, 1.4);
        assertEquals(
                new RasterWindow(1, 1, 1, 1),
                plan(metadata, exact, RasterRequestLimits.LEVEL_1).request().sourceWindow());
        Envelope expandedWest = new Envelope(Math.nextDown(0.5), 0.6, Math.nextDown(1.5), 1.4);
        assertEquals(
                new RasterWindow(0, 1, 2, 1),
                plan(metadata, expandedWest, RasterRequestLimits.LEVEL_1).request().sourceWindow());
        Envelope expandedEast = new Envelope(Math.nextUp(0.5), 0.6, Math.nextUp(1.5), 1.4);
        assertEquals(
                new RasterWindow(1, 1, 2, 1),
                plan(metadata, expandedEast, RasterRequestLimits.LEVEL_1).request().sourceWindow());
    }

    @Test
    void planningPinsEverySharedLimitAtOneLessEqualAndOneMore() {
        ElevationSourceMetadata metadata = metadata(CrsDefinitions.EPSG_3857, ElevationUnit.METRE);
        assertPlanLimit(metadata, new RasterRequestLimits(8, 3, 9, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 9, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(10, 3, 9, 36, 36, 1));

        assertPlanLimit(metadata, new RasterRequestLimits(9, 2, 9, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 9, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 4, 9, 36, 36, 1));

        assertPlanLimit(metadata, new RasterRequestLimits(9, 3, 8, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 9, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 10, 36, 36, 1));

        assertPlanLimit(metadata, new RasterRequestLimits(9, 3, 9, 35, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 9, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 9, 37, 36, 1));

        assertPlanLimit(metadata, new RasterRequestLimits(9, 3, 9, 36, 35, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 9, 36, 36, 1));
        plan(metadata, metadata.sampleBounds(), new RasterRequestLimits(9, 3, 9, 36, 37, 1));
    }

    @Test
    void hillshadeChargesItsExpandedUniqueWindowBeforeSampling() {
        CountingSource source =
                new CountingSource(projectedGrid(new double[9], new BitSet(), ElevationUnit.METRE));
        Envelope center = new Envelope(0.75, 0.75, 1.25, 1.25);
        RasterRequestLimits eight = new RasterRequestLimits(8, 3, 9, 36, 36, 1);
        var rejected =
                ElevationRasterization.plan(
                                source.metadata(), center, 1, RasterInterpolation.NEAREST, eight)
                        .orElseThrow();
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                ElevationRasterization.rasterize(
                                        source,
                                        rejected,
                                        hillshadeStyle(
                                                ElevationUnit.METRE, ElevationHillshade.defaults()),
                                        CancellationToken.none()));
        assertEquals("sourceWindowPixels", failure.terminal().context().get("limit"));
        assertEquals(0, source.samples.get());

        RasterRequestLimits nine = new RasterRequestLimits(9, 3, 9, 36, 36, 1);
        var accepted =
                ElevationRasterization.plan(
                                source.metadata(), center, 1, RasterInterpolation.NEAREST, nine)
                        .orElseThrow();
        ElevationRasterization.rasterize(
                source,
                accepted,
                hillshadeStyle(ElevationUnit.METRE, ElevationHillshade.defaults()),
                CancellationToken.none());
        assertTrue(source.samples.get() > 0);
    }

    @Test
    void geographicHillshadeHasAnExactNonFlatResultAndConstantTimeDomainPreflight() {
        ElevationSourceMetadata geographic =
                metadata(CrsDefinitions.EPSG_4326, ElevationUnit.METRE);
        PackedElevationGrid slope =
                PackedElevationGrid.copyOf(
                        geographic,
                        new double[] {
                            0, 100_000, 200_000,
                            0, 100_000, 200_000,
                            0, 100_000, 200_000
                        },
                        new BitSet());
        assertEquals(11, shadeChannel(slope, new ElevationHillshade(90, 45, 1), 1, 1));

        ElevationSourceMetadata tallMetadata =
                new ElevationSourceMetadata(
                        new SourceIdentity("tall", "Tall"),
                        3,
                        100_000_000,
                        new Envelope(0, -80, 2, 80),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326,
                                Optional.of("EPSG:4326"),
                                Optional.empty()),
                        ElevationUnit.METRE);
        ElevationSource tall = new ConstantSource(tallMetadata);
        Envelope equator = new Envelope(0.9, -0.000001, 1.1, 0.000001);
        assertTimeout(
                Duration.ofSeconds(1),
                () -> {
                    var plan =
                            ElevationRasterization.plan(
                                            tallMetadata,
                                            equator,
                                            1,
                                            RasterInterpolation.NEAREST,
                                            new RasterRequestLimits(20, 4, 4, 16, 16, 1))
                                    .orElseThrow();
                    ElevationRasterization.rasterize(
                            tall,
                            plan,
                            hillshadeStyle(ElevationUnit.METRE, ElevationHillshade.defaults()),
                            CancellationToken.none());
                });
    }

    @Test
    void directionalHillshadeCoversAxesAzimuthExaggerationEdgesPolesAndNoDataNeighbors() {
        double[] eastRise = {0, 10, 20, 0, 10, 20, 0, 10, 20};
        PackedElevationGrid east = projectedGrid(eastRise, new BitSet(), ElevationUnit.METRE);
        int litFromWest = shadeChannel(east, new ElevationHillshade(270, 45, 1), 1, 1);
        int litFromEast = shadeChannel(east, new ElevationHillshade(90, 45, 1), 1, 1);
        assertTrue(litFromWest > litFromEast);
        assertTrue(
                shadeChannel(east, new ElevationHillshade(90, 90, 1), 1, 1)
                        > shadeChannel(east, new ElevationHillshade(90, 90, 10), 1, 1));

        double[] northRise = {20, 20, 20, 10, 10, 10, 0, 0, 0};
        PackedElevationGrid north = projectedGrid(northRise, new BitSet(), ElevationUnit.METRE);
        assertTrue(
                shadeChannel(north, new ElevationHillshade(180, 45, 1), 1, 1)
                        > shadeChannel(north, new ElevationHillshade(0, 45, 1), 1, 1));

        BitSet missingNeighbors = new BitSet();
        for (int index : new int[] {1, 3, 5, 7}) {
            missingNeighbors.set(index);
        }
        PackedElevationGrid isolated =
                projectedGrid(new double[9], missingNeighbors, ElevationUnit.METRE);
        assertEquals(141, shadeChannel(isolated, new ElevationHillshade(0, 45, 1), 1, 1));
        assertTrue(shadeChannel(east, new ElevationHillshade(270, 45, 1), 0, 0) >= 0);

        ElevationSourceMetadata polarMetadata =
                new ElevationSourceMetadata(
                        new SourceIdentity("polar", "Polar"),
                        3,
                        3,
                        new Envelope(0, 88, 2, 90),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326,
                                Optional.of("EPSG:4326"),
                                Optional.empty()),
                        ElevationUnit.METRE);
        PackedElevationGrid polar =
                PackedElevationGrid.copyOf(polarMetadata, eastRise, new BitSet());
        assertEquals(141, shadeChannel(polar, new ElevationHillshade(90, 45, 1), 1, 0));
    }

    @Test
    void extremeCentralAndOneSidedOppositeSignSlopesRemainFiniteAndPreserveAlpha() {
        double[] extremes = {
            0, Double.MAX_VALUE, 0, -Double.MAX_VALUE, 0, Double.MAX_VALUE, 0, -Double.MAX_VALUE, 0
        };
        PackedElevationGrid central =
                projectedGrid(extremes, new BitSet(), ElevationUnit.US_SURVEY_FOOT);
        assertTrue(shadeChannel(central, new ElevationHillshade(315, 45, 100), 1, 1) >= 0);
        BitSet oneSided = new BitSet();
        oneSided.set(3);
        oneSided.set(1);
        PackedElevationGrid edge = projectedGrid(extremes, oneSided, ElevationUnit.US_SURVEY_FOOT);
        RasterRead read = rasterize(edge, new ElevationHillshade(315, 45, 100));
        assertEquals(77, read.pixels().rgbaAt(1, 1) & 0xff);
    }

    @Test
    void invalidGeographicHillshadeFailsBeforeAllocationOrSamplingAndMidRowCancellationIsAtomic() {
        ElevationSourceMetadata invalidMetadata =
                new ElevationSourceMetadata(
                        new SourceIdentity("invalid-geographic", "Invalid geographic"),
                        3,
                        3,
                        new Envelope(0, 91, 2, 93),
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326,
                                Optional.of("EPSG:4326"),
                                Optional.empty()),
                        ElevationUnit.METRE);
        CountingSource invalid =
                new CountingSource(
                        PackedElevationGrid.copyOf(invalidMetadata, new double[9], new BitSet()));
        var invalidPlan =
                ElevationRasterization.plan(
                                invalidMetadata,
                                invalidMetadata.sampleBounds(),
                                0.01,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ElevationRasterization.rasterize(
                                invalid,
                                invalidPlan,
                                hillshadeStyle(ElevationUnit.METRE, ElevationHillshade.defaults()),
                                CancellationToken.none()));
        assertEquals(0, invalid.samples.get());

        CountingSource cancellable =
                new CountingSource(projectedGrid(new double[9], new BitSet(), ElevationUnit.METRE));
        var plan =
                ElevationRasterization.plan(
                                cancellable.metadata(),
                                cancellable.metadata().sampleBounds(),
                                0.01,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        AtomicInteger polls = new AtomicInteger();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                ElevationRasterization.rasterize(
                                        cancellable,
                                        plan,
                                        style(ElevationUnit.METRE),
                                        () -> polls.incrementAndGet() >= 5));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertFalse(cancellable.isClosed());
        assertTrue(cancellable.samples.get() > 0);
    }

    @Test
    void cancellationBeforeTransferAndAfterBufferBuildPublishesNoRead() {
        for (int cancellationPoll : new int[] {7, 8}) {
            CountingSource source =
                    new CountingSource(
                            projectedGrid(new double[9], new BitSet(), ElevationUnit.METRE));
            var plan =
                    ElevationRasterization.plan(
                                    source.metadata(),
                                    source.metadata().sampleBounds(),
                                    0.01,
                                    RasterInterpolation.NEAREST,
                                    RasterRequestLimits.LEVEL_1)
                            .orElseThrow();
            AtomicInteger polls = new AtomicInteger();
            SourceException cancelled =
                    assertThrows(
                            SourceException.class,
                            () ->
                                    ElevationRasterization.rasterize(
                                            source,
                                            plan,
                                            style(ElevationUnit.METRE),
                                            () -> polls.incrementAndGet() >= cancellationPoll));
            assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
            assertEquals(9, source.samples.get());
            assertFalse(source.isClosed());
        }
    }

    @Test
    void cancellationIsRecheckedImmediatelyBeforeOutputAllocation() {
        CountingSource source =
                new CountingSource(projectedGrid(new double[9], new BitSet(), ElevationUnit.METRE));
        var plan =
                ElevationRasterization.plan(
                                source.metadata(),
                                source.metadata().sampleBounds(),
                                0.01,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        AtomicInteger polls = new AtomicInteger();
        SourceException cancelled =
                assertThrows(
                        SourceException.class,
                        () ->
                                ElevationRasterization.rasterize(
                                        source,
                                        plan,
                                        style(ElevationUnit.METRE),
                                        () -> polls.incrementAndGet() >= 2));
        assertEquals("SOURCE_CANCELLED", cancelled.terminal().code());
        assertEquals(2, polls.get());
        assertEquals(0, source.samples.get());
    }

    private static ElevationRasterization.Plan plan(
            ElevationSourceMetadata metadata, Envelope visible, RasterRequestLimits limits) {
        return ElevationRasterization.plan(
                        metadata, visible, 0.01, RasterInterpolation.NEAREST, limits)
                .orElseThrow();
    }

    private static void assertPlanLimit(
            ElevationSourceMetadata metadata, RasterRequestLimits limits) {
        assertEquals(
                "SOURCE_LIMIT_EXCEEDED",
                assertThrows(
                                SourceException.class,
                                () -> plan(metadata, metadata.sampleBounds(), limits))
                        .terminal()
                        .code());
    }

    private static PackedElevationGrid projectedGrid(
            double[] samples, BitSet noData, ElevationUnit unit) {
        return PackedElevationGrid.copyOf(
                metadata(CrsDefinitions.EPSG_3857, unit), samples, noData);
    }

    private static int shadeChannel(
            PackedElevationGrid source, ElevationHillshade hillshade, int column, int row) {
        return (rasterize(source, hillshade).pixels().rgbaAt(column, row) >>> 24) & 0xff;
    }

    private static RasterRead rasterize(ElevationSource source, ElevationHillshade hillshade) {
        var plan =
                ElevationRasterization.plan(
                                source.metadata(),
                                source.metadata().sampleBounds(),
                                0.01,
                                RasterInterpolation.NEAREST,
                                RasterRequestLimits.LEVEL_1)
                        .orElseThrow();
        return ElevationRasterization.rasterize(
                source,
                plan,
                hillshadeStyle(source.metadata().elevationUnit(), hillshade),
                CancellationToken.none());
    }

    private static ElevationRasterStyle hillshadeStyle(
            ElevationUnit unit, ElevationHillshade hillshade) {
        return new ElevationRasterStyle(
                new ElevationColorRamp(
                        unit,
                        List.of(
                                new ElevationColorStop(-1, new Rgba(200, 200, 200, 77)),
                                new ElevationColorStop(1, new Rgba(200, 200, 200, 77)))),
                Rgba.TRANSPARENT,
                Optional.of(hillshade));
    }

    private static ElevationRasterStyle style(ElevationUnit unit) {
        return ElevationRasterStyle.of(
                new ElevationColorRamp(
                        unit,
                        List.of(
                                new ElevationColorStop(0, Rgba.rgb(0, 0, 0)),
                                new ElevationColorStop(1, Rgba.rgb(255, 255, 255)))));
    }

    private static ElevationSourceMetadata metadata(
            io.github.mundanej.map.api.CrsDefinition crs, ElevationUnit unit) {
        return new ElevationSourceMetadata(
                new SourceIdentity("elevation", "Elevation"),
                3,
                3,
                new Envelope(0, 0, 2, 2),
                CrsMetadata.recognized(
                        crs, Optional.of(crs.canonicalIdentifier()), Optional.empty()),
                unit);
    }

    private static final class CountingSource implements ElevationSource {
        private final ElevationSource delegate;
        private final AtomicInteger samples = new AtomicInteger();

        private CountingSource(ElevationSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public ElevationSourceMetadata metadata() {
            return delegate.metadata();
        }

        @Override
        public ElevationSourceLimits limits() {
            return delegate.limits();
        }

        @Override
        public io.github.mundanej.map.api.DiagnosticReport openingDiagnostics() {
            return delegate.openingDiagnostics();
        }

        @Override
        public OptionalDouble sample(int column, int row) {
            samples.incrementAndGet();
            return delegate.sample(column, row);
        }

        @Override
        public boolean isClosed() {
            return delegate.isClosed();
        }

        @Override
        public void close() {
            delegate.close();
        }
    }

    private static final class ConstantSource implements ElevationSource {
        private final ElevationSourceMetadata metadata;

        private ConstantSource(ElevationSourceMetadata metadata) {
            this.metadata = metadata;
        }

        @Override
        public ElevationSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public ElevationSourceLimits limits() {
            return new ElevationSourceLimits(
                    metadata.columnCount(),
                    metadata.rowCount(),
                    metadata.sampleCount(),
                    Math.multiplyExact(metadata.sampleCount(), 9L),
                    1);
        }

        @Override
        public io.github.mundanej.map.api.DiagnosticReport openingDiagnostics() {
            return io.github.mundanej.map.api.DiagnosticReport.empty();
        }

        @Override
        public OptionalDouble sample(int column, int row) {
            return OptionalDouble.of(0);
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }
}
