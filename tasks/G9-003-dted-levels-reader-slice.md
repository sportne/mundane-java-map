# G9-003 — DTED Levels 0, 1, and 2 reader slice

Status: Proposed
Depends on: G9-001
Gate: G9
Type: AFK

## Goal

Read valid DTED Level 0, Level 1, and Level 2 files into the shared elevation model through one working,
dependency-free vertical slice.

## Context

DTED stores elevation profiles with UHL, DSI, ACC, and data records. It is elevation data, not a
generic image. G9-001 defines failure-free random sample access after successful publication, so this
first file source must decode eagerly and close transaction I/O before returning. Follow
MIL-PRF-89020B sections 3.9/3.13 and tables I–III; detailed hostile-input hardening follows in G9-004.

## Scope

Create `modules/mundane-map-io-dted` only as part of delivering the working reader, tests, Gradle
registration, publication/consumer extension, and Javadocs. Add only public `DtedFiles` and immutable
`DtedOpenOptions`; return `ElevationSource`. Implement the strict fixed-profile WGS84 with `MSL|E96`,
zero-orientation, one-degree Level 0/1/2 subset with standard latitude-zone intervals/dimensions,
full-length west-to-east profiles, south-to-north signed-magnitude metre samples, declared-partial
Level 2 voids, bounded eager `FileChannel` decoding, stable diagnostics, and G9-001 limits/lifecycle.
Generate complete/no-void Level 0 and Level 1 plus fixed-array partial/void Level 2 zone-V fixtures,
and extend staged artifacts plus the standalone Java 21 consumer to the new published module.

## Out of scope

GeoTIFF, compressed archives, DTED writing, network retrieval, terrain rendering, coordinate
interpolation, a real-world corpus, lazy/windowed access, native code, public header/level/record
models, file-retaining sources, multiple accuracy subregions, nonzero orientation, non-WGS84 datum,
two's-complement dialects, variable-length partial profiles, checksum enforcement, and exhaustive
malformed-input behavior. Partial Level 0 and SRTM/other partial Level 1 profiles are unsupported;
G9-004 owns typed DTED parser limits, checksum/malformed matrices, and fuzz.

## Acceptance criteria

- `DtedFiles` synchronously opens exact supported Level 0, 1, and 2 content through caller identity,
  immutable options, and optional cancellation, returning only the format-neutral elevation source.
- Required UHL/DSI/ACC fields agree exactly in integral tenths of arc-seconds; declared level,
  one-degree bounds, standard latitude-zone intervals, dimensions, expected file size, WGS84 with
  `MSL|E96`, zero orientation, and no-subregion profile are established before sample allocation.
- Fixed data records validate their sentinel/counts, decode big-endian signed magnitude without
  two's-complement guessing, map `0xffff` to no-data, canonicalize negative zero, and transpose
  west-to-east/south-to-north file order to north-first row-major G9 storage.
- The opener reuses fixed buffers, applies G9-001 limits before allocation, checks cancellation through
  controlled work, closes its channel before packed-grid publication, discards a post-copy cancelled
  grid, and never retains path, header, channel, temporary array, token, cache, or worker.
- Initial `DTED_*` I/O/header/level/profile/length/record failures and existing source limit/cancel
  outcomes have stable bounded diagnostics, deterministic precedence, non-sensitive context, and
  primary/suppressed cleanup behavior.
- Independent generated zone-V fixtures prove Level 0 `21x121`, Level 1 `201x1201`, and Level 2
  `601x3601` metadata, orientation, positive/negative/zero samples, Level 2 leading/interior/trailing
  voids, checksums, and lifecycle.
- Production code is JDK-only, AWT-free, reflection-free, and contains no DTED-specific type in
  `mundane-map-api`.
- The module is not added until its reader/tests/query exist in the same change; it is classified as
  published Level 2 JDK-only runtime but not Level 1/native-targeted, the staged contract contains the
  six G8-baseline task-required artifacts plus any approved append-only Level 2 addenda, and the clean
  Java 21 consumer retains prior scenarios and opens/queries a tiny Level 0 fixture.

## Required tests

Unit tests for exact ASCII coordinate/interval/level fields, zone/count derivation, checked size/index
math, big-endian preambles, signed-magnitude positive/negative/positive-zero/negative-zero/void
conversion, level-specific partial/void policy, and south-to-north transpose. Public integration tests
generate/open one independent zone-V fixture per level and assert metadata, known samples, Level 2
voids, empty diagnostics, limits, pre-/mid-/pre-copy/post-copy cancellation, channel-before-return
ownership, I/O/close primary-suppressed failures, idempotent close, and retained metadata.
Architecture/project-inventory tests, the six-artifact task-required publication subset plus approved
addenda, and its standalone consumer query are required; exhaustive malformed/checksum/fuzz cases
remain G9-004.

## Validation

```bash
./gradlew :modules:mundane-map-io-dted:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Use positional JDK file I/O, fixed reusable record buffers, explicit big-endian decoding, and checked
arithmetic. The file checksum is consumed but enforcement begins in G9-004. Do not add a dependency,
viewer, public parser model, or lazy abstraction. See `design/G9-elevation-and-dted.md` for the exact
supported profile, transaction, diagnostics, fixtures, and publication boundary.
If another approved Level 2 publication task lands first, retain its artifact/consumer entries and
reconcile the complete authoritative manifest; the six-artifact count is this task's G8-baseline
required subset, not a reset instruction.
