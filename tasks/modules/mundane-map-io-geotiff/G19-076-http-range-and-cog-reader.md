# G19-076 — Guarded HTTP range and COG reader

Status: Proposed
Depends on: G19-050, G19-051, G19-075
Gate: G19
Type: HITL

## Goal

Read and structurally validate Cloud Optimized GeoTIFF through bounded, validator-consistent HTTP
range requests using the project's explicit transport policy.

## Context

The adapter has no HTTP access or COG validation. A generic download would defeat COG's windowed
access utility and could mix bytes if a remote representation changes between requests.

## Scope

- Adapt the guarded HTTP transport to explicit random byte ranges with strong validator/length/
  content-range consistency and no ambient URL or credential behavior.
- Coalesce bounded metadata/tile ranges, cap requests/bytes/gaps/retries, integrate encoded cache
  validators, and cancel sibling work on terminal failure.
- Validate applicable OGC COG 1.0 GeoTIFF Tiles, Overviews, Keys, and Optimized GeoTIFF file classes,
  including IFD/data ordering and optimized range discovery.
- Expose validation reports separately from ordinary compatible remote-GeoTIFF reading.
- Preserve atomic request results across 200 fallback, multipart/byteranges policy, 206 errors,
  representation change, stale cache, and server policy failures.

## Out of scope

- Operating an HTTP range server, claiming the OGC HTTP Range server class, generic URL opening, and
  bypassing G19-050 authority/credential policy.

## Acceptance criteria

- Viewport reads fetch only prospectively bounded metadata and required overview/tile ranges.
- Bytes from different entity versions are never combined into one parsed dataset.
- COG validation names exact passed/failed classes without treating every readable GeoTIFF as COG.

## Required tests

- Scripted range server covering HEAD/no-HEAD, 200/206/416, content ranges, strong/weak validators,
  representation changes, multipart policy, redirects/auth, cache/revalidation/retry, coalescing,
  request/byte ceilings, cancellation, and every OGC COG file test.

## Validation

Run `./gradlew :modules:mundane-map-io-geotiff:check --console=plain`, HTTP/COG evidence lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact COG reader conformance statement and observed
range-request evidence before completion.
