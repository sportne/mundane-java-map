package io.github.mundanej.map.io.mbtiles;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;
import javax.imageio.ImageIO;
import org.sqlite.jdbc4.JDBC4Connection;

/** Generates the strict synthetic MBTiles fixture staged for the downstream consumer. */
public final class MbTilesConsumerFixtureGenerator {
    private MbTilesConsumerFixtureGenerator() {}

    /** Writes one deterministic local fixture. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one output path");
        }
        Path output = Path.of(arguments[0]).toAbsolutePath().normalize();
        Files.createDirectories(Objects.requireNonNull(output.getParent(), "output parent"));
        Files.deleteIfExists(output);
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + output, output.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE metadata (name TEXT NOT NULL, value TEXT NOT NULL)");
            statement.execute(
                    "CREATE TABLE tiles (zoom_level INTEGER NOT NULL,"
                            + " tile_column INTEGER NOT NULL,tile_row INTEGER NOT NULL,"
                            + " tile_data BLOB NOT NULL)");
            statement.execute(
                    "INSERT INTO metadata VALUES"
                            + " ('name','Consumer tiles'),('format','png'),"
                            + " ('minzoom','0'),('maxzoom','0')");
            try (PreparedStatement tile =
                    connection.prepareStatement("INSERT INTO tiles VALUES (0,0,0,?)")) {
                tile.setBytes(1, png());
                tile.executeUpdate();
            }
        }
    }

    private static byte[] png() throws Exception {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(30, 110, 210));
            graphics.fillRect(0, 0, 256, 256);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IllegalStateException("PNG writer unavailable");
        }
        return output.toByteArray();
    }
}
