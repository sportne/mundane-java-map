package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.io.image.ImageCachePolicy;
import io.github.mundanej.map.io.image.ImageOpenOptions;
import io.github.mundanej.map.io.image.ImagePlacement;
import io.github.mundanej.map.io.image.RasterImages;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RasterGridFixture implements AutoCloseable {
    private static final String RESOURCE_ROOT =
            "/io/github/mundanej/map/performance/fixture/raster-1024x768-v1/";
    private static final java.util.Map<String, FileFact> FILES =
            java.util.Map.of(
                    "PROVENANCE.md",
                    new FileFact(
                            792,
                            "61ba3aa19537c12a8ab36a31bc34ed7d7ac4fb98ec822acecdb4b0159838a133"),
                    "evidence.jgw",
                    new FileFact(
                            24, "a82e8504957403224b81cf68a85aec058e0a410c70cabd14a6e9f3158dad8de3"),
                    "evidence.jpg",
                    new FileFact(
                            14_106,
                            "5afcf4a3fbaf2c4b03f2a12929afc4a3c96b7faee378778e3b4a4dd013d7c731"),
                    "evidence.pgw",
                    new FileFact(
                            24, "a82e8504957403224b81cf68a85aec058e0a410c70cabd14a6e9f3158dad8de3"),
                    "evidence.png",
                    new FileFact(
                            1_178_082,
                            "1d7a32d6c8901637d684f6061e1e0563243a118e4b813f94fc7985460a4050a2"));
    private final Path directory;
    private final Path png;
    private final Path jpeg;
    private final String pngSha;
    private final String jpegSha;

    private RasterGridFixture(Path directory, Path png, Path jpeg, String pngSha, String jpegSha) {
        this.directory = directory;
        this.png = png;
        this.jpeg = jpeg;
        this.pngSha = pngSha;
        this.jpegSha = jpegSha;
    }

    static RasterGridFixture create(Path parent) throws IOException {
        Path directory = Files.createDirectories(parent.resolve("raster-1024x768-v1"));
        Path png = directory.resolve("evidence.png");
        Path jpeg = directory.resolve("evidence.jpg");
        copy("evidence.png", png);
        copy("evidence.jpg", jpeg);
        copy("evidence.pgw", directory.resolve("evidence.pgw"));
        copy("evidence.jgw", directory.resolve("evidence.jgw"));
        copy("PROVENANCE.md", directory.resolve("PROVENANCE.md"));
        String pngSha = sha256(png);
        String jpegSha = sha256(jpeg);
        for (java.util.Map.Entry<String, FileFact> entry : FILES.entrySet()) {
            Path file = directory.resolve(entry.getKey());
            FileFact expected = entry.getValue();
            if (Files.size(file) != expected.length() || !sha256(file).equals(expected.sha256())) {
                throw new IOException(
                        "Checked raster fixture digest or length changed: " + entry.getKey());
            }
        }
        return new RasterGridFixture(directory, png, jpeg, pngSha, jpegSha);
    }

    RasterSource openPng(ImageCachePolicy policy, boolean placed) {
        return open(png, "performance-png", policy, placed);
    }

    RasterSource openJpeg(ImageCachePolicy policy, boolean placed) {
        return open(jpeg, "performance-jpeg", policy, placed);
    }

    String pngSha() {
        return pngSha;
    }

    String jpegSha() {
        return jpegSha;
    }

    @Override
    public void close() throws IOException {
        if (!Files.exists(directory)) {
            return;
        }
        try (var stream = Files.walk(directory)) {
            for (Path path : stream.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    private static RasterSource open(
            Path path, String id, ImageCachePolicy policy, boolean placed) {
        ImageOpenOptions options =
                ImageOpenOptions.defaults()
                        .withCachePolicy(policy)
                        .withPlacement(
                                placed
                                        ? ImagePlacement.worldFile(
                                                CrsMetadata.recognized(
                                                        CrsDefinitions.EPSG_3857,
                                                        java.util.Optional.of("EPSG:3857"),
                                                        java.util.Optional.empty()))
                                        : ImagePlacement.unplaced());
        return RasterImages.open(
                path, new SourceIdentity(id, id), options, AwtRasterDecoders.level1());
    }

    private static void copy(String name, Path target) throws IOException {
        try (InputStream input =
                RasterGridFixture.class.getResourceAsStream(RESOURCE_ROOT + name)) {
            if (input == null) {
                throw new IOException("Missing checked raster fixture: " + name);
            }
            Files.copy(input, target);
        }
    }

    private static String sha256(Path path) throws IOException {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private record FileFact(long length, String sha256) {}
}
