# G5-009 — Shapefile corpus and viewer completion

Status: Proposed
Depends on: G5-008
Gate: G5
Type: HITL

## Goal

Prove the complete Level 1 shapefile profile against a small legally redistributable corpus and make
every supported shape, attribute, encoding, and recognized CRS observable in the viewer.

## Context

Hand-built fixtures and fuzzing establish precise parser behavior. A separate corpus lane adds
interoperability evidence without slowing or destabilizing the normal quality gate.

## Scope

- Curated corpus fixtures, provenance/license manifest, expected metadata, and assertions
- Root `shapefileCorpus` Gradle lane and CI wiring separate from `qualityGate`
- Completion of `examples/shapefile-viewer` for supported sidecars, diagnostics, and CRS cases

## Out of scope

- Downloading test data during the build, checking in data with unclear redistribution rights, or
  claiming compatibility outside G5-001
- Native Image verification, writer support, and performance benchmarking

## Acceptance criteria

- The committed corpus includes small examples for point, multipoint, polyline, polygon, multipart,
  holes, null shapes, DBF fields, each approved encoding path, indexed access, and recognized/unknown
  PRJ behavior; cases may be combined where traceability remains clear.
- At least one corpus dataset was produced independently by a real GIS tool or data publisher rather
  than by the parser's fixture generator, with redistribution rights and expected behavior reviewed.
- Every fixture has origin, license or generation statement, checksum, expected profile behavior, and
  any intentional defect recorded in a machine-checkable or reviewable manifest.
- `shapefileCorpus` runs offline, is deterministic, has no network dependency, and is not pulled into
  `qualityGate`.
- Corpus assertions verify counts, IDs, geometry topology/envelopes, selected attributes, CRS
  metadata, diagnostics, and sequential/indexed agreement rather than merely opening files.
- The viewer accepts a dataset path, exposes stable load diagnostics, fits valid data, and renders
  all Level 1 geometry/attribute/CRS cases without format logic in AWT.
- Unsupported or malformed corpus entries fail with the documented diagnostic and do not crash the
  viewer.
- **HITL checkpoint:** a maintainer approves fixture provenance/redistribution rights and manually
  reviews representative point, line, polygon-hole, encoding, and CRS datasets in the viewer.

## Required tests

- Corpus integration tests for every manifest entry and its expected behavior.
- Tests that the corpus task is isolated from the normal gate and works with network access disabled.
- Viewer loading, diagnostic presentation, fit-to-data, and offscreen rendering tests.
- A manifest completeness test that rejects unrecorded or checksum-mismatched fixtures.

## Validation

Manual HITL validation:

```bash
./gradlew :examples:shapefile-viewer:run
```

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew shapefileCorpus --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Prefer purpose-built or public-domain fixtures small enough to review. Keep parser-unit fixtures
separate from the interoperability corpus even if they share generation helpers.
