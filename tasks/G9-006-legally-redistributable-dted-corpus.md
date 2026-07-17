# G9-006 — Legally redistributable DTED corpus

Status: Complete
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

### G9 DTED corpus approval

- Reviewer/date/outcome: project maintainer, preapproved in the task thread; 2026-07-16; `APPROVED`.
  No blocker remains. Approval was recorded before any candidate binary entered the worktree.
- Approved candidates:
  - `gdal-zone-v-l0-complete` ->
    `data/gdal-zone-v-l0-complete/w001/s81.dt0`, 8,762 bytes,
    SHA-256 `9b0f2d2d0b1fdeefb2e551fee98c4fac2da88141dc0fd02e712840fc9508c802`.
  - `gdal-zone-v-l1-complete` ->
    `data/gdal-zone-v-l1-complete/w001/s81.dt1`, 488,642 bytes,
    SHA-256 `ba2b8033ee4942989ec9acb916f95fffc88f054c3e9917145b4e613978db5c4f`.
  - `gdal-zone-v-l2-partial` ->
    `data/gdal-zone-v-l2-partial/w001/s81.dt2`, 4,339,042 bytes,
    SHA-256 `4d0511dd1551b05449ee9a60a4849e3baf132bcff64438f242aebfbaf126e58d`.
  - Approved binary total: 4,836,446 bytes. Approved complete resource-tree total after ingest:
    4,848,167 bytes.
- Factual inventory SHA-256: `manifest.tsv` is
  `6343eaad6b92f317b6fbef25426eae9287f4ecf46c54258bd662a4e75cd99a82`.
  Acquisition recipe SHA-256: `gdal-3.13.0-zone-v.sh` is
  `7112e2dd57437356170446108970fbc08c0da9b9ac384584f4ffef6e7187c7c7`.
- Producer: `GDAL 3.13.0 "Iowa City", released 2026/05/04`, official image tag
  `ghcr.io/osgeo/gdal:ubuntu-full-3.13.0`, Linux/amd64 manifest digest
  `sha256:fd205102ddfaa537e18dac37a9f648e79989e99a4e6f6a2375e5f7e0e511616c`,
  and image-config digest
  `sha256:be85b2a4b798f1d2f10bb9b724336976ce1bf1b0791298b8ad8379fc012d3138`.
  The local Docker credential helper could not perform the anonymous pull, so pinned `crane 0.20.6`
  fetched that exact platform manifest into `/tmp`; the recipe then verified config digest,
  platform, and exact `gdal --version` before generation.
- Origin/transformation: the project owns the deterministic integer elevation formula and Level 2
  3-by-3 void selection. The recipe writes those values to out-of-tree raw/VRT inputs, applies the
  designed outer-edge WGS84 geotransform and fixed DTED metadata, and performs strict GDAL DTED
  `CreateCopy`. No downloaded, access-controlled, paid, personal, operational, or real-terrain data
  contributed.
- License/redistribution decision: the synthetic inputs and generated DTED outputs use the committed
  BSD-3-Clause data license; GDAL producer provenance uses the committed GDAL 3.13.0 MIT terms.
  Repository/source redistribution is approved. Maven binary/source/Javadoc artifacts must remain
  corpus-free and are independently verified before completion.
- Final post-ingest worktree equality before handoff: on 2026-07-16 all three data SHA-256 values,
  the manifest SHA-256, the recipe SHA-256, and the 4,848,167-byte complete resource-tree total were
  rechecked and exactly matched the approved values above. The BSD-3-Clause text is pinned as
  `bdb8a64e8b9d8b172c29ac55927b3659b7f44f21093a4a6880d85dd9a8dcae91`; the GDAL MIT text is pinned
  as `248ef06377cf0679b9ae3c80ab04f6b73fd78bf66d3d81f7d7d934addb212844`.
- Independent review outcome: the initial review findings were remediated and the same reviewer found
  no remaining actionable issue after re-review. Focused module/architecture checks, a newly primed
  empty-home offline corpus run, normal-gate isolation, the full quality gate, and whitespace passed.
  The exact dirty source snapshot passed `publicationDryRun` from `/tmp` in 45 seconds; two runs from
  `/mnt/d` timed out in the filesystem-heavy functional child without reporting a test failure.
