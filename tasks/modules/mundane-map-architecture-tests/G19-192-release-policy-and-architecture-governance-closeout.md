# G19-192 — Release policy and architecture-governance closeout

Status: Proposed
Depends on: G19-190, G19-191
Gate: G19
Type: HITL

## Goal

Integrate API/JPMS governance into reproducible release workflows and close the architecture support module without overclaiming behavior.

## Context

Comparison tasks are ineffective if ordinary release/publication paths can bypass them, baselines advance early, reports disappear,
or documentation/version metadata disagree.

## Scope

- Make candidate release, publication dry run and CI consume verified baselines, compatibility declarations and module-path tests;
  prohibit release tasks from silently skipping them.
- Generate bounded machine/human API and module reports, minimum-version decision, migration/deprecation inventory and exact release-note links.
- Advance baseline manifests only after immutable publication/checksum/POM/module/consumer/reproducibility verification and provide reviewed
  recovery for a failed publication without reusing coordinates.
- Verify project inventory, dependency categories/scopes, public leakage, forbidden mechanisms, documentation/task links and all generated
  artifact metadata remain consistent.
- State the boundary clearly: domain behavior stays governed by owning-module tests/docs and is not inferred by the API analyzer.

## Out of scope

- A central behavioral specification, automatic release publishing, signing-key ownership or compatibility promises for internals/examples.

## Acceptance criteria

- No supported release path can publish an insufficiently versioned, API-incompatible, incorrectly modularized or unreported artifact.
- Baseline advancement is post-publication, deterministic, reviewable and reproducible online/offline without committing release JARs.
- Public docs, POM/JAR/module metadata, release notes and reports agree on versions, modules, compatibility and exclusions.

## Required tests

- Synthetic compatible/breaking release trains, pre-1.0/1.0 transition, deprecation/removal, emergency exception and multi-artifact bumps.
- Connected/offline baseline staging, publication success/failure/retry, report reproducibility, coordinate reuse, metadata drift and complete
  classpath/module-path consumer matrix.

## Validation

Run architecture checks, qualityGate, offlineRepositoryVerification, nativeSmoke, publicationDryRun and `git diff --check`.

## Notes

HITL checkpoint: approve the final compatibility/module reports, release workflow, baseline advancement and public wording.
