package io.github.mundanej.map.io.geopackage;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;
import org.sqlite.jdbc4.JDBC4Connection;

/** Generates the synthetic strict fixture consumed by the staged downstream build. */
public final class GeoPackageConsumerFixtureGenerator {
    private GeoPackageConsumerFixtureGenerator() {}

    /** Writes one deterministic-content Point GeoPackage to the supplied path. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one fixture output path");
        }
        Path path = Path.of(arguments[0]).toAbsolutePath().normalize();
        Files.createDirectories(Objects.requireNonNull(path.getParent(), "fixture parent"));
        Files.deleteIfExists(path);
        try (JDBC4Connection connection =
                        new JDBC4Connection(
                                "jdbc:sqlite:" + path, path.toString(), new Properties());
                Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA application_id=1196444487");
            statement.execute("PRAGMA user_version=10400");
            statement.execute(
                    """
                    CREATE TABLE gpkg_spatial_ref_sys (
                      srs_name TEXT NOT NULL,
                      srs_id INTEGER NOT NULL PRIMARY KEY,
                      organization TEXT NOT NULL,
                      organization_coordsys_id INTEGER NOT NULL,
                      definition TEXT NOT NULL,
                      description TEXT)
                    """);
            statement.execute(
                    """
                    INSERT INTO gpkg_spatial_ref_sys VALUES
                      ('Undefined Cartesian',-1,'NONE',-1,'undefined',''),
                      ('Undefined Geographic',0,'NONE',0,'undefined',''),
                      ('WGS 84',4326,'EPSG',4326,'WGS84','')
                    """);
            statement.execute(
                    """
                    CREATE TABLE gpkg_contents (
                      table_name TEXT NOT NULL PRIMARY KEY,
                      data_type TEXT NOT NULL,
                      identifier TEXT UNIQUE,
                      description TEXT DEFAULT '',
                      last_change DATETIME NOT NULL DEFAULT
                        (strftime('%Y-%m-%dT%H:%M:%fZ','now')),
                      min_x DOUBLE, min_y DOUBLE, max_x DOUBLE, max_y DOUBLE,
                      srs_id INTEGER,
                      FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))
                    """);
            statement.execute(
                    """
                    CREATE TABLE gpkg_geometry_columns (
                      table_name TEXT NOT NULL,
                      column_name TEXT NOT NULL,
                      geometry_type_name TEXT NOT NULL,
                      srs_id INTEGER NOT NULL,
                      z TINYINT NOT NULL,
                      m TINYINT NOT NULL,
                      PRIMARY KEY (table_name, column_name),
                      FOREIGN KEY (table_name) REFERENCES gpkg_contents(table_name),
                      FOREIGN KEY (srs_id) REFERENCES gpkg_spatial_ref_sys(srs_id))
                    """);
            statement.execute("CREATE TABLE points (fid INTEGER PRIMARY KEY, geom BLOB NOT NULL)");
            statement.execute(
                    """
                    INSERT INTO gpkg_contents VALUES
                      ('points','features','points','',
                       '2026-01-01T00:00:00.000Z',-5,-5,5,5,4326)
                    """);
            statement.execute(
                    "INSERT INTO gpkg_geometry_columns VALUES"
                            + " ('points','geom','POINT',4326,0,0)");
            try (PreparedStatement insert =
                    connection.prepareStatement("INSERT INTO points VALUES (?,?)")) {
                insert.setLong(1, 1);
                insert.setBytes(2, point(0, 0));
                insert.executeUpdate();
            }
        }
    }

    private static byte[] point(double x, double y) {
        ByteBuffer bytes = ByteBuffer.allocate(8 + 1 + 4 + 16).order(ByteOrder.LITTLE_ENDIAN);
        bytes.put((byte) 'G').put((byte) 'P').put((byte) 0).put((byte) 1);
        bytes.putInt(4326);
        bytes.put((byte) 1).putInt(1).putDouble(x).putDouble(y);
        return bytes.array();
    }
}
