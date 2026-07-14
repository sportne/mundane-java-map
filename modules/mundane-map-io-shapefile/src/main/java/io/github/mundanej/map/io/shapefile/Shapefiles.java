package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.FeatureSource;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Opens bounded, read-only feature sources backed by an ESRI Shapefile main file.
 *
 * <p>Callers own the returned source and must close it. The path is used only while opening the
 * source; diagnostics identify the source by the supplied {@link SourceIdentity} and do not expose
 * the path.
 */
public final class Shapefiles {
    private Shapefiles() {}

    /**
     * Opens a source without an opening cancellation signal.
     *
     * @param identity stable identity used by the source and its diagnostics
     * @param shpPath path to the required {@code .shp} main file
     * @param options immutable parser, query, and CRS options
     * @return an open feature source owned by the caller
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code shpPath} does not end in {@code .shp}, ignoring
     *     case
     * @throws SourceException if the file cannot be opened, violates the supported profile or
     *     configured limits, or is malformed
     */
    public static FeatureSource open(
            SourceIdentity identity, Path shpPath, ShapefileOpenOptions options) {
        return open(identity, shpPath, options, CancellationToken.none());
    }

    /**
     * Opens a source using an operation-local cancellation signal.
     *
     * @param identity stable identity used by the source and its diagnostics
     * @param shpPath path to the required {@code .shp} main file
     * @param options immutable parser, query, and CRS options
     * @param cancellation signal checked throughout component discovery, main-file opening, and
     *     optional index validation
     * @return an open feature source owned by the caller
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code shpPath} does not end in {@code .shp}, ignoring
     *     case
     * @throws SourceException if opening is cancelled, the file cannot be opened, violates the
     *     supported profile or configured limits, or is malformed
     */
    public static FeatureSource open(
            SourceIdentity identity,
            Path shpPath,
            ShapefileOpenOptions options,
            CancellationToken cancellation) {
        return open(identity, shpPath, options, cancellation, new JdkShapefileFileAccess());
    }

