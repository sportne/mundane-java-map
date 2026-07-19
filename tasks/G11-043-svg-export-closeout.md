# G11-043 — SVG export closeout

Status: Proposed
Depends on: G11-024, G11-042
Gate: G11
Type: HITL

## Goal

Close canonical vector-map export with public documentation, structural/render evidence, manual
browser interoperability, staged-consumer verification, and representative Linux Native Image proof.

## Context

G11-042 supplies hardened export behavior and G11-024 supplies the final label metric/native/
publication boundary. G11-005 requires browsers only as tolerant manual evidence, not an automated
SVG-rendering oracle.

## Scope

Complete public Javadocs and architecture allowlists; add tolerant render-structure fixtures/tests;
record a Firefox/Chromium comparison of one checked-in expected export; extend the shared native
executable, SVG publication metadata, and clean offline consumer with programmatic encode/write.

## Out of scope

Pixel hashes, cross-platform glyph identity, a second SVG renderer dependency, AWT capture in the
headless native scenario, Windows/macOS Native Image claims, additional export formats, and release
publication.

## Acceptance criteria

- Javadocs state the detached picture boundary, supported/rejected matrix, canonical output,
  ownership, limits, diagnostics, cancellation, and atomic-file policy.
- Structural and tolerant rendering evidence covers broad page/background/color regions, geometry
  bounds/order, holes, markers, arrowheads, hatches, and label envelopes without glyph pixel identity.
- A named Firefox and Chromium run records browser/OS versions and agrees with the live example on
  the approved broad properties.
- Linux Native Image and the clean staged Java 21 consumer encode and atomically write one
  resource-free snapshot and verify one stable unsupported-symbol diagnostic.

## Required tests

Javadoc/doclint, architecture, exact-byte/structure and rendering regression, manual two-browser
comparison, JVM/native encode/write/diagnostic, publication metadata, and clean offline consumer.

## Validation

```bash
./gradlew renderRegression --console=plain
./gradlew nativeSmoke --console=plain
./gradlew offlineRepositoryVerification publicationDryRun consumerSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 SVG export browser and Linux Native Image review**. The maintainer records the
named Firefox/Chromium comparison and approves the exact Linux/support statement before closeout.
