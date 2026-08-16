# G19-010 — Common CRS catalog and WKT2 coordinate operations

Status: Proposed
Depends on: G19-001
Gate: G19
Type: HITL

## Goal

Expand the JDK-only CRS engine beyond EPSG:4326 and EPSG:3857 with axis-aware common coordinate
systems and a bounded WKT2 parser/operation model.

## Context

Format adapters routinely encounter projected, geographic, vertical, and compound CRS definitions.
Recognizing only two identifiers makes otherwise valid GeoTIFF, PRJ, GeoPackage, and KML workflows
terminally incompatible.

## Scope

- Freeze the supported WKT2 edition, grammar, authority, axis, unit, datum, and operation subset.
- Add an explicitly generated common-CRS registry with provenance and checksum review.
- Implement deterministic axis/unit handling and supported projection operations in pure Java.
- Preserve vertical/compound metadata even where a 3D operation is unavailable.
- Bound parsing and transformations and expose stable unsupported-operation diagnostics.

## Out of scope

- Runtime EPSG database discovery, network grid downloads, JNI, or silently approximate datum shifts.

## Acceptance criteria

- The declared CRS/operation matrix round-trips through identifiers and WKT2.
- Axis order and units are tested independently from presentation conventions.
- Unsupported grids or methods fail before partial transformation with stable context-free codes.
- Registry generation is reproducible and license/provenance documented.

## Required tests

- Authoritative control-point tests per projection/method and difficult axis/unit cases.
- WKT2 corpus, malformed/deep input, numeric boundary, and round-trip tests.
- Cross-adapter PRJ, GeoTIFF, and GeoPackage fixtures.

## Validation

Run `./gradlew :modules:mundane-map-core:check --console=plain`, the CRS corpus lane, then
`./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the pinned profile, external evidence, and any licensed corpus or manual review named by this card before completion.
