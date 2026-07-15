package io.github.mundanej.map.performance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class EvidenceReport {
    static final String SCHEMA = "mundane-map-performance-evidence/v1";
    private static final java.util.regex.Pattern ENVIRONMENT_TEXT =
            java.util.regex.Pattern.compile("[A-Za-z0-9][A-Za-z0-9 ._+()/#:,\\-]{0,127}");
    private final EvidenceConfiguration configuration;
    private final Map<String, String> environment;
    private final List<FixtureFact> fixtures;
    private final List<ScenarioFact> scenarios;

    private EvidenceReport(
            EvidenceConfiguration configuration,
            Map<String, String> environment,
            List<FixtureFact> fixtures,
            List<ScenarioFact> scenarios) {
        this.configuration = configuration;
        this.environment = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(environment));
        this.fixtures = List.copyOf(fixtures);
        this.scenarios = List.copyOf(scenarios);
    }

    static EvidenceReport capture(
            EvidenceConfiguration configuration,
            List<EvidenceScenario> scenarios,
            List<EvidenceSample> samples) {
        Map<String, EvidenceScenario> byId = new LinkedHashMap<>();
        scenarios.forEach(item -> byId.put(item.id(), item));
        List<ScenarioFact> facts = new ArrayList<>();
        for (EvidenceSample sample : samples) {
            EvidenceScenario scenario = byId.get(sample.scenarioId());
            facts.add(ScenarioFact.from(scenario, sample));
        }
        return new EvidenceReport(
                configuration, environment(), FixtureCatalog.facts(configuration.profile()), facts);
    }

    byte[] json() {
        StringBuilder result = new StringBuilder(32_768);
        result.append("{\n  \"schemaVersion\": \"").append(SCHEMA).append("\",");
        configuration
                .revision()
                .ifPresent(
                        value -> result.append("\n  \"revision\": \"").append(value).append("\","));
        result.append("\n  \"environment\": ");
        appendStringMap(result, environment, 2);
        result.append(",\n  \"configuration\": {\n");
        result.append("    \"profile\": \"").append(configuration.profile()).append("\",\n");
        result.append("    \"seed\": \"0x4d554e44414e454a\",\n");
        result.append("    \"warmups\": ").append(configuration.warmups()).append(",\n");
        result.append("    \"measurements\": ").append(configuration.measurements()).append(",\n");
        result.append("    \"investigation\": ")
                .append(configuration.investigation())
                .append(",\n");
        result.append("    \"jvmSettings\": ");
        appendStrings(result, EvidenceConfiguration.JVM_SETTINGS);
        result.append("\n  },\n  \"fixtures\": [\n");
        for (int index = 0; index < fixtures.size(); index++) {
            FixtureFact fixture = fixtures.get(index);
            result.append("    {\"id\": \"")
                    .append(fixture.id())
                    .append("\", \"count\": ")
                    .append(fixture.count())
                    .append(", \"semanticDigest\": \"")
                    .append(hex(fixture.digest()))
                    .append("\", \"files\": ");
            appendStringMap(result, fixture.files(), 4);
            result.append('}');
            result.append(index + 1 == fixtures.size() ? "\n" : ",\n");
        }
        result.append("  ],\n  \"scenarios\": [\n");
        for (int index = 0; index < scenarios.size(); index++) {
            appendScenarioJson(result, scenarios.get(index));
            result.append(index + 1 == scenarios.size() ? "\n" : ",\n");
        }
        result.append("  ]\n}\n");
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    byte[] markdown() {
        StringBuilder result = new StringBuilder(16_384);
        result.append("# Mundane map performance evidence v1\n\n")
                .append(
                        "Durations are environment-specific evidence, not API claims or portable thresholds. ")
                .append(
                        "JVM warmup, scheduling, filesystem caches, and machine load add cross-run noise.\n\n")
                .append("- Schema: `")
                .append(SCHEMA)
                .append("`\n")
                .append("- Profile: `")
                .append(configuration.profile())
                .append("`\n")
                .append("- Seed: `0x4d554e44414e454a`\n")
                .append("- Warmups: ")
                .append(configuration.warmups())
                .append("\n")
                .append("- Measurements: ")
                .append(configuration.measurements())
                .append("\n")
                .append("- Investigation: ")
                .append(configuration.investigation())
                .append("\n");
        configuration
                .revision()
                .ifPresent(value -> result.append("- Revision: `").append(value).append("`\n"));
        result.append("\n## Environment\n\n");
        environment.forEach(
                (key, value) ->
                        result.append("- ").append(key).append(": `").append(value).append("`\n"));
        result.append("\n## JVM settings\n\n");
        for (String setting : EvidenceConfiguration.JVM_SETTINGS) {
            result.append("- `").append(setting).append("`\n");
        }
        result.append("\n## Fixtures\n");
        for (FixtureFact fixture : fixtures) {
            result.append("\n### `")
                    .append(fixture.id())
                    .append("`\n\n- Count: ")
                    .append(fixture.count())
                    .append("\n- Semantic digest: `")
                    .append(hex(fixture.digest()))
                    .append("`\n- Files:");
            if (fixture.files().isEmpty()) {
                result.append(" none\n");
            } else {
                result.append('\n');
                fixture.files()
                        .forEach(
                                (name, fact) ->
                                        result.append("  - `")
                                                .append(name)
                                                .append("`: `")
                                                .append(fact)
                                                .append("`\n"));
            }
        }
        result.append("\n## Scenarios\n");
        for (ScenarioFact scenario : scenarios) {
            result.append("\n### `")
                    .append(scenario.id())
                    .append("`\n\n- Next experiment: `")
                    .append(scenario.next())
                    .append("`\n- Batch: ")
                    .append(scenario.operations())
                    .append(" `")
                    .append(scenario.unit())
                    .append("`\n- Source cache: `")
                    .append(scenario.sourceCache())
                    .append("`\n- View cache: `")
                    .append(scenario.viewCache())
                    .append("`\n- Semantic digest: `")
                    .append(hex(scenario.digest()))
                    .append("`\n- Semantic counters: ");
            appendMarkdownCounters(result, scenario.counters());
            result.append("\n- Raw nanos: `")
                    .append(scenario.rawNanos())
                    .append("`\n- Median nanos: ")
                    .append(scenario.median())
                    .append("\n- p95 nanos: ")
                    .append(scenario.p95())
                    .append("\n- Operations per second milli: ")
                    .append(scenario.throughputMilli())
                    .append(" (`")
                    .append(formatMilli(scenario.throughputMilli()))
                    .append(' ')
                    .append(scenario.unit())
                    .append("/s`)\n");
        }
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendMarkdownCounters(StringBuilder result, Map<String, Long> counters) {
        int index = 0;
        for (Map.Entry<String, Long> entry : counters.entrySet()) {
            if (index++ > 0) {
                result.append(", ");
            }
            result.append('`')
                    .append(entry.getKey())
                    .append('=')
                    .append(entry.getValue())
                    .append('`');
        }
    }

    private static void appendScenarioJson(StringBuilder result, ScenarioFact fact) {
        result.append("    {\"id\": \"")
                .append(fact.id())
                .append("\", \"nextExperiment\": \"")
                .append(fact.next())
                .append("\", \"batchOperations\": ")
                .append(fact.operations())
                .append(", \"workUnit\": \"")
                .append(fact.unit())
                .append("\", \"sourceCacheState\": \"")
                .append(fact.sourceCache())
                .append("\", \"viewCacheState\": \"")
                .append(fact.viewCache())
                .append("\", \"semanticDigest\": \"")
                .append(hex(fact.digest()))
                .append("\", \"semanticCounters\": ");
        appendLongMap(result, fact.counters());
        result.append(", \"rawNanos\": ");
        appendLongs(result, fact.rawNanos());
        result.append(", \"medianNanos\": ")
                .append(fact.median())
                .append(", \"p95Nanos\": ")
                .append(fact.p95())
                .append(", \"operationsPerSecondMilli\": ")
                .append(fact.throughputMilli())
                .append('}');
    }

    private static Map<String, String> environment() {
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        addEnvironment(result, "javaSpecification", "java.specification.version");
        addEnvironment(result, "javaRuntime", "java.runtime.version");
        addEnvironment(result, "javaVendor", "java.vendor");
        addEnvironment(result, "javaVmName", "java.vm.name");
        addEnvironment(result, "javaVmVersion", "java.vm.version");
        addEnvironment(result, "osName", "os.name");
        addEnvironment(result, "osVersion", "os.version");
        addEnvironment(result, "osArch", "os.arch");
        result.put("processors", Integer.toString(Runtime.getRuntime().availableProcessors()));
        result.put("maximumHeap", Long.toString(Runtime.getRuntime().maxMemory()));
        List<String> collectors =
                ManagementFactory.getGarbageCollectorMXBeans().stream()
                        .map(GarbageCollectorMXBean::getName)
                        .sorted(Comparator.naturalOrder())
                        .toList();
        result.put("garbageCollectors", requireEnvironment(String.join(",", collectors), "gc"));
        result.put("headless", System.getProperty("java.awt.headless"));
        return result;
    }

    private static void addEnvironment(Map<String, String> target, String key, String property) {
        String value = System.getProperty(property);
        if (value == null) {
            throw new IllegalStateException("Invalid environment property: " + property);
        }
        target.put(key, requireEnvironment(value, property));
    }

    private static String requireEnvironment(String value, String property) {
        if (!ENVIRONMENT_TEXT.matcher(value).matches()) {
            throw new IllegalStateException("Invalid environment property: " + property);
        }
        return value;
    }

    private static void appendStringMap(
            StringBuilder result, Map<String, String> values, int indent) {
        result.append("{\n");
        int current = 0;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            result.append(" ".repeat(indent + 2))
                    .append('"')
                    .append(entry.getKey())
                    .append("\": \"")
                    .append(escape(entry.getValue()))
                    .append('"')
                    .append(++current == values.size() ? "\n" : ",\n");
        }
        result.append(" ".repeat(indent)).append('}');
    }

    private static void appendLongMap(StringBuilder result, Map<String, Long> values) {
        result.append('{');
        int current = 0;
        for (Map.Entry<String, Long> entry : values.entrySet()) {
            if (current++ > 0) {
                result.append(", ");
            }
            result.append('"').append(entry.getKey()).append("\": ").append(entry.getValue());
        }
        result.append('}');
    }

    private static void appendStrings(StringBuilder result, List<String> values) {
        result.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append('"').append(escape(values.get(index))).append('"');
        }
        result.append(']');
    }

    private static void appendLongs(StringBuilder result, List<Long> values) {
        result.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                result.append(", ");
            }
            result.append(values.get(index));
        }
        result.append(']');
    }

    private static String escape(String value) {
        StringBuilder result = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char item = value.charAt(index);
            if (item == '"' || item == '\\') {
                result.append('\\').append(item);
            } else {
                result.append(item);
            }
        }
        return result.toString();
    }

    private static String formatMilli(long value) {
        return String.format(Locale.ROOT, "%d.%03d", value / 1000, value % 1000);
    }

    static String hex(long value) {
        return String.format(Locale.ROOT, "%016x", value);
    }

    record FixtureFact(String id, long count, long digest, Map<String, String> files) {
        FixtureFact {
            files = java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(files));
        }
    }

    private record ScenarioFact(
            String id,
            String next,
            long operations,
            String unit,
            String sourceCache,
            String viewCache,
            long digest,
            Map<String, Long> counters,
            List<Long> rawNanos,
            long median,
            long p95,
            long throughputMilli) {
        static ScenarioFact from(EvidenceScenario scenario, EvidenceSample sample) {
            return new ScenarioFact(
                    scenario.id(),
                    scenario.nextExperiment(),
                    scenario.batchOperations(),
                    scenario.workUnit(),
                    scenario.sourceCacheState(),
                    scenario.viewCacheState(),
                    sample.observation().digest(),
                    sample.observation().counters(),
                    sample.rawNanos(),
                    sample.medianNanos(),
                    sample.p95Nanos(),
                    sample.operationsPerSecondMilli());
        }
    }
}
