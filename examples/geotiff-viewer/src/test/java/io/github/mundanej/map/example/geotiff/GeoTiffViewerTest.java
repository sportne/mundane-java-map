package io.github.mundanej.map.example.geotiff;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.awt.MapView;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GeoTiffViewerTest {
    @Test
    void parsesOnlyOneNonNullPath() {
        assertEquals(Path.of("area.tif"), GeoTiffViewer.parsePath(new String[] {"area.tif"}));
        assertThrows(NullPointerException.class, () -> GeoTiffViewer.parsePath(null));
        assertThrows(
                NullPointerException.class, () -> GeoTiffViewer.parsePath(new String[] {null}));
        assertThrows(IllegalArgumentException.class, () -> GeoTiffViewer.parsePath(new String[0]));
        assertThrows(
                IllegalArgumentException.class,
                () -> GeoTiffViewer.parsePath(new String[] {"one.tif", "two.tif"}));
    }

    @Test
    void loadsSupportedFixtureAndTransfersOwnershipToLauncher(@TempDir Path directory)
            throws Exception {
        Path path = directory.resolve("area.tif");
        Files.write(path, fixture());
        ArrayList<String> failures = new ArrayList<>();
        AtomicReference<RasterSource> launched = new AtomicReference<>();
        assertTrue(
                GeoTiffViewer.runMain(
                        new String[] {path.toString()},
                        failures::add,
                        source -> {
                            assertEquals(4, source.metadata().width());
                            launched.set(source);
                        }));
        assertTrue(failures.isEmpty());
        RasterSource source = launched.get();
        assertFalse(source.isClosed());
        source.close();
        assertTrue(source.isClosed());
    }

    @Test
    void reportsArgumentAndStructuredFormatFailures(@TempDir Path directory) throws Exception {
        ArrayList<String> summaries = new ArrayList<>();
        assertFalse(GeoTiffViewer.runMain(new String[0], summaries::add, ignored -> {}));
        assertTrue(summaries.getFirst().contains("Usage: geotiff-viewer"));
        Path malformed = directory.resolve("bad.tif");
        Files.write(malformed, new byte[] {'I', 'I', 42, 0});
        assertFalse(
                GeoTiffViewer.runMain(
                        new String[] {malformed.toString()}, summaries::add, ignored -> {}));
        assertTrue(summaries.getLast().startsWith("GEOTIFF_HEADER_INVALID:"));
    }

    @Test
    void createsAPlacedViewOnEdtAndClosesOwnedSource(@TempDir Path directory) throws Exception {
        Path path = directory.resolve("area.tif");
        Files.write(path, fixture());
        RasterSource source = GeoTiffViewer.load(path);
        AtomicReference<MapView> view = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> view.set(GeoTiffViewer.createView(source)));
        assertEquals(1, view.get().layerBindings().size());
        SwingUtilities.invokeAndWait(view.get()::close);
        assertTrue(source.isClosed());
    }

    private static byte[] fixture() {
        ByteBuffer bytes = ByteBuffer.allocate(286).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'I').put((byte) 'I').putShort((short) 42).putInt(8);
        bytes.position(8).putShort((short) 13);
        entry(bytes, 256, 3, 1, 4);
        entry(bytes, 257, 3, 1, 3);
        entry(bytes, 258, 3, 1, 8);
        entry(bytes, 259, 3, 1, 1);
        entry(bytes, 262, 3, 1, 1);
        entry(bytes, 273, 4, 1, 274);
        entry(bytes, 277, 3, 1, 1);
        entry(bytes, 278, 4, 1, 3);
        entry(bytes, 279, 4, 1, 12);
        entry(bytes, 284, 3, 1, 1);
        entry(bytes, 33550, 12, 3, 170);
        entry(bytes, 33922, 12, 6, 194);
        entry(bytes, 34735, 3, 16, 242);
        bytes.putInt(0);
        bytes.position(170).putDouble(1).putDouble(1).putDouble(0);
        bytes.position(194)
                .putDouble(0)
                .putDouble(0)
                .putDouble(0)
                .putDouble(10)
                .putDouble(20)
                .putDouble(0);
        bytes.position(242)
                .putShort((short) 1)
                .putShort((short) 1)
                .putShort((short) 0)
                .putShort((short) 3);
        key(bytes, 1024, 2);
        key(bytes, 1025, 1);
        key(bytes, 2048, 4326);
        bytes.position(274);
        for (int value = 0; value < 12; value++) {
            bytes.put((byte) (value * 20));
        }
        return bytes.array();
    }

    private static void entry(ByteBuffer bytes, int tag, int type, int count, int value) {
        bytes.putShort((short) tag).putShort((short) type).putInt(count).putInt(value);
    }

    private static void key(ByteBuffer bytes, int key, int value) {
        bytes.putShort((short) key).putShort((short) 0).putShort((short) 1).putShort((short) value);
    }
}
