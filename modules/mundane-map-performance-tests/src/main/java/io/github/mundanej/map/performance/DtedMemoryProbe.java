package io.github.mundanej.map.performance;

import io.github.mundanej.map.api.ElevationSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.io.dted.DtedFiles;
import io.github.mundanej.map.io.dted.DtedOpenOptions;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/** Fresh-JVM observational memory probe for the maximum eager DTED publication. */
final class DtedMemoryProbe {
    static final String SCHEMA = "mundane-map-dted-memory-probe/v1";

    private DtedMemoryProbe() {}

    static void run(Path output) throws Exception {
        Path workspace = output.resolve("dted-memory-probe-work");
        Files.createDirectories(workspace);
        Path fixture = workspace.resolve("e000/n00.dt2");
        DtedEvidenceFixture.Fixture generated =
                DtedEvidenceFixture.write(fixture, DtedEvidenceFixture.MAXIMUM);
        Snapshot before = snapshot("beforeOpen");
        com.sun.management.ThreadMXBean threads = allocatedBean();
        long allocatedBefore = allocated(threads);
        ElevationSource source =
                DtedFiles.open(
                        new SourceIdentity("dted-memory-probe", ""),
                        fixture,
                        DtedOpenOptions.defaults());
        Snapshot published = snapshot("afterPublication");
        source.close();
        Snapshot closed = snapshot("afterClose");
        long allocatedAfter = allocated(threads);
        Long delta =
                allocatedBefore < 0 || allocatedAfter < allocatedBefore
                        ? null
                        : allocatedAfter - allocatedBefore;
        byte[] report = json(generated, before, published, closed, threads != null, delta);
        if (report.length > 65_536) {
            throw new IllegalStateException("DTED memory probe report exceeds 65536 bytes");
        }
        Files.write(output.resolve("dted-memory-probe-v1.json"), report);
        Files.delete(fixture);
        Files.delete(workspace.resolve("e000"));
        Files.delete(workspace);
    }

    static void validate(Path path) throws Exception {
        byte[] bytes = Files.readAllBytes(path);
        if (bytes.length == 0 || bytes.length > 65_536) {
            throw new IllegalStateException("DTED memory probe size is invalid");
        }
        String text;
        try {
            text =
                    StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(CodingErrorAction.REPORT)
                            .onUnmappableCharacter(CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(bytes))
                            .toString();
        } catch (java.nio.charset.CharacterCodingException failure) {
            throw new IllegalStateException("DTED memory probe is not UTF-8", failure);
        }
        if (!text.endsWith("\n") || text.indexOf('\r') >= 0) {
            throw new IllegalStateException("DTED memory probe line endings are invalid");
        }
        String[] lines = text.split("\n", -1);
        int index = 0;
        requireLine(lines, index++, "{");
        requireLine(lines, index++, "  \"schemaVersion\": \"" + SCHEMA + "\",");
        requireLine(
                lines,
                index++,
                "  \"fixture\": {\"id\": \"dted-zone-i-l2-v1\", \"columns\": 3601, "
                        + "\"rows\": 3601, \"samples\": 12967201, \"bytes\": 25981042, "
                        + "\"sha256\": \"2e1e3adcb1f65d41d93ad5d31c63211522ca830bd8f2716415070e3ae8b72330\"},");
        requireMatch(
                lines,
                index++,
                "  \"environment\": \\{\"javaVersion\": \"[A-Za-z0-9 ._+()#,-]{1,128}\", "
                        + "\"vm\": \"[A-Za-z0-9 ._+()#,-]{1,128}\", "
                        + "\"os\": \"[A-Za-z0-9 ._+()#,-]{1,128}\"\\},");
        requireLine(
                lines,
                index++,
                "  \"jvmSettings\": [\"-Xms512m\", \"-Xmx512m\", \"-XX:+UseG1GC\"],");
        requireMatch(
                lines,
                index++,
                "  \"capabilities\": \\{\"threadAllocatedBytes\": (true|false)\\},");
        requireLine(lines, index++, "  \"snapshots\": [");
        index = requireSnapshot(lines, index, "beforeOpen", true);
        index = requireSnapshot(lines, index, "afterPublication", true);
        index = requireSnapshot(lines, index, "afterClose", false);
        requireLine(lines, index++, "  \"poolPeaks\": [");
        String previousPool = null;
        int pools = 0;
        while (index < lines.length && lines[index].startsWith("    {\"name\":")) {
            var matcher =
                    java.util.regex.Pattern.compile(
                                    "    \\{\"name\": \"([A-Za-z0-9 ._+()#,-]{1,128})\", "
                                            + "\"usedBytes\": ([0-9]+), "
                                            + "\"committedBytes\": ([0-9]+), "
                                            + "\"maxBytes\": ([1-9][0-9]*|\"UNAVAILABLE\")\\}(,?)")
                            .matcher(lines[index]);
            if (!matcher.matches()
                    || (previousPool != null && previousPool.compareTo(matcher.group(1)) >= 0)) {
                throw new IllegalStateException("DTED memory probe pool row is invalid");
            }
            previousPool = matcher.group(1);
            pools++;
            index++;
            boolean morePools = index < lines.length && lines[index].startsWith("    {\"name\":");
            if (morePools != matcher.group(5).equals(",")) {
                throw new IllegalStateException("DTED memory probe pool delimiters are invalid");
            }
        }
        if (pools == 0) {
            throw new IllegalStateException("DTED memory probe has no pool peaks");
        }
        requireLine(lines, index++, "  ],");
        requireMatch(lines, index++, "  \"allocatedBytesDelta\": (null|[0-9]+),");
        requireLine(
                lines,
                index++,
                "  \"logicalStorage\": {\"maskBytes\": 1620904, \"publishedBytes\": 105358512, "
                        + "\"recordBytes\": 7214, \"openPeakBytes\": 210726938}");
        requireLine(lines, index++, "}");
        requireLine(lines, index++, "");
        if (index != lines.length) {
            throw new IllegalStateException("DTED memory probe has trailing content");
        }
    }

