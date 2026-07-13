package io.github.mundanej.map.example.symbols;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.SolidLineSymbol;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.awt.SymbolRendererRegistry;
import io.github.mundanej.map.core.InMemoryLayer;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.core.WebMercatorProjection;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SymbolGalleryTest {
    @Test
    void documentHasExactSectionsCasesAndCompleteDeclaredCoverage() {
        GalleryDocument document = GalleryDocument.create();
        assertEquals(List.of("markers", "placement", "lines", "fills"), ids(document.sections()));
        assertEquals(
                List.of(
                        "marker-circle",
                        "marker-square",
                        "marker-triangle",
                        "marker-diamond",
                        "marker-cross",
                        "marker-x",
                        "marker-star",
                        "marker-arrow",
                        "raster-nearest",
                        "raster-bilinear",
                        "marker-opacity",
                        "marker-composite"),
                caseIds(document.sections().get(0)));
        assertEquals(
                List.of(
                        "anchor-center",
                        "anchor-north",
                        "anchor-north-east",
                        "anchor-east",
                        "anchor-south-east",
                        "anchor-south",
                        "anchor-south-west",
                        "anchor-west",
                        "anchor-north-west",
                        "offset-positive",
                        "offset-negative",
                        "screen-pixel-size",
                        "map-unit-size",
                        "screen-relative",
                        "map-relative"),
                caseIds(document.sections().get(1)));
        assertEquals(
                List.of(
                        "line-plain",
                        "line-cased",
                        "line-endpoints",
                        "arrow-horizontal",
                        "arrow-rising",
                        "arrow-falling"),
                caseIds(document.sections().get(2)));
        assertEquals(
                List.of(
                        "fill-solid",
                        "fill-forward",
                        "fill-backward",
                        "fill-cross",
                        "fill-solid-hatch",
                        "fill-outline",
                        "fill-hole"),
                caseIds(document.sections().get(3)));
        GalleryCase fillHole = document.sections().get(3).cases().get(6);
        assertEquals(
                List.of("fill-hole-reference", "fill-hole"),
                fillHole.features().stream().map(feature -> feature.id()).toList());

        List<GalleryCoverage> coverage =
                document.sections().stream()
                        .flatMap(section -> section.cases().stream())
                        .map(GalleryCase::coverage)
                        .toList();
        assertEquals(
                EnumSet.allOf(BuiltInMarker.class),
                union(coverage, GalleryCoverage::markers, BuiltInMarker.class));
        assertEquals(
                EnumSet.allOf(SymbolAnchor.class),
                union(coverage, GalleryCoverage::anchors, SymbolAnchor.class));
        assertEquals(
                EnumSet.allOf(SymbolUnit.class),
                union(coverage, GalleryCoverage::units, SymbolUnit.class));
        assertEquals(
                EnumSet.allOf(SymbolRotationMode.class),
                union(coverage, GalleryCoverage::rotationModes, SymbolRotationMode.class));
        assertEquals(
                EnumSet.allOf(RasterInterpolation.class),
                union(coverage, GalleryCoverage::interpolations, RasterInterpolation.class));
        assertEquals(
                EnumSet.allOf(HatchPattern.class),
                union(coverage, GalleryCoverage::hatchPatterns, HatchPattern.class));

        document.sections().stream()
                .flatMap(section -> section.cases().stream())
                .forEach(
                        galleryCase -> {
                            assertFalse(galleryCase.features().isEmpty(), galleryCase.id());
                            galleryCase
                                    .features()
                                    .forEach(
                                            feature -> {
                                                assertTrue(feature.name().isEmpty(), feature.id());
                                                assertEquals(
                                                        feature.symbol(),
                                                        document.catalog()
                                                                .require(
                                                                        feature.attributes()
                                                                                .get("symbol")
                                                                                .toString()));
                                            });
                        });
    }

    @Test
    void inventoryIsImmutableAndRejectsDuplicateIdentifiers() {
        GalleryDocument document = GalleryDocument.create();
        assertThrows(
                UnsupportedOperationException.class,
                () -> document.sections().add(document.sections().get(0)));
        GallerySection section = document.sections().get(0);
        assertThrows(
                UnsupportedOperationException.class,
                () -> section.cases().add(section.cases().get(0)));
        GalleryCase galleryCase = section.cases().get(0);
        assertThrows(
                UnsupportedOperationException.class,
                () -> galleryCase.features().add(galleryCase.features().get(0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GallerySection(
                                "duplicates", "Duplicates", List.of(galleryCase, galleryCase)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GalleryDocument(
                                document.catalog(),
                                List.of(document.sections().get(0), document.sections().get(0))));
        GallerySection duplicateCaseAcrossSection =
                new GallerySection("other", "Other", List.of(galleryCase));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GalleryDocument(
                                document.catalog(),
                                List.of(document.sections().get(0), duplicateCaseAcrossSection)));
    }

    @Test
    void mapUnitMarkerIsVisibleAtProjectedScaleAndHatchesExposeBothPhaseModes() throws Exception {
        GalleryDocument document = GalleryDocument.create();
        GalleryCase mapUnit =
                document.sections().get(1).cases().stream()
                        .filter(galleryCase -> galleryCase.id().equals("map-unit-size"))
                        .findFirst()
                        .orElseThrow();
        Feature feature = mapUnit.features().get(0);
        CompositeSymbol placed = (CompositeSymbol) feature.symbol();
        VectorMarkerSymbol marker = (VectorMarkerSymbol) placed.children().get(1);
        assertEquals(SymbolUnit.MAP_UNIT, marker.placement().size().unit());
        assertTrue(marker.placement().size().width() >= 10_000);

        AtomicReference<BufferedImage> rendered = new AtomicReference<>();
        SwingUtilities.invokeAndWait(
                () -> {
                    WebMercatorProjection projection = new WebMercatorProjection();
                    Coordinate coordinate = ((PointGeometry) feature.geometry()).coordinate();
                    Coordinate projected = projection.project(coordinate);
                    MapView view =
                            new MapView(
                                    projection,
                                    SymbolRendererRegistry.builderWithBuiltIns().build());
                    view.setOpaque(true);
                    view.setBackground(Color.WHITE);
                    view.setSize(200, 160);
                    view.setViewport(new MapViewport(200, 160, projected.x(), projected.y(), 500));
                    view.setLayers(
                            List.of(new InMemoryLayer("map-unit", "Map unit", mapUnit.features())));
                    BufferedImage image = new BufferedImage(200, 160, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D graphics = image.createGraphics();
                    try {
                        graphics.setColor(Color.WHITE);
                        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                        view.paint(graphics);
                    } finally {
                        graphics.dispose();
                    }
                    rendered.set(image);
                });
        assertTrue(countBlue(rendered.get()) >= 100, "map-unit marker must remain visibly painted");

        HatchFillSymbol screenRelative = (HatchFillSymbol) document.catalog().require("fill-cross");
        CompositeSymbol mapRelativeComposite =
                (CompositeSymbol) document.catalog().require("fill-solid-hatch");
        Symbol mapRelative = mapRelativeComposite.children().get(1);
        assertEquals(SymbolRotationMode.SCREEN_RELATIVE, screenRelative.rotationMode());
        assertTrue(mapRelative instanceof HatchFillSymbol);
        assertEquals(
                SymbolRotationMode.MAP_RELATIVE, ((HatchFillSymbol) mapRelative).rotationMode());
    }

    @Test
    void arrowLineUsesAHeadOnlyEndpointAnchoredAtTheLineEnd() {
        GalleryDocument document = GalleryDocument.create();
        SolidLineSymbol arrowLine =
                (SolidLineSymbol) document.catalog().require("arrow-horizontal");
        VectorMarkerSymbol arrow = (VectorMarkerSymbol) arrowLine.endMarker().orElseThrow();

        assertEquals(5.4, arrowLine.stroke().width().value(), 0.0);
        assertEquals(SymbolUnit.SCREEN_PIXEL, arrowLine.stroke().width().unit());
        assertEquals(SymbolAnchor.WEST, arrow.placement().anchor());
        assertEquals(4, arrow.path().commandCount());
        assertEquals(12, arrow.placement().size().width(), 0.0);
        assertEquals(14, arrow.placement().size().height(), 0.0);
    }

    @Test
    void panelConstructsAndPaintsHeadlesslyOnTheEdtWithFourExplicitViews() throws Exception {
        assertThrows(IllegalStateException.class, SymbolGallery::createGalleryPanel);
        AtomicReference<JPanel> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(SymbolGallery.createGalleryPanel()));
        JPanel panel = result.get();
        List<MapView> views = descendants(panel, MapView.class);
        assertEquals(4, views.size());
        assertEquals(
                List.of(
                        "gallery-map-markers",
                        "gallery-map-placement",
                        "gallery-map-lines",
                        "gallery-map-fills"),
                views.stream().map(Component::getName).toList());
        SwingUtilities.invokeAndWait(
                () -> {
                    for (MapView view : views) {
                        assertEquals(1, view.layers().size());
                        assertFalse(view.layers().get(0).features().isEmpty());
                        BufferedImage image =
                                new BufferedImage(760, 520, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D graphics = image.createGraphics();
                        try {
                            view.paint(graphics);
                        } finally {
                            graphics.dispose();
                        }
                    }
                });
    }

    private static List<String> ids(List<GallerySection> sections) {
        return sections.stream().map(GallerySection::id).toList();
    }

    private static List<String> caseIds(GallerySection section) {
        return section.cases().stream().map(GalleryCase::id).toList();
    }

    private static <E extends Enum<E>> EnumSet<E> union(
            List<GalleryCoverage> coverage,
            java.util.function.Function<GalleryCoverage, Set<E>> selector,
            Class<E> type) {
        EnumSet<E> values = EnumSet.noneOf(type);
        coverage.stream().map(selector).forEach(values::addAll);
        return values;
    }

    private static int countBlue(BufferedImage image) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(image.getRGB(x, y), true);
                if (Math.abs(color.getRed() - 35) <= 18
                        && Math.abs(color.getGreen() - 105) <= 18
                        && Math.abs(color.getBlue() - 205) <= 18) {
                    count++;
                }
            }
        }
        return count;
    }

    private static <T extends Component> List<T> descendants(Component root, Class<T> type) {
        ArrayList<T> result = new ArrayList<>();
        collect(root, type, result);
        return List.copyOf(result);
    }

    private static <T extends Component> void collect(
            Component component, Class<T> type, List<T> result) {
        if (type.isInstance(component)) {
            result.add(type.cast(component));
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                collect(child, type, result);
            }
        }
    }
}
