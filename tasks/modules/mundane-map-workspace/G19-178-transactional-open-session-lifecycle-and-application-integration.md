# G19-178 — Transactional open, session lifecycle, and application integration

Status: Proposed
Depends on: G19-177
Gate: G19
Type: AFK

## Goal

Open, replace, cancel, and close complete standalone/packaged workspaces atomically with exact resource ownership
and integration across toolkit-neutral, AWT, and Vaadin applications.

## Context

Parsing a document or archive is not enough. Sources, catalogs, remote authorization, editable resources and tool
state open on separate lanes and must never leak or partially replace the active project.

## Scope

- Build a phased plan/preflight/authorize/verify/open/assemble/install transaction for XML/package resources,
  registries, local/remote access, source lanes, catalogs, edit sessions and idle tool state.
- Define one session owner, borrowed/owned relationships, reverse dependency close order, generation invalidation,
  cancellation checkpoints, superseding open, application shutdown and failure aggregation.
- Keep current workspace live until the replacement is fully accepted; on failure close every candidate and preserve
  prior state/selection/tool/resource registrations exactly.
- Integrate examples, AWT and Vaadin upload/open/save/download/detach/session/application lifecycle with bounded progress,
  stable diagnostics and no toolkit/private protocol in workspace public values.
- Verify packaged temporary resources remain owned/alive exactly through their source sessions and are removed after last use.

## Out of scope

- UI framework APIs in the workspace module, background network authority, live collaboration and persisting runtime objects.

## Acceptance criteria

- Successful open installs the complete project once; failed/cancelled/superseded open leaves the old project unchanged.
- Every candidate/source/catalog/temp/lock/thread/session closes exactly once in dependency order across all lifecycles.
- AWT and Vaadin restore equivalent committed state while maintaining their own transient interaction/render state.

## Required tests

- XML/package/local/remote/embedded mixed open, replacement, supersession, cancellation-at-phase, active edit/tool/selection,
  source failure, missing adapter/resource, and atomic old/new state matrices.
- Throwing open/close/listener/registrar/verifier/dispatcher, ownership claims, thread/lane races, detach/reattach, session/
  application shutdown, temporary cleanup, AWT/Vaadin integration and long repeated open/save/close soak tests.

## Validation

Run workspace lifecycle plus AWT/Vaadin/example checks and soak evidence, then qualityGate and `git diff --check`.

## Notes

None.
