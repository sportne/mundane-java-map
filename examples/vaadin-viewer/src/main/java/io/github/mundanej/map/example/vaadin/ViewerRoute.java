package io.github.mundanej.map.example.vaadin;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.dom.Element;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.shared.Registration;
import io.github.mundanej.map.api.Layer;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.CompletionStage;
import org.springframework.beans.factory.annotation.Autowired;

/** Responsive, keyboard-ordered application shell for the in-memory browser map. */
@Tag("main")
@Route("")
@PageTitle("Mundane Java Map — Vaadin viewer")
@SuppressWarnings("serial")
public final class ViewerRoute extends Component implements AutoCloseable {
    private static final String STYLE =
            """
            .viewer-root{display:block;min-height:100vh;background:#f4f6f8;color:#17212b;
                font-family:system-ui,sans-serif}
            .shell{display:grid;grid-template-rows:auto 1fr auto;min-height:100vh}
            .top{padding:.75rem 1rem;background:#17324d;color:white}
            h1{font-size:1.15rem;margin:0}
            .toolbar{display:flex;flex-wrap:wrap;gap:.45rem;margin-top:.65rem}
            button{min-height:2.5rem;padding:.45rem .75rem;border:1px solid #6d7f90;
                border-radius:.35rem;background:white;color:#17212b}
            button[aria-pressed=true]{background:#cfe5fb;border-color:#195f9d}
            .body{display:grid;grid-template-columns:minmax(13rem,18rem) 1fr;min-height:0}
            aside{padding:1rem;background:#fff;border-right:1px solid #cad2da;overflow:auto}
            .map{min-width:0;min-height:28rem;padding:.75rem}
            .layer{display:grid;grid-template-columns:1fr auto auto;gap:.3rem;
                align-items:center;margin:.45rem 0}
            .source-open{display:grid;gap:.4rem;margin:.8rem 0}.source-open input{min-height:2.2rem}
            .status{display:grid;gap:.35rem;margin-top:1rem}
            footer{padding:.6rem 1rem;background:#e7edf2}
            .hint{font-size:.86rem;color:#405264}.sr-status{min-height:1.2rem}
            @media(max-width:760px){
                .body{grid-template-columns:1fr;grid-template-rows:auto minmax(24rem,1fr)}
                aside{border-right:0;border-bottom:1px solid #cad2da}.map{padding:.35rem}
            }
            """;

    /** Route-local owner of the map and its mutable in-memory data. */
    private final ViewerSession session;

    /** Session-destruction registration seam used by production and lifecycle tests. */
    private final SessionAccess sessionAccess;

    /** Application-stop owner registration. */
    private final Registration applicationRegistration;

    /** Current idempotent Vaadin session-destruction registration. */
    private Registration sessionDestroyRegistration = () -> {};

    /** Keyboard-ordered native control container. */
    private final NativeElement toolbar = element("nav", "toolbar");

    /** Native layer visibility and ordering control container. */
    private final NativeElement layerList = element("div", "layers");

    /** Native opened-source visibility and ordering control container. */
    private final NativeElement sourceList = element("div", "sources");

    /** Caller-selected trusted server-local path input. */
    private final NativeElement sourcePath = element("input", "");

    /** Screen-reader-visible coordinate status. */
    private final NativeElement coordinates = element("output", "sr-status");

    /** Screen-reader-visible selection status. */
    private final NativeElement selection = element("output", "sr-status");

    /** Screen-reader-visible structured diagnostic status. */
    private final NativeElement diagnostics = element("output", "sr-status");

    /** Screen-reader-visible measurement status. */
    private final NativeElement measurement = element("output", "sr-status");

    /** Guards the idempotent route-owned lifecycle. */
    private volatile boolean closed;

    /**
     * Creates one route-local map session and registers application-stop cleanup.
     *
     * @param registry application viewer-session registry
     */
    @Autowired
    public ViewerRoute(ViewerSessionRegistry registry) {
        this(registry, SessionAccess.production());
    }

