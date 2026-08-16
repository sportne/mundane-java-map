# G19-221 — OGC API guarded discovery and link traversal

Status: Proposed
Depends on: G19-051, G19-220
Gate: G19
Type: HITL

## Goal

Discover OGC API Tiles resources from one authorized landing page through bounded typed link
traversal under the hardened HTTP policy.

## Context

The approved client starts from a landing page rather than requiring callers to assemble every
resource URL. OGC API links are data supplied by the service; following every link or relation would
create an SSRF, fan-out, loop, credential, and retained-state surface.

## Scope

- Fetch an explicitly authorized landing page and traverse only the pinned `service-desc`,
  `conformance`, `data`, collection, tilesets, tileset, and tile-matrix-set relations required by the
  selected OGC API Tiles conformance path.
- Validate declared conformance before using optional Dataset Tilesets, GeoData Tilesets, Collections
  Selection, DateTime, XML metadata, or encoding behaviors.
- Apply exact relation/media/profile preference and resolve relative links through URI normalization,
  same-origin defaults, and explicit additional-authority allowlists.
- Detect cycles, aliases, duplicate/conflicting resources, changed validators, inconsistent versions,
  stale generations, and cross-document identifier/reference mismatches.
- Bound traversal depth, links visited/fetched, redirects, requests, retries, bytes, documents,
  collections, tilesets, aliases, retained metadata, cache entries, wall time, and concurrent work.
- Return one detached immutable discovery snapshot; no parser/model object retains response bodies,
  credentials, mutable caches, or an unrestricted transport handle.

## Out of scope

- Internet-wide crawling, untyped link following, HTML scraping, OpenAPI-driven client generation,
  ambient credentials/authorities, tile requests, and server behavior.

## Acceptance criteria

- Official and cross-provider landing pages reach the same expected resources independent of JSON
  member/link ordering and without following unrelated relations.
- Unauthorized/cyclic/excessive/inconsistent links fail atomically before tile work and never forward
  secrets outside the approved authority scope.
- Cancellation, cache/retry failure, replacement, and close release all bodies, workers, snapshots,
  registrations, and temporary state.

## Required tests

- Scripted landing/conformance/collection/tileset/TMS graphs with relative/absolute links,
  representation negotiation, aliases, validators, cache, retry, redirects, and multiple authorities.
- SSRF, cross-origin secret leakage, cycles, excessive graphs, relation/media confusion, stale/mutated
  resources, hostile redirects, cancellation, concurrent close, cleanup, and diagnostics tests.

## Validation

Run the OGC API Tiles and HTTP checks plus scripted discovery/corpus lanes, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer/security reviewer approves link relations, traversal/authority limits,
representation negotiation, snapshot lifecycle, and cross-provider discovery evidence.
