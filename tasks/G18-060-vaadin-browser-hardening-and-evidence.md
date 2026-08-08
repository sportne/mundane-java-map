# G18-060 — Vaadin browser hardening and evidence

Status: Proposed
Depends on: G18-052
Gate: G18
Type: HITL

## Goal

Harden the real client/server boundary and create reproducible Chromium/Firefox functional,
rendering, lifecycle, accessibility, and performance evidence using open-source Playwright.

## Context

Java unit tests cannot prove Canvas behavior, pointer gestures, browser resource fetches, route
lifecycle, or responsive presentation. TestBench is commercial and excluded, so browser evidence
must use an explicitly managed open-source lane.

## Scope

- Create a separate `vaadinBrowserTest` task/lane that starts the production-like example on a
  loopback random port and uses pinned open-source Playwright Java.
- Require explicitly installed pinned Chromium and Firefox binaries; add a separate opt-in setup
  command if downloads are needed, never an implicit normal-gate download.
- Exercise complete vector/symbol/label/raster/elevation rendering, resize, local gestures, settled
  queries, selection, measurement, editing, upload, export, wrap, detach/reattach, session close,
  malformed client messages, and transport authorization.
- Add tolerant structural/render assertions, accessibility checks, leak/cleanup probes, and bounded
  transfer/query/paint/memory evidence without portable timing thresholds.

## Out of scope

TestBench, Selenium duplication, pixel-perfect golden images, every browser/version/OS, load testing,
internet map data, live-track streaming, portable latency/FPS claims, or production penetration
testing.

## Acceptance criteria

- One command produces deterministic machine-readable and Markdown browser evidence with exact Java,
  Vaadin, Node, Playwright, browser, and OS versions.
- Current pinned Chromium and Firefox complete all critical user workflows against the real adapter
  and example with no commercial artifacts or external network requests.
- Rendering assertions cover geometry/order, holes, markers, endpoints, hatches, icon/raster color
  regions, label envelopes, overlays, affine placement, and repeated worlds without glyph/pixel
  identity claims.
- Hostile/stale/oversized client events and expired/forged binary resources fail predictably without
  script injection, partial state, cross-session access, or server resource leaks.
- Accessibility evidence covers component name/role/help, focus visibility/order, keyboard
  navigation and commands, disabled state, and non-Canvas text alternatives for current status.
- Evidence records bounded full/patch bytes, query generations, scene/paint latency, dropped stale
  work, and retained resources; observations remain environment-specific.

## Required tests

Pinned Chromium/Firefox Playwright workflows; client protocol mutation/boundaries; tolerant render
fixtures; route/session lifecycle and resource authorization; accessibility scan/manual keyboard
review; repeated navigation/resize/upload soak; bounded transfer/performance report.

## Validation

```bash
./gradlew vaadinBrowserTest --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **Chromium/Firefox map behavior, tolerant rendering, keyboard/accessibility,
lifecycle, and evidence interpretation review**. Browser setup remains explicit and separate from
normal offline/JVM verification.
