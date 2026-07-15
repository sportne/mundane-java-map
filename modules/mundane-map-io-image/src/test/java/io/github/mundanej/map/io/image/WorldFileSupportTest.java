package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorldFileSupportTest {
    @TempDir Path directory;

    @Test
    void resolvesEveryDeclaredSuffixAndRejectsMixedCaseAndAmbiguity() throws Exception {
        for (Case candidate :
                List.of(
                        new Case("png", "pngw"),
                        new Case("png", "PNGW"),
                        new Case("png", "pgw"),
                        new Case("png", "PGW"),
                        new Case("png", "wld"),
                        new Case("png", "WLD"),
                        new Case("jpg", "jpgw"),
                        new Case("jpg", "JPGW"),
                        new Case("jpg", "jgw"),
                        new Case("jpg", "JGW"),
                        new Case("jpg", "wld"),
                        new Case("jpg", "WLD"),
                        new Case("jpeg", "jpegw"),
                        new Case("jpeg", "JPEGW"),
                        new Case("jpeg", "jgw"),
                        new Case("jpeg", "JGW"),
                        new Case("jpeg", "wld"),
                        new Case("jpeg", "WLD"))) {
            Path image =
                    image(
                            "case-" + candidate.extension() + '-' + candidate.suffix(),
                            candidate.extension());
            Path world = sibling(image, candidate.suffix());
            Files.writeString(world, northUp(), StandardCharsets.US_ASCII);
            try (RasterSource source =
                    open(
                            image,
                            ImageOpenOptions.defaults()
                                    .withPlacement(ImagePlacement.worldFile()))) {
                assertEquals(
                        RasterGridPlacement.Kind.AFFINE,
                        source.metadata().gridPlacement().orElseThrow().kind());
            }
            Files.delete(world);
        }

        Path mixed = image("mixed", "png");
        Files.writeString(sibling(mixed, "PnGw"), northUp(), StandardCharsets.US_ASCII);
        assertCode("IMAGE_WORLD_FILE_MISSING", () -> openWorld(mixed));

        Path ambiguous = image("ambiguous", "png");
        Files.writeString(sibling(ambiguous, "pngw"), northUp(), StandardCharsets.US_ASCII);
        Files.writeString(sibling(ambiguous, "pgw"), northUp(), StandardCharsets.US_ASCII);
        SourceException failure =
                assertCode("IMAGE_WORLD_FILE_AMBIGUOUS", () -> openWorld(ambiguous));
        assertEquals("2", failure.terminal().context().get("candidateCount"));
        assertEquals("longLower", failure.terminal().context().get("candidate0"));
        assertEquals("shortLower", failure.terminal().context().get("candidate1"));

        Path aliases = image("aliases", "png");
        Path lower = sibling(aliases, "pngw");
        Files.writeString(lower, northUp(), StandardCharsets.US_ASCII);
        Files.createLink(sibling(aliases, "PNGW"), lower);
        try (RasterSource source = openWorld(aliases)) {
            assertTrue(source.metadata().mapBounds().isPresent());
        }
    }

    @Test
    void parsesPhysicalCoefficientOrderCentersCornersAndSignedZero() throws Exception {
        Path image = image("affine", "png");
        Files.writeString(
                sibling(image, "pgw"),
                "2\r\n3\r\n4\r\n-5\r\n100\r\n200\r\n",
                StandardCharsets.US_ASCII);
        try (RasterSource source = openWorld(image)) {
            var transform =
                    source.metadata().gridPlacement().orElseThrow().affineTransform().orElseThrow();
            assertEquals(100, transform.gridToMap(0, 0).x());
            assertEquals(200, transform.gridToMap(0, 0).y());
            assertEquals(106, transform.gridToMap(1, 1).x());
            assertEquals(198, transform.gridToMap(1, 1).y());
            assertEquals(97, source.metadata().mapBounds().orElseThrow().minX());
            assertEquals(109, source.metadata().mapBounds().orElseThrow().maxX());
        }

        Files.writeString(sibling(image, "pgw"), "1\n-0\n+0\n-1\n0\n0", StandardCharsets.US_ASCII);
        try (RasterSource source = openWorld(image)) {
            var transform =
                    source.metadata().gridPlacement().orElseThrow().affineTransform().orElseThrow();
            assertEquals(Double.doubleToLongBits(0.0), Double.doubleToLongBits(transform.d()));
            assertEquals(Double.doubleToLongBits(0.0), Double.doubleToLongBits(transform.b()));
        }
    }

    @Test
    void acceptsEveryDeclaredDecimalAndWhitespaceForm() throws Exception {
        Path image = image("decimal-grammar", "png");
        Files.writeString(
                sibling(image, "pgw"),
                "\t+1. \n .5\t\n-2e1\n3E+0\n4e-1\n-0\n",
                StandardCharsets.US_ASCII);
        try (RasterSource source = openWorld(image)) {
            var transform =
                    source.metadata().gridPlacement().orElseThrow().affineTransform().orElseThrow();
            assertEquals(1, transform.a());
            assertEquals(.5, transform.d());
            assertEquals(-20, transform.b());
            assertEquals(3, transform.e());
            assertEquals(.4, transform.c());
            assertEquals(0L, Double.doubleToRawLongBits(transform.f()));
        }
    }

    @Test
    void enforcesStrictGrammarAndStableInvalidReasons() throws Exception {
        for (Invalid invalid :
                List.of(
                        new Invalid(new byte[0], "empty"),
                        new Invalid(
                                "1\n2\n3\n4\n5".getBytes(StandardCharsets.US_ASCII), "lineCount"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n6\n7".getBytes(StandardCharsets.US_ASCII),
                                "lineCount"),
                        new Invalid(
                                "1\r2\n3\n4\n5\n6".getBytes(StandardCharsets.US_ASCII),
                                "lineCount"),
                        new Invalid(
                                "1\n2\n3\n4\n5\nNaN".getBytes(StandardCharsets.US_ASCII), "number"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n1e999".getBytes(StandardCharsets.US_ASCII),
                                "nonFinite"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n1,0".getBytes(StandardCharsets.US_ASCII), "number"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n# 6".getBytes(StandardCharsets.US_ASCII), "number"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n6 7".getBytes(StandardCharsets.US_ASCII), "number"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n0x1".getBytes(StandardCharsets.US_ASCII), "number"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n1e".getBytes(StandardCharsets.US_ASCII), "number"),
                        new Invalid(
                                "1\n2\n3\n4\n5\nInfinity".getBytes(StandardCharsets.US_ASCII),
                                "number"),
                        new Invalid(
                                "1\n2\n3\n4\n5\n\u00016".getBytes(StandardCharsets.US_ASCII),
                                "encoding"),
                        new Invalid(
                                new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf, '1'},
                                "encoding"),
                        new Invalid(
                                new byte[] {
                                    '1',
                                    '\n',
                                    '2',
                                    '\n',
                                    '3',
                                    '\n',
                                    '4',
                                    '\n',
                                    '5',
                                    '\n',
                                    (byte) 0xff
                                },
                                "encoding"))) {
            Path image = image("invalid-" + invalid.reason() + '-' + invalid.bytes().length, "png");
            Files.write(sibling(image, "pgw"), invalid.bytes());
            SourceException failure =
                    assertCode("IMAGE_WORLD_FILE_INVALID", () -> openWorld(image));
            assertEquals(invalid.reason(), failure.terminal().context().get("reason"));
        }
    }

    @Test
    void numericAndEncodingFailuresLocateTheFirstOffendingCoefficientByte() throws Exception {
        assertInvalidLocation("1\n2\n3\n4\n5\n12x", 12, "number", "F");
        assertInvalidLocation("1\n2\n3\n4\n5\n1 2", 11, "number", "F");
        assertInvalidLocation("1\n2\n3\n4\n5\n1e", 12, "number", "F");
        assertInvalidLocation("1\n2\n3\n4\n5\n\u00016", 10, "encoding", "F");
        assertInvalidLocation("1\n2\n3\n4\n5\n6\u007f", 11, "encoding", "F");
    }

    @Test
    void checksWholeAndLineLimitsAtEqualityAndPlusOne() throws Exception {
        Path image = image("limits", "png");
        byte[] exact = northUp().getBytes(StandardCharsets.US_ASCII);
        Files.write(sibling(image, "pgw"), exact);
        ImageSourceLimits limits =
                ImageSourceLimits.defaults().withMaximumWorldFileBytes(exact.length);
        try (RasterSource below =
                open(
                        image,
                        ImageOpenOptions.defaults()
                                .withImageLimits(limits.withMaximumWorldFileBytes(exact.length + 1))
                                .withPlacement(ImagePlacement.worldFile()))) {
            assertTrue(below.metadata().mapBounds().isPresent());
        }
        try (RasterSource ignored =
                open(
                        image,
                        ImageOpenOptions.defaults()
                                .withImageLimits(limits)
                                .withPlacement(ImagePlacement.worldFile()))) {
            assertTrue(ignored.metadata().mapBounds().isPresent());
        }
        SourceException bytes =
                assertCode(
                        "SOURCE_LIMIT_EXCEEDED",
                        () ->
                                open(
                                        image,
                                        ImageOpenOptions.defaults()
                                                .withImageLimits(
                                                        limits.withMaximumWorldFileBytes(
                                                                exact.length - 1))
                                                .withPlacement(ImagePlacement.worldFile())));
        assertEquals("worldFileBytes", bytes.terminal().context().get("limit"));
        assertEquals(
                exact.length - 1,
                bytes.terminal().location().orElseThrow().byteOffset().orElseThrow());

        Files.writeString(sibling(image, "pgw"), "  1  \n2\n3\n4\n5\n6", StandardCharsets.US_ASCII);
        try (RasterSource ignored =
                open(
                        image,
                        ImageOpenOptions.defaults()
                                .withImageLimits(
                                        ImageSourceLimits.defaults()
                                                .withMaximumWorldFileLineBytes(5))
                                .withPlacement(ImagePlacement.worldFile()))) {
            assertTrue(ignored.metadata().mapBounds().isPresent());
        }
        try (RasterSource below =
                open(
                        image,
                        ImageOpenOptions.defaults()
                                .withImageLimits(
                                        ImageSourceLimits.defaults()
                                                .withMaximumWorldFileLineBytes(6))
                                .withPlacement(ImagePlacement.worldFile()))) {
            assertTrue(below.metadata().mapBounds().isPresent());
        }
        SourceException line =
                assertCode(
                        "SOURCE_LIMIT_EXCEEDED",
                        () ->
                                open(
                                        image,
                                        ImageOpenOptions.defaults()
                                                .withImageLimits(
                                                        ImageSourceLimits.defaults()
                                                                .withMaximumWorldFileLineBytes(4))
                                                .withPlacement(ImagePlacement.worldFile())));
        assertEquals("worldFileLineBytes", line.terminal().context().get("limit"));
        assertEquals(4, line.terminal().location().orElseThrow().byteOffset().orElseThrow());

        Files.writeString(
                sibling(image, "pgw"),
                "  1  \r\n2\r\n3\r\n4\r\n5\r\n6\r\n",
                StandardCharsets.US_ASCII);
        try (RasterSource crlf =
                open(
                        image,
                        ImageOpenOptions.defaults()
                                .withImageLimits(
                                        ImageSourceLimits.defaults()
                                                .withMaximumWorldFileLineBytes(5))
                                .withPlacement(ImagePlacement.worldFile()))) {
            assertTrue(crlf.metadata().mapBounds().isPresent());
        }
    }

    @Test
    void worldFileIsExplicitAndPrecedesDecoderLookupAndPublishedSourceSnapshotsIt()
            throws Exception {
        Path image = image("explicit", "png");
        try (RasterSource normalized = open(image, ImageOpenOptions.defaults())) {
            assertTrue(normalized.metadata().gridPlacement().isEmpty());
        }
        assertCode(
                "IMAGE_WORLD_FILE_MISSING",
                () ->
                        RasterImages.open(
                                image,
                                identity(),
                                ImageOpenOptions.defaults()
                                        .withPlacement(ImagePlacement.worldFile()),
                                EncodedRasterDecoderRegistry.builder().build()));

        Path world = sibling(image, "pgw");
        Files.writeString(world, northUp(), StandardCharsets.US_ASCII);
        RasterSource source = openWorld(image);
        var placement = source.metadata().gridPlacement().orElseThrow();
        Files.writeString(world, "9\n0\n0\n-9\n900\n900", StandardCharsets.US_ASCII);
        Files.delete(world);
        assertEquals(placement, source.metadata().gridPlacement().orElseThrow());
        source.close();
    }

    @Test
    void singularTransformAndCancellationFailStably() throws Exception {
        Path image = image("singular", "png");
        Files.writeString(sibling(image, "pgw"), "1\n2\n2\n4\n0\n0", StandardCharsets.US_ASCII);
        SourceException singular =
                assertCode("IMAGE_WORLD_FILE_TRANSFORM_INVALID", () -> openWorld(image));
        assertEquals("singular", singular.terminal().context().get("reason"));

        assertCode(
                "SOURCE_CANCELLED",
                () ->
                        RasterImages.open(
                                image,
                                identity(),
                                ImageOpenOptions.defaults()
                                        .withPlacement(ImagePlacement.worldFile()),
                                registry(),
                                () -> true));
    }

    @Test
    void mapsEveryStructuredTransformFailureReasonWithoutNumericContext() throws Exception {
        assertTransformReason(image("reason-singular", "png"), "1\n2\n2\n4\n0\n0", "singular");
        assertTransformReason(
                image("reason-inverse", "png"),
                "4.9e-324\n0\n0\n4.9e-324\n0\n0",
                "inverseNonFinite");
        assertTransformReason(
                image("reason-corner", "png"),
                "1.7976931348623157e308\n0\n0\n1\n0\n0",
                "cornerNonFinite");
        assertTransformReason(
                image("reason-envelope-finite", "png", 1, 1),
                """
                1.7976931348623157e308
                1.7976931348623157e308
                1.7976931348623157e308
                -1.7976931348623157e308
                0
                0
                """
                        .stripTrailing(),
                "envelopeNonFinite");
        assertTransformReason(
                image("reason-envelope-positive", "png", 1, 1),
                "1\n0\n0\n-1\n1e16\n1e16",
                "envelopeNonPositive");
    }

    @Test
    void finiteCandidateSnapshotDoesNotRescanAndPreOpenBytesAreAuthoritative() throws Exception {
        Path image = image("snapshot", "png");
        Path longCandidate = sibling(image, "pngw");
        Path laterCandidate = sibling(image, "pgw");
        Files.writeString(longCandidate, northUp(), StandardCharsets.US_ASCII);
        TrackingChannel imageChannel = new TrackingChannel(pngHeader(2, 2), false);
        TrackingChannel replacement =
                new TrackingChannel(
                        "1\n0\n0\n-1\n50\n60".getBytes(StandardCharsets.US_ASCII), false);
        try (RasterSource source =
                RasterImages.open(
                        image,
                        identity(),
                        ImageOpenOptions.defaults().withPlacement(ImagePlacement.worldFile()),
                        registry(),
                        io.github.mundanej.map.api.CancellationToken.none(),
                        path -> {
                            if (path.equals(image)) {
                                return imageChannel;
                            }
                            Files.writeString(laterCandidate, northUp(), StandardCharsets.US_ASCII);
                            return replacement;
                        })) {
            assertEquals(
                    50,
                    source.metadata()
                            .gridPlacement()
                            .orElseThrow()
                            .affineTransform()
                            .orElseThrow()
                            .c());
        }
        assertTrue(replacement.closed);

        TrackingChannel removalImage = new TrackingChannel(pngHeader(2, 2), false);
        SourceException removed =
                assertCode(
                        "IMAGE_WORLD_FILE_AMBIGUOUS",
                        () ->
                                RasterImages.open(
                                        image,
                                        identity(),
                                        ImageOpenOptions.defaults()
                                                .withPlacement(ImagePlacement.worldFile()),
                                        registry(),
                                        io.github.mundanej.map.api.CancellationToken.none(),
                                        ignored -> removalImage));
        assertEquals("2", removed.terminal().context().get("candidateCount"));
        Files.delete(laterCandidate);
        assertTrue(removalImage.closed);
    }

    @Test
    void malformedWorldFileRemainsPrimaryAndBothCleanupFailuresAreSuppressed() throws Exception {
        Path image = image("cleanup", "png");
        Path world = sibling(image, "pgw");
        Files.writeString(world, northUp(), StandardCharsets.US_ASCII);
        TrackingChannel imageChannel = new TrackingChannel(pngHeader(2, 2), true);
        TrackingChannel malformed =
                new TrackingChannel("1\n2\n3".getBytes(StandardCharsets.US_ASCII), true);

        SourceException failure =
                assertCode(
                        "IMAGE_WORLD_FILE_INVALID",
                        () ->
                                RasterImages.open(
                                        image,
                                        identity(),
                                        ImageOpenOptions.defaults()
                                                .withPlacement(ImagePlacement.worldFile()),
                                        registry(),
                                        io.github.mundanej.map.api.CancellationToken.none(),
                                        path -> path.equals(image) ? imageChannel : malformed));

        assertEquals("lineCount", failure.terminal().context().get("reason"));
        assertEquals(2, failure.getSuppressed().length);
        assertTrue(imageChannel.closed);
        assertTrue(malformed.closed);
    }

    @Test
    void sameSizeMidReadMixtureIsParsedWithoutAFalseMutationClaim() throws Exception {
        Path image = image("mixed-read", "png");
        Path world = sibling(image, "pgw");
        byte[] first = "1\n0\n0\n-1\n10\n20".getBytes(StandardCharsets.US_ASCII);
        byte[] second = "1\n0\n0\n-1\n30\n40".getBytes(StandardCharsets.US_ASCII);
        Files.write(world, first);
        TrackingChannel imageChannel = new TrackingChannel(pngHeader(2, 2), false);
        SwitchingChannel switching = new SwitchingChannel(first, second, 10);
        try (RasterSource source =
                RasterImages.open(
                        image,
                        identity(),
                        ImageOpenOptions.defaults().withPlacement(ImagePlacement.worldFile()),
                        registry(),
                        io.github.mundanej.map.api.CancellationToken.none(),
                        path -> path.equals(image) ? imageChannel : switching)) {
            assertTrue(source.metadata().mapBounds().isPresent());
        }
        assertTrue(switching.wasClosed());
    }

    @Test
    void mapsEveryWorldFileIoPhaseAndDoesNotFallbackAfterSelection() throws Exception {
        Path image = image("io-phases", "png");
        Path selected = sibling(image, "pgw");
        Files.writeString(selected, northUp(), StandardCharsets.US_ASCII);

        assertIoOperation(
                image,
                "probe",
                new WorldFileSupport.Access() {
                    @Override
                    public boolean exists(Path path) throws IOException {
                        throw new IOException("probe");
                    }

                    @Override
                    public boolean isSameFile(Path first, Path second) {
                        return false;
                    }

                    @Override
                    public ImageChannel open(Path path) {
                        throw new AssertionError("unreachable");
                    }
                });

        assertIoOperation(
                image,
                "open",
                accessWithChannel(
                        selected,
                        () -> {
                            throw new IOException("open");
                        }));
        assertIoOperation(
                image,
                "size",
                accessWithChannel(selected, () -> new FailingChannel(FailurePhase.SIZE)));
        assertIoOperation(
                image,
                "read",
                accessWithChannel(selected, () -> new FailingChannel(FailurePhase.READ)));
        assertIoOperation(
                image,
                "close",
                accessWithChannel(
                        selected,
                        () ->
                                new TrackingChannel(
                                        northUp().getBytes(StandardCharsets.US_ASCII), true)));

        Path alias = sibling(image, "PGW");
        Files.createLink(alias, selected);
        assertIoOperation(
                image,
                "identity",
                new WorldFileSupport.Access() {
                    @Override
                    public boolean exists(Path path) {
                        return path.equals(selected) || path.equals(alias);
                    }

                    @Override
                    public boolean isSameFile(Path first, Path second) throws IOException {
                        throw new IOException("identity");
                    }

                    @Override
                    public ImageChannel open(Path path) {
                        throw new AssertionError("unreachable");
                    }
                });
        Files.delete(alias);

        AtomicInteger opens = new AtomicInteger();
        Path fallback = sibling(image, "wld");
        WorldFileSupport.Access removal =
                new WorldFileSupport.Access() {
                    @Override
                    public boolean exists(Path path) {
                        return path.equals(selected);
                    }

                    @Override
                    public boolean isSameFile(Path first, Path second) {
                        return false;
                    }

                    @Override
                    public ImageChannel open(Path path) throws IOException {
                        opens.incrementAndGet();
                        Files.deleteIfExists(selected);
                        Files.writeString(fallback, northUp(), StandardCharsets.US_ASCII);
                        throw new IOException("removed");
                    }
                };
        assertIoOperation(image, "open", removal);
        assertEquals(1, opens.get());
    }

    @Test
    void distinguishesTruncationAndSizeChangeAndChecksCancellationAfterIdentity() throws Exception {
        Path image = image("mutation-phases", "png");
        Path selected = sibling(image, "pgw");
        byte[] world = northUp().getBytes(StandardCharsets.US_ASCII);
        Files.write(selected, world);
        SourceException truncated =
                openFailureWithWorldAccess(
                        image,
                        accessWithChannel(selected, () -> new ChangingSizeChannel(world, true)));
        assertEquals("IMAGE_WORLD_FILE_INVALID", truncated.terminal().code());
        assertEquals("truncated", truncated.terminal().context().get("reason"));
        SourceException changed =
                openFailureWithWorldAccess(
                        image,
                        accessWithChannel(selected, () -> new ChangingSizeChannel(world, false)));
        assertEquals("IMAGE_WORLD_FILE_INVALID", changed.terminal().code());
        assertEquals("sizeChanged", changed.terminal().context().get("reason"));

        Path alias = sibling(image, "PGW");
        Files.createLink(alias, selected);
        AtomicBoolean cancelled = new AtomicBoolean();
        WorldFileSupport.Access identityCancellation =
                new WorldFileSupport.Access() {
                    @Override
                    public boolean exists(Path path) {
                        return path.equals(selected) || path.equals(alias);
                    }

                    @Override
                    public boolean isSameFile(Path first, Path second) {
                        cancelled.set(true);
                        return true;
                    }

                    @Override
                    public ImageChannel open(Path path) {
                        throw new AssertionError("Cancellation must win after identity");
                    }
                };
        TrackingChannel imageChannel = new TrackingChannel(pngHeader(2, 2), false);
        SourceException cancellation =
                assertCode(
                        "SOURCE_CANCELLED",
                        () ->
                                RasterImages.open(
                                        image,
                                        identity(),
                                        ImageOpenOptions.defaults()
                                                .withPlacement(ImagePlacement.worldFile()),
                                        registry(),
                                        cancelled::get,
                                        ignored -> imageChannel,
                                        identityCancellation));
        assertEquals("identity", cancellation.terminal().context().get("operation"));
        assertTrue(imageChannel.wasClosed());
    }

    @Test
    void cancellationIsObservedAfterProbeOpenSizeReadAndClosePhases() throws Exception {
        Path image = image("phase-cancellation", "png");
        Path selected = sibling(image, "pgw");
        byte[] world = northUp().getBytes(StandardCharsets.US_ASCII);
        Files.write(selected, world);

        AtomicBoolean probe = new AtomicBoolean();
        assertCancellation(
                image,
                "probe",
                probe,
                new WorldFileSupport.Access() {
                    @Override
                    public boolean exists(Path path) {
                        if (path.equals(selected)) {
                            probe.set(true);
                            return true;
                        }
                        return false;
                    }

                    @Override
                    public boolean isSameFile(Path first, Path second) {
                        return false;
                    }

                    @Override
                    public ImageChannel open(Path path) {
                        throw new AssertionError("unreachable");
                    }
                });

        AtomicBoolean opened = new AtomicBoolean();
        assertCancellation(
                image,
                "open",
                opened,
                accessWithChannel(
                        selected,
                        () -> {
                            opened.set(true);
                            return new TrackingChannel(world, false);
                        }));

        AtomicBoolean sized = new AtomicBoolean();
        assertCancellation(
                image,
                "size",
                sized,
                accessWithChannel(
                        selected,
                        () ->
                                new TrackingChannel(world, false) {
                                    @Override
                                    public long size() {
                                        sized.set(true);
                                        return world.length;
                                    }
                                }));

        AtomicBoolean read = new AtomicBoolean();
        assertCancellation(
                image,
                "read",
                read,
                accessWithChannel(
                        selected,
                        () ->
                                new TrackingChannel(world, false) {
                                    @Override
                                    public int read(ByteBuffer target, long position)
                                            throws IOException {
                                        int count = super.read(target, position);
                                        read.set(true);
                                        return count;
                                    }
                                }));

        AtomicBoolean closed = new AtomicBoolean();
        assertCancellation(
                image,
                "close",
                closed,
                accessWithChannel(
                        selected,
                        () ->
                                new TrackingChannel(world, false) {
                                    @Override
                                    public void close() throws IOException {
                                        super.close();
                                        closed.set(true);
                                    }
                                }));
    }

    private Path image(String stem, String extension) throws Exception {
        return image(stem, extension, 2, 2);
    }

    private Path image(String stem, String extension, int width, int height) throws Exception {
        Path path = directory.resolve(stem + '.' + extension);
        Files.write(
                path,
                extension.equals("png") ? pngHeader(width, height) : jpegHeader(width, height));
        return path;
    }

    private static Path sibling(Path image, String suffix) {
        String name = Objects.requireNonNull(image.getFileName()).toString();
        String stem = name.substring(0, name.lastIndexOf('.'));
        return image.resolveSibling(stem + '.' + suffix);
    }

    private RasterSource openWorld(Path image) {
        return open(image, ImageOpenOptions.defaults().withPlacement(ImagePlacement.worldFile()));
    }

    private static RasterSource open(Path image, ImageOpenOptions options) {
        return RasterImages.open(image, identity(), options, registry());
    }

    private static EncodedRasterDecoderRegistry registry() {
        io.github.mundanej.map.api.EncodedRasterDecoder decoder =
                (input, context) -> {
                    context.claimReservedIntermediateBytes(context.encodedByteLength());
                    context.claimReservedIntermediateBytes(8L * context.width() * context.height());
                    context.claimReservedIntermediateBytes(
                            4L * context.outputWidth() * context.outputHeight());
                    return RgbaPixelBuffer.builder(context.outputWidth(), context.outputHeight())
                            .build();
                };
        return EncodedRasterDecoderRegistry.builder()
                .register(EncodedRasterFormat.PNG, decoder)
                .register(EncodedRasterFormat.JPEG, decoder)
                .build();
    }

    private static SourceIdentity identity() {
        return new SourceIdentity("world-image", "World image");
    }

    private static SourceException assertCode(
            String code, org.junit.jupiter.api.function.Executable executable) {
        SourceException failure = assertThrows(SourceException.class, executable);
        assertEquals(code, failure.terminal().code());
        return failure;
    }

    private void assertIoOperation(Path image, String operation, WorldFileSupport.Access access) {
        SourceException failure = openFailureWithWorldAccess(image, access);
        assertEquals("IMAGE_IO_FAILED", failure.terminal().code());
        assertEquals(operation, failure.terminal().context().get("operation"));
    }

    private SourceException openFailureWithWorldAccess(Path image, WorldFileSupport.Access access) {
        TrackingChannel imageChannel = new TrackingChannel(pngHeader(2, 2), false);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        image,
                                        identity(),
                                        ImageOpenOptions.defaults()
                                                .withPlacement(ImagePlacement.worldFile()),
                                        registry(),
                                        io.github.mundanej.map.api.CancellationToken.none(),
                                        ignored -> imageChannel,
                                        access));
        assertTrue(imageChannel.wasClosed());
        return failure;
    }

    private void assertCancellation(
            Path image, String operation, AtomicBoolean cancelled, WorldFileSupport.Access access) {
        TrackingChannel imageChannel = new TrackingChannel(pngHeader(2, 2), false);
        SourceException failure =
                assertCode(
                        "SOURCE_CANCELLED",
                        () ->
                                RasterImages.open(
                                        image,
                                        identity(),
                                        ImageOpenOptions.defaults()
                                                .withPlacement(ImagePlacement.worldFile()),
                                        registry(),
                                        cancelled::get,
                                        ignored -> imageChannel,
                                        access));
        assertEquals(operation, failure.terminal().context().get("operation"));
        assertTrue(imageChannel.wasClosed());
    }

    private static WorldFileSupport.Access accessWithChannel(
            Path selected, ChannelFactory channelFactory) {
        return new WorldFileSupport.Access() {
            @Override
            public boolean exists(Path path) {
                return path.equals(selected);
            }

            @Override
            public boolean isSameFile(Path first, Path second) {
                return false;
            }

            @Override
            public ImageChannel open(Path path) throws IOException {
                return channelFactory.open();
            }
        };
    }

    private void assertInvalidLocation(
            String content, long offset, String reason, String coefficient) throws Exception {
        Path image = image("offset-" + offset + '-' + reason, "png");
        Files.writeString(sibling(image, "pgw"), content, StandardCharsets.US_ASCII);
        SourceException failure = assertCode("IMAGE_WORLD_FILE_INVALID", () -> openWorld(image));
        assertEquals(
                offset, failure.terminal().location().orElseThrow().byteOffset().orElseThrow());
        assertEquals(
                Map.of("reason", reason, "coefficient", coefficient), failure.terminal().context());
    }

    private void assertTransformReason(Path image, String content, String reason) throws Exception {
        Files.writeString(sibling(image, "pgw"), content, StandardCharsets.US_ASCII);
        SourceException failure =
                assertCode("IMAGE_WORLD_FILE_TRANSFORM_INVALID", () -> openWorld(image));
        assertEquals(Map.of("reason", reason), failure.terminal().context());
        assertTrue(failure.terminal().location().orElseThrow().byteOffset().isEmpty());
    }

    private static String northUp() {
        return "1\n0\n0\n-1\n0\n0";
    }

    private static byte[] pngHeader(int width, int height) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.writeBytes(new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a});
        ByteBuffer ihdr = ByteBuffer.allocate(13).order(ByteOrder.BIG_ENDIAN);
        ihdr.putInt(width).putInt(height).put((byte) 8).put((byte) 6);
        ihdr.put((byte) 0).put((byte) 0).put((byte) 0);
        writePngChunk(bytes, "IHDR", ihdr.array());
        byte[] filtered = new byte[(width * 4 + 1) * height];
        java.util.zip.Deflater deflater = new java.util.zip.Deflater();
        deflater.setInput(filtered);
        deflater.finish();
        byte[] compressed = new byte[filtered.length + 64];
        int count = deflater.deflate(compressed);
        deflater.end();
        writePngChunk(bytes, "IDAT", java.util.Arrays.copyOf(compressed, count));
        writePngChunk(bytes, "IEND", new byte[0]);
        return bytes.toByteArray();
    }

    private static void writePngChunk(ByteArrayOutputStream target, String type, byte[] payload) {
        ByteBuffer chunk = ByteBuffer.allocate(12 + payload.length).order(ByteOrder.BIG_ENDIAN);
        chunk.putInt(payload.length);
        byte[] typeBytes = type.getBytes(StandardCharsets.US_ASCII);
        chunk.put(typeBytes).put(payload);
        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(payload);
        chunk.putInt((int) crc.getValue());
        target.writeBytes(chunk.array());
    }

    private static byte[] jpegHeader(int width, int height) {
        return new byte[] {
            (byte) 0xff,
            (byte) 0xd8,
            (byte) 0xff,
            (byte) 0xc0,
            0,
            11,
            8,
            (byte) (height >>> 8),
            (byte) height,
            (byte) (width >>> 8),
            (byte) width,
            1,
            1,
            0x11,
            0,
            (byte) 0xff,
            (byte) 0xda,
            0,
            8,
            1,
            1,
            0,
            0,
            63,
            0,
            1,
            2,
            (byte) 0xff,
            (byte) 0xd9
        };
    }

    private record Case(String extension, String suffix) {}

    private static final class Invalid {
        private final byte[] bytes;
        private final String reason;

        private Invalid(byte[] bytes, String reason) {
            this.bytes = bytes;
            this.reason = reason;
        }

        private byte[] bytes() {
            return bytes;
        }

        private String reason() {
            return reason;
        }
    }

    private static class TrackingChannel implements ImageChannel {
        private final byte[] bytes;
        private final boolean failClose;
        private boolean closed;

        private TrackingChannel(byte[] bytes, boolean failClose) {
            this.bytes = bytes.clone();
            this.failClose = failClose;
        }

        @Override
        public long size() throws IOException {
            return bytes.length;
        }

        @Override
        public int read(ByteBuffer target, long position) throws IOException {
            if (position >= bytes.length) {
                return -1;
            }
            int count = Math.min(target.remaining(), bytes.length - Math.toIntExact(position));
            target.put(bytes, Math.toIntExact(position), count);
            return count;
        }

        @Override
        public void close() throws IOException {
            closed = true;
            if (failClose) {
                throw new IOException("close failed");
            }
        }

        final boolean wasClosed() {
            return closed;
        }
    }

    private static final class SwitchingChannel extends TrackingChannel {
        private final byte[] first;
        private final byte[] second;
        private final int switchPosition;

        private SwitchingChannel(byte[] first, byte[] second, int switchPosition) {
            super(first, false);
            this.first = first.clone();
            this.second = second.clone();
            this.switchPosition = switchPosition;
        }

        @Override
        public int read(ByteBuffer target, long position) {
            if (position >= first.length) {
                return -1;
            }
            byte[] source = position < switchPosition ? first : second;
            int boundary = position < switchPosition ? switchPosition : source.length;
            int count =
                    Math.min(
                            target.remaining(),
                            Math.min(
                                    boundary - Math.toIntExact(position),
                                    source.length - Math.toIntExact(position)));
            target.put(source, Math.toIntExact(position), count);
            return count;
        }
    }

    private static final class FailingChannel extends TrackingChannel {
        private final FailurePhase failure;

        private FailingChannel(FailurePhase failure) {
            super(northUp().getBytes(StandardCharsets.US_ASCII), false);
            this.failure = failure;
        }

        @Override
        public long size() throws IOException {
            if (failure == FailurePhase.SIZE) {
                throw new IOException("size");
            }
            return super.size();
        }

        @Override
        public int read(ByteBuffer target, long position) throws IOException {
            if (failure == FailurePhase.READ) {
                throw new IOException("read");
            }
            return super.read(target, position);
        }
    }

    private static final class ChangingSizeChannel extends TrackingChannel {
        private final int length;
        private final boolean truncated;
        private int sizeCalls;

        private ChangingSizeChannel(byte[] bytes, boolean truncated) {
            super(bytes, false);
            length = bytes.length;
            this.truncated = truncated;
        }

        @Override
        public long size() {
            sizeCalls++;
            if (truncated) {
                return length + 1L;
            }
            return sizeCalls == 1 ? length : length + 1L;
        }
    }

    @FunctionalInterface
    private interface ChannelFactory {
        ImageChannel open() throws IOException;
    }

    private enum FailurePhase {
        SIZE,
        READ
    }
}
