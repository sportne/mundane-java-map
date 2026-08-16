# G19-021 — Swing accessibility and deterministic print output

Status: Proposed
Depends on: G19-020
Gate: G19
Type: HITL

## Goal

Define and verify an accessible Swing map component surface plus deterministic pageable/print export.

## Context

Keyboard routing alone does not expose map state, selections, tools, coordinates, or feature actions
to assistive technology, and screen rendering is not a reviewed printing contract.

## Scope

- Implement an `AccessibleContext` role, names, descriptions, state, actions, and change events.
- Define focus traversal, keyboard-only inspection, selection, tool, and cancellation behavior.
- Add bounded `Printable`/`Pageable` rendering with explicit scale, margins, DPI, and page tiling.
- Keep print rendering deterministic and independent of live component timing.
- Document platform support and accessible customization APIs.

## Out of scope

- Claiming conformance for an embedding application that fails to label or operate the component.

## Acceptance criteria

- A frozen assistive-technology matrix is manually and automatically evidenced.
- All essential map actions are keyboard operable with visible focus and stable announcements.
- Print output has deterministic pagination and matches screen portrayal under stated differences.

## Required tests

- Accessibility tree/event/action tests and keyboard workflow tests.
- Multi-page, high-DPI, cancellation, and print-limit golden tests.
- Human verification on the supported OS/JDK matrix.

## Validation

Run `./gradlew :modules:mundane-map-awt:check --console=plain`, the rendering lane, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the pinned profile, external evidence, and any licensed corpus or manual review named by this card before completion.
