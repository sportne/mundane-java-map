package io.github.mundanej.map.io.gpx;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Opens bounded local GPX 1.1 documents as immutable feature sources. */
public final class GpxFiles {
    private GpxFiles() {}

    /**
     * Opens one regular non-symbolic-link local GPX file.
     *
     * @param path local GPX file
     * @param identity stable logical source identity
     * @param options immutable opening policy
     * @param cancellation cancellation signal
     * @return caller-owned immutable feature source
     */
    public static FeatureSource open(
            Path path,
            SourceIdentity identity,
            GpxOpenOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(identity, cancellation);
        byte[] snapshot = readSnapshot(path, identity, options.formatLimits(), cancellation);
        return openOwnedSnapshot(snapshot, identity, options, cancellation);
    }

    static FeatureSource openSnapshot(
            byte[] snapshot,
            SourceIdentity identity,
            GpxOpenOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(identity, cancellation);
        GpxLimits limits = options.formatLimits();
        if (snapshot.length == 0 || snapshot.length > limits.maximumInputBytes()) {
            throw limit(identity, "inputBytes", snapshot.length, limits.maximumInputBytes());
        }
        if (snapshot.length > limits.maximumOwnedBytes()) {
            throw limit(identity, "ownedBytes", snapshot.length, limits.maximumOwnedBytes());
        }
        byte[] owned = java.util.Arrays.copyOf(snapshot, snapshot.length);
        return openOwnedSnapshot(owned, identity, options, cancellation);
    }

    private static FeatureSource openOwnedSnapshot(
            byte[] owned,
            SourceIdentity identity,
            GpxOpenOptions options,
            CancellationToken cancellation) {
        GpxLimits limits = options.formatLimits();
        GpxParser.Opening opening = new GpxParser(owned, identity, limits, cancellation).parse();
        checkCancelled(identity, cancellation);
        FeatureSource delegate =
                InMemoryFeatureSource.open(
                        identity,
                        opening.records(),
                        Optional.of(GpxParser.SCHEMA),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsRegistry.level1().resolve("EPSG:4326"),
                                        Optional.empty(),
                                        Optional.empty())),
                        options.sourceLimits());
        return new GpxSource(delegate, opening.diagnostics());
    }

    private static byte[] readSnapshot(
            Path path, SourceIdentity identity, GpxLimits limits, CancellationToken cancellation) {
        Path normalized = path.toAbsolutePath().normalize();
        try {
            BasicFileAttributes before =
                    Files.readAttributes(
                            normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!before.isRegularFile() || before.isSymbolicLink() || before.size() == 0) {
                throw ioFailure(identity, "attributes", "other", null);
            }
            if (before.size() > limits.maximumInputBytes()) {
                throw limit(identity, "inputBytes", before.size(), limits.maximumInputBytes());
            }
            if (before.size() > limits.maximumOwnedBytes()) {
                throw limit(identity, "ownedBytes", before.size(), limits.maximumOwnedBytes());
            }
            int expected = Math.toIntExact(before.size());
            byte[] snapshot = new byte[expected];
            try (SeekableByteChannel input =
                    Files.newByteChannel(
                            normalized,
                            java.util.Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
                ByteBuffer target = ByteBuffer.wrap(snapshot);
                while (target.hasRemaining()) {
                    checkCancelled(identity, cancellation);
                    int count = input.read(target);
                    if (count < 0) {
                        throw ioFailure(identity, "read", "changed", null);
                    }
                }
                checkCancelled(identity, cancellation);
                if (input.size() != expected) {
                    throw ioFailure(identity, "read", "changed", null);
                }
            }
            BasicFileAttributes after =
                    Files.readAttributes(
                            normalized, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!after.isRegularFile()
                    || after.isSymbolicLink()
                    || before.size() != after.size()
                    || !before.lastModifiedTime().equals(after.lastModifiedTime())
                    || !sameFileKey(before.fileKey(), after.fileKey())) {
                throw ioFailure(identity, "read", "changed", null);
            }
            return snapshot;
        } catch (SourceException failure) {
            throw failure;
        } catch (NoSuchFileException failure) {
            throw ioFailure(identity, "open", "notFound", failure);
        } catch (AccessDeniedException | SecurityException failure) {
            throw ioFailure(identity, "open", "accessDenied", failure);
        } catch (IOException failure) {
            throw ioFailure(identity, "read", "other", failure);
        }
    }

    private static boolean sameFileKey(Object before, Object after) {
        return before == null || after == null || before.equals(after);
    }

    private static void checkCancelled(SourceIdentity identity, CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            GpxDiagnostics diagnostics = new GpxDiagnostics(identity.id(), 1);
            throw diagnostics.failure(
                    "SOURCE_CANCELLED",
                    Map.of("operation", "gpx-open"),
                    0,
                    "GPX operation was cancelled",
                    null);
        }
    }

    static SourceException limit(
            SourceIdentity identity, String limit, long requested, long maximum) {
        GpxDiagnostics diagnostics = new GpxDiagnostics(identity.id(), 1);
        return diagnostics.failure(
                "SOURCE_LIMIT_EXCEEDED",
                Map.of(
                        "scope",
                        "gpxOpen",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                0,
                "GPX opening limit exceeded",
                null);
    }

    private static SourceException ioFailure(
            SourceIdentity identity, String operation, String reason, Throwable cause) {
        GpxDiagnostics diagnostics = new GpxDiagnostics(identity.id(), 1);
        return diagnostics.failure(
                "GPX_IO_FAILED",
                Map.of("operation", operation, "reason", reason),
                0,
                "GPX local I/O failed",
                cause);
    }
}
