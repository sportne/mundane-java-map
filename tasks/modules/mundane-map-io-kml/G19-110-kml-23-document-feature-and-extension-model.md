# G19-110 — KML 2.3 document, feature, and extension model

Status: Proposed
Depends on: G19-001
Gate: G19
Type: HITL

## Goal

Establish a complete secure immutable KML 2.3 document/feature/data model and bounded extension registry.

## Context

The released parser flattens a KML 2.2 Placemark geometry subset and terminally rejects most feature,
metadata, data-schema, version, and extension constructs.

## Scope

- Pin OGC 12-007r2, namespace/version dispatch, applicable assertions/schemas, Atom/xAL subset, XML datatypes,
  extension points, and hardened StAX behavior.
- Model the root, `NetworkLinkControl`, common object/feature fields, Document, Folder, Placemark, IDs/target IDs,
  metadata, views/time/style/region references, Schema, ExtendedData, Data, SchemaData, and array/simple fields.
- Preserve foreign attributes/simple/object extensions as a bounded XML infoset and support immutable explicit
  QName codec registration without reflection, discovery, schema downloads, execution, or ambient I/O.
- Expose the document model beside feature-source projections with deterministic order, identity, ownership,
  cancellation, and diagnostics.
- Bound bytes/events/elements/depth/text/namespaces/attributes/objects/features/data/extensions/owned memory.

## Out of scope

- Detailed geometry, portrayal, resources, networking, updates, tours, and writing.

## Acceptance criteria

- Every approved common KML 2.3 object/feature/data field and extension point maps to immutable values.
- Wrong version/assertion/order/cardinality/reference and hostile XML fail before a document/source is returned.
- Opening performs no implicit resource or network access.

## Required tests

- Root/version/object/feature/container/metadata/Atom/xAL/schema/data/ID/extension matrices and OGC-derived fixtures.
- XXE/XInclude/schema/namespace/reference/depth/fan-out/text/byte limits, cancellation, and cleanup.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, OGC/XML corpus lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact KML/Atom/xAL/extension profile and corpus.
