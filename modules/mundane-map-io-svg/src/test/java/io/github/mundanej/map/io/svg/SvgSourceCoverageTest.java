package io.github.mundanej.map.io.svg;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.MarkerPlacement;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SvgSourceCoverageTest {
    private static final SourceIdentity ID = new SourceIdentity("coverage", "coverage");
    private static final MarkerPlacement PLACEMENT = MarkerPlacement.centeredScreen(16);

    @TempDir Path temporaryDirectory;

    @Test
    void byteSinkEncodesEveryUtf8WidthAndReportsHardFailures() {
        SvgByteSink sink = new SvgByteSink(32, 8_224, 0, CancellationToken.none());
        sink.begin();
        sink.append("A¢€😀");
        sink.appendAscii('!');
        assertArrayEquals("A¢€😀!".getBytes(StandardCharsets.UTF_8), sink.finish());

        SvgByteSink nonAscii = new SvgByteSink(1, 8_193, 0, CancellationToken.none());
        assertThrows(IllegalArgumentException.class, () -> nonAscii.appendAscii('é'));

        SvgExportException output =
                assertThrows(
                        SvgExportException.class,
                        () -> new SvgByteSink(1, 8_193, 0, CancellationToken.none()).append("ab"));
        assertEquals("outputBytes", output.problem().context().get("limit"));

        SvgExportException owned =
                assertThrows(
                        SvgExportException.class,
                        () -> new SvgByteSink(1, 0, 1, CancellationToken.none()).begin());
        assertEquals("ownedBytes", owned.problem().context().get("limit"));

        SvgExportException overflow =
                assertThrows(
                        SvgExportException.class,
                        () ->
                                new SvgByteSink(
                                                1,
                                                Long.MAX_VALUE - 1,
                                                Long.MAX_VALUE - 1,
                                                CancellationToken.none())
                                        .charge(2));
        assertEquals(Long.toString(Long.MAX_VALUE), overflow.problem().context().get("requested"));

        SvgExportException cancelled =
                assertThrows(
                        SvgExportException.class,
                        () -> new SvgByteSink(1, 1, 0, () -> true).append("a"));
        assertEquals("SVG_EXPORT_CANCELLED", cancelled.problem().code());
    }

    @Test
    void exportLimitCopiesAndValidationCoverEveryField() {
        SvgExportLimits defaults = SvgExportLimits.defaults();
        assertEquals(1, defaults.withMaximumElements(1).maximumElements());
        assertEquals(1, defaults.withMaximumPathCommands(1).maximumPathCommands());
        assertEquals(1, defaults.withMaximumHatchSegments(1).maximumHatchSegments());
        assertEquals(1, defaults.withMaximumOutputBytes(1).maximumOutputBytes());
        assertEquals(1, defaults.withMaximumOwnedBytes(1).maximumOwnedBytes());

        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumElements(0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertNotNull(
                                defaults.withMaximumPathCommands(
                                        SvgExportLimits.PATH_COMMANDS_HARD_MAXIMUM + 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumHatchSegments(0)));
        assertThrows(
                IllegalArgumentException.class,
                () -> assertNotNull(defaults.withMaximumOutputBytes(0)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        assertNotNull(
                                defaults.withMaximumOwnedBytes(
                                        SvgExportLimits.OWNED_BYTES_HARD_MAXIMUM + 1)));
    }

    @Test
    void localReadAndReaderPreflightCoverBoundaries() throws Exception {
        byte[] document =
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><rect width=\"1\" height=\"1\"/></svg>"
                        .getBytes(StandardCharsets.UTF_8);
        Path file = temporaryDirectory.resolve("marker.svg");
        Files.write(file, document);
        assertEquals(
                io.github.mundanej.map.api.SymbolRole.MARKER,
                SvgSymbols.read(ID, file, PLACEMENT).role());

        SourceException directory =
                assertThrows(
                        SourceException.class,
                        () -> SvgSymbols.read(ID, temporaryDirectory, PLACEMENT));
        assertEquals("other", directory.terminal().context().get("reason"));

        SourceException fileLimit =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.read(
                                        ID,
                                        file,
                                        PLACEMENT,
                                        SvgImportLimits.defaults()
                                                .withMaximumInputBytes(document.length - 1),
                                        CancellationToken.none()));
        assertEquals("inputBytes", fileLimit.terminal().context().get("limit"));

        SourceException negative =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.readOpened(
                                        ID,
                                        new ByteArrayInputStream(document),
                                        -1,
                                        PLACEMENT,
                                        SvgImportLimits.defaults(),
                                        CancellationToken.none()));
        assertEquals("read", negative.terminal().context().get("operation"));

        SourceException capturedLimit =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.readOpened(
                                        ID,
                                        new ByteArrayInputStream(document),
                                        document.length,
                                        PLACEMENT,
                                        SvgImportLimits.defaults()
                                                .withMaximumInputBytes(document.length - 1),
                                        CancellationToken.none()));
        assertEquals("inputBytes", capturedLimit.terminal().context().get("limit"));

        SourceException ownedLimit =
                assertThrows(
                        SourceException.class,
                        () ->
                                SvgSymbols.parse(
                                        ID,
                                        document,
                                        PLACEMENT,
                                        SvgImportLimits.defaults()
                                                .withMaximumOwnedBytes(document.length * 3L + 255L),
                                        CancellationToken.none()));
        assertEquals("ownedBytes", ownedLimit.terminal().context().get("limit"));
    }
}
