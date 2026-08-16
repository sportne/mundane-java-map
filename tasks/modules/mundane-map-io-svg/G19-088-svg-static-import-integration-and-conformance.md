# G19-088 — SVG static-import integration and conformance

Status: Proposed
Depends on: G19-083, G19-084, G19-085, G19-086, G19-087
Gate: G19
Type: HITL

## Goal

Integrate the complete declared static SVG import profile into one immutable toolkit-neutral scene
with closed conformance, rendering, security, diagnostic, and lifecycle evidence.

## Context

Feature slices can pass independently while document order, bounds, hit behavior, resource sharing,
and mixed rendering still diverge. SVG 2 permits restricted processing profiles, so the exact claim
must be explicit rather than calling the adapter a complete browser viewer.

## Scope

- Define the public immutable static-scene/portrayal model retaining groups, definitions, paint,
  markers, text, images, clips/masks, filters, metadata, exact order, and reusable resources.
- Integrate mixed feature rendering/hit/bounds behavior across AWT, browser, marker symbols, and
  export without toolkit types in the format module.
- Run applicable W3C static SVG/SVG 1.1 tests and independently rendered fixtures under the pinned
  restricted-profile statement.
- Add full-document hostile mutation, CSS/reference/resource/filter/text/image aggregate limits,
  cancellation, caching, and exact ownership/cleanup evidence.
- Reconcile stable diagnostic inventory and package/import Javadocs without broadening export claims.

## Out of scope

- Dynamic viewer/browser conformance and generated-map export completeness.

## Acceptance criteria

- Every capability-matrix import row is supported, deliberately excluded, or given standards-correct
  ignored behavior with matching tests/docs.
- Mixed documents produce deterministic cross-renderer pixels/bounds/hits within declared tolerances.
- All success/rejection/cancellation/failure paths release resource graphs and buffers exactly once.

## Required tests

- Applicable W3C/cross-renderer corpus, mixed feature documents, scene immutability/order/bounds/hits,
  hostile field/reference/resource mutations, aggregate-limit interactions, cancellation, concurrent
  ownership, native/publication compatibility, and documentation inventory.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, SVG corpus/rendering/native/
publication lanes, then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the restricted SVG processing/conformance statement,
cross-renderer evidence, corpus provenance/licenses, and stable exclusions before completion.
