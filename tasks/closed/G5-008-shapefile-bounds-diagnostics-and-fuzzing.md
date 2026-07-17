# G5-008 — Shapefile bounds, diagnostics, and fuzzing

Status: Complete
Depends on: G5-003, G5-005, G5-006, G5-007
Gate: G5
Type: AFK

## Goal

Harden every supported shapefile and sidecar path against malformed or hostile inputs with uniform
limits, stable diagnostics, and deterministic fuzz tests.

## Context

G5-002 through G5-007 implement all Level 1 records and sidecars. This task closes cross-cutting
parser gaps before real-world corpus and Native Image validation.

## Scope

- Shared limits, checked arithmetic, and diagnostic mapping in `mundane-map-io-shapefile`
- Adversarial hand-built fixtures and deterministic mutation/generation tests
- Test-only fixed-seed mutation support that invokes the public `Shapefiles` opener
- Architecture checks for prohibited parser/runtime mechanisms

## Out of scope

- Real-world corpus acquisition, nondeterministic long-running fuzz infrastructure, and performance
  tuning
- Recovery of corrupt geometry beyond the approved G5-001 policy

## Acceptance criteria

- The format-local immutable `ShapefileLimits` controls file bytes, physical records, record bytes,
  parts, points, polygon topology comparisons, fields, field widths, sidecar text, decoded DBF text,
  and parser allocation with the approved defaults/logical charge table. The separate immutable G4
  `FeatureSourceLimits`/`FeatureQueryLimits` continue to control public query work and payload.
- Every untrusted count, offset, length, multiplication, and addition is range-checked before seeking,
  slicing, or allocating.
- SHP, SHX, DBF, CPG, and PRJ failures map to stable structured codes with source, sidecar, record,
  part/field, byte offset, severity, and bounded cause context as applicable.
- Truncated, overlong, malformed, mixed-endian, inconsistent-sidecar, non-finite, and integer-overflow
  inputs produce the approved terminal or warning/fallback outcome within configured work/allocation
  bounds without hangs, `OutOfMemoryError`, stack overflow, leaked resources, or unclassified parser
  exceptions.
- Deterministic fuzz tests use committed seeds, fixed iteration/work limits, reproducible failure
  output, and exercise both generated records and mutations of valid hand-built fixtures.
- Breadth cases use an absent source envelope, `ALL` attributes, no tighter query limits, and
  `CancellationToken.none()`; injected I/O, cancellation, cleanup, required-file, and ambiguity
  failures are accepted only in their named targeted fixtures.
- Targeted fixtures assert exact diagnostics and precedence; mutation cases assert a repeatable public
  success/warning/known-terminal outcome and fail on every other runtime exception. Fatal VM errors
  are never caught or relabeled.
- The mutation harness has no production API, external fuzzing dependency, automatic shrinker,
  unbounded random input, or new Gradle lane. It records a bounded replay descriptor; any durable
  regression is manually minimized into a committed targeted fixture.
- A failure in one opened source cannot alter registry, charset, limit, or diagnostic behavior for a
  later source.
- Valid fixtures from earlier tasks retain identical observable features and diagnostics.

## Required tests

- Parameterized boundary tests at limit-1, limit, and limit+1 for every configured maximum, including
  topology work and each logical allocation category.
- Separate G4 boundary tests cover records examined/returned, returned coordinates/attribute values/
  decoded text/payload bytes, and retained-warning omission at one below/equal/one above.
- Targeted hostile fixtures for truncation at structural boundaries, endian swaps, offset/count
  overflow, huge declarations, invalid encodings, inconsistent sidecars, and malformed multipart
  tables.
- Fixed-seed fuzz tests accept success or a code from the exact documented G5 warning/terminal set,
  never a prefix/family match, and require the complete normalized outcome to repeat exactly.
- Repeat each generated case from its replay descriptor and compare normalized metadata, features,
  warning/error codes, locations, contexts, and separate omitted-warning counts for opening, cursor,
  and optional close reports; message/cause text is excluded.
- File-handle/lifecycle tests after success, parser rejection, cancellation, and cursor abandonment.
- Architecture/source checks retain the JDK-only, AWT-free, no-reflection/scanning/serialization/JNI/
  `Unsafe`/internal-JDK/memory-mapping boundary and reject production fuzz helpers.

## Validation

```bash
./gradlew :modules:mundane-map-io-shapefile:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep fuzzing deterministic and short enough for the normal JVM gate. Commit only manually minimized
reproducer bytes for a durable regression. Never catch fatal VM errors to disguise an unchecked
allocation. Do not add or invoke
`shapefileCorpus`; G5-009 owns that separate command and real-world corpus lane.

Completed on 2026-07-14 with component-accurate prospective accounting, bounded zero-progress reads,
hard Java-capacity checks for raised sidecar limits, cancellation-aware sidecar scans, and decimal
allocation approval before materialization. Test-only adversarial fixtures provide exact structural,
endian, capacity, warning-order, lifecycle, format-limit, and G4 query-limit boundary evidence. The
fixed five-seed harness executes 256 replayable cases twice through the public source/cursor path,
normalizes phase reports independently, enforces exact code/severity allowlists, deletes every fresh
dataset after close, and checks a clean sentinel after each component family. Architecture policy now
also prohibits memory mapping and keeps all mutation helpers outside production output.
