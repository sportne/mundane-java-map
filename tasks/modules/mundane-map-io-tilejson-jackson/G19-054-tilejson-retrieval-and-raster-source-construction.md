# G19-054 — TileJSON retrieval and raster-source construction

Status: Proposed
Depends on: G19-051, G19-052, G19-053
Gate: G19
Type: HITL

## Goal

Retrieve approved TileJSON metadata and explicitly construct a bounded HTTP raster tile source from
its versioned model.

## Context

Parsing alone does not provide discovery. Network retrieval, endpoint authorization, multiple tile
templates, XYZ/TMS scheme mapping, metadata caching, format selection, bounds, attribution, and
source ownership must share the hardened HTTP policy without allowing arbitrary link traversal.

## Scope

- Add explicit entry points for caller-supplied TileJSON bytes/streams and for one caller-authorized
  metadata URI fetched through the G19 HTTP policy/cache.
- Resolve absolute tile endpoints only through the immutable scheme/authority allowlist; define
  same-origin defaults and require explicit opt-in for every additional authority.
- Construct the approved direct raster tile matrix from `scheme`, zooms, bounds, fill zoom, endpoint
  templates, and registered raster decoders without guessing unsupported metadata.
- Define deterministic bounded selection/failover among equivalent `tiles` endpoints, content type,
  signature, dimensions, cache identity, attribution-as-data, and source metadata exposure.
- Retain but do not fetch `data`/`grids`, decode vector tiles, or evaluate attribution/template HTML.
- Aggregate metadata/tile requests, bytes, redirects, retries, endpoints, fan-out, caches, workers,
  cancellation, and close ownership under one source lifecycle.

## Out of scope

- TileJSON authoring/serving, internet-wide discovery, vector tile or UTFGrid decoding, GeoJSON
  overlay acquisition, trusted HTML rendering, and implicit credentials/authorities.

## Acceptance criteria

- Caller-supplied and guarded-network TileJSON paths construct equivalent raster requests and
  envelopes for each approved version/scheme.
- Unauthorized endpoints, unsupported media/profile metadata, and conflicting bounds/zooms fail
  before tile work begins with stable diagnostics.
- Metadata/tile cache, retry, cancellation, replacement, and close paths leak no bodies, files,
  workers, registrations, credentials, or partially published raster state.

## Required tests

- Official/cross-provider TileJSON metadata over bytes and scripted HTTP; XYZ/TMS, multiple endpoints,
  zoom/bounds/fillzoom, attribution, registered image media, cache, retry, and source-query fixtures.
- Cross-origin/link-policy, endpoint/template injection, unsupported vector/grid/data behavior,
  oversized metadata/tile, stale metadata, cancellation, concurrent close, cleanup, and secret-
  redaction tests.

## Validation

Run the TileJSON and HTTP modules' checks, scripted protocol/corpus lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the network/link/failover policy, raster-only construction
boundary, interoperability evidence, and lifecycle/security review before completion.
