package io.github.mundanej.map.io.image;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.mundanej.map.api.EncodedRasterDecoderRegistry;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Public-facade evidence for the complete bounded Level 1 container profile. */
class RasterImagesHostileMatrixTest {
    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
    };

    @TempDir Path directory;

    private final AtomicInteger sequence = new AtomicInteger();

    @Test
    void exercisesPngPhysicalChunkPaletteAndOrderingMatrixThroughPublicFacade() throws Exception {
        byte[] indexed =
                png(
                        1,
                        1,
                        1,
                        3,
                        0,
                        chunk("PLTE", new byte[] {0, 0, 0, (byte) 255, 0, 0}),
                        chunk("tRNS", new byte[] {0, (byte) 255}),
                        chunk("IDAT", deflate(new byte[] {0, 0})),
                        chunk("IEND", new byte[0]));
        assertOpens("indexed-palette", indexed, EncodedRasterFormat.PNG);

        assertFailure(
                "short-signature",
                new byte[] {(byte) 0x89},
                EncodedRasterFormat.PNG,
                "IMAGE_HEADER_INVALID",
                "field",
                "signature");

        byte[] invalidLength = pngRgba(1, 1, new byte[] {0, 0, 0, 0, 0});
        putInt(invalidLength, 33, Integer.MAX_VALUE);
        assertFailure(
                "chunk-length",
                invalidLength,
                EncodedRasterFormat.PNG,
                "IMAGE_CONTAINER_INVALID",
                "reason",
                "chunkLength");

        byte[] invalidType = pngRgba(1, 1, new byte[] {0, 0, 0, 0, 0});
        invalidType[39] = 'a';
        assertFailure(
                "chunk-type",
                invalidType,
                EncodedRasterFormat.PNG,
                "IMAGE_CONTAINER_INVALID",
                "reason",
                "chunkType");

        byte[] invalidCrc = pngRgba(1, 1, new byte[] {0, 0, 0, 0, 0});
        invalidCrc[invalidCrc.length - 5] ^= 1;
        assertFailure(
                "chunk-crc",
                invalidCrc,
                EncodedRasterFormat.PNG,
                "IMAGE_CONTAINER_INVALID",
                "reason",
                "chunkCrc");

        assertPngReason(
                "duplicate-ihdr",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("IHDR", ihdr(1, 1, 8, 6, 0)),
                        chunk("IDAT", deflate(new byte[] {0, 0, 0, 0, 0})),
                        chunk("IEND", new byte[0])),
                "chunkOrder");
        assertPngReason(
                "palette-on-gray",
                png(
                        1,
                        1,
                        8,
                        0,
                        0,
                        chunk("PLTE", new byte[] {0, 0, 0}),
                        chunk("IDAT", deflate(new byte[] {0, 0})),
                        chunk("IEND", new byte[0])),
                "palette");
        assertPngReason(
                "transparency-before-palette",
                png(
                        1,
                        1,
                        1,
                        3,
                        0,
                        chunk("tRNS", new byte[] {0}),
                        chunk("PLTE", new byte[] {0, 0, 0}),
                        chunk("IDAT", deflate(new byte[] {0, 0})),
                        chunk("IEND", new byte[0])),
                "palette");

        byte[] compressed = deflate(new byte[] {0, 0, 0, 0, 0});
        int split = compressed.length / 2;
        assertPngReason(
                "non-contiguous-idat",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("IDAT", Arrays.copyOf(compressed, split)),
                        chunk("aaAa", new byte[0]),
                        chunk("IDAT", Arrays.copyOfRange(compressed, split, compressed.length)),
                        chunk("IEND", new byte[0])),
                "chunkOrder");
        assertPngReason(
                "missing-iend", png(1, 1, 8, 6, 0, chunk("IDAT", compressed)), "missingEnd");
        assertPngReason(
                "trailing-after-iend",
                concatenate(pngRgba(1, 1, new byte[] {0, 0, 0, 0, 0}), new byte[] {1}),
                "trailingData");
    }

    @Test
    void exercisesPngInflationAdam7AnimationAndLimitsThroughPublicFacade() throws Exception {
        assertPngReason("invalid-filter", pngRgba(1, 1, new byte[] {5, 0, 0, 0, 0}), "filter");
        assertPngReason(
                "preset-dictionary",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("IDAT", deflateWithDictionary(new byte[] {0, 0, 0, 0, 0})),
                        chunk("IEND", new byte[0])),
                "dictionary");
        assertPngReason(
                "corrupt-compressed-stream",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("IDAT", new byte[] {0x78, 1, 2, 3}),
                        chunk("IEND", new byte[0])),
                "compressedData");
        assertPngReason(
                "second-zlib-member",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk(
                                "IDAT",
                                concatenate(
                                        deflate(new byte[] {0, 0, 0, 0, 0}),
                                        deflate(new byte[] {0, 0, 0, 0, 0}))),
                        chunk("IEND", new byte[0])),
                "compressedData");
        assertPngReason("decoded-short", pngRgba(1, 1, new byte[] {0, 0, 0, 0}), "decodedLength");
        assertPngReason(
                "decoded-long", pngRgba(1, 1, new byte[] {0, 0, 0, 0, 0, 0}), "decodedLength");

        byte[] adam7 = pngRgba(8, 8, adam7FilteredRgba(8, 8), 1);
        assertOpens("adam7", adam7, EncodedRasterFormat.PNG);
        byte[] shortAdam7 = pngRgba(8, 8, Arrays.copyOf(adam7FilteredRgba(8, 8), 270), 1);
        assertPngReason("adam7-short", shortAdam7, "decodedLength");

        assertFailure(
                "apng",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("acTL", new byte[8]),
                        chunk("IDAT", deflate(new byte[] {0, 0, 0, 0, 0})),
                        chunk("IEND", new byte[0])),
                EncodedRasterFormat.PNG,
                "IMAGE_PROFILE_UNSUPPORTED",
                "field",
                "animation");

        byte[] ordinary = pngRgba(1, 1, new byte[] {0, 0, 0, 0, 0});
        ImageSourceLimits exact =
                ImageSourceLimits.defaults()
                        .withMaximumEncodedBytes(ordinary.length)
                        .withMaximumContainerElements(3)
                        .withMaximumInflatedRasterBytes(5);
        assertOpens("png-limit-equality", ordinary, EncodedRasterFormat.PNG, exact);
        assertLimit(
                "png-encoded-limit",
                ordinary,
                EncodedRasterFormat.PNG,
                exact.withMaximumEncodedBytes(ordinary.length - 1),
                "encodedBytes");
        assertLimit(
                "png-container-limit",
                ordinary,
                EncodedRasterFormat.PNG,
                exact.withMaximumContainerElements(2),
                "containerElements");
        assertLimit(
                "png-inflated-limit",
                ordinary,
                EncodedRasterFormat.PNG,
                exact.withMaximumInflatedRasterBytes(4),
                "inflatedRasterBytes");
    }

    @Test
    void exercisesEveryPngPaletteTransparencyAnimationAndEndBoundaryThroughPublicFacade()
            throws Exception {
        assertPngReason(
                "missing-idat", png(1, 1, 8, 6, 0, chunk("IEND", new byte[0])), "missingData");
        assertPngReason(
                "duplicate-palette",
                indexedPng(
                        1,
                        chunk("PLTE", new byte[] {0, 0, 0}),
                        chunk("PLTE", new byte[] {0, 0, 0})),
                "palette");
        assertPngReason(
                "duplicate-transparency",
                indexedPng(
                        1,
                        chunk("PLTE", new byte[] {0, 0, 0}),
                        chunk("tRNS", new byte[] {0}),
                        chunk("tRNS", new byte[] {0})),
                "palette");

        assertOpens(
                "palette-minimum",
                indexedPng(1, chunk("PLTE", new byte[] {0, 0, 0})),
                EncodedRasterFormat.PNG);
        assertOpens(
                "palette-maximum",
                indexedPng(8, chunk("PLTE", new byte[768])),
                EncodedRasterFormat.PNG);
        for (byte[] palette : new byte[][] {new byte[0], new byte[2], new byte[771], new byte[9]}) {
            int depth = palette.length == 9 ? 1 : 8;
            assertPngReason(
                    "palette-length-" + palette.length,
                    indexedPng(depth, chunk("PLTE", palette)),
                    "palette");
        }

        assertOpens(
                "gray-transparency-minimum",
                pngWithTransparency(8, 0, new byte[] {0, 0}, new byte[] {0, 0}),
                EncodedRasterFormat.PNG);
        assertOpens(
                "gray-transparency-maximum",
                pngWithTransparency(8, 0, new byte[] {0, (byte) 255}, new byte[] {0, 0}),
                EncodedRasterFormat.PNG);
        assertPngReason(
                "gray-transparency-length",
                pngWithTransparency(8, 0, new byte[] {0}, new byte[] {0, 0}),
                "palette");
        assertPngReason(
                "gray-transparency-sample",
                pngWithTransparency(8, 0, new byte[] {1, 0}, new byte[] {0, 0}),
                "palette");

        assertOpens(
                "rgb-transparency-maximum",
                pngWithTransparency(
                        8,
                        2,
                        new byte[] {0, (byte) 255, 0, (byte) 255, 0, (byte) 255},
                        new byte[] {0, 0, 0, 0}),
                EncodedRasterFormat.PNG);
        assertPngReason(
                "rgb-transparency-length",
                pngWithTransparency(8, 2, new byte[5], new byte[] {0, 0, 0, 0}),
                "palette");
        assertPngReason(
                "rgb-transparency-sample",
                pngWithTransparency(8, 2, new byte[] {1, 0, 0, 0, 0, 0}, new byte[] {0, 0, 0, 0}),
                "palette");

        assertOpens(
                "indexed-transparency-maximum",
                indexedPng(
                        1,
                        chunk("PLTE", new byte[] {0, 0, 0, 1, 1, 1}),
                        chunk("tRNS", new byte[] {0, (byte) 255})),
                EncodedRasterFormat.PNG);
        assertPngReason(
                "indexed-transparency-empty",
                indexedPng(1, chunk("PLTE", new byte[] {0, 0, 0}), chunk("tRNS", new byte[0])),
                "palette");
        assertPngReason(
                "indexed-transparency-too-long",
                indexedPng(
                        1, chunk("PLTE", new byte[] {0, 0, 0}), chunk("tRNS", new byte[] {0, 1})),
                "palette");
        for (int alphaColor : new int[] {4, 6}) {
            int channels = alphaColor == 4 ? 2 : 4;
            assertPngReason(
                    "alpha-transparency-" + alphaColor,
                    png(
                            1,
                            1,
                            8,
                            alphaColor,
                            0,
                            chunk("tRNS", new byte[] {0, 0}),
                            chunk("IDAT", deflate(new byte[channels + 1])),
                            chunk("IEND", new byte[0])),
                    "palette");
        }

        for (String animationChunk : new String[] {"fcTL", "fdAT"}) {
            assertFailure(
                    "apng-" + animationChunk,
                    png(
                            1,
                            1,
                            8,
                            6,
                            0,
                            chunk(animationChunk, new byte[4]),
                            chunk("IDAT", deflate(new byte[] {0, 0, 0, 0, 0})),
                            chunk("IEND", new byte[0])),
                    EncodedRasterFormat.PNG,
                    "IMAGE_PROFILE_UNSUPPORTED",
                    "field",
                    "animation");
        }
        assertFailure(
                "unknown-critical",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("ABCD", new byte[0]),
                        chunk("IDAT", deflate(new byte[] {0, 0, 0, 0, 0})),
                        chunk("IEND", new byte[0])),
                EncodedRasterFormat.PNG,
                "IMAGE_PROFILE_UNSUPPORTED",
                "field",
                "criticalChunk");
        assertPngReason(
                "duplicate-iend",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("IDAT", deflate(new byte[] {0, 0, 0, 0, 0})),
                        chunk("IEND", new byte[0]),
                        chunk("IEND", new byte[0])),
                "trailingData");
        assertPngReason(
                "nonzero-iend",
                png(
                        1,
                        1,
                        8,
                        6,
                        0,
                        chunk("IDAT", deflate(new byte[] {0, 0, 0, 0, 0})),
                        chunk("IEND", new byte[] {0})),
                "chunkLength");
    }

    @Test
    void exercisesJpegBaselineProgressiveEntropyRestartAndScanMatrixThroughPublicFacade()
            throws Exception {
        assertOpens("baseline", baseline(new byte[] {1, 2}), EncodedRasterFormat.JPEG);
        assertOpens(
                "stuffed-entropy",
                baseline(new byte[] {1, (byte) 0xff, 0, 2}),
                EncodedRasterFormat.JPEG);
        assertOpens("progressive-multiscan", progressive(false), EncodedRasterFormat.JPEG);
        assertOpens("progressive-refinement", progressive(true), EncodedRasterFormat.JPEG);

        byte[] restart =
                jpeg(
                        segment(0xdd, new byte[] {0, 1}),
                        sof(0xc0),
                        scan(new byte[] {1, (byte) 0xff, (byte) 0xd0, 2}, 0, 63, 0));
        assertOpens("restart", restart, EncodedRasterFormat.JPEG);
        assertJpegReason(
                "restart-without-dri",
                jpeg(sof(0xc0), scan(new byte[] {1, (byte) 0xff, (byte) 0xd0, 2}, 0, 63, 0)),
                "entropy");
        assertJpegReason(
                "restart-sequence",
                jpeg(
                        segment(0xdd, new byte[] {0, 1}),
                        sof(0xc0),
                        scan(new byte[] {1, (byte) 0xff, (byte) 0xd1, 2}, 0, 63, 0)),
                "entropy");

        assertJpegReason(
                "baseline-spectral", jpeg(sof(0xc0), scan(new byte[] {1}, 1, 63, 0)), "scan");
        assertJpegReason(
                "progressive-refinement-shape",
                jpeg(sof(0xc2), scan(new byte[] {1}, 1, 5, 0x22)),
                "scan");
    }

    @Test
    void exercisesJpegMarkerBoundaryTerminationAndHeavyEntropyMatrixThroughPublicFacade()
            throws Exception {
        assertJpegReason(
                "frame-after-scan",
                jpeg(sof(0xc0), scanWithoutEnd(new byte[] {1}, 0, 63, 0), sof(0xc0), eoi()),
                "markerOrder");

        byte[] invalidSegment = baseline(new byte[] {1});
        invalidSegment[4] = 0;
        invalidSegment[5] = 1;
        assertFailure(
                "jpeg-segment-boundary",
                invalidSegment,
                EncodedRasterFormat.JPEG,
                "IMAGE_HEADER_INVALID",
                "field",
                "segmentLength");

        byte[] baseline = baseline(new byte[] {1});
        ImageSourceLimits exact =
                ImageSourceLimits.defaults()
                        .withMaximumEncodedBytes(baseline.length)
                        .withMaximumContainerElements(4);
        assertOpens("jpeg-limit-equality", baseline, EncodedRasterFormat.JPEG, exact);
        assertLimit(
                "jpeg-container-limit",
                baseline,
                EncodedRasterFormat.JPEG,
                exact.withMaximumContainerElements(3),
                "containerElements");
        assertLimit(
                "jpeg-encoded-limit",
                baseline,
                EncodedRasterFormat.JPEG,
                exact.withMaximumEncodedBytes(baseline.length - 1),
                "encodedBytes");

        assertFailure(
                "bad-soi",
                new byte[] {(byte) 0xff, (byte) 0xd7, 1, 2},
                EncodedRasterFormat.JPEG,
                "IMAGE_FORMAT_MISMATCH",
                "signature",
                "unknown");
        assertJpegReason("eoi-before-scan", jpeg(sof(0xc0), eoi()), "missingData");
        assertJpegReason(
                "concatenated-jpeg",
                concatenate(baseline, baseline(new byte[] {2})),
                "trailingData");
        assertJpegReason(
                "truncated-entropy",
                jpegWithoutEoi(sof(0xc0), scanWithoutEnd(new byte[] {1}, 0, 63, 0)),
                "missingEnd");

        byte[] heavy = new byte[20_000];
        Arrays.fill(heavy, (byte) 1);
        assertOpens("large-entropy", baseline(heavy), EncodedRasterFormat.JPEG);
    }

    @Test
    void exercisesEveryJpegTableBoundaryAndReplacementThroughPublicFacade() throws Exception {
        assertOpens(
                "single-dqt-dht",
                jpeg(
                        segment(0xdb, dqt(0, 0)),
                        segment(0xc4, dht(0, 0, 1, 1)),
                        sof(0xc0),
                        scan(new byte[] {1}, 0, 63, 0)),
                EncodedRasterFormat.JPEG);
        assertOpens(
                "multiple-dqt-dht",
                jpeg(
                        segment(0xdb, concatenate(dqt(0, 0), dqt(1, 1))),
                        segment(0xc4, concatenate(dht(0, 0, 1, 1), dht(1, 1, 1, 1))),
                        sof(0xc0),
                        scan(new byte[] {1}, 0, 63, 0)),
                EncodedRasterFormat.JPEG);
        assertOpens(
                "replacement-dqt-dht",
                jpeg(
                        segment(0xdb, dqt(0, 0)),
                        segment(0xdb, dqt(0, 0)),
                        segment(0xc4, dht(0, 0, 1, 1)),
                        segment(0xc4, dht(0, 0, 1, 1)),
                        sof(0xc0),
                        scan(new byte[] {1}, 0, 63, 0)),
                EncodedRasterFormat.JPEG);

        for (byte[] invalidDqt :
                new byte[][] {new byte[0], dqt(2, 0), dqt(0, 4), Arrays.copyOf(dqt(0, 0), 64)}) {
            assertJpegReason(
                    "invalid-dqt-"
                            + invalidDqt.length
                            + '-'
                            + (invalidDqt.length == 0 ? 0 : invalidDqt[0]),
                    jpeg(segment(0xdb, invalidDqt), sof(0xc0), scan(new byte[] {1}, 0, 63, 0)),
                    "table");
        }
        for (byte[] invalidDht :
                new byte[][] {
                    new byte[0],
                    dht(2, 0, 0, 0),
                    dht(0, 4, 0, 0),
                    dht(0, 0, 257, 257),
                    dht(0, 0, 1, 0)
                }) {
            assertJpegReason(
                    "invalid-dht-"
                            + invalidDht.length
                            + '-'
                            + (invalidDht.length == 0 ? 0 : invalidDht[0]),
                    jpeg(segment(0xc4, invalidDht), sof(0xc0), scan(new byte[] {1}, 0, 63, 0)),
                    "table");
        }
    }

    @Test
    void exercisesEveryJpegScanOrderRestartAndTerminationBoundaryThroughPublicFacade()
            throws Exception {
        assertJpegReason("empty-entropy", jpeg(sof(0xc0), scan(new byte[0], 0, 63, 0)), "entropy");
        assertFailure(
                "stuffing-before-frame",
                concatenate(
                        new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff, 0},
                        sof(0xc0),
                        scan(new byte[] {1}, 0, 63, 0)),
                EncodedRasterFormat.JPEG,
                "IMAGE_HEADER_INVALID",
                "reason",
                "stuffedBeforeSof");

        assertJpegReason(
                "duplicate-scan-components",
                jpeg(
                        sof(0xc0, 3),
                        customScan(
                                new int[] {1, 1},
                                new int[] {0, 0},
                                0,
                                63,
                                0,
                                new byte[] {1},
                                true)),
                "scan");
        assertJpegReason(
                "unknown-scan-component",
                jpeg(
                        sof(0xc0),
                        customScan(new int[] {2}, new int[] {0}, 0, 63, 0, new byte[] {1}, true)),
                "scan");
        for (int selector : new int[] {0x40, 0x04}) {
            assertJpegReason(
                    "scan-selector-" + selector,
                    jpeg(
                            sof(0xc0),
                            customScan(
                                    new int[] {1},
                                    new int[] {selector},
                                    0,
                                    63,
                                    0,
                                    new byte[] {1},
                                    true)),
                    "scan");
        }

        assertJpegReason(
                "progressive-ac-components",
                jpeg(
                        sof(0xc2, 3),
                        customScan(
                                new int[] {1, 2}, new int[] {0, 0}, 1, 5, 0, new byte[] {1}, true)),
                "scan");
        for (int[] bounds : new int[][] {{6, 5}, {1, 64}}) {
            assertJpegReason(
                    "progressive-spectral-" + bounds[0] + '-' + bounds[1],
                    jpeg(sof(0xc2), scan(new byte[] {1}, bounds[0], bounds[1], 0)),
                    "scan");
        }
        for (int approximation : new int[] {0xe0, 0x0e, 0x31}) {
            assertJpegReason(
                    "progressive-approximation-" + approximation,
                    jpeg(sof(0xc2), scan(new byte[] {1}, 1, 5, approximation)),
                    "scan");
        }

        assertJpegReason(
                "dri-length",
                jpeg(segment(0xdd, new byte[] {1}), sof(0xc0), scan(new byte[] {1}, 0, 63, 0)),
                "table");
        assertOpens(
                "zero-dri",
                jpeg(segment(0xdd, new byte[] {0, 0}), sof(0xc0), scan(new byte[] {1}, 0, 63, 0)),
                EncodedRasterFormat.JPEG);

        assertOpens(
                "tables-and-app-between-scans",
                jpeg(
                        sof(0xc2),
                        scanWithoutEnd(new byte[] {1}, 0, 0, 0),
                        segment(0xdb, dqt(0, 0)),
                        segment(0xc4, dht(0, 0, 1, 1)),
                        segment(0xe1, new byte[] {1, 2}),
                        scan(new byte[] {2}, 1, 5, 0)),
                EncodedRasterFormat.JPEG);
        assertJpegReason(
                "second-soi",
                jpeg(
                        sof(0xc0),
                        new byte[] {(byte) 0xff, (byte) 0xd8},
                        scan(new byte[] {1}, 0, 63, 0)),
                "markerOrder");
        assertJpegReason(
                "second-sof",
                jpeg(sof(0xc0), sof(0xc0), scan(new byte[] {1}, 0, 63, 0)),
                "markerOrder");

        byte[] fill = new byte[4_097];
        Arrays.fill(fill, (byte) 0xff);
        assertJpegReason(
                "missing-eoi-after-marker-fill",
                jpegWithoutEoi(sof(0xc0), scanWithoutEnd(new byte[] {1}, 0, 63, 0), fill),
                "missingEnd");
    }

    private void assertPngReason(String name, byte[] bytes, String reason) throws Exception {
        assertFailure(
                name, bytes, EncodedRasterFormat.PNG, "IMAGE_CONTAINER_INVALID", "reason", reason);
    }

    private void assertJpegReason(String name, byte[] bytes, String reason) throws Exception {
        assertFailure(
                name, bytes, EncodedRasterFormat.JPEG, "IMAGE_CONTAINER_INVALID", "reason", reason);
    }

    private void assertLimit(
            String name,
            byte[] bytes,
            EncodedRasterFormat format,
            ImageSourceLimits limits,
            String limit)
            throws Exception {
        assertFailure(name, bytes, format, limits, "SOURCE_LIMIT_EXCEEDED", "limit", limit);
    }

    private void assertFailure(
            String name,
            byte[] bytes,
            EncodedRasterFormat format,
            String code,
            String contextKey,
            String contextValue)
            throws Exception {
        assertFailure(
                name, bytes, format, ImageSourceLimits.defaults(), code, contextKey, contextValue);
    }

    private void assertFailure(
            String name,
            byte[] bytes,
            EncodedRasterFormat format,
            ImageSourceLimits limits,
            String code,
            String contextKey,
            String contextValue)
            throws Exception {
        Path path = write(name, bytes, format);
        SourceException failure =
                assertThrows(
                        SourceException.class,
                        () ->
                                RasterImages.open(
                                        path,
                                        identity(),
                                        ImageOpenOptions.defaults().withImageLimits(limits),
                                        registry(format)));
        assertEquals(code, failure.terminal().code(), name);
        assertEquals(contextValue, failure.terminal().context().get(contextKey), name);
    }

    private void assertOpens(String name, byte[] bytes, EncodedRasterFormat format)
            throws Exception {
        assertOpens(name, bytes, format, ImageSourceLimits.defaults());
    }

    private void assertOpens(
            String name, byte[] bytes, EncodedRasterFormat format, ImageSourceLimits limits)
            throws Exception {
        Path path = write(name, bytes, format);
        try (RasterSource source =
                RasterImages.open(
                        path,
                        identity(),
                        ImageOpenOptions.defaults().withImageLimits(limits),
                        registry(format))) {
            int expectedWidth =
                    format == EncodedRasterFormat.PNG ? ByteBuffer.wrap(bytes, 16, 4).getInt() : 1;
            assertEquals(expectedWidth, source.metadata().width(), name);
        }
    }

    private Path write(String name, byte[] bytes, EncodedRasterFormat format) throws Exception {
        String extension = format == EncodedRasterFormat.PNG ? ".png" : ".jpeg";
        Path path = directory.resolve(sequence.incrementAndGet() + "-" + name + extension);
        Files.write(path, bytes);
        return path;
    }

    private static SourceIdentity identity() {
        return new SourceIdentity("hostile-matrix", "Hostile matrix");
    }

    private static EncodedRasterDecoderRegistry registry(EncodedRasterFormat format) {
        return EncodedRasterDecoderRegistry.builder()
                .register(
                        format,
                        (input, context) ->
                                RgbaPixelBuffer.builder(
                                                context.outputWidth(), context.outputHeight())
                                        .build())
                .build();
    }

    private static byte[] pngRgba(int width, int height, byte[] filtered) {
        return pngRgba(width, height, filtered, 0);
    }

    private static byte[] pngRgba(int width, int height, byte[] filtered, int interlace) {
        return png(
                width,
                height,
                8,
                6,
                interlace,
                chunk("IDAT", deflate(filtered)),
                chunk("IEND", new byte[0]));
    }

    private static byte[] indexedPng(int depth, byte[]... prefixChunks) {
        byte[][] chunks = Arrays.copyOf(prefixChunks, prefixChunks.length + 2);
        chunks[chunks.length - 2] = chunk("IDAT", deflate(new byte[] {0, 0}));
        chunks[chunks.length - 1] = chunk("IEND", new byte[0]);
        return png(1, 1, depth, 3, 0, chunks);
    }

    private static byte[] pngWithTransparency(
            int depth, int color, byte[] transparency, byte[] filtered) {
        return png(
                1,
                1,
                depth,
                color,
                0,
                chunk("tRNS", transparency),
                chunk("IDAT", deflate(filtered)),
                chunk("IEND", new byte[0]));
    }

    private static byte[] png(
            int width, int height, int depth, int color, int interlace, byte[]... chunks) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(PNG_SIGNATURE);
        output.writeBytes(chunk("IHDR", ihdr(width, height, depth, color, interlace)));
        for (byte[] chunk : chunks) {
            output.writeBytes(chunk);
        }
        return output.toByteArray();
    }

    private static byte[] ihdr(int width, int height, int depth, int color, int interlace) {
        return ByteBuffer.allocate(13)
                .order(ByteOrder.BIG_ENDIAN)
                .putInt(width)
                .putInt(height)
                .put((byte) depth)
                .put((byte) color)
                .put((byte) 0)
                .put((byte) 0)
                .put((byte) interlace)
                .array();
    }

    private static byte[] chunk(String type, byte[] payload) {
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        ByteBuffer chunk = ByteBuffer.allocate(payload.length + 12).order(ByteOrder.BIG_ENDIAN);
        chunk.putInt(payload.length).put(name).put(payload);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(payload);
        chunk.putInt((int) crc.getValue());
        return chunk.array();
    }

    private static byte[] deflate(byte[] bytes) {
        Deflater deflater = new Deflater();
        return deflate(bytes, deflater);
    }

    private static byte[] deflateWithDictionary(byte[] bytes) {
        Deflater deflater = new Deflater();
        deflater.setDictionary(new byte[] {1, 2, 3, 4});
        return deflate(bytes, deflater);
    }

    private static byte[] deflate(byte[] bytes, Deflater deflater) {
        try {
            deflater.setInput(bytes);
            deflater.finish();
            byte[] output = new byte[Math.max(64, bytes.length + 64)];
            return Arrays.copyOf(output, deflater.deflate(output));
        } finally {
            deflater.end();
        }
    }

    private static byte[] adam7FilteredRgba(int width, int height) {
        int[] xStart = {0, 4, 0, 2, 0, 1, 0};
        int[] yStart = {0, 0, 4, 0, 2, 0, 1};
        int[] xStep = {8, 8, 4, 4, 2, 2, 1};
        int[] yStep = {8, 8, 8, 4, 4, 2, 2};
        int size = 0;
        for (int pass = 0; pass < 7; pass++) {
            int passWidth = span(width, xStart[pass], xStep[pass]);
            int passHeight = span(height, yStart[pass], yStep[pass]);
            if (passWidth > 0) {
                size += (passWidth * 4 + 1) * passHeight;
            }
        }
        return new byte[size];
    }

    private static int span(int extent, int start, int step) {
        return extent <= start ? 0 : (extent - start + step - 1) / step;
    }

    private static void putInt(byte[] bytes, int offset, int value) {
        ByteBuffer.wrap(bytes, offset, 4).order(ByteOrder.BIG_ENDIAN).putInt(value);
    }

    private static byte[] baseline(byte[] entropy) {
        return jpeg(sof(0xc0), scan(entropy, 0, 63, 0));
    }

    private static byte[] progressive(boolean refinement) {
        if (refinement) {
            return jpeg(
                    sof(0xc2),
                    scanWithoutEnd(new byte[] {1}, 0, 0, 1),
                    scan(new byte[] {2}, 0, 0, 0x10));
        }
        return jpeg(
                sof(0xc2), scanWithoutEnd(new byte[] {1}, 0, 0, 0), scan(new byte[] {2}, 1, 5, 0));
    }

    private static byte[] jpeg(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {(byte) 0xff, (byte) 0xd8});
        for (byte[] part : parts) {
            output.writeBytes(part);
        }
        if (parts.length == 0 || !endsWithEoi(parts[parts.length - 1])) {
            output.writeBytes(eoi());
        }
        return output.toByteArray();
    }

    private static byte[] jpegWithoutEoi(byte[]... parts) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.writeBytes(new byte[] {(byte) 0xff, (byte) 0xd8});
        for (byte[] part : parts) {
            output.writeBytes(part);
        }
        return output.toByteArray();
    }

    private static byte[] sof(int marker) {
        return sof(marker, 1);
    }

    private static byte[] sof(int marker, int components) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.writeBytes(new byte[] {8, 0, 1, 0, 1, (byte) components});
        for (int component = 1; component <= components; component++) {
            payload.writeBytes(new byte[] {(byte) component, 0x11, 0});
        }
        return segment(marker, payload.toByteArray());
    }

    private static byte[] scan(byte[] entropy, int start, int end, int approximation) {
        return concatenate(scanWithoutEnd(entropy, start, end, approximation), eoi());
    }

    private static byte[] scanWithoutEnd(byte[] entropy, int start, int end, int approximation) {
        return customScan(new int[] {1}, new int[] {0}, start, end, approximation, entropy, false);
    }

    private static byte[] customScan(
            int[] componentIds,
            int[] selectors,
            int start,
            int end,
            int approximation,
            byte[] entropy,
            boolean includeEnd) {
        ByteArrayOutputStream payload = new ByteArrayOutputStream();
        payload.write(componentIds.length);
        for (int index = 0; index < componentIds.length; index++) {
            payload.write(componentIds[index]);
            payload.write(selectors[index]);
        }
        payload.write(start);
        payload.write(end);
        payload.write(approximation);
        byte[] body = concatenate(segment(0xda, payload.toByteArray()), entropy);
        return includeEnd ? concatenate(body, eoi()) : body;
    }

    private static byte[] dqt(int precision, int id) {
        byte[] table = new byte[1 + 64 * (precision + 1)];
        table[0] = (byte) (precision << 4 | id);
        return table;
    }

    private static byte[] dht(int tableClass, int id, int symbols, int supplied) {
        byte[] table = new byte[17 + supplied];
        table[0] = (byte) (tableClass << 4 | id);
        int remaining = symbols;
        for (int index = 1; index <= 16 && remaining > 0; index++) {
            int count = Math.min(255, remaining);
            table[index] = (byte) count;
            remaining -= count;
        }
        return table;
    }

    private static byte[] segment(int marker, byte[] payload) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(0xff);
        output.write(marker);
        output.write((payload.length + 2) >>> 8);
        output.write(payload.length + 2);
        output.writeBytes(payload);
        return output.toByteArray();
    }

    private static byte[] eoi() {
        return new byte[] {(byte) 0xff, (byte) 0xd9};
    }

    private static boolean endsWithEoi(byte[] bytes) {
        return bytes.length >= 2
                && (bytes[bytes.length - 2] & 0xff) == 0xff
                && (bytes[bytes.length - 1] & 0xff) == 0xd9;
    }

    private static byte[] concatenate(byte[]... parts) {
        int length = 0;
        for (byte[] part : parts) {
            length += part.length;
        }
        byte[] result = new byte[length];
        int offset = 0;
        for (byte[] part : parts) {
            System.arraycopy(part, 0, result, offset, part.length);
            offset += part.length;
        }
        return result;
    }
}
