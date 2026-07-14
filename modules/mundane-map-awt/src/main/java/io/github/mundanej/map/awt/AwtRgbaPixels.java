package io.github.mundanej.map.awt;

import io.github.mundanej.map.api.RgbaPixelBuffer;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Objects;

/** Direct packed-RGBA conversion confined to the Java2D host. */
final class AwtRgbaPixels {
    private AwtRgbaPixels() {}

    static int toArgb(int rgba) {
        return (rgba >>> 8) | (rgba << 24);
    }

    static BufferedImage toBufferedImage(RgbaPixelBuffer pixels, Runnable checkpoint) {
        Objects.requireNonNull(pixels, "pixels");
        Objects.requireNonNull(checkpoint, "checkpoint");
        checkpoint.run();
        BufferedImage image =
                new BufferedImage(pixels.width(), pixels.height(), BufferedImage.TYPE_INT_ARGB);
        int[] argb = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        int index = 0;
        for (int row = 0; row < pixels.height(); row++) {
            checkpoint.run();
            for (int column = 0; column < pixels.width(); column++) {
                if ((index & 4095) == 0) {
                    checkpoint.run();
                }
                argb[index++] = toArgb(pixels.rgbaAt(column, row));
            }
        }
        checkpoint.run();
        return image;
    }
}
