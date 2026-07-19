# G11-042 — SVG export hardening

Status: Proposed
Depends on: G11-041
Gate: G11
Type: AFK

## Goal

Make detached capture and SVG serialization predictably bounded and failure-atomic under hostile
values, cancellation, accounting limits, and injected filesystem faults.

## Context

G11-041 completes supported behavior. G11-005 defines exact snapshot/writer inventories, prospective
limit precedence, cancellation checkpoints, stable diagnostic contexts, deterministic sink chunks,
and atomic-file cleanup.

## Scope

Complete snapshot and writer limit/accounting enforcement in API/AWT/core/SVG; implement every exact
diagnostic and cancellation checkpoint; harden finite transforms, geometry, labels, symbol traversal,
hatch counting, encoded output, temporary files, force/close/move/delete, and suppression ordering.

## Out of scope

New supported effects, warning-only fallback, lossy number rounding, partial documents, non-atomic
replacement, retained export plans, external renderers, or performance-native libraries.

## Acceptance criteria

- Every snapshot and writer inventory accepts equality and rejects plus one prospectively with the
  exact stable code/context and no published partial value or touched target.
- Checked overflow, non-finite/invalid geometry/transform/label data, unsupported symbols, and hatch
  per-symbol versus writer limits follow the approved deterministic precedence.
- Cancellation at every capture, two-pass encode, chunk/final-array, and file checkpoint closes each
  resource once, preserves the existing target, and leaves no temporary file.
- Injected preflight/create/write/force/close/move/delete failures preserve the first failure and
  suppress cleanup failures in approved order.

## Required tests

Exact/one-over semantic inventories, overflow and fixed numeric-token reservation, sink chunking,
hatch candidate/zero-segment behavior, every stable problem shape, all cancellation polls, hostile
values, injected file failures, cleanup, and target preservation.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check :modules:mundane-map-io-svg:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Writer and snapshot accounting are deterministic logical inventories, not heap estimates. Do not add
an element plan, DOM, third traversal, or retained hatch layout to simplify tests.
