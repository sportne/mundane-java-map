package io.github.mundanej.map.example.symbols;

import io.github.mundanej.map.api.BuiltInMarker;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CoordinateSequence;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Feature;
import io.github.mundanej.map.api.HatchFillSymbol;
import io.github.mundanej.map.api.HatchPattern;
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
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolAnchor;
import io.github.mundanej.map.api.SymbolLength;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.SymbolSize;
import io.github.mundanej.map.api.SymbolStroke;
import io.github.mundanej.map.api.SymbolUnit;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPath;
import io.github.mundanej.map.core.BuiltInMarkers;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Immutable catalog and four-section symbol-gallery document. */
record GalleryDocument(NamedSymbolCatalog catalog, List<GallerySection> sections) {
    private static final Rgba BLUE = Rgba.rgb(35, 105, 205);
    private static final Rgba RED = Rgba.rgb(195, 45, 45);
    private static final Rgba GREEN = Rgba.rgb(45, 145, 80);
    private static final Rgba YELLOW = Rgba.rgb(235, 175, 35);
    private static final Rgba DARK = Rgba.rgb(45, 50, 60);
    private static final Rgba REFERENCE = new Rgba(95, 100, 110, 140);
    private static final double ARROW_LINE_WIDTH = 5.4;
    private static final double ARROW_HEAD_WIDTH = 12;
    private static final double ARROW_HEAD_HEIGHT = 14;
    private static final Envelope ARROW_HEAD_VIEW_BOX = new Envelope(-0.5, -0.5, 0.5, 0.5);
    private static final VectorPath ARROW_HEAD_PATH =
            VectorPath.builder()
                    .moveTo(-0.5, -0.5)
                    .lineTo(0.5, 0)
                    .lineTo(-0.5, 0.5)
                    .close()
                    .build();

    GalleryDocument {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(sections, "sections");
        sections = List.copyOf(sections);
        Set<String> sectionIdentifiers = new HashSet<>();
        Set<String> caseIdentifiers = new HashSet<>();
        for (GallerySection section : sections) {
            if (!sectionIdentifiers.add(section.id())) {
                throw new IllegalArgumentException("duplicate gallery section id: " + section.id());
            }
            for (GalleryCase galleryCase : section.cases()) {
                if (!caseIdentifiers.add(galleryCase.id())) {
                    throw new IllegalArgumentException(
                            "duplicate gallery case id: " + galleryCase.id());
                }
            }
        }
    }

    static GalleryDocument create() {
        ArrayList<NamedSymbol> symbols = new ArrayList<>();
        ArrayList<GalleryCase> markerCases = new ArrayList<>();
        int markerIndex = 0;
        for (BuiltInMarker marker : BuiltInMarker.values()) {
            Symbol symbol = BuiltInMarkers.filledScreen(marker, BLUE, 26, 1);
            String id =
                    "marker-" + marker.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            symbols.add(new NamedSymbol(id, symbol));
            markerCases.add(
                    pointCase(
                            id,
                            marker.name(),
                            markerIndex++,
                            symbol,
                            new GalleryCoverage(
                                    Set.of(marker),
                                    Set.of(),
                                    Set.of(),
                                    Set.of(),
                                    Set.of(),
                                    Set.of())));
        }

        RasterIconSymbol nearest = raster(RasterInterpolation.NEAREST);
        RasterIconSymbol bilinear = raster(RasterInterpolation.BILINEAR);
        symbols.add(new NamedSymbol("raster-nearest", nearest));
        symbols.add(new NamedSymbol("raster-bilinear", bilinear));
        markerCases.add(
                pointCase(
                        "raster-nearest",
                        "Raster icon — nearest",
                        markerIndex++,
                        nearest,
                        coverage(RasterInterpolation.NEAREST)));
        markerCases.add(
                pointCase(
                        "raster-bilinear",
                        "Raster icon — bilinear",
                        markerIndex++,
                        bilinear,
                        coverage(RasterInterpolation.BILINEAR)));

        Symbol opacity =
                CompositeSymbol.of(
                        List.of(
                                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, YELLOW, 30, 1),
                                BuiltInMarkers.filledScreen(
                                        BuiltInMarker.CIRCLE,
                                        new Rgba(195, 45, 45, 170),
                                        24,
                                        0.55)),
                        1);
        symbols.add(new NamedSymbol("marker-opacity", opacity));
        markerCases.add(
                pointCase(
                        "marker-opacity",
                        "Opacity over reference",
                        markerIndex++,
                        opacity,
                        GalleryCoverage.none()));

