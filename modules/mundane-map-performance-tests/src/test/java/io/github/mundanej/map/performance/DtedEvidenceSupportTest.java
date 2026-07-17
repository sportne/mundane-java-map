package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DtedEvidenceSupportTest {
    @TempDir Path temporary;

    @Test
    void generatedSmokeFixtureHasPinnedShapeFormulaAndHash() throws Exception {
        Path path = temporary.resolve("e000/n00.dt0");
        DtedEvidenceFixture.Fixture fixture =
                DtedEvidenceFixture.write(path, DtedEvidenceFixture.SMOKE);
        assertEquals(34_162L, fixture.bytes());
        assertEquals(14_641L, fixture.samples());
        assertEquals(1_200, DtedEvidenceFixture.value(0, 0, 121));
        assertEquals(2_400, DtedEvidenceFixture.value(120, 0, 121));
        assertEquals(400, DtedEvidenceFixture.value(0, 120, 121));
        assertEquals(1_600, DtedEvidenceFixture.value(120, 120, 121));
        assertEquals(1_400, DtedEvidenceFixture.value(60, 60, 121));
        assertEquals(
                "99bd897d6d4af55ffe1092be7a3ee8051d1fbfff1613d6f008fbfb447c46fad5",
                fixture.sha256());
        assertTrue(Files.isRegularFile(path));

        byte[] bytes = Files.readAllBytes(path);
        assertEquals("UHL1", ascii(bytes, 0, 4));
        assertEquals("DSI", ascii(bytes, 80, 3));
        assertEquals("ACC", ascii(bytes, 728, 3));
        assertEquals("2607", ascii(bytes, 80 + 90, 4));
        assertEquals("2607", ascii(bytes, 80 + 94, 4));
        assertEquals("0000", ascii(bytes, 80 + 98, 4));
        assertEquals("8902", ascii(bytes, 80 + 137, 4));
        assertEquals("2607", ascii(bytes, 80 + 159, 4));
        verifyRecord(bytes, 0, 121);
        verifyRecord(bytes, 120, 121);
        int column = 17;
        int row = 23;
        int fileSample = 120 - row;
        int sampleOffset = 3_428 + column * 254 + 8 + fileSample * 2;
        int raw =
                ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort(sampleOffset) & 0x7fff;
        assertEquals(independentValue(column, row, 121), raw);

        try (ElevationSource source =
                DtedFiles.open(
                        new SourceIdentity("evidence-smoke", ""),
                        path,
                        DtedOpenOptions.defaults())) {
            assertEquals(121, source.metadata().columnCount());
            assertEquals(121, source.metadata().rowCount());
            assertEquals(new Envelope(0, 0, 1, 1), source.metadata().sampleBounds());
            assertEquals(
                    "EPSG:4326",
                    source.metadata().crs().definition().orElseThrow().canonicalIdentifier());
            assertEquals(ElevationUnit.METRE, source.metadata().elevationUnit());
            assertTrue(source.openingDiagnostics().entries().isEmpty());
            assertEquals(1_200, source.sample(0, 0).orElseThrow());
            assertEquals(2_400, source.sample(120, 0).orElseThrow());
            assertEquals(400, source.sample(0, 120).orElseThrow());
            assertEquals(1_600, source.sample(120, 120).orElseThrow());
            assertEquals(1_400, source.sample(60, 60).orElseThrow());
        }
    }

    @Test
    void maximumLogicalMemoryMatchesDecisionRubric() {
        long samples = 12_967_201L;
        assertEquals(1_620_904L, DtedLogicalMemory.mask(samples));
        assertEquals(105_358_512L, DtedLogicalMemory.published(samples));
        assertEquals(7_214L, DtedLogicalMemory.record(3_601));
        assertEquals(210_726_938L, DtedLogicalMemory.openPeak(samples, 3_601));
        assertTrue(DtedLogicalMemory.retainEager(samples, 3_601));
        assertTrue(
                DtedLogicalMemory.withinDecisionLimits(
                        DtedLogicalMemory.PUBLISHED_LIMIT, DtedLogicalMemory.OPEN_PEAK_LIMIT));
        assertFalse(
                DtedLogicalMemory.withinDecisionLimits(
                        DtedLogicalMemory.PUBLISHED_LIMIT + 1, DtedLogicalMemory.OPEN_PEAK_LIMIT));
        assertFalse(
                DtedLogicalMemory.withinDecisionLimits(
                        DtedLogicalMemory.PUBLISHED_LIMIT, DtedLogicalMemory.OPEN_PEAK_LIMIT + 1));
        assertThrows(
                ArithmeticException.class,
                () -> assertEquals(0L, DtedLogicalMemory.published(Long.MAX_VALUE)));
    }

    @Test
    void analyticalLruResultsMatchTheDesignedLocalAndScatteredTraces() {
        assertResult(DtedProfileCacheModel.Trace.LOCAL, 1, 33_279, 65_025, 469_090_350, 36_478);
        assertResult(DtedProfileCacheModel.Trace.LOCAL, 64, 97_791, 513, 3_700_782, 1_880_110);
        assertResult(DtedProfileCacheModel.Trace.LOCAL, 256, 97_791, 513, 3_700_782, 7_498_798);
        assertResult(DtedProfileCacheModel.Trace.SCATTERED, 1, 0, 98_304, 709_165_056, 36_478);
        assertResult(DtedProfileCacheModel.Trace.SCATTERED, 64, 0, 98_304, 709_165_056, 1_880_110);
        assertResult(DtedProfileCacheModel.Trace.SCATTERED, 256, 0, 98_304, 709_165_056, 7_498_798);
    }

    @Test
    void smokeRegistryAppendsDtedScenariosWithoutCorpusProperties() throws Exception {
        ListIds ids = new ListIds(ScenarioRegistry.ids());
        assertEquals(45, ids.values().size());
        assertEquals(
                java.util.List.of(
                        "dted-corpus-open",
                        "dted-eager-open",
                        "dted-sequential-scan",
                        "dted-position-query"),
                ids.values().subList(41, 45));
        assertFalse(System.getProperties().containsKey("performanceDtedCorpus.l0"));
    }

    @Test
    void memoryProbeIsBoundedStrictlyOrderedAndPathFree() throws Exception {
        assertEquals("jdk_home_C__java", DtedMemoryProbe.bounded("jdk/home:C:\\java"));
        Path output = temporary.resolve("probe");
        Files.createDirectories(output);
        Path report = output.resolve("dted-memory-probe-v1.json");
        Files.writeString(report, validProbe());
        DtedMemoryProbe.validate(report);
        String text = Files.readString(report);
        assertTrue(Files.size(report) <= 65_536);
        assertFalse(text.contains(temporary.toString()));
        assertFalse(text.contains("\"maxBytes\": 0"));
        Files.writeString(
                report, text.replace("  \"fixture\":", "  \"unknown\": {},\n  \"fixture\":"));
        assertThrows(IllegalStateException.class, () -> DtedMemoryProbe.validate(report));
        Files.writeString(report, validProbe().replace("\"usedBytes\": 1", "\"usedBytes\": -1"));
        assertThrows(IllegalStateException.class, () -> DtedMemoryProbe.validate(report));
        Files.writeString(report, validProbe().replace("G1 Old Gen", "../../home"));
        assertThrows(IllegalStateException.class, () -> DtedMemoryProbe.validate(report));
    }

    private static String validProbe() {
        return """
                {
                  "schemaVersion": "mundane-map-dted-memory-probe/v1",
                  "fixture": {"id": "dted-zone-i-l2-v1", "columns": 3601, \
                "rows": 3601, "samples": 12967201, "bytes": 25981042, \
                "sha256": "2e1e3adcb1f65d41d93ad5d31c63211522ca830bd8f2716415070e3ae8b72330"},
                  "environment": {"javaVersion": "21.0.11", \
                "vm": "OpenJDK 64-Bit Server VM", "os": "Linux amd64"},
                  "jvmSettings": ["-Xms512m", "-Xmx512m", "-XX:+UseG1GC"],
                  "capabilities": {"threadAllocatedBytes": true},
                  "snapshots": [
                    {"phase": "beforeOpen", "usedBytes": 1, \
                "committedBytes": 2, "maxBytes": 3},
                    {"phase": "afterPublication", "usedBytes": 4, \
                "committedBytes": 5, "maxBytes": 6},
                    {"phase": "afterClose", "usedBytes": 7, \
                "committedBytes": 8, "maxBytes": 9}],
                  "poolPeaks": [
                    {"name": "G1 Old Gen", "usedBytes": 1, \
                "committedBytes": 2, "maxBytes": "UNAVAILABLE"}
                  ],
                  "allocatedBytesDelta": 10,
                  "logicalStorage": {"maskBytes": 1620904, \
                "publishedBytes": 105358512, "recordBytes": 7214, \
                "openPeakBytes": 210726938}
                }
                """;
    }

    private static void assertResult(
            DtedProfileCacheModel.Trace trace,
            int width,
            long hits,
            long misses,
            long bytes,
            long retained) {
        DtedProfileCacheModel.Result result = DtedProfileCacheModel.replay(trace, width);
        assertEquals(hits, result.hits());
        assertEquals(misses, result.misses());
        assertEquals(bytes, result.bytesRead());
        assertEquals(retained, result.retainedBytes());
    }

    private static String ascii(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, java.nio.charset.StandardCharsets.US_ASCII);
    }

    private static void verifyRecord(byte[] bytes, int column, int posts) {
        int recordBytes = 12 + 2 * posts;
        int start = 3_428 + column * recordBytes;
        assertEquals(0xaa, bytes[start] & 0xff);
        assertEquals(
                column, ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).getShort(start + 4));
        long expected = 0;
        for (int index = start; index < start + recordBytes - 4; index++) {
            expected += bytes[index] & 0xffL;
        }
        long actual =
                Integer.toUnsignedLong(
                        ByteBuffer.wrap(bytes)
                                .order(ByteOrder.BIG_ENDIAN)
                                .getInt(start + recordBytes - 4));
        assertEquals(expected, actual);
    }

    private static int independentValue(int column, int row, int posts) {
        return 1_200
                + Math.floorDiv(1_200 * column, posts - 1)
                - Math.floorDiv(800 * row, posts - 1);
    }

    private record ListIds(java.util.List<String> values) {}
}
