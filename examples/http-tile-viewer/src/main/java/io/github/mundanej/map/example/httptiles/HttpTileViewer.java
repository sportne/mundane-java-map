package io.github.mundanej.map.example.httptiles;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.github.mundanej.map.api.CancellationToken;
import io.github.mundanej.map.api.RasterSource;
import io.github.mundanej.map.api.SourceIdentity;
import io.github.mundanej.map.awt.AwtRasterDecoders;
import io.github.mundanej.map.awt.MapLayerBinding;
import io.github.mundanej.map.awt.MapView;
import io.github.mundanej.map.core.CrsDefinitions;
import io.github.mundanej.map.core.CrsRegistry;
import io.github.mundanej.map.io.http.tiles.HttpXyzClientOptions;
import io.github.mundanej.map.io.http.tiles.HttpXyzTemplate;
import io.github.mundanej.map.io.http.tiles.HttpXyzTileClient;
import io.github.mundanej.map.io.http.tiles.HttpXyzTiles;
import io.github.mundanej.map.io.http.tiles.XyzTileRegion;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.EventQueue;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.imageio.ImageIO;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;

/** Demonstrates bounded HTTP tile acquisition on a worker before map rendering. */
public final class HttpTileViewer {
    private HttpTileViewer() {}

    /**
     * Launches the self-contained loopback tile demonstration.
     *
     * @param arguments ignored; the example has no external locator
     */
    public static void main(String[] arguments) {
        EventQueue.invokeLater(HttpTileViewer::show);
    }

    static RasterSource acquireDemo() {
        if (EventQueue.isDispatchThread()) {
            throw new IllegalStateException("HTTP tile acquisition must run off the event thread");
        }
        HttpServer server = null;
        try {
            byte[][] tiles = {
                image(Color.RED), image(Color.GREEN), image(Color.BLUE), image(Color.YELLOW)
            };
            server =
                    HttpServer.create(
                            new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
            server.createContext(
                    "/",
                    exchange -> {
                        int[] coordinate = coordinate(exchange);
                        if (coordinate[0] == 1 && coordinate[1] == 1) {
                            respondMissing(exchange);
                        } else {
                            respond(exchange, tiles[coordinate[1] * 2 + coordinate[0]]);
                        }
                    });
            server.setExecutor(command -> Thread.ofVirtual().start(command));
            server.start();
            HttpXyzTemplate template =
                    HttpXyzTemplate.parse(
                            "http://127.0.0.1:"
                                    + server.getAddress().getPort()
                                    + "/{z}/{x}/{y}.png");
            try (HttpXyzTileClient client =
                    HttpXyzTiles.open(
                            new SourceIdentity("http-tile-viewer", "Loopback tiles"),
                            template,
                            HttpXyzClientOptions.defaults().allowingHttp(),
                            AwtRasterDecoders.level1())) {
                return client.fetch(new XyzTileRegion(1, 0, 0, 1, 1), CancellationToken.none());
            }
        } catch (IOException failure) {
            throw new IllegalStateException(
                    "Could not run the loopback tile demonstration", failure);
        } finally {
            if (server != null) {
                server.stop(0);
            }
        }
    }

    static MapView createView(RasterSource source) {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Map view creation must run on the event thread");
        }
        MapView view =
                new MapView(
                        CrsRegistry.level1(), CrsDefinitions.EPSG_3857, CrsDefinitions.EPSG_3857);
        view.setSize(800, 500);
        view.setLayerBindings(
                List.of(MapLayerBinding.ownedRaster("tiles", "Loopback tiles", source)));
        view.fitToData(12);
        return view;
    }

    private static void show() {
        JFrame frame = new JFrame("Mundane HTTP tile viewer");
        JLabel status = new JLabel("Loading four loopback tiles on a worker…");
        frame.add(status, BorderLayout.NORTH);
        frame.setSize(900, 600);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);
        ViewerSession session = startLoading(frame.getContentPane(), status);
        frame.addWindowListener(
                new WindowAdapter() {
                    @Override
                    public void windowClosed(WindowEvent event) {
                        session.close();
                    }
                });
    }

    static ViewerSession startLoading(Container container, JLabel status) {
        if (!EventQueue.isDispatchThread()) {
            throw new IllegalStateException("Viewer loading must start on the event thread");
        }
        ViewerSession session = new ViewerSession(container, status);
        session.execute();
        return session;
    }

    static final class ViewerSession implements AutoCloseable {
        private final Container container;
        private final JLabel status;
        private final AtomicBoolean disposed = new AtomicBoolean();
        private final AtomicReference<RasterSource> acquired = new AtomicReference<>();
        private final AtomicReference<MapView> installed = new AtomicReference<>();
        private final CountDownLatch finished = new CountDownLatch(1);
        private final SwingWorker<RasterSource, Void> worker =
                new SwingWorker<>() {
                    @Override
                    protected RasterSource doInBackground() {
                        RasterSource source = acquireDemo();
                        acquired.set(source);
                        if (disposed.get() && acquired.compareAndSet(source, null)) {
                            source.close();
                            return null;
                        }
                        return source;
                    }

                    @Override
                    protected void done() {
                        try {
                            get();
                            install();
                        } catch (Exception failure) {
                            closeAcquired();
                            status.setText("Load failed: " + failure.getClass().getSimpleName());
                        } finally {
                            finished.countDown();
                        }
                    }
                };

        ViewerSession(Container container, JLabel status) {
            this.container = container;
            this.status = status;
        }

        void execute() {
            worker.execute();
        }

        boolean await(long timeout, TimeUnit unit) throws InterruptedException {
            return finished.await(timeout, unit);
        }

        MapView installedView() {
            return installed.get();
        }

        private void install() {
            RasterSource source = acquired.getAndSet(null);
            if (source == null) {
                return;
            }
            if (disposed.get()) {
                source.close();
                return;
            }
            MapView view;
            try {
                view = createView(source);
            } catch (RuntimeException | Error failure) {
                source.close();
                throw failure;
            }
            installed.set(view);
            if (disposed.get()) {
                installed.compareAndSet(view, null);
                view.close();
                return;
            }
            try {
                container.add(view, BorderLayout.CENTER);
            } catch (RuntimeException | Error failure) {
                installed.compareAndSet(view, null);
                view.close();
                throw failure;
            }
            status.setText("Detached 2 × 2 XYZ mosaic; network and decode completed off EDT");
            container.revalidate();
        }

        @Override
        public void close() {
            disposed.set(true);
            worker.cancel(true);
            closeAcquired();
            MapView view = installed.getAndSet(null);
            if (view != null) {
                view.close();
            }
        }

        private void closeAcquired() {
            RasterSource source = acquired.getAndSet(null);
            if (source != null) {
                source.close();
            }
        }
    }

    private static byte[] image(Color color) throws IOException {
        BufferedImage image = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(color);
            graphics.fillRect(0, 0, 256, 256);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("PNG encoder unavailable");
        }
        return output.toByteArray();
    }

    private static int[] coordinate(HttpExchange exchange) {
        String path = exchange.getRequestURI().getPath();
        int zoomEnd = path.indexOf('/', 1);
        int xEnd = path.indexOf('/', zoomEnd + 1);
        int suffix = path.indexOf('.', xEnd + 1);
        return new int[] {
            Integer.parseInt(path.substring(zoomEnd + 1, xEnd)),
            Integer.parseInt(path.substring(xEnd + 1, suffix))
        };
    }

    private static void respond(HttpExchange exchange, byte[] body) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.sendResponseHeaders(200, body.length);
        try (exchange;
                var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private static void respondMissing(HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(404, -1);
        exchange.close();
    }
}
