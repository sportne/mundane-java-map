package io.github.mundanej.map.io.se;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.NamedSymbolCatalog;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.ClosedFileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SeSourceCoverageTest {
    private static final NamedSymbolCatalog EMPTY_CATALOG = NamedSymbolCatalog.of(List.of());
    private static final byte[] STYLE =
            """
            <se:FeatureTypeStyle xmlns:se="http://www.opengis.net/se">
              <se:Rule>
                <se:Name>coverage</se:Name>
                <se:PointSymbolizer>
                  <se:Graphic><se:Mark/></se:Graphic>
                </se:PointSymbolizer>
              </se:Rule>
            </se:FeatureTypeStyle>
            """
                    .getBytes(StandardCharsets.UTF_8);

    @TempDir Path temporaryDirectory;

    @Test
    void pathPreflightRejectsNonFilesAndCapturedOversize() throws Exception {
        SeReadException directory =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.read(
                                        temporaryDirectory,
                                        EMPTY_CATALOG,
                                        SeReadOptions.defaults()));
        assertEquals("notRegularFile", directory.problem().context().get("reason"));

        Path oversized = temporaryDirectory.resolve("oversized.xml");
        Files.write(oversized, STYLE);
        SeReadLimits defaults = SeReadLimits.defaults();
        SeReadLimits limits =
                new SeReadLimits(
                        STYLE.length - 1,
                        defaults.maximumElementDepth(),
                        defaults.maximumElements(),
                        defaults.maximumAttributes(),
                        defaults.maximumAggregateTextCharacters(),
                        defaults.maximumValueCharacters(),
                        defaults.maximumRules(),
                        defaults.maximumPredicates(),
                        defaults.maximumPredicateDepth(),
                        defaults.maximumSymbolizers(),
                        defaults.maximumCatalogReferences(),
                        defaults.maximumOutputSymbols(),
                        defaults.maximumOwnedBytes());
        SeReadException failure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.read(
                                        oversized,
                                        EMPTY_CATALOG,
                                        new SeReadOptions(limits, CancellationToken.none())));
        assertEquals("inputBytes", failure.problem().context().get("limit"));
    }

    @Test
    void boundedReaderCoversGrowthExactAndTruncatedSnapshots() {
        assertEquals(
                "coverage",
                SeStyles.readOpened(
                                "growth",
                                new ZeroThenBytesInputStream(STYLE),
                                0,
                                EMPTY_CATALOG,
                                SeReadOptions.defaults())
                        .rules()
                        .get(0)
                        .name()
                        .orElseThrow());
        assertEquals(
                1,
                SeStyles.readOpened(
                                "exact",
                                new ByteArrayInputStream(STYLE),
                                STYLE.length,
                                EMPTY_CATALOG,
                                SeReadOptions.defaults())
                        .rules()
                        .size());
        assertEquals(
                1,
                SeStyles.readOpened(
                                "truncate",
                                new ByteArrayInputStream(STYLE),
                                STYLE.length + 10L,
                                EMPTY_CATALOG,
                                SeReadOptions.defaults())
                        .rules()
                        .size());
    }

    @Test
    void readerAndCleanupMapRuntimeFilesystemFailures() {
        assertReason(
                new ThrowingInputStream(new ClosedFileSystemException(), null), "read", "closed");
        assertReason(
                new ThrowingInputStream(new SecurityException("denied"), null),
                "read",
                "accessDenied");
        assertReason(new ThrowingInputStream(new IOException("read"), null), "read", "other");
        assertReason(
                new ThrowingInputStream(null, new ClosedFileSystemException()), "close", "closed");
        assertReason(
                new ThrowingInputStream(null, new SecurityException("denied")),
                "close",
                "accessDenied");
    }

    private static void assertReason(InputStream input, String operation, String reason) {
        SeReadException failure =
                assertThrows(
                        SeReadException.class,
                        () ->
                                SeStyles.readOpened(
                                        "failure",
                                        input,
                                        1,
                                        EMPTY_CATALOG,
                                        SeReadOptions.defaults()));
        assertEquals(operation, failure.problem().context().get("operation"));
        assertEquals(reason, failure.problem().context().get("reason"));
    }

    private static final class ZeroThenBytesInputStream extends ByteArrayInputStream {
        private boolean zero = true;

        private ZeroThenBytesInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public synchronized int read(byte[] target, int offset, int length) {
            if (zero) {
                zero = false;
                return 0;
            }
            return super.read(target, offset, length);
        }
    }

    private static final class ThrowingInputStream extends InputStream {
        private final ByteArrayInputStream delegate = new ByteArrayInputStream(STYLE);
        private final RuntimeException runtimeFailure;
        private final IOException ioFailure;
        private final RuntimeException closeRuntimeFailure;
        private final IOException closeIoFailure;

        private ThrowingInputStream(Exception readFailure, Exception closeFailure) {
            runtimeFailure = readFailure instanceof RuntimeException value ? value : null;
            ioFailure = readFailure instanceof IOException value ? value : null;
            closeRuntimeFailure = closeFailure instanceof RuntimeException value ? value : null;
            closeIoFailure = closeFailure instanceof IOException value ? value : null;
        }

        @Override
        public int read(byte[] target, int offset, int length) throws IOException {
            if (runtimeFailure != null) {
                throw runtimeFailure;
            }
            if (ioFailure != null) {
                throw ioFailure;
            }
            return delegate.read(target, offset, length);
        }

        @Override
        public int read() throws IOException {
            return delegate.read();
        }

        @Override
        public void close() throws IOException {
            if (closeRuntimeFailure != null) {
                throw closeRuntimeFailure;
            }
            if (closeIoFailure != null) {
                throw closeIoFailure;
            }
        }
    }
}
