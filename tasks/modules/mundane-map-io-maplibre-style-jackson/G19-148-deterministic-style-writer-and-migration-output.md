# G19-148 — Deterministic style writer and migration output

Status: Proposed
Depends on: G19-140, G19-141, G19-142, G19-143, G19-144, G19-145, G19-146, G19-147
Gate: G19
Type: AFK

## Goal

Write complete deterministic v26.2.1 style documents, including losslessly preserved non-renderable 3D state.

## Context

The adapter is read-only. Generated/edited styles need reproducible output, safe migration from deprecated
syntax, complete document fidelity, exact representability checks, and atomic file behavior.

## Scope

- Serialize every pinned root/source/layer/property/expression/resource/extension value in frozen member order.
- Emit stable UTF-8, escaping, finite-number/color/value formatting, source/layer order, and no insignificant whitespace.
- Normalize losslessly migrated legacy filters/functions to current expressions and retain migration/audit observations.
- Preserve and write valid terrain/extrusion/sky/projection/video declarations even when the 2D binder cannot render them.
- Preflight types/references/expressions/resources/extensions/representability and exact byte/work limits.
- Add bounded stream output, atomic filesystem replacement, cancellation, committed-byte semantics and cleanup aggregation.

## Out of scope

- Legacy syntax output, RFC 8785 claims, source lexical formatting preservation, and in-place mutation.

## Acceptance criteria

- Identical semantic style/options produce byte-identical output accepted by the pinned reference validator.
- Complete read/write/read round trips preserve every supported or document-only construct semantically.
- Lossy migration and validation/limit/sink/cleanup failures obey the documented atomic/commit boundary.

## Required tests

- Full generated writer/member-order/value/expression/resource/non-renderable/extension matrix.
- Legacy migration, byte reproducibility, reference validation, atomic files, short/failing sinks, limits, and cancellation.

## Validation

Run module/reference/writer interoperability checks, qualityGate, and `git diff --check`.

## Notes

None.
