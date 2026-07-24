package io.github.mundanej.map.io.kml;

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
import java.util.Set;

/** Opens bounded local KML 2.2 documents as immutable feature sources. */
public final class KmlFiles {
    private KmlFiles() {}

    /**
     * Opens one regular non-symbolic-link local KML file.
     *
     * @param path local KML file
     * @param identity stable logical source identity
     * @param options immutable opening policy
     * @param cancellation cancellation signal
     * @return caller-owned immutable feature source
     */
    public static FeatureSource open(
            Path path,
            SourceIdentity identity,
            KmlOpenOptions options,
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
            KmlOpenOptions options,
            CancellationToken cancellation) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(identity, cancellation);
        KmlLimits limits = options.formatLimits();
        if (snapshot.length == 0 || snapshot.length > limits.maximumInputBytes()) {
            throw limit(identity, "inputBytes", snapshot.length, limits.maximumInputBytes());
        }
        if (snapshot.length > limits.maximumOwnedBytes()) {
            throw limit(identity, "ownedBytes", snapshot.length, limits.maximumOwnedBytes());
        }
        return openOwnedSnapshot(
                java.util.Arrays.copyOf(snapshot, snapshot.length),
                identity,
                options,
                cancellation);
    }

    private static FeatureSource openOwnedSnapshot(
            byte[] owned,
            SourceIdentity identity,
            KmlOpenOptions options,
            CancellationToken cancellation) {
        KmlParser.Opening opening =
                new KmlParser(owned, identity, options.formatLimits(), cancellation).parse();
        checkCancelled(identity, cancellation);
        FeatureSource delegate =
                InMemoryFeatureSource.open(
                        identity,
                        opening.records(),
                        Optional.of(KmlParser.SCHEMA),
                        Optional.of(
                                CrsMetadata.recognized(
                                        CrsRegistry.level1().resolve("EPSG:4326"),
                                        Optional.empty(),
                                        Optional.empty())),
                        options.sourceLimits());
        return new KmlSource(delegate, opening.diagnostics());
    }

    private static byte[] readSnapshot(
            Path path, SourceIdentity identity, KmlLimits limits, CancellationToken cancellation) {
        Path normalized = path.toAbsolutePath().normalize();
        BasicFileAttributes before = attributes(normalized, identity, "open");
        if (!before.isRegularFile() || before.isSymbolicLink() || before.size() == 0) {
            throw ioFailure(identity, "attributes", "other", null);
        }
        if (before.size() > limits.maximumInputBytes()) {
            throw limit(identity, "inputBytes", before.size(), limits.maximumInputBytes());
        }
        if (before.size() > limits.maximumOwnedBytes()) {
            throw limit(identity, "ownedBytes", before.size(), limits.maximumOwnedBytes());
        }
        byte[] snapshot = new byte[Math.toIntExact(before.size())];
        try (SeekableByteChannel input =
                Files.newByteChannel(
                        normalized, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            ByteBuffer target = ByteBuffer.wrap(snapshot);
            while (target.hasRemaining()) {
                checkCancelled(identity, cancellation);
                if (input.read(target) < 0) {
                    throw ioFailure(identity, "read", "changed", null);
                }
            }
            if (input.size() != snapshot.length) {
                throw ioFailure(identity, "read", "changed", null);
            }
        } catch (NoSuchFileException failure) {
            throw ioFailure(identity, "open", "notFound", failure);
        } catch (AccessDeniedException | SecurityException failure) {
            throw ioFailure(identity, "open", "accessDenied", failure);
        } catch (IOException failure) {
            throw ioFailure(identity, "read", "other", failure);
        }
        BasicFileAttributes after = attributes(normalized, identity, "read");
        if (!after.isRegularFile()
                || after.isSymbolicLink()
                || before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !sameFileKey(before.fileKey(), after.fileKey())) {
            throw ioFailure(identity, "read", "changed", null);
        }
        return snapshot;
    }

    private static BasicFileAttributes attributes(
            Path path, SourceIdentity identity, String operation) {
        try {
            return Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        } catch (NoSuchFileException failure) {
            throw ioFailure(identity, operation, "notFound", failure);
        } catch (AccessDeniedException | SecurityException failure) {
            throw ioFailure(identity, operation, "accessDenied", failure);
        } catch (IOException failure) {
            throw ioFailure(identity, operation, "other", failure);
        }
    }

    private static boolean sameFileKey(Object before, Object after) {
        return before == null || after == null || before.equals(after);
    }

    static void checkCancelled(SourceIdentity identity, CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            KmlDiagnostics diagnostics = new KmlDiagnostics(identity.id(), 1);
            throw diagnostics.failure(
                    "SOURCE_CANCELLED",
                    Map.of("operation", "kml-open"),
                    0,
                    "KML operation was cancelled",
                    null);
        }
    }

    static SourceException limit(
            SourceIdentity identity, String limit, long requested, long maximum) {
        KmlDiagnostics diagnostics = new KmlDiagnostics(identity.id(), 1);
        return diagnostics.failure(
                "SOURCE_LIMIT_EXCEEDED",
                Map.of(
                        "scope",
                        "kmlOpen",
                        "limit",
                        limit,
                        "requested",
                        Long.toString(requested),
                        "maximum",
                        Long.toString(maximum)),
                0,
                "KML opening limit was exceeded",
                null);
    }

    private static SourceException ioFailure(
            SourceIdentity identity, String operation, String reason, Throwable cause) {
        KmlDiagnostics diagnostics = new KmlDiagnostics(identity.id(), 1);
        return diagnostics.failure(
                "KML_IO_FAILED",
                Map.of("operation", operation, "reason", reason),
                0,
                "KML file I/O failed",
                cause);
    }
}
