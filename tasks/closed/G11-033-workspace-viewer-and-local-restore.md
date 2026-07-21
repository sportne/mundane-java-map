# G11-033 — Workspace viewer and local restore

Status: Complete
Depends on: G11-032
Gate: G11
Type: AFK

## Goal

Reopen and display a useful local workspace containing shapefile and world-file raster layers through
explicit application glue and correct resource ownership.

## Context

G11-032 supplies transactional session opening. G11-003 defines the exact example opener policies,
fixed named-symbol catalog, persisted view/raster state, runtime component sizing, and close order.

## Scope

Create `examples/workspace-viewer` with small local shapefile and world-file PNG fixtures; explicitly
register fixed shapefile/image opener policies and a symbol catalog; construct AWT bindings from the
opened session, restore viewport and raster interpolation/opacity, and close the view before session.

## Out of scope

Persisting feature data, portrayal/labels, edit history, selection/tools, credentials, remote sources,
automatic opener discovery, workspace editing UI, file watching, or additional formats.

## Acceptance criteria

- The example reads one `.mmap.xml`, resolves only guarded local fixture paths, opens both layer kinds,
  and restores order, names, fixed symbols, CRS, center/scale, interpolation, and opacity.
- Runtime component dimensions combine with persisted center/scale without being serialized.
- View bindings borrow sources while the workspace session remains open; successful and failed paths
  close the view then all owned sources exactly once.
- Missing/tampered fixture, unregistered policy/catalog, and incompatible CRS surface structured
  workspace/source failures rather than partial display or guessed recovery.

## Required tests

Example configuration/fixture tests, local opener integration, restored view/layer/raster assertions,
ownership and failure cleanup, no-network/no-discovery architecture checks, and runnable smoke.

## Validation

```bash
./gradlew :modules:mundane-map-workspace:check :examples:workspace-viewer:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Application glue fixes parser/decoder limits and CRS policy under versioned opener IDs; XML cannot
raise those limits or supply arbitrary options.
