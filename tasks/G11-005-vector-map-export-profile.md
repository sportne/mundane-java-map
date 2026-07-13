# G11-005 — Vector map export profile

Status: Proposed
Depends on: G10-001, G11-002
Gate: G11
Type: HITL

## Goal

Approve one canonical static SVG 1.1 map-export profile and a detached toolkit-neutral snapshot
boundary that preserves the visible vector map without coupling the writer to AWT or live sources.

## Context

G10-001 establishes the secure marker-SVG importer and published `mundane-map-io-svg`; G11-002
establishes authoritative geometry paint order and already measured/placed point labels. G2 supplies
the built-in vector symbol algorithms and G7 keeps optimized screen paths paint-only. The
authoritative export and whole-design closeout are in
`design/G11-editing-styling-persistence-adapters-export.md`.

## Scope

Define immutable API-owned vector-export snapshot/limit values, `MapView`-owned synchronous capture, canonical SVG
encoding in the existing SVG module, supported geometry/symbol/text behavior, exact exclusions,
ordering, clipping, opacity, hatch, number/XML rules, limits, cancellation, stable diagnostics,
atomic replacement, manual browser evidence, Native Image/publication expectations, and G11-040
through G11-043 vertical slices. Close G11 and re-evaluate G0–G11 for simplicity and boundary
coherence.

## Out of scope

Production code/modules/tasks for later slices; map import/round trip; raster/elevation export;
`RasterIconSymbol`, legacy/custom symbols, renderer callbacks, image embedding or silent
rasterization; editing/interaction/tool overlays; CRS/georeferencing or source metadata; PDF,
printing, animation, CSS, scripts, arbitrary SVG, fonts/glyph outlines, and a test-only SVG renderer.

## Acceptance criteria

- **G11 canonical static SVG vector-map export profile approval** selects SVG 1.1 and approves the
  snapshot/capture ownership, exact supported/rejected matrix, text/font policy, canonical grammar,
  fixed and configurable bounds, diagnostic/cancellation policy, and no-fallback behavior.
- API snapshot values contain only detached screen geometry, supported immutable symbols, stripped
  placed-label data, page dimensions/background/view frame, and traversal ordinals; `MapView` owns live capture and
  `mundane-map-io-svg` remains AWT-free while gaining only its approved core algorithm dependency.
- Identical valid snapshots produce byte-identical UTF-8 output with exact order, clipping, numeric,
  color/opacity, XML escaping, and local ordinal-ID rules; unsupported content rejects the whole
  operation even when transparent.
- File output validates/materializes first and uses forced same-directory temporary output plus
  atomic replacement with no non-atomic fallback; limits, cancellation, and fault cleanup are exact.
- G11-040 through G11-043 deliver values/solid encoding, real AWT capture and complete built-ins,
  hardening, and Javadocs/native/publication/consumer/manual closeout before an export claim exists.
- The gate and whole-design audits retain one-way module dependencies, explicit registries, JDK-only
  algorithmic modules, no empty speculative modules, and no generic renderer/export/plugin framework.

## Required tests

No production tests in this design task. Specify later immutable snapshot/value and ownership tests;
exact canonical-byte and equal-snapshot determinism tests; secure structural XML assertions; every
geometry/symbol/order/opacity/text case, including hatch outlines/clips, positive-candidate hatches
whose corner-only intersections emit no segment, and fixed paint/capture font metrics; exact/one-over
semantic byte inventories, prospective fixed numeric-token reservations, limit, cancellation
(including file checkpoints),
unsupported-profile, hostile-value, and injected atomic-file failure tests;
authoritative-versus-optimized geometry checks; manual Firefox/Chromium comparisons using broad geometry/color regions; and existing native,
publication, consumer, architecture, and Javadoc lanes. Do not add a second SVG renderer for tests.

## Validation

```bash
./gradlew :modules:mundane-map-api:check :modules:mundane-map-core:check :modules:mundane-map-awt:check --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

HITL checkpoint: **G11 canonical static SVG vector-map export profile approval**. The maintainer
approves the SVG target, API/AWT/I/O ownership, effect and text matrices, canonical serialization,
limits/failures/atomic replacement, four-slice graph, and G11/G0–G11 simplicity closeout before
G11-040 is created. Export extends the G10 SVG artifact; it creates no new module.