    /**
     * Creates one route using an explicit session lifecycle seam.
     *
     * @param sessionAccess non-null session lifecycle seam
     */
    ViewerRoute(SessionAccess sessionAccess) {
        this(new ViewerSessionRegistry(), sessionAccess);
    }

    ViewerRoute(ViewerSessionRegistry registry, SessionAccess sessionAccess) {
        this.sessionAccess = java.util.Objects.requireNonNull(sessionAccess, "sessionAccess");
        session = new ViewerSession(this::dispatch);
        applicationRegistration =
                java.util.Objects.requireNonNull(registry, "registry")
                        .register(session, this::closeFromApplicationStop);
        getElement().getClassList().add("viewer-root");
        getElement().setAttribute("aria-label", "Mundane Java Map viewer");
        getElement().getStyle().set("display", "block");
        Element style = new Element("style");
        style.setText(STYLE);
        getElement().appendChild(style);

        NativeElement shell = element("div", "shell");
        NativeElement header = element("header", "top");
        NativeElement title = element("h1", "");
        title.text("Mundane Java Map — in-memory Vaadin viewer");
        toolbar.getElement().setAttribute("aria-label", "Map tools");
        addToolbarButtons();
        header.add(title, toolbar);

        NativeElement body = element("div", "body");
        NativeElement sidebar = element("aside", "");
        sidebar.getElement().setAttribute("aria-label", "Layers and map status");
        NativeElement layerHeading = element("h2", "");
        layerHeading.text("Layers");
        NativeElement sourceHeading = element("h2", "");
        sourceHeading.text("Server-local sources");
        NativeElement sourceOpen = sourceControls();
        NativeElement status = element("section", "status");
        status.getElement().setAttribute("aria-label", "Map status");
        status.add(label("Coordinates", coordinates), label("Selection", selection));
        status.add(label("Diagnostics", diagnostics), label("Measurement", measurement));
        NativeElement hint = element("p", "hint");
        hint.text(
                "No basemap or network map data is used. Select a tool, then focus the map "
                        + "and use pointer or keyboard input.");
        sidebar.add(layerHeading, layerList, sourceHeading, sourceOpen, sourceList, status, hint);

        NativeElement mapRegion = element("section", "map");
        mapRegion.getElement().setAttribute("aria-label", "Interactive map");
        mapRegion.add(session.map());
        body.add(sidebar, mapRegion);

        NativeElement footer = element("footer", "");
        footer.text(
                "Source paths are read by the trusted server process; browsers receive only bounded map payloads.");
        shell.add(header, body, footer);
        getElement().appendChild(shell.getElement());
        session.addObserver(this::refresh);
        refresh();
    }

    ViewerSession session() {
        return session;
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        if (!closed) {
            sessionDestroyRegistration.remove();
            sessionDestroyRegistration = sessionAccess.addDestroyListener(this, this::close);
        }
    }

    @Override
    protected void onDetach(DetachEvent event) {
        close();
        super.onDetach(event);
    }