    private static int requireSnapshot(String[] lines, int index, String phase, boolean comma) {
        requireMatch(
                lines,
                index,
                "    \\{\"phase\": \""
                        + phase
                        + "\", \"usedBytes\": [0-9]+, \"committedBytes\": [0-9]+, "
                        + "\"maxBytes\": [0-9]+\\}"
                        + (comma ? "," : "\\],"));
        return index + 1;
    }

    private static void requireLine(String[] lines, int index, String expected) {
        if (index >= lines.length || !lines[index].equals(expected)) {
            throw new IllegalStateException("DTED memory probe structure is invalid");
        }
    }

    private static void requireMatch(String[] lines, int index, String expression) {
        if (index >= lines.length || !lines[index].matches(expression)) {
            throw new IllegalStateException("DTED memory probe field is invalid");
        }
    }

    private static byte[] json(
            DtedEvidenceFixture.Fixture fixture,
            Snapshot before,
            Snapshot published,
            Snapshot closed,
            boolean allocationSupported,
            Long allocationDelta) {
        String javaVersion = bounded(System.getProperty("java.version"));
        String vm = bounded(System.getProperty("java.vm.name"));
        String os = bounded(System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        List<MemoryPoolMXBean> pools =
                ManagementFactory.getMemoryPoolMXBeans().stream()
                        .sorted(Comparator.comparing(MemoryPoolMXBean::getName))
                        .toList();
        StringBuilder result = new StringBuilder(8_192);
        result.append("{\n  \"schemaVersion\": \"")
                .append(SCHEMA)
                .append("\",\n")
                .append("  \"fixture\": {\"id\": \"")
                .append(fixture.id())
                .append("\", \"columns\": 3601, \"rows\": 3601, \"samples\": 12967201, \"bytes\": ")
                .append(fixture.bytes())
                .append(", \"sha256\": \"")
                .append(fixture.sha256())
                .append("\"},\n")
                .append("  \"environment\": {\"javaVersion\": \"")
                .append(javaVersion)
                .append("\", \"vm\": \"")
                .append(vm)
                .append("\", \"os\": \"")
                .append(os)
                .append("\"},\n")
                .append("  \"jvmSettings\": [\"-Xms512m\", \"-Xmx512m\", \"-XX:+UseG1GC\"],\n")
                .append("  \"capabilities\": {\"threadAllocatedBytes\": ")
                .append(allocationSupported)
                .append("},\n")
                .append("  \"snapshots\": [");
        appendSnapshot(result, before, false);
        appendSnapshot(result, published, true);
        appendSnapshot(result, closed, true);
        result.append("],\n  \"poolPeaks\": [");
        for (int index = 0; index < pools.size(); index++) {
            MemoryPoolMXBean pool = pools.get(index);
            MemoryUsage peak = pool.getPeakUsage();
            if (index > 0) {
                result.append(',');
            }
            result.append("\n    {\"name\": \"")
                    .append(bounded(pool.getName()))
                    .append("\", \"usedBytes\": ")
                    .append(nonnegative(peak.getUsed()))
                    .append(", \"committedBytes\": ")
                    .append(nonnegative(peak.getCommitted()))
                    .append(", \"maxBytes\": ")
                    .append(maximumValue(peak.getMax()))
                    .append('}');
        }
        result.append("\n  ],\n  \"allocatedBytesDelta\": ")
                .append(allocationDelta == null ? "null" : allocationDelta)
                .append(",\n  \"logicalStorage\": {\"maskBytes\": 1620904, ")
                .append("\"publishedBytes\": 105358512, \"recordBytes\": 7214, ")
                .append("\"openPeakBytes\": 210726938}\n}\n");
        return result.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static void appendSnapshot(StringBuilder target, Snapshot snapshot, boolean comma) {
        if (comma) {
            target.append(',');
        }
        target.append("\n    {\"phase\": \"")
                .append(snapshot.phase())
                .append("\", \"usedBytes\": ")
                .append(snapshot.used())
                .append(", \"committedBytes\": ")
                .append(snapshot.committed())
                .append(", \"maxBytes\": ")
                .append(snapshot.max())
                .append('}');
    }

    private static Snapshot snapshot(String phase) {
        MemoryUsage usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        return new Snapshot(
                phase,
                nonnegative(usage.getUsed()),
                nonnegative(usage.getCommitted()),
                nonnegative(usage.getMax()));
    }

    private static com.sun.management.ThreadMXBean allocatedBean() {
        if (ManagementFactory.getThreadMXBean() instanceof com.sun.management.ThreadMXBean bean
                && bean.isThreadAllocatedMemorySupported()) {
            if (!bean.isThreadAllocatedMemoryEnabled()) {
                bean.setThreadAllocatedMemoryEnabled(true);
            }
            return bean;
        }
        return null;
    }

    private static long allocated(com.sun.management.ThreadMXBean bean) {
        return bean == null ? -1 : bean.getCurrentThreadAllocatedBytes();
    }

    private static long nonnegative(long value) {
        return Math.max(0, value);
    }

    private static String maximumValue(long value) {
        return value <= 0 ? "\"UNAVAILABLE\"" : Long.toString(value);
    }

    static String bounded(String value) {
        String normalized =
                value == null ? "UNKNOWN" : value.replaceAll("[^A-Za-z0-9 ._+()#,\\-]", "_");
        return normalized.substring(0, Math.min(128, normalized.length()));
    }

    private record Snapshot(String phase, long used, long committed, long max) {}
}
