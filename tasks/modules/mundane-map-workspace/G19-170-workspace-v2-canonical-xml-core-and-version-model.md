# G19-170 — Workspace v2 canonical XML core and version model

Status: Proposed
Depends on: G19-001, G19-002, G19-010, G19-014
Gate: G19
Type: HITL

## Goal

Define and implement the immutable canonical XML v2 document, versioning rules, limits, and complete
stored/derived/transient/forbidden state inventory.

## Context

V1 is a small strict XML grammar. Expanding it without first freezing a coherent domain and version
policy would make later migrations and package compatibility unstable.

## Scope

- Inventory every public map/workspace value and classify it as persisted, derived, transient, or forbidden.
- Define immutable v2 document identities, metadata, view/composition/resource/state sections, namespaces,
  major/minor compatibility, defaults, ordering, optionality and validation.
- Implement bounded hardened direct-JDK-StAX reading and byte-deterministic canonical UTF-8 XML writing.
- Freeze XML declaration/namespace/prefix/element/attribute/number/boolean/enum/URI/path/digest/whitespace/
  escaping rules and stable structural/version/limit diagnostics.
- Bound bytes, depth, elements/attributes, namespaces, text, values, references, owned memory and work.

## Out of scope

- OGC WMC/OWS Context, JSON workspace documents, package entries, migrations, extensions, live object
  serialization, credentials and private renderer/browser protocols.

## Acceptance criteria

- The complete public-state inventory has no unclassified value and v2 can represent every approved core section.
- Semantically equal documents write identical bytes and read/write is semantically idempotent.
- Forbidden XML/provider behavior and prospective limit failures cannot create a partial document.

## Required tests

- Full core/default/order/namespace/version/scalar/identity/reference skeleton and canonical-byte matrix.
- DTD/entity/PI/external access, malformed UTF-8/XML/Unicode, duplicate/unknown/ambiguous fields, deep/wide/
  long/overflow inputs, cancellation and writer sink/cleanup tests.

## Validation

Run workspace schema/reader/writer checks and corpus tests, then qualityGate and `git diff --check`.

## Notes

HITL checkpoint: approve the v2 state inventory, XML grammar/canonical form, version rules, limits and
explicit non-standard support wording before dependent state cards proceed.
