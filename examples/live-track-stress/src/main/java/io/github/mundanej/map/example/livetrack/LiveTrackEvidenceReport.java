package io.github.mundanej.map.example.livetrack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

record LiveTrackEvidenceReport(
        String runId,
        String profile,
        Status status,
        List<Limitation> limitations,
        Configuration configuration,
        Environment environment,
        Phases phases,
        Storage storage,
        Telemetry telemetry,
        Cleanup cleanup,
        List<Diagnostic> diagnostics) {
    static final String SCHEMA = "mundane-map-live-track-evidence/v1";

    enum Status {
        SUCCESS,
        CANCELLED,
        FAILED
    }

    enum Limitation {
        CPU_LIMITED,
        FRAME_CAP_LIMITED,
        BACKLOG_LIMITED,
        INDETERMINATE
    }

    record Configuration(
            int population,
            long seed,
            int workers,
            Integer fpsCap,
            double beta,
            double sigma,
            double measurementStandardDeviation,
            int warmupSeconds,
            int measurementSeconds,
            int viewportWidth,
            int viewportHeight) {}

    record Environment(
            String os,
            String architecture,
            String cpu,
            int availableProcessors,
            String javaVersion,
            String javaVendor,
            long maximumHeap) {}

    record Phase(
            long wallNanos,
            long scheduledReports,
            long processedReports,
            long requestedFrames,
            long completedFrames,
            long paintedFrames) {}

    record Phases(Phase initialization, Phase warmup, Phase measurement) {}

    record Storage(
            long logicalTrackBytes,
            long packedPositionBytes,
            long frameBufferBytes,
            long largestAllocationBytes,
            long maximumHeap,
            long peakObservedHeap) {}

    record Telemetry(
            int simulationSecond,
            long scheduledReports,
            long processedReports,
            long rejectedReports,
            long lateReports,
            long pendingReports,
            double processedReportsPerSecond,
            long requestedFrames,
            long completedFrames,
            long paintedFrames,
            long skippedRequests,
            long staleDiscards,
            long replacedPendingFrames,
            double achievedFps,
            long buildP50Nanos,
            long buildP95Nanos,
            long buildP99Nanos,
            long buildMaximumNanos,
            int backlogSeconds,
            double shardReportSkew,
            double shardWorkSkew,
            double positionRmse,
            long innovationCount,
            double normalizedInnovationMean,
            double normalizedInnovationMaximum) {}

    record Cleanup(boolean workersTerminated, boolean resourcesClosed, boolean workspaceRemoved) {}

    record Diagnostic(String category, String severity, String message) {
        Diagnostic {
            requireText(category, "diagnostic category");
            requireText(severity, "diagnostic severity");
            Objects.requireNonNull(message, "diagnostic message");
        }
    }

    LiveTrackEvidenceReport {
        requireText(runId, "runId");
        if (!profile.equals("10k") && !profile.equals("100k") && !profile.equals("1m")) {
            throw new IllegalArgumentException("unsupported evidence profile");
        }
        Objects.requireNonNull(status, "status");
        limitations = List.copyOf(limitations);
        if (limitations.isEmpty()) {
            throw new IllegalArgumentException(
                    "at least one limitation classification is required");
        }
        Objects.requireNonNull(configuration, "configuration");
        Objects.requireNonNull(environment, "environment");
        Objects.requireNonNull(phases, "phases");
        Objects.requireNonNull(storage, "storage");
        Objects.requireNonNull(telemetry, "telemetry");
        Objects.requireNonNull(cleanup, "cleanup");
        diagnostics = List.copyOf(diagnostics);
    }

    String toJson() {
        StringBuilder json = new StringBuilder(4_096);
        json.append("{\n");
        property(json, 1, "schema", SCHEMA, true);
        property(json, 1, "runId", runId, true);
        property(json, 1, "profile", profile, true);
        property(json, 1, "status", status.name(), true);
        indent(json, 1).append("\"limitations\": [");
        for (int index = 0; index < limitations.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            string(json, limitations.get(index).name());
        }
        json.append("],\n");
        indent(json, 1).append("\"configuration\": {\n");
        number(json, 2, "population", configuration.population(), true);
        number(json, 2, "seed", configuration.seed(), true);
        number(json, 2, "workers", configuration.workers(), true);
        indent(json, 2).append("\"fpsCap\": ");
        if (configuration.fpsCap() == null) {
            json.append("null");
        } else {
            json.append(configuration.fpsCap());
        }
        json.append(",\n");
        decimal(json, 2, "beta", configuration.beta(), true);
        decimal(json, 2, "sigma", configuration.sigma(), true);
        decimal(
                json,
                2,
                "measurementStandardDeviation",
                configuration.measurementStandardDeviation(),
                true);
        number(json, 2, "warmupSeconds", configuration.warmupSeconds(), true);
        number(json, 2, "measurementSeconds", configuration.measurementSeconds(), true);
        number(json, 2, "viewportWidth", configuration.viewportWidth(), true);
        number(json, 2, "viewportHeight", configuration.viewportHeight(), false);
        indent(json, 1).append("},\n");
        indent(json, 1).append("\"environment\": {\n");
        property(json, 2, "os", environment.os(), true);
        property(json, 2, "architecture", environment.architecture(), true);
        property(json, 2, "cpu", environment.cpu(), true);
        number(json, 2, "availableProcessors", environment.availableProcessors(), true);
        property(json, 2, "javaVersion", environment.javaVersion(), true);
        property(json, 2, "javaVendor", environment.javaVendor(), true);
        number(json, 2, "maximumHeap", environment.maximumHeap(), false);
        indent(json, 1).append("},\n");
        indent(json, 1).append("\"phases\": {\n");
        phase(json, "initialization", phases.initialization(), true);
        phase(json, "warmup", phases.warmup(), true);
        phase(json, "measurement", phases.measurement(), false);
        indent(json, 1).append("},\n");
        indent(json, 1).append("\"storage\": {\n");
        number(json, 2, "logicalTrackBytes", storage.logicalTrackBytes(), true);
        number(json, 2, "packedPositionBytes", storage.packedPositionBytes(), true);
        number(json, 2, "frameBufferBytes", storage.frameBufferBytes(), true);
        number(json, 2, "largestAllocationBytes", storage.largestAllocationBytes(), true);
        number(json, 2, "maximumHeap", storage.maximumHeap(), true);
        number(json, 2, "peakObservedHeap", storage.peakObservedHeap(), false);
        indent(json, 1).append("},\n");
        telemetry(json);
        indent(json, 1).append("\"cleanup\": {\n");
        bool(json, 2, "workersTerminated", cleanup.workersTerminated(), true);
        bool(json, 2, "resourcesClosed", cleanup.resourcesClosed(), true);
        bool(json, 2, "workspaceRemoved", cleanup.workspaceRemoved(), false);
        indent(json, 1).append("},\n");
        indent(json, 1).append("\"diagnostics\": [");
        if (!diagnostics.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < diagnostics.size(); index++) {
                Diagnostic diagnostic = diagnostics.get(index);
                indent(json, 2).append("{\n");
                property(json, 3, "category", diagnostic.category(), true);
                property(json, 3, "severity", diagnostic.severity(), true);
                property(json, 3, "message", diagnostic.message(), false);
                indent(json, 2).append('}');
                json.append(index + 1 == diagnostics.size() ? '\n' : ',').append('\n');
            }
            indent(json, 1);
        }
        json.append("]\n}\n");
        return json.toString();
    }

    String toMarkdown() {
        StringBuilder markdown = new StringBuilder(2_048);
        markdown.append("# Live-track evidence — ").append(profile).append("\n\n");
        markdown.append("- Schema: `").append(SCHEMA).append("`\n");
        markdown.append("- Run: `").append(runId).append("`\n");
        markdown.append("- Status: **").append(status).append("**\n");
        markdown.append("- Limitation: ").append(limitations).append("\n");
        markdown.append("- Environment: ")
                .append(environment.os())
                .append(", ")
                .append(environment.cpu())
                .append(", Java ")
                .append(environment.javaVersion())
                .append("\n\n");
        markdown.append("## Configuration and outcome\n\n");
        markdown.append("| Metric | Value |\n| --- | ---: |\n");
        row(markdown, "Population", configuration.population());
        row(markdown, "Workers", configuration.workers());
        row(markdown, "FPS cap", configuration.fpsCap());
        row(markdown, "Warmup seconds", configuration.warmupSeconds());
        row(markdown, "Measurement seconds", configuration.measurementSeconds());
        row(markdown, "Processed reports", telemetry.processedReports());
        row(markdown, "Processed reports/second", format(telemetry.processedReportsPerSecond()));
        row(markdown, "Achieved FPS", format(telemetry.achievedFps()));
        row(markdown, "Frame build p95 (ms)", format(telemetry.buildP95Nanos() / 1_000_000.0));
        row(markdown, "Backlog seconds", telemetry.backlogSeconds());
        row(markdown, "Report shard skew", format(telemetry.shardReportSkew()));
        row(markdown, "Work shard skew", format(telemetry.shardWorkSkew()));
        row(markdown, "Position RMSE (map units)", format(telemetry.positionRmse()));
        row(markdown, "Normalized innovation mean", format(telemetry.normalizedInnovationMean()));
        row(markdown, "Peak observed heap bytes", storage.peakObservedHeap());
        markdown.append("\nCleanup: workers terminated=")
                .append(cleanup.workersTerminated())
                .append(", resources closed=")
                .append(cleanup.resourcesClosed())
                .append(", workspace removed=")
                .append(cleanup.workspaceRemoved())
                .append(".\n");
        if (!diagnostics.isEmpty()) {
            markdown.append("\n## Diagnostics\n\n");
            for (Diagnostic diagnostic : diagnostics) {
                markdown.append("- `")
                        .append(diagnostic.category())
                        .append("` (")
                        .append(diagnostic.severity())
                        .append("): ")
                        .append(diagnostic.message())
                        .append('\n');
            }
        }
        return markdown.toString();
    }

    void writeAtomically(Path outputDirectory) throws IOException {
        Files.createDirectories(outputDirectory);
        writeAtomically(outputDirectory.resolve("live-track-" + profile + ".json"), toJson());
        writeAtomically(outputDirectory.resolve("live-track-" + profile + ".md"), toMarkdown());
    }

    private void telemetry(StringBuilder json) {
        indent(json, 1).append("\"telemetry\": {\n");
        number(json, 2, "simulationSecond", telemetry.simulationSecond(), true);
        number(json, 2, "scheduledReports", telemetry.scheduledReports(), true);
        number(json, 2, "processedReports", telemetry.processedReports(), true);
        number(json, 2, "rejectedReports", telemetry.rejectedReports(), true);
        number(json, 2, "lateReports", telemetry.lateReports(), true);
        number(json, 2, "pendingReports", telemetry.pendingReports(), true);
        decimal(json, 2, "processedReportsPerSecond", telemetry.processedReportsPerSecond(), true);
        number(json, 2, "requestedFrames", telemetry.requestedFrames(), true);
        number(json, 2, "completedFrames", telemetry.completedFrames(), true);
        number(json, 2, "paintedFrames", telemetry.paintedFrames(), true);
        number(json, 2, "skippedRequests", telemetry.skippedRequests(), true);
        number(json, 2, "staleDiscards", telemetry.staleDiscards(), true);
        number(json, 2, "replacedPendingFrames", telemetry.replacedPendingFrames(), true);
        decimal(json, 2, "achievedFps", telemetry.achievedFps(), true);
        number(json, 2, "buildP50Nanos", telemetry.buildP50Nanos(), true);
        number(json, 2, "buildP95Nanos", telemetry.buildP95Nanos(), true);
        number(json, 2, "buildP99Nanos", telemetry.buildP99Nanos(), true);
        number(json, 2, "buildMaximumNanos", telemetry.buildMaximumNanos(), true);
        number(json, 2, "backlogSeconds", telemetry.backlogSeconds(), true);
        decimal(json, 2, "shardReportSkew", telemetry.shardReportSkew(), true);
        decimal(json, 2, "shardWorkSkew", telemetry.shardWorkSkew(), true);
        decimal(json, 2, "positionRmse", telemetry.positionRmse(), true);
        number(json, 2, "innovationCount", telemetry.innovationCount(), true);
        decimal(json, 2, "normalizedInnovationMean", telemetry.normalizedInnovationMean(), true);
        decimal(
                json,
                2,
                "normalizedInnovationMaximum",
                telemetry.normalizedInnovationMaximum(),
                false);
        indent(json, 1).append("},\n");
    }

    private static void phase(StringBuilder json, String name, Phase phase, boolean comma) {
        indent(json, 2).append('"').append(name).append("\": {\n");
        number(json, 3, "wallNanos", phase.wallNanos(), true);
        number(json, 3, "scheduledReports", phase.scheduledReports(), true);
        number(json, 3, "processedReports", phase.processedReports(), true);
        number(json, 3, "requestedFrames", phase.requestedFrames(), true);
        number(json, 3, "completedFrames", phase.completedFrames(), true);
        number(json, 3, "paintedFrames", phase.paintedFrames(), false);
        indent(json, 2).append('}').append(comma ? ',' : ' ').append('\n');
    }

    private static void property(
            StringBuilder json, int depth, String name, String value, boolean comma) {
        indent(json, depth).append('"').append(name).append("\": ");
        string(json, value);
        json.append(comma ? ",\n" : "\n");
    }

    private static void number(
            StringBuilder json, int depth, String name, long value, boolean comma) {
        indent(json, depth)
                .append('"')
                .append(name)
                .append("\": ")
                .append(value)
                .append(comma ? ",\n" : "\n");
    }

    private static void decimal(
            StringBuilder json, int depth, String name, double value, boolean comma) {
        if (!Double.isFinite(value)) {
            throw new IllegalStateException(name + " is not finite");
        }
        indent(json, depth)
                .append('"')
                .append(name)
                .append("\": ")
                .append(Double.toString(value))
                .append(comma ? ",\n" : "\n");
    }

    private static void bool(
            StringBuilder json, int depth, String name, boolean value, boolean comma) {
        indent(json, depth)
                .append('"')
                .append(name)
                .append("\": ")
                .append(value)
                .append(comma ? ",\n" : "\n");
    }

    private static StringBuilder indent(StringBuilder target, int depth) {
        return target.append("  ".repeat(depth));
    }

    private static void string(StringBuilder target, String value) {
        target.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> target.append("\\\"");
                case '\\' -> target.append("\\\\");
                case '\b' -> target.append("\\b");
                case '\f' -> target.append("\\f");
                case '\n' -> target.append("\\n");
                case '\r' -> target.append("\\r");
                case '\t' -> target.append("\\t");
                default -> {
                    if (character < 0x20) {
                        target.append(String.format(Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        target.append(character);
                    }
                }
            }
        }
        target.append('"');
    }

    private static void row(StringBuilder markdown, String name, Object value) {
        markdown.append("| ").append(name).append(" | ").append(value).append(" |\n");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    private static void writeAtomically(Path target, String content) throws IOException {
        Path part = target.resolveSibling(target.getFileName() + ".part");
        Files.writeString(part, content, StandardCharsets.UTF_8);
        try {
            Files.move(
                    part,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
