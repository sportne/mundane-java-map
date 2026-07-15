package io.github.mundanej.map.io.image;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.RasterAffineTransform;
import io.github.mundanej.map.api.RasterGridPlacement;
import io.github.mundanej.map.api.RasterPlacementException;
import io.github.mundanej.map.api.SourceException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

final class WorldFileSupport {
    private static final String[] COEFFICIENTS = {"A", "D", "B", "E", "C", "F"};

    private WorldFileSupport() {}

    static RasterGridPlacement read(
            Path image,
            String extension,
            String sourceId,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            Access access) {
        Candidate selected = select(image, extension, sourceId, cancellation, access);
        double[] values = snapshotAndParse(selected.path(), sourceId, limits, cancellation, access);
        checkpoint(sourceId, cancellation, "close");
        checkpoint(sourceId, cancellation, "transform");
        try {
            RasterAffineTransform transform =
                    RasterAffineTransform.of(
                            values[0], values[1], values[2], values[3], values[4], values[5]);
            checkpoint(sourceId, cancellation, "transform");
            return RasterGridPlacement.affine(transform);
        } catch (RasterPlacementException failure) {
            throw ImageDiagnostics.worldFileTransform(sourceId, failure);
        }
    }

    private static Candidate select(
            Path image,
            String extension,
            String sourceId,
            CancellationToken cancellation,
            Access access) {
        List<Candidate> declarations = declarations(image, extension);
        List<Candidate> distinct = new ArrayList<>();
        for (Candidate candidate : declarations) {
            checkpoint(sourceId, cancellation, "probe");
            boolean exists;
            try {
                exists = access.exists(candidate.path());
            } catch (IOException | SecurityException failure) {
                throw ioFailure(sourceId, "probe", failure);
            }
            checkpoint(sourceId, cancellation, "probe");
            if (!exists) {
                continue;
            }
            boolean alias = false;
            for (Candidate prior : distinct) {
                checkpoint(sourceId, cancellation, "identity");
                try {
                    alias = access.isSameFile(prior.path(), candidate.path());
                } catch (IOException | SecurityException failure) {
                    throw ioFailure(sourceId, "identity", failure);
                }
                checkpoint(sourceId, cancellation, "identity");
                if (alias) {
                    break;
                }
            }
            if (!alias) {
                distinct.add(candidate);
            }
        }
        if (distinct.isEmpty()) {
            throw ImageDiagnostics.failure(
                    sourceId,
                    "IMAGE_WORLD_FILE_MISSING",
                    "worldFile",
                    "No declared world-file candidate exists",
                    Map.of());
        }
        if (distinct.size() > 1) {
            Map<String, String> context = new LinkedHashMap<>();
            context.put("candidateCount", Integer.toString(distinct.size()));
            for (int index = 0; index < distinct.size(); index++) {
                context.put("candidate" + index, distinct.get(index).label());
            }
            throw ImageDiagnostics.failure(
                    sourceId,
                    "IMAGE_WORLD_FILE_AMBIGUOUS",
                    "worldFile",
                    "More than one distinct world file exists",
                    context);
        }
        return distinct.get(0);
    }

