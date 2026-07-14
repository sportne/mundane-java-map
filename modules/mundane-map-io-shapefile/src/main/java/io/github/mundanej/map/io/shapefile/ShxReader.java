package io.github.mundanej.map.io.shapefile;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.SourceDiagnostic;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

/** Reads and validates one optional SHX address index without retaining its channel. */
final class ShxReader {
    private ShxReader() {}

    static Result read(
            String source,
            Path path,
            ShapefileFileAccess access,
            ShapefileFileAccess.Channel shp,
            long shpSize,
            ShpHeader shpHeader,
            ShapefileLimits limits,
            ShapefileAccounting accounting,
            ByteBuffer scratch,
            CancellationToken cancellation) {
        ShapefileFileAccess.Channel shx = null;
        try {
            Shapefiles.checkpoint(source, cancellation);
            try {
                shx = access.open(path);
            } catch (IOException exception) {
                return Result.ignored(ioWarning(source, -1, exception));
            }
            Shapefiles.checkpoint(source, cancellation);

            long capturedSize;
            try {
                Shapefiles.checkpoint(source, cancellation);
                capturedSize = shx.size();
                Shapefiles.checkpoint(source, cancellation);
            } catch (IOException exception) {
                throw rejected(ioWarning(source, -1, exception));
            }
            if (capturedSize < 100
                    || capturedSize > limits.maximumComponentBytes()
                    || ((capturedSize - 100) & 7) != 0) {
                throw rejected(warning(source, "length", 0));
            }
            long entryCount = (capturedSize - 100) / 8;

            int read = read(shx, scratch, 100, 0, source, cancellation, "shx");
            if (read != 100) {
                throw rejected(warning(source, "length", read));
            }
            validateHeader(source, scratch, capturedSize, shpHeader);

            long packedBytes;
            try {
                packedBytes = Math.multiplyExact(entryCount, 8);
            } catch (ArithmeticException exception) {
                throw rejected(warning(source, "entry", 100));
            }
            if (entryCount > limits.maximumPhysicalRecords()
                    || entryCount > Integer.MAX_VALUE
                    || !accounting.canAllocate(packedBytes)) {
                throw rejected(warning(source, "entry", 100));
            }
            Shapefiles.checkpoint(source, cancellation);
            accounting.allocate(packedBytes, OptionalLong.empty(), 100);
            Shapefiles.checkpoint(source, cancellation);
            long[] packed = new long[(int) entryCount];
            Shapefiles.checkpoint(source, cancellation);

            long expectedShpOffset = 100;
            int previousOffsetWords = 0;
            for (int index = 0; index < packed.length; index++) {
                Shapefiles.checkpoint(source, cancellation);
                long shxEntryOffset = 100L + index * 8L;
                int entryRead = read(shx, scratch, 8, shxEntryOffset, source, cancellation, "shx");
                if (entryRead != 8) {
                    throw rejected(warning(source, "length", shxEntryOffset + entryRead));
                }
                scratch.order(ByteOrder.BIG_ENDIAN);
                int offsetWords = scratch.getInt(0);
                int contentWords = scratch.getInt(4);
                if (offsetWords <= 0) {
                    throw rejected(warning(source, "entry", shxEntryOffset));
                }
                if (contentWords < 2) {
                    throw rejected(warning(source, "entry", shxEntryOffset + 4));
                }
                long offsetBytes = Math.multiplyExact((long) offsetWords, 2);
                if ((index == 0 && offsetWords != 50)
                        || (index > 0 && offsetWords <= previousOffsetWords)) {
                    throw rejected(warning(source, "entry", shxEntryOffset));
                }
                if (offsetBytes != expectedShpOffset) {
                    throw rejected(warning(source, "shpMismatch", shxEntryOffset));
                }

                int shpRead;
                try {
                    shpRead = read(shp, scratch, 8, offsetBytes, source, cancellation, "shp");
                } catch (IOException exception) {
                    throw ShapefileFailures.io(source, "shp", "read", offsetBytes, exception);
                }
                if (shpRead != 8) {
                    throw rejected(warning(source, "shpMismatch", shxEntryOffset + 4));
                }
                scratch.order(ByteOrder.BIG_ENDIAN);
                int shpContentWords = scratch.getInt(4);
                if (shpContentWords < 2) {
                    throw rejected(warning(source, "shpMismatch", shxEntryOffset + 4));
                }
                long shpContentBytes = Math.multiplyExact((long) shpContentWords, 2);
                long frameEnd;
                try {
                    frameEnd = Math.addExact(Math.addExact(offsetBytes, 8), shpContentBytes);
                } catch (ArithmeticException exception) {
                    throw rejected(warning(source, "shpMismatch", shxEntryOffset + 4));
                }
                if (frameEnd > shpSize || shpContentWords != contentWords) {
                    throw rejected(warning(source, "shpMismatch", shxEntryOffset + 4));
                }
                expectedShpOffset = frameEnd;
                previousOffsetWords = offsetWords;
                packed[index] = ShxIndex.pack(offsetWords, contentWords);
            }
            if (expectedShpOffset != shpSize) {
                throw rejected(warning(source, "shpMismatch", capturedSize));
            }

            long finalSize;
            try {
                Shapefiles.checkpoint(source, cancellation);
                finalSize = shx.size();
                Shapefiles.checkpoint(source, cancellation);
            } catch (IOException exception) {
                throw rejected(ioWarning(source, -1, exception));
            }
            if (finalSize != capturedSize) {
                throw rejected(warning(source, "length", 0));
            }
            Shapefiles.checkpoint(source, cancellation);
            try {
                shx.close();
                shx = null;
            } catch (IOException exception) {
                Rejected rejection = rejected(ioWarning(source, -1, exception));
                closeSuppressed(shx, rejection);
                shx = null;
                return Result.ignored(rejection.warning);
            }
            Shapefiles.checkpoint(source, cancellation);
            return Result.valid(new ShxIndex(packed));
        } catch (Rejected rejection) {
            closeSuppressed(shx, rejection);
            return Result.ignored(rejection.warning);
        } catch (RuntimeException | Error failure) {
            closeSuppressed(shx, failure);
            throw failure;
        } catch (IOException exception) {
            Rejected rejection = rejected(ioWarning(source, -1, exception));
            closeSuppressed(shx, rejection);
            return Result.ignored(rejection.warning);
        }
    }

