package io.github.mundanej.map.io.mbtiles;

import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.RasterRequest;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.RasterWindow;
import io.github.mundanej.map.api.SourceException;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.List;
import javax.swing.SwingUtilities;

/** Separate-JVM deployment probe for the exact MBTiles adapter runtime. */
public final class MbTilesDeploymentProbe {
    private MbTilesDeploymentProbe() {}

    /** Opens, reads, and renders the supplied zoom-zero fixture, then prints the stable outcome. */
    public static void main(String[] arguments) throws Exception {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one fixture path");
        }
        try {
            Path path = Path.of(arguments[0]);
            MbTiles.inspect(
                    path,
                    new SourceIdentity("deployment-probe", ""),
                    MbTilesInspectOptions.defaults(),
                    CancellationToken.none());
            try (RasterSource source =
                    MbTiles.open(
                            path,
                            new SourceIdentity("deployment-probe", ""),
                            0,
                            MbTilesOpenOptions.defaults(),
                            AwtRasterDecoders.level1(),
                            CancellationToken.none())) {
                source.read(
                        new RasterRequest(
                                new RasterWindow(0, 0, 256, 256),
                                16,
                                16,
                                java.util.Optional.empty()),
                        CancellationToken.none());
                SwingUtilities.invokeAndWait(() -> render(source));
            }
            System.out.println("SUCCESS");
        } catch (SourceException failure) {
            System.out.println(
                    failure.terminal().code()
                            + "|"
                            + failure.terminal().context().getOrDefault("reason", ""));
        }
    }

    private static void render(RasterSource source) {
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        try {
            view.setLayerBindings(
                    List.of(
                            MapLayerBinding.borrowedRaster(
                                    "deployment", "Deployment fixture", source)));
            view.setSize(160, 120);
            view.fitToData(8);
            BufferedImage image = new BufferedImage(160, 120, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                view.paint(graphics);
            } finally {
                graphics.dispose();
            }
        } finally {
            view.close();
        }
    }
}