    @SuppressWarnings("Finally")
    private static double[] snapshotAndParse(
            Path path,
            String sourceId,
            ImageSourceLimits limits,
            CancellationToken cancellation,
            Access access) {
        ImageChannel channel = null;
        Throwable primary = null;
        String operation = "open";
        try {
            checkpoint(sourceId, cancellation, "open");
            channel = access.open(path);
            checkpoint(sourceId, cancellation, "open");
            checkpoint(sourceId, cancellation, "size");
            operation = "size";
            long size = channel.size();
            checkpoint(sourceId, cancellation, "size");
            if (size > limits.maximumWorldFileBytes()) {
                throw ImageDiagnostics.worldFileLimit(
                        sourceId,
                        "worldFileBytes",
                        size,
                        limits.maximumWorldFileBytes(),
                        limits.maximumWorldFileBytes());
            }
            checkpoint(sourceId, cancellation, "allocation");
            byte[] bytes = new byte[Math.toIntExact(size)];
            checkpoint(sourceId, cancellation, "allocation");
            ByteBuffer target = ByteBuffer.wrap(bytes);
            long position = 0;
            while (target.hasRemaining()) {
                checkpoint(sourceId, cancellation, "read");
                operation = "read";
                int count = channel.read(target, position);
                if (count < 0) {
                    throw invalid(
                            sourceId,
                            position,
                            "World file ended before its captured size",
                            Map.of("reason", "truncated"));
                }
                if (count == 0) {
                    continue;
                }
                position += count;
            }
            checkpoint(sourceId, cancellation, "read");
            checkpoint(sourceId, cancellation, "size");
            operation = "size";
            if (channel.size() != size) {
                throw invalid(
                        sourceId,
                        size,
                        "World-file size changed during opening",
                        Map.of("reason", "sizeChanged"));
            }
            checkpoint(sourceId, cancellation, "size");
            checkpoint(sourceId, cancellation, "parse");
            return parse(bytes, sourceId, limits, cancellation);
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } catch (IOException failure) {
            SourceException mapped = ioFailure(sourceId, operation, failure);
            primary = mapped;
            throw mapped;
        } finally {
            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException closeFailure) {
                    if (primary != null) {
                        primary.addSuppressed(closeFailure);
                    } else {
                        throw ioFailure(sourceId, "close", closeFailure);
                    }
                }
            }
        }
    }

    private static double[] parse(
            byte[] bytes,
            String sourceId,
            ImageSourceLimits limits,
            CancellationToken cancellation) {
        if (bytes.length == 0) {
            throw invalid(sourceId, 0, "World file is empty", Map.of("reason", "empty"));
        }
        List<Line> lines = new ArrayList<>(6);
        int start = 0;
        int index = 0;
        while (index < bytes.length) {
            checkpoint(sourceId, cancellation, "parse");
            int current = bytes[index] & 0xff;
            if (current >= 0x7f
                    || (current < 0x20 && current != '\t' && current != '\r' && current != '\n')) {
                throw invalid(
                        sourceId,
                        index,
                        "World file is not strict US-ASCII",
                        coefficientContext("encoding", lines.size()));
            }
            if (current == '\r' || current == '\n') {
                int length = index - start;
                checkLineLimit(sourceId, start, length, limits);
                lines.add(new Line(start, index));
                if (current == '\r') {
                    if (index + 1 >= bytes.length || bytes[index + 1] != '\n') {
                        throw invalid(
                                sourceId,
                                index,
                                "World file contains a bare carriage return",
                                Map.of("reason", "lineCount"));
                    }
                    index++;
                }
                index++;
                start = index;
                if (lines.size() == 6 && index < bytes.length) {
                    throw invalid(
                            sourceId,
                            index,
                            "World file contains trailing content",
                            Map.of("reason", "lineCount"));
                }
                continue;
            }
            int length = index - start + 1;
            checkLineLimit(sourceId, start, length, limits);
            index++;
        }
        if (start < bytes.length) {
            checkLineLimit(sourceId, start, bytes.length - start, limits);
            lines.add(new Line(start, bytes.length));
        }
        if (lines.size() != 6) {
            throw invalid(
                    sourceId,
                    bytes.length,
                    "World file must contain exactly six lines",
                    Map.of("reason", "lineCount"));
        }
        double[] physical = new double[6];
        for (int lineIndex = 0; lineIndex < 6; lineIndex++) {
            checkpoint(sourceId, cancellation, "parse");
            Line line = lines.get(lineIndex);
            int first = line.start();
            int last = line.end();
            while (first < last && (bytes[first] == ' ' || bytes[first] == '\t')) {
                first++;
            }
            while (last > first && (bytes[last - 1] == ' ' || bytes[last - 1] == '\t')) {
                last--;
            }
            String coefficient = COEFFICIENTS[lineIndex];
            if (first == last) {
                throw invalid(
                        sourceId,
                        line.start(),
                        "World-file coefficient is blank",
                        Map.of("reason", "number", "coefficient", coefficient));
            }
            int invalidOffset = firstInvalidDecimal(bytes, first, last);
            if (invalidOffset >= 0) {
                throw invalid(
                        sourceId,
                        invalidOffset,
                        "World-file coefficient is malformed",
                        Map.of("reason", "number", "coefficient", coefficient));
            }
            String token = new String(bytes, first, last - first, StandardCharsets.US_ASCII);
            double parsed;
            try {
                parsed = Double.parseDouble(token);
            } catch (NumberFormatException failure) {
                throw invalid(
                        sourceId,
                        first,
                        "World-file coefficient is malformed",
                        Map.of("reason", "number", "coefficient", coefficient));
            }
            if (!Double.isFinite(parsed)) {
                throw invalid(
                        sourceId,
                        first,
                        "World-file coefficient is non-finite",
                        Map.of("reason", "nonFinite", "coefficient", coefficient));
            }
            physical[lineIndex] = parsed == 0.0 ? 0.0 : parsed;
        }
        return physical;
    }

    private static int firstInvalidDecimal(byte[] bytes, int first, int last) {
        int index = first;
        if (index < last && (bytes[index] == '+' || bytes[index] == '-')) {
            index++;
        }
        int integralStart = index;
        while (index < last && isDigit(bytes[index])) {
            index++;
        }
        boolean integralDigits = index > integralStart;
        boolean fractionalDigits = false;
        if (index < last && bytes[index] == '.') {
            index++;
            int fractionalStart = index;
            while (index < last && isDigit(bytes[index])) {
                index++;
            }
            fractionalDigits = index > fractionalStart;
        }
        if (!integralDigits && !fractionalDigits) {
            return Math.min(index, last);
        }
        if (index < last && (bytes[index] == 'e' || bytes[index] == 'E')) {
            index++;
            if (index < last && (bytes[index] == '+' || bytes[index] == '-')) {
                index++;
            }
            int exponentStart = index;
            while (index < last && isDigit(bytes[index])) {
                index++;
            }
            if (index == exponentStart) {
                return index;
            }
        }
        return index == last ? -1 : index;
    }

    private static boolean isDigit(byte value) {
        return value >= '0' && value <= '9';
    }

    private static Map<String, String> coefficientContext(String reason, int lineIndex) {
        return lineIndex < COEFFICIENTS.length
                ? Map.of("reason", reason, "coefficient", COEFFICIENTS[lineIndex])
                : Map.of("reason", reason);
    }

    private static void checkLineLimit(
            String sourceId, int lineStart, int length, ImageSourceLimits limits) {
        if (length > limits.maximumWorldFileLineBytes()) {
            throw ImageDiagnostics.worldFileLimit(
                    sourceId,
                    "worldFileLineBytes",
                    (long) limits.maximumWorldFileLineBytes() + 1,
                    limits.maximumWorldFileLineBytes(),
                    (long) lineStart + limits.maximumWorldFileLineBytes());
        }
    }

    private static List<Candidate> declarations(Path image, String extension) {
        String filename = Objects.requireNonNull(image.getFileName(), "image filename").toString();
        String stem = filename.substring(0, filename.lastIndexOf('.'));
        Path parent = image.getParent();
        String longSuffix =
                extension.equals("png") ? "pngw" : extension.equals("jpg") ? "jpgw" : "jpegw";
        String shortSuffix = extension.equals("png") ? "pgw" : "jgw";
        return List.of(
                candidate(parent, stem, longSuffix, "longLower"),
                candidate(parent, stem, longSuffix.toUpperCase(java.util.Locale.ROOT), "longUpper"),
                candidate(parent, stem, shortSuffix, "shortLower"),
                candidate(
                        parent, stem, shortSuffix.toUpperCase(java.util.Locale.ROOT), "shortUpper"),
                candidate(parent, stem, "wld", "genericLower"),
                candidate(parent, stem, "WLD", "genericUpper"));
    }

    private static Candidate candidate(Path parent, String stem, String suffix, String label) {
        Path path = Path.of(stem + '.' + suffix);
        return new Candidate(parent == null ? path : parent.resolve(path), label);
    }

    private static SourceException invalid(
            String sourceId, long offset, String message, Map<String, String> context) {
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_WORLD_FILE_INVALID",
                "worldFile",
                OptionalLong.of(offset),
                message,
                context,
                null);
    }

    private static SourceException ioFailure(String sourceId, String operation, Throwable cause) {
        return ImageDiagnostics.failure(
                sourceId,
                "IMAGE_IO_FAILED",
                "worldFile",
                OptionalLong.empty(),
                "World-file I/O failed",
                Map.of("operation", operation, "causeKind", "IOException"),
                cause);
    }

    private static void checkpoint(
            String sourceId, CancellationToken cancellation, String operation) {
        if (cancellation.isCancellationRequested()) {
            throw ImageDiagnostics.failure(
                    sourceId,
                    "SOURCE_CANCELLED",
                    "worldFile",
                    "World-file operation was cancelled",
                    Map.of("operation", operation));
        }
    }

    static Access fileSystemAccess(ImageChannel.Opener opener) {
        Objects.requireNonNull(opener, "opener");
        return new Access() {
            @Override
            public boolean exists(Path path) throws IOException {
                try {
                    Files.readAttributes(
                            path,
                            BasicFileAttributes.class,
                            java.nio.file.LinkOption.NOFOLLOW_LINKS);
                    return true;
                } catch (NoSuchFileException failure) {
                    return false;
                }
            }

            @Override
            public boolean isSameFile(Path first, Path second) throws IOException {
                return Files.isSameFile(first, second);
            }

            @Override
            public ImageChannel open(Path path) throws IOException {
                return opener.open(path);
            }
        };
    }

    interface Access {
        boolean exists(Path path) throws IOException;

        boolean isSameFile(Path first, Path second) throws IOException;

        ImageChannel open(Path path) throws IOException;
    }

    private record Candidate(Path path, String label) {}

    private record Line(int start, int end) {}
}