        Symbol composite =
                CompositeSymbol.of(
                        List.of(
                                BuiltInMarkers.filledScreen(BuiltInMarker.SQUARE, BLUE, 32, 1),
                                BuiltInMarkers.filledScreen(BuiltInMarker.DIAMOND, YELLOW, 18, 1)),
                        1);
        symbols.add(new NamedSymbol("marker-composite", composite));
        markerCases.add(
                pointCase(
                        "marker-composite",
                        "Ordered composite",
                        markerIndex,
                        composite,
                        GalleryCoverage.none()));

        ArrayList<GalleryCase> placementCases = new ArrayList<>();
        int placementIndex = 0;
        for (SymbolAnchor anchor : SymbolAnchor.values()) {
            MarkerPlacement placement =
                    new MarkerPlacement(
                            new SymbolSize(24, 16, SymbolUnit.SCREEN_PIXEL),
                            anchor,
                            0,
                            0,
                            0,
                            SymbolRotationMode.SCREEN_RELATIVE);
            Symbol placed = withReference(vectorSquare(placement, BLUE, 1));
            String id =
                    "anchor-" + anchor.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
            symbols.add(new NamedSymbol(id, placed));
            placementCases.add(
                    pointCase(
                            id,
                            "Anchor " + anchor,
                            placementIndex++,
                            placed,
                            new GalleryCoverage(
                                    Set.of(),
                                    Set.of(anchor),
                                    Set.of(),
                                    Set.of(),
                                    Set.of(),
                                    Set.of())));
        }
        placementIndex =
                addPlacementCase(
                        symbols,
                        placementCases,
                        "offset-positive",
                        "Offset +x/+y",
                        placementIndex,
                        new MarkerPlacement(
                                new SymbolSize(22, 14, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                13,
                                9,
                                0,
                                SymbolRotationMode.SCREEN_RELATIVE));
        placementIndex =
                addPlacementCase(
                        symbols,
                        placementCases,
                        "offset-negative",
                        "Offset -x/-y",
                        placementIndex,
                        new MarkerPlacement(
                                new SymbolSize(22, 14, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                -13,
                                -9,
                                0,
                                SymbolRotationMode.SCREEN_RELATIVE));
        placementIndex =
                addPlacementCase(
                        symbols,
                        placementCases,
                        "screen-pixel-size",
                        "Screen-pixel size",
                        placementIndex,
                        new MarkerPlacement(
                                new SymbolSize(26, 14, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                0,
                                0,
                                25,
                                SymbolRotationMode.SCREEN_RELATIVE));
        placementIndex =
                addPlacementCase(
                        symbols,
                        placementCases,
                        "map-unit-size",
                        "Map-unit size",
                        placementIndex,
                        new MarkerPlacement(
                                new SymbolSize(12_000, 7_000, SymbolUnit.MAP_UNIT),
                                SymbolAnchor.CENTER,
                                0,
                                0,
                                25,
                                SymbolRotationMode.MAP_RELATIVE));
        placementIndex =
                addPlacementCase(
                        symbols,
                        placementCases,
                        "screen-relative",
                        "Screen-relative rotation",
                        placementIndex,
                        new MarkerPlacement(
                                new SymbolSize(26, 14, SymbolUnit.SCREEN_PIXEL),
                                SymbolAnchor.CENTER,
                                0,
                                0,
                                55,
                                SymbolRotationMode.SCREEN_RELATIVE));
        addPlacementCase(
                symbols,
                placementCases,
                "map-relative",
                "Map-relative rotation",
                placementIndex,
                new MarkerPlacement(
                        new SymbolSize(26, 14, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.CENTER,
                        0,
                        0,
                        55,
                        SymbolRotationMode.MAP_RELATIVE));

        Symbol plainLine = SolidLineSymbol.of(stroke(BLUE, 4), 1);
        Symbol casedLine =
                CompositeSymbol.of(
                        List.of(
                                SolidLineSymbol.of(stroke(DARK, 9), 1),
                                SolidLineSymbol.of(stroke(YELLOW, 4), 1)),
                        1);
        Symbol distinctEndpoints =
                SolidLineSymbol.of(
                        stroke(GREEN, 4),
                        Optional.of(BuiltInMarkers.filledScreen(BuiltInMarker.CIRCLE, BLUE, 15, 1)),
                        Optional.of(
                                BuiltInMarkers.filledScreen(BuiltInMarker.TRIANGLE, RED, 17, 1)),
                        1);
        Symbol arrowLine =
                SolidLineSymbol.of(
                        stroke(RED, ARROW_LINE_WIDTH),
                        Optional.empty(),
                        Optional.of(endpointArrow()),
                        1);
        symbols.add(new NamedSymbol("line-plain", plainLine));
        symbols.add(new NamedSymbol("line-cased", casedLine));
        symbols.add(new NamedSymbol("line-endpoints", distinctEndpoints));
        symbols.add(new NamedSymbol("arrow-horizontal", arrowLine));
        symbols.add(new NamedSymbol("arrow-rising", arrowLine));
        symbols.add(new NamedSymbol("arrow-falling", arrowLine));
        List<GalleryCase> lineCases =
                List.of(
                        lineCase("line-plain", "Plain line", 0, plainLine, -0.28, 0.0, 0.28, 0.0),
                        lineCase(
                                "line-cased",
                                "Cased composite",
                                1,
                                casedLine,
                                -0.28,
                                0.0,
                                0.28,
                                0.0),
                        lineCase(
                                "line-endpoints",
                                "Distinct endpoints",
                                2,
                                distinctEndpoints,
                                -0.28,
                                0.0,
                                0.28,
                                0.0),
                        lineCase(
                                "arrow-horizontal",
                                "Arrow — horizontal",
                                3,
                                arrowLine,
                                -0.28,
                                0.0,
                                0.28,
                                0.0),
                        lineCase(
                                "arrow-rising",
                                "Arrow — rising",
                                4,
                                arrowLine,
                                -0.28,
                                -0.12,
                                0.28,
                                0.12),
                        lineCase(
                                "arrow-falling",
                                "Arrow — falling",
                                5,
                                arrowLine,
                                -0.28,
                                0.12,
                                0.28,
                                -0.12));

        Symbol outline = SolidLineSymbol.of(stroke(DARK, 3), 1);
        Symbol solidFill = SolidFillSymbol.of(new Rgba(35, 105, 205, 150), Optional.of(outline), 1);
        Symbol forward = hatch(HatchPattern.FORWARD_DIAGONAL, Optional.empty());
        Symbol backward = hatch(HatchPattern.BACKWARD_DIAGONAL, Optional.empty());
        Symbol cross = hatch(HatchPattern.CROSS_DIAGONAL, Optional.empty());
        Symbol mapRelativeCross =
                hatch(
                        HatchPattern.CROSS_DIAGONAL,
                        Optional.empty(),
                        SymbolRotationMode.MAP_RELATIVE);
        Symbol solidHatch =
                CompositeSymbol.of(
                        List.of(
                                SolidFillSymbol.of(new Rgba(235, 175, 35, 100), 1),
                                mapRelativeCross),
                        1);
        Symbol outlinedHatch = hatch(HatchPattern.FORWARD_DIAGONAL, Optional.of(outline));
        Symbol holeReference = SolidFillSymbol.of(new Rgba(35, 105, 205, 190), 1);
        Symbol holeFill = SolidFillSymbol.of(new Rgba(195, 45, 45, 150), Optional.of(outline), 1);
        symbols.add(new NamedSymbol("fill-solid", solidFill));
        symbols.add(new NamedSymbol("fill-forward", forward));
        symbols.add(new NamedSymbol("fill-backward", backward));
        symbols.add(new NamedSymbol("fill-cross", cross));
        symbols.add(new NamedSymbol("fill-solid-hatch", solidHatch));
        symbols.add(new NamedSymbol("fill-outline", outlinedHatch));
        symbols.add(new NamedSymbol("fill-hole-reference", holeReference));
        symbols.add(new NamedSymbol("fill-hole", holeFill));
        List<GalleryCase> fillCases =
                List.of(
                        polygonCase(
                                "fill-solid",
                                "Solid fill",
                                0,
                                solidFill,
                                false,
                                GalleryCoverage.none()),
                        polygonCase(
                                "fill-forward",
                                "Forward hatch",
                                1,
                                forward,
                                false,
                                coverage(HatchPattern.FORWARD_DIAGONAL)),
                        polygonCase(
                                "fill-backward",
                                "Backward hatch",
                                2,
                                backward,
                                false,
                                coverage(HatchPattern.BACKWARD_DIAGONAL)),
                        polygonCase(
                                "fill-cross",
                                "Cross hatch — screen-relative",
                                3,
                                cross,
                                false,
                                coverage(HatchPattern.CROSS_DIAGONAL)),
                        polygonCase(
                                "fill-solid-hatch",
                                "Solid plus cross — map-relative",
                                4,
                                solidHatch,
                                false,
                                GalleryCoverage.none()),
                        polygonCase(
                                "fill-outline",
                                "Line-symbol outline",
                                5,
                                outlinedHatch,
                                false,
                                GalleryCoverage.none()),
                        polygonCase(
                                "fill-hole",
                                "Polygon hole",
                                6,
                                holeReference,
                                holeFill,
                                GalleryCoverage.none()));

        NamedSymbolCatalog catalog = NamedSymbolCatalog.of(symbols);
        List<GallerySection> sections =
                List.of(
                        new GallerySection("markers", "Markers", resolve(markerCases, catalog)),
                        new GallerySection(
                                "placement", "Placement", resolve(placementCases, catalog)),
                        new GallerySection("lines", "Lines", resolve(lineCases, catalog)),
                        new GallerySection("fills", "Fills", resolve(fillCases, catalog)));
        return new GalleryDocument(catalog, sections);
    }

    private static List<GalleryCase> resolve(List<GalleryCase> cases, NamedSymbolCatalog catalog) {
        return cases.stream()
                .map(
                        galleryCase ->
                                new GalleryCase(
                                        galleryCase.id(),
                                        galleryCase.title(),
                                        galleryCase.features().stream()
                                                .map(
                                                        feature ->
                                                                new Feature(
                                                                        feature.id(),
                                                                        "",
                                                                        feature.geometry(),
                                                                        feature.attributes(),
                                                                        catalog.require(
                                                                                feature.attributes()
                                                                                        .get(
                                                                                                "symbol")
                                                                                        .toString())))
                                                .toList(),
                                        galleryCase.coverage()))
                .toList();
    }

    private static GalleryCase pointCase(
            String id, String title, int index, Symbol symbol, GalleryCoverage coverage) {
        Coordinate coordinate = grid(index);
        return new GalleryCase(
                id, title, List.of(feature(id, new PointGeometry(coordinate), symbol)), coverage);
    }

    private static GalleryCase lineCase(
            String id,
            String title,
            int index,
            Symbol symbol,
            double x1,
            double y1,
            double x2,
            double y2) {
        Coordinate center = grid(index);
        return new GalleryCase(
                id,
                title,
                List.of(
                        feature(
                                id,
                                new LineStringGeometry(
                                        CoordinateSequence.of(
                                                center.x() + x1,
                                                center.y() + y1,
                                                center.x() + x2,
                                                center.y() + y2)),
                                symbol)),
                GalleryCoverage.none());
    }

    private static GalleryCase polygonCase(
            String id,
            String title,
            int index,
            Symbol symbol,
            boolean hole,
            GalleryCoverage coverage) {
        Coordinate center = grid(index);
        CoordinateSequence exterior = ring(center, 0.27);
        PolygonGeometry polygon =
                hole
                        ? new PolygonGeometry(exterior, List.of(ring(center, 0.11)))
                        : new PolygonGeometry(exterior);
        return new GalleryCase(id, title, List.of(feature(id, polygon, symbol)), coverage);
    }

    private static GalleryCase polygonCase(
            String id,
            String title,
            int index,
            Symbol reference,
            Symbol symbol,
            GalleryCoverage coverage) {
        Coordinate center = grid(index);
        CoordinateSequence exterior = ring(center, 0.27);
        PolygonGeometry background = new PolygonGeometry(exterior);
        PolygonGeometry polygon = new PolygonGeometry(exterior, List.of(ring(center, 0.11)));
        return new GalleryCase(
                id,
                title,
                List.of(
                        feature(id + "-reference", background, reference),
                        feature(id, polygon, symbol)),
                coverage);
    }

    private static Feature feature(
            String id, io.github.mundanej.map.api.Geometry geometry, Symbol symbol) {
        return new Feature(id, "", geometry, Map.of("symbol", id), symbol);
    }

    private static Coordinate grid(int index) {
        return new Coordinate((index % 4) * 0.85 - 1.25, 0.85 - Math.floorDiv(index, 4) * 0.85);
    }

    private static CoordinateSequence ring(Coordinate center, double radius) {
        return CoordinateSequence.of(
                center.x() - radius,
                center.y() - radius,
                center.x() + radius,
                center.y() - radius,
                center.x() + radius,
                center.y() + radius,
                center.x() - radius,
                center.y() + radius,
                center.x() - radius,
                center.y() - radius);
    }

    private static int addPlacementCase(
            List<NamedSymbol> symbols,
            List<GalleryCase> cases,
            String id,
            String title,
            int index,
            MarkerPlacement placement) {
        Symbol symbol = withReference(vectorSquare(placement, BLUE, 1));
        symbols.add(new NamedSymbol(id, symbol));
        GalleryCoverage coverage =
                new GalleryCoverage(
                        Set.of(),
                        Set.of(placement.anchor()),
                        Set.of(placement.size().unit()),
                        Set.of(placement.rotationMode()),
                        Set.of(),
                        Set.of());
        cases.add(pointCase(id, title, index, symbol, coverage));
        return index + 1;
    }

    private static VectorMarkerSymbol vectorSquare(
            MarkerPlacement placement, Rgba color, double opacity) {
        return VectorMarkerSymbol.of(
                BuiltInMarkers.path(BuiltInMarker.SQUARE),
                BuiltInMarkers.viewBox(),
                color,
                Optional.of(stroke(DARK, 1.5)),
                placement,
                opacity);
    }

    private static Symbol withReference(Symbol symbol) {
        VectorMarkerSymbol reference =
                BuiltInMarkers.filledScreen(BuiltInMarker.CROSS, REFERENCE, 34, 1);
        return CompositeSymbol.of(List.of(reference, symbol), 1);
    }

    private static RasterIconSymbol raster(RasterInterpolation interpolation) {
        int[] pixels = {
            0xff0000ff, 0x00ff00ff, 0x0000ffff, 0xffffff00,
            0xffff00ff, 0x00ffffff, 0xff00ff80, 0x000000ff
        };
        return RasterIconSymbol.screenWidth(4, 2, pixels, 36, interpolation, 1);
    }

    private static VectorMarkerSymbol endpointArrow() {
        MarkerPlacement placement =
                new MarkerPlacement(
                        new SymbolSize(
                                ARROW_HEAD_WIDTH, ARROW_HEAD_HEIGHT, SymbolUnit.SCREEN_PIXEL),
                        SymbolAnchor.WEST,
                        0,
                        0,
                        0,
                        SymbolRotationMode.SCREEN_RELATIVE);
        return VectorMarkerSymbol.of(
                ARROW_HEAD_PATH, ARROW_HEAD_VIEW_BOX, RED, Optional.empty(), placement, 1);
    }

    private static HatchFillSymbol hatch(HatchPattern pattern, Optional<Symbol> outline) {
        return hatch(pattern, outline, SymbolRotationMode.SCREEN_RELATIVE);
    }

    private static HatchFillSymbol hatch(
            HatchPattern pattern, Optional<Symbol> outline, SymbolRotationMode rotationMode) {
        return HatchFillSymbol.of(
                pattern,
                stroke(GREEN, 2),
                new SymbolLength(9, SymbolUnit.SCREEN_PIXEL),
                rotationMode,
                outline,
                1,
                HatchFillSymbol.DEFAULT_MAX_SEGMENTS);
    }

    private static SymbolStroke stroke(Rgba color, double width) {
        return new SymbolStroke(color, new SymbolLength(width, SymbolUnit.SCREEN_PIXEL));
    }

    private static GalleryCoverage coverage(RasterInterpolation interpolation) {
        return new GalleryCoverage(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(interpolation), Set.of());
    }

    private static GalleryCoverage coverage(HatchPattern pattern) {
        return new GalleryCoverage(
                Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(pattern));
    }
}
