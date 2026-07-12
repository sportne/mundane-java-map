# G10-006 — Remote tile-source first slice

Status: Proposed
Depends on: G8-004
Gate: G10
Type: AFK

## Goal

Load and render Web Mercator XYZ PNG/JPEG tiles from a bounded, cancellable HTTP source using only JDK
networking and local-server tests.

## Context

Level 1 provides raster decoding, requests, caches, diagnostics, and explicit lifecycle. A remote tile
adapter must not hide network, authentication, cache, or cancellation policy behind generic raster
behavior.

## Scope

Create `mundane-map-io-http-tiles` only with working behavior, tests, Gradle registration, and Javadocs.
Support validated HTTPS/HTTP XYZ templates, Web Mercator bounds, caller-set connect/request timeouts,
maximum response bytes, bounded concurrency, cancellation, stable HTTP diagnostics, and explicit
no-cache or bounded in-memory cache modes. Disable redirects and credentials by default. Render tiles
through the Level 1 explicitly registered PNG/JPEG decoder.

## Out of scope

MBTiles, vector tiles, WMS/WMTS, authentication helpers, cookies, retries beyond an explicit fixed
caller policy, disk caches, offline synchronization, arbitrary projections, production network tests,
and external HTTP libraries.

## Acceptance criteria

- A caller can open a bounded XYZ source, request visible tiles, cancel outstanding work, render
  successful PNG/JPEG responses, and close owned resources.
- URI templates, zoom/x/y ranges, status/content type, response size, timeout, redirect, concurrency,
  and cache limits have deterministic validation and diagnostics.
- Tests use only a loopback JDK HTTP server and cover success, missing tile, malformed image, timeout,
  cancellation, oversized response, cache hit/eviction, and close-with-work-in-flight.
- The module is JDK-only, contains no AWT types, performs no implicit credential lookup, and is added
  only with its complete first slice.
- Native-targeted code avoids reflection, scanning, dynamic proxies, serialization, JNI, and internal
  JDK APIs.

## Required tests

Unit tests for tile math/template validation and cache bounds; loopback integration tests for HTTP
status, limits, cancellation, lifecycle, and decode/render integration; architecture tests for module
and prohibited-mechanism boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-io-http-tiles:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Do not contact a public tile server in tests or examples. Document that service terms, attribution, and
credentials remain the consuming application's responsibility.

