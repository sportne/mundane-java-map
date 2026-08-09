package io.github.mundanej.map.example.vaadin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Request;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.WebSocketFrame;
import com.microsoft.playwright.options.BoundingBox;
import com.microsoft.playwright.options.WaitForSelectorState;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

/** Real-browser acceptance and evidence for the deliberately separate Vaadin lane. */
final class VaadinBrowserEvidenceTest {
    private static final String PLAYWRIGHT_VERSION = "1.60.0";
    private static final String VAADIN_VERSION = "25.2.4";

    @Test
    void pinnedBrowsersProduceBoundedFunctionalRenderingAndLifecycleEvidence() throws Exception {
        Path evidenceDirectory =
                Path.of(requireProperty("mundane.viewer.browserEvidence")).toAbsolutePath();
        Files.createDirectories(evidenceDirectory);
        List<Map<String, Object>> browserEvidence = new ArrayList<>();
        List<String> lifecycleEvidence = new ArrayList<>();

        SpringApplication application = VaadinViewerApplication.application();
        application.setDefaultProperties(
                Map.of(
                        "server.address", "127.0.0.1",
                        "server.port", "0",
                        "vaadin.productionMode", "true"));
        ConfigurableApplicationContext applicationContext = application.run();
        ViewerSessionRegistry registry = applicationContext.getBean(ViewerSessionRegistry.class);
        int port =
                Integer.parseInt(
                        applicationContext
                                .getEnvironment()
                                .getRequiredProperty("local.server.port"));
        String baseUrl = "http://127.0.0.1:" + port + "/";
        try (Playwright playwright = Playwright.create()) {
            browserEvidence.add(
                    exerciseBrowser(
                            "chromium",
                            playwright.chromium(),
                            baseUrl,
                            evidenceDirectory,
                            registry));
            browserEvidence.add(
                    exerciseBrowser(
                            "firefox", playwright.firefox(), baseUrl, evidenceDirectory, registry));
        } finally {
            int registeredBeforeShutdown = registry.registeredSessionCount();
            applicationContext.close();
            lifecycleEvidence.add(
                    "registered-before-application-shutdown=" + registeredBeforeShutdown);
            lifecycleEvidence.add(
                    "registered-after-application-shutdown=" + registry.registeredSessionCount());
        }
        assertEquals(0, registry.registeredSessionCount());

        LinkedHashMap<String, Object> report = new LinkedHashMap<>();
        report.put("schema", "G18-060-vaadin-browser-evidence-v1");
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("vaadin", VAADIN_VERSION);
        report.put("node", configuredNodeVersion());
        report.put("playwright", PLAYWRIGHT_VERSION);
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        report.put("architecture", System.getProperty("os.arch"));
        report.put("browsers", browserEvidence);
        report.put("lifecycle", lifecycleEvidence);
        writeEvidence(evidenceDirectory, report);
    }

    private static Map<String, Object> exerciseBrowser(
            String engine,
            BrowserType browserType,
            String baseUrl,
            Path evidenceDirectory,
            ViewerSessionRegistry registry)
            throws IOException {
        long started = System.nanoTime();
        LinkedHashMap<String, Object> observations = new LinkedHashMap<>();
        observations.put("engine", engine);
        List<String> externalRequests = new ArrayList<>();
        List<String> pageErrors = new ArrayList<>();
        AtomicLong webSocketFrames = new AtomicLong();
        AtomicLong maximumWebSocketFrameBytes = new AtomicLong();
        AtomicLong totalWebSocketFrameBytes = new AtomicLong();
        try (Browser browser =
                        browserType.launch(new BrowserType.LaunchOptions().setHeadless(true));
                BrowserContext context =
                        browser.newContext(
                                new Browser.NewContextOptions()
                                        .setAcceptDownloads(true)
                                        .setViewportSize(1280, 900))) {
            Page page = context.newPage();
            page.setDefaultTimeout(20_000);
            page.setDefaultNavigationTimeout(30_000);
            page.onPageError(pageErrors::add);
            page.onRequest(request -> recordExternalRequest(baseUrl, request, externalRequests));
            page.onWebSocket(
                    socket -> {
                        socket.onFrameReceived(
                                frame ->
                                        recordFrame(
                                                frame,
                                                webSocketFrames,
                                                maximumWebSocketFrameBytes,
                                                totalWebSocketFrameBytes));
                        socket.onFrameSent(
                                frame ->
                                        recordFrame(
                                                frame,
                                                webSocketFrames,
                                                maximumWebSocketFrameBytes,
                                                totalWebSocketFrameBytes));
                    });

            page.navigate(baseUrl);
            waitForMap(page);
            observations.put("browserVersion", browser.version());
            assertEquals(
                    engine.equals("chromium") ? "148.0.7778.96" : "150.0.2", browser.version());
            observations.put("initialSessionCount", registry.registeredSessionCount());
            assertAccessibleShell(page);
            assertResponsiveResize(page);

            @SuppressWarnings("unchecked")
            Map<String, Object> initialScene =
                    (Map<String, Object>)
                            page.evaluate(
                                    """
                                    () => { const map=document.querySelector('mundane-map-canvas');
                                      return {sceneGeneration:map.sceneGeneration,
                                        viewportGeneration:map.viewportGeneration,
                                        layerCount:map.scene.layers.length,
                                        utf8SceneBytes:new TextEncoder().encode(
                                          JSON.stringify(map.scene)).byteLength,
                                        kinds:[...new Set(map.scene.layers.flatMap(l=>l.features)
                                          .flatMap(f=>f.primitives).map(p=>p.kind))]}; }
                                    """);
            assertTrue(((Number) initialScene.get("layerCount")).intValue() >= 3);
            observations.put("initialScene", initialScene);

            exercisePointerSelectionMeasurementAndEditing(page, observations);
            exerciseStandaloneRenderAndProtocolFixture(
                    page, engine, evidenceDirectory, observations);
            exerciseRasterElevationAndAuthorization(page, browser, baseUrl, observations);
            exerciseDetachReattach(page, baseUrl, observations);
            exerciseUploadAndExport(page, observations);
            exerciseWrapAndSoak(page, observations);
            exerciseHostileClientEvents(page, observations);

            int sessionsBeforeReload = registry.registeredSessionCount();
            page.reload();
            waitForMap(page);
            assertTrue(registry.registeredSessionCount() >= 1);
            observations.put("sessionsBeforeReload", sessionsBeforeReload);
            observations.put("sessionsAfterReload", registry.registeredSessionCount());
            observations.put("reloadSceneGeneration", sceneGeneration(page));
            int sessionsBeforeClose = registry.registeredSessionCount();
            int closeStatus =
                    ((Number)
                                    page.evaluate(
                                            """
                                            async () => (await fetch('/browser-evidence/close-session',
                                              {method:'POST',credentials:'same-origin'})).status
                                            """))
                            .intValue();
            assertEquals(204, closeStatus);
            page.waitForCondition(() -> registry.registeredSessionCount() < sessionsBeforeClose);
            observations.put("sessionsAfterExplicitClose", registry.registeredSessionCount());
            assertTrue(pageErrors.isEmpty(), () -> "browser script errors: " + pageErrors);
            assertTrue(externalRequests.isEmpty(), () -> "external requests: " + externalRequests);
            observations.put("externalRequests", 0);
            observations.put("pageErrors", 0);
            observations.put("webSocketFrames", webSocketFrames.get());
            observations.put("maximumWebSocketFrameBytes", maximumWebSocketFrameBytes.get());
            observations.put("totalWebSocketFrameBytes", totalWebSocketFrameBytes.get());
            observations.put("elapsedMillis", (System.nanoTime() - started) / 1_000_000L);
        }
        return observations;
    }

