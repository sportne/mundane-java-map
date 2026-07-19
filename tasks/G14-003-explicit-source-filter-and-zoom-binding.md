# G14-003 — Explicit source, filter, and zoom binding

Status: Proposed
Depends on: G14-002
Gate: G14
Type: AFK

## Goal

Bind style layer source IDs transactionally to caller-owned feature sources and apply layer filters
and zoom ranges with deterministic ordering.

## Context

G14-002 provides literal vector layers. The first profile treats source descriptions as detached
metadata; only an explicit application registry grants access to actual `FeatureSource` instances.

## Scope

Implement immutable source registry/bind context, all-or-nothing preflight, approved legacy/filter
syntax or expression-form filters fixed by G14-001, min/max zoom, Web Mercator zoom derivation,
required-attribute projection, ordered map-binding construction, and ownership/cleanup tests.

## Out of scope

Opening source URLs/files, inline GeoJSON, tile/vector-tile sources, `source-layer`, retries/caches,
implicit registry lookup, feature-state, non-Web-Mercator zoom inference, or mutable rebinding.

## Acceptance criteria

- Every layer source resolves by exact key before any binding is published.
- Filter missing/null/type behavior and min-inclusive/max-exclusive zoom boundaries are exact.
- Zoom-dependent styles attach only to supported Web Mercator context; literal styles remain usable
  with otherwise compatible CRSs.
- Layer/source order, feature query projection, ownership, close, and failure rollback are stable.
- Paint/hit/hover/select evaluate one captured style result consistently.

## Required tests

Registry missing/duplicate/source-order cases, transactional rollback/close, filter truth/type tables,
zoom boundaries/CRS rejection, attribute projection, source rendering, interaction agreement, and
architecture tests.

## Validation

```bash
./gradlew :modules:mundane-map-io-maplibre-style-jackson:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

The style adapter borrows sources during preflight; resulting ordinary map bindings retain the
existing explicit ownership model.
