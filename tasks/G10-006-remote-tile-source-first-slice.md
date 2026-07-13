# G10-006 — Remote tile-source first slice

Status: Proposed
Depends on: G8-004
Gate: G10
Type: AFK

## Goal

Approve an explicit, bounded Java 21 HTTP acquisition boundary that turns one Web Mercator XYZ
PNG/JPEG tile region into a detached raster source without performing network I/O during rendering.

## Context

Level 1 provides raster decoding, requests, caches, diagnostics, and explicit lifecycle; the approved
G10-004 profile defines the shared encoded-byte decode surface. A remote adapter must not block Swing
painting or hide network, authentication, cache, cancellation, or JDK HTTP-client lifetime behind
generic raster behavior.

## Scope

Define the future `mundane-map-io-http-tiles` facade, client/snapshot ownership, exact URI-template and
XYZ math, HTTPS/default and explicit cleartext policy, direct/proxy-free Java 21 `HttpClient`, fixed
headers, no-client-certificate TLS context, response/status/media profile, bounded body subscriber,
timeouts/deadline/concurrency, cross-thread cancellation/close behavior, missing-tile recovery,
transactional decoded LRU, stable diagnostics, detached raster semantics, G6 composition, publication,
consumer, test server, and later working vertical slices.

## Out of scope

Production code or modules, a live-network `RasterSource`, MBTiles, vector tiles, WMS/WMTS,
authentication/custom headers, cookies, redirects, proxies, retries, compression, disk caches,
offline synchronization, arbitrary projections, production/public-network tests, external HTTP
libraries, background refresh/prefetch, and a Native Image or latency claim.

## Acceptance criteria

- The design exposes one blocking `HttpXyzTileClient.fetch(...)` that must run off UI/render threads
  and returns a fully detached G4 `RasterSource`; the returned source performs no network I/O.
- Template, scheme, request headers, XYZ/extent math, deterministic batching, status/media/encoded
  body handling, cancellation/deadline, missing tiles, cache commit, close races, and cleanup order are
  exact and implementation-ready.
- Limits cover template/region, tiles/concurrency, headers, per/aggregate bodies, output/cache/project
  bytes, deadlines, and warnings while explicitly qualifying opaque JDK DNS/TLS/transport allocation.
- The adapter never derives diagnostic identity or context from host, URI, headers, body,
  credential-like data, or provider messages; callers supply a nonsensitive logical source ID, and a
  decode failure exposes only one closed G6 `imageCode` token.
- G10-039 and G10-060 through G10-062 deliver the shared image helper and working module slices; no
  module, network request, new verification command, Native Image claim, or public example URL lands
  in this design task.

## Required tests

No production tests. Define later pure tile/template/cache tests and loopback-only integration cases
for PNG/JPEG success, missing/invalid/status/media/header/body outcomes, deadline/cancellation,
deterministic concurrency, close-in-flight, transactional cache hit/eviction/rollback, detached
rendering, aggregate reservation, nested image cancellation/limit/closed-code translation, common
failed-batch drain/poisoning, no-client-certificate TLS, publication consumer, and architecture/
prohibited-mechanism boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

AFK design checkpoint: the task has no maintainer-only policy choice. The approved first profile uses
no credentials and no default service; consumers separately own service selection, permission,
attribution, rate limits, and cleartext opt-in. Later implementation never contacts a public server in
automated tests.
