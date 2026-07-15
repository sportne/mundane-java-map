package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.DiagnosticLocation;
import io.github.mundanej.map.api.DiagnosticReport;
import io.github.mundanej.map.api.DiagnosticSeverity;
import io.github.mundanej.map.api.EncodedRasterDecodeContext;
import io.github.mundanej.map.api.EncodedRasterDecoder;
import io.github.mundanej.map.api.EncodedRasterFormat;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.RgbaPixelBuffer;
import io.github.mundanej.map.api.SourceDiagnostic;
import io.github.mundanej.map.api.SourceException;
import java.awt.image.BufferedImage;
import java.awt.image.DataBuffer;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class ImageIoRasterDecoder implements EncodedRasterDecoder {
    private final Map<EncodedRasterFormat, ImageReaderSpi> providers;
    private final ImageInputFactory inputFactory;

    ImageIoRasterDecoder(Map<EncodedRasterFormat, ImageReaderSpi> providers) {
        this(providers, MemoryCacheImageInputStream::new);
    }

    ImageIoRasterDecoder(
            Map<EncodedRasterFormat, ImageReaderSpi> providers, ImageInputFactory inputFactory) {
        this.providers = Map.copyOf(new EnumMap<>(Objects.requireNonNull(providers, "providers")));
        this.inputFactory = Objects.requireNonNull(inputFactory, "inputFactory");
    }

    @Override
    @SuppressWarnings("Finally")
    public RgbaPixelBuffer decode(InputStream borrowedInput, EncodedRasterDecodeContext context) {
        Objects.requireNonNull(borrowedInput, "borrowedInput");
        Objects.requireNonNull(context, "context");
        ImageReaderSpi provider = providers.get(context.format());
        if (provider == null) {
            throw new IllegalStateException("Decoder was not configured for " + context.format());
        }
        long fullPixels = Math.multiplyExact((long) context.width(), context.height());
        long outputPixels =
                Math.multiplyExact((long) context.outputWidth(), context.outputHeight());
        context.checkpoint();
        context.claimReservedIntermediateBytes(context.encodedByteLength());
        ImageInputStream input = null;
        ImageReader reader = null;
        Throwable primary = null;
        try {
            input = inputFactory.create(borrowedInput);
            reader = provider.createReaderInstance();
            reader.setInput(input, true, true);
            if (reader.getWidth(0) != context.width() || reader.getHeight(0) != context.height()) {
                throw mismatch(
                        context,
                        "dimensions",
                        context.width() + "x" + context.height(),
                        reader.getWidth(0) + "x" + reader.getHeight(0));
            }
            context.checkpoint();
            context.claimReservedIntermediateBytes(Math.multiplyExact(fullPixels, 8));
            ImageReadParam parameters = reader.getDefaultReadParam();
            BufferedImage image = reader.read(0, parameters);
            context.checkpoint();
            if (image == null
                    || image.getWidth() != context.width()
                    || image.getHeight() != context.height()) {
                throw mismatch(
                        context,
                        "decodedDimensions",
                        context.width() + "x" + context.height(),
                        image == null ? "null" : image.getWidth() + "x" + image.getHeight());
            }
            DataBuffer data = image.getRaster().getDataBuffer();
            long actualCapacity =
                    Math.multiplyExact(
                            Math.multiplyExact((long) data.getSize(), data.getNumBanks()),
                            elementBytes(data.getDataType()));
            long allowedCapacity = Math.multiplyExact(fullPixels, 8);
            if (actualCapacity > allowedCapacity) {
                throw failure(
                        context,
                        "IMAGE_DECODE_FAILED",
                        "Image backing exceeds reservation",
                        Map.of("format", context.format().name(), "reason", "bufferCapacity"),
                        null);
            }
            context.claimReservedIntermediateBytes(Math.multiplyExact(outputPixels, 4));
            RgbaPixelBuffer.Builder output =
                    RgbaPixelBuffer.builder(context.outputWidth(), context.outputHeight());
            RasterWindow window = context.sourceWindow();
            long converted = 0;
            for (int row = 0; row < context.outputHeight(); row++) {
                context.checkpoint();
                int sourceRow =
                        window.row() + nearest(row, window.height(), context.outputHeight());
                for (int column = 0; column < context.outputWidth(); column++) {
                    if ((converted++ & 4095) == 0) {
                        context.checkpoint();
                    }
                    int sourceColumn =
                            window.column()
                                    + nearest(column, window.width(), context.outputWidth());
                    int argb = image.getRGB(sourceColumn, sourceRow);
                    output.setRgba(column, row, (argb << 8) | (argb >>> 24));
                }
            }
            context.checkpoint();
            return output.build();
        } catch (RuntimeException | Error failure) {
            primary = failure;
            throw failure;
        } catch (IOException failure) {
            SourceException mapped =
                    failure(
                            context,
                            "IMAGE_DECODE_FAILED",
                            "JDK image decode failed",
                            Map.of(
                                    "format", context.format().name(),
                                    "reason", "codec",
                                    "causeKind", "IOException"),
                            failure);
            primary = mapped;
            throw mapped;
        } finally {
            cleanup(reader, input, primary, context);
        }
    }

    private static int nearest(int outputIndex, int sourceSize, int outputSize) {
        long numerator =
                Math.multiplyExact(
                        Math.addExact(Math.multiplyExact(2L, outputIndex), 1L), sourceSize);
        return Math.toIntExact(numerator / Math.multiplyExact(2L, outputSize));
    }

    private static int elementBytes(int dataType) {
        return switch (dataType) {
            case DataBuffer.TYPE_BYTE -> 1;
            case DataBuffer.TYPE_USHORT, DataBuffer.TYPE_SHORT -> 2;
            case DataBuffer.TYPE_INT, DataBuffer.TYPE_FLOAT -> 4;
            case DataBuffer.TYPE_DOUBLE -> 8;
            default -> throw new IllegalStateException("Unsupported JDK image backing type");
        };
    }

    @SuppressWarnings("Finally")
    private static void cleanup(
            ImageReader reader,
            ImageInputStream input,
            Throwable operationFailure,
            EncodedRasterDecodeContext context) {
        Throwable cleanupFailure = null;
        if (reader != null) {
            try {
                reader.dispose();
            } catch (RuntimeException | Error failure) {
                cleanupFailure = retainCleanup(operationFailure, cleanupFailure, failure);
            }
        }
        if (input != null) {
            try {
                input.close();
            } catch (IOException | RuntimeException | Error failure) {
                cleanupFailure = retainCleanup(operationFailure, cleanupFailure, failure);
            }
        }
        if (operationFailure == null && cleanupFailure != null) {
            if (cleanupFailure instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cleanupFailure instanceof Error error) {
                throw error;
            }
            throw failure(
                    context,
                    "IMAGE_IO_FAILED",
                    "JDK image stream close failed",
                    Map.of("operation", "close", "causeKind", "IOException"),
                    cleanupFailure);
        }
    }

    private static Throwable retainCleanup(
            Throwable operationFailure, Throwable cleanupFailure, Throwable next) {
        if (operationFailure != null) {
            operationFailure.addSuppressed(next);
            return cleanupFailure;
        }
        if (cleanupFailure == null) {
            return next;
        }
        cleanupFailure.addSuppressed(next);
        return cleanupFailure;
    }

    private static SourceException mismatch(
            EncodedRasterDecodeContext context, String field, String expected, String actual) {
        return failure(
                context,
                "IMAGE_DECODE_MISMATCH",
                "Decoded image facts do not match the bounded header",
                Map.of("field", field, "expected", expected, "actual", actual),
                null);
    }

    private static SourceException failure(
            EncodedRasterDecodeContext context,
            String code,
            String message,
            Map<String, String> values,
            Throwable cause) {
        DiagnosticLocation location =
                new DiagnosticLocation(
                        Optional.of("decoder"),
                        java.util.OptionalLong.empty(),
                        java.util.OptionalInt.empty(),
                        java.util.OptionalInt.empty(),
                        Optional.empty(),
                        java.util.OptionalLong.empty());
        SourceDiagnostic terminal =
                new SourceDiagnostic(
                        code,
                        DiagnosticSeverity.ERROR,
                        context.sourceIdentity().id(),
                        Optional.of(location),
                        message,
                        values);
        return new SourceException(new DiagnosticReport(List.of(terminal), 0), terminal, cause);
    }

    @FunctionalInterface
    interface ImageInputFactory {
        ImageInputStream create(InputStream input) throws IOException;
    }
}
