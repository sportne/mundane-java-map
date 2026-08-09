package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeSelection;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.ElevationColorRamp;
import io.github.mundanej.map.api.ElevationColorStop;
import io.github.mundanej.map.api.ElevationRasterStyle;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterRead;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterRequestLimits;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterSourceLimits;
import io.github.mundanej.map.api.RasterSourceMetadata;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.PackedElevationGrid;
import io.github.mundanej.map.core.SyntheticRasterSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class BrowserRasterSliceTest {
    private static final CrsMetadata WEB_MERCATOR =
            CrsMetadata.recognized(CrsDefinitions.EPSG_3857, Optional.empty(), Optional.empty());

    @Test
    void binaryFramingIsExactAndGenerationAuthorized() {
        RgbaPixelBuffer pixels = RgbaPixelBuffer.copyOf(2, 1, new int[] {0x01020304, 0xfefdfcfb});
        byte[] encoded = RasterResourceBatch.encode(pixels, 17, 23);

        assertEquals(40, encoded.length);
        ByteBuffer header = ByteBuffer.wrap(encoded).order(ByteOrder.BIG_ENDIAN);
        assertArrayEquals(new byte[] {'M', 'M', 'R', 'W'}, java.util.Arrays.copyOf(encoded, 4));
        assertEquals(1, Byte.toUnsignedInt(header.get(4)));
        assertEquals(32, Short.toUnsignedInt(header.getShort(6)));
        assertEquals(2, header.getInt(8));
        assertEquals(1, header.getInt(12));
        assertEquals(17, header.getLong(16));
        assertEquals(23, header.getLong(24));
        assertArrayEquals(
                new byte[] {1, 2, 3, 4, (byte) 0xfe, (byte) 0xfd, (byte) 0xfc, (byte) 0xfb},
                java.util.Arrays.copyOfRange(encoded, 32, encoded.length));

        AtomicBoolean authorized = new AtomicBoolean(true);
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        RasterResourceBatch.Payload payload =
                new RasterResourceBatch.Payload(encoded, expiry, authorized::get);
        assertTrue(payload.availableAt(expiry));
        assertFalse(payload.availableAt(expiry.plusNanos(1)));
        authorized.set(false);
        assertFalse(payload.availableAt(expiry.minusSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RasterResourceBatch.encode(
                                RgbaPixelBuffer.copyOf(1, 1, new int[] {0}), -1, 0));
    }

    @Test
    void resourceResponsesEnforceAuthorizationExpiryAndHeaders() throws IOException {
        byte[] body =
                RasterResourceBatch.encode(
                        RgbaPixelBuffer.copyOf(1, 1, new int[] {0x01020304}), 2, 3);
        AtomicBoolean authorized = new AtomicBoolean(true);
        Instant expiry = Instant.parse("2030-01-01T00:00:00Z");
        RasterResourceBatch.Payload payload =
                new RasterResourceBatch.Payload(body, expiry, authorized::get);
        RecordingResponse accepted = new RecordingResponse();

        RasterResourceBatch.writeResponse(payload, expiry, accepted);

        assertEquals("nosniff", accepted.headers.get("X-Content-Type-Options"));
        assertEquals("private, no-store", accepted.headers.get("Cache-Control"));
        assertEquals("default-src 'none'", accepted.headers.get("Content-Security-Policy"));
        assertEquals(RasterResourceBatch.CONTENT_TYPE, accepted.contentType);
        assertEquals(body.length, accepted.contentLength);
        assertArrayEquals(body, accepted.output.toByteArray());

        authorized.set(false);
        RecordingResponse unauthorized = new RecordingResponse();
        RasterResourceBatch.writeResponse(payload, expiry.minusSeconds(1), unauthorized);
        assertEquals(410, unauthorized.status);
        assertEquals(0, unauthorized.output.size());

        authorized.set(true);
        RecordingResponse expired = new RecordingResponse();
        RasterResourceBatch.writeResponse(payload, expiry.plusNanos(1), expired);
        assertEquals(410, expired.status);
        assertEquals(0, expired.output.size());
    }

    @Test
    void rasterPlanningPreservesWindowInterpolationAndAxisPlacement() {
        SyntheticRasterSource source =
                SyntheticRasterSource.open(
                        new SourceIdentity("raster", "Raster"),
                        8,
                        4,
                        new Envelope(0, 0, 80, 40),
                        WEB_MERCATOR);
        RasterSourceBinding binding =
                RasterSourceBinding.borrowed(
                        "raster-layer",
                        "Raster layer",
                        source,
                        new BrowserRasterOptions(RasterInterpolation.BILINEAR, 0.5),
                        Optional.empty());

        BrowserRasterQueryEngine.Result result =
                new BrowserRasterQueryEngine()
                        .query(
                                List.of(binding),
                                List.of(),
                                new MapViewport(10, 20, 40, 20, 2),
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());

        assertFalse(result.cancelled());
        BrowserRasterWindow window = result.windows().getFirst();
        assertEquals(new RasterWindow(3, 0, 2, 4), window.sourceWindow());
        assertEquals(2, window.pixels().width());
        assertEquals(4, window.pixels().height());
        assertEquals(0.5, window.options().opacity());
        assertEquals(RasterInterpolation.BILINEAR, window.options().interpolation());
        assertEquals(new Envelope(30, 0, 50, 40), window.imageMapBounds());
        assertEquals(
                RasterGridPlacement.Kind.AXIS_ALIGNED, window.placement().orElseThrow().kind());
    }

    @Test
    void affinePlacementAndSparseElevationRemainServerProduced() {
        RasterAffineTransform transform = RasterAffineTransform.of(2, 1, 0.5, -2, 10, 30);
        AffineRasterSource affine = new AffineRasterSource(transform);
        RasterSourceBinding raster = RasterSourceBinding.borrowed("affine", "Affine", affine);

        ElevationSourceMetadata metadata =
                new ElevationSourceMetadata(
                        new SourceIdentity("terrain", "Terrain"),
                        2,
                        2,
                        new Envelope(0, 0, 10, 10),
                        WEB_MERCATOR,
                        ElevationUnit.METRE);
        BitSet noData = new BitSet();
        noData.set(1);
        PackedElevationGrid terrain =
                PackedElevationGrid.copyOf(metadata, new double[] {0, 0, 100, 50}, noData);
        ElevationColorRamp ramp =
                new ElevationColorRamp(
                        ElevationUnit.METRE,
                        List.of(
                                new ElevationColorStop(0, Rgba.rgb(0, 0, 0)),
                                new ElevationColorStop(100, Rgba.rgb(255, 255, 255))));
        ElevationRasterStyle style =
                ElevationRasterStyle.of(ramp).withNoDataColor(Rgba.TRANSPARENT);
        ElevationSourceBinding elevation =
                ElevationSourceBinding.borrowed(
                        "terrain-layer",
                        "Terrain",
                        terrain,
                        style,
                        BrowserRasterOptions.defaults(),
                        RasterRequestLimits.LEVEL_1);

        BrowserRasterQueryEngine.Result result =
                new BrowserRasterQueryEngine()
                        .query(
                                List.of(raster),
                                List.of(elevation),
                                new MapViewport(20, 20, 10, 15, 2),
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());

        assertEquals(2, result.windows().size());
        BrowserRasterWindow affineWindow = result.windows().getFirst();
        assertEquals(
                RasterGridPlacement.Kind.AFFINE, affineWindow.placement().orElseThrow().kind());
        BrowserRasterWindow elevationWindow = result.windows().get(1);
        assertTrue(elevationWindow.placement().isEmpty());
        assertEquals(0, elevationWindow.pixels().rgbaAt(1, 0));
        assertEquals(0xffffffff, elevationWindow.pixels().rgbaAt(0, 1));
    }

    @Test
    void bindingsEnforceExclusiveClaimsAndOwnedBorrowedCleanup() {
        SyntheticRasterSource borrowedSource = source("borrowed");
        RasterSourceBinding borrowed =
                RasterSourceBinding.borrowed("borrowed", "Borrowed", borrowedSource);
        RasterSourceBinding duplicate =
                RasterSourceBinding.borrowed("duplicate", "Duplicate", borrowedSource);
        MundaneMap map = new MundaneMap(System::nanoTime);
        borrowed.attach(map);
        assertThrows(IllegalStateException.class, () -> duplicate.attach(map));
        borrowed.release(map);
        assertFalse(borrowedSource.isClosed());

        SyntheticRasterSource ownedSource = source("owned");
        RasterSourceBinding owned =
                RasterSourceBinding.owned(
                        "owned",
                        "Owned",
                        ownedSource,
                        BrowserRasterOptions.defaults(),
                        Optional.empty());
        owned.attach(map);
        owned.release(map);
        assertTrue(ownedSource.isClosed());
        map.close();
    }

    @Test
    void resourceBatchStagesMetadataAndCleansUpAtomically() {
        BrowserRasterWindow window = window();
        AtomicInteger removals = new AtomicInteger();
        List<RasterResourceBatch.Payload> payloads = new ArrayList<>();
        RasterResourceBatch batch =
                RasterResourceBatch.prepare(
                        List.of(window),
                        2,
                        3,
                        100,
                        () -> true,
                        payload -> {
                            payloads.add(payload);
                            return new RasterResourceBatch.RegisteredResource(
                                    "VAADIN/dynamic/window", removals::incrementAndGet);
                        });
        assertEquals(1, payloads.size());
        assertEquals(window.encodedBytes(), batch.encodedBytes());
        Map<String, Object> encoded = batch.encodedWindows().getFirst();
        assertEquals("./VAADIN/dynamic/window", encoded.get("resource"));
        assertEquals("AFFINE", ((Map<?, ?>) encoded.get("placement")).get("kind"));
        batch.close();
        batch.close();
        assertEquals(1, removals.get());
        assertThrows(MundaneMapException.class, batch::encodedWindows);

        AtomicInteger invalidRemoval = new AtomicInteger();
        assertThrows(
                MundaneMapException.class,
                () ->
                        RasterResourceBatch.prepare(
                                List.of(window),
                                2,
                                3,
                                0,
                                () -> true,
                                ignored ->
                                        new RasterResourceBatch.RegisteredResource(
                                                "https://remote.example/window",
                                                invalidRemoval::incrementAndGet)));
        assertEquals(1, invalidRemoval.get());

        List<BrowserRasterWindow> tooMany = new ArrayList<>();
        for (int index = 0; index <= RasterResourceBatch.MAX_WINDOWS; index++) {
            tooMany.add(window);
        }
        assertThrows(
                MundaneMapException.class,
                () -> RasterResourceBatch.prepare(tooMany, 1, 1, 0, () -> true, ignored -> null));
        BrowserRasterWindow tooWide =
                new BrowserRasterWindow(
                        "wide",
                        "Wide",
                        RgbaPixelBuffer.copyOf(16_385, 1, new int[16_385]),
                        new Envelope(0, 0, 1, 1),
                        new Envelope(0, 0, 1, 1),
                        Optional.empty(),
                        new RasterWindow(0, 0, 1, 1),
                        BrowserRasterOptions.defaults());
        assertThrows(
                MundaneMapException.class,
                () ->
                        RasterResourceBatch.prepare(
                                List.of(tooWide), 1, 1, 0, () -> true, ignored -> null));
        assertEquals(0, RasterResourceBatch.empty().encodedBytes());
        RasterResourceBatch failingClose =
                RasterResourceBatch.prepare(
                        List.of(window),
                        1,
                        1,
                        0,
                        () -> true,
                        ignored ->
                                new RasterResourceBatch.RegisteredResource(
                                        "./window",
                                        () -> {
                                            throw new IllegalStateException("close");
                                        }));
        assertThrows(IllegalStateException.class, failingClose::close);
    }

    @Test
    void optionsBindingsAndFailuresUseClosedContracts() {
        BrowserRasterOptions options = BrowserRasterOptions.defaults();
        assertEquals(
                RasterInterpolation.BILINEAR,
                options.withInterpolation(RasterInterpolation.BILINEAR).interpolation());
        assertEquals(0.25, options.withOpacity(0.25).opacity());
        assertThrows(
                IllegalArgumentException.class,
                () -> new BrowserRasterOptions(RasterInterpolation.NEAREST, 2));

        SyntheticRasterSource source = source("accessors");
        RasterSourceBinding binding = RasterSourceBinding.borrowed("a", "A", source);
        assertEquals("a", binding.id());
        assertEquals("A", binding.name());
        assertEquals(source, binding.source());
        assertEquals(options, binding.options());
        assertTrue(binding.tighterLimits().isEmpty());
        assertFalse(binding.owned());
        assertFalse(binding.isClosed());
        binding.close();
        assertTrue(binding.isClosed());
        binding.close();
        MundaneMap closedBindingMap = new MundaneMap(System::nanoTime);
        assertThrows(IllegalStateException.class, () -> binding.attach(closedBindingMap));
        closedBindingMap.close();

        PackedElevationGrid terrain = terrain("terrain-accessors");
        ElevationRasterStyle style = style();
        ElevationSourceBinding elevation =
                ElevationSourceBinding.owned(
                        "e", "E", terrain, style, options, RasterRequestLimits.LEVEL_1);
        assertEquals("e", elevation.id());
        assertEquals("E", elevation.name());
        assertEquals(terrain, elevation.source());
        assertEquals(style, elevation.style());
        assertEquals(options, elevation.options());
        assertEquals(RasterRequestLimits.LEVEL_1, elevation.requestLimits());
        assertTrue(elevation.owned());
        elevation.close();
        assertTrue(elevation.isClosed());
        assertTrue(terrain.isClosed());

        MundaneMap map = new MundaneMap(System::nanoTime);
        PackedElevationGrid borrowedTerrain = terrain("borrowed-terrain");
        ElevationSourceBinding borrowedElevation =
                ElevationSourceBinding.borrowed(
                        "borrowed-elevation",
                        "Borrowed elevation",
                        borrowedTerrain,
                        style(),
                        options,
                        RasterRequestLimits.LEVEL_1);
        ElevationSourceBinding duplicateElevation =
                ElevationSourceBinding.borrowed(
                        "duplicate-elevation",
                        "Duplicate elevation",
                        borrowedTerrain,
                        style(),
                        options,
                        RasterRequestLimits.LEVEL_1);
        borrowedElevation.attach(map);
        assertThrows(IllegalStateException.class, borrowedElevation::close);
        assertThrows(IllegalStateException.class, () -> duplicateElevation.attach(map));
        borrowedElevation.detach(map);
        duplicateElevation.attach(map);
        duplicateElevation.release(map);
        assertFalse(borrowedTerrain.isClosed());
        borrowedElevation.close();
        duplicateElevation.close();
        borrowedTerrain.close();
        map.close();
    }

    @Test
    void queryFailuresRemainStableAndLeaveExplicitAbsence() {
        CrsMetadata geographic =
                CrsMetadata.recognized(
                        CrsDefinitions.EPSG_4326, Optional.empty(), Optional.empty());
        SyntheticRasterSource wrongRaster =
                SyntheticRasterSource.open(
                        new SourceIdentity("wrong-raster", "Wrong raster"),
                        2,
                        2,
                        new Envelope(0, 0, 2, 2),
                        geographic);
        ElevationSourceMetadata wrongMetadata =
                new ElevationSourceMetadata(
                        new SourceIdentity("wrong-terrain", "Wrong terrain"),
                        2,
                        2,
                        new Envelope(0, 0, 1, 1),
                        geographic,
                        ElevationUnit.METRE);
        PackedElevationGrid wrongTerrain =
                PackedElevationGrid.copyOf(wrongMetadata, new double[] {0, 1, 2, 3}, new BitSet());
        BrowserRasterQueryEngine.Result result =
                new BrowserRasterQueryEngine()
                        .query(
                                List.of(
                                        RasterSourceBinding.borrowed(
                                                "wrong-raster-layer", "Wrong raster", wrongRaster)),
                                List.of(
                                        ElevationSourceBinding.borrowed(
                                                "wrong-terrain-layer",
                                                "Wrong terrain",
                                                wrongTerrain,
                                                style(),
                                                BrowserRasterOptions.defaults(),
                                                RasterRequestLimits.LEVEL_1)),
                                new MapViewport(10, 10, 1, 1, 1),
                                CrsDefinitions.EPSG_3857,
                                CancellationToken.none());
        assertTrue(result.windows().isEmpty());
        assertEquals(2, result.reports().size());
        result.reports()
                .values()
                .forEach(
                        report ->
                                assertEquals(
                                        "RASTER_CONFIGURATION_UNSUPPORTED",
                                        report.entries().getLast().code()));
        wrongRaster.close();
        wrongTerrain.close();
    }

    @Test
    void componentPublishesAndRevokesRasterGenerations() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger removals = new AtomicInteger();
        MundaneMap.IconSessionAccess sessionAccess =
                new MundaneMap.IconSessionAccess() {
                    @Override
                    public IconResourceBatch.Registrar resourceRegistrar(MundaneMap map) {
                        return bytes ->
                                new IconResourceBatch.RegisteredResource(
                                        "./resource/" + registrations.incrementAndGet(),
                                        removals::incrementAndGet);
                    }

                    @Override
                    public com.vaadin.flow.shared.Registration addDestroyListener(
                            MundaneMap map, Runnable listener) {
                        return null;
                    }
                };
        MundaneMap map =
                new MundaneMap(
                        System::nanoTime,
                        Runnable::run,
                        Runnable::run,
                        Runnable::run,
                        sessionAccess);
        map.setViewport(new MapViewport(2, 2, 1, 1, 1));
        SyntheticRasterSource source = source("component");
        RasterSourceBinding binding =
                RasterSourceBinding.owned(
                        "component-raster",
                        "Component raster",
                        source,
                        BrowserRasterOptions.defaults(),
                        Optional.empty());
        map.setRasterSourceBindings(List.of(binding));
        assertEquals(1, ((List<?>) map.encodedSceneForTest().get("rasters")).size());
        assertTrue(registrations.get() > 0);
        map.setRasterSourceBindings(List.of());
        assertTrue(source.isClosed());
        assertTrue(removals.get() > 0);
        map.close();
    }

    @Test
    void componentOwnsReplacementWhenPriorResourceCleanupFails() {
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger laterRemoval = new AtomicInteger();
        MundaneMap.IconSessionAccess sessionAccess =
                new MundaneMap.IconSessionAccess() {
                    @Override
                    public IconResourceBatch.Registrar resourceRegistrar(MundaneMap map) {
                        return ignored -> {
                            throw new AssertionError("no icon registration expected");
                        };
                    }

                    @Override
                    public RasterResourceBatch.Registrar rasterResourceRegistrar(MundaneMap map) {
                        return ignored -> {
                            int registration = registrations.incrementAndGet();
                            return new RasterResourceBatch.RegisteredResource(
                                    "./raster/" + registration,
                                    () -> {
                                        if (registration == 1) {
                                            throw new IllegalStateException("prior unregister");
                                        }
                                        laterRemoval.incrementAndGet();
                                    });
                        };
                    }

                    @Override
                    public com.vaadin.flow.shared.Registration addDestroyListener(
                            MundaneMap map, Runnable listener) {
                        return null;
                    }
                };
        MundaneMap map =
                new MundaneMap(
                        System::nanoTime,
                        Runnable::run,
                        Runnable::run,
                        Runnable::run,
                        sessionAccess);
        map.setViewport(new MapViewport(2, 2, 1, 1, 1));
        RasterSourceBinding binding =
                RasterSourceBinding.borrowed("cleanup", "Cleanup", source("cleanup"));
        map.setRasterSourceBindings(List.of(binding));
        assertEquals(1, registrations.get());

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class, () -> map.setBackground(Rgba.rgb(1, 2, 3)));

        assertEquals("prior unregister", failure.getMessage());
        assertEquals(2, registrations.get());
        map.close();
        assertEquals(1, laterRemoval.get());
        binding.source().close();
        binding.close();
    }

    @Test
    void ownedBindingRemovalCompletesWhenPriorResourceCleanupFails() {
        MundaneMap.IconSessionAccess sessionAccess =
                new MundaneMap.IconSessionAccess() {
                    @Override
                    public IconResourceBatch.Registrar resourceRegistrar(MundaneMap map) {
                        return ignored -> {
                            throw new AssertionError("no icon registration expected");
                        };
                    }

                    @Override
                    public RasterResourceBatch.Registrar rasterResourceRegistrar(MundaneMap map) {
                        return ignored ->
                                new RasterResourceBatch.RegisteredResource(
                                        "./raster/owned",
                                        () -> {
                                            throw new IllegalStateException("unregister");
                                        });
                    }

                    @Override
                    public com.vaadin.flow.shared.Registration addDestroyListener(
                            MundaneMap map, Runnable listener) {
                        return null;
                    }
                };
        MundaneMap map =
                new MundaneMap(
                        System::nanoTime,
                        Runnable::run,
                        Runnable::run,
                        Runnable::run,
                        sessionAccess);
        map.setViewport(new MapViewport(2, 2, 1, 1, 1));
        SyntheticRasterSource source = source("owned-removal");
        RasterSourceBinding binding =
                RasterSourceBinding.owned(
                        "owned-removal",
                        "Owned removal",
                        source,
                        BrowserRasterOptions.defaults(),
                        Optional.empty());
        map.setRasterSourceBindings(List.of(binding));

        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class, () -> map.setRasterSourceBindings(List.of()));

        assertEquals("unregister", failure.getMessage());
        assertTrue(source.isClosed());
        assertTrue(binding.isClosed());
        map.close();
    }

    @Test
    void ownedElevationRemovalCompletesWhenPriorResourceCleanupFails() {
        AtomicBoolean failNextRemoval = new AtomicBoolean();
        MundaneMap map = resourceMapWithFallibleRasterRemoval(failNextRemoval);
        map.setViewport(new MapViewport(2, 2, 0.5, 0.5, 0.5));
        PackedElevationGrid terrain = terrain("owned-elevation-removal");
        ElevationSourceBinding binding =
                ElevationSourceBinding.owned(
                        "owned-elevation-removal",
                        "Owned elevation removal",
                        terrain,
                        style(),
                        BrowserRasterOptions.defaults(),
                        RasterRequestLimits.LEVEL_1);
        map.setElevationSourceBindings(List.of(binding));
        failNextRemoval.set(true);

        assertThrows(IllegalStateException.class, () -> map.setElevationSourceBindings(List.of()));

        assertTrue(terrain.isClosed());
        assertTrue(binding.isClosed());
        map.close();
    }

    @Test
    void featureRemovalCompletesAcrossFallibleRasterPublication() {
        AtomicBoolean failNextRemoval = new AtomicBoolean();
        MundaneMap map = resourceMapWithFallibleRasterRemoval(failNextRemoval);
        map.setViewport(new MapViewport(2, 2, 1, 1, 1));
        SyntheticRasterSource raster = source("feature-removal-raster");
        RasterSourceBinding rasterBinding =
                RasterSourceBinding.borrowed(
                        "feature-removal-raster", "Feature removal raster", raster);
        map.setRasterSourceBindings(List.of(rasterBinding));
        InMemoryFeatureSource featureSource =
                InMemoryFeatureSource.open(
                        new SourceIdentity("owned-feature-removal", "Owned feature removal"),
                        List.of(),
                        Optional.empty(),
                        Optional.of(WEB_MERCATOR),
                        FeatureSourceLimits.LEVEL_1);
        FeatureSourceBinding featureBinding =
                FeatureSourceBinding.owned(
                        "owned-feature-removal",
                        "Owned feature removal",
                        featureSource,
                        marker(),
                        SolidLineSymbol.of(
                                new SymbolStroke(
                                        Rgba.rgb(10, 20, 30),
                                        new SymbolLength(1, SymbolUnit.SCREEN_PIXEL)),
                                1),
                        SolidFillSymbol.of(Rgba.rgb(30, 20, 10), 1),
                        AttributeSelection.NONE,
                        Optional.empty());
        map.setFeatureSourceBindings(List.of(featureBinding));
        failNextRemoval.set(true);

        assertThrows(IllegalStateException.class, () -> map.setFeatureSourceBindings(List.of()));

        assertTrue(featureSource.isClosed());
        assertTrue(featureBinding.isClosed());
        map.close();
        raster.close();
        rasterBinding.close();
    }

    private static SyntheticRasterSource source(String id) {
        return SyntheticRasterSource.open(
                new SourceIdentity(id, id), 2, 2, new Envelope(0, 0, 2, 2), WEB_MERCATOR);
    }

    private static BrowserRasterWindow window() {
        RasterAffineTransform transform = RasterAffineTransform.of(1, 0, 0, -1, 0.5, 0.5);
        return new BrowserRasterWindow(
                "window",
                "Window",
                RgbaPixelBuffer.copyOf(1, 1, new int[] {0x01020304}),
                new Envelope(0, 0, 1, 1),
                new Envelope(0, 0, 1, 1),
                Optional.of(RasterGridPlacement.affine(transform)),
                new RasterWindow(0, 0, 1, 1),
                BrowserRasterOptions.defaults());
    }

    private static MundaneMap resourceMapWithFallibleRasterRemoval(AtomicBoolean failNextRemoval) {
        AtomicInteger registrations = new AtomicInteger();
        MundaneMap.IconSessionAccess sessionAccess =
                new MundaneMap.IconSessionAccess() {
                    @Override
                    public IconResourceBatch.Registrar resourceRegistrar(MundaneMap map) {
                        return ignored -> {
                            throw new AssertionError("no icon registration expected");
                        };
                    }

                    @Override
                    public RasterResourceBatch.Registrar rasterResourceRegistrar(MundaneMap map) {
                        return ignored -> {
                            int registration = registrations.incrementAndGet();
                            return new RasterResourceBatch.RegisteredResource(
                                    "./raster/fallible/" + registration,
                                    () -> {
                                        if (failNextRemoval.compareAndSet(true, false)) {
                                            throw new IllegalStateException("unregister");
                                        }
                                    });
                        };
                    }

                    @Override
                    public com.vaadin.flow.shared.Registration addDestroyListener(
                            MundaneMap map, Runnable listener) {
                        return null;
                    }
                };
        return new MundaneMap(
                System::nanoTime, Runnable::run, Runnable::run, Runnable::run, sessionAccess);
    }

    private static VectorMarkerSymbol marker() {
        VectorPath path =
                VectorPath.builder().moveTo(0, 0).lineTo(1, 0).lineTo(0, 1).close().build();
        return VectorMarkerSymbol.filledScreen(
                path, new Envelope(0, 0, 1, 1), Rgba.rgb(10, 20, 30), 8, 1);
    }

    private static PackedElevationGrid terrain(String id) {
        ElevationSourceMetadata metadata =
                new ElevationSourceMetadata(
                        new SourceIdentity(id, id),
                        2,
                        2,
                        new Envelope(0, 0, 1, 1),
                        WEB_MERCATOR,
                        ElevationUnit.METRE);
        return PackedElevationGrid.copyOf(metadata, new double[] {0, 1, 2, 3}, new BitSet());
    }

    private static ElevationRasterStyle style() {
        return ElevationRasterStyle.of(
                new ElevationColorRamp(
                        ElevationUnit.METRE,
                        List.of(
                                new ElevationColorStop(0, Rgba.rgb(0, 0, 0)),
                                new ElevationColorStop(3, Rgba.rgb(255, 255, 255)))));
    }

    private static final class AffineRasterSource implements RasterSource {
        private final RasterSourceMetadata metadata;

        private AffineRasterSource(RasterAffineTransform transform) {
            metadata =
                    RasterSourceMetadata.withPlacement(
                            new SourceIdentity("affine-source", "Affine source"),
                            4,
                            4,
                            RasterGridPlacement.affine(transform),
                            Optional.of(WEB_MERCATOR));
        }

        @Override
        public RasterSourceMetadata metadata() {
            return metadata;
        }

        @Override
        public RasterSourceLimits limits() {
            return RasterSourceLimits.LEVEL_1;
        }

        @Override
        public DiagnosticReport openingDiagnostics() {
            return DiagnosticReport.empty();
        }

        @Override
        public RasterRead read(RasterRequest request, CancellationToken cancellation) {
            RgbaPixelBuffer.Builder pixels =
                    RgbaPixelBuffer.builder(request.outputWidth(), request.outputHeight());
            for (int row = 0; row < request.outputHeight(); row++) {
                for (int column = 0; column < request.outputWidth(); column++) {
                    pixels.setRgba(column, row, 0x102030ff);
                }
            }
            return new RasterRead(request.sourceWindow(), pixels.build(), DiagnosticReport.empty());
        }

        @Override
        public boolean isClosed() {
            return false;
        }

        @Override
        public void close() {}
    }

    private static final class RecordingResponse implements RasterResourceBatch.ResourceResponse {
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int status;
        private String contentType;
        private long contentLength;

        @Override
        public void header(String name, String value) {
            headers.put(name, value);
        }

        @Override
        public void status(int value) {
            status = value;
        }

        @Override
        public void contentType(String value) {
            contentType = value;
        }

        @Override
        public void contentLength(long value) {
            contentLength = value;
        }

        @Override
        public OutputStream outputStream() {
            return output;
        }
    }
}
