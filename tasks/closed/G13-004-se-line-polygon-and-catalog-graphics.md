# G13-004 — SE line, polygon, and catalog graphics

Status: Complete
Depends on: G13-003
Gate: G13
Type: AFK

## Goal

Complete the approved vector SE profile with line and polygon symbolizers plus explicitly resolved
catalog graphics.

## Context

G13-003 supplies ordered conditional evaluation. Existing solid line/fill symbols, composites, named
catalogs, SVG-imported markers, and renderer preflight provide the target behavior.

## Scope

Implement approved solid stroke/fill/outline properties, atomic polygon fill-plus-outline ordering,
geometry-role validation, point `ExternalGraphic` exact-key resolution through a caller catalog,
recursive renderer preflight, and mixed point/line/polygon rule rendering.

## Out of scope

Network/file dereferencing, automatic SVG/image import, graphic strokes/fills, unrepresentable dash
patterns, joins, caps, offsets, gradients, text/raster symbolizers, arbitrary geometry properties,
alternative-graphic fallback, or hidden catalog fallback.

## Acceptance criteria

- Supported line/polygon properties map exactly to ordinary symbols and preserve rule/symbolizer order.
- External graphics resolve only from the supplied immutable catalog and validate marker role.
- Missing resources, wrong roles, unsupported strokes/fills, and excessive composition fail stably.
- Mixed geometry bindings paint and hit-test only compatible symbol roles without toolkit leakage.

## Required tests

Property/default matrices, line/polygon holes and multipart rendering, role mismatch, catalog missing/
duplicate/wrong-role cases, composition limits/order, renderer preflight, and tolerant regression.

## Validation

```bash
./gradlew :modules:mundane-map-io-se:check :modules:mundane-map-awt:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

`OnlineResource` values are treated only as approved exact catalog keys; this task adds no resolver
callback or I/O authority.
