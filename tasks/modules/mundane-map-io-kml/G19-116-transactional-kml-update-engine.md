# G19-116 — Transactional KML Update engine

Status: Proposed
Depends on: G19-110, G19-115
Gate: G19
Type: AFK

## Goal

Apply standard KML `Create`, `Change`, and `Delete` operations transactionally to an explicitly loaded
document representation with stable identity and generation isolation.

## Context

KML Update changes the earth-browser representation of a previously retrieved document; it does not modify
the remote resource. The module currently rejects all update content.

## Scope

- Model/validate Update, targetHref, ordered Create/Change/Delete options, targetId/id assertions, extension points,
  object-type/content restrictions, partial Change semantics, insertion placement, deletion, and multiple operations.
- Resolve targets only within an explicitly registered loaded-document identity and matching accepted generation.
- Preflight IDs, target types, duplicates, references, style/resource graphs, resulting object/feature/coordinate/
  resource/owned-byte totals, and operation work; build an immutable candidate before atomic swap.
- Define conflict/stale/missing/ambiguous/cyclic/invalid-target and listener/publication failure behavior with stable diagnostics.
- Keep bounded history only when explicitly configured for rollback/inspection; cancel active interactions/tours/link work as required.

## Out of scope

- Modifying remote/local source files, sending updates, collaborative editing, merge/conflict resolution, and unbounded undo.

## Acceptance criteria

- Valid ordered updates produce the exact immutable candidate and publish atomically at one new generation.
- Failure leaves the prior document/scene/resources active and cannot leak staged state or mutate remote data.
- Stale/ambiguous/over-limit operations are deterministically rejected before callbacks/publication.

## Required tests

- Create/Change/Delete/order/partial-change/type/ID/targetHref/extension/reference/resource matrices.
- Stale/concurrent generations, duplicate/missing/cyclic targets, resulting-size limits, callback failures, rollback/cleanup.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

None.
