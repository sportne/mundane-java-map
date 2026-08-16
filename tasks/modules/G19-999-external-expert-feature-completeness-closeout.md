# G19-999 — External-expert feature-completeness closeout

Status: Proposed
Depends on: G18-061, G19-002, G19-014, G19-021, G19-036, G19-044, G19-054, G19-058, G19-066, G19-079, G19-089, G19-099, G19-106, G19-119, G19-129, G19-136, G19-149, G19-159, G19-169, G19-179, G19-189, G19-192, G19-204, G19-214, G19-224, G19-226, G19-229
Gate: G19
Type: HITL

## Goal

Close G19 only after independent subject-matter review confirms that every module is complete for its
named domain/profile and that all remaining exclusions are intentional, precise, and non-misleading.

## Context

Passing internal tests does not by itself establish standards or domain completeness. Each adapter,
renderer, platform surface, and cross-module workflow needs an expert-readable conformance matrix,
interoperability evidence, and a fresh source-level gap audit.

## Scope

- Verify every G19 card and G18-061 is complete and its code/docs/tests match the recorded outcome.
- Produce per-module matrices mapping specification clauses/features to support, exclusion, and evidence.
- Run fresh applicable standards suites, independent-producer/consumer corpora, hostile-input, ownership,
  rendering, browser, native, publication, offline, and performance lanes.
- Commission independent review by qualified specialists for geospatial standards, image formats,
  symbology, accessibility, security, SQLite/native deployment, and API compatibility as applicable.
- Reconcile README, package Javadocs, design, roadmap, task records, support matrices, and release notes.

## Out of scope

- Declaring “complete” because unknown inputs fail cleanly, or hiding a material gap as an undocumented
  implementation detail.

## Acceptance criteria

- Every module has a version-pinned conformance matrix and no unaccounted standard feature.
- All expert findings are fixed or recorded as explicit product-boundary exclusions with rationale.
- Public documentation makes narrower profiles unmistakable and does not overclaim certification.
- Full, specialized, offline, native, publication, browser, corpus, rendering, and performance evidence
  is green on the declared platform matrix.

## Required tests

- Re-run every predecessor card's required evidence from clean reproducible inputs.
- Perform an independent diff/source/API/docs audit and archive signed-off findings and provenance.
- Verify every task/status/link and no proposed G19 card remains outside the closed archive.

## Validation

Run `./gradlew qualityGate checkAll offlineRepositoryVerification nativeSmoke publicationDryRun
--console=plain`, all separately documented corpus/rendering/browser/performance/platform lanes, and
`git diff --check`.

## Notes

This card is the only G19 card that may change the project-wide wording from “bounded profiles” to an
expert-reviewed feature-completeness claim.
