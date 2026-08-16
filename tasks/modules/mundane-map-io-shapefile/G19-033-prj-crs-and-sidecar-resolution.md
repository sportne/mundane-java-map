# G19-033 — PRJ CRS and sidecar resolution

Status: Proposed
Depends on: G19-010
Gate: G19
Type: HITL

## Goal

Resolve common Shapefile PRJ/CPG/DBF/SHX sidecars deterministically and interpret the approved OGC and
ESRI WKT 1 CRS dialects through the core CRS model.

## Context

The current adapter recognizes two exact PRJ trees and has strict sidecar naming behavior. Common
producers vary WKT 1 spelling, authority metadata, projection parameters, filename case, and optional
sidecar presence without changing the underlying dataset.

## Scope

- Pin the supported OGC WKT 1 and ESRI WKT dialect/alias/parameter profiles and connect them to the
  G19 core CRS catalog and coordinate operations.
- Preserve a bounded unknown definition while distinguishing syntactically invalid, well-formed
  unknown, unsupported operation, and conflicting caller override states.
- Define exact basename, extension case, duplicate, symlink, path confinement, optional/missing, and
  ownership rules for SHX, DBF, CPG, DBT, and PRJ companions.
- Reconcile PRJ unit/axis/datum/prime-meridian/projection aliases without heuristic CRS guessing.
- Publish stable sidecar/CRS diagnostics and update the module capability matrix.

## Out of scope

- Guessing a CRS from coordinates, consulting network registries, or accepting an ambiguous sidecar.
- WKT 2 export as a `.prj` file unless a future explicit interoperability decision adds it.

## Acceptance criteria

- Common equivalent OGC/ESRI WKT 1 definitions resolve to the same registered CRS and operation.
- Unknown but valid definitions remain available without being silently treated as another CRS.
- Duplicate/case/symlink/path conflicts are platform-deterministic and cannot escape the dataset
  directory or leak resources.

## Required tests

- Cross-producer WKT 1 corpus covering common geographic/projected datums, units, axes, aliases, and
  authority variations.
- Unknown/invalid/conflicting WKT, duplicate/case-variant sidecars, symlink/path attacks, missing
  companions, cancellation, character/recursion limits, and resource-ownership tests.

## Validation

Run `./gradlew :modules:mundane-map-io-shapefile:check --console=plain`, its approved PRJ corpus lane,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the pinned WKT 1 dialects, alias policy, sidecar resolution,
and external CRS corpus before completion.
