# G10-005 — GPX and KML source profiles

Status: Proposed
Depends on: G8-004
Gate: G10
Type: HITL

## Goal

Define small, secure first profiles for GPX waypoints/tracks and static KML features before creating
independent source adapters.

## Context

Both are XML formats but differ substantially in geometry, styling, extension, and network behavior.
The existing feature-source and secure-input rules allow a shared review approach, not a combined
production module.

## Scope

For GPX, decide supported versions, waypoints, track segments, timestamps/elevation, extensions, and
WGS 84 mapping. For KML, decide static point/line/polygon/multipart features, folders, names,
altitude handling, and a minimal style subset. For both, define secure JDK XML parsing, stable
diagnostics, size/depth/element/coordinate limits, lifecycle, and later separate task sequences.

## Out of scope

Production parsing or modules, GPX routes unless promoted by the decision, KML tours/models/regions,
NetworkLinks, remote resources, scripts, arbitrary extensions/styles, KMZ, writing, and shared XML
library types in public APIs.

## Acceptance criteria

- A maintainer approves separate GPX and KML supported/rejected/deferred matrices.
- Profiles define geometry/attribute mapping, CRS/axis behavior, unsupported-content diagnostics, XML
  hardening, and conservative resource limits.
- Network and external entity resolution are disabled; local input never causes implicit I/O.
- Follow-up tasks are format-specific working slices and create no empty or combined XML adapter.

## Required tests

No production tests. Identify later valid, namespace/version, malformed XML, XXE/entity, limit,
geometry/rendering, corpus, and Native Image cases for each format.

## Validation

```bash
./gradlew :modules:mundane-map-api:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: the maintainer approves both first profiles and their separate implementation task
graphs. Features not approved here remain backlog items, not implied parser behavior.
