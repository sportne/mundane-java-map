package io.github.mundanej.map.io.geojson;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

/** Opens bounded local or caller-provided RFC 7946 documents. */
public final class GeoJsonFiles {
    private GeoJsonFiles() {}

    /**
     * Opens one regular local GeoJSON file.
     *
     * @param path regular non-symbolic-link input file
     * @param identity stable logical source identity
     * @param options immutable opening policy
     * @param cancellation cancellation signal
     * @return caller-owned open feature source
     */
    public static FeatureSource open(
            Path path,
            SourceIdentity identity,
            GeoJsonOpenOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        if (cancellation.isCancellationRequested()) {
            throw GeoJsonReader.failure(
                    identity.id(),
                    "SOURCE_CANCELLED",
                    "GeoJSON operation was cancelled",
                    Map.of("operation", "geojson-open"));
        }
        byte[] bytes = read(path, identity, options.formatLimits(), cancellation);
        return openOwned(bytes, identity, options, cancellation);
    }

    /**
     * Opens one defensively copied GeoJSON byte array.
     *
     * @param bytes complete encoded document, defensively copied
     * @param identity stable logical source identity
     * @param options immutable opening policy
     * @param cancellation cancellation signal
     * @return caller-owned open feature source
     */
    public static FeatureSource open(
            byte[] bytes,
            SourceIdentity identity,
            GeoJsonOpenOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        if (cancellation.isCancellationRequested()) {
            throw GeoJsonReader.failure(
                    identity.id(),
                    "SOURCE_CANCELLED",
                    "GeoJSON operation was cancelled",
                    Map.of("operation", "geojson-open"));
        }
        if (bytes.length > options.formatLimits().maximumInputBytes()) {
            throw GeoJsonReader.limit(
                    identity.id(),
                    "inputBytes",
                    bytes.length,
                    options.formatLimits().maximumInputBytes());
        }
        if (bytes.length > options.formatLimits().maximumOwnedBytes()) {
            throw GeoJsonReader.limit(
                    identity.id(),
                    "ownedBytes",
                    bytes.length,
                    options.formatLimits().maximumOwnedBytes());
        }
        return openOwned(Arrays.copyOf(bytes, bytes.length), identity, options, cancellation);
    }

    private static FeatureSource openOwned(
            byte[] bytes,
            SourceIdentity identity,
            GeoJsonOpenOptions options,
            CancellationToken cancellation) {
        GeoJsonReader.Opening opening =
                GeoJsonReader.inspect(bytes, identity, options.formatLimits(), cancellation);
        return new GeoJsonSource(bytes, identity, options, opening);
    }

    private static byte[] read(
            Path path,
            SourceIdentity identity,
            GeoJsonLimits limits,
            CancellationToken cancellation) {
        try {
            BasicFileAttributes before =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile() || before.isSymbolicLink()) {
                throw ioFailure(identity, "open", "other", null);
            }
            if (before.size() > limits.maximumInputBytes()) {
                throw GeoJsonReader.failure(
                        identity.id(),
                        "SOURCE_LIMIT_EXCEEDED",
                        "GeoJSON opening limit exceeded",
                        Map.of(
                                "scope",
                                "geojsonOpen",
                                "limit",
                                "inputBytes",
                                "requested",
                                Long.toString(before.size()),
                                "maximum",
                                Integer.toString(limits.maximumInputBytes())));
            }
            int expected = Math.toIntExact(before.size());
            byte[] snapshot = new byte[expected];
            try (InputStream input = Files.newInputStream(path)) {
                int total = 0;
                while (total < expected) {
                    if (cancellation.isCancellationRequested()) {
                        throw GeoJsonReader.failure(
                                identity.id(),
                                "SOURCE_CANCELLED",
                                "GeoJSON operation was cancelled",
                                Map.of("operation", "geojson-open"));
                    }
                    int count = input.read(snapshot, total, expected - total);
                    if (count < 0) {
                        throw ioFailure(identity, "read", "other", null);
                    }
                    total = Math.addExact(total, count);
                }
                if (input.read() >= 0) {
                    if (expected == limits.maximumInputBytes()) {
                        throw GeoJsonReader.failure(
                                identity.id(),
                                "SOURCE_LIMIT_EXCEEDED",
                                "GeoJSON opening limit exceeded",
                                Map.of(
                                        "scope",
                                        "geojsonOpen",
                                        "limit",
                                        "inputBytes",
                                        "requested",
                                        Long.toString((long) expected + 1),
                                        "maximum",
                                        Integer.toString(limits.maximumInputBytes())));
                    }
                    throw ioFailure(identity, "read", "other", null);
                }
            }
            BasicFileAttributes after =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!Objects.equals(before.fileKey(), after.fileKey())
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())) {
                throw ioFailure(identity, "read", "other", null);
            }
            return snapshot;
        } catch (io.github.mundanej.map.api.SourceException failure) {
            throw failure;
        } catch (java.nio.file.NoSuchFileException failure) {
            throw ioFailure(identity, "open", "notFound", failure);
        } catch (java.nio.file.AccessDeniedException failure) {
            throw ioFailure(identity, "open", "accessDenied", failure);
        } catch (IOException failure) {
            throw ioFailure(identity, "read", "other", failure);
        }
    }

    private static io.github.mundanej.map.api.SourceException ioFailure(
            SourceIdentity identity, String operation, String reason, Throwable cause) {
        return GeoJsonReader.failure(
                identity.id(),
                "GEOJSON_IO_FAILED",
                "GeoJSON local I/O failed",
                Map.of("operation", operation, "reason", reason),
                cause);
    }
}
