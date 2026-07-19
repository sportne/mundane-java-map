package io.github.mundanej.map.io.geotiff;

import io.github.mundanej.map.api.CancellationToken;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

final class GeoTiffSegmentDecoder {
    private static final int CHECKPOINT_BYTES = 4_096;

    private GeoTiffSegmentDecoder() {}

    static void decode(
            String sourceId,
            int segment,
            int compression,
            byte[] encoded,
            int encodedOffset,
            int encodedLength,
            byte[] decoded,
            int decodedLength,
            String operation,
            CancellationToken cancellation) {
        GeoTiffFailures.checkpoint(sourceId, cancellation, operation);
        switch (compression) {
            case 1 ->
                    copy(
                            sourceId,
                            encoded,
                            encodedOffset,
                            decoded,
                            decodedLength,
                            operation,
                            cancellation);
            case 8 ->
                    inflate(
                            sourceId,
                            segment,
                            encoded,
                            encodedOffset,
                            encodedLength,
                            decoded,
                            decodedLength,
                            operation,
                            cancellation);
            case 32773 ->
                    packBits(
                            sourceId,
                            segment,
                            encoded,
                            encodedOffset,
                            encodedLength,
                            decoded,
                            decodedLength,
                            operation,
                            cancellation);
            default -> throw new AssertionError("Unsupported compression reached decoder");
        }
        GeoTiffFailures.checkpoint(sourceId, cancellation, operation);
    }

    private static void copy(
            String sourceId,
            byte[] encoded,
            int encodedOffset,
            byte[] decoded,
            int decodedLength,
            String operation,
            CancellationToken cancellation) {
        int copied = 0;
        while (copied < decodedLength) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, operation);
            int chunk = Math.min(CHECKPOINT_BYTES, decodedLength - copied);
            System.arraycopy(encoded, encodedOffset + copied, decoded, copied, chunk);
            copied += chunk;
        }
    }

    private static void packBits(
            String sourceId,
            int segment,
            byte[] encoded,
            int encodedOffset,
            int encodedLength,
            byte[] decoded,
            int decodedLength,
            String operation,
            CancellationToken cancellation) {
        int input = encodedOffset;
        int inputEnd = encodedOffset + encodedLength;
        int output = 0;
        while (input < inputEnd) {
            GeoTiffFailures.checkpoint(sourceId, cancellation, operation);
            int header = encoded[input++];
            if (header >= 0) {
                int literal = header + 1;
                if (literal > inputEnd - input) {
                    throw GeoTiffFailures.decode(sourceId, segment, 32773, "packet");
                }
                if (literal > decodedLength - output) {
                    throw GeoTiffFailures.decode(sourceId, segment, 32773, "overrun");
                }
                System.arraycopy(encoded, input, decoded, output, literal);
                input += literal;
                output += literal;
            } else if (header != -128) {
                if (input >= inputEnd) {
                    throw GeoTiffFailures.decode(sourceId, segment, 32773, "packet");
                }
                int repeated = 1 - header;
                if (repeated > decodedLength - output) {
                    throw GeoTiffFailures.decode(sourceId, segment, 32773, "overrun");
                }
                Arrays.fill(decoded, output, output + repeated, encoded[input++]);
                output += repeated;
            }
        }
        if (output != decodedLength) {
            throw GeoTiffFailures.decode(sourceId, segment, 32773, "truncated");
        }
    }

    private static void inflate(
            String sourceId,
            int segment,
            byte[] encoded,
            int encodedOffset,
            int encodedLength,
            byte[] decoded,
            int decodedLength,
            String operation,
            CancellationToken cancellation) {
        Inflater inflater = new Inflater();
        int input = encodedOffset;
        int inputEnd = encodedOffset + encodedLength;
        int output = 0;
        byte[] overrunProbe = new byte[1];
        try {
            while (output < decodedLength) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, operation);
                if (inflater.needsInput() && input < inputEnd) {
                    int chunk = Math.min(CHECKPOINT_BYTES, inputEnd - input);
                    inflater.setInput(encoded, input, chunk);
                    input += chunk;
                }
                int produced =
                        inflater.inflate(
                                decoded,
                                output,
                                Math.min(CHECKPOINT_BYTES, decodedLength - output));
                output += produced;
                if (inflater.needsDictionary()) {
                    throw GeoTiffFailures.decode(sourceId, segment, 8, "dictionary");
                }
                if (inflater.finished()) {
                    if (output != decodedLength) {
                        throw GeoTiffFailures.decode(sourceId, segment, 8, "truncated");
                    }
                    break;
                }
                if (produced == 0 && inflater.needsInput() && input == inputEnd) {
                    throw GeoTiffFailures.decode(sourceId, segment, 8, "truncated");
                }
                if (produced == 0 && !inflater.needsInput()) {
                    throw GeoTiffFailures.decode(sourceId, segment, 8, "unfinished");
                }
            }
            while (!inflater.finished()) {
                GeoTiffFailures.checkpoint(sourceId, cancellation, operation);
                if (inflater.needsInput() && input < inputEnd) {
                    int chunk = Math.min(CHECKPOINT_BYTES, inputEnd - input);
                    inflater.setInput(encoded, input, chunk);
                    input += chunk;
                }
                int produced = inflater.inflate(overrunProbe);
                if (produced > 0) {
                    throw GeoTiffFailures.decode(sourceId, segment, 8, "overrun");
                }
                if (inflater.needsDictionary()) {
                    throw GeoTiffFailures.decode(sourceId, segment, 8, "dictionary");
                }
                if (!inflater.finished() && inflater.needsInput() && input == inputEnd) {
                    throw GeoTiffFailures.decode(sourceId, segment, 8, "unfinished");
                }
                if (!inflater.finished() && !inflater.needsInput()) {
                    throw GeoTiffFailures.decode(sourceId, segment, 8, "unfinished");
                }
            }
            if (inflater.getRemaining() != 0 || input != inputEnd) {
                throw GeoTiffFailures.decode(sourceId, segment, 8, "trailing");
            }
        } catch (DataFormatException failure) {
            throw GeoTiffFailures.decode(sourceId, segment, 8, "packet");
        } finally {
            inflater.end();
        }
    }
}
