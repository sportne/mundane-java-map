# G19-179 — Workspace capability and portability closeout

Status: Proposed
Depends on: G19-178
Gate: G19
Type: HITL

## Goal

Close the native workspace module only after complete state, migration, package, security, fault, lifecycle,
filesystem/platform and public-support evidence agrees with its capability matrix.

## Context

Individual persistence slices can pass while state remains untracked, packages are non-portable, migrations lose
values, recovery fails on a platform, or documentation misrepresents the custom format as standardized.

## Scope

- Reconcile every public state item and every XML/package/version/extension/resource/integrity/transaction/lifecycle
  rule with implementation, stable diagnostics and evidence.
- Cross-read/write/migrate all released workspace fixtures and generate deterministic v2 XML/packages consumable by
  the current release on every supported platform/filesystem.
- Exercise every registered adapter/resource-set and representative complete AWT/Vaadin projects without adding hard
  adapter dependencies or leaking toolkit/third-party types.
- Run hostile XML/ZIP/path/URI/extension/digest corpora, fuzzing, limits, cancellation, concurrency, locking,
  disk-full/crash/recovery, ownership, deterministic reproduction and long lifecycle soak.
- Reconcile README, package Javadocs, examples, schema/capability documentation and explicit custom/non-standard/
  no-secret/no-signature/no-OWS-Context support wording.

## Out of scope

- Declaring third-party project-format compatibility or broader security/durability/platform guarantees than evidenced.

## Acceptance criteria

- No committed public state or resource type is omitted without explicit derived/transient/forbidden rationale.
- Standalone and packaged projects migrate, round-trip, reopen and recover portably across the supported matrix.
- Independent persistence/security review finds no untracked gap or misleading standards/trust claim.

## Required tests

- Re-run every predecessor state/migration/package/security/fault/lifecycle test and all historical fixture migrations.
- Full registered-adapter, AWT/Vaadin/example, supported filesystem/OS/JDK, offline/publication/Javadoc, deterministic
  clean-build and long repeated open/edit/save/package/reopen/close evidence.
- Independent source/API/schema/docs/security review with every finding fixed or explicitly resolved in the matrix.

## Validation

Run all predecessor and specialized lanes, `./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: maintainers and an independent persistence/security reviewer approve the state inventory,
migrations, package/profile, portability/recovery evidence and final custom-format support wording.
