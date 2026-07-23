package io.github.mundanej.map.io.se;

import io.github.mundanej.map.api.NamedSymbolCatalog;
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

/** Entry points for secure bounded reading of the approved OGC SE 1.1 subset. */
public final class SeStyles {
    private static final int READ_CHUNK = 65_536;

    private SeStyles() {}

    /**
     * Reads one local regular UTF-8 file with explicit catalog and options.
     *
     * @param path local regular file
     * @param catalog explicit immutable symbol catalog
     * @param options bounded read options
     * @return immutable supported feature style
     */
    public static SeFeatureStyle read(
            Path path, NamedSymbolCatalog catalog, SeReadOptions options) {
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(options, "options");
        String source = sourceName(path);
        checkCancelled(source, options);
        BasicFileAttributes attributes;
        try {
            attributes = Files.readAttributes(path, BasicFileAttributes.class);
        } catch (NoSuchFileException exception) {
            throw SeFailures.io(source, "open", "notFound");
        } catch (AccessDeniedException | SecurityException exception) {
            throw SeFailures.io(source, "open", "accessDenied");
        } catch (ClosedFileSystemException exception) {
            throw SeFailures.io(source, "open", "closed");
        } catch (IOException exception) {
            throw SeFailures.io(source, "open", "other");
        }
        if (!attributes.isRegularFile()) {
            throw SeFailures.io(source, "open", "notRegularFile");
        }
        if (attributes.size() > options.limits().maximumInputBytes()) {
            throw SeFailures.inputLimit(
                    source, attributes.size(), options.limits().maximumInputBytes());
        }
        InputStream input;
        try {
            input = Files.newInputStream(path);
        } catch (NoSuchFileException exception) {
            throw SeFailures.io(source, "read", "notFound");
        } catch (AccessDeniedException | SecurityException exception) {
            throw SeFailures.io(source, "read", "accessDenied");
        } catch (ClosedFileSystemException exception) {
            throw SeFailures.io(source, "read", "closed");
        } catch (IOException exception) {
            throw SeFailures.io(source, "read", "other");
        }
        return readOpened(source, input, attributes.size(), catalog, options);
    }

    /**
     * Parses a defensively copied UTF-8 byte snapshot with explicit catalog and options.
     *
     * @param sourceName bounded logical source name used by diagnostics
     * @param bytes caller-owned encoded bytes
     * @param catalog explicit immutable symbol catalog
     * @param options bounded read options
     * @return immutable supported feature style
     */
    public static SeFeatureStyle read(
            String sourceName, byte[] bytes, NamedSymbolCatalog catalog, SeReadOptions options) {
        String source = requireSourceName(sourceName);
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(options, "options");
        checkCancelled(source, options);
        if (bytes.length > options.limits().maximumInputBytes()) {
            throw SeFailures.inputLimit(source, bytes.length, options.limits().maximumInputBytes());
        }
        SeOwnedBudget budget = new SeOwnedBudget(source, options.limits());
        budget.charge(bytes.length, "/");
        return parseOwned(source, Arrays.copyOf(bytes, bytes.length), catalog, options, budget);
    }

    private static SeFeatureStyle parseOwned(
            String source,
            byte[] bytes,
            NamedSymbolCatalog catalog,
            SeReadOptions options,
            SeOwnedBudget budget) {
        checkCancelled(source, options);
        return new SeParser(source, bytes, catalog, options, budget).parse();
    }

    static SeFeatureStyle readOpened(
            String source,
            InputStream input,
            long capturedSize,
            NamedSymbolCatalog catalog,
            SeReadOptions options) {
        Objects.requireNonNull(input, "input");
        SeReadException primary = null;
        SeFeatureStyle result = null;
        try {
            SeOwnedBudget budget = new SeOwnedBudget(source, options.limits());
            result =
                    parseOwned(
                            source,
                            readBounded(source, input, capturedSize, options, budget),
                            catalog,
                            options,
                            budget);
        } catch (SeReadException failure) {
            primary = failure;
        } catch (ClosedFileSystemException failure) {
            primary = SeFailures.io(source, "read", "closed");
        } catch (SecurityException failure) {
            primary = SeFailures.io(source, "read", "accessDenied");
        } catch (IOException failure) {
            primary = SeFailures.io(source, "read", "other");
        }
        try {
            input.close();
        } catch (ClosedFileSystemException failure) {
            primary = mergeCloseFailure(primary, SeFailures.io(source, "close", "closed"));
        } catch (SecurityException failure) {
            primary = mergeCloseFailure(primary, SeFailures.io(source, "close", "accessDenied"));
        } catch (IOException failure) {
            primary = mergeCloseFailure(primary, SeFailures.io(source, "close", "other"));
        }
        if (primary != null) {
            throw primary;
        }
        return Objects.requireNonNull(result, "successful parse result");
    }

    private static SeReadException mergeCloseFailure(
            SeReadException primary, SeReadException close) {
        if (primary == null) {
            return close;
        }
        primary.addSuppressed(close);
        return primary;
    }

    private static byte[] readBounded(
            String source,
            InputStream input,
            long capturedSize,
            SeReadOptions options,
            SeOwnedBudget budget)
            throws IOException {
        int capacity = Math.toIntExact(capturedSize);
        budget.charge(capacity, "/");
        byte[] bytes = new byte[capacity];
        int length = 0;
        while (true) {
            checkCancelled(source, options);
            if (length == bytes.length) {
                int probe = input.read();
                if (probe < 0) {
                    break;
                }
                int next = Math.min(options.limits().maximumInputBytes(), length + READ_CHUNK);
                if (next <= length) {
                    throw SeFailures.inputLimit(
                            source, (long) length + 1L, options.limits().maximumInputBytes());
                }
                budget.charge(next, "/");
                bytes = Arrays.copyOf(bytes, next);
                bytes[length++] = (byte) probe;
            }
            int count = input.read(bytes, length, bytes.length - length);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                continue;
            }
            length += count;
        }
        if (length == bytes.length) {
            return bytes;
        }
        budget.charge(length, "/");
        return Arrays.copyOf(bytes, length);
    }

    private static String sourceName(Path path) {
        Path name = path.getFileName();
        return requireSourceName(name == null ? "se-style" : name.toString());
    }

    private static String requireSourceName(String value) {
        Objects.requireNonNull(value, "sourceName");
        if (value.isBlank() || value.length() > 256) {
            throw new IllegalArgumentException("sourceName must contain 1 through 256 characters");
        }
        return value;
    }

    private static void checkCancelled(String source, SeReadOptions options) {
        if (options.cancellation().isCancellationRequested()) {
            throw SeFailures.cancelled(source);
        }
    }
}
