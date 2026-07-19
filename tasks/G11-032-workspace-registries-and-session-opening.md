# G11-032 — Workspace registries and session opening

Status: Proposed
Depends on: G11-031
Gate: G11
Type: AFK

## Goal

Open every layer in a workspace as one all-or-nothing owned session through explicit application
registries and guarded local paths.

## Context

G11-003 fixes immutable source/catalog registries, typed opener kinds, closed suffix profiles,
Level 1 CRS resolution, complete preflight before I/O, reverse cleanup, cancellation, and session
ownership.

## Scope

Implement source opener/path-profile and symbol-catalog registries, open context, guarded path
resolution, feature/raster opened-layer values, `WorkspaceOpener`, and closeable `WorkspaceSession` in
`mundane-map-workspace`; prove behavior with explicit fake openers and sources.

## Out of scope

Built-in concrete format openers, service discovery, automatic format detection, remote paths,
credentials/options in XML, AWT bindings, partial sessions, parallel/background opening, or ownership
transfer to consumers.

## Acceptance criteria

- Complete registry/CRS/catalog/symbol/path preflight occurs in layer order before the first source
  opens, using only explicitly registered exact IDs and closed suffix profiles.
- Lexical, real-path, symlink, suffix, kind, identity, CRS-definition, catalog, and symbol-role
  mismatches use the approved bounded problem shapes without leaking paths or caller data.
- Cancellation/source failures close the current and all earlier sources in reverse order; no partial
  session escapes and primary/suppressed failure ordering is stable.
- A successful session owns every source, closes each exactly once in reverse order, and retains only
  immutable descriptors/reports after close.

## Required tests

Registry grammar/duplicate tests; complete preflight/no-I/O tests; path/symlink/suffix/race-boundary,
identity/kind/CRS/catalog/role tests; cancellation at every phase; source warning/failure mapping;
reverse cleanup, repeated close, and architecture tests with fake sources.

## Validation

```bash
./gradlew :modules:mundane-map-workspace:check :modules:mundane-map-architecture-tests:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Trusted opener code receives only the persisted identity, guarded primary path, and cancellation
token. The workspace module supplies no concrete opener and retains no global default registry.
