# G11-003 — Workspace persistence profile

Status: Complete
Depends on: G8-004
Gate: G11
Type: HITL

## Goal

Approve an implementation-ready version 1 workspace profile that securely reopens portable local
Level 1 map configuration without Java serialization, ambient discovery, or live-object persistence.

## Context

Level 1 has explicit feature/raster sources, CRS registries, viewport state, fixed symbols/catalogs,
and bounded diagnostics. G11-001/G11-002 add runtime editing and portrayal state that need not enter a
first portable file. The authoritative profile is in
`design/G11-editing-styling-persistence-adapters-export.md`.

## Scope

Define one AWT-free `mundane-map-workspace` boundary; immutable document/session values; exact UTF-8
XML v1 grammar and canonical bytes; map/display CRS and viewport persistence; ordered feature/raster
layers; guarded local relative paths; fixed named-symbol references; explicit application source/
catalog registries; bounded secure StAX reading; all-or-nothing source opening; structured failures;
atomic replacement; Native Image expectations; and G11-030 through G11-034 vertical slices.

## Out of scope

Production code/modules/tasks for later slices; Java serialization or databinding; live sources,
cursors, bindings, views, listeners, diagnostics, caches, limits, selection/tools/measurement,
G11-001 edit/history state, G11-002 portrayal/labels, catalog contents, credentials, network/cloud
references, Level 2 formats/elevation/adapters, arbitrary object graphs, and format data duplication.

## Acceptance criteria

- Version 1 persists only canonical CRS keys, display center/scale, ordered local opener/identity/path
  references, fixed catalog symbol names, and raster interpolation/opacity; all exclusions are exact.
- `.mmap.xml` has one strict namespace/version grammar, canonical UTF-8 writer, exact unknown-field/
  version rejection, supported-version set `{1}`, and no premature migration framework.
- Portable paths cannot escape the captured workspace base lexically or through resolved symlinks;
  source openers, closed suffix profiles, and catalog/CRS registries are immutable, instance-owned,
  and explicit.
- Secure parsing, semantic values, registries, paths, sessions, and output have explicit limits,
  hard maxima, stable exact-context failures, deterministic precedence, and no implicit network/
  credential/classpath access; only canonical Level 1 CRS keys enter version 1.
- Source open is preflighted and all-or-nothing with reverse cleanup; canonical replace requires a
  same-directory forced temporary file and supported atomic move with no non-atomic fallback.
- G11-030 through G11-034 deliver read, write, open/session, runnable restore, and hardening/native/
  consumer slices before the module or broader persistence claims exist.

## Required tests

No production tests in this design task. Specify later immutable-value, strict XML/UTF-8, limit,
canonical byte/round-trip, XML-scalar, hard/operation-allocation limit, canonical-CRS/alias,
path/symlink/derived-sidecar, registry/catalog, missing resource, opener kind/identity/cancellation,
all-or-nothing cleanup, atomic-write fault, workspace-viewer, publication/consumer, and representative
Linux Native Image scenarios.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 workspace persistence profile approval**. The maintainer approves the portable
field/exclusion list, XML/version/canonicalization policy, local-path threat model, two example opener
policies, catalog rule, limits/failures, atomic replacement, viewer scenario, and five-slice
decomposition before G11-030 is created.

The maintainer approved the checkpoint on 2026-07-17 through the advance HITL authorization for
dependency-free remaining tasks. The design fixes the complete version 1 workspace contract and later
slice graph without creating production APIs, implementation cards, modules, or dependencies. Focused
API/core checks, `qualityGate`, and `git diff --check` passed before closure.