    private static void assertAccessibleShell(Page page) {
        assertEquals("Mundane Java Map — Vaadin viewer", page.title());
        assertEquals(
                "main",
                page.locator("main.viewer-root").evaluate("node => node.tagName.toLowerCase()"));
        Locator canvas = page.locator("mundane-map-canvas canvas");
        page.evaluate(
                """
                () => {const m=document.querySelector('mundane-map-canvas');
                  const accept=m.$server.acceptMapInteraction.bind(m.$server);
                  m.__evidenceInteractions=[];
                  m.$server.acceptMapInteraction=(...args)=>accept(...args).then(outcome=>{
                    m.__evidenceInteractions.push({sequence:args[4],scene:args[2],
                      viewport:args[3],type:args[5],button:args[8],buttons:args[9],
                      clickCount:args[11],outcome,currentScene:m.sceneGeneration,
                      currentViewport:m.viewportGeneration});
                    return outcome;
                  });}
                """);
        assertEquals("application", canvas.getAttribute("role"));
        assertEquals("Interactive map", canvas.getAttribute("aria-label"));
        assertNotNull(canvas.getAttribute("aria-keyshortcuts"));
        assertTrue(canvas.getAttribute("aria-description").contains("Arrow keys"));
        assertEquals("0", canvas.getAttribute("tabindex"));
        @SuppressWarnings("unchecked")
        List<String> toolbarOrder =
                (List<String>)
                        page.evaluate(
                                "() => [...document.querySelectorAll('.top .toolbar [id]')].map(e=>e.id)");
        assertEquals(List.of("fit", "zoom-in", "zoom-out", "navigate"), toolbarOrder.subList(0, 4));
        page.locator("#fit").focus();
        page.keyboard().press("Tab");
        assertEquals("zoom-in", page.evaluate("() => document.activeElement.id"));
        for (String id :
                List.of(
                        "status-coordinates",
                        "status-selection",
                        "status-diagnostics",
                        "status-measurement",
                        "upload-status")) {
            assertEquals("polite", page.locator("#" + id).getAttribute("aria-live"));
        }
        canvas.focus();
        String outline =
                String.valueOf(
                        canvas.evaluate(
                                "node => { const s=getComputedStyle(node);"
                                        + " return s.outlineStyle+' '+s.outlineWidth; }"));
        assertTrue(outline.contains("solid") && !outline.endsWith("0px"), outline);

        page.evaluate(
                """
                () => document.querySelector('mundane-map-canvas').setMapEnabled(false)
                """);
        assertEquals("true", canvas.getAttribute("aria-disabled"));
        assertEquals("-1", canvas.getAttribute("tabindex"));
        page.evaluate(
                """
                () => document.querySelector('mundane-map-canvas').setMapEnabled(true)
                """);
        assertEquals("false", canvas.getAttribute("aria-disabled"));
        assertEquals("0", canvas.getAttribute("tabindex"));
    }

    private static void assertResponsiveResize(Page page) {
        page.setViewportSize(1280, 900);
        String wideColumns = computedStyle(page, ".body", "gridTemplateColumns");
        page.setViewportSize(640, 800);
        String narrowColumns = computedStyle(page, ".body", "gridTemplateColumns");
        assertFalse(wideColumns.equals(narrowColumns));
        assertTrue(narrowColumns.split(" ").length <= 1, narrowColumns);
        page.setViewportSize(1280, 900);
        page.waitForFunction(
                "() => document.querySelector('mundane-map-canvas').viewport.width > 700");
        waitForInteractionIdle(page);
    }

