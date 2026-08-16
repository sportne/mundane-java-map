# G19-057 — WMTS KVP/REST tile source

Status: Proposed
Depends on: G19-044, G19-051, G19-052, G19-056
Gate: G19
Type: HITL

## Goal

Construct a bounded raster source that retrieves WMTS 1.0.0 tiles through both KVP and RESTful
resource bindings.

## Context

Capabilities parsing and selection become useful when exact requests, response validation, sparse
tiles, caching, retry, cancellation, and source lifecycle share the hardened HTTP transport. KVP and
REST templates have different parameter/escaping rules and need independent evidence.

## Scope

- Build exact WMTS 1.0.0 `GetTile` KVP requests and RESTful `ResourceURL` requests from an immutable
  selected plan, including service/request/version, layer, style, format, dimensions, matrix set,
  matrix, row, and column.
- Apply the G19 HTTP authority, credential, redirect, cache, retry, timeout, body, and lifecycle
  policies to capabilities and tile requests under one source owner.
- Resolve only advertised and caller-approved endpoints/bindings; define deterministic KVP/REST
  preference/fallback without treating a server error as permission to change profiles silently.
- Validate OWS exception reports, status/content type/signature, registered raster decoder,
  dimensions, matrix coverage, sparse/missing tiles, and atomic multi-tile publication.
- Aggregate requests, retries, redirects, bytes, decoded pixels, tiles, caches, fan-out, workers, and
  cleanup prospectively across the source/query.
- Expose bounded service attribution/metadata as data without rendering or evaluating markup.

## Out of scope

- SOAP, server behavior, vector tile decoding, reprojection, implicit endpoints, and GetFeatureInfo
  payloads, assigned to G19-058.

## Acceptance criteria

- Official and cross-vendor KVP/REST services produce exact requests, envelopes, pixels, and cache
  behavior for the selected layer/matrix/dimensions.
- Unauthorized links, service exceptions, wrong media/dimensions, unsupported formats, and excessive
  fan-out fail before partial scene publication with stable diagnostics.
- Cancellation, replacement, close, cache/retry failures, and partial batches leak no bodies, files,
  workers, credentials, source claims, or decoded state.

## Required tests

- Scripted KVP/REST capabilities and tile servers covering exact escaping/parameters, dimensions,
  styles, matrices/limits, sparse/missing tiles, formats, exceptions, validators, retry, and caching.
- Cross-authority/downgrade/template injection, wrong content/signature/dimensions, oversized/partial
  responses, cancellation, concurrent close, failure aggregation, and cross-vendor rendering tests.

## Validation

Run the WMTS, HTTP, image, and tile-matrix checks plus approved scripted protocol corpus, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves KVP/REST request and fallback semantics, service-exception
handling, security/lifecycle review, and cross-vendor raster evidence before completion.
