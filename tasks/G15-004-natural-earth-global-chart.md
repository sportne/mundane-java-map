# G15-004 — Natural Earth global chart

Status: Proposed
Depends on: G15-001, G5-010
Gate: G15
Type: HITL

## Goal

Bundle a small, legally redistributable Natural Earth 1:110m land dataset and display it through the
existing shapefile/CRS/MapView stack as the live-track example's global chart.

## Context

Natural Earth publishes `ne_110m_land` and identifies its site data as public domain. G5 already
provides bounded shapefile reading, retained/recognized PRJ, rendering, corpus practice, and native
resource discipline.

## Scope

Obtain the official versioned `ne_110m_land` archive; retain only required sidecars under the
example resources; add provenance, retrieval URL/date, upstream version, terms reference/snapshot,
hashes, and any deterministic preparation recipe; open the data with explicit limits/CRS handling;
render and fit the full-world chart in the example with source lifecycle and diagnostic visibility.

## Out of scope

Runtime downloads, coastlines/borders/labels, Natural Earth raster data, reprojection libraries,
format changes, editing, general dataset packaging, track rendering, or a new map-data module.

## Acceptance criteria

- Every bundled byte has an exact manifest entry, cryptographic hash, upstream identity, and approved
  redistribution record.
- The chart opens through `mundane-map-io-shapefile`, recognizes or explicitly supplies the intended
  CRS, transforms through the existing registry, fits the world, and renders land predictably.
- Missing/corrupt resource behavior is stable and the source is closed with the example.
- A maintainer visually approves land coverage, antimeridian/world framing, and useful contrast for
  later tracks.

## Required tests

Manifest/hash tests, resource-open/query tests, recognized-CRS and corrupt/missing-resource tests,
offscreen full-world rendering invariants, source cleanup, and manual pan/zoom/fit review.

## Validation

```bash
./gradlew :examples:live-track-stress:check --console=plain
./gradlew renderRegression --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **bundled Natural Earth provenance/redistribution and full-world chart visual
approval**. Do not infer approval merely from the public-domain statement; record the exact artifact.