    /** Releases the route-owned component, edit lane, resources, and listeners exactly once. */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        Throwable primary = null;
        primary = cleanup(primary, sessionDestroyRegistration::remove);
        sessionDestroyRegistration = () -> {};
        primary = cleanup(primary, applicationRegistration::remove);
        primary = cleanup(primary, session::close);
        throwIfPresent(primary);
    }

    private void addToolbarButtons() {
        toolbar.add(button("fit", "Fit", session::fit));
        toolbar.add(button("zoom-in", "Zoom in", () -> session.zoom(0.5)));
        toolbar.add(button("zoom-out", "Zoom out", () -> session.zoom(2)));
        toolbar.add(
                toolButton(
                        "navigate",
                        "Navigate",
                        ViewerSession.ToolMode.NAVIGATE,
                        session::navigate));
        toolbar.add(
                toolButton("measure", "Measure", ViewerSession.ToolMode.MEASURE, session::measure));
        toolbar.add(
                toolButton(
                        "create-point",
                        "Create point",
                        ViewerSession.ToolMode.CREATE_POINT,
                        session::createPoint));
        toolbar.add(
                toolButton(
                        "move-point",
                        "Move selected",
                        ViewerSession.ToolMode.MOVE_POINT,
                        session::movePoint));
        toolbar.add(button("undo", "Undo", session::undo));
        toolbar.add(button("redo", "Redo", session::redo));
        NativeElement wrap = element("label", "");
        NativeElement input = element("input", "");
        input.getElement().setAttribute("type", "checkbox");
        input.getElement().setAttribute("id", "wrap-world");
        input.getElement().setAttribute("aria-label", "Repeat horizontal world");
        input.getElement()
                .addEventListener(
                        "change",
                        event -> {
                            session.setWrapEnabled(
                                    event.getEventData().get("event.target.checked").asBoolean());
                            refresh();
                        })
                .addEventData("event.target.checked");
        NativeElement caption = element("span", "");
        caption.text("Repeat world");
        wrap.add(input, caption);
        toolbar.add(wrap);
    }

    private NativeElement toolButton(
            String id, String text, ViewerSession.ToolMode mode, Runnable action) {
        NativeElement button = button(id, text, action);
        button.getElement()
                .setAttribute("aria-pressed", Boolean.toString(session.toolMode() == mode));
        button.getElement().setAttribute("data-tool-mode", mode.name());
        return button;
    }

    private NativeElement button(String id, String text, Runnable action) {
        NativeElement button = element("button", "");
        button.getElement().setAttribute("type", "button");
        button.getElement().setAttribute("id", id);
        button.text(text);
        button.getElement()
                .addEventListener(
                        "click",
                        ignored -> {
                            action.run();
                            refresh();
                        });
        return button;
    }

    private void refresh() {
        if (closed) {
            return;
        }
        coordinates.text(session.coordinateText());
        selection.text(session.selectionText());
        diagnostics.text(session.diagnosticText());
        measurement.text(
                String.format(
                        Locale.ROOT,
                        "%s — %.1f m",
                        session.measurementState().phase(),
                        session.measurementState().displayedDistance().metres()));
        for (Element child : toolbar.getElement().getChildren().toList()) {
            String mode = child.getAttribute("data-tool-mode");
            if (mode != null) {
                child.setAttribute(
                        "aria-pressed", Boolean.toString(session.toolMode().name().equals(mode)));
            }
        }
        rebuildLayerList();
        rebuildSourceList();
    }

    private NativeElement sourceControls() {
        NativeElement controls = element("div", "source-open");
        sourcePath.getElement().setAttribute("type", "text");
        sourcePath.getElement().setAttribute("aria-label", "Trusted server-local source path");
        sourcePath.getElement().setAttribute("placeholder", "/server/path/data.shp");
        sourcePath
                .getElement()
                .addEventListener(
                        "change",
                        event ->
                                synchronizeSourcePath(
                                        event.getEventData()
                                                .get("event.target.value")
                                                .stringValue()))
                .addEventData("event.target.value");
        controls.add(sourcePath);
        NativeElement actions = element("div", "toolbar");
        actions.add(
                button("open-shapefile", "Open shapefile", () -> openPath(SourceKind.SHAPEFILE)));
        actions.add(button("open-raster", "Open GeoTIFF", () -> openPath(SourceKind.RASTER)));
        actions.add(
                button("open-elevation", "Open elevation", () -> openPath(SourceKind.ELEVATION)));
        actions.add(
                button("open-workspace", "Open workspace", () -> openPath(SourceKind.WORKSPACE)));
        actions.add(button("clear-sources", "Clear sources", session::clearSources));
        controls.add(actions);
        NativeElement fixtures = element("div", "toolbar");
        fixtures.add(
                button(
                        "fixture-shapefile",
                        "Fixture shapefile",
                        () -> openFixture(SourceKind.SHAPEFILE)));
        fixtures.add(
                button("fixture-raster", "Fixture raster", () -> openFixture(SourceKind.RASTER)));
        fixtures.add(
                button(
                        "fixture-elevation",
                        "Fixture elevation",
                        () -> openFixture(SourceKind.ELEVATION)));
        fixtures.add(
                button(
                        "fixture-workspace",
                        "Fixture workspace",
                        () -> openFixture(SourceKind.WORKSPACE)));
        controls.add(fixtures);
        return controls;
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> openPath(SourceKind kind) {
        try {
            return open(kind, Path.of(sourcePath.getElement().getProperty("value", "")));
        } catch (InvalidPathException failure) {
            return session.rejectInvalidSourcePath();
        }
    }

    void synchronizeSourcePath(String value) {
        String checked = java.util.Objects.requireNonNull(value, "value");
        sourcePath
                .getElement()
                .setProperty(
                        "value", checked.length() <= 4096 ? checked : String.valueOf((char) 0));
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> openFixture(SourceKind kind) {
        Path root =
                Path.of(
                        System.getProperty(
                                "mundane.viewer.fixtures",
                                "examples/vaadin-viewer/build/source-fixtures"));
        Path path =
                switch (kind) {
                    case SHAPEFILE ->
                            root.resolve("shapefile/generated-polygon-hole-windows1252-3857.shp");
                    case RASTER -> root.resolve("geotiff/gdal-rgb-strip-none-4326.tif");
                    case ELEVATION -> root.resolve("geotiff/gdal-int16-strip-packbits-4326.tif");
                    case WORKSPACE -> root.resolve("workspace/example.mmap.xml");
                };
        sourcePath.getElement().setProperty("value", path.toString());
        return open(kind, path);
    }

    CompletionStage<ViewerSourceWorkflows.OpenResult> open(SourceKind kind, Path path) {
        return switch (kind) {
            case SHAPEFILE -> session.openShapefile(path);
            case RASTER -> session.openRaster(path);
            case ELEVATION -> session.openElevation(path);
            case WORKSPACE -> session.openWorkspace(path);
        };
    }

    private void rebuildSourceList() {
        sourceList.getElement().removeAllChildren();
        if (session.sourceLayers().isEmpty()) {
            NativeElement empty = element("p", "hint");
            empty.text(session.sourceBusy() ? "Opening source…" : "No local source opened");
            sourceList.add(empty);
            return;
        }
        for (ViewerSourceWorkflows.SourceLayer layer : session.sourceLayers()) {
            NativeElement row = element("div", "layer");
            NativeElement visible = element("input", "");
            visible.getElement().setAttribute("type", "checkbox");
            visible.getElement().setAttribute("aria-label", "Show " + layer.name());
            visible.getElement().setProperty("checked", layer.visible());
            visible.getElement()
                    .addEventListener(
                            "change",
                            event -> {
                                session.setSourceVisible(
                                        layer.id(),
                                        event.getEventData()
                                                .get("event.target.checked")
                                                .asBoolean());
                                refresh();
                            })
                    .addEventData("event.target.checked");
            NativeElement name = element("span", "");
            name.text(layer.name() + " (" + layer.kind().name().toLowerCase(Locale.ROOT) + ")");
            NativeElement actions = element("span", "");
            actions.add(
                    button(
                            "source-up-" + layer.id(),
                            "↑",
                            () -> session.moveSource(layer.id(), -1)));
            actions.add(
                    button(
                            "source-down-" + layer.id(),
                            "↓",
                            () -> session.moveSource(layer.id(), 1)));
            row.add(visible, name, actions);
            sourceList.add(row);
        }
    }

    private void dispatch(Runnable operation) {
        getUI().ifPresentOrElse(ui -> ui.accessSynchronously(operation::run), operation);
    }

    private void closeFromApplicationStop() {
        try {
            dispatch(this::close);
        } catch (RuntimeException | Error dispatchFailure) {
            try {
                close();
            } catch (RuntimeException | Error cleanupFailure) {
                dispatchFailure.addSuppressed(cleanupFailure);
            }
            throw dispatchFailure;
        }
    }

    private static Throwable cleanup(Throwable primary, Runnable operation) {
        try {
            operation.run();
        } catch (RuntimeException | Error failure) {
            if (primary == null) {
                return failure;
            }
            if (primary != failure) {
                primary.addSuppressed(failure);
            }
        }
        return primary;
    }

    private static void throwIfPresent(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            throw runtime;
        }
        if (failure instanceof Error error) {
            throw error;
        }
    }

    enum SourceKind {
        SHAPEFILE,
        RASTER,
        ELEVATION,
        WORKSPACE
    }

    private void rebuildLayerList() {
        layerList.getElement().removeAllChildren();
        for (Layer layer : session.layers()) {
            NativeElement row = element("div", "layer");
            NativeElement visible = element("input", "");
            visible.getElement().setAttribute("type", "checkbox");
            visible.getElement().setAttribute("aria-label", "Show " + layer.name());
            visible.getElement().setProperty("checked", session.isLayerVisible(layer.id()));
            visible.getElement()
                    .addEventListener(
                            "change",
                            event -> {
                                session.setLayerVisible(
                                        layer.id(),
                                        event.getEventData()
                                                .get("event.target.checked")
                                                .asBoolean());
                                refresh();
                            })
                    .addEventData("event.target.checked");
            NativeElement name = element("span", "");
            name.text(layer.name());
            NativeElement actions = element("span", "");
            actions.add(button("up-" + layer.id(), "↑", () -> session.moveLayer(layer.id(), -1)));
            actions.add(button("down-" + layer.id(), "↓", () -> session.moveLayer(layer.id(), 1)));
            row.add(visible, name, actions);
            layerList.add(row);
        }
    }

    private static NativeElement label(String heading, NativeElement output) {
        NativeElement wrapper = element("div", "");
        NativeElement strong = element("strong", "");
        strong.text(heading + ": ");
        output.getElement().setAttribute("aria-live", "polite");
        wrapper.add(strong, output);
        return wrapper;
    }

    private static NativeElement element(String tag, String className) {
        NativeElement element = new NativeElement(tag);
        if (!className.isEmpty()) {
            element.getElement().setAttribute("class", className);
        }
        return element;
    }

    private static final class NativeElement extends Component {
        NativeElement(String tag) {
            super(new Element(tag));
        }

        void add(Component... children) {
            for (Component child : children) {
                getElement().appendChild(child.getElement());
            }
        }

        void text(String text) {
            getElement().setText(text);
        }
    }

    /** Registers route-local cleanup against the exact attached Vaadin session. */
    @FunctionalInterface
    interface SessionAccess {
        /**
         * Registers one route-local destruction listener.
         *
         * @param route attached route
         * @param listener idempotent cleanup listener
         * @return idempotent listener-removal registration
         */
        Registration addDestroyListener(ViewerRoute route, Runnable listener);

        /**
         * Returns the production Vaadin session access.
         *
         * @return exact attached-session registration strategy
         */
        static SessionAccess production() {
            return (route, listener) ->
                    route.getUI()
                            .<Registration>map(
                                    ui -> {
                                        var ownedSession = ui.getSession();
                                        return ownedSession
                                                .getService()
                                                .addSessionDestroyListener(
                                                        event -> {
                                                            if (event.getSession()
                                                                    == ownedSession) {
                                                                listener.run();
                                                            }
                                                        });
                                    })
                            .orElse(() -> {});
        }
    }
}