    private static void validateHeader(
            String source, ByteBuffer header, long capturedSize, ShpHeader shpHeader) {
        header.order(ByteOrder.BIG_ENDIAN);
        if (header.getInt(0) != 9994) {
            throw rejected(warning(source, "header", 0));
        }
        for (int offset = 4; offset <= 20; offset += 4) {
            if (header.getInt(offset) != 0) {
                throw rejected(warning(source, "header", offset));
            }
        }
        int words = header.getInt(24);
        if (words < 0 || Math.multiplyExact((long) words, 2) != capturedSize) {
            throw rejected(warning(source, "length", 24));
        }
        header.order(ByteOrder.LITTLE_ENDIAN);
        if (header.getInt(28) != 1000) {
            throw rejected(warning(source, "header", 28));
        }
        if (header.getInt(32) != shpHeader.shapeType()) {
            throw rejected(warning(source, "shpMismatch", 32));
        }
        double minX = bound(source, header, 36);
        double minY = bound(source, header, 44);
        double maxX = bound(source, header, 52);
        double maxY = bound(source, header, 60);
        if (minX > maxX) {
            throw rejected(warning(source, "header", 36));
        }
        if (minY > maxY) {
            throw rejected(warning(source, "header", 44));
        }
        Envelope shpBounds = shpHeader.extent().orElseGet(() -> new Envelope(0.0, 0.0, 0.0, 0.0));
        if (minX != shpBounds.minX()) {
            throw rejected(warning(source, "shpMismatch", 36));
        }
        if (minY != shpBounds.minY()) {
            throw rejected(warning(source, "shpMismatch", 44));
        }
        if (maxX != shpBounds.maxX()) {
            throw rejected(warning(source, "shpMismatch", 52));
        }
        if (maxY != shpBounds.maxY()) {
            throw rejected(warning(source, "shpMismatch", 60));
        }
    }

    private static double bound(String source, ByteBuffer header, int offset) {
        double value = Shapefiles.canonical(header.getDouble(offset));
        if (!Double.isFinite(value)) {
            throw rejected(warning(source, "header", offset));
        }
        return value;
    }

    private static int read(
            ShapefileFileAccess.Channel channel,
            ByteBuffer target,
            int length,
            long position,
            String source,
            CancellationToken cancellation,
            String component)
            throws IOException {
        target.clear();
        target.limit(length);
        int total = 0;
        while (target.hasRemaining()) {
            Shapefiles.checkpoint(source, cancellation);
            int count;
            try {
                count = channel.read(target, position + total);
            } catch (IOException exception) {
                if ("shx".equals(component)) {
                    throw rejected(ioWarning(source, position + total, exception));
                }
                throw exception;
            }
            Shapefiles.checkpoint(source, cancellation);
            if (count < 0) {
                break;
            }
            if (count > 0) {
                total += count;
            }
        }
        target.flip();
        return total;
    }

    private static SourceDiagnostic warning(String source, String reason, long offset) {
        return new SourceDiagnostic(
                "SHAPEFILE_SHX_IGNORED",
                DiagnosticSeverity.WARNING,
                source,
                Optional.of(location(offset)),
                "Shapefile index was ignored",
                Map.of("reason", reason));
    }

    private static SourceDiagnostic ioWarning(String source, long offset, IOException cause) {
        return new SourceDiagnostic(
                "SHAPEFILE_SHX_IGNORED",
                DiagnosticSeverity.WARNING,
                source,
                Optional.of(location(offset)),
                "Shapefile index was ignored after an I/O failure",
                Map.of("causeKind", ShapefileFailures.causeKind(cause), "reason", "io"));
    }

    private static DiagnosticLocation location(long offset) {
        return new DiagnosticLocation(
                Optional.of("shx"),
                OptionalLong.empty(),
                OptionalInt.empty(),
                OptionalInt.empty(),
                Optional.empty(),
                offset < 0 ? OptionalLong.empty() : OptionalLong.of(offset));
    }

    private static Rejected rejected(SourceDiagnostic warning) {
        return new Rejected(warning);
    }

    private static void closeSuppressed(
            ShapefileFileAccess.Channel channel, Throwable primaryFailure) {
        if (channel == null) {
            return;
        }
        try {
            channel.close();
        } catch (IOException exception) {
            primaryFailure.addSuppressed(exception);
        }
    }

    record Result(Optional<ShxIndex> index, Optional<SourceDiagnostic> warning) {
        static Result valid(ShxIndex index) {
            return new Result(Optional.of(index), Optional.empty());
        }

        static Result ignored(SourceDiagnostic warning) {
            return new Result(Optional.empty(), Optional.of(warning));
        }
    }

    @SuppressWarnings("serial")
    private static final class Rejected extends RuntimeException {
        private final SourceDiagnostic warning;

        private Rejected(SourceDiagnostic warning) {
            this.warning = warning;
        }
    }
}
