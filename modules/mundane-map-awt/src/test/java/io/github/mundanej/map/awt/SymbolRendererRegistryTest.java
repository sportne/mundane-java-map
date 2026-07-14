package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.LineStringGeometry;
import io.github.mundanej.map.api.LineSymbol;
import io.github.mundanej.map.api.MarkerSymbol;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.PolygonGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SolidFillSymbol;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolException;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRendererKey;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SymbolRendererRegistryTest {
    private static final SymbolRendererKey CUSTOM_MARKER_KEY =
            new SymbolRendererKey("example.marker");
    private static final SymbolRendererKey CUSTOM_LINE_KEY = new SymbolRendererKey("example.line");
    private static final SymbolRendererKey PARENT_MARKER_KEY =
            new SymbolRendererKey("example.parent-marker");

    @Test
    void builtInsAreSourceListedAndBuildersAreSingleUseAndIsolated() {
        SymbolRendererRegistry builtIns = SymbolRendererRegistry.builtIn();
        assertNotNull(builtIns.find(SymbolRole.MARKER, VectorMarkerSymbol.RENDERER_KEY));
        assertNotNull(builtIns.find(SymbolRole.MARKER, RasterIconSymbol.RENDERER_KEY));
        assertNotNull(builtIns.find(SymbolRole.LINE, SolidLineSymbol.RENDERER_KEY));
        assertNotNull(builtIns.find(SymbolRole.FILL, SolidFillSymbol.RENDERER_KEY));
        assertNotNull(builtIns.find(SymbolRole.FILL, HatchFillSymbol.RENDERER_KEY));
        assertNotNull(builtIns.find(SymbolRole.MARKER, CompositeSymbol.RENDERER_KEY));
        assertNotNull(builtIns.find(SymbolRole.LINE, CompositeSymbol.RENDERER_KEY));
        assertNotNull(builtIns.find(SymbolRole.FILL, CompositeSymbol.RENDERER_KEY));

        SymbolRendererRegistry.Builder builder = SymbolRendererRegistry.builder();
        builder.register(
                SymbolRole.MARKER, CUSTOM_MARKER_KEY, markerRenderer(new AtomicReference<>()));
        SymbolRendererRegistry first = builder.build();
        assertNotNull(first.find(SymbolRole.MARKER, CUSTOM_MARKER_KEY));
        assertThrows(IllegalStateException.class, builder::build);
        assertThrows(
                IllegalStateException.class,
                () ->
                        builder.register(
                                SymbolRole.MARKER,
                                new SymbolRendererKey("example.after-build"),
                                markerRenderer(new AtomicReference<>())));
        assertNull(
                SymbolRendererRegistry.builder()
                        .build()
                        .find(SymbolRole.MARKER, CUSTOM_MARKER_KEY));
    }

    @Test
    void registrationRejectsReservedDuplicateAndLegacyApplicationSlots() {
        AwtSymbolRenderer renderer = markerRenderer(new AtomicReference<>());
        SymbolException reserved =
                assertThrows(
                        SymbolException.class,
                        () ->
                                SymbolRendererRegistry.builder()
                                        .register(
                                                SymbolRole.MARKER,
                                                VectorMarkerSymbol.RENDERER_KEY,
                                                renderer));
        assertEquals(SymbolException.RENDERER_RESERVED_KEY, reserved.code());
        assertEquals(List.of("role", "key"), List.copyOf(reserved.context().keySet()));

        SymbolRendererRegistry.Builder duplicate = SymbolRendererRegistry.builder();
        duplicate.register(SymbolRole.MARKER, CUSTOM_MARKER_KEY, renderer);
        SymbolException failure =
                assertThrows(
                        SymbolException.class,
                        () -> duplicate.register(SymbolRole.MARKER, CUSTOM_MARKER_KEY, renderer));
        assertEquals(SymbolException.RENDERER_DUPLICATE, failure.code());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        SymbolRendererRegistry.builder()
                                .register(SymbolRole.LEGACY_GEOMETRY, CUSTOM_MARKER_KEY, renderer));
    }

    @Test
    void customMarkerUsesExplicitRegistryContextAndEmptyRegistryHasNoFallback() throws Exception {
        AtomicReference<AwtSymbolRenderContext> observed = new AtomicReference<>();
        SymbolRendererRegistry registry =
                SymbolRendererRegistry.builder()
                        .register(SymbolRole.MARKER, CUSTOM_MARKER_KEY, markerRenderer(observed))
                        .build();
        Feature feature =
                new Feature(
                        "custom",
                        "",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        new CustomMarker("leaf", 1.0));
        BufferedImage image = render(feature, registry);
        assertEquals(new Color(30, 100, 210), new Color(image.getRGB(50, 50), true));
        assertEquals(new Coordinate(50, 50), observed.get().markerAnchorScreen().orElseThrow());
        assertEquals(feature.geometry(), observed.get().featureGeometry());
        assertEquals(feature.geometry(), observed.get().renderGeometry());

        SymbolException missing =
                assertThrows(
                        SymbolException.class,
                        () -> render(feature, SymbolRendererRegistry.builder().build()));
        assertEquals(SymbolException.RENDERER_NOT_REGISTERED, missing.code());
    }

    @Test
    void supportsAndResultContractsFailDeterministically() throws Exception {
        AwtSymbolRenderer unsupported =
                new AwtSymbolRenderer() {
                    @Override
                    public boolean supports(Symbol value) {
                        return false;
                    }

                    @Override
                    public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                        throw new AssertionError();
                    }
                };
        Feature feature =
                new Feature(
                        "custom",
                        "",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        new CustomMarker("leaf", 1));
        SymbolException mismatch =
                assertThrows(
                        SymbolException.class,
                        () ->
                                render(
                                        feature,
                                        SymbolRendererRegistry.builder()
                                                .register(
                                                        SymbolRole.MARKER,
                                                        CUSTOM_MARKER_KEY,
                                                        unsupported)
                                                .build()));
        assertEquals(SymbolException.RENDERER_VALUE_MISMATCH, mismatch.code());

        AwtSymbolRenderer invalid =
                new AwtSymbolRenderer() {
                    @Override
                    public boolean supports(Symbol value) {
                        return true;
                    }

                    @Override
                    public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                        return SymbolRenderResult.none();
                    }
                };
        SymbolException invalidResult =
                assertThrows(
                        SymbolException.class,
                        () ->
                                render(
                                        feature,
                                        SymbolRendererRegistry.builder()
                                                .register(
                                                        SymbolRole.MARKER,
                                                        CUSTOM_MARKER_KEY,
                                                        invalid)
                                                .build()));
        assertEquals(SymbolException.RENDERER_INVALID_RESULT, invalidResult.code());
    }

    @Test
    void derivedEndpointAndClosedOutlineContextsUseRegisteredCustomRenderers() throws Exception {
        AtomicReference<AwtSymbolRenderContext> markerContext = new AtomicReference<>();
        AtomicReference<AwtSymbolRenderContext> lineContext = new AtomicReference<>();
        AwtSymbolRenderer lineRenderer =
                new AwtSymbolRenderer() {
                    @Override
                    public boolean supports(Symbol value) {
                        return value instanceof CustomLine;
                    }

                    @Override
                    public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                        lineContext.set(context);
                        return SymbolRenderResult.none();
                    }
                };
        SymbolRendererRegistry registry =
                SymbolRendererRegistry.builderWithBuiltIns()
                        .register(
                                SymbolRole.MARKER, CUSTOM_MARKER_KEY, markerRenderer(markerContext))
                        .register(SymbolRole.LINE, CUSTOM_LINE_KEY, lineRenderer)
                        .build();
        SolidLineSymbol line =
                SolidLineSymbol.of(
                        new SymbolStroke(
                                Rgba.TRANSPARENT, new SymbolLength(1, SymbolUnit.SCREEN_PIXEL)),
                        Optional.empty(),
                        Optional.of(new CustomMarker("endpoint", 1)),
                        1);
        render(
                new Feature(
                        "line",
                        "",
                        new LineStringGeometry(CoordinateSequence.of(-10, 0, 10, 0)),
                        Map.of(),
                        line),
                registry);
        assertTrue(markerContext.get().endpointBearingDegrees().isPresent());
        assertTrue(markerContext.get().featureGeometry() instanceof LineStringGeometry);
        assertTrue(markerContext.get().renderGeometry() instanceof PointGeometry);

        SolidFillSymbol fill =
                SolidFillSymbol.of(Rgba.TRANSPARENT, Optional.of(new CustomLine()), 1);
        PolygonGeometry polygon =
                new PolygonGeometry(
                        CoordinateSequence.of(-10, -10, 10, -10, 10, 10, -10, 10, -10, -10));
        render(new Feature("fill", "", polygon, Map.of(), fill), registry);
        assertTrue(lineContext.get().closedRing());
        assertEquals(polygon, lineContext.get().featureGeometry());
        assertTrue(lineContext.get().renderGeometry() instanceof LineStringGeometry);
    }

    @Test
    void sameRoleRecursionPropagatesOpacityAndRejectsCrossRoleBeforeLookup() throws Exception {
        AtomicReference<AwtSymbolRenderContext> childContext = new AtomicReference<>();
        CustomMarker child = new CustomMarker("child", 0.25);
        AwtSymbolRenderer parentRenderer =
                new AwtSymbolRenderer() {
                    @Override
                    public boolean supports(Symbol value) {
                        return value instanceof ParentMarker;
                    }

                    @Override
                    public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                        ParentMarker parent = (ParentMarker) value;
                        return context.renderChild(parent.child(), 0.5);
                    }
                };
        SymbolRendererRegistry registry =
                SymbolRendererRegistry.builder()
                        .register(SymbolRole.MARKER, PARENT_MARKER_KEY, parentRenderer)
                        .register(
                                SymbolRole.MARKER, CUSTOM_MARKER_KEY, markerRenderer(childContext))
                        .build();
        render(
                new Feature(
                        "recursive",
                        "",
                        new PointGeometry(new Coordinate(0, 0)),
                        Map.of(),
                        new ParentMarker(child)),
                registry);
        assertEquals(0.5, childContext.get().inheritedOpacity());

        AwtSymbolRenderer crossRole =
                new AwtSymbolRenderer() {
                    @Override
                    public boolean supports(Symbol value) {
                        return value instanceof ParentMarker;
                    }

                    @Override
                    public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                        return context.renderChild(new CustomLine(), 1);
                    }
                };
        SymbolException mismatch =
                assertThrows(
                        SymbolException.class,
                        () ->
                                render(
                                        new Feature(
                                                "cross-role",
                                                "",
                                                new PointGeometry(new Coordinate(0, 0)),
                                                Map.of(),
                                                new ParentMarker(child)),
                                        SymbolRendererRegistry.builder()
                                                .register(
                                                        SymbolRole.MARKER,
                                                        PARENT_MARKER_KEY,
                                                        crossRole)
                                                .build()));
        assertEquals(SymbolException.ROLE_MISMATCH, mismatch.code());
        assertEquals(Map.of("contextRole", "MARKER", "childRole", "LINE"), mismatch.context());
    }

    private static AwtSymbolRenderer markerRenderer(
            AtomicReference<AwtSymbolRenderContext> observed) {
        return new AwtSymbolRenderer() {
            @Override
            public boolean supports(Symbol value) {
                return value instanceof CustomMarker;
            }

            @Override
            public SymbolRenderResult render(Symbol value, AwtSymbolRenderContext context) {
                observed.set(context);
                Coordinate anchor = context.markerAnchorScreen().orElseThrow();
                Graphics2D graphics = context.createGraphics();
                try {
                    graphics.setColor(new Color(30, 100, 210));
                    graphics.fillRect((int) anchor.x() - 3, (int) anchor.y() - 3, 7, 7);
                } finally {
                    graphics.dispose();
                }
                return SymbolRenderResult.markerBounds(
                        new Envelope(
                                anchor.x() - 3, anchor.y() - 3, anchor.x() + 4, anchor.y() + 4));
            }
        };
    }

    private static BufferedImage render(Feature feature, SymbolRendererRegistry registry)
            throws Exception {
        AtomicReference<BufferedImage> result = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        MapView view = TestMapViews.identity(registry);
                        view.setSize(100, 100);
                        view.setViewport(new MapViewport(100, 100, 0, 0, 1));
                        view.setLayers(
                                List.of(new InMemoryLayer("layer", "Layer", List.of(feature))));
                        BufferedImage image =
                                new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                        result.set(image);
                    });
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw failure;
        }
        return result.get();
    }

    private record CustomMarker(String kind, double opacity) implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return CUSTOM_MARKER_KEY;
        }
    }

    private record CustomLine() implements LineSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return CUSTOM_LINE_KEY;
        }

        @Override
        public double opacity() {
            return 1;
        }
    }

    private record ParentMarker(CustomMarker child) implements MarkerSymbol {
        @Override
        public SymbolRendererKey rendererKey() {
            return PARENT_MARKER_KEY;
        }

        @Override
        public double opacity() {
            return 1;
        }
    }
}
