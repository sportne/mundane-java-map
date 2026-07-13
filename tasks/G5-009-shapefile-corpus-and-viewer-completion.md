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
- Corpus-test source set/resources in the existing shapefile module; no corpus production module

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
- Manifest entries distinguish independently curated artifacts from generated/derived coverage, link
  one exact expectation ID, and are complete for every committed corpus component and license file.
  Derived entries resolve an acyclic parent and inherit its license; every dataset has exactly one SHP
  and at most one approved sidecar of each kind.
- `shapefileCorpus` runs offline, is deterministic, has no network dependency, and is not pulled into
  `qualityGate`. Corpus code cannot use network or child-process APIs; only source formatting may be
  shared with the normal gate.
- Corpus assertions verify counts, IDs, geometry topology/envelopes, selected attributes, CRS
  metadata, diagnostics, and sequential/indexed agreement rather than merely opening files.
- The viewer accepts a dataset path, exposes stable load diagnostics, fits valid data, and renders
  all Level 1 geometry/attribute/CRS cases without format logic in AWT.
- Viewer attribute/encoding evidence is a bounded generic schema/value preview; rendering checks use
  geometry, fit, region, ordering, and hole invariants rather than cross-platform pixel identity.
- Unsupported or malformed corpus entries fail with the documented diagnostic and do not crash the
  viewer.
- Negative corpus entries include permanent Z/M rejection, a present discarded-corrupt SHX, a
  terminal corrupt DBF, and a retained-unknown PRJ.
- **HITL checkpoint — G5 corpus and viewer approval:** a maintainer approves every fixture's license/
  generation statement and curated provenance/redistribution rights, then manually
  reviews representative point, line, polygon-hole, encoding, and CRS datasets in the viewer.

## Required tests

- Corpus integration tests for every manifest entry and its expected behavior.
- Tests that the corpus task is isolated from the normal gate and works with network access disabled.
- Viewer loading, diagnostic presentation, fit-to-data, and offscreen rendering tests.
- Viewer tests pin off-EDT open/preview, EDT-only view/listener/panel/install/fit/close work, report
  seeding, pre/during-wait interruption with restored status, wrapper-free startup failures, and
  stage-exact source/binding/view cleanup including close-failure suppression.
- A manifest completeness test that rejects unrecorded or checksum-mismatched fixtures.
- Tests copy parser inputs from the exact checksummed source files, not an unverified classpath copy,
  and enforce parent/license/component-cardinality rules.
- Tests prove `qualityGate`/`checkAll` do not depend on the corpus task, while the corpus task executes
  the complete corpus source set from local committed bytes with network access disabled.
- CI/build-wiring evidence starts from an empty isolated Gradle user home, primes every corpus/runtime/
  Error Prone/JaCoCo/Checkstyle/SpotBugs artifact, then completes the rerun offline from that same home.

## Validation

Manual HITL validation:

```bash
./gradlew :examples:shapefile-viewer:run --args="modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/curated-point-utf8-4326/curated-point-utf8-4326.shp" --console=plain
./gradlew :examples:shapefile-viewer:run --args="modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/generated-multipoint-null-ibm437/generated-multipoint-null-ibm437.shp EPSG:4326" --console=plain
./gradlew :examples:shapefile-viewer:run --args="modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/generated-multipart-line-ibm850/generated-multipart-line-ibm850.shp EPSG:4326" --console=plain
./gradlew :examples:shapefile-viewer:run --args="modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/generated-polygon-hole-windows1252-3857/generated-polygon-hole-windows1252-3857.shp" --console=plain
./gradlew :examples:shapefile-viewer:run --args="modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/generated-point-iso88591-unknown-prj/generated-point-iso88591-unknown-prj.shp EPSG:4326" --console=plain
./gradlew :examples:shapefile-viewer:run --args="modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/generated-point-fallback-deleted/generated-point-fallback-deleted.shp EPSG:4326" --console=plain
./gradlew :examples:shapefile-viewer:run --args="modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/data/generated-corrupt-shx/generated-corrupt-shx.shp" --console=plain
```

```bash
./gradlew :modules:mundane-map-io-shapefile:check :examples:shapefile-viewer:check --console=plain
./gradlew shapefileCorpus --console=plain
./gradlew qualityGate --dry-run --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Prefer purpose-built or public-domain fixtures small enough to review. Keep parser-unit fixtures
separate from the interoperability corpus; project hand-built/fuzz builders do not create corpus
artifacts at test time. Do not duplicate G5-008 fuzzing or G5-010 native verification in this lane.
