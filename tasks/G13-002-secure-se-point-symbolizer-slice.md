# G13-002 — Secure SE point-symbolizer slice

Status: Proposed
Depends on: G13-001
Gate: G13
Type: AFK

## Goal

Create a working JDK-only OGC SE module that securely reads one literal point-symbolizer style and
renders its well-known marker through the real map stack.

## Context

G13-001 fixes the grammar and shared portrayal bridge. OGC SE permits `FeatureTypeStyle` as a root;
this slice proves parse-to-ordinary-symbol behavior before adding conditional rule complexity.

## Scope

Create `mundane-map-io-se`; implement bounded UTF-8 Path/byte input, hardened direct StAX parsing,
metadata and one unconditional rule, approved `Mark`/`WellKnownName` fill/stroke/size/rotation/
placement mapping, immutable output, diagnostics, Javadocs, architecture/publication inventory, and
one runnable point example.

## Out of scope

Filters, scale ranges, ElseFilter, line/polygon symbolizers, ExternalGraphic, text/raster/coverage,
SLD, URLs, schema validation/download, DOM, or arbitrary functions.

## Acceptance criteria

- A namespace-correct bounded SE 1.1 style produces the expected ordinary marker and map rendering.
- DTD/entity/XInclude/external access, wrong roots/namespaces/versions, and unsupported elements fail
  with stable diagnostics before partial publication.
- Input ownership, cancellation, limits, and cleanup follow existing format-module conventions.
- The new module is AWT-free, JDK-only, published only with working behavior, and explicitly listed.

## Required tests

Secure XML controls, namespace/version/root grammar, mark properties and defaults, path/byte parity,
limits/cancellation/cleanup, map rendering, Javadocs, architecture, and publication metadata.

## Validation

```bash
./gradlew :modules:mundane-map-io-se:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Runtime XSD validation is not introduced; tests prove the exact approved grammar directly.
