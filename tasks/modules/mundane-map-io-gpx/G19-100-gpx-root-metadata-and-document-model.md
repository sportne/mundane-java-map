# G19-100 — GPX root, metadata, and document model

Status: Proposed
Depends on: G19-001
Gate: G19
Type: AFK

## Goal

Create a complete immutable GPX 1.1 root/metadata domain model and securely parse every standard root and
metadata field without silent loss.

## Context

The released reader validates much metadata structurally but discards most values, and its primary public
surface is a flattened feature source rather than a complete GPX document.

## Scope

- Pin the GPX 1.1 namespace/schema, required version/creator, root order/cardinality, XML datatypes, WGS 84,
  metric-unit, and hardened XML processing contract.
- Add immutable root, metadata, person, email, copyright, link, and bounds values retaining all standard
  fields, optionality, repeated links, date-time offsets, and document order.
- Expose secure bounded open-to-document APIs alongside the existing feature-source facade with explicit
  ownership, cancellation, diagnostics, and close behavior.
- Validate latitude/longitude, bounds, year, URI, time, text, identifier, and creator/version value spaces.
- Bound bytes, XML events/elements/depth/namespaces/attributes/text, metadata entries, links, owned memory,
  and diagnostics prospectively; keep DTD/entities/XInclude/schema/network/file lookup disabled.

## Out of scope

- Waypoint details, routes/tracks, extension content, writing, and GPX 1.0.

## Acceptance criteria

- Every standard root/metadata field maps to a typed immutable value without warnings or string-map loss.
- Wrong namespace/version/order/cardinality/value or exceeded limits fail before a document/source is returned.
- Opening performs no ambient I/O and cancellation/close releases file/parser state deterministically.

## Required tests

- Root/version/creator/order and complete metadata/person/email/copyright/link/time/keywords/bounds matrices.
- XML datatype boundaries, XXE/XInclude/schema/encoding/truncation, every limit edge, cancellation, and cleanup.

## Validation

Run `./gradlew :modules:mundane-map-io-gpx:check --console=plain`, XML corpus tests,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

None.