    private static void exercisePointerSelectionMeasurementAndEditing(
            Page page, Map<String, Object> observations) {
        Locator canvas = page.locator("mundane-map-canvas canvas");
        double centerX = viewportNumber(page, "width") / 2;
        double centerY = viewportNumber(page, "height") / 2;
        clickCanvas(page, centerX, centerY, 1);
        page.waitForFunction(
                "() => document.querySelector('#status-selection').textContent"
                        + ".includes('study-area / region')");
        String selected = page.locator("#status-selection").textContent();
        Object interactionState =
                page.evaluate(
                        """
                        () => {const m=document.querySelector('mundane-map-canvas'); return {
                          active:m.active,enabled:m.enabled,closed:m.closed,pointers:m.pointers.size,
                          clientSequence:m.clientEventSequence,
                          diagnostic:document.querySelector('#status-diagnostics').textContent,
                          coordinates:document.querySelector('#status-coordinates').textContent,
                          interactions:m.__evidenceInteractions};}
                        """);
        assertTrue(
                selected.contains("study-area") && selected.contains("region"),
                selected + " " + interactionState);

        page.locator("#measure").click();
        page.waitForFunction("() => document.querySelector('mundane-map-canvas').toolActive");
        clickCanvas(page, 360, 260, 1);
        moveCanvasPointer(page, 520, 300);
        page.waitForFunction(
                "() => !document.querySelector('#status-measurement').textContent.startsWith('EMPTY')");
        clickCanvas(page, 520, 300, 2);
        observations.put("measurement", page.locator("#status-measurement").textContent());
        assertTrue(page.locator("#status-coordinates").textContent().startsWith("x "));

        int before = editableFeatureCount(page);
        page.locator("#create-point").click();
        page.waitForTimeout(150);
        moveCanvasPointer(page, 610, 250);
        clickCanvas(page, 610, 250, 1);
        page.waitForFunction(
                "expected => document.querySelector('mundane-map-canvas').scene.layers"
                        + ".find(l=>l.id==='editable-points').features.length===expected",
                before + 1);
        page.locator("#undo").click();
        page.waitForFunction(
                "expected => document.querySelector('mundane-map-canvas').scene.layers"
                        + ".find(l=>l.id==='editable-points').features.length===expected",
                before);
        page.locator("mundane-map-canvas canvas").focus();
        page.keyboard().press("Control+Shift+Z");
        page.waitForFunction(
                "expected => document.querySelector('mundane-map-canvas').scene.layers"
                        + ".find(l=>l.id==='editable-points').features.length===expected",
                before + 1);
        observations.put("editableFeaturesAfterKeyboardRedo", editableFeatureCount(page));

        double priorScale = viewportNumber(page, "worldUnitsPerPixel");
        page.locator("#navigate").click();
        page.locator("#zoom-in").click();
        page.waitForFunction(
                "previous => document.querySelector('mundane-map-canvas').viewport.worldUnitsPerPixel < previous",
                priorScale);
        canvas.focus();
        double keyboardCenter = viewportNumber(page, "centerX");
        page.keyboard().press("ArrowRight");
        page.waitForFunction(
                "previous => document.querySelector('mundane-map-canvas').viewport.centerX > previous",
                keyboardCenter);
        BoundingBox box = canvas.boundingBox();
        assertNotNull(box);
        double priorCenter = viewportNumber(page, "centerX");
        page.mouse().move(box.x + 500, box.y + 300);
        waitForInteractionIdle(page);
        page.mouse().down();
        page.mouse()
                .move(
                        box.x + 560,
                        box.y + 330,
                        new com.microsoft.playwright.Mouse.MoveOptions().setSteps(4));
        page.mouse().up();
        page.waitForFunction(
                "previous => document.querySelector('mundane-map-canvas').viewport.centerX !== previous",
                priorCenter);
        observations.put("navigationViewportGeneration", viewportGeneration(page));
    }

