package io.github.mundanej.map.performance;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;

/** Maintainer-only deterministic authoring helper for the checked raster evidence resources. */
public final class AuthorRasterResources {
    private AuthorRasterResources() {}

    /** Writes the two fixed resources to the one supplied empty directory. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one output directory");
        }
        Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        BufferedImage png = new BufferedImage(1_024, 768, BufferedImage.TYPE_INT_ARGB);
        BufferedImage jpeg = new BufferedImage(1_024, 768, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < 768; row++) {
            for (int column = 0; column < 1_024; column++) {
                int alpha = (column + row) % 5 == 0 ? 128 : 255;
                png.setRGB(
                        column,
                        row,
                        alpha << 24
                                | (column & 255) << 16
                                | (row & 255) << 8
                                | ((column ^ row) & 255));
                int tileX = column / 64;
                int tileY = row / 64;
                jpeg.setRGB(
                        column,
                        row,
                        ((17 * tileX + 3 * tileY) & 255) << 16
                                | ((5 * tileX + 19 * tileY) & 255) << 8
                                | ((11 * tileX + 7 * tileY) & 255));
            }
        }
        if (!ImageIO.write(png, "png", output.resolve("evidence.png").toFile())
                || !ImageIO.write(jpeg, "jpeg", output.resolve("evidence.jpg").toFile())) {
            throw new IllegalStateException("Required JDK image encoder is unavailable");
        }
    }
}
