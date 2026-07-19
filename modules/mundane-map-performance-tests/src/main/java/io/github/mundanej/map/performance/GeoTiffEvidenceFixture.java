package io.github.mundanej.map.performance;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/** Deterministic support-only author for bounded GeoTIFF performance inputs. */
final class GeoTiffEvidenceFixture {
    private GeoTiffEvidenceFixture() {}

    static Fixture write(Path workspace, EvidenceConfiguration.Profile profile) throws IOException {
        int rasterSize = profile == EvidenceConfiguration.Profile.BASELINE ? 1_024 : 128;
        int elevationSize = profile == EvidenceConfiguration.Profile.BASELINE ? 512 : 64;
        Path root = workspace.resolve("geotiff-evidence-v1");
        Files.createDirectories(root);
        Path raster = root.resolve("raster-window.tif");
        Path elevation = root.resolve("eager-elevation.tif");
        write(raster, rasterSize, rasterSize, false);
        write(elevation, elevationSize, elevationSize, true);
        return new Fixture(
                raster,
                elevation,
                rasterSize,
                elevationSize,
                Files.size(raster),
                Files.size(elevation));
    }

    private static void write(Path path, int width, int height, boolean elevation)
            throws IOException {
        int rowsPerStrip = elevation ? 32 : 16;
        int bytesPerSample = elevation ? 2 : 1;
        int strips = Math.ceilDiv(height, rowsPerStrip);
        int entries = elevation ? 13 : 12;
        int position = 8 + 2 + entries * 12 + 4;
        int offsetsOffset = even(position);
        position = offsetsOffset + strips * Integer.BYTES;
        int countsOffset = even(position);
        position = countsOffset + strips * Integer.BYTES;
        int scaleOffset = even(position);
        position = scaleOffset + 3 * Double.BYTES;
        int tieOffset = even(position);
        position = tieOffset + 6 * Double.BYTES;
        int keysOffset = even(position);
        position = keysOffset + 16 * Short.BYTES;
        int[] offsets = new int[strips];
        int[] counts = new int[strips];
        for (int strip = 0; strip < strips; strip++) {
            int rows = Math.min(rowsPerStrip, height - strip * rowsPerStrip);
            offsets[strip] = even(position);
            counts[strip] = Math.multiplyExact(Math.multiplyExact(width, rows), bytesPerSample);
            position = offsets[strip] + counts[strip];
        }

        ByteBuffer output = ByteBuffer.allocate(position).order(ByteOrder.LITTLE_ENDIAN);
        output.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        output.position(8).putShort((short) entries);
        shortEntry(output, 256, width);
        shortEntry(output, 257, height);
        shortEntry(output, 258, elevation ? 16 : 8);
        shortEntry(output, 259, 1);
        shortEntry(output, 262, 1);
        longArrayEntry(output, 273, strips, offsetsOffset);
        longEntry(output, 278, rowsPerStrip);
        longArrayEntry(output, 279, strips, countsOffset);
        shortEntry(output, 284, 1);
        if (elevation) {
            shortEntry(output, 339, 2);
        }
        doubleEntry(output, 33550, 3, scaleOffset);
        doubleEntry(output, 33922, 6, tieOffset);
        shortArrayEntry(output, 34735, 16, keysOffset);
        output.putInt(0);

        output.position(offsetsOffset);
        for (int offset : offsets) {
            output.putInt(offset);
        }
        output.position(countsOffset);
        for (int count : counts) {
            output.putInt(count);
        }
        output.position(scaleOffset)
                .putDouble(elevation ? 0.01 : 0.001)
                .putDouble(elevation ? 0.01 : 0.001)
                .putDouble(0);
        output.position(tieOffset)
                .putDouble(0)
                .putDouble(0)
                .putDouble(0)
                .putDouble(-10)
                .putDouble(20)
                .putDouble(0);
        output.position(keysOffset)
                .putShort((short) 1)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 3);
        key(output, 1024, 2);
        key(output, 1025, elevation ? 2 : 1);
        key(output, 2048, 4326);
        for (int strip = 0; strip < strips; strip++) {
            output.position(offsets[strip]);
            int startRow = strip * rowsPerStrip;
            int rows = counts[strip] / width / bytesPerSample;
            for (int row = startRow; row < startRow + rows; row++) {
                for (int column = 0; column < width; column++) {
                    if (elevation) {
                        output.putShort((short) (column - row));
                    } else {
                        output.put((byte) (3 * column + 5 * row));
                    }
                }
            }
        }
        Files.write(path, output.array());
    }

    private static int even(int value) {
        return (value + 1) & ~1;
    }

    private static void shortEntry(ByteBuffer output, int tag, int value) {
        output.putShort((short) tag)
                .putShort((short) 3)
                .putInt(1)
                .putShort((short) value)
                .putShort((short) 0);
    }

    private static void longEntry(ByteBuffer output, int tag, int value) {
        output.putShort((short) tag).putShort((short) 4).putInt(1).putInt(value);
    }

    private static void longArrayEntry(ByteBuffer output, int tag, int count, int offset) {
        output.putShort((short) tag).putShort((short) 4).putInt(count).putInt(offset);
    }

    private static void shortArrayEntry(ByteBuffer output, int tag, int count, int offset) {
        output.putShort((short) tag).putShort((short) 3).putInt(count).putInt(offset);
    }

    private static void doubleEntry(ByteBuffer output, int tag, int count, int offset) {
        output.putShort((short) tag).putShort((short) 12).putInt(count).putInt(offset);
    }

    private static void key(ByteBuffer output, int key, int value) {
        output.putShort((short) key)
                .putShort((short) 0)
                .putShort((short) 1)
                .putShort((short) value);
    }

    record Fixture(
            Path raster,
            Path elevation,
            int rasterSize,
            int elevationSize,
            long rasterBytes,
            long elevationBytes) {}
}