    private static void exerciseStandaloneRenderAndProtocolFixture(
            Page page, String engine, Path evidenceDirectory, Map<String, Object> observations)
            throws IOException {
        byte[] icon =
                new byte[] {
                    77, 77, 82, 73, 1, 0, 0, 1, 0, 1, 0, 0, (byte) 230, 24, (byte) 190, (byte) 255
                };
        byte[] raster = rasterBytes();
        page.route(
                "**/browser-evidence/icon.mmri",
                route -> fulfill(route, "application/vnd.mundane-map.rgba-icon", icon));
        page.route(
                "**/browser-evidence/window.mmrw",
                route -> fulfill(route, "application/vnd.mundane-map.rgba-window", raster));
        @SuppressWarnings("unchecked")
        Map<String, Object> fixture =
                (Map<String, Object>)
                        page.evaluate(
                                """
                                async () => {
                                  const source=document.querySelector('mundane-map-canvas');
                                  const scene=structuredClone(source.scene);
                                  scene.componentGeneration=900; scene.sceneGeneration=0;
                                  scene.viewportGeneration=0; scene.rasters=[]; scene.labelCandidates=[];
                                  scene.viewport={...scene.viewport,width:600,height:400};
                                  const cx=scene.viewport.centerX, cy=scene.viewport.centerY;
                                  const w=scene.viewport.worldUnitsPerPixel;
                                  const map=(x,y)=>[cx+(x-300)*w,cy-(y-200)*w];
                                  const ring=points=>points.flatMap(point=>map(...point));
                                  const rings=[ring([[150,100],[450,100],[450,350],[150,350],
                                    [150,100]]),ring([[280,180],[320,180],[320,220],[280,220],
                                    [280,180]])];
                                  const polygon={kind:'polygon',rings,fill:[42,168,82,255],opacity:1};
                                  const hatch={kind:'hatch',rings:structuredClone(polygon.rings),
                                    pattern:'CROSS_DIAGONAL',stroke:{color:[28,42,96,255],width:2,
                                      unit:'SCREEN_PIXEL'},spacing:12,spacingUnit:'SCREEN_PIXEL',
                                    rotationMode:'SCREEN_RELATIVE',maxSegments:2000,opacity:.8};
                                  const point={kind:'point',coordinate:map(380,270),
                                    path:{commands:['MOVE_TO','LINE_TO','LINE_TO','CLOSE'],
                                      ordinates:[0,0,10,5,0,10]},viewBox:[0,0,10,10],
                                    size:[40,40],unit:'SCREEN_PIXEL',anchor:'CENTER',offset:[0,0],
                                    rotationDegrees:0,rotationMode:'SCREEN_RELATIVE',
                                    fill:[230,24,190,255],stroke:{present:false},
                                    endpointBearing:{present:true,value:90},opacity:1};
                                  const repeated={id:'browser-repeat-1',logicalId:'browser-repeat',
                                    copyIndex:1,name:'Repeated endpoint marker',primitives:[point]};
                                  scene.layers=[{id:'browser-vectors',name:'Browser vectors',features:[
                                    {id:'browser-polygon',logicalId:'browser-polygon',copyIndex:0,
                                      name:'Polygon with hole and hatch',primitives:[polygon,hatch]},
                                    {id:'browser-line',logicalId:'browser-line',copyIndex:0,
                                      name:'Line above fill',primitives:[{kind:'line',
                                        coordinates:[...map(160,130),...map(440,130)],stroke:{
                                          color:[220,28,34,255],width:8,unit:'SCREEN_PIXEL'},
                                        opacity:1}]},repeated,{id:'browser-icon',
                                    logicalId:'browser-icon',copyIndex:0,name:'Browser icon',primitives:[{
                                      kind:'icon',coordinate:map(520,300),
                                      resource:'./browser-evidence/icon.mmri',intrinsicWidth:1,
                                      intrinsicHeight:1,size:[32,32],unit:'SCREEN_PIXEL',anchor:'CENTER',
                                      offset:[0,0],rotationDegrees:0,rotationMode:'SCREEN_RELATIVE',
                                      interpolation:'NEAREST',endpointBearing:{present:false},opacity:1}]}
                                  ]}];
                                  const axisBounds=[...map(20,100),...map(120,20)];
                                  const affineBounds=[...map(480,100),...map(580,20)];
                                  scene.rasters=[{id:'browser-axis',logicalId:'browser-axis',copyIndex:0,
                                    name:'Axis raster',resource:'./browser-evidence/window.mmrw',width:2,
                                    height:2,opacity:1,interpolation:'NEAREST',sourceWindow:[0,0,2,2],
                                    imageMapBounds:axisBounds,clipMapBounds:axisBounds,
                                    placement:{kind:'AXIS_ALIGNED',bounds:axisBounds}},
                                    {id:'browser-affine',logicalId:'browser-affine',copyIndex:0,
                                    name:'Affine raster',resource:'./browser-evidence/window.mmrw',width:2,
                                    height:2,opacity:.85,interpolation:'BILINEAR',sourceWindow:[0,0,2,2],
                                    imageMapBounds:affineBounds,clipMapBounds:affineBounds,
                                    placement:{kind:'AFFINE',transform:[50*w,0,0,-40*w,
                                      ...map(495,40)]}}];
                                  scene.labelCandidates=[{ordinal:0,text:'Browser evidence label',
                                    fontFamily:'SANS_SERIF',weight:'BOLD',sizePixels:14}];
                                  const host=document.createElement('mundane-map-canvas');
                                  host.id='browser-evidence-map';
                                  host.style.cssText='display:block;width:600px;height:400px';
                                  const failures=[]; const measurements=[];
                                  host.$server={acceptClientFailure:(...a)=>failures.push(a),
                                    acceptLabelMeasurements:(...a)=>measurements.push(a),
                                    acceptPlacedLabels:()=>{},acceptSettledViewport:()=>{}};
                                  document.body.append(host); host.activateMap(1,900,0);
                                  const paintStarted=performance.now(); host.setScene(scene);
                                  for(let retry=0;retry<100&&host.sceneGeneration!==0;retry++)
                                    await new Promise(r=>setTimeout(r,10));
                                  host.setPlacedLabels(1,900,0,0,[{text:'Browser evidence label',
                                    color:[16,24,32,255],weight:'BOLD',sizePixels:14,
                                    baselineX:220,baselineY:55,advance:160,ordinal:0}]);
                                  const overlayPoint=structuredClone(point);
                                  overlayPoint.coordinate=map(250,130);
                                  overlayPoint.size=[28,28];
                                  overlayPoint.fill=[16,210,220,255];
                                  overlayPoint.endpointBearing={present:false};
                                  host.setInteractionOverlay(1,900,0,0,[{id:'__selection',
                                    name:'Selection overlay',features:[{id:'overlay',
                                      logicalId:'overlay',copyIndex:0,name:'Overlay',
                                      primitives:[overlayPoint]}]}]);
                                  await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));
                                  const ctx=host.canvas.getContext('2d');
                                  const data=ctx.getImageData(0,0,600,400).data;
                                  let nonBackground=0,opaque=0,redRegion=0,blueRegion=0,magentaRegion=0;
                                  for(let i=0;i<data.length;i+=4){
                                    if(data[i]!==255||data[i+1]!==255||data[i+2]!==255) nonBackground++;
                                    if(data[i+3]===255) opaque++;
                                    if(data[i]>data[i+1]+30&&data[i]>data[i+2]+30) redRegion++;
                                    if(data[i+2]>data[i]+30&&data[i+2]>data[i+1]+30) blueRegion++;
                                    if(data[i]>150&&data[i+2]>120&&data[i+1]<100) magentaRegion++;
                                  }
                                  const count=(x0,y0,x1,y1,predicate)=>{
                                    let result=0;
                                    for(let y=y0;y<y1;y++) for(let x=x0;x<x1;x++){
                                      const i=(y*600+x)*4;
                                      if(predicate(data[i],data[i+1],data[i+2],data[i+3])) result++;
                                    }
                                    return result;
                                  };
                                  const nonWhite=(r,g,b)=>r<245||g<245||b<245;
                                  const red=(r,g,b)=>r>g+60&&r>b+60;
                                  const blue=(r,g,b)=>b>r+30&&b>g+15;
                                  const magenta=(r,g,b)=>r>150&&b>120&&g<100;
                                  const cyan=(r,g,b)=>r<80&&g>150&&b>150;
                                  const localized={
                                    axisRaster:count(20,20,120,100,nonWhite),
                                    affineRaster:count(480,20,580,100,nonWhite),
                                    lineAboveFill:count(205,126,235,134,red),
                                    hatch:count(170,230,260,320,blue),
                                    labelEnvelope:count(215,35,395,62,nonWhite),
                                    endpointUpper:count(360,248,400,270,magenta),
                                    endpointLower:count(360,270,400,292,magenta),
                                    repeatedMarker:count(358,246,402,294,magenta),
                                    rasterIcon:count(504,284,536,316,magenta),
                                    interactionOverlay:count(236,116,264,144,cyan)};
                                  const kinds=[...new Set(scene.layers.flatMap(l=>l.features)
                                    .flatMap(f=>f.primitives).map(p=>p.kind))];
                                  const before=host.sceneGeneration;
                                  const malformed=structuredClone(scene); malformed.sceneGeneration=1;
                                  malformed.layers.push(structuredClone(malformed.layers[0]));
                                  host.setScene(malformed);
                                  const oversized=structuredClone(scene); oversized.sceneGeneration=1;
                                  oversized.layers[0].id='x'.repeat(257); host.setScene(oversized);
                                  const stale=structuredClone(scene); host.setScene(stale);
                                  host.setPlacedLabels(1,900,0,0,[{text:'</script><img src=x onerror=alert(1)>',
                                    color:[0,0,0,255],weight:'NORMAL',sizePixels:12,
                                    baselineX:10,baselineY:20,advance:220,ordinal:0}]);
                                  const holePixel=[...ctx.getImageData(300,200,1,1).data];
                                  return {kinds,nonBackground,opaque,redRegion,blueRegion,magentaRegion,
                                    paintMilliseconds:performance.now()-paintStarted,
                                    localized,
                                    holePixel,rasterPlacements:scene.rasters.map(r=>r.placement.kind),
                                    iconResources:host.iconResources.size,
                                    rasterResources:host.rasterResources.size,
                                    interactionLayers:host.interactionLayers.length,
                                    drawOrder:scene.layers.flatMap(layer=>layer.features)
                                      .flatMap(feature=>feature.primitives)
                                      .map(primitive=>primitive.kind),
                                    copyIndexes:scene.layers.flatMap(l=>l.features)
                                    .map(f=>f.copyIndex),labelMeasurements:measurements.length,
                                    failures:failures.map(f=>f[3]),
                                    droppedStaleWork:failures.some(f=>f[3]==='STALE_GENERATION'),
                                    generationBefore:before,
                                    generationAfter:host.sceneGeneration,
                                    injectedImages:host.querySelectorAll('img').length};
                                }
                                """);
        @SuppressWarnings("unchecked")
        List<String> kinds = (List<String>) fixture.get("kinds");
        assertTrue(
                kinds.containsAll(List.of("point", "icon", "line", "polygon", "hatch")),
                kinds::toString);
        assertTrue(((Number) fixture.get("nonBackground")).longValue() > 5_000);
        assertTrue(((Number) fixture.get("redRegion")).longValue() > 50);
        assertTrue(((Number) fixture.get("blueRegion")).longValue() > 50);
        assertTrue(((Number) fixture.get("magentaRegion")).longValue() > 50);
        assertEquals(
                List.of("polygon", "hatch", "line", "point", "icon"), fixture.get("drawOrder"));
        @SuppressWarnings("unchecked")
        Map<String, Number> localized = (Map<String, Number>) fixture.get("localized");
        assertTrue(localized.get("axisRaster").intValue() > 7_500, localized::toString);
        assertTrue(localized.get("affineRaster").intValue() > 7_000, localized::toString);
        assertTrue(localized.get("lineAboveFill").intValue() > 150, localized::toString);
        assertTrue(localized.get("hatch").intValue() > 300, localized::toString);
        assertTrue(localized.get("labelEnvelope").intValue() > 100, localized::toString);
        assertTrue(
                localized.get("endpointUpper").intValue()
                        > localized.get("endpointLower").intValue() * 1.4,
                localized::toString);
        assertTrue(localized.get("repeatedMarker").intValue() > 500, localized::toString);
        assertTrue(localized.get("rasterIcon").intValue() > 900, localized::toString);
        assertTrue(localized.get("interactionOverlay").intValue() > 250, localized::toString);
        assertEquals(List.of("AXIS_ALIGNED", "AFFINE"), fixture.get("rasterPlacements"));
        assertEquals(1, ((Number) fixture.get("iconResources")).intValue());
        assertEquals(1, ((Number) fixture.get("rasterResources")).intValue());
        assertEquals(1, ((Number) fixture.get("interactionLayers")).intValue());
        @SuppressWarnings("unchecked")
        List<Number> holePixel = (List<Number>) fixture.get("holePixel");
        assertTrue(
                holePixel.subList(0, 3).stream().allMatch(channel -> channel.intValue() >= 245),
                holePixel::toString);
        assertTrue(((List<?>) fixture.get("copyIndexes")).contains(1));
        assertTrue(((Number) fixture.get("labelMeasurements")).intValue() == 1);
        assertTrue(
                ((List<?>) fixture.get("failures"))
                        .containsAll(
                                List.of("DUPLICATE_ID", "LIMIT_EXCEEDED", "STALE_GENERATION")));
        assertEquals(fixture.get("generationBefore"), fixture.get("generationAfter"));
        assertEquals(0, ((Number) fixture.get("injectedImages")).intValue());
        page.locator("#browser-evidence-map canvas")
                .screenshot(
                        new Locator.ScreenshotOptions()
                                .setPath(evidenceDirectory.resolve(engine + "-render.png")));
        observations.put("renderFixture", fixture);
    }