    static FeatureSource open(
            SourceIdentity identity,
            Path path,
            ShapefileOpenOptions options,
            CancellationToken cancellation,
            ShapefileFileAccess access) {
        Objects.requireNonNull(identity, "identity");
        Objects.requireNonNull(path, "shpPath");
        Objects.requireNonNull(options, "options");
        Objects.requireNonNull(cancellation, "cancellation");
        Objects.requireNonNull(access, "access");
        Path fileNamePath = path.getFileName();
        String filename = fileNamePath == null ? null : fileNamePath.toString();
        if (filename == null || !hasShpExtension(filename)) {
            throw new IllegalArgumentException("shpPath must have a final .shp filename");
        }
        checkpoint(identity.id(), cancellation);
        if (!exists(identity.id(), access, path, cancellation, "shp")) {
            throw ShapefileFailures.failure(
                    identity.id(),
                    "SHAPEFILE_COMPONENT_MISSING",
                    "shp",
                    OptionalLong.empty(),
                    -1,
                    "Required SHP component is missing",
                    Map.of());
        }
        String stem = filename.substring(0, filename.length() - 4);
        Path parent = path.getParent();
        Path shx = select(identity.id(), access, parent, stem, "shx", cancellation);
        Path dbf = select(identity.id(), access, parent, stem, "dbf", cancellation);
        Path cpg = select(identity.id(), access, parent, stem, "cpg", cancellation);
        Path prj = select(identity.id(), access, parent, stem, "prj", cancellation);
        checkpoint(identity.id(), cancellation);
        List<SourceDiagnostic> openingWarnings = new java.util.ArrayList<>();
        ShapefileFileAccess.Channel channel;
        DbfTable dbfTable = null;
        try {
            channel = access.open(path);
        } catch (IOException exception) {
            throw ShapefileFailures.io(identity.id(), "shp", "open", -1, exception);
        }
        try {
            checkpoint(identity.id(), cancellation);
            long size;
            try {
                size = channel.size();
            } catch (IOException exception) {
                throw ShapefileFailures.io(identity.id(), "shp", "size", -1, exception);
            }
            checkpoint(identity.id(), cancellation);
            if (size > options.shapefileLimits().maximumComponentBytes()) {
                throw ShapefileFailures.limit(
                        identity.id(),
                        "shapefileOpen",
                        "componentBytes",
                        size,
                        options.shapefileLimits().maximumComponentBytes(),
                        OptionalLong.empty(),
                        0);
            }
            if (size < 100) {
                throw ShapefileFailures.failure(
                        identity.id(),
                        "SHAPEFILE_HEADER_INVALID",
                        "shp",
                        OptionalLong.empty(),
                        0,
                        "Shapefile header is too short",
                        Map.of(
                                "field",
                                "headerSize",
                                "expectedBytes",
                                "100",
                                "actualBytes",
                                Long.toString(size)));
            }
            ShapefileAccounting openingAccounting =
                    new ShapefileAccounting(
                            identity.id(), "shapefileOpen", options.shapefileLimits());
            openingAccounting.allocate(100, OptionalLong.empty(), 0);
            ByteBuffer header = ByteBuffer.allocate(100);
            try {
                readExact(identity.id(), channel, header, 0, cancellation, true);
            } catch (IOException exception) {
                throw ShapefileFailures.io(
                        identity.id(), "shp", "read", header.position(), exception);
            }
            ShpHeader parsed = parseHeader(identity.id(), header.array(), size);
            checkpoint(identity.id(), cancellation);
            ShxIndex index = null;
            if (shx == null) {
                openingWarnings.add(missingShx(identity.id()));
            } else {
                ShxReader.Result result =
                        ShxReader.read(
                                identity.id(),
                                shx,
                                access,
                                channel,
                                size,
                                parsed,
                                options.shapefileLimits(),
                                openingAccounting,
                                header,
                                cancellation);
                index = result.index().orElse(null);
                result.warning().ifPresent(openingWarnings::add);
            }
            if (dbf == null) {
                openingWarnings.add(sidecarWarning(identity.id(), "SHAPEFILE_DBF_MISSING", "dbf"));
                if (cpg != null) {
                    openingWarnings.add(
                            sidecarWarning(identity.id(), "SHAPEFILE_CPG_WITHOUT_DBF", "cpg"));
                }
            } else {
                DbfReader.Result result =
                        DbfReader.read(
                                identity.id(),
                                dbf,
                                cpg,
                                access,
                                index,
                                options,
                                openingAccounting,
                                cancellation,
                                openingWarnings);
                dbfTable = result.table();
            }
            PrjReader.Result prjResult =
                    prj == null
                            ? PrjReader.missing(options.crsOverride())
                            : PrjReader.read(
                                    identity.id(),
                                    prj,
                                    access,
                                    options.crsOverride(),
                                    options.shapefileLimits(),
                                    openingAccounting,
                                    cancellation);
            openingWarnings.addAll(prjResult.warnings());
            checkpoint(identity.id(), cancellation);
            Optional<CrsMetadata> crs = prjResult.metadata();
            return new ShapefileFeatureSource(
                    identity,
                    channel,
                    size,
                    parsed,
                    crs,
                    options,
                    index,
                    dbfTable,
                    openingReport(openingWarnings, options));
        } catch (RuntimeException | Error failure) {
            Throwable emitted =
                    failure instanceof SourceException sourceFailure
                            ? withOpeningWarnings(sourceFailure, openingWarnings, options)
                            : failure;
            if (dbfTable != null) {
                try {
                    dbfTable.close();
                } catch (IOException exception) {
                    emitted.addSuppressed(exception);
                }
            }
            closeSuppressed(channel, emitted);
            if (emitted instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw (Error) emitted;
        }
    }

    private static DiagnosticReport openingReport(
            List<SourceDiagnostic> warnings, ShapefileOpenOptions options) {
        int maximum = options.featureSourceLimits().queryLimits().retainedWarnings();
        int retained = Math.min(maximum, warnings.size());
        return new DiagnosticReport(
                warnings.subList(0, retained), Math.max(0L, (long) warnings.size() - retained));
    }

    private static SourceException withOpeningWarnings(
            SourceException failure,
            List<SourceDiagnostic> warnings,
            ShapefileOpenOptions options) {
        DiagnosticReport report = openingReport(warnings, options);
        return ShapefileFailures.withOpeningWarnings(
                failure, report.entries(), report.omittedWarningCount());
    }

    private static SourceDiagnostic sidecarWarning(String source, String code, String component) {
        return new SourceDiagnostic(
                code,
                DiagnosticSeverity.WARNING,
                source,
                Optional.of(
                        new DiagnosticLocation(
                                Optional.of(component),
                                OptionalLong.empty(),
                                java.util.OptionalInt.empty(),
                                java.util.OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.empty())),
                "Optional Shapefile sidecar is absent or ignored",
                Map.of());
    }

    private static Path select(
            String source,
            ShapefileFileAccess access,
            Path parent,
            String stem,
            String component,
            CancellationToken cancellation) {
        Path lower =
                parent == null
                        ? Path.of(stem + '.' + component)
                        : parent.resolve(stem + '.' + component);
        String upperName = stem + '.' + component.toUpperCase(java.util.Locale.ROOT);
        Path upper = parent == null ? Path.of(upperName) : parent.resolve(upperName);
        boolean low = exists(source, access, lower, cancellation, component);
        boolean up = exists(source, access, upper, cancellation, component);
        if (low && up) {
            try {
                checkpoint(source, cancellation);
                if (!access.isSameFile(lower, upper)) {
                    throw ambiguous(source, component);
                }
                checkpoint(source, cancellation);
            } catch (IOException exception) {
                throw ambiguous(source, component);
            }
        }
        return low ? lower : up ? upper : null;
    }

    private static SourceDiagnostic missingShx(String source) {
        return new SourceDiagnostic(
                "SHAPEFILE_SHX_MISSING",
                DiagnosticSeverity.WARNING,
                source,
                Optional.of(
                        new DiagnosticLocation(
                                Optional.of("shx"),
                                OptionalLong.empty(),
                                java.util.OptionalInt.empty(),
                                java.util.OptionalInt.empty(),
                                Optional.empty(),
                                OptionalLong.empty())),
                "Shapefile index is missing; sequential access will be used",
                Map.of());
    }

    private static boolean exists(
            String source,
            ShapefileFileAccess access,
            Path path,
            CancellationToken token,
            String component) {
        try {
            checkpoint(source, token);
            boolean result = access.exists(path);
            checkpoint(source, token);
            return result;
        } catch (IOException e) {
            throw ShapefileFailures.io(source, component, "probe", -1, e);
        }
    }

    private static SourceException ambiguous(String source, String component) {
        return ShapefileFailures.failure(
                source,
                "SHAPEFILE_COMPONENT_AMBIGUOUS",
                component,
                OptionalLong.empty(),
                -1,
                "Shapefile component variants are ambiguous",
                Map.of());
    }

    static void checkpoint(String source, CancellationToken token) {
        if (token.isCancellationRequested()) {
            throw ShapefileFailures.cancelled(source);
        }
    }

    private static void closeSuppressed(ShapefileFileAccess.Channel channel, Throwable primary) {
        try {
            channel.close();
        } catch (IOException closeFailure) {
            primary.addSuppressed(closeFailure);
        }
    }

    static void readExact(
            String source,
            ShapefileFileAccess.Channel channel,
            ByteBuffer target,
            long position,
            CancellationToken token,
            boolean header)
            throws IOException {
        int read = 0;
        while (target.hasRemaining()) {
            checkpoint(source, token);
            int count = channel.read(target, position + read);
            checkpoint(source, token);
            if (count < 0) {
                break;
            }
            if (count == 0) {
                continue;
            }
            read += count;
        }
        if (target.hasRemaining()) {
            throw ShapefileFailures.failure(
                    source,
                    header ? "SHAPEFILE_HEADER_INVALID" : "SHAPEFILE_RECORD_LENGTH_INVALID",
                    "shp",
                    OptionalLong.empty(),
                    position + read,
                    "Shapefile positional read was truncated",
                    Map.of(
                            "field",
                            "truncated",
                            "expectedBytes",
                            Integer.toString(target.capacity()),
                            "actualBytes",
                            Integer.toString(read)));
        }
        target.flip();
    }

    private static ShpHeader parseHeader(String source, byte[] bytes, long size) {
        ByteBuffer big = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        if (big.getInt(0) != 9994) {
            throw header(source, 0, "fileCode", Map.of());
        }
        for (int o = 4; o <= 20; o += 4) {
            if (big.getInt(o) != 0) {
                throw header(source, o, "reserved", Map.of());
            }
        }
        int words = big.getInt(24);
        if (words < 0) {
            throw header(source, 24, "fileLength", Map.of("reason", "negative"));
        }
        long declared = Math.multiplyExact((long) words, 2);
        if (declared != size) {
            throw ShapefileFailures.failure(
                    source,
                    "SHAPEFILE_FILE_LENGTH_MISMATCH",
                    "shp",
                    OptionalLong.empty(),
                    24,
                    "Declared Shapefile length differs from opened size",
                    Map.of(
                            "declaredBytes",
                            Long.toString(declared),
                            "actualBytes",
                            Long.toString(size)));
        }
        ByteBuffer little = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        if (little.getInt(28) != 1000) {
            throw header(source, 28, "version", Map.of());
        }
        int type = little.getInt(32);
        if (type != 0 && type != 1 && type != 3 && type != 5 && type != 8) {
            throw ShapefileFailures.failure(
                    source,
                    "SHAPEFILE_SHAPE_TYPE_UNSUPPORTED",
                    "shp",
                    OptionalLong.empty(),
                    32,
                    "Shapefile shape type is unsupported",
                    Map.of("shapeType", Integer.toString(type)));
        }
        double minX = bound(source, little, 36, "nonFinite"),
                minY = bound(source, little, 44, "nonFinite"),
                maxX = bound(source, little, 52, "nonFinite"),
                maxY = bound(source, little, 60, "nonFinite");
        if (minX > maxX) {
            throw header(source, 36, "bounds", Map.of("reason", "unordered"));
        }
        if (minY > maxY) {
            throw header(source, 44, "bounds", Map.of("reason", "unordered"));
        }
        if (type == 0) {
            if (minX != 0) {
                throw header(source, 36, "bounds", Map.of("reason", "nonZeroNull"));
            }
            if (minY != 0) {
                throw header(source, 44, "bounds", Map.of("reason", "nonZeroNull"));
            }
            if (maxX != 0) {
                throw header(source, 52, "bounds", Map.of("reason", "nonZeroNull"));
            }
            if (maxY != 0) {
                throw header(source, 60, "bounds", Map.of("reason", "nonZeroNull"));
            }
        }
        return new ShpHeader(
                type,
                type == 0 ? Optional.empty() : Optional.of(new Envelope(minX, minY, maxX, maxY)));
    }

    private static double bound(String source, ByteBuffer buffer, int offset, String reason) {
        double value = canonical(buffer.getDouble(offset));
        if (!Double.isFinite(value)) {
            throw header(source, offset, "bounds", Map.of("reason", reason));
        }
        return value;
    }

    private static SourceException header(
            String source, long offset, String field, Map<String, String> rest) {
        java.util.LinkedHashMap<String, String> context = new java.util.LinkedHashMap<>(rest);
        context.put("field", field);
        return ShapefileFailures.failure(
                source,
                "SHAPEFILE_HEADER_INVALID",
                "shp",
                OptionalLong.empty(),
                offset,
                "Shapefile header is invalid",
                context);
    }

    static double canonical(double value) {
        return value == 0.0 ? 0.0 : value;
    }

    private static boolean hasShpExtension(String filename) {
        int extension = filename.length() - 4;
        return extension > 0
                && filename.charAt(extension) == '.'
                && asciiEqualIgnoreCase(filename.charAt(extension + 1), 's')
                && asciiEqualIgnoreCase(filename.charAt(extension + 2), 'h')
                && asciiEqualIgnoreCase(filename.charAt(extension + 3), 'p');
    }

    private static boolean asciiEqualIgnoreCase(char actual, char expectedLowercase) {
        return actual == expectedLowercase || actual == expectedLowercase - ('a' - 'A');
    }
}
