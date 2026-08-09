# G18-060 Vaadin browser evidence profile

The separate `vaadinBrowserTest` lane runs the production-mode Vaadin viewer on a random loopback
port. It uses pinned Playwright Java 1.60.0 with explicitly installed Chromium 148.0.7778.96 and
Firefox 150.0.2. Browser installation is owned only by the opt-in
`:examples:vaadin-viewer:installVaadinBrowserBinaries` task; normal gates do not invoke it.
The binaries use `playwright/mundane-map-1.60.0` under the Gradle user home, isolating their
Playwright garbage-collection markers from unrelated local installations.

The checked evidence schema is `G18-060-vaadin-browser-evidence-v1`. Each engine must establish:

- no external HTTP requests or browser script errors;
- an accessible application role/name/help surface, visible keyboard focus, native control order,
  live text status, and an explicit disabled map state;
- responsive Canvas sizing, pointer navigation, selection, measurement, point editing, keyboard
  redo, source upload, SVG export, horizontal-wrap transitions, reload, and real servlet-session
  destruction;
- vector fill/line/point order, a polygon hole, endpoint rotation, hatching, a raster icon, label
  measurement/placement, axis-aligned and affine raster windows, and repeated-copy metadata through
  tolerant structural, color-region, and screenshot checks;
- stable rejection of duplicate, oversized, stale, and injection-shaped protocol values without a
  partial scene, plus same-session authorization and cross-session/forged rejection for binary
  resources; and
- bounded UTF-8 full-scene bytes, explicit full-replacement/no-patch observations,
  named 64-MiB/50,000-feature/200,000-primitive profile ceilings, feature/resource counts, pending
  input, exact total/maximum WebSocket frame bytes, source-query acceptance times, three
  upload/clear cycles, and repeated resize/zoom work without a portable latency or memory threshold.

The task writes `evidence.json`, `evidence.md`, `chromium-render.png`, and `firefox-render.png` to
`examples/vaadin-viewer/build/reports/vaadin-browser/`. The report records exact Java, Vaadin,
configured Node, Playwright, browser, operating-system, and architecture versions.
