package io.github.mundanej.map.awt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.EncodedRasterDecodeContext;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterInterpolation;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.MapViewport;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapViewAffineImageTest {
    @TempDir Path directory;

    @Test
    void distinctPngCellsProveAffineRowColumnAlphaAndPaintBounds() throws Exception {
        Path imagePath =
                copyPngFixture(
                        "distinct-affine",
                        "affine-rgba.pgw",
                        15,
                        "c79fc38d5bf1062a272505e645fb2cba5c8a59ddefbc38ede7c4e1fb9b81e2d8");

        RasterSource source = open(imagePath, ImagePlacement.worldFile(projectedCrs()));
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    view.setSize(200, 200);
                    view.setViewport(new MapViewport(200, 200, 7, -3.5, 0.2));
                    view.setLayerBindings(
                            List.of(MapLayerBinding.ownedRaster("affine", "affine", source)));

                    BufferedImage painted = paint(view, 200);
                    var transform =
                            source.metadata()
                                    .gridPlacement()
                                    .orElseThrow()
                                    .affineTransform()
                                    .orElseThrow();
                    assertRegion(view, painted, transform.gridToMap(0, 0), Color.RED, 1);
                    assertRegion(
                            view, painted, transform.gridToMap(1, 0), new Color(127, 255, 127), 2);
                    assertRegion(view, painted, transform.gridToMap(0, 1), Color.BLUE, 1);
                    assertRegion(view, painted, transform.gridToMap(1, 1), Color.WHITE, 1);

                    Coordinate envelopeOnly = view.viewport().worldToScreen(new Coordinate(-6, 8));
                    assertColorNear(
                            Color.WHITE,
                            new Color(
                                    painted.getRGB((int) envelopeOnly.x(), (int) envelopeOnly.y())),
                            1);
                    assertPaintContainedAndBounded(painted, 29, 34, 171, 167, 7_500, 10_000);
                    view.close();
                });
        assertTrue(source.isClosed());
    }

    @Test
    void northUpPngFixtureRetainsHalfPixelBoundsAndOrientation() throws Exception {
        Path imagePath =
                copyPngFixture(
                        "north-up",
                        "north-up-rgba.pgw",
                        19,
                        "aee88312dc748b7ebb8265649d86020948a80a1d00199ae38c6b8ad2d8d85ac9");
        RasterSource source = open(imagePath, ImagePlacement.worldFile(projectedCrs()));
        assertEquals(new Envelope(95, 185, 115, 205), source.metadata().mapBounds().orElseThrow());
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    view.setSize(160, 160);
                    view.setViewport(new MapViewport(160, 160, 105, 195, 0.2));
                    view.setLayerBindings(
                            List.of(MapLayerBinding.ownedRaster("north", "north", source)));
                    BufferedImage painted = paint(view, 160);
                    var transform =
                            source.metadata()
                                    .gridPlacement()
                                    .orElseThrow()
                                    .affineTransform()
                                    .orElseThrow();
                    assertRegion(view, painted, transform.gridToMap(0, 0), Color.RED, 1);
                    assertRegion(
                            view, painted, transform.gridToMap(1, 0), new Color(127, 255, 127), 2);
                    assertRegion(view, painted, transform.gridToMap(0, 1), Color.BLUE, 1);
                    assertRegion(view, painted, transform.gridToMap(1, 1), Color.WHITE, 1);
                    view.close();
                });
        assertTrue(source.isClosed());
    }

    @Test
    void projectedNonSolidJpegProvesShearedOrientationWithTolerantRegions() throws Exception {
        Path imagePath =
                copyJpegFixture(
                        "projected-regions",
                        "projected-regions.jgw",
                        21,
                        "ce0a9158c4e080cd655ff57d7a4e0bb19888cd77651f640321cbd00d6f4be064");
        RasterSource source = open(imagePath, ImagePlacement.worldFile(projectedCrs()));
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    view.setSize(160, 160);
                    view.setViewport(new MapViewport(160, 160, 0, 0, 0.4));
                    view.setLayerBindings(
                            List.of(MapLayerBinding.ownedRaster("jpeg", "jpeg", source)));
                    BufferedImage painted = paint(view, 160);
                    var transform =
                            source.metadata()
                                    .gridPlacement()
                                    .orElseThrow()
                                    .affineTransform()
                                    .orElseThrow();
                    assertRegion(view, painted, transform.gridToMap(8, 8), Color.RED, 20);
                    assertRegion(view, painted, transform.gridToMap(24, 8), Color.BLUE, 20);
                    assertColorNear(Color.WHITE, new Color(painted.getRGB(10, 10)), 1);
                    view.close();
                });
        assertTrue(source.isClosed());
    }

    @Test
    void affineMapViewDensityPlanReachesMixedAxisImageIoSubsampling() throws Exception {
        Path imagePath =
                copyJpegFixture(
                        "mixed-axis",
                        "projected-regions.jgw",
                        21,
                        "ce0a9158c4e080cd655ff57d7a4e0bb19888cd77651f640321cbd00d6f4be064");
        EncodedRasterDecoder delegate =
                AwtRasterDecoders.level1().find(EncodedRasterFormat.JPEG).orElseThrow();
        AtomicReference<DecodeFacts> observed = new AtomicReference<>();
        EncodedRasterDecoder recording =
                new EncodedRasterDecoder() {
                    @Override
                    public boolean supportsInterpolation(RasterInterpolation interpolation) {
                        return delegate.supportsInterpolation(interpolation);
                    }

                    @Override
                    public RgbaPixelBuffer decode(
                            InputStream input, EncodedRasterDecodeContext context) {
                        observed.set(
                                new DecodeFacts(
                                        context.sourceWindow(),
                                        context.outputWidth(),
                                        context.outputHeight(),
                                        context.interpolation(),
                                        ImageIoRasterDecoder.Subsampling.forContext(context)));
                        return delegate.decode(input, context);
                    }
                };
        EncodedRasterDecoderRegistry registry =
                EncodedRasterDecoderRegistry.builder()
                        .register(EncodedRasterFormat.JPEG, recording)
                        .build();
        RasterSource source =
                RasterImages.open(
                        imagePath,
                        new SourceIdentity("mixed-axis", "mixed-axis"),
                        ImageOpenOptions.defaults()
                                .withPlacement(ImagePlacement.worldFile(projectedCrs())),
                        registry);
        SwingUtilities.invokeAndWait(
                () -> {
                    MapView view = TestMapViews.identity();
                    view.setSize(160, 160);
                    Envelope bounds = source.metadata().mapBounds().orElseThrow();
                    view.setViewport(
                            new MapViewport(
                                    160, 160, bounds.center().x(), bounds.center().y(), 8.1));
                    view.setLayerBindings(
                            List.of(MapLayerBinding.ownedRaster("jpeg", "jpeg", source)));
                    paint(view, 160);
                    view.close();
                });
        assertEquals(
                new DecodeFacts(
                        new RasterWindow(0, 0, 32, 16),
                        8,
                        5,
                        RasterInterpolation.NEAREST,
                        new ImageIoRasterDecoder.Subsampling(4, 1, 2, 0, 8, 16)),
                observed.get());
        assertTrue(source.isClosed());
    }

    @Test
    void affineWorldFilesRetainCrsAndEnforceAttachmentBoundaries() throws Exception {
        Path imagePath =
                copyPngFixture(
                        "geographic",
                        "geographic-rgba.pgw",
                        22,
                        "2f04215b9625536b036b768d8bffc03c2045debd66cb34dc0361c5398ffdbbd5");
        Path worldPath = directory.resolve("geographic.pgw");

        RasterSource geographic =
                open(
                        imagePath,
                        ImagePlacement.worldFile(
                                CrsMetadata.recognized(
                                        CrsDefinitions.EPSG_4326,
                                        Optional.of("EPSG:4326"),
                                        Optional.empty())));
        MapView geographicView =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_4326, CrsDefinitions.EPSG_4326);
        geographicView.setSize(100, 100);
        geographicView.setLayerBindings(
                List.of(MapLayerBinding.borrowedRaster("geographic", "geographic", geographic)));
        assertEquals(
                "EPSG:4326",
                geographic
                        .metadata()
                        .crs()
                        .orElseThrow()
                        .definition()
                        .orElseThrow()
                        .canonicalIdentifier());
        geographicView.close();
        geographic.close();

        assertAttachmentFailure(
                imagePath,
                ImagePlacement.worldFile(),
                CrsDefinitions.EPSG_3857,
                "CRS_METADATA_MISSING");
        assertAttachmentFailure(
                imagePath,
                ImagePlacement.worldFile(
                        CrsMetadata.unknown(Optional.of("LOCAL"), Optional.empty())),
                CrsDefinitions.EPSG_3857,
                "CRS_DEFINITION_UNKNOWN");
        assertAttachmentFailure(
                imagePath,
                ImagePlacement.worldFile(
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326,
                                Optional.of("EPSG:4326"),
                                Optional.empty())),
                CrsDefinitions.EPSG_3857,
                "CRS_RASTER_WARP_UNSUPPORTED");

        Files.writeString(worldPath, "1\n0\n0\n-1\n200\n0");
        assertAttachmentFailure(
                imagePath,
                ImagePlacement.worldFile(
                        CrsMetadata.recognized(
                                CrsDefinitions.EPSG_4326,
                                Optional.of("EPSG:4326"),
                                Optional.empty())),
                CrsDefinitions.EPSG_4326,
                "CRS_ENVELOPE_OUT_OF_DOMAIN");
    }

    private Path copyPngFixture(
            String stem, String worldResource, int worldLength, String worldHash) throws Exception {
        return copyFixture(
                stem,
                "png",
                "rgba-2x2.png.b64",
                79,
                "b24037705a1852832821c1a3419df9eee1cc7e9c9bff877be6a37f6eb32368fe",
                worldResource,
                "pgw",
                worldLength,
                worldHash);
    }

    private Path copyJpegFixture(
            String stem, String worldResource, int worldLength, String worldHash) throws Exception {
        return copyFixture(
                stem,
                "jpeg",
                "rgb-regions-32x16.jpeg.b64",
                642,
                "c24dac6ae511de2680b0b66a83b003058dc3b0e150cb1fc46873243854752990",
                worldResource,
                "jgw",
                worldLength,
                worldHash);
    }

    private Path copyFixture(
            String stem,
            String imageExtension,
            String encodedResource,
            int imageLength,
            String imageHash,
            String worldResource,
            String worldExtension,
            int worldLength,
            String worldHash)
            throws Exception {
        byte[] image =
                Base64.getMimeDecoder().decode(resourceBytes("image-fixtures/" + encodedResource));
        assertFixture(image, imageLength, imageHash);
        byte[] world = resourceBytes("image-fixtures/g6-002/" + worldResource);
        assertFixture(world, worldLength, worldHash);
        Path imagePath = directory.resolve(stem + '.' + imageExtension);
        Files.write(imagePath, image);
        Files.write(directory.resolve(stem + '.' + worldExtension), world);
        return imagePath;
    }

    private static byte[] resourceBytes(String resource) throws IOException {
        try (InputStream input =
                MapViewAffineImageTest.class.getResourceAsStream(
                        "/io/github/mundanej/map/awt/" + resource)) {
            if (input == null) {
                throw new IOException("Missing test fixture: " + resource);
            }
            return input.readAllBytes();
        }
    }

    private static void assertFixture(byte[] bytes, int length, String hash) throws Exception {
        assertEquals(length, bytes.length);
        assertEquals(
                hash, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }

    private RasterSource open(Path imagePath, ImagePlacement placement) {
        return RasterImages.open(
                imagePath,
                new SourceIdentity("affine-" + imagePath.getFileName(), "affine"),
                ImageOpenOptions.defaults().withPlacement(placement),
                AwtRasterDecoders.level1());
    }

    private void assertAttachmentFailure(
            Path imagePath,
            ImagePlacement placement,
            CrsDefinition displayCrs,
            String expectedCode) {
        try (RasterSource source = open(imagePath, placement);
                MapView view = new MapView(CrsRegistry.level1(), displayCrs, displayCrs)) {
            CrsException failure =
                    assertThrows(
                            CrsException.class,
                            () ->
                                    view.setLayerBindings(
                                            List.of(
                                                    MapLayerBinding.borrowedRaster(
                                                            "affine", "affine", source))));
            assertEquals(expectedCode, failure.problem().code());
        }
    }

    private static BufferedImage paint(MapView view, int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            view.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void assertRegion(
            MapView view,
            BufferedImage image,
            Coordinate mapCoordinate,
            Color expected,
            int tolerance) {
        Coordinate screen = view.viewport().worldToScreen(mapCoordinate);
        int centerX = (int) Math.round(screen.x());
        int centerY = (int) Math.round(screen.y());
        for (int y = centerY - 1; y <= centerY + 1; y++) {
            for (int x = centerX - 1; x <= centerX + 1; x++) {
                assertColorNear(expected, new Color(image.getRGB(x, y)), tolerance);
            }
        }
    }

    private static void assertPaintContainedAndBounded(
            BufferedImage image,
            int minX,
            int minY,
            int maxX,
            int maxY,
            int minimumCount,
            int maximumCount) {
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) & 0x00ff_ffff) != 0x00ff_ffff) {
                    count++;
                    assertTrue(x >= minX && x <= maxX, "paint x outside affine envelope: " + x);
                    assertTrue(y >= minY && y <= maxY, "paint y outside affine envelope: " + y);
                }
            }
        }
        assertTrue(count >= minimumCount, "too little affine paint: " + count);
        assertTrue(count <= maximumCount, "too much affine paint: " + count);
    }

    private static CrsMetadata projectedCrs() {
        return CrsMetadata.recognized(
                CrsDefinitions.EPSG_3857, Optional.of("EPSG:3857"), Optional.empty());
    }

    private static void assertColorNear(Color expected, Color actual, int tolerance) {
        assertTrue(Math.abs(expected.getRed() - actual.getRed()) <= tolerance);
        assertTrue(Math.abs(expected.getGreen() - actual.getGreen()) <= tolerance);
        assertTrue(Math.abs(expected.getBlue() - actual.getBlue()) <= tolerance);
    }

    private record DecodeFacts(
            RasterWindow window,
            int outputWidth,
            int outputHeight,
            RasterInterpolation interpolation,
            ImageIoRasterDecoder.Subsampling subsampling) {}
}
