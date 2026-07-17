package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

@EnabledIfSystemProperty(named = "performanceBaselineOracle", matches = "true")
class DtedBaselineFixtureTest {
    @Test
    void maximumFixtureHasPinnedHeadersRecordsAndPublicReaderBehavior() throws Exception {
        Path scratch = Files.createTempDirectory(Path.of("/tmp"), "mundane-map-dted-baseline-");
        Path cellDirectory = scratch.resolve("e000");
        Path path = cellDirectory.resolve("n00.dt2");
        try {
            DtedEvidenceFixture.Fixture fixture =
                    DtedEvidenceFixture.write(path, DtedEvidenceFixture.MAXIMUM);
            assertEquals(25_981_042L, fixture.bytes());
            assertEquals(12_967_201L, fixture.samples());
            assertEquals(
                    "2e1e3adcb1f65d41d93ad5d31c63211522ca830bd8f2716415070e3ae8b72330",
                    fixture.sha256());

            try (FileChannel input = FileChannel.open(path, StandardOpenOption.READ)) {
                ByteBuffer header = ByteBuffer.allocate(3_428);
                readFully(input, header, 0);
                byte[] bytes = header.array();
                assertEquals("UHL1", ascii(bytes, 0, 4));
                assertEquals("DSI", ascii(bytes, 80, 3));
                assertEquals("ACC", ascii(bytes, 728, 3));
                assertEquals("2607", ascii(bytes, 80 + 90, 4));
                assertEquals("2607", ascii(bytes, 80 + 94, 4));
                assertEquals("0000", ascii(bytes, 80 + 98, 4));
                assertEquals("8902", ascii(bytes, 80 + 137, 4));
                assertEquals("2607", ascii(bytes, 80 + 159, 4));
                verifyRecord(input, 0, 3_601);
                verifyRecord(input, 3_600, 3_601);

                int column = 617;
                int row = 2_143;
                int fileSample = 3_600 - row;
                long offset = 3_428L + column * 7_214L + 8L + fileSample * 2L;
                ByteBuffer sample = ByteBuffer.allocate(2).order(ByteOrder.BIG_ENDIAN);
                readFully(input, sample, offset);
                assertEquals(independentValue(column, row, 3_601), sample.getShort(0) & 0x7fff);
            }

            try (ElevationSource source =
                    DtedFiles.open(
                            new SourceIdentity("evidence-maximum", ""),
                            path,
                            DtedOpenOptions.defaults())) {
                assertEquals(3_601, source.metadata().columnCount());
                assertEquals(3_601, source.metadata().rowCount());
                assertEquals(new Envelope(0, 0, 1, 1), source.metadata().sampleBounds());
                assertEquals(
                        "EPSG:4326",
                        source.metadata().crs().definition().orElseThrow().canonicalIdentifier());
                assertEquals(ElevationUnit.METRE, source.metadata().elevationUnit());
                assertTrue(source.openingDiagnostics().entries().isEmpty());
                assertEquals(1_200, source.sample(0, 0).orElseThrow());
                assertEquals(2_400, source.sample(3_600, 0).orElseThrow());
                assertEquals(400, source.sample(0, 3_600).orElseThrow());
                assertEquals(1_600, source.sample(3_600, 3_600).orElseThrow());
                assertEquals(1_400, source.sample(1_800, 1_800).orElseThrow());
            }
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(cellDirectory);
            Files.deleteIfExists(scratch);
        }
    }

    private static void verifyRecord(FileChannel input, int column, int posts) throws Exception {
        int recordBytes = 12 + 2 * posts;
        long offset = 3_428L + (long) column * recordBytes;
        ByteBuffer record = ByteBuffer.allocate(recordBytes).order(ByteOrder.BIG_ENDIAN);
        readFully(input, record, offset);
        assertEquals(0xaa, record.get(0) & 0xff);
        assertEquals(column, record.getShort(4));
        long expected = 0;
        for (int index = 0; index < recordBytes - 4; index++) {
            expected += record.get(index) & 0xffL;
        }
        assertEquals(expected, Integer.toUnsignedLong(record.getInt(recordBytes - 4)));
    }

    private static void readFully(FileChannel input, ByteBuffer target, long offset)
            throws Exception {
        while (target.hasRemaining()) {
            int count = input.read(target, offset + target.position());
            if (count < 0) {
                throw new AssertionError("Unexpected end of generated DTED fixture");
            }
        }
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.US_ASCII);
    }

    private static int independentValue(int column, int row, int posts) {
        return 1_200
                + Math.floorDiv(1_200 * column, posts - 1)
                - Math.floorDiv(800 * row, posts - 1);
    }
}
