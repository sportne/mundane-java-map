package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationUnit;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.geotiff.GeoTiffElevationOptions;
import io.github.mundanej.map.io.geotiff.GeoTiffFiles;
import io.github.mundanej.map.io.geotiff.GeoTiffRasterOptions;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Bounded GeoTIFF window-read and eager-elevation performance scenarios. */
final class GeoTiffEvidenceScenarios {
    private GeoTiffEvidenceScenarios() {}

    static void append(
            List<EvidenceScenario> target,
            EvidenceConfiguration.Profile profile,
            Optional<String> selected,
            Path workspace)
            throws Exception {
        if (selected.isPresent() && !selected.orElseThrow().startsWith("geotiff-")) {
            return;
        }
        GeoTiffEvidenceFixture.Fixture fixture = GeoTiffEvidenceFixture.write(workspace, profile);
        if (matches(selected, "geotiff-raster-window-read")) {
            target.add(new RasterWindowRead(profile, fixture));
        }
        if (matches(selected, "geotiff-eager-elevation-open")) {
            target.add(new EagerElevationOpen(profile, fixture));
        }
    }

    private static boolean matches(Optional<String> selected, String id) {
        return selected.isEmpty() || selected.orElseThrow().equals(id);
    }

    private abstract static class Base implements EvidenceScenario {
        final EvidenceConfiguration.Profile profile;
        final String id;
        final long operations;
        final String unit;
        final Map<String, Long> counters;
        final EvidenceObservation expected;

        Base(
                EvidenceConfiguration.Profile profile,
                String id,
                long operations,
                String unit,
                Map<String, Long> counters) {
            this.profile = profile;
            this.id = id;
            this.operations = operations;
            this.unit = unit;
            this.counters = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(counters));
            expected = ScenarioOracleV1.expected(profile, id, counters);
        }

        @Override
        public final String id() {
            return id;
        }

        @Override
        public final String nextExperiment() {
            return "separate GeoTIFF optimization task only if profiling evidence justifies it";
        }

        @Override
        public final long batchOperations() {
            return operations;
        }

        @Override
        public final String workUnit() {
            return unit;
        }

        @Override
        public final String sourceCacheState() {
            return "NONE";
        }

        @Override
        public final ScenarioOracle oracle() {
            return ScenarioOracleV1.exact(expected);
        }

