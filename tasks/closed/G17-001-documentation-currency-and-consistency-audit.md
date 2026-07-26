# G17-001 — Documentation currency and consistency audit

Status: Complete
Depends on: G10-044, G16-007
Gate: G17
Type: AFK

## Goal

Make the project documentation an accurate, navigable description of the current implementation,
supported profiles, examples, verification lanes, and known limitations.

## Context

All existing task cards are complete, but the project has grown across formats, symbology,
interaction, persistence, export, stress testing, and continuous world wrap. Documentation written
for earlier gates can become stale even when each gate updated its immediate design section. Current
source, tests, examples, build tasks, and CI workflows are authoritative when prose disagrees.

## Scope

- `README.md`, `DESIGN.md`, `design/`, `ROADMAP.md`, `tasks/README.md`, and completed task links
- Module and example README files, checked-in support statements, fixture provenance notices, and
  user-facing run instructions
- Cross-document links, module/example inventories, supported-format/profile tables, command names,
  Java/platform requirements, Native Image statements, and current limitations
- Small documentation-validation checks in `build-logic` or architecture tests when they prevent a
  demonstrated class of stale link, inventory, or command reference

## Out of scope

- Production behavior, public API changes, new formats or examples, broad documentation-site
  infrastructure, or rewriting completed task evidence
- Exhaustive declaration-level Javadocs, which belong to G17-003
- Claims that require new interoperability, performance, platform, or release evidence

## Acceptance criteria

- Every documented module, example, Gradle command, task link, design link, and workflow exists and
  uses its current name; every current published module and runnable example appears in the
  appropriate inventory.
- README and design material distinguish implemented behavior, bounded supported profiles,
  optional adapters, examples/evidence, and unsupported or deferred behavior.
- Format, symbology, projection, world-wrap, Native Image, SQLite-platform, Java baseline, and
  publication statements match current tests and workflows without broadening support claims.
- Run instructions use working arguments and identify opt-in, expensive, external-tool, or
  platform-specific lanes accurately.
- Duplicate or contradictory normative descriptions are consolidated or linked to one
  authoritative location while historical task evidence remains intact.
- Fixture provenance and third-party notices are linked from the relevant user/developer
  documentation and retain exact licenses and support boundaries.
- Any automated documentation check added by this task is deterministic, offline, fast enough for
  `qualityGate`, and validates repository facts rather than prose style.
- A final source-versus-documentation audit records no known stale capability, command, inventory,
  support, or limitation statement.

## Required tests

- Deterministic internal-link, indexed-task, module/example inventory, and documented-command checks.
- Focused tests for any new build-logic or architecture validation.
- Manual source/test/workflow comparison for support claims that cannot be checked mechanically.

## Validation

```bash
./gradlew :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Treat source and executable evidence as authoritative, but do not erase useful rationale or
completed-task evidence merely to shorten documents. Correct stale claims at their authoritative
location and link to it from summaries.
