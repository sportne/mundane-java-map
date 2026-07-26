# G17-005 — Enforce 80 percent coverage per source file

Status: Proposed
Depends on: G17-004
Gate: G17
Type: AFK

## Goal

Prevent high-coverage files from hiding untested production files by requiring at least 80%
instruction coverage for every hand-authored production source file.

## Context

G17-004 raises each governed project's bundle-level instruction ratio to 80%, but aggregate coverage
can still conceal a file with little or no direct evidence. JaCoCo supports `SOURCEFILE` violation
rules; the project needs exact population, zero-instruction, multi-class-source, generated-source,
and diagnostic policies before making that rule mandatory.

## Scope

- All hand-authored production Java/Groovy source files in modules, examples, and build logic
- Shared JaCoCo aggregation/verification conventions and deterministic coverage-population tests
- Behavioral tests and fixtures needed to bring every executable source file to the threshold
- Machine-readable XML/CSV and human/LLM-readable Markdown summaries of per-file results

## Out of scope

- Test-source coverage, generated source, vendored third-party source, line/branch/mutation coverage
  thresholds, or specialized performance/corpus/native execution accounting
- Blanket exclusions for records, exceptions, DTOs, adapters, examples, build logic, difficult
  packages, or files below the threshold
- Suppressing violations by merging files, moving executable code into excluded source sets, or
  adding execution-only tests without assertions

## Acceptance criteria

- An authoritative inventory maps every hand-authored production source file to its JaCoCo classes
  and reports any executable file missing from coverage data; build logic is included rather than
  implicitly exempted.
- Every executable source file reaches at least `0.80`
  `INSTRUCTION/COVEREDRATIO` at JaCoCo `SOURCEFILE` scope.
- Files with no executable instructions are reported separately and do not produce false failures;
  generated or vendored sources may be excluded only through exact path provenance checked by a
  test, never by a broad name/package pattern.
- Multi-class, nested-class, record, enum, lambda, and Groovy source mappings are handled
  deterministically without allowing one physical source file to disappear from the inventory.
- The normal `check` and `qualityGate` fail with the project-relative source path, actual ratio, and
  required ratio for every violating file, while retaining the G17-004 bundle-level rule.
- Tests added for low-coverage files assert real behavior, boundary conditions, failures,
  diagnostics, state transitions, ownership, or cleanup and do not weaken production limits or
  visibility.
- Per-file XML/CSV and Markdown reports are deterministic, sorted by project-relative path, and
  identify covered/missed instructions and ratio for coding-agent review.
- Build-logic functional tests prove exact-80% success, below-80% failure, uncovered-file detection,
  zero-instruction handling, deterministic reporting, and continued attachment to `check`.

## Required tests

- Build-logic unit/functional fixtures covering the complete per-file policy and failure output.
- Focused behavioral tests for every production source file brought above the threshold.
- Full-project aggregate and per-source-file verification from a clean coverage-data state.

## Validation

```bash
./gradlew :build-logic:check checkAll --console=plain
./gradlew clean qualityGate --console=plain
git diff --check
```

## Notes

Keep the per-file rule additive to the G17-004 bundle rule. If JaCoCo's built-in `SOURCEFILE` failure
message cannot provide stable project-relative paths and ratios, add a small report reader over the
generated XML rather than replacing JaCoCo instrumentation or introducing a coverage service.
