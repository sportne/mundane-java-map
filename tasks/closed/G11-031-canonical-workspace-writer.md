# G11-031 — Canonical workspace writer

Status: Complete
Depends on: G11-030
Gate: G11
Type: AFK

## Goal

Serialize any valid version 1 workspace document to deterministic UTF-8 bytes and replace a local
workspace file atomically without corrupting an existing target on failure.

## Context

G11-030 supplies the immutable model and reader. G11-003 fixes canonical element/attribute order,
numeric/XML escaping, bounded direct encoding, and forced same-directory atomic replacement.

## Scope

Add canonical version 1 encoding and `WorkspaceFiles.write` in `mundane-map-workspace`, with exact
output/operation accounting, private same-directory temporary files, `force(true)`,
`ATOMIC_MOVE|REPLACE_EXISTING`, deterministic cleanup, and typed writer problems.

## Out of scope

Pretty-print options, preserving input comments/formatting, non-atomic fallback, directory creation,
source/path resolution, registry lookup, migration, backup/version management, or serializers from
external dependencies.

## Acceptance criteria

- Equal documents emit byte-identical XML declaration, namespace, indentation, attribute order,
  escapes, `Double.toString` numbers, LF endings, and final newline independent of locale/platform.
- Write-read-write is byte-identical and exact/one-over output and operation-byte limits are checked
  prospectively before touching the destination.
- Existing targets survive pre-move failure; successful output is forced and atomically replaced;
  unsupported atomic move and injected temporary/write/force/move/cleanup failures retain the
  approved primary/suppressed ordering.
- No source reference is resolved or opened during serialization.

## Required tests

Exact canonical bytes, escaping and numeric boundaries, locale/line-ending independence, round-trip
identity, output/accounting limits, replacement preservation, symlink/wrong-kind targets, injected
file-stage failures, and cleanup suppression tests.

## Validation

```bash
./gradlew :modules:mundane-map-workspace:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

There is no non-atomic fallback. Success claims only completion of the provider's atomic name move,
not directory-fsync or power-loss durability beyond the approved profile.
