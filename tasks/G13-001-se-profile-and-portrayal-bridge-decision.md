# G13-001 — SE profile and portrayal-bridge decision

Status: Proposed
Depends on: G12-006, G11-024
Gate: G13
Type: HITL

## Goal

Approve the exact OGC SE 1.1 `FeatureTypeStyle` subset and the smallest standards-neutral ordered
rule/scale/predicate bridge needed to apply it through existing map bindings.

## Context

The G13 design proposes secure JDK StAX, point/line/polygon symbolizers, bounded Filter 1.1
predicates, explicit scale context, and caller-catalog graphics. G11 intentionally stops short of a
general expression language, so any bridge extension requires explicit review.

## Scope

Review `design/G13-ogc-symbology-encoding.md`; freeze XML/root/namespace rules, symbolizer/filter/scale
matrices, scalar semantics, painter ordering, resource policy, limits, diagnostics, module surface,
fixture licensing, and the closed portrayal bridge shared later with MapLibre.

## Out of scope

Production code or module creation; SLD/WMS; CoverageStyle; Text/RasterSymbolizer; arbitrary OGC
functions; remote resources; schema downloads; callbacks; or a generic expression engine.

## Acceptance criteria

- Supported/rejected SE elements and attributes are exhaustive for the accepted roots.
- Rule matching, multiple matches, ElseFilter, scale bounds, missing/null attributes, and composition
  have exact deterministic semantics.
- The bridge is standard-neutral, immutable, closed, bounded, and reuses existing symbols/renderers.
- XML security, limits, resource lookup, diagnostic precedence, and fixture rights are explicit.
- G13-002 through G13-006 are actionable vertical slices with no empty module task.

## Required tests

No production tests. Review representative OGC examples, negative XML/security cases, portrayal
evaluation sketches, scale calculations, and the full dependency/module graph.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G13 OGC SE profile and shared portrayal-bridge approval**. Material changes to the
closed G11 portrayal model require maintainer approval here before implementation.
