# G10-060 — HTTP tile one-tile slice

Status: Proposed
Depends on: G10-039
Gate: G10
Type: AFK

## Goal

Publish a working JDK-only HTTP XYZ adapter that acquires one bounded PNG/JPEG tile from a loopback
service and returns a detached raster source.

## Context

G10-006 approves the strict Java 21 HTTP client, URI-template, response, ownership, cancellation, and
diagnostic profile. G10-039 provides the dependency-neutral bounded encoded-byte image helper.

## Scope

Create `modules/mundane-map-io-http-tiles` with explicit client construction, strict template and XYZ
validation, HTTPS-by-default plus explicit cleartext opt-in for one fixed configured host, fixed
headers, one request with bounded body handling, PNG/JPEG media validation, G10-039 decode, one-tile
Web Mercator placement, and a detached no-network `RasterSource`. Callers own any network-range
allowlist; automated and consumer tests use loopback only. Add architecture, publication, staged
artifact, and offline-consumer coverage.

## Out of scope

Multi-tile regions, concurrent batches, missing-tile recovery, decoded caching, a viewer, retries,
redirects, authentication, proxies, custom headers, disk cache, public-network tests, and Native Image
claims.

## Acceptance criteria

- A valid one-tile loopback PNG or JPEG response becomes a correctly bounded detached raster whose
  reads and rendering perform no network I/O.
- Template, scheme, XYZ, fixed request headers, status/media/length, per-request timeout,
  cancellation, close, and one-body limits follow the G10-006 profile with stable nonsensitive
  diagnostics.
- The adapter uses direct Java 21 HTTP and explicit decoder construction; it is AWT-free and contains
  no external HTTP dependency, discovery, credential default, or public service URL.
- Complete module artifacts stage and a clean offline Java 21 consumer fetches from a loopback server
  and reads the detached source.

## Required tests

Template/scheme/XYZ math; loopback PNG/JPEG success; fixed headers; media/status/length failures; body
limit, cancellation, timeout, close, detached reads/rendering, architecture, publication, and offline-
consumer tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-http-tiles:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Automated and consumer tests use only repository-owned loopback servers. Fetch is an explicit blocking
operation that callers run off UI/render threads; the returned source is fully detached.
