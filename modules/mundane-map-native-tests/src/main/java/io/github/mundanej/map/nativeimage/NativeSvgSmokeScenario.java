package io.github.mundanej.map.nativeimage;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.RasterIconSymbol;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import io.github.mundanej.map.api.VectorExportSnapshot;
import io.github.mundanej.map.api.VectorExportSnapshotException;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.BuiltInMarkers;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.WebMercatorProjection;
import io.github.mundanej.map.io.svg.SvgMapExports;
import io.github.mundanej.map.io.svg.SvgSymbols;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;

final class NativeSvgSmokeScenario {
    private static final SourceIdentity ID = new SourceIdentity("native-svg", "Native SVG");

    private NativeSvgSmokeScenario() {}

    static void run() {
        Symbol symbol =
                SvgSymbols.parse(
                        ID,
                        """
                        <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 10 10">
                          <path d="M1 9 L5 1 L9 9 Z" fill="#24905e" fill-rule="evenodd"/>
                        </svg>
                        """
                                .getBytes(StandardCharsets.UTF_8),
                        MarkerPlacement.centeredScreen(32));
        if (symbol.role() != SymbolRole.MARKER) {
            throw new IllegalStateException("svg-native: imported role changed");
        }
        assertDtdRejected();
        renderOnEdt(symbol);
        assertVectorMapExport();
    }

    private static void assertVectorMapExport() {
        VectorExportSnapshot snapshot =
                VectorExportSnapshot.of(
                        64,
                        48,
                        Rgba.rgb(245, 246, 247),
                        new VectorExportSnapshot.ViewFrame(1, 0, new Coordinate(0, 0)),
                        1,
                        List.of(
                                new VectorExportSnapshot.Primitive(
                                        0,
                                        0,
                                        new PointGeometry(new Coordinate(20, 24)),
                                        BuiltInMarkers.filledScreen(
                                                BuiltInMarker.TRIANGLE,
                                                Rgba.rgb(30, 110, 210),
                                                12,
                                                1))),
                        List.of());
        byte[] encoded = SvgMapExports.encode(snapshot);
        String document = new String(encoded, StandardCharsets.UTF_8);
        if (!document.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                || !document.contains("viewBox=\"0 0 64 48\"")
                || !document.contains("fill=\"#1e6ed2\"")
                || !document.endsWith("</svg>\n")) {
            throw new IllegalStateException("svg-native: vector-map export structure changed");
        }

        Path target = null;
        try {
            target = Files.createTempFile("mundane-map-native-svg-", ".svg");
            SvgMapExports.writeAtomically(target, snapshot);
            if (!java.util.Arrays.equals(encoded, Files.readAllBytes(target))) {
                throw new IllegalStateException("svg-native: atomic export bytes changed");
            }
            Files.delete(target);
        } catch (RuntimeException exception) {
            deleteAfterFailure(target, exception);
            throw exception;
        } catch (java.io.IOException exception) {
            deleteAfterFailure(target, exception);
            throw new IllegalStateException("svg-native: vector-map file export failed", exception);
        }

        RasterIconSymbol unsupported =
                RasterIconSymbol.nativeScreenSize(
                        1, 1, new int[] {0xffffffff}, RasterInterpolation.NEAREST, 1);
        try {
            VectorExportSnapshot.of(
                    8,
                    8,
                    Rgba.TRANSPARENT,
                    new VectorExportSnapshot.ViewFrame(1, 0, new Coordinate(0, 0)),
                    1,
                    List.of(
                            new VectorExportSnapshot.Primitive(
                                    0, 0, new PointGeometry(new Coordinate(1, 1)), unsupported)),
                    List.of());
            throw new IllegalStateException("svg-native: unsupported raster icon was accepted");
        } catch (VectorExportSnapshotException expected) {
            if (!expected.problem().code().equals("VECTOR_EXPORT_SYMBOL_UNSUPPORTED")
                    || !expected.problem().context().get("kind").equals("rasterIcon")) {
                throw new IllegalStateException(
                        "svg-native: unsupported-symbol diagnostic changed", expected);
            }
        }
    }

    private static void deleteAfterFailure(Path target, Exception failure) {
        if (target != null) {
            try {
                Files.deleteIfExists(target);
            } catch (java.io.IOException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }
        }
    }

    private static void assertDtdRejected() {
        try {
            SvgSymbols.parse(
                    ID,
                    "<!DOCTYPE svg><svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>"
                            .getBytes(StandardCharsets.UTF_8),
                    MarkerPlacement.centeredScreen(16));
            throw new IllegalStateException("svg-native: DTD was accepted");
        } catch (SourceException expected) {
            if (!expected.terminal().code().equals("SVG_XML_INVALID")
                    || !expected.terminal().context().equals(Map.of("reason", "doctype"))) {
                throw new IllegalStateException("svg-native: DTD diagnostic changed", expected);
            }
        }
    }

    private static void render(Symbol symbol) {
        MapView view = new MapView(new WebMercatorProjection(), SymbolRendererRegistry.builtIn());
        view.setDoubleBuffered(false);
        view.setSize(96, 96);
        view.setLayers(
                List.of(
                        new InMemoryLayer(
                                "svg",
                                "SVG",
                                List.of(
                                        new Feature(
                                                "svg",
                                                "",
                                                new PointGeometry(new Coordinate(0, 0)),
                                                Map.of(),
                                                symbol)))));
        view.fitToData(24);
        BufferedImage image = new BufferedImage(96, 96, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 96, 96);
            view.paint(graphics);
        } finally {
            graphics.dispose();
            view.close();
        }
        int colored = 0;
        for (int y = 28; y < 68; y++) {
            for (int x = 28; x < 68; x++) {
                int rgb = image.getRGB(x, y);
                int red = (rgb >>> 16) & 255;
                int green = (rgb >>> 8) & 255;
                int blue = rgb & 255;
                if (green > red + 30 && green > blue + 20) {
                    colored++;
                }
            }
        }
        if (colored < 80) {
            throw new IllegalStateException("svg-native: imported marker did not render");
        }
    }

    private static void renderOnEdt(Symbol symbol) {
        if (SwingUtilities.isEventDispatchThread()) {
            render(symbol);
            return;
        }
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            SwingUtilities.invokeAndWait(
                    () -> {
                        try {
                            render(symbol);
                        } catch (Throwable throwable) {
                            failure.set(throwable);
                        }
                    });
        } catch (Exception exception) {
            throw new IllegalStateException("svg-native: unable to render on EDT", exception);
        }
        if (failure.get() != null) {
            throw new IllegalStateException("svg-native: rendering failed", failure.get());
        }
    }
}
