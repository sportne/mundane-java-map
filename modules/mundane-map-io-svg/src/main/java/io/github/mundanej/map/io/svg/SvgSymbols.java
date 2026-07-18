package io.github.mundanej.map.io.svg;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.Symbol;
import io.github.mundanej.map.api.SymbolRole;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.Objects;

/** Secure, bounded import of the supported static SVG marker profile. */
public final class SvgSymbols {
    private SvgSymbols() {}

    /**
     * Reads a local regular file using the default limits and a non-cancelling token.
     *
     * @param identity stable source identity used by diagnostics
     * @param path local regular file
     * @param placement marker placement applied to every imported leaf
     * @return immutable marker-role symbol
     */
    public static Symbol read(SourceIdentity identity, Path path, MarkerPlacement placement) {
        return read(
                identity, path, placement, SvgImportLimits.defaults(), CancellationToken.none());
    }

    /**
     * Reads a local regular file using explicit limits and cancellation.
     *
     * @param identity stable source identity used by diagnostics
     * @param path local regular file
     * @param placement marker placement applied to every imported leaf
     * @param limits import limits
     * @param cancellation cancellation signal
     * @return immutable marker-role symbol
     */
    public static Symbol read(
            SourceIdentity identity,
            Path path,
            MarkerPlacement placement,
            SvgImportLimits limits,
            CancellationToken cancellation) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(identity.id(), cancellation);
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (NoSuchFileException exception) {
            throw SvgFailures.io(identity.id(), "open", "notFound");
        } catch (AccessDeniedException exception) {
            throw SvgFailures.io(identity.id(), "open", "accessDenied");
        } catch (ClosedFileSystemException exception) {
            throw SvgFailures.io(identity.id(), "open", "closed");
        } catch (SecurityException exception) {
            throw SvgFailures.io(identity.id(), "open", "accessDenied");
        } catch (IOException exception) {
            throw SvgFailures.io(identity.id(), "open", "other");
        }
        if (!attributes.isRegularFile()) {
            throw SvgFailures.io(identity.id(), "open", "other");
        }
        long capturedSize = attributes.size();
        if (capturedSize > limits.maximumInputBytes()) {
            throw SvgFailures.limit(
                    identity.id(), "inputBytes", capturedSize, limits.maximumInputBytes());
        }
        checkCancelled(identity.id(), cancellation);
        InputStream input;
        try {
            input = Files.newInputStream(path);
        } catch (NoSuchFileException exception) {
            throw SvgFailures.io(identity.id(), "open", "notFound");
        } catch (AccessDeniedException exception) {
            throw SvgFailures.io(identity.id(), "open", "accessDenied");
        } catch (ClosedFileSystemException exception) {
            throw SvgFailures.io(identity.id(), "open", "closed");
        } catch (SecurityException exception) {
            throw SvgFailures.io(identity.id(), "open", "accessDenied");
        } catch (IOException exception) {
            throw SvgFailures.io(identity.id(), "open", "other");
        }
        return readOpened(identity, input, capturedSize, placement, limits, cancellation);
    }

    /**
     * Parses encoded SVG bytes using default limits and a non-cancelling token.
     *
     * @param identity stable source identity used by diagnostics
     * @param bytes UTF-8 encoded SVG, defensively copied
     * @param placement marker placement applied to every imported leaf
     * @return immutable marker-role symbol
     */
    public static Symbol parse(SourceIdentity identity, byte[] bytes, MarkerPlacement placement) {
        return parse(
                identity, bytes, placement, SvgImportLimits.defaults(), CancellationToken.none());
    }

    /**
     * Parses encoded SVG bytes using explicit limits and cancellation.
     *
     * @param identity stable source identity used by diagnostics
     * @param bytes UTF-8 encoded SVG, defensively copied
     * @param placement marker placement applied to every imported leaf
     * @param limits import limits
     * @param cancellation cancellation signal
     * @return immutable marker-role symbol
     */
    public static Symbol parse(
            SourceIdentity identity,
            byte[] bytes,
            MarkerPlacement placement,
            SvgImportLimits limits,
            CancellationToken cancellation) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(limits, "limits");
        Objects.requireNonNull(cancellation, "cancellation");
        checkCancelled(identity.id(), cancellation);
        if (bytes.length > limits.maximumInputBytes()) {
            throw SvgFailures.limit(
                    identity.id(), "inputBytes", bytes.length, limits.maximumInputBytes());
        }
        long owned = Math.addExact((long) bytes.length * 3L, 256L);
        if (owned > limits.maximumOwnedBytes()) {
            throw SvgFailures.limit(identity.id(), "ownedBytes", owned, limits.maximumOwnedBytes());
        }
        byte[] copy = Arrays.copyOf(bytes, bytes.length);
        return parseOwned(identity, copy, bytes.length, placement, limits, cancellation);
    }

    static Symbol readOpened(
            SourceIdentity identity,
            InputStream input,
            long capturedSize,
            MarkerPlacement placement,
            SvgImportLimits limits,
            CancellationToken cancellation) {
        OwnedInput owned = null;
        SourceExceptionHolder failure = new SourceExceptionHolder();
        try {
            owned = readBounded(identity.id(), input, capturedSize, limits, cancellation);
        } catch (SourceException exception) {
            failure.value = exception;
        } catch (NoSuchFileException exception) {
            failure.value = SvgFailures.io(identity.id(), "read", "notFound");
        } catch (AccessDeniedException exception) {
            failure.value = SvgFailures.io(identity.id(), "read", "accessDenied");
        } catch (ClosedFileSystemException exception) {
            failure.value = SvgFailures.io(identity.id(), "read", "closed");
        } catch (SecurityException exception) {
            failure.value = SvgFailures.io(identity.id(), "read", "accessDenied");
        } catch (IOException exception) {
            failure.value = SvgFailures.io(identity.id(), "read", "other");
        }
        try {
            input.close();
        } catch (NoSuchFileException exception) {
            recordCleanup(failure, SvgFailures.io(identity.id(), "close", "notFound"));
        } catch (AccessDeniedException exception) {
            recordCleanup(failure, SvgFailures.io(identity.id(), "close", "accessDenied"));
        } catch (ClosedFileSystemException exception) {
            recordCleanup(failure, SvgFailures.io(identity.id(), "close", "closed"));
        } catch (SecurityException exception) {
            recordCleanup(failure, SvgFailures.io(identity.id(), "close", "accessDenied"));
        } catch (IOException exception) {
            recordCleanup(failure, SvgFailures.io(identity.id(), "close", "other"));
        }
        if (failure.value != null) {
            throw failure.value;
        }
        return parseOwned(
                identity, owned.bytes(), owned.allocatedBytes(), placement, limits, cancellation);
    }

    private static OwnedInput readBounded(
            String sourceId,
            InputStream input,
            long capturedSize,
            SvgImportLimits limits,
            CancellationToken cancellation)
            throws IOException {
        if (capturedSize < 0) {
            throw SvgFailures.io(sourceId, "read", "other");
        }
        if (capturedSize > limits.maximumInputBytes()) {
            throw SvgFailures.limit(
                    sourceId, "inputBytes", capturedSize, limits.maximumInputBytes());
        }
        int capacity = Math.toIntExact(capturedSize);
        long allocated = chargeReaderAllocation(sourceId, 0, capacity, limits);
        byte[] bytes = new byte[capacity];
        int length = 0;
        while (true) {
            checkCancelled(sourceId, cancellation);
            if (length < bytes.length) {
                int count = input.read(bytes, length, bytes.length - length);
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    length += count;
                    continue;
                }
            }
            int probe = input.read();
            if (probe < 0) {
                break;
            }
            long requested = (long) length + 1L;
            if (requested > limits.maximumInputBytes()) {
                throw SvgFailures.limit(
                        sourceId, "inputBytes", requested, limits.maximumInputBytes());
            }
            int expanded =
                    Math.min(
                            limits.maximumInputBytes(),
                            Math.max(length + 1, Math.max(1, bytes.length * 2)));
            allocated = chargeReaderAllocation(sourceId, allocated, expanded, limits);
            bytes = Arrays.copyOf(bytes, expanded);
            bytes[length++] = (byte) probe;
        }
        if (length != bytes.length) {
            allocated = chargeReaderAllocation(sourceId, allocated, length, limits);
            bytes = Arrays.copyOf(bytes, length);
        }
        return new OwnedInput(bytes, allocated);
    }

    private static long chargeReaderAllocation(
            String sourceId, long allocated, int requested, SvgImportLimits limits) {
        long total = Math.addExact(allocated, requested);
        if (total > limits.maximumOwnedBytes()) {
            throw SvgFailures.limit(sourceId, "ownedBytes", total, limits.maximumOwnedBytes());
        }
        return total;
    }

    private static Symbol parseOwned(
            SourceIdentity identity,
            byte[] bytes,
            long ownedInputBytes,
            MarkerPlacement placement,
            SvgImportLimits limits,
            CancellationToken cancellation) {
        long initialOwned =
                Math.addExact(ownedInputBytes, Math.addExact((long) bytes.length * 2L, 256L));
        if (initialOwned > limits.maximumOwnedBytes()) {
            throw SvgFailures.limit(
                    identity.id(), "ownedBytes", initialOwned, limits.maximumOwnedBytes());
        }
        Symbol symbol =
                new SvgImporter(identity.id(), bytes, placement, limits, cancellation, initialOwned)
                        .importSymbol();
        checkCancelled(identity.id(), cancellation);
        if (symbol.role() != SymbolRole.MARKER) {
            throw new IllegalStateException("SVG importer published a non-marker symbol");
        }
        return symbol;
    }

    private static void recordCleanup(
            SourceExceptionHolder failure, SourceException cleanupFailure) {
        if (failure.value == null) {
            failure.value = cleanupFailure;
        } else {
            failure.value.addSuppressed(cleanupFailure);
        }
    }

    private static void checkCancelled(String sourceId, CancellationToken cancellation) {
        if (cancellation.isCancellationRequested()) {
            throw SvgFailures.cancelled(sourceId);
        }
    }

    private static final class OwnedInput {
        private final byte[] bytes;
        private final long allocatedBytes;

        private OwnedInput(byte[] bytes, long allocatedBytes) {
            this.bytes = bytes;
            this.allocatedBytes = allocatedBytes;
        }

        private byte[] bytes() {
            return bytes;
        }

        private long allocatedBytes() {
            return allocatedBytes;
        }
    }

    private static final class SourceExceptionHolder {
        private SourceException value;
    }
}