    private static void fulfill(Route route, String contentType, byte[] body) {
        route.fulfill(
                new Route.FulfillOptions()
                        .setStatus(200)
                        .setContentType(contentType)
                        .setHeaders(Map.of("Content-Length", Integer.toString(body.length)))
                        .setBodyBytes(body));
    }

    private static byte[] rasterBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(48);
        buffer.put(new byte[] {77, 77, 82, 87, 1, 0, 0, 32});
        buffer.putInt(2).putInt(2).putLong(900).putLong(0);
        buffer.put(
                new byte[] {
                    (byte) 230,
                    25,
                    25,
                    (byte) 255,
                    25,
                    40,
                    (byte) 230,
                    (byte) 255,
                    25,
                    (byte) 190,
                    65,
                    (byte) 255,
                    (byte) 230,
                    (byte) 190,
                    25,
                    (byte) 255
                });
        return buffer.array();
    }

    private static void exerciseRasterElevationAndAuthorization(
            Page page, Browser browser, String baseUrl, Map<String, Object> observations) {
        long rasterStarted = System.nanoTime();
        page.locator("#fixture-raster").click();
        page.waitForFunction(
                "() => document.querySelector('.sources').textContent.toLowerCase().includes('raster')");
        navigateToMap(page, 108_000, 192_000, 30);
        page.waitForFunction(
                "() => document.querySelector('mundane-map-canvas').scene.rasters?.length > 0");
        observations.put(
                "rasterQueryAcceptedMillis", (System.nanoTime() - rasterStarted) / 1_000_000L);
        Object rasterState =
                page.evaluate(
                        """
                        () => {const m=document.querySelector('mundane-map-canvas'); return {
                          rasters:m.scene.rasters?.length,viewport:m.viewport,
                          diagnostic:document.querySelector('#status-diagnostics').textContent,
                          sources:document.querySelector('.sources').textContent,
                          sceneGeneration:m.sceneGeneration,viewportGeneration:m.viewportGeneration};}
                        """);
        assertTrue(
                ((Number)
                                        page.evaluate(
                                                "() => document.querySelector('mundane-map-canvas')"
                                                        + ".scene.rasters?.length || 0"))
                                .intValue()
                        > 0,
                String.valueOf(rasterState));
        String resource =
                String.valueOf(
                        page.evaluate(
                                "() => document.querySelector('mundane-map-canvas').scene.rasters[0].resource"));
        @SuppressWarnings("unchecked")
        Map<String, Object> authorized =
                (Map<String, Object>)
                        page.evaluate(
                                """
                                async resource => { const response=await fetch(resource,
                                  {credentials:'same-origin',cache:'no-store'});
                                  return {status:response.status,type:response.headers.get('content-type'),
                                    length:(await response.arrayBuffer()).byteLength}; }
                                """,
                                resource);
        assertEquals(200, ((Number) authorized.get("status")).intValue());
        assertEquals("application/vnd.mundane-map.rgba-window", authorized.get("type"));
        assertTrue(((Number) authorized.get("length")).longValue() > 32);
        String absoluteResource = URI.create(baseUrl).resolve(resource).toString();
        try (BrowserContext stranger = browser.newContext()) {
            Page strangerPage = stranger.newPage();
            strangerPage.navigate(baseUrl);
            int status =
                    ((Number)
                                    strangerPage.evaluate(
                                            "async url => (await fetch(url,{credentials:'same-origin'})).status",
                                            absoluteResource))
                            .intValue();
            assertTrue(
                    status == 403 || status == 404 || status == 410,
                    "cross-session status=" + status);
            observations.put("crossSessionRasterStatus", status);
        }
        String forged = absoluteResource.substring(0, absoluteResource.length() - 1) + "x";
        int forgedStatus =
                ((Number)
                                page.evaluate(
                                        "async url => (await fetch(url,{credentials:'same-origin'})).status",
                                        forged))
                        .intValue();
        assertTrue(forgedStatus == 403 || forgedStatus == 404 || forgedStatus == 410);
        observations.put("forgedRasterStatus", forgedStatus);
        observations.put("raster", authorized);

        long elevationStarted = System.nanoTime();
        page.locator("#fixture-elevation").click();
        page.waitForFunction(
                "() => document.querySelector('.sources').textContent.toLowerCase().includes('elevation')");
        navigateToMap(page, 1_750, 1_250, 4);
        page.waitForFunction(
                "() => document.querySelector('.sources').textContent.toLowerCase().includes('elevation')"
                        + " && document.querySelector('mundane-map-canvas').scene.rasters?.length > 0");
        observations.put(
                "elevationQueryAcceptedMillis",
                (System.nanoTime() - elevationStarted) / 1_000_000L);
        int expiredStatus =
                ((Number)
                                page.evaluate(
                                        "async url => (await fetch(url,{credentials:'same-origin'})).status",
                                        absoluteResource))
                        .intValue();
        assertTrue(expiredStatus == 403 || expiredStatus == 404 || expiredStatus == 410);
        observations.put("expiredRasterStatus", expiredStatus);
        observations.put(
                "elevationRasterCount",
                page.evaluate(
                        "() => document.querySelector('mundane-map-canvas').scene.rasters.length"));
    }

    private static void exerciseUploadAndExport(Page page, Map<String, Object> observations) {
        Path shapefile = Path.of(requireProperty("mundane.viewer.fixtures"), "shapefile");
        Path[] files;
        try (var stream = Files.list(shapefile)) {
            files = stream.sorted().toArray(Path[]::new);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
        page.selectOption(".upload-form select", "SHAPEFILE");
        for (int iteration = 0; iteration < 3; iteration++) {
            long priorScene = sceneGeneration(page);
            page.setInputFiles(".upload-form input[type=file]", files);
            page.locator(".upload-form button[type=submit]").focus();
            page.keyboard().press("Enter");
            page.waitForFunction(
                    "prior => document.querySelector('#upload-status').textContent"
                            + "==='UPLOAD_ACCEPTED'"
                            + " && document.querySelector('mundane-map-canvas').sceneGeneration>prior"
                            + " && document.querySelector('.sources').textContent"
                            + ".toLowerCase().includes('feature')",
                    (double) priorScene);
            page.locator("#clear-sources").click();
            page.waitForFunction(
                    "() => {const m=document.querySelector('mundane-map-canvas');"
                            + " return document.querySelector('.sources').textContent"
                            + ".includes('No local source opened') && m.scene.rasters.length===0"
                            + " && m.iconResources.size===0 && m.rasterResources.size===0;}");
        }
        observations.put("uploadStatus", page.locator("#upload-status").textContent());
        observations.put("uploadSoakIterations", 3);
        page.locator("#prepare-svg").click();
        page.waitForFunction(
                "() => document.querySelector('#status-diagnostics').textContent==='SVG_EXPORT_READY'");
        Download download = page.waitForDownload(() -> page.locator("#download-svg").click());
        Path downloaded = download.path();
        String svg = readUtf8(downloaded);
        assertTrue(svg.startsWith("<?xml") && svg.contains("<svg"));
        assertTrue(Files.exists(downloaded));
        observations.put("svgBytes", utf8Length(svg));
    }

    private static void exerciseWrapAndSoak(Page page, Map<String, Object> observations) {
        page.locator("#fixture-shapefile").click();
        page.waitForFunction(
                "() => document.querySelector('.sources').textContent.toLowerCase().includes('feature')");
        page.locator("#wrap-world").check();
        assertTrue(page.locator("#wrap-world").isChecked());
        long initialScene = sceneGeneration(page);
        for (int iteration = 0; iteration < 8; iteration++) {
            page.locator("#zoom-out").click();
            page.locator("#zoom-in").click();
            page.setViewportSize(1000 + iteration * 5, 760 + iteration * 3);
        }
        page.waitForFunction(
                "initial => document.querySelector('mundane-map-canvas').sceneGeneration > initial",
                (double) initialScene);
        @SuppressWarnings("unchecked")
        Map<String, Object> bounded =
                (Map<String, Object>)
                        page.evaluate(
                                """
                                () => { const map=document.querySelector('mundane-map-canvas');
                                  return {sceneGeneration:map.sceneGeneration,
                                    viewportGeneration:map.viewportGeneration,
                                    utf8FullSceneBytes:new TextEncoder().encode(
                                      JSON.stringify(map.scene)).byteLength,
                                    sceneTransferMode:'FULL_REPLACEMENT',
                                    patchMessagesObserved:0,patchBytesObserved:0,
                                    profileMaximumCanonicalLogicalBytes:64*1024*1024,
                                    profileMaximumLogicalFeatures:50000,
                                    profileMaximumPrimitives:200000,
                                    logicalFeatures:new Set(map.scene.layers.flatMap(layer=>
                                      layer.features.map(feature=>layer.id+'\u0000'+feature.logicalId))).size,
                                    visualFeatures:map.scene.layers.flatMap(l=>l.features).length,
                                    primitives:map.scene.layers.flatMap(l=>l.features)
                                      .flatMap(f=>f.primitives).length,
                                    rasters:map.scene.rasters.length,
                                    iconResources:map.iconResources.size,
                                    rasterResources:map.rasterResources.size,
                                    pendingToolEvents:map.pendingToolEvents,
                                    canvasBackingPixels:map.canvas.width*map.canvas.height,
                                    usedJsHeapBytes:performance.memory?.usedJSHeapSize || -1}; }
                                """);
        assertTrue(((Number) bounded.get("utf8FullSceneBytes")).longValue() <= 64L * 1024 * 1024);
        assertTrue(((Number) bounded.get("logicalFeatures")).longValue() <= 50_000);
        assertTrue(((Number) bounded.get("primitives")).longValue() <= 200_000);
        assertTrue(((Number) bounded.get("pendingToolEvents")).longValue() <= 34);
        observations.put("soak", bounded);
    }

    private static void exerciseHostileClientEvents(Page page, Map<String, Object> observations) {
        page.waitForTimeout(1_000);
        waitForInteractionIdle(page);
        @SuppressWarnings("unchecked")
        Map<String, Object> hostile =
                (Map<String, Object>)
                        page.evaluate(
                                """
                                async () => { const m=document.querySelector('mundane-map-canvas');
                                  const before={scene:m.sceneGeneration,viewport:m.viewportGeneration,
                                    icons:m.iconResources.size,rasters:m.rasterResources.size,
                                    pending:m.pendingToolEvents,pointers:m.pointers.size};
                                  const send=(sequence,scene,type,button,buttons,reason='') =>
                                    m.$server.acceptMapInteraction(1,m.componentGeneration,scene,
                                      m.viewportGeneration,sequence,type,20,20,button,buttons,
                                      0,0,0,false,reason);
                                  const stale=await send(m.eventSequence++,m.sceneGeneration-1,
                                    'MOVE',0,0);
                                  const malformedSequence=m.eventSequence++;
                                  const malformed=await send(malformedSequence,m.sceneGeneration,
                                    'NOT_AN_EVENT',0,0);
                                  const duplicate=await send(malformedSequence,m.sceneGeneration,
                                    'MOVE',0,0);
                                  const oversized=await send(m.eventSequence++,m.sceneGeneration,
                                    'X'.repeat(513),0,0);
                                  const invalidMask=await send(m.eventSequence++,m.sceneGeneration,
                                    'MOVE',0,8);
                                  const after={scene:m.sceneGeneration,viewport:m.viewportGeneration,
                                    icons:m.iconResources.size,rasters:m.rasterResources.size,
                                    pending:m.pendingToolEvents,pointers:m.pointers.size};
                                  return {before,after,accepted:[stale,malformed,duplicate,
                                    oversized,invalidMask].map(outcome=>outcome.accepted),
                                    cursors:[stale,malformed,duplicate,oversized,invalidMask]
                                      .map(outcome=>outcome.cursor)};
                                }
                                """);
        assertEquals(hostile.get("before"), hostile.get("after"));
        assertEquals(List.of(false, false, false, false, false), hostile.get("accepted"));
        assertEquals(
                List.of("DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT", "DEFAULT"),
                hostile.get("cursors"));
        observations.put("hostileClientEvents", hostile);
    }

    private static void exerciseDetachReattach(
            Page page, String baseUrl, Map<String, Object> observations) {
        String oldResource =
                String.valueOf(
                        page.evaluate(
                                "() => document.querySelector('mundane-map-canvas')"
                                        + ".scene.rasters[0].resource"));
        String oldResourceUrl = URI.create(baseUrl).resolve(oldResource).toString();

        page.navigate(URI.create(baseUrl).resolve("browser-evidence/lifecycle").toString());
        waitForMap(page);
        int revokedStatus =
                ((Number)
                                page.evaluate(
                                        "async url => (await fetch(url,{credentials:'same-origin'})).status",
                                        oldResourceUrl))
                        .intValue();
        assertTrue(revokedStatus == 403 || revokedStatus == 404 || revokedStatus == 410);
        long firstComponentGeneration =
                ((Number)
                                page.evaluate(
                                        "() => document.querySelector('mundane-map-canvas')"
                                                + ".componentGeneration"))
                        .longValue();
        page.locator("#detach-map").click();
        page.waitForFunction("() => !document.querySelector('#browser-lifecycle-map')");
        page.locator("#reattach-map").click();
        waitForMap(page);
        @SuppressWarnings("unchecked")
        Map<String, Object> reattached =
                (Map<String, Object>)
                        page.evaluate(
                                """
                                async oldGeneration => {
                                  const m=document.querySelector('mundane-map-canvas');
                                  const outcome=await m.$server.acceptMapInteraction(1,oldGeneration,
                                    m.sceneGeneration,m.viewportGeneration,m.eventSequence++,
                                    'MOVE',20,20,0,0,0,0,0,false,'');
                                  return {componentGeneration:m.componentGeneration,
                                    sceneGeneration:m.sceneGeneration,
                                    staleAccepted:outcome.accepted,pending:m.pendingToolEvents,
                                    pointers:m.pointers.size,icons:m.iconResources.size,
                                    rasters:m.rasterResources.size};
                                }
                                """,
                                (double) firstComponentGeneration);
        assertTrue(
                ((Number) reattached.get("componentGeneration")).longValue()
                        > firstComponentGeneration);
        assertEquals(false, reattached.get("staleAccepted"));
        assertEquals(0, ((Number) reattached.get("pending")).intValue());
        assertEquals(0, ((Number) reattached.get("pointers")).intValue());
        assertEquals(0, ((Number) reattached.get("icons")).intValue());
        assertEquals(0, ((Number) reattached.get("rasters")).intValue());
        reattached.put("revokedResourceStatus", revokedStatus);
        observations.put("detachReattach", reattached);

        page.navigate(baseUrl);
        waitForMap(page);
    }

    private static void waitForMap(Page page) {
        page.locator("mundane-map-canvas canvas")
                .waitFor(new Locator.WaitForOptions().setState(WaitForSelectorState.VISIBLE));
        page.waitForFunction(
                "() => { const map=document.querySelector('mundane-map-canvas');"
                        + " return map?.active && map.scene && map.sceneGeneration >= 0"
                        + " && map.viewportGeneration >= 0; }");
    }

    private static void clickCanvas(Page page, double x, double y, int clickCount) {
        Locator canvas = page.locator("mundane-map-canvas canvas");
        canvas.focus();
        waitForInteractionIdle(page);
        BoundingBox box = canvas.boundingBox();
        assertNotNull(box);
        double absoluteX = box.x + x;
        double absoluteY = box.y + y;
        page.mouse().move(absoluteX, absoluteY);
        waitForInteractionIdle(page);
        page.mouse()
                .click(
                        absoluteX,
                        absoluteY,
                        new com.microsoft.playwright.Mouse.ClickOptions()
                                .setClickCount(clickCount));
    }

    private static void moveCanvasPointer(Page page, double x, double y) {
        BoundingBox box = page.locator("mundane-map-canvas canvas").boundingBox();
        assertNotNull(box);
        page.mouse().move(box.x + x, box.y + y);
        waitForInteractionIdle(page);
    }

    private static void waitForInteractionIdle(Page page) {
        page.waitForFunction(
                "() => {const m=document.querySelector('mundane-map-canvas');"
                        + " return !m.viewportDirty && !m.settledTimer"
                        + " && m.pendingToolEvents===0"
                        + " && m.viewportGeneration===m.authoritativeViewportGeneration;}");
    }

    private static int editableFeatureCount(Page page) {
        return ((Number)
                        page.evaluate(
                                "() => document.querySelector('mundane-map-canvas').scene.layers"
                                        + ".find(l=>l.id==='editable-points').features.length"))
                .intValue();
    }

    private static void navigateToMap(
            Page page, double centerX, double centerY, double worldUnitsPerPixel) {
        page.evaluate(
                """
                a => {const m=document.querySelector('mundane-map-canvas');
                  m.viewport={...m.viewport,centerX:a.centerX,centerY:a.centerY,
                    worldUnitsPerPixel:a.worldUnitsPerPixel};
                  m.afterLocalNavigation(true);}
                """,
                Map.of(
                        "centerX", centerX,
                        "centerY", centerY,
                        "worldUnitsPerPixel", worldUnitsPerPixel));
        page.waitForTimeout(250);
    }

    private static long sceneGeneration(Page page) {
        return ((Number)
                        page.evaluate(
                                "() => document.querySelector('mundane-map-canvas').sceneGeneration"))
                .longValue();
    }

    private static long viewportGeneration(Page page) {
        return ((Number)
                        page.evaluate(
                                "() => document.querySelector('mundane-map-canvas').viewportGeneration"))
                .longValue();
    }

    private static double viewportNumber(Page page, String field) {
        return ((Number)
                        page.evaluate(
                                "field => document.querySelector('mundane-map-canvas').viewport[field]",
                                field))
                .doubleValue();
    }

    private static String computedStyle(Page page, String selector, String property) {
        return String.valueOf(
                page.evaluate(
                        "([selector,property]) => getComputedStyle(document.querySelector(selector))[property]",
                        List.of(selector, property)));
    }

    private static void recordExternalRequest(
            String baseUrl, Request request, List<String> externalRequests) {
        URI requestUri = URI.create(request.url());
        URI allowedOrigin = URI.create(baseUrl);
        if ((requestUri.getScheme().equals("http") || requestUri.getScheme().equals("https"))
                && (!requestUri.getScheme().equals(allowedOrigin.getScheme())
                        || !requestUri.getHost().equals(allowedOrigin.getHost())
                        || requestUri.getPort() != allowedOrigin.getPort())) {
            externalRequests.add(request.url());
        }
    }

    private static void recordFrame(
            WebSocketFrame frame,
            AtomicLong count,
            AtomicLong maximumBytes,
            AtomicLong totalBytes) {
        count.incrementAndGet();
        long bytes =
                frame.binary() == null
                        ? frame.text().getBytes(StandardCharsets.UTF_8).length
                        : frame.binary().length;
        maximumBytes.accumulateAndGet(bytes, Math::max);
        totalBytes.addAndGet(bytes);
    }

    private static String configuredNodeVersion() throws IOException, InterruptedException {
        Path node =
                Path.of(System.getProperty("user.home"), ".vaadin", "node-v24.14.0", "bin", "node");
        if (!Files.isExecutable(node)) {
            Path bundledNode =
                    Path.of(
                            System.getProperty("user.home"),
                            ".vaadin",
                            "node-v24.14.0",
                            "node.exe");
            node =
                    List.of(node.resolveSibling("node.exe"), bundledNode).stream()
                            .filter(Files::isRegularFile)
                            .findFirst()
                            .orElseThrow(() -> new IOException("configured Vaadin Node is absent"));
        }
        Process process = new ProcessBuilder(node.toString(), "--version").start();
        String version =
                new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertEquals(0, process.waitFor());
        assertEquals("v24.14.0", version);
        return version;
    }

    private static void writeEvidence(Path directory, Map<String, Object> report)
            throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        String json = gson.toJson(report) + System.lineSeparator();
        writeAtomically(directory.resolve("evidence.json"), json);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> browsers = (List<Map<String, Object>>) report.get("browsers");
        StringBuilder markdown = new StringBuilder();
        markdown.append("# G18-060 Vaadin browser evidence\n\n")
                .append("- Java: `")
                .append(report.get("java"))
                .append("`\n")
                .append("- Vaadin: `")
                .append(report.get("vaadin"))
                .append("`\n")
                .append("- Node: `")
                .append(report.get("node"))
                .append("`\n")
                .append("- Playwright: `")
                .append(report.get("playwright"))
                .append("`\n")
                .append("- OS: `")
                .append(report.get("os"))
                .append("` (`")
                .append(report.get("architecture"))
                .append("`)\n\n")
                .append(
                        "| Engine | Exact browser | Scene bytes/mode | Total/max WebSocket bytes | Elapsed |\n")
                .append("| --- | --- | --- | ---: | ---: |\n");
        for (Map<String, Object> browser : browsers) {
            @SuppressWarnings("unchecked")
            Map<String, Object> soak = (Map<String, Object>) browser.get("soak");
            markdown.append("| ")
                    .append(browser.get("engine"))
                    .append(" | `")
                    .append(browser.get("browserVersion"))
                    .append("` | ")
                    .append(soak.get("utf8FullSceneBytes"))
                    .append(" / `")
                    .append(soak.get("sceneTransferMode"))
                    .append("` | ")
                    .append(browser.get("totalWebSocketFrameBytes"))
                    .append(" / ")
                    .append(browser.get("maximumWebSocketFrameBytes"))
                    .append(" | ")
                    .append(browser.get("elapsedMillis"))
                    .append(" ms |\n");
        }
        markdown.append(
                "\nTiming and memory/transfer observations are environment-specific; "
                        + "no portable performance threshold is claimed.\n");
        writeAtomically(directory.resolve("evidence.md"), markdown.toString());
    }

    private static void writeAtomically(Path target, String text) throws IOException {
        Path parent = Objects.requireNonNull(target.getParent(), "target parent");
        Path fileName = Objects.requireNonNull(target.getFileName(), "target file name");
        Path temporary = Files.createTempFile(parent, fileName.toString(), ".tmp");
        Files.writeString(temporary, text, StandardCharsets.UTF_8);
        Files.move(
                temporary,
                target,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }

    private static String readUtf8(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static int utf8Length(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static String requireProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing system property " + name);
        }
        return value;
    }
}