        final EvidenceObservation observation(java.util.function.Consumer<FnvOracle> content) {
            return ObservationDigests.observation(profile, id, counters, content);
        }
    }

    private static final class RasterWindowRead extends Base {
        private final Path path;
        private final int size;
        private final int window;
        private final int windows;
        private RasterSource source;
        private long digest;

        RasterWindowRead(
                EvidenceConfiguration.Profile profile, GeoTiffEvidenceFixture.Fixture fixture) {
            super(
                    profile,
                    "geotiff-raster-window-read",
                    (profile == EvidenceConfiguration.Profile.BASELINE ? 4L : 2L)
                            * square(profile == EvidenceConfiguration.Profile.BASELINE ? 64 : 32),
                    "pixelsRead",
                    counters(
                            "windowsRead",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 4 : 2,
                            "outputPixels",
                            (profile == EvidenceConfiguration.Profile.BASELINE ? 4L : 2L)
                                    * square(
                                            profile == EvidenceConfiguration.Profile.BASELINE
                                                    ? 64
                                                    : 32),
                            "decodedSegmentBytes",
                            (profile == EvidenceConfiguration.Profile.BASELINE ? 4L : 2L)
                                    * (profile == EvidenceConfiguration.Profile.BASELINE
                                            ? 64L * fixture.rasterSize()
                                            : 32L * fixture.rasterSize()),
                            "encodedBytes",
                            fixture.rasterBytes()));
            path = fixture.raster();
            size = fixture.rasterSize();
            window = profile == EvidenceConfiguration.Profile.BASELINE ? 64 : 32;
            windows = profile == EvidenceConfiguration.Profile.BASELINE ? 4 : 2;
        }

        @Override
        public void setupScenario() {
            source =
                    GeoTiffFiles.openRaster(
                            new SourceIdentity(id, ""), path, GeoTiffRasterOptions.defaults());
        }

        @Override
        public void prepareSample() {
            digest = 0;
        }

        @Override
        public void runTimedBatch() {
            FnvOracle value = new FnvOracle(EvidenceConfiguration.SEED).add(id);
            for (int index = 0; index < windows; index++) {
                int column = Math.floorMod(index * 193, size - window + 1);
                int row = 16 * Math.floorMod(index * 19, Math.floorDiv(size - window, 16) + 1);
                var read =
                        source.read(
                                new RasterRequest(
                                        new RasterWindow(column, row, window, window),
                                        window,
                                        window,
                                        Optional.empty()),
                                CancellationToken.none());
                for (int rowIndex = 0; rowIndex < window; rowIndex++) {
                    for (int columnIndex = 0; columnIndex < window; columnIndex++) {
                        value.addPackedRgba(read.pixels().rgbaAt(columnIndex, rowIndex));
                    }
                }
            }
            digest = value.value();
        }

        @Override
        public EvidenceObservation observeSample() {
            return observation(value -> value.add(digest));
        }

        @Override
        public void finishScenario() {
            source.close();
            source = null;
        }
    }

    private static final class EagerElevationOpen extends Base {
        private final Path path;
        private ElevationSource source;

        EagerElevationOpen(
                EvidenceConfiguration.Profile profile, GeoTiffEvidenceFixture.Fixture fixture) {
            super(
                    profile,
                    "geotiff-eager-elevation-open",
                    square(fixture.elevationSize()),
                    "samplesPublished",
                    elevationCounters(fixture));
            path = fixture.elevation();
        }

        @Override
        public void prepareSample() {
            closeSource();
        }

        @Override
        public void runTimedBatch() {
            source =
                    GeoTiffFiles.openElevation(
                            new SourceIdentity(id, ""),
                            path,
                            GeoTiffElevationOptions.of(ElevationUnit.METRE));
        }

        @Override
        public EvidenceObservation observeSample() {
            return observation(
                    value -> {
                        value.add(source.metadata().columnCount())
                                .add(source.metadata().rowCount())
                                .add(source.sample(0, 0).orElseThrow())
                                .add(
                                        source.sample(
                                                        source.metadata().columnCount() - 1,
                                                        source.metadata().rowCount() - 1)
                                                .orElseThrow());
                        ObservationDigests.addDiagnostics(value, source.openingDiagnostics());
                    });
        }

        @Override
        public void finishSample() {
            closeSource();
        }

        private void closeSource() {
            if (source != null) {
                source.close();
                source = null;
            }
        }
    }

    private static Map<String, Long> elevationCounters(GeoTiffEvidenceFixture.Fixture fixture) {
        long samples = square(fixture.elevationSize());
        long publishedMask = Math.multiplyExact(8L, Math.ceilDiv(samples, 64L));
        long published = Math.addExact(Math.multiplyExact(samples, 8L), publishedMask);
        long temporary = Math.multiplyExact(samples, 8L);
        long scratch = Math.multiplyExact(Math.multiplyExact(32L, fixture.elevationSize()), 2L);
        long segments = Math.ceilDiv(fixture.elevationSize(), 32L);
        long parserPlan =
                Math.addExact(
                        Math.addExact(13L * 64L, 9L * Double.BYTES),
                        Math.multiplyExact(4L * Long.BYTES, segments));
        long formatWorking = Math.addExact(parserPlan, Math.addExact(temporary, scratch));
        long snapshotAndFormat = Math.addExact(fixture.elevationBytes(), formatWorking);
        long formatAndPublished = Math.addExact(formatWorking, published);
        return counters(
                "samplesPublished", samples,
                "encodedBytes", fixture.elevationBytes(),
                "logicalTemporaryBytes", temporary,
                "logicalPublishedBytes", published,
                "logicalDecoderScratchBytes", scratch,
                "logicalParserPlanBytes", parserPlan,
                "logicalFormatWorkingBytes", formatWorking,
                "logicalSnapshotAndFormatPeakBytes", snapshotAndFormat,
                "logicalFormatAndPublishedPeakBytes", formatAndPublished,
                "logicalOpenPeakBytes", Math.max(snapshotAndFormat, formatAndPublished));
    }

    private static long square(long value) {
        return Math.multiplyExact(value, value);
    }

    private static Map<String, Long> counters(Object... entries) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], ((Number) entries[index + 1]).longValue());
        }
        return result;
    }
}
