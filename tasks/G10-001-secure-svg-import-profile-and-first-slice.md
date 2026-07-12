# G10-001 — Secure SVG import profile and first slice

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Approve a deliberately small static SVG profile and import it into the Level 1 toolkit-neutral vector
symbol model through one secure, rendered vertical slice.

## Context

Level 1 supports vector paths and explicit symbol registration, but arbitrary SVG is too broad and
unsafe for this library. JDK XML parsing is sufficient for a bounded first profile if external
resolution and expansion are disabled.

## Scope

Before implementation, record and approve exact limits for input bytes, elements, nesting, attributes,
path commands/segments, and transform depth. Then create `mundane-map-io-svg` only with working AWT-free
import and tests for local static SVG using `viewBox`, basic shapes, affine transforms, representable
move/line/quadratic/cubic/close path commands, fill, stroke, and opacity. Render an imported symbol via
explicit registration and the existing Java2D path.

## Out of scope

Scripts, animation, CSS, text, filters, masks, clipping, external references, network access, embedded
raster data, data URLs, DTDs/entities, arbitrary SVG conformance, export, and silent fallback for
unsupported content.

## Acceptance criteria

- A maintainer approves the supported subset, diagnostic policy, and conservative numeric/resource
  limits before implementation begins.
- Secure parser configuration rejects DTDs, entities, external resources, oversized/deep documents,
  non-finite coordinates, unsupported elements/attributes, and limit overruns with stable diagnostics.
- A supported local SVG imports to immutable toolkit-neutral symbols and renders through explicit
  renderer registration; no DOM or XML type leaks into `mundane-map-api`.
- The new module is added only with working parse-to-render behavior, tests, Javadocs, and architecture
  coverage.
- Unsupported SVG features remain explicit Level 2 backlog candidates and receive separate tasks only
  after demonstrated demand.

## Required tests

Parser and transform/path tests; XXE, entity-expansion, external-reference, depth, count, size, and
numeric-limit tests; parse-to-render integration and tolerant rendering regression tests; architecture
tests for AWT and dependency boundaries.

## Validation

```bash
./gradlew :modules:mundane-map-io-svg:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer must approve the profile table, limit defaults, and reject-versus-warn
behavior before code or module creation. General SVG support is not a goal.

