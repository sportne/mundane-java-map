# G11-001 — Editing, undo, and snapping model

Status: Proposed
Depends on: G8-004
Gate: G11
Type: HITL

## Goal

Define a bounded editing model with immutable commands, transaction ownership, undo/redo, and explicit
snapping behavior before mutable workflows are added to the map library.

## Context

Level 1 public values are immutable and interaction tools have explicit lifecycle. Editing must preserve
those properties instead of making geometries or layers mutable in place.

## Scope

Decide editable target ownership, command/result contracts, transaction and validation boundaries,
history ownership/limits, undo/redo grouping, failure and conflict behavior, selection integration, and
snapping targets, priority, pixel/map tolerance, tie-breaking, and CRS handling. Define the smallest
observable first edit slice and decompose create/move/delete, history, and snapping work as needed.

## Out of scope

Production editing APIs, collaborative editing, topology engines, persistence, format write-back,
unbounded history, hidden mutation of source features, and coupling public APIs to JTS.

## Acceptance criteria

- A maintainer approves who owns edit state, when immutable replacement values are produced, and how
  transactions succeed, fail, or roll back.
- Undo/redo grouping, bounded history eviction, command failure, and listener/event ordering are
  deterministic and documented.
- Snapping has explicit target types, tolerance units, priority/tie policy, cancellation, and behavior
  for unsupported or mismatched CRS data.
- Follow-up tasks each deliver a runnable edit behavior with tests and preserve compatibility with
  read-only sources; no empty editing module is created.

## Required tests

No production tests. Define later state-transition, transaction rollback, bounded-history,
selection/tool integration, snapping boundary/tie, and immutable-value tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer approves ownership, transaction/history semantics, snapping policy, and
the first vertical slice before public API implementation.
