# G19-174 — Workspace v1 migration, extensions, and downgrade

Status: Proposed
Depends on: G19-173
Gate: G19
Type: HITL

## Goal

Add deterministic v1-to-v2 migration, bounded optional extension preservation/codecs, and explicit loss-audited downgrade.

## Context

A new native major version must preserve existing projects and future optional content without turning the
closed grammar into permissive XML or silently discarding state.

## Scope

- Map every v1 field/default/omission/warning to v2 and produce a deterministic migration report and canonical result.
- Implement major/minor negotiation: reject unknown required core content and preserve bounded namespace-aware
  optional elements/attributes as an immutable infoset with canonical semantic output.
- Add direct typed extension codec registration with namespace/name/version, placement, collision, cost, failure,
  lifecycle and no-authority rules; never discover codecs or expose StAX nodes.
- Define v2-to-v1/future downgrade planning with exact loss/transformation reports and caller accept/reject policy.
- Bound migration/extension/downgrade nodes, depth, namespaces, text, codecs, output bytes and aggregate work.

## Out of scope

- Heuristic repair, arbitrary XML round-trip, lexical formatting preservation, executable extensions, automatic
  downgrade, OGC/QGIS conversion and silent loss.

## Acceptance criteria

- Every released v1 fixture migrates to one deterministic semantically correct v2 document.
- Unknown optional extensions survive semantic read/write while unknown required content fails closed.
- Downgrade changes nothing until the caller explicitly accepts a complete stable loss report.

## Required tests

- All historical v1 forms/defaults/warnings, v2 minor/major negotiation, optional/required extension placement,
  infoset namespace/order/value and typed codec matrices.
- Extension bombs/cycles/collisions/authority attempts/codec failures, malformed migrations, downgrade losses,
  deterministic output, limits, cancellation and cross-release golden corpus.

## Validation

Run workspace migration/extension corpus checks, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve the complete v1 mapping, extension infoset/codec contract, version negotiation and
downgrade loss policy before v2 is treated as stable.
