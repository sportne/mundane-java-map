# G9-006 — Legally redistributable DTED corpus

Status: Proposed
Depends on: G9-004
Gate: G9
Type: HITL

## Goal

Establish a small automated DTED Level 0/1/2 corpus whose independent producer, provenance, and
redistribution terms are explicitly approved for the repository while remaining absent from Maven
artifacts.

## Context

Hand-built fixtures prove parser mechanics but not independent producer variation. Downloaded terrain
often has access or redistribution restrictions; project-authored synthetic values emitted by pinned
GDAL can provide legally reviewable compatibility bytes. G9-004 fixes the strict validation and public
diagnostic contract that the corpus must exercise without expanding.

## Scope

Create exactly three approved GDAL 3.13.0-generated zone-V cells: complete Level 0/1 and fixed-array
partial-99 Level 2 with project-owned deterministic values and a 3-by-3 Level 2 void island. Add the
checked non-build recipe, exact manifest/roles/licenses/hashes/caps, finite public-reader expectations,
dedicated `dtedCorpusTest` source set, root `dtedCorpus` plus prime helper, normal/publication isolation,
and separate offline Java 21 CI. Complete the named pre-binary maintainer checkpoint.

## Out of scope

Downloaded or real terrain, runtime/build acquisition, invoking GDAL from Gradle/tests, ambiguous,
paid, personal, or access-controlled inputs, invalid cropped cells, broad geographic coverage,
benchmark-scale files, parser/profile expansion, G9-003 writer reuse, queries/interpolation, malformed
matrices, rendering, native/performance evidence, and any corpus/runtime Maven artifact.

## Acceptance criteria

- **G9 DTED corpus approval** records reviewer/date/outcome, candidate/manifest/recipe hashes, exact
  sizes/paths, GDAL tag and Linux/amd64 image digest, data/tool license bases, transformations, and
  absence of downloaded/access-controlled input before any binary is staged or committed.
- The exact three generated files are independently written by pinned GDAL, use the approved formula
  and `-1..0`/`-81..-80` bounds, have dimensions/byte lengths fixed by the design, and match approved
  SHA-256 values; Level 0/1 have no void and only declared-partial Level 2 has `0xffff` no-data.
- UTF-8/LF `manifest.tsv` enforces safe paths, closed roles/tags, literal sizes/hashes, original names,
  provenance, recipe/tool version, BSD data and MIT tool license references, derivation, and a
  one-to-one expectation ID for every data file; no resource is unreferenced.
- Caps are at most six datasets, 5,242,880 bytes per data file, and 6,291,456 bytes for the complete
  resource tree; equality is accepted and any increase or replacement returns to HITL.
- The finite oracle uses only `DtedFiles`/`ElevationSource` and asserts exact metadata, bounds, CRS,
  units, orientation, corner/midpoint/interior samples, Level 2 voids, empty diagnostics, close, and
  immediate temporary-file deletion for every initial dataset.
- `dtedCorpus` owns the isolated test/static-analysis lane, passes from a newly primed empty home with
  `--offline --rerun-tasks`, and corpus bytecode cannot use network or process APIs.
- Corpus resources/tasks are mechanically absent from normal gates and binary/source/Javadoc Maven
  artifacts; `publicationDryRun` proves the staged DTED artifact remains clean.
- Ambiguous or mismatched candidates are excluded/Blocked rather than committed, downloaded at
  runtime, relabeled, or accepted with a disclaimer.

## Required tests

Manifest grammar/completeness/role/path/reference/cap/digest/expectation tests; exact Level 0/1/2
public compatibility and orientation/no-data/lifecycle tests; corpus task-closure and publication
absence tests; network/process bytecode policy; separate empty-home offline CI evidence.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew primeDtedCorpusDependencies --console=plain
./gradlew --offline dtedCorpus --rerun-tasks --console=plain
./gradlew publicationDryRun --console=plain
./gradlew qualityGate --dry-run --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G9 DTED corpus approval**, with the exact evidence and pre-staging timing named in
the design. Candidate generation happens out-of-tree; Gradle never runs the recipe. The task notes,
not the factual manifest, record approval and final pre-commit worktree hash equality. A new/changed
file repeats the checkpoint. Do not substitute real terrain, weaker parser expectations, or a runtime
download.
