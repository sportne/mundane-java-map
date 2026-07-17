package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.ElevationQueryMode;
import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.ElevationSourceMetadata;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.ElevationQueries;
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Four DTED evidence scenarios sharing independently authored fixtures and stable semantics. */
final class DtedEvidenceScenarios {
    private DtedEvidenceScenarios() {}

    static void append(
            List<EvidenceScenario> target,
            EvidenceConfiguration.Profile profile,
            java.util.Optional<String> selected,
            Path workspace)
            throws Exception {
        boolean selectedDted = selected.isEmpty() || selected.orElseThrow().startsWith("dted-");
        if (!selectedDted) {
            return;
        }
        boolean needsGenerated =
                profile == EvidenceConfiguration.Profile.SMOKE
                        || selected.isEmpty()
                        || !selected.orElseThrow().equals("dted-corpus-open");
        DtedEvidenceFixture.Fixture shape =
                profile == EvidenceConfiguration.Profile.BASELINE
                        ? DtedEvidenceFixture.MAXIMUM
                        : DtedEvidenceFixture.SMOKE;
        Path fixturePath = null;
        DtedEvidenceFixture.Fixture generated = null;
        if (needsGenerated) {
            fixturePath = workspace.resolve(shape.id()).resolve("e000/n00.dt" + shape.level());
            generated = DtedEvidenceFixture.write(fixturePath, shape);
        }
        if (matches(selected, "dted-corpus-open")) {
            target.add(new CorpusOpen(profile, fixturePath));
        }
        if (matches(selected, "dted-eager-open")) {
            target.add(new EagerOpen(profile, fixturePath, generated));
        }
        if (matches(selected, "dted-sequential-scan")) {
            target.add(new SequentialScan(profile, fixturePath, generated));
        }
        if (matches(selected, "dted-position-query")) {
            target.add(new PositionQuery(profile, fixturePath, generated));
        }
    }

    private static boolean matches(java.util.Optional<String> selected, String id) {
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
            return "G9-007 eager/windowed decision";
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
            return "NOT_APPLICABLE";
        }

        @Override
        public final ScenarioOracle oracle() {
            return ScenarioOracleV1.exact(expected);
        }

