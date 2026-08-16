# G19-099 — SE/SLD conformance and capability closeout

Status: Proposed
Depends on: G19-098
Gate: G19
Type: HITL

## Goal

Close the SE/SLD adapter with exact conformance claims, independent interoperability, cross-renderer
parity, hostile-input proof, and reconciled public capability documentation.

## Context

Feature implementation alone does not establish externally recognizable format completeness. The
approved bounded profile needs schema/ATS evidence, independent documents/tools, and explicit exclusions.

## Scope

- Freeze exact SE 1.1, SLD 1.1, Filter Encoding 1.1, GML, XML, XLink, schema, and applicable conformance classes.
- Add official-derived and independently produced reader/writer fixtures, schema validation outside runtime,
  read-write-read semantics, deterministic goldens, and independent tool observations.
- Add AWT/Vaadin/SVG comparisons for vector, text, raster, coverage, UOM, rule, scale, geometry, and resource behavior.
- Complete malformed/hostile XML, AST/reference/geometry/resource/raster/text/output bombs, cancellation,
  cleanup, native/publication/offline, API compatibility, and diagnostic/no-value-leak evidence.
- Reconcile `CAPABILITIES.md`, package/module/root Javadocs, README/support tables, examples, limits,
  diagnostics, task outcomes, and deliberate exclusions including temporal FES 2.0 and WMS operations.

## Out of scope

- Claiming FES 2.0, CQL2, OGC API Styles, WMS server/client operations, vendor dialects, or general GML support.

## Acceptance criteria

- Every public claim is backed by current automated evidence or a recorded reproducible external observation.
- Multiple independent implementations consume generated documents and their supported documents parse/render
  within declared semantic/visual tolerances.
- The module has no undocumented accepted construct, omission, ambient dependency, or unbounded work path.

## Required tests

- Applicable OGC/schema/independent corpus matrix, reader/writer round trips, cross-renderer goldens, and fuzz/mutation.
- Limit/cancellation/cleanup/native/publication/offline/API/documentation inventories and external tool records.

## Validation

Run `./gradlew :modules:mundane-map-io-se:check --console=plain`, approved OGC/corpus/rendering/native/
publication/offline lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact conformance statement, schemas/corpus licensing,
independent tool observations, rendering tolerances, deliberate exclusions, and public support wording.
