package io.github.mundanej.map.vaadin;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.FeaturePortrayal;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FixedSymbolSelector;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.Layer;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.NamedSymbol;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class IconResourceBatchTest {
    @Test
    void discoversIconsThroughEverySupportedNestedSymbolPath() {
        RasterIconSymbol icon = icon(0x11223344);
        CompositeSymbol composite = CompositeSymbol.of(List.of(icon), 1);
        SymbolStroke stroke =
                new SymbolStroke(
                        Rgba.rgb(10, 20, 30), new SymbolLength(1, SymbolUnit.SCREEN_PIXEL));
        SolidLineSymbol endpoints =
                SolidLineSymbol.of(
                        stroke, java.util.Optional.of(composite), java.util.Optional.of(icon), 1);
        SolidFillSymbol solid =
                SolidFillSymbol.of(Rgba.rgb(40, 50, 60), java.util.Optional.of(endpoints), 1);
        HatchFillSymbol hatch =
                HatchFillSymbol.of(
                        HatchPattern.FORWARD_DIAGONAL,
                        stroke,
                        new SymbolLength(8, SymbolUnit.SCREEN_PIXEL),
                        SymbolRotationMode.SCREEN_RELATIVE,
                        java.util.Optional.of(endpoints),
                        1,
                        100);
        CoordinateSequence ring = CoordinateSequence.of(0, 0, 2, 0, 2, 2, 0, 2, 0, 0);
        Layer layer =
                new InMemoryLayer(
                        "nested",
                        "Nested",
                        List.of(
                                new Feature(
                                        "marker",
                                        "Marker",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of(),
                                        composite),
                                new Feature(
                                        "line",
                                        "Line",
                                        new LineStringGeometry(CoordinateSequence.of(0, 0, 1, 1)),
                                        Map.of(),
                                        endpoints),
                                new Feature(
                                        "solid",
                                        "Solid",
                                        new PolygonGeometry(ring),
                                        Map.of(),
                                        solid),
                                new Feature(
                                        "hatch",
                                        "Hatch",
                                        new PolygonGeometry(ring),
                                        Map.of(),
                                        hatch)));
        AtomicInteger registrations = new AtomicInteger();
        IconResourceBatch batch =
                IconResourceBatch.prepare(
                        List.of(layer),
                        authorized(icon)::contains,
                        bytes -> {
                            registrations.incrementAndGet();
                            return new IconResourceBatch.RegisteredResource(
                                    "./resource/nested", () -> {});
                        });
        assertEquals(1, registrations.get());
        batch.close();
    }

    @Test
    void bindingRequiresTheExactCatalogOwnedIconInstance() {
        RasterIconSymbol authorized = icon(0x01020304);
        RasterIconSymbol equalCopy = icon(0x01020304);
        InMemoryFeatureSource source =
                InMemoryFeatureSource.open(
                        new SourceIdentity("icons", "Icons"),
                        List.of(
                                new FeatureRecord(
                                        "one",
                                        "One",
                                        new PointGeometry(new Coordinate(0, 0)),
                                        Map.of())));
        FeaturePortrayal portrayal = FeaturePortrayal.markers(new FixedSymbolSelector(authorized));

        MundaneMapException unauthorized =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                FeatureSourceBinding.borrowed(
                                        "icons",
                                        "Icons",
                                        source,
                                        portrayal,
                                        NamedSymbolCatalog.of(
                                                List.of(new NamedSymbol("copy", equalCopy))),
                                        java.util.Optional.empty()));
        assertEquals(MundaneMapException.UNSUPPORTED_VALUE, unauthorized.code());
        assertEquals("binding", unauthorized.context().get("scope"));

        FeatureSourceBinding binding =
                FeatureSourceBinding.borrowed(
                        "icons",
                        "Icons",
                        source,
                        portrayal,
                        NamedSymbolCatalog.of(List.of(new NamedSymbol("authorized", authorized))),
                        java.util.Optional.empty());
        assertTrue(binding.authorizes(authorized));
        assertTrue(!binding.authorizes(equalCopy));
        binding.close();
        source.close();
    }

    @Test
    void stagesImmutableClosedBytesAndEncodesOnlyTheOpaqueRelativeResource() {
        RasterIconSymbol icon = icon(0x12345678);
        Layer layer = layer("icons", icon);
        List<byte[]> registered = new ArrayList<>();
        AtomicInteger removed = new AtomicInteger();
        IconResourceBatch batch =
                IconResourceBatch.prepare(
                        List.of(layer),
                        authorized(icon)::contains,
                        bytes -> {
                            registered.add(bytes.clone());
                            return new IconResourceBatch.RegisteredResource(
                                    "VAADIN/dynamic/resource/token/icon.mmri",
                                    removed::incrementAndGet);
                        });

        assertEquals(1, registered.size());
        assertArrayEquals(
                new byte[] {77, 77, 82, 73, 1, 0, 0, 1, 0, 1, 0, 0, 0x12, 0x34, 0x56, 0x78},
                registered.getFirst());
        SceneProtocol.Result result =
                new SceneProtocol(SceneProtocol.DEFAULT_LIMITS)
                        .encode(
                                List.of(layer),
                                Rgba.rgb(255, 255, 255),
                                MapViewport.initial(100, 100),
                                1,
                                2,
                                0,
                                batch);
        Map<?, ?> encodedLayer = map(list(result.scene().get("layers")).getFirst());
        Map<?, ?> encodedFeature = map(list(encodedLayer.get("features")).getFirst());
        Map<?, ?> primitive = map(list(encodedFeature.get("primitives")).getFirst());
        assertEquals("icon", primitive.get("kind"));
        assertEquals("./VAADIN/dynamic/resource/token/icon.mmri", primitive.get("resource"));
        assertTrue(!primitive.containsKey("pixels"));
        assertEquals(299, result.logicalBytes());

        batch.close();
        batch.close();
        assertEquals(1, removed.get());
        MundaneMapException expired =
                assertThrows(MundaneMapException.class, () -> batch.uri(icon));
        assertEquals(MundaneMapException.RESOURCE_UNAVAILABLE, expired.code());
    }

    @Test
    void authorizationRegistrationAndUriFailuresAreAtomic() {
        RasterIconSymbol first = icon(0x01020304);
        RasterIconSymbol second = icon(0x05060708);
        Layer layer =
                new InMemoryLayer(
                        "icons",
                        "Icons",
                        List.of(feature("first", first), feature("second", second)));
        AtomicInteger registrations = new AtomicInteger();
        AtomicInteger removals = new AtomicInteger();

        MundaneMapException unauthorized =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                IconResourceBatch.prepare(
                                        List.of(layer),
                                        authorized(first)::contains,
                                        ignored -> {
                                            registrations.incrementAndGet();
                                            return null;
                                        }));
        assertEquals(MundaneMapException.UNSUPPORTED_VALUE, unauthorized.code());
        assertEquals(0, registrations.get());

        assertThrows(
                IllegalStateException.class,
                () ->
                        IconResourceBatch.prepare(
                                List.of(layer),
                                ignored -> true,
                                bytes -> {
                                    if (registrations.incrementAndGet() == 2) {
                                        throw new IllegalStateException("register");
                                    }
                                    return new IconResourceBatch.RegisteredResource(
                                            "./resource/one", removals::incrementAndGet);
                                }));
        assertEquals(1, removals.get());

        AtomicInteger invalidRemoval = new AtomicInteger();
        MundaneMapException invalidUri =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                IconResourceBatch.prepare(
                                        List.of(layer("one", first)),
                                        ignored -> true,
                                        bytes ->
                                                new IconResourceBatch.RegisteredResource(
                                                        "https://remote.example/icon",
                                                        invalidRemoval::incrementAndGet)));
        assertEquals(MundaneMapException.RESOURCE_UNAVAILABLE, invalidUri.code());
        assertEquals(1, invalidRemoval.get());
    }

    @Test
    void rejectsResourceCardinalityBeforeRegistration() {
        List<Feature> features = new ArrayList<>();
        for (int index = 0; index <= IconResourceBatch.MAX_RESOURCES; index++) {
            features.add(feature("icon-" + index, icon(index)));
        }
        Layer layer = new InMemoryLayer("many", "Many", features);
        AtomicInteger registrations = new AtomicInteger();
        MundaneMapException failure =
                assertThrows(
                        MundaneMapException.class,
                        () ->
                                IconResourceBatch.prepare(
                                        List.of(layer),
                                        ignored -> true,
                                        bytes -> {
                                            registrations.incrementAndGet();
                                            return null;
                                        }));
        assertEquals(MundaneMapException.LIMIT_EXCEEDED, failure.code());
        assertEquals("iconResources", failure.context().get("limit"));
        assertEquals(0, registrations.get());
    }

    private static Layer layer(String id, RasterIconSymbol icon) {
        return new InMemoryLayer(id, id, List.of(feature(id, icon)));
    }

    private static Feature feature(String id, RasterIconSymbol icon) {
        return new Feature(id, id, new PointGeometry(new Coordinate(0, 0)), Map.of(), icon);
    }

    private static RasterIconSymbol icon(int rgba) {
        return RasterIconSymbol.of(
                1,
                1,
                new int[] {rgba},
                MarkerPlacement.centeredScreen(16),
                RasterInterpolation.NEAREST,
                1);
    }

    private static Set<RasterIconSymbol> authorized(RasterIconSymbol... icons) {
        Set<RasterIconSymbol> result = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(result, icons);
        return result;
    }

    private static List<?> list(Object value) {
        return (List<?>) value;
    }

    private static Map<?, ?> map(Object value) {
        return (Map<?, ?>) value;
    }
}
