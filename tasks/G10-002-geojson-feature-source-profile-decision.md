# G10-002 — GeoJSON feature-source profile decision

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Approve a bounded RFC 7946 feature-source profile and one isolated Jackson Core adapter strategy,
then decompose the working vertical slices without creating a module in this task.

## Context

Level 1 supplies format-neutral sources, packed multipart geometry, canonical attributes, CRS
normalization, limits, and diagnostics. GeoJSON adds recursive JSON, foreign members, fixed CRS84
longitude/latitude tuples, null geometry, and an optional external parser boundary.

## Scope

Complete **G10 GeoJSON profile approval** for strict UTF-8 RFC 7946 FeatureCollection, Feature, and six
2D geometry roots; null-geometry recovery; flat scalar properties; typed IDs; `bbox`; foreign members;
CRS84-to-EPSG:4326 normalization; bounded byte snapshot/index/cursor behavior; diagnostics and exact
limits. Approve one `mundane-map-io-geojson-jackson` optional adapter using pinned Jackson Core with no
external type leakage, non-recycling/direct parser construction, and a complete shaded/service-content
audit; record G10-020 through G10-024 for creation only after approval.

## Out of scope

Production parsing or module creation; GeometryCollection, empty or Z/M geometry, recursive property
values, nonstandard/legacy CRS, JSON sequences/lines, remote retrieval, writing/export, alternate
parallel parsers, and Jackson types in public contracts.

## Acceptance criteria

- **G10 GeoJSON profile approval** records the exact document/member/geometry/property/ID/bbox/foreign
  matrix, null recovery, coordinate and CRS normalization, parser/source ownership, limits,
  diagnostics, and deterministic precedence.
- The approved source design preserves record order, packed immutable values, one-cursor query
  semantics, exact feature count/extent, dynamic attributes, stable IDs, bounded warnings, and no
  input mutation or renderer/parser-type leakage.
- The dependency record pins Jackson Core 3.1.5 and its checksum, license/provenance/runtime and shaded
  contents, exact strict factory controls and direct construction, explains the substantial tokenizer
  benefit, isolates it as an Optional adapter, and leaves Native Image unclaimed until executable
  evidence.
- G10-020 through G10-024 are ordered one-to-five-day slices covering the first working module,
  geometry completion/rendering, hostile hardening, fixture/viewer evidence, and Native Image; none is
  created as an empty scaffold.

## Required tests

No production tests. Review representative RFC examples and negative/limit/cancellation fixture
tables, dependency/license evidence, parser-boundary compile sketches, and the follow-up graph's
valid, malformed, rendering, fixture, publication/consumer, and native coverage.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GeoJSON profile approval**. The selected strategy is one isolated Jackson Core
adapter; rejection reopens this design for one bounded JDK tokenizer rather than authorizing both.
This card does not create or register a module.
