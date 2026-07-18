# G10-005 — GPX and KML source profiles

Status: Complete
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Approve small, secure GPX 1.1 waypoint/track and static KML 2.2 geometry profiles before creating two
independent JDK-only source modules.

## Context

Both are XML formats but differ substantially in geometry, presentation, extension, and network
behavior. The existing G4 source/CRS/diagnostic contracts and G0 explicit-construction rules allow one
secure JDK StAX policy to be reviewed, not a combined production module or public XML abstraction.
The normative references are the Topografix GPX 1.1 schema and OGC KML 2.2.

## Scope

Lock GPX version/root/order, waypoint and track-segment records, fixed attributes, omitted per-vertex
data, routes/extensions, and longitude/latitude mapping. Lock KML containers, placemarks, Point,
LineString, Polygon, homogeneous MultiGeometry, clamp-to-ground handling, ignored presentation, and
rejected dynamic/network constructs. For both, define strict UTF-8 local snapshots, direct hardened
JDK StAX, materialized source lifecycle, stable diagnostics, exact limits/accounting, rendering,
publication, consumer, Native Image evidence, and separate working implementation sequences.

## Out of scope

Production parsing or modules, GPX routes/extension semantics, per-track-point attribute series, KML
styles/labels/temporal or region filtering, tours/models/overlays/NetworkLinks, remote resources,
scripts, ExtendedData semantics, KMZ, altitude rendering, writing, schema validation, alternate
encodings, and shared XML library types in public APIs.

## Acceptance criteria

- The **G10 GPX/KML source profile approval** checkpoint approves both independent format matrices,
  warned omissions, and later task graphs.
- Both profiles expose fixed facades returning G4 `FeatureSource`, materialize bounded immutable
  records from one closed local snapshot, normalize longitude/latitude to recognized EPSG:4326, and
  keep parser and format types private.
- `XMLInputFactory.newDefaultFactory()` is namespace-aware and configured/read back with DTD,
  external entities, validation, and replacement disabled plus a resolver that always rejects; no
  schema location, XInclude, URI, href, or external resource causes I/O.
- Exact grammar, geometry, attribute, warning, diagnostic-precedence, cancellation, lifecycle,
  UTF-8, XML-event, feature/coordinate, text, and owned-byte behavior is implementation-ready.
- G10-050 through G10-057 each add working format behavior before a module appears; there is no shared
  XML module, style engine, live-parser source, or empty adapter.

## Required tests

No production tests. Define later generated and independent fixtures for valid feature mappings,
namespace/version/order, UTF-8/XML/XXE/entity/schema-location canaries, warning retention/omission,
malformed and exact/one-over limits, cancellation/cleanup, tolerant rendering, publication consumers,
and explicit Linux Native Image success plus stable malformed outcomes for each format.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G10 GPX/KML source profile approval**. Approval covers both exact subsets, warned
data loss, strict UTF-8/direct JDK StAX boundary, two module/lifecycle designs, stable diagnostics,
Native Image expectations, and eight later task cards. Unapproved behavior remains backlog work.

The maintainer approved the checkpoint on 2026-07-17 through the advance HITL authorization for
dependency-free remaining tasks. The design records both closed profiles and their separate follow-up
graphs without creating a module, changing production code, or adding a dependency. The focused API
check, `qualityGate`, and `git diff --check` passed before closure.
