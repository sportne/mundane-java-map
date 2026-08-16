# G19-119 — KML conformance and capability closeout

Status: Proposed
Depends on: G19-118, G19-226
Gate: G19
Type: HITL

## Goal

Close the KML/KMZ adapter with exact OGC conformance claims, independent earth-browser interoperability,
2D presentation evidence, dynamic/lifecycle proof, and reconciled public capability documentation.

## Context

KML's large object, presentation, resource, network, update, and tour surfaces require conformance and external
evidence beyond unit tests; deliberate 2D/HTML/media exclusions must be explicit rather than hidden gaps.

## Scope

- Pin local KML 2.3 schemas/hashes, applicable OGC 14-068r2 classes/assertions, KML 2.2 compatibility,
  Atom/xAL subset, ZIP/HTTP/HTML profiles, and every deliberate exclusion.
- Add OGC-derived and independent KML/KMZ corpus, schema/ATS execution, writer/read-back, independent consumer/
  producer/earth-browser observations, and AWT/Vaadin 2D rendering comparisons.
- Cover controlled NetworkLink servers, Update generation/failure atomicity, Tour clocks/handlers/lifecycle,
  KMZ/resources, HTML adapter, hostile XML/ZIP/URI/reference/markup, fuzz/mutation, and exact aggregate limits.
- Complete cancellation/cleanup/concurrency, native/publication/offline/API compatibility, diagnostics/no-value-leak,
  examples, `CAPABILITIES.md`, package/module/root Javadocs, README/support tables, and task outcomes.
- Record Model/PhotoOverlay 2D representation, no COLLADA/panorama engine, media authority, and extension boundaries.

## Out of scope

- General 3D earth-browser, panorama/browser/media engines, remote mutation, server hosting, and arbitrary vendor extensions.

## Acceptance criteria

- Every claimed KML class/assertion/object has current automated or reproducible external evidence.
- Multiple independent applications exchange generated/fixture KML/KMZ and declared 2D visuals meet tolerances.
- No undocumented accepted construct, discarded standard value, ambient authority, or unbounded work path remains.

## Required tests

- Full OGC/schema/corpus/consumer/rendering/network/update/tour/KMZ/HTML/fuzz inventory.
- Limits, lifecycle, native/publication/offline/API/documentation inventories and external observation record.

## Validation

Run `./gradlew :modules:mundane-map-io-kml:check --console=plain`, approved OGC/corpus/rendering/network/
native/publication/offline lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves conformance claims, corpus/licenses, external observations, visual/timeline
tolerances, dynamic authority, 2D exclusions, and exact public support wording.
