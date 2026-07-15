package io.github.mundanej.map.performance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.AttributeBytes;
import io.github.mundanej.map.api.AttributeNull;
import io.github.mundanej.map.api.CompositeSymbol;
import io.github.mundanej.map.api.Coordinate;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.FeatureRecord;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.FeatureSourceLimits;
import io.github.mundanej.map.api.PointGeometry;
import io.github.mundanej.map.api.Rgba;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.api.SymbolRotationMode;
import io.github.mundanej.map.api.VectorMarkerSymbol;
import io.github.mundanej.map.api.VectorPathCommand;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.InMemoryFeatureSource;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvidenceHarnessTest {
    @TempDir Path temporary;

    @Test
    void smokeProfileExecutesAllTwelveScenariosAndRendersEquivalentReports() throws Exception {
        RuntimeSettings settings = RuntimeSettings.install();
        try {
            EvidenceConfiguration configuration =
                    new EvidenceConfiguration(
                            EvidenceConfiguration.Profile.SMOKE,
                            EvidenceConfiguration.SEED,
                            1,
                            2,
                            Optional.empty(),
                            Optional.of("0123456"),
                            false);
            List<EvidenceScenario> scenarios =
                    ScenarioRegistry.create(configuration.profile(), temporary.resolve("fixtures"));
            EvidenceReport report = new EvidenceRunner().run(configuration, scenarios);
            String json = new String(report.json(), StandardCharsets.UTF_8);
            String markdown = new String(report.markdown(), StandardCharsets.UTF_8);
            assertEquals(18, count(json, "\"id\": \""));
            assertTrue(json.startsWith("{\n  \"schemaVersion\""));
            assertTrue(json.endsWith("\n"));
            assertTrue(markdown.contains("Durations are environment-specific evidence"));
            assertFalse(json.contains("consumer"));
            assertFalse(markdown.contains("consumer"));
            for (String setting : EvidenceConfiguration.JVM_SETTINGS) {
                assertTrue(json.contains('"' + setting + '"'));
                assertTrue(markdown.contains("`" + setting + "`"));
            }
            for (String id : ScenarioRegistry.ids()) {
                assertTrue(json.contains("\"id\": \"" + id + "\""));
                assertTrue(markdown.contains("`" + id + "`"));
            }
            assertEquivalentScenarioFacts(json, markdown);
            for (String file :
                    List.of(
                            "evidence.png",
                            "evidence.pgw",
                            "evidence.jpg",
                            "evidence.jgw",
                            "PROVENANCE.md")) {
                assertTrue(json.contains('"' + file + '"'));
                assertTrue(markdown.contains("`" + file + "`"));
            }
            assertFalse(Files.exists(temporary.resolve("fixtures")));
        } finally {
            settings.restore();
        }
    }

    @Test
    void statisticsUseExactMedianNearestRankAndIntegerThroughput() {
        EvidenceObservation observation = new EvidenceObservation(7, Map.of("records", 1L));
        EvidenceSample odd = EvidenceSample.of("odd", new long[] {9, 1, 5}, 10, observation);
        EvidenceSample even = EvidenceSample.of("even", new long[] {8, 2, 4, 6}, 10, observation);
        assertEquals(5, odd.medianNanos());
        assertEquals(9, odd.p95Nanos());
        assertEquals(5, even.medianNanos());
        assertEquals(8, even.p95Nanos());
        assertEquals(2_000_000_000_000L, even.operationsPerSecondMilli());
        assertThrows(IllegalArgumentException.class, () -> EvidenceSample.median(new long[] {0}));
        assertThrows(IllegalArgumentException.class, () -> EvidenceSample.median(new long[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> EvidenceSample.of("bad", new long[] {1}, 0, observation));
        assertThrows(IllegalArgumentException.class, () -> new TimedObservation(0));
        assertThrows(IllegalArgumentException.class, () -> new TimedObservation(-1));
    }

    @Test
    void typedOracleIsDeterministicAndCanonicalizesNegativeZero() {
        long first =
                new FnvOracle(EvidenceConfiguration.SEED)
                        .add("value")
                        .add(7)
                        .add(9L)
                        .add(-0.0)
                        .add(true)
                        .add(EvidenceConfiguration.Profile.SMOKE)
                        .add(io.github.mundanej.map.api.Rgba.rgb(1, 2, 3))
                        .value();
        long second =
                new FnvOracle(EvidenceConfiguration.SEED)
                        .add("value")
                        .add(7)
                        .add(9L)
                        .add(0.0)
                        .add(true)
                        .add(EvidenceConfiguration.Profile.SMOKE)
                        .add(io.github.mundanej.map.api.Rgba.rgb(1, 2, 3))
                        .value();
        assertEquals(first, second);
        int packed = 0x01020304;
        long packedProduction =
                new FnvOracle(EvidenceConfiguration.SEED).addPackedRgba(packed).value();
        long valueProduction =
                new FnvOracle(EvidenceConfiguration.SEED).add(new Rgba(1, 2, 3, 4)).value();
        long reference =
                new ReferenceDigest(EvidenceConfiguration.SEED).color(new Rgba(1, 2, 3, 4)).value();
        assertEquals(valueProduction, packedProduction);
        assertEquals(reference, packedProduction);
        assertThrows(IllegalArgumentException.class, () -> new FnvOracle(1).add(Double.NaN));
    }

    @Test
    void attributeDigestUsesCanonicalTypedImmutableValues() {
        LinkedHashMap<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("text", "7");
        attributes.put("flag", true);
        attributes.put("long", 7L);
        attributes.put("double", 7.5);
        attributes.put("decimal", new BigDecimal("7.50"));
        attributes.put("date", LocalDate.of(2026, 7, 15));
        attributes.put("null", AttributeNull.INSTANCE);
        attributes.put("bytes", new AttributeBytes(new byte[] {1, -2, 3}));
        FeatureRecord record =
                new FeatureRecord("typed", "", new PointGeometry(new Coordinate(1, 2)), attributes);
        FnvOracle production = new FnvOracle(EvidenceConfiguration.SEED);
        ObservationDigests.addRecord(production, record);
        ReferenceDigest reference = new ReferenceDigest(EvidenceConfiguration.SEED);
        ReferenceDigest.record(reference, record);
        assertEquals(reference.value(), production.value());

        FeatureRecord stringRecord =
                new FeatureRecord(
                        "typed", "", new PointGeometry(new Coordinate(1, 2)), Map.of("long", "7"));
        FnvOracle stringDigest = new FnvOracle(EvidenceConfiguration.SEED);
        ObservationDigests.addRecord(stringDigest, stringRecord);
        assertFalse(stringDigest.value() == production.value());
    }

    @Test
    void fixtureFactsAndObservationsAreStableAndOwned() {
        List<EvidenceReport.FixtureFact> first =
                FixtureCatalog.facts(EvidenceConfiguration.Profile.SMOKE);
        List<EvidenceReport.FixtureFact> second =
                FixtureCatalog.facts(EvidenceConfiguration.Profile.SMOKE);
        assertEquals(first, second);
        assertEquals(6, first.size());
        assertEquals(1_024, first.getFirst().count());
        assertEquals(2_080, first.get(1).count());
        assertEquals(
                List.of(
                        "PROVENANCE.md",
                        "evidence.jgw",
                        "evidence.jpg",
                        "evidence.pgw",
                        "evidence.png"),
                new ArrayList<>(first.get(5).files().keySet()));
        LinkedHashMap<String, Long> mutable = new LinkedHashMap<>();
        mutable.put("records", 2L);
        EvidenceObservation observation = new EvidenceObservation(1, mutable);
        mutable.put("records", 3L);
        assertEquals(2, observation.counters().get("records"));
        assertThrows(
                IllegalArgumentException.class,
                () -> new EvidenceObservation(1, Map.of("bad", -1L)));
        assertEquals(24, ScenarioOracleV1.frozenDigests().size());
        assertEquals(
                "56d246ef2d1394ce",
                ScenarioOracleV1.frozenDigests().get("SMOKE/vector-zoom-sequence"));

        List<io.github.mundanej.map.api.Feature> pointFeatures =
                FixtureCatalog.symbolLayers(EvidenceConfiguration.Profile.SMOKE)
                        .getFirst()
                        .features();
        VectorMarkerSymbol nativeMarker = (VectorMarkerSymbol) pointFeatures.get(8).symbol();
        assertEquals(
                List.of(
                        VectorPathCommand.MOVE_TO,
                        VectorPathCommand.LINE_TO,
                        VectorPathCommand.QUADRATIC_TO,
                        VectorPathCommand.CUBIC_TO,
                        VectorPathCommand.LINE_TO,
                        VectorPathCommand.CLOSE),
                java.util.stream.IntStream.range(0, nativeMarker.path().commandCount())
                        .mapToObj(nativeMarker.path()::commandAt)
                        .toList());
        CompositeSymbol transformed = (CompositeSymbol) pointFeatures.get(11).symbol();
        VectorMarkerSymbol transformedChild =
                (VectorMarkerSymbol) transformed.children().getFirst();
        assertEquals(3.0, transformedChild.placement().offsetX());
        assertEquals(-2.0, transformedChild.placement().offsetY());
        assertEquals(30.0, transformedChild.placement().rotationDegrees());
        assertEquals(
                SymbolRotationMode.SCREEN_RELATIVE, transformedChild.placement().rotationMode());
    }

    @Test
    void mainRejectsArgumentsAndOutputPaths() {
        RuntimeSettings settings = RuntimeSettings.install();
        String previousOutput = System.getProperty("performanceOutput");
        String previousProfile = System.getProperty("performanceProfile");
        try {
            System.setProperty("performanceProfile", "SMOKE");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PerformanceEvidenceMain.main(new String[] {"unexpected"}));
            System.setProperty("performanceOutput", "relative");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> PerformanceEvidenceMain.main(new String[0]));
        } finally {
            restore("performanceOutput", previousOutput);
            restore("performanceProfile", previousProfile);
            settings.restore();
        }
    }

    @Test
    void reportRenderersReuseOneImmutableCapture() throws Exception {
        RuntimeSettings settings = RuntimeSettings.install();
        try {
            EvidenceConfiguration configuration =
                    new EvidenceConfiguration(
                            EvidenceConfiguration.Profile.SMOKE,
                            EvidenceConfiguration.SEED,
                            0,
                            1,
                            Optional.of("memory-query-full"),
                            Optional.empty(),
                            true);
            List<EvidenceScenario> scenarios =
                    ScenarioRegistry.create(configuration.profile(), temporary.resolve("single"));
            EvidenceReport report =
                    new EvidenceRunner().run(configuration, List.of(scenarios.getFirst()));
            byte[] first = report.json();
            byte[] second = report.json();
            assertArrayEquals(first, second);
            String text = new String(first, StandardCharsets.UTF_8);
            assertFalse(text.contains(System.getProperty("user.home")));
            assertFalse(text.contains(temporary.toString()));
            assertFalse(text.contains("timestamp"));
        } finally {
            settings.restore();
        }
    }

    @Test
    void lifecycleSeparatesIterationsUsesEdtAndSuppressesCleanupFailure() {
        RuntimeSettings settings = RuntimeSettings.install();
        try {
            AtomicInteger prepares = new AtomicInteger();
            AtomicInteger finishes = new AtomicInteger();
            AtomicInteger closes = new AtomicInteger();
            EvidenceObservation expected = new EvidenceObservation(17, Map.of("frames", 1L));
            EvidenceScenario scenario =
                    new EvidenceScenario() {
                        @Override
                        public String id() {
                            return "fake";
                        }

                        @Override
                        public String nextExperiment() {
                            return "none";
                        }

                        @Override
                        public long batchOperations() {
                            return 1;
                        }

                        @Override
                        public String workUnit() {
                            return "frames";
                        }

                        @Override
                        public String sourceCacheState() {
                            return "NOT_APPLICABLE";
                        }

                        @Override
                        public void prepareSample() {
                            prepares.incrementAndGet();
                        }

                        @Override
                        public void runTimedBatch() {
                            assertTrue(javax.swing.SwingUtilities.isEventDispatchThread());
                        }

                        @Override
                        public EvidenceObservation observeSample() {
                            return expected;
                        }

                        @Override
                        public boolean runsOnEdt() {
                            return true;
                        }

                        @Override
                        public void finishSample() {
                            finishes.incrementAndGet();
                        }

                        @Override
                        public ScenarioOracle oracle() {
                            return ScenarioOracleV1.exact(expected);
                        }

                        @Override
                        public void close() {
                            closes.incrementAndGet();
                        }
                    };
            EvidenceConfiguration configuration =
                    new EvidenceConfiguration(
                            EvidenceConfiguration.Profile.SMOKE,
                            EvidenceConfiguration.SEED,
                            2,
                            3,
                            Optional.empty(),
                            Optional.empty(),
                            true);
            new EvidenceRunner().run(configuration, List.of(scenario));
            assertEquals(5, prepares.get());
            assertEquals(5, finishes.get());
            assertEquals(1, closes.get());

            EvidenceScenario failing = failingScenario(false);
            IllegalStateException failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> new EvidenceRunner().run(configuration, List.of(failing)));
            assertEquals("timed", failure.getMessage());
            assertEquals(1, failure.getSuppressed().length);
            assertEquals("finish", failure.getSuppressed()[0].getMessage());

            IllegalStateException prepareFailure =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    new EvidenceRunner()
                                            .run(configuration, List.of(failingScenario(true))));
            assertEquals("prepare", prepareFailure.getMessage());
            assertEquals("finish", prepareFailure.getSuppressed()[0].getMessage());

            IllegalArgumentException invalidBatch =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    new EvidenceRunner()
                                            .run(
                                                    configuration,
                                                    List.of(failingScenario(false, 0))));
            assertEquals("Batch operations must be positive", invalidBatch.getMessage());
        } catch (Exception failure) {
            throw new AssertionError(failure);
        } finally {
            settings.restore();
        }
    }

    @Test
    void filteredRunUsesSourceFactoryAndClosesEveryDeclaredScenario() throws Exception {
        RuntimeSettings settings = RuntimeSettings.install();
        Path workspace = temporary.resolve("filtered");
        List<FeatureSource> opened = new ArrayList<>();
        try {
            List<EvidenceScenario> scenarios =
                    ScenarioRegistry.create(
                            EvidenceConfiguration.Profile.SMOKE,
                            workspace,
                            (id, records) -> {
                                FeatureSource source =
                                        InMemoryFeatureSource.open(
                                                new SourceIdentity(id, id),
                                                records,
                                                Optional.empty(),
                                                Optional.of(
                                                        CrsMetadata.recognized(
                                                                CrsDefinitions.EPSG_3857,
                                                                Optional.of("EPSG:3857"),
                                                                Optional.empty())),
                                                FeatureSourceLimits.LEVEL_1);
                                opened.add(source);
                                return source;
                            });
            EvidenceConfiguration configuration =
                    new EvidenceConfiguration(
                            EvidenceConfiguration.Profile.SMOKE,
                            EvidenceConfiguration.SEED,
                            0,
                            1,
                            Optional.of("dense-vector-render"),
                            Optional.empty(),
                            true);
            new EvidenceRunner().run(configuration, scenarios);
            assertEquals(1, opened.size());
            assertTrue(opened.getFirst().isClosed());
            assertFalse(Files.exists(workspace));
        } finally {
            settings.restore();
        }
    }

    @Test
    void constructionFailureClosesAlreadyCreatedScenariosInReverseOrder() {
        Path workspace = temporary.resolve("construction-failure");
        List<String> closed = new ArrayList<>();
        IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ScenarioRegistry.createForConstructionTest(
                                        EvidenceConfiguration.Profile.SMOKE,
                                        workspace,
                                        scenarios -> {
                                            scenarios.add(closeTrackingScenario("first", closed));
                                            scenarios.add(closeTrackingScenario("second", closed));
                                            scenarios.add(closeTrackingScenario("third", closed));
                                            throw new IllegalStateException("planned");
                                        }));
        assertEquals("planned", failure.getMessage());
        assertEquals(List.of("third", "second", "first"), closed);
        assertFalse(Files.exists(workspace));
    }

    @Test
    void cleanupActionsContinueAfterFailureAndSuppressLaterFailures() {
        List<String> closed = new ArrayList<>();
        IllegalStateException first = new IllegalStateException("first");
        IllegalArgumentException second = new IllegalArgumentException("second");
        IllegalStateException actual =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                ScenarioRegistry.closeActions(
                                        () -> {
                                            closed.add("view");
                                            throw first;
                                        },
                                        () -> {
                                            closed.add("source");
                                            throw second;
                                        },
                                        () -> closed.add("fixture")));
        assertSame(first, actual);
        assertEquals(List.of("view", "source", "fixture"), closed);
        assertArrayEquals(new Throwable[] {second}, actual.getSuppressed());
    }

    private static EvidenceScenario closeTrackingScenario(String id, List<String> closed) {
        return new EvidenceScenario() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String nextExperiment() {
                return "none";
            }

            @Override
            public long batchOperations() {
                return 1;
            }

            @Override
            public String workUnit() {
                return "items";
            }

            @Override
            public String sourceCacheState() {
                return "NOT_APPLICABLE";
            }

            @Override
            public void prepareSample() {}

            @Override
            public void runTimedBatch() {}

            @Override
            public EvidenceObservation observeSample() {
                return new EvidenceObservation(1, Map.of());
            }

            @Override
            public ScenarioOracle oracle() {
                return ignored -> {};
            }

            @Override
            public void close() {
                closed.add(id);
            }
        };
    }

    private static EvidenceScenario failingScenario(boolean failPrepare) {
        return failingScenario(failPrepare, 1);
    }

    private static EvidenceScenario failingScenario(boolean failPrepare, long operations) {
        return new EvidenceScenario() {
            @Override
            public String id() {
                return "failing";
            }

            @Override
            public String nextExperiment() {
                return "none";
            }

            @Override
            public long batchOperations() {
                return operations;
            }

            @Override
            public String workUnit() {
                return "items";
            }

            @Override
            public String sourceCacheState() {
                return "NOT_APPLICABLE";
            }

            @Override
            public void prepareSample() {
                if (failPrepare) {
                    throw new IllegalStateException("prepare");
                }
            }

            @Override
            public void runTimedBatch() {
                if (failPrepare) {
                    throw new AssertionError("unreachable");
                }
                throw new IllegalStateException("timed");
            }

            @Override
            public EvidenceObservation observeSample() {
                throw new AssertionError("unreachable");
            }

            @Override
            public void finishSample() {
                throw new IllegalStateException("finish");
            }

            @Override
            public ScenarioOracle oracle() {
                return ignored -> {};
            }
        };
    }

    private static void assertEquivalentScenarioFacts(String json, String markdown) {
        Pattern scenario =
                Pattern.compile(
                        "\\{\\\"id\\\": \\\"([^\\\"]+)\\\""
                                + ", \\\"nextExperiment\\\": \\\"([^\\\"]+)\\\""
                                + ", \\\"batchOperations\\\": ([0-9]+)"
                                + ", \\\"workUnit\\\": \\\"([^\\\"]+)\\\""
                                + ", \\\"sourceCacheState\\\": \\\"([^\\\"]+)\\\""
                                + ", \\\"viewCacheState\\\": \\\"([^\\\"]+)\\\""
                                + ", \\\"semanticDigest\\\": \\\"([^\\\"]+)\\\""
                                + ", \\\"semanticCounters\\\": \\{([^}]*)}"
                                + ", \\\"rawNanos\\\": (\\[[^]]+])"
                                + ", \\\"medianNanos\\\": ([0-9]+)"
                                + ", \\\"p95Nanos\\\": ([0-9]+)"
                                + ", \\\"operationsPerSecondMilli\\\": ([0-9]+)}");
        Matcher matcher = scenario.matcher(json);
        int matched = 0;
        while (matcher.find()) {
            matched++;
            assertTrue(markdown.contains("### `" + matcher.group(1) + "`"));
            assertTrue(markdown.contains("Next experiment: `" + matcher.group(2) + "`"));
            assertTrue(
                    markdown.contains(
                            "Batch: " + matcher.group(3) + " `" + matcher.group(4) + "`"));
            assertTrue(markdown.contains("Source cache: `" + matcher.group(5) + "`"));
            assertTrue(markdown.contains("View cache: `" + matcher.group(6) + "`"));
            assertTrue(markdown.contains("Semantic digest: `" + matcher.group(7) + "`"));
            Matcher counters =
                    Pattern.compile("\\\"([^\\\"]+)\\\": ([0-9]+)").matcher(matcher.group(8));
            while (counters.find()) {
                assertTrue(
                        markdown.contains("`" + counters.group(1) + "=" + counters.group(2) + "`"));
            }
            assertTrue(markdown.contains("Raw nanos: `" + matcher.group(9) + "`"));
            assertTrue(markdown.contains("Median nanos: " + matcher.group(10)));
            assertTrue(markdown.contains("p95 nanos: " + matcher.group(11)));
            assertTrue(markdown.contains("Operations per second milli: " + matcher.group(12)));
        }
        assertEquals(ScenarioRegistry.ids().size(), matched);
    }

    private static int count(String text, String needle) {
        int result = 0;
        int offset = 0;
        while ((offset = text.indexOf(needle, offset)) >= 0) {
            result++;
            offset += needle.length();
        }
        return result;
    }

    private static void restore(String name, String value) {
        if (value == null) {
            System.clearProperty(name);
        } else {
            System.setProperty(name, value);
        }
    }

    private record RuntimeSettings(Locale locale, TimeZone zone) {
        static RuntimeSettings install() {
            RuntimeSettings result =
                    new RuntimeSettings(Locale.getDefault(), TimeZone.getDefault());
            Locale.setDefault(Locale.US);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            System.setProperty("java.awt.headless", "true");
            return result;
        }

        void restore() {
            Locale.setDefault(locale);
            TimeZone.setDefault(zone);
        }
    }
}
