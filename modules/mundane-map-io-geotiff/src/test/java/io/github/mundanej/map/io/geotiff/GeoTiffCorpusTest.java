package io.github.mundanej.map.io.geotiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.ElevationQueries;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GeoTiffCorpusTest {
    private static final String ROOT = "/geotiff-corpus/";
    private static final SourceIdentity ID = new SourceIdentity("corpus", "GeoTIFF corpus");

    @Test
    void manifestPinsProvenanceLicenseDigestsAndRepresentativeProfileCoverage() throws IOException {
        List<Row> rows = manifest();
        assertEquals(4, rows.size());
        assertEquals(Set.of("raster", "elevation"), values(rows, Row::route));
        assertEquals(Set.of("strips", "tiles"), values(rows, Row::layout));
        assertEquals(Set.of("none", "packbits", "deflate"), values(rows, Row::compression));
        assertEquals(Set.of("EPSG:4326", "EPSG:3857"), values(rows, Row::crs));
        assertEquals(
                Set.of("RGB8", "BlackIsZero8", "Int16", "Float32"), values(rows, Row::profile));
        assertFalse(resource("PROVENANCE.md").length == 0);
        assertFalse(resource("recipes/gdal-3.13.0-geotiff.sh").length == 0);
        assertToolchain();

        Set<String> ids = new HashSet<>();
        for (Row row : rows) {
            assertTrue(ids.add(row.id()));
            assertTrue(row.path().matches("data/[a-z0-9-]+\\.tif"));
            assertEquals("GDAL-GTiff", row.generator());
            assertEquals("3.13.0", row.generatorVersion());
            assertEquals("Apache-2.0", row.artifactLicense());
            assertEquals("recipes/gdal-3.13.0-geotiff.sh", row.recipe());
            assertEquals("toolchain.tsv", row.toolchainInventory());
            byte[] encoded = resource(row.path());
            assertEquals(row.bytes(), encoded.length);
            assertEquals(row.sha256(), sha256(encoded));
            assertLayoutAndCompression(row, encoded);
        }
    }

    private static void assertToolchain() throws IOException {
        List<String> lines =
                new String(resource("toolchain.tsv"), StandardCharsets.UTF_8).lines().toList();
        assertEquals("component\tversionOrIdentity\trole\tlicenseRecord", lines.getFirst());
        List<Tool> tools = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] fields = lines.get(index).split("\t", -1);
            assertEquals(4, fields.length);
            tools.add(new Tool(fields[0], fields[1], fields[2], fields[3]));
        }
        assertEquals(
                Set.of(
                        "generator-image",
                        "python3.14-minimal",
                        "GDAL-GTiff",
                        "internal-PROJ",
                        "libproj25",
                        "proj-data",
                        "libgeotiff5",
                        "libtiff6",
                        "zlib1g",
                        "libdeflate0"),
                values(tools, Tool::component));
        assertEquals("3.14.4-1", version(tools, "python3.14-minimal"));
        assertEquals("3.13.0", version(tools, "GDAL-GTiff"));
        assertEquals("9.8.1", version(tools, "internal-PROJ"));
        assertEquals("9.7.1-1", version(tools, "libproj25"));
        assertEquals("9.7.1-1", version(tools, "proj-data"));
        for (Tool tool : tools) {
            assertFalse(tool.versionOrIdentity().isBlank());
            assertFalse(tool.role().isBlank());
            if (tool.component().equals("generator-image")) {
                assertEquals("NOT_DISTRIBUTED", tool.licenseRecord());
            } else {
                assertFalse(resource(tool.licenseRecord()).length == 0);
            }
        }
    }

    private static String version(List<Tool> tools, String component) {
        return tools.stream()
                .filter(tool -> tool.component().equals(component))
                .findFirst()
                .orElseThrow()
                .versionOrIdentity();
    }

    @Test
    void independentRasterFixturesOpenQueryAndServeExactPixels() throws IOException {
        for (Row row :
                manifest().stream().filter(value -> value.route().equals("raster")).toList()) {
            try (RasterSource source =
                    GeoTiffFiles.openRaster(
                            ID, resource(row.path()), GeoTiffRasterOptions.defaults())) {
                assertEquals(row.width(), source.metadata().width());
                assertEquals(row.height(), source.metadata().height());
                assertEquals(
                        row.crs(),
                        source.metadata().crs().orElseThrow().canonicalIdentifier().orElseThrow());
                var pixels =
                        source.read(
                                        new RasterRequest(
                                                new RasterWindow(0, 0, row.width(), row.height()),
                                                row.width(),
                                                row.height(),
                                                Optional.empty()),
                                        CancellationToken.none())
                                .pixels();
                assertEquals(row.width(), pixels.width());
                assertEquals(row.height(), pixels.height());
                assertEquals(expectedRaster(row.profile(), 0, 0), pixels.rgbaAt(0, 0));
                assertEquals(expectedRaster(row.profile(), 3, 5), pixels.rgbaAt(3, 5));
                assertEquals(expectedRaster(row.profile(), 7, 11), pixels.rgbaAt(7, 11));
                assertEquals(
                        expectedRasterBounds(row.crs()),
                        source.metadata().mapBounds().orElseThrow());
            }
        }
    }

    @Test
    void independentElevationFixturesOpenSampleAndQueryThroughSharedModel() throws IOException {
        for (Row row :
                manifest().stream().filter(value -> value.route().equals("elevation")).toList()) {
            try (ElevationSource source =
                    GeoTiffFiles.openElevation(
                            ID,
                            resource(row.path()),
                            GeoTiffElevationOptions.of(ElevationUnit.METRE))) {
                assertEquals(row.width(), source.metadata().columnCount());
                assertEquals(row.height(), source.metadata().rowCount());
                assertEquals(
                        row.crs(), source.metadata().crs().canonicalIdentifier().orElseThrow());
                double first = expectedElevation(row.profile(), 0, 0);
                double last = expectedElevation(row.profile(), row.width() - 1, row.height() - 1);
                assertEquals(first, source.sample(0, 0).orElseThrow());
                assertEquals(
                        expectedElevation(row.profile(), 3, 5), source.sample(3, 5).orElseThrow());
                assertEquals(last, source.sample(row.width() - 1, row.height() - 1).orElseThrow());
                assertEquals(expectedElevationBounds(row.crs()), source.metadata().sampleBounds());
                var definition =
                        row.crs().equals("EPSG:4326")
                                ? CrsDefinitions.EPSG_4326
                                : CrsDefinitions.EPSG_3857;
                var bounds = source.metadata().sampleBounds();
                assertEquals(
                        first,
                        ElevationQueries.query(
                                        source,
                                        definition,
                                        new Coordinate(bounds.minX(), bounds.maxY()),
                                        ElevationQueryMode.NEAREST)
                                .orElseThrow()
                                .value());
            }
        }
    }

    private static int expectedRaster(String profile, int column, int row) {
        if (profile.equals("RGB8")) {
            return ((17 * column & 0xff) << 24)
                    | ((29 * row & 0xff) << 16)
                    | ((11 * (column + row) & 0xff) << 8)
                    | 0xff;
        }
        int gray = (13 * column + 7 * row) & 0xff;
        return (gray << 24) | (gray << 16) | (gray << 8) | 0xff;
    }

    private static double expectedElevation(String profile, int column, int row) {
        return profile.equals("Int16")
                ? -24_000 + 500.0 * column + 250.0 * row
                : -120.0 + 2.5 * column + 1.25 * row;
    }

    private static Envelope expectedRasterBounds(String crs) {
        return crs.equals("EPSG:4326")
                ? new Envelope(-10.0, 18.4, -7.6, 20.0)
                : new Envelope(100_000.0, 184_000.0, 116_000.0, 200_000.0);
    }

    private static Envelope expectedElevationBounds(String crs) {
        return crs.equals("EPSG:4326")
                ? new Envelope(
                        -0.9999999999999999,
                        0.8499999999999999,
                        -0.8499999999999999,
                        0.9999999999999999)
                : new Envelope(1_000.0, 500.0, 2_500.0, 2_000.0);
    }

    private static void assertLayoutAndCompression(Row row, byte[] encoded) {
        ByteOrder order = encoded[0] == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN;
        ByteBuffer data = ByteBuffer.wrap(encoded).order(order);
        assertEquals(42, Short.toUnsignedInt(data.getShort(2)));
        int ifd = data.getInt(4);
        int entries = Short.toUnsignedInt(data.getShort(ifd));
        Set<Integer> tags = new HashSet<>();
        int compression = -1;
        for (int index = 0; index < entries; index++) {
            int entry = ifd + 2 + 12 * index;
            int tag = Short.toUnsignedInt(data.getShort(entry));
            tags.add(tag);
            if (tag == 259) {
                compression = Short.toUnsignedInt(data.getShort(entry + 8));
            }
        }
        assertEquals(row.layout().equals("tiles"), tags.contains(324));
        assertEquals(row.layout().equals("strips"), tags.contains(273));
        assertEquals(
                switch (row.compression()) {
                    case "none" -> 1;
                    case "packbits" -> 32_773;
                    case "deflate" -> 8;
                    default -> throw new AssertionError("unknown manifest compression");
                },
                compression);
    }

    private static <T> Set<String> values(
            List<T> rows, java.util.function.Function<T, String> accessor) {
        Set<String> result = new HashSet<>();
        rows.forEach(row -> result.add(accessor.apply(row)));
        return Set.copyOf(result);
    }

    private static List<Row> manifest() throws IOException {
        String text = new String(resource("manifest.tsv"), StandardCharsets.UTF_8);
        List<String> lines = text.lines().toList();
        assertEquals(
                "id\tpath\troute\twidth\theight\tlayout\tcompression\tcrs\tprofile\tbytes\tsha256"
                        + "\tgenerator\tgeneratorVersion\tartifactLicense\trecipe\ttoolchainInventory",
                lines.getFirst());
        List<Row> rows = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            String[] fields = lines.get(index).split("\\t", -1);
            assertEquals(16, fields.length);
            rows.add(
                    new Row(
                            fields[0],
                            fields[1],
                            fields[2],
                            Integer.parseInt(fields[3]),
                            Integer.parseInt(fields[4]),
                            fields[5],
                            fields[6],
                            fields[7],
                            fields[8],
                            Long.parseLong(fields[9]),
                            fields[10],
                            fields[11],
                            fields[12],
                            fields[13],
                            fields[14],
                            fields[15]));
        }
        return List.copyOf(rows);
    }

    private static byte[] resource(String path) throws IOException {
        try (var input = GeoTiffCorpusTest.class.getResourceAsStream(ROOT + path)) {
            if (input == null) {
                throw new AssertionError("Missing corpus resource " + path);
            }
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private record Row(
            String id,
            String path,
            String route,
            int width,
            int height,
            String layout,
            String compression,
            String crs,
            String profile,
            long bytes,
            String sha256,
            String generator,
            String generatorVersion,
            String artifactLicense,
            String recipe,
            String toolchainInventory) {}

    private record Tool(
            String component, String versionOrIdentity, String role, String licenseRecord) {}
}
