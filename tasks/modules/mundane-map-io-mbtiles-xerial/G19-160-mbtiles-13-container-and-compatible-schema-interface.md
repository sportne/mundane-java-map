# G19-160 — MBTiles 1.3 container and compatible schema interface

Status: Proposed
Depends on: G19-014
Gate: G19
Type: HITL

## Goal

Complete bounded read-only MBTiles 1.3 container and logical-schema interoperability for both tables
and compatible views.

## Context

MBTiles specifies retrievable SQLite interfaces, not one physical schema. The current adapter rejects
valid view-backed producers and therefore cannot make a complete MBTiles 1.3 reader claim.

## Scope

- Pin all applicable MBTiles 1.3 SQLite/core-only, UTF-8, magic/application-ID, one-tileset, global-
  mercator, TMS-row, logical column, type, index, and integrity requirements.
- Validate `metadata` and `tiles` as safe tables or views yielding the exact logical interfaces;
  classify physical layouts without reverse-engineering writable behavior.
- Open immutable/read sessions with fixed prepared queries, query-only/authorizer/progress controls,
  exact connection ownership, cancellation, mutation detection, close, and stable diagnostics.
- Integrate stored TMS coordinates with the neutral tile-matrix model and bound schema objects/SQL,
  pages/bytes, rows, coordinates/zooms, query output, owned memory, and work.

## Out of scope

- Metadata semantics, payload decoding, write inference, arbitrary SQL, SQLite extensions, remote files,
  encryption, and accepting non-global-mercator content as MBTiles 1.3.

## Acceptance criteria

- Spec-compatible table- and view-backed databases expose identical logical tiles without mutation or
  executing an unapproved extension.
- Invalid/ambiguous/side-effecting schemas and domain/limit violations fail before content publication.
- The public schema classification states whether a later card may edit directly or requires rewrite.

## Required tests

- Flat/view/recognized-normalized column/type/order/index/application-ID/TMS/zoom/sparse/empty matrices
  and independent producer databases.
- Malicious views/triggers/functions/extensions, schema churn, mutation canaries, SQL/identifier tricks,
  progress cancellation, page/row/byte/work boundaries, thread/close races, and corrupt SQLite files.

## Validation

Run the MBTiles module check and container corpus, then `./gradlew qualityGate --console=plain` and
`git diff --check`.

## Notes

HITL checkpoint: approve the exact MBTiles 1.3 requirement map, SQLite authorizer profile, recognized
layout inventory, and independent corpus before implementation.