        final EvidenceObservation observation(java.util.function.Consumer<FnvOracle> content) {
            return ObservationDigests.observation(profile, id, counters, content);
        }
    }

    private static final class CorpusOpen extends Base {
        private final List<Path> paths;
        private List<ElevationSource> opened = List.of();

        CorpusOpen(EvidenceConfiguration.Profile profile, Path smoke) {
            super(
                    profile,
                    "dted-corpus-open",
                    profile == EvidenceConfiguration.Profile.BASELINE ? 3 : 1,
                    "filesOpened",
                    counters(
                            "filesOpened",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 3 : 1,
                            "profiles",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 823 : 121,
                            "samplesPublished",
                            profile == EvidenceConfiguration.Profile.BASELINE
                                    ? 2_541L + 241_401L + 2_164_201L
                                    : 14_641L,
                            "encodedBytes",
                            profile == EvidenceConfiguration.Profile.BASELINE
                                    ? 4_836_446L
                                    : 34_162L,
                            "noDataSamples",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 9 : 0));
            paths =
                    profile == EvidenceConfiguration.Profile.BASELINE
                            ? List.of(
                                    corpusPath(
                                            "l0",
                                            8_762L,
                                            "9b0f2d2d0b1fdeefb2e551fee98c4fac2da88141dc0fd02e712840fc9508c802"),
                                    corpusPath(
                                            "l1",
                                            488_642L,
                                            "ba2b8033ee4942989ec9acb916f95fffc88f054c3e9917145b4e613978db5c4f"),
                                    corpusPath(
                                            "l2",
                                            4_339_042L,
                                            "4d0511dd1551b05449ee9a60a4849e3baf132bcff64438f242aebfbaf126e58d"))
                            : List.of(smoke);
        }

        @Override
        public void prepareSample() {
            closeReverse(opened);
            opened = new ArrayList<>();
        }

        @Override
        public void runTimedBatch() {
            for (int index = 0; index < paths.size(); index++) {
                opened.add(open(paths.get(index), id + '-' + index));
            }
        }

        @Override
        public EvidenceObservation observeSample() {
            return observation(digest -> opened.forEach(source -> addSource(digest, source)));
        }

        @Override
        public void finishSample() {
            closeReverse(opened);
            opened = List.of();
        }
    }

    private static final class EagerOpen extends Base {
        private final Path path;
        private ElevationSource source;

        EagerOpen(
                EvidenceConfiguration.Profile profile,
                Path path,
                DtedEvidenceFixture.Fixture fixture) {
            super(
                    profile,
                    "dted-eager-open",
                    fixture.samples(),
                    "samplesPublished",
                    counters(
                            "filesOpened", 1,
                            "profiles", fixture.posts(),
                            "samplesPublished", fixture.samples(),
                            "logicalPublishedBytes", DtedLogicalMemory.published(fixture.samples()),
                            "logicalOpenPeakBytes",
                                    DtedLogicalMemory.openPeak(
                                            fixture.samples(), fixture.posts())));
            this.path = path;
        }

        @Override
        public void prepareSample() {
            DtedEvidenceScenarios.close(source);
            source = null;
        }

        @Override
        public void runTimedBatch() {
            source = open(path, id);
        }

        @Override
        public EvidenceObservation observeSample() {
            return observation(digest -> addSource(digest, source));
        }

        @Override
        public void finishSample() {
            DtedEvidenceScenarios.close(source);
            source = null;
        }
    }

    private abstract static class OpenOnce extends Base {
        final Path path;
        ElevationSource source;
        long timedDigest;

        OpenOnce(
                EvidenceConfiguration.Profile profile,
                String id,
                long operations,
                String unit,
                Map<String, Long> counters,
                Path path) {
            super(profile, id, operations, unit, counters);
            this.path = path;
        }

        @Override
        public void setupScenario() {
            source = open(path, id);
        }

        @Override
        public void prepareSample() {
            timedDigest = 0;
        }

        @Override
        public EvidenceObservation observeSample() {
            return observation(digest -> digest.add(timedDigest));
        }

        @Override
        public void finishScenario() {
            DtedEvidenceScenarios.close(source);
            source = null;
        }
    }

    private static final class SequentialScan extends OpenOnce {
        private final int posts;

        SequentialScan(
                EvidenceConfiguration.Profile profile,
                Path path,
                DtedEvidenceFixture.Fixture fixture) {
            super(
                    profile,
                    "dted-sequential-scan",
                    fixture.samples(),
                    "samplesVisited",
                    counters(
                            "profiles", fixture.posts(),
                            "samplesVisited", fixture.samples(),
                            "noDataSamples", 0),
                    path);
            posts = fixture.posts();
        }

        @Override
        public void runTimedBatch() {
            FnvOracle digest = new FnvOracle(EvidenceConfiguration.SEED).add(id);
            for (int row = 0; row < posts; row++) {
                for (int column = 0; column < posts; column++) {
                    OptionalDouble value = source.sample(column, row);
                    digest.add(column).add(row).add(value.isPresent());
                    value.ifPresent(digest::add);
                }
            }
            timedDigest = digest.value();
        }
    }

    private static final class PositionQuery extends OpenOnce {
        private final int posts;
        private final int queries;

        PositionQuery(
                EvidenceConfiguration.Profile profile,
                Path path,
                DtedEvidenceFixture.Fixture fixture) {
            super(
                    profile,
                    "dted-position-query",
                    profile == EvidenceConfiguration.Profile.BASELINE ? 65_536 : 256,
                    "queries",
                    counters(
                            "queries",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 65_536 : 256,
                            "nearestQueries",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 32_768 : 128,
                            "bilinearQueries",
                            profile == EvidenceConfiguration.Profile.BASELINE ? 32_768 : 128),
                    path);
            posts = fixture.posts();
            queries = profile == EvidenceConfiguration.Profile.BASELINE ? 65_536 : 256;
        }

        @Override
        public void runTimedBatch() {
            FnvOracle digest = new FnvOracle(EvidenceConfiguration.SEED).add(id);
            ElevationSourceMetadata metadata = source.metadata();
            int modulus = posts - 1;
            for (int query = 0; query < queries; query++) {
                int column = Math.floorMod(997 * query, modulus);
                int row = Math.floorMod(613 * query, modulus);
                boolean nearest = (query & 1) == 0;
                Coordinate first = metadata.sampleCoordinate(column, row);
                Coordinate coordinate;
                if (nearest) {
                    coordinate = first;
                } else {
                    Coordinate second = metadata.sampleCoordinate(column + 1, row + 1);
                    coordinate =
                            new Coordinate(
                                    (first.x() + second.x()) / 2.0, (first.y() + second.y()) / 2.0);
                }
                var value =
                        ElevationQueries.query(
                                source,
                                CrsDefinitions.EPSG_4326,
                                coordinate,
                                nearest ? ElevationQueryMode.NEAREST : ElevationQueryMode.BILINEAR);
                digest.add(query).add(nearest).add(value.isPresent());
                value.ifPresent(item -> digest.add(item.value()).add(item.unit()));
            }
            timedDigest = digest.value();
        }
    }

    private static ElevationSource open(Path path, String id) {
        return DtedFiles.open(new SourceIdentity(id, ""), path, DtedOpenOptions.defaults());
    }

    private static Path corpusPath(String level, long bytes, String sha256) {
        String value = System.getProperty("performanceDtedCorpus." + level);
        if (value == null) {
            throw new IllegalStateException("Missing staged DTED corpus path: " + level);
        }
        Path path = Path.of(value).toAbsolutePath().normalize();
        try {
            if (!path.startsWith(Path.of("/tmp"))
                    || Files.size(path) != bytes
                    || !DtedEvidenceFixture.sha256(path).equals(sha256)) {
                throw new IllegalStateException("Staged DTED corpus fixture changed: " + level);
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot validate staged DTED corpus fixture", failure);
        }
        return path;
    }

    private static void addSource(FnvOracle digest, ElevationSource source) {
        ElevationSourceMetadata metadata = source.metadata();
        digest.add(metadata.columnCount())
                .add(metadata.rowCount())
                .add(metadata.sampleBounds().minX())
                .add(metadata.sampleBounds().minY())
                .add(metadata.sampleBounds().maxX())
                .add(metadata.sampleBounds().maxY())
                .add(metadata.crs().canonicalIdentifier().orElse("UNKNOWN"))
                .add(metadata.elevationUnit());
        for (int[] corner :
                List.of(
                        new int[] {0, 0},
                        new int[] {metadata.columnCount() - 1, 0},
                        new int[] {0, metadata.rowCount() - 1},
                        new int[] {metadata.columnCount() - 1, metadata.rowCount() - 1})) {
            OptionalDouble value = source.sample(corner[0], corner[1]);
            digest.add(corner[0]).add(corner[1]).add(value.isPresent());
            value.ifPresent(digest::add);
        }
        ObservationDigests.addDiagnostics(digest, source.openingDiagnostics());
    }

    private static Map<String, Long> counters(Object... entries) {
        LinkedHashMap<String, Long> result = new LinkedHashMap<>();
        for (int index = 0; index < entries.length; index += 2) {
            result.put((String) entries[index], ((Number) entries[index + 1]).longValue());
        }
        return result;
    }

    private static void close(ElevationSource source) {
        if (source != null) {
            source.close();
        }
    }

    private static void closeReverse(List<ElevationSource> sources) {
        for (int index = sources.size() - 1; index >= 0; index--) {
            sources.get(index).close();
        }
    }
}
