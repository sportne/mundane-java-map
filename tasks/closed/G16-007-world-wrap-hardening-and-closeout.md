# G16-007 — World-wrap hardening and closeout

Status: Complete
Depends on: G16-002, G16-004, G16-005, G16-006
Gate: G16
Type: HITL

## Goal

Close the horizontal world-wrap capability with hostile/boundary evidence, performance and Native
Image verification, coherent public documentation, staged-consumer proof, and maintainer-approved
continuous pan/zoom behavior.

## Context

G16-002 through G16-006 deliver dense-example, vector, interaction/edit, and global-raster slices.
This task integrates their shared `MapView` behavior and proves that explicit wrapping remains
bounded, optional, responsive, and compatible with existing non-wrapped consumers.

## Scope

Audit public API/Javadocs and diagnostics; add deterministic fuzz/boundary cases for copy arithmetic,
query splitting, seam geometry, interaction, and raster planning; extend rendering regression,
performance scenarios, architecture/native smoke, publication staging, and the clean consumer;
manually review G15, measurement, point-edit, mixed-vector, raster, and export examples across
several east/west crossings and zoom levels; record final limits and support wording in design,
roadmap, and README material.

## Out of scope

Portable latency SLAs; automatic wrap discovery; arbitrary topology repair; globe/polar wrap;
external adapters; native acceleration; or implementing open tile/container tasks.

## Acceptance criteria

- All supported vector, interaction/edit, export, and compatible raster paths remain continuous and
  deterministic through multiple east/west dateline crossings.
- Default non-wrapped behavior and local layers remain compatible, with no hidden query, copy, or
  cache overhead beyond the approved fixed check.
- Precision, copy, query, coordinate, label/export, allocation, cancellation, and lifecycle limits
  have direct stable-diagnostic tests and no partial publication.
- Performance evidence reports wrapped work/latency without adding a wall-clock quality threshold or
  native optimization.
- Linux Native Image exercises toolkit-neutral success and failure paths; staged artifacts build a
  clean Java 21 consumer using explicit wrap configuration.
- The maintainer approves visual seams, continuous pan/zoom ergonomics, limits, Javadocs, and honest
  support wording.

## Required tests

Unit/integration/property-style boundary tests; malformed/excessive inputs; rendering regression;
G15 10k/1m presentation evidence; wrapped/non-wrapped performance scenarios; architecture, native,
publication, consumer, Javadoc/doclint, lifecycle, and manual example review.

## Validation

```bash
./gradlew qualityGate --console=plain
./gradlew renderRegression --console=plain
./gradlew performanceEvidence --console=plain
./gradlew nativeSmoke --console=plain
./gradlew publicationDryRun consumerSmoke --console=plain
git diff --check
```

## Notes

HITL checkpoint: **continuous east/west pan/zoom visual approval and public world-wrap support
statement**. Linux is the required Native Image evidence lane; broader platform claims require their
own evidence.
