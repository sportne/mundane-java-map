# G10-002 — GeoJSON feature-source profile decision

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Select a bounded RFC 7946 GeoJSON profile and parser-dependency strategy that can later become
reviewable `FeatureSource` vertical slices.

## Context

Level 1 supplies format-neutral feature sources, CRS diagnostics, parser-limit conventions, and
multipart geometry. GeoJSON has recursive values, foreign members, geometry collections, and a fixed
WGS 84 coordinate model that require an explicit support decision.

## Scope

Record decisions for Feature/FeatureCollection and geometry coverage, null geometry, properties and
numeric representation, `bbox`, foreign members, coordinate order/CRS behavior, streaming versus
materialization, duplicate keys, diagnostics, and byte/depth/member/coordinate/allocation limits.
Compare a JDK-only parser with an isolated optional JSON adapter and define Native Image implications.
Create the ordered implementation tasks after approval.

## Out of scope

Production parsing, creating `mundane-map-io-geojson`, nonstandard CRS members, GeoJSON Text Sequences,
writing/export, remote retrieval, and leaking a JSON library's node types into public APIs.

## Acceptance criteria

- A maintainer approves a profile matrix tied to RFC 7946 and records each supported, rejected, or
  deferred behavior.
- The decision documents immutable property mapping, stable diagnostics, explicit input limits, and
  WGS 84/axis-order handling.
- Dependency evaluation records complexity, maintenance, license, module isolation, and Native Image
  consequences; an external library is selected only if the benefit is substantial.
- Follow-up tasks are decomposed into one-to-five-day working slices and add no module before the first
  parser/source behavior and tests.

## Required tests

No production tests. Review proposed fixtures against representative RFC 7946 examples and verify the
follow-up dependency graph covers valid, malformed, limit, rendering, corpus, and native paths.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer chooses the supported profile and JDK-only versus isolated-adapter
strategy, then approves the decomposed implementation tasks. This card does not authorize a module.
