# G19-224 — OGC API Tiles conformance and lifecycle closeout

Status: Proposed
Depends on: G19-223
Gate: G19
Type: HITL

## Goal

Close the declared full guarded read-only OGC API Tiles client with conformance, interoperability,
security, bounded-work, lifecycle, and documentation evidence.

## Context

Model, discovery, selection, and retrieval cards can pass separately while disagreeing about declared
conformance, representation handling, caches, generations, diagnostics, or cleanup. The closeout
proves the client as one coherent source/protocol adapter without implying a server.

## Scope

- Run and record applicable OGC API Tiles Part 1, Common Part 1, TMS 2.0, JSON/XML metadata, Dataset
  Tilesets, GeoData Tilesets, Collections Selection, DateTime, and declared encoding test suites.
- Reconcile caller-supplied documents, guarded discovery, raw tiles, raster sources, vector-adapter
  handoff, attribution/metadata, caching, cancellation, and diagnostics across all public entry points.
- Verify exact limits for graph traversal, links, documents, matrices, collections/time selections,
  requests, redirects/retries, bytes, retained raw results, decoded rasters, caches, and concurrent work.
- Verify capability-generation invalidation when any linked resource, conformance declaration,
  validator, selection, representation, matrix definition, credentials, or source lifecycle changes.
- Publish the planned module's local capability matrix and align package/root documentation with the
  complete read-only discovery/raw-tile boundary and deliberate exclusions.
- Obtain independent OGC API Tiles expert review and cross-provider evidence.

## Out of scope

- OGC API server/authoring behavior, OpenAPI client generation, HTML scraping, reprojection, tile
  production, implicit decoder discovery, or claiming conformance classes not exercised.

## Acceptance criteria

- Every claimed conformance class has reproducible evidence and every unsupported optional class is
  named without weakening the core read-client claim.
- Cross-provider discovery, selection, raw retrieval, registered raster construction, cancellation,
  and close behave consistently without stale generations, leaks, or hidden network work.
- An external reviewer records no untracked gap in the declared read-only OGC API Tiles profile.

## Required tests

- Applicable OGC conformance suites, official examples, at least two independent provider fixtures,
  JSON/XML parity, raw/raster/vector-handoff, and public capability-matrix tests.
- Mutating linked graph/validator/conformance, stale selection, cache corruption, cancellation at every
  stage, concurrent replace/close, resource/failure aggregation, bounded soak, and architecture tests.

## Validation

Run the OGC API Tiles and dependent module checks, approved OGC conformance/network/corpus lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer and independent OGC API Tiles reviewer approve the conformance matrix,
provider evidence, lifecycle/security report, exclusions, and final support wording.
