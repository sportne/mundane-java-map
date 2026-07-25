package io.github.mundanej.map.io.geopackage;

import io.github.mundanej.map.api.CancellationToken;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;

final class GeoPackageFile {
    private static final int HEADER_BYTES = 100;
    private static final int APPLICATION_ID = 0x47504B47;
    private static final byte[] SQLITE_SIGNATURE =
            new byte[] {
                'S', 'Q', 'L', 'i', 't', 'e', ' ', 'f', 'o', 'r', 'm', 'a', 't', ' ', '3', 0
            };

    private GeoPackageFile() {}

    static Fingerprint preflight(
            String sourceId,
            Path supplied,
            GeoPackageLimits limits,
            CancellationToken cancellation) {
        GeoPackageFailures.checkpoint(sourceId, cancellation::isCancellationRequested, "open");
        if (!isSupportedPlatform()) {
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SQLITE_ADAPTER_UNAVAILABLE",
                    "The SQLite adapter does not support this platform",
                    Map.of("reason", "unsupportedPlatform"));
        }
        Path path = supplied.normalize();
        Path name = path.getFileName();
        if (!path.isAbsolute()
                || name == null
                || !name.toString().toLowerCase(Locale.ROOT).endsWith(".gpkg")
                || Files.isSymbolicLink(path)
                || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                || !Files.isReadable(path)) {
            throw invalid(sourceId, "path");
        }
        rejectSidecars(sourceId, path);
        try {
            BasicFileAttributes attributes =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            long size = attributes.size();
            if (size < HEADER_BYTES || size > limits.maximumInputBytes()) {
                throw invalid(sourceId, size < HEADER_BYTES ? "header" : "pageLayout");
            }
            ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.BIG_ENDIAN);
            try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                while (header.hasRemaining() && channel.read(header) >= 0) {
                    GeoPackageFailures.checkpoint(
                            sourceId, cancellation::isCancellationRequested, "open");
                }
            }
            if (header.position() != HEADER_BYTES) {
                throw invalid(sourceId, "header");
            }
            byte[] bytes = header.array();
            ByteBuffer fields = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            if (!Arrays.equals(SQLITE_SIGNATURE, Arrays.copyOf(bytes, SQLITE_SIGNATURE.length))) {
                throw invalid(sourceId, "header");
            }
            int pageValue =
                    Short.toUnsignedInt(
                            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort(16));
            int pageSize = pageValue == 1 ? 65_536 : pageValue;
            if (pageSize < 512
                    || pageSize > 65_536
                    || Integer.bitCount(pageSize) != 1
                    || size % pageSize != 0) {
                throw invalid(sourceId, "pageLayout");
            }
            long pageCount = Integer.toUnsignedLong(fields.getInt(28));
            long physicalPages = size / pageSize;
            int reservedBytes = Byte.toUnsignedInt(bytes[20]);
            int schemaFormat = fields.getInt(44);
            int textEncoding = fields.getInt(56);
            if (bytes[18] != 1
                    || bytes[19] != 1
                    || reservedBytes >= pageSize
                    || bytes[21] != 64
                    || bytes[22] != 32
                    || bytes[23] != 32
                    || pageCount == 0
                    || pageCount != physicalPages
                    || schemaFormat < 1
                    || schemaFormat > 4
                    || textEncoding < 1
                    || textEncoding > 3) {
                throw invalid(sourceId, "pageLayout");
            }
            for (int index = 72; index < 92; index++) {
                if (bytes[index] != 0) {
                    throw invalid(sourceId, "header");
                }
            }
            if (fields.getInt(68) != APPLICATION_ID) {
                throw GeoPackageFailures.failure(
                        sourceId,
                        "GEOPACKAGE_PROFILE_UNSUPPORTED",
                        "Unsupported GeoPackage application identifier",
                        Map.of("construct", "applicationId"));
            }
            int version = fields.getInt(60);
            if (version != 10_400) {
                throw GeoPackageFailures.failure(
                        sourceId,
                        "GEOPACKAGE_PROFILE_UNSUPPORTED",
                        "Unsupported GeoPackage version",
                        Map.of("construct", "version"));
            }
            Object key = attributes.fileKey();
            if (key == null) {
                throw invalid(sourceId, "type");
            }
            return new Fingerprint(path, key.toString(), size, attributes.lastModifiedTime());
        } catch (IOException exception) {
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SQLITE_INPUT_INVALID",
                    "GeoPackage input could not be inspected",
                    Map.of("reason", "type"),
                    exception);
        }
    }

    static void verify(String sourceId, Fingerprint expected, String phase) {
        if (hasSidecar(expected.path())) {
            throw changed(sourceId, phase);
        }
        try {
            BasicFileAttributes actual =
                    Files.readAttributes(
                            expected.path(), BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (actual.fileKey() == null
                    || !expected.fileKey().equals(actual.fileKey().toString())
                    || expected.size() != actual.size()
                    || !expected.modified().equals(actual.lastModifiedTime())) {
                throw changed(sourceId, phase);
            }
        } catch (IOException exception) {
            throw GeoPackageFailures.failure(
                    sourceId,
                    "SQLITE_INPUT_CHANGED",
                    "GeoPackage input changed",
                    Map.of("phase", phase),
                    exception);
        }
    }

    private static boolean isSupportedPlatform() {
        String os = System.getProperty("os.name");
        String arch = System.getProperty("os.arch");
        return "Linux".equals(os)
                && ("amd64".equals(arch) || "x86_64".equals(arch))
                && !org.sqlite.util.OSInfo.isMusl();
    }

    private static void rejectSidecars(String sourceId, Path path) {
        if (hasSidecar(path)) {
            throw invalid(sourceId, "sidecar");
        }
    }

    private static boolean hasSidecar(Path path) {
        String base = java.util.Objects.requireNonNull(path.getFileName(), "fileName").toString();
        Path parent = java.util.Objects.requireNonNull(path.getParent(), "parent");
        for (String suffix : new String[] {"-journal", "-wal", "-shm"}) {
            if (Files.exists(parent.resolve(base + suffix), LinkOption.NOFOLLOW_LINKS)) {
                return true;
            }
        }
        return false;
    }

    private static io.github.mundanej.map.api.SourceException invalid(
            String sourceId, String reason) {
        return GeoPackageFailures.failure(
                sourceId,
                "SQLITE_INPUT_INVALID",
                "GeoPackage input failed preflight",
                Map.of("reason", reason));
    }

    private static io.github.mundanej.map.api.SourceException changed(
            String sourceId, String phase) {
        return GeoPackageFailures.failure(
                sourceId,
                "SQLITE_INPUT_CHANGED",
                "GeoPackage input changed",
                Map.of("phase", phase));
    }

    record Fingerprint(Path path, String fileKey, long size, FileTime modified) {}
}
