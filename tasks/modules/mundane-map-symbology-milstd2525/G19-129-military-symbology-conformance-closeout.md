# G19-129 — Military symbology conformance closeout

Status: Proposed
Depends on: G19-128
Gate: G19
Type: HITL

## Goal

Close the module with exhaustive inventory/provenance proof, independently licensed render comparisons, translation
coverage, expert review, and reconciled edition/capability claims.

## Context

Military-symbology completeness cannot be inferred from a handful of goldens. Every catalog row, applicability rule,
generated source, translation, rendering family, and deliberate edition exclusion needs traceable evidence.

## Scope

- Freeze exact 2525E C1/APP-06E/legacy document hashes, corrigenda, distribution/licensing, inventories, generators,
  mapping tables, fonts/reference sets, and independently reproducible coverage reports.
- Exhaustively verify identifier/catalog/applicability, current point/tactical rendering, modifiers/labels, editing/hit,
  translation outcomes, AWT/Vaadin/SVG/workspace parity, and performance/work ceilings.
- Compare reviewed official examples and independently licensed implementations by family within declared vector,
  geometry, stroke, color, text, placement, and pixel tolerances; record explained differences.
- Complete malformed/fuzz/hostile text/geometry, cancellation/cleanup/concurrency, native/publication/offline/API compatibility,
  examples, diagnostics/no-value-leak, `CAPABILITIES.md`, package/root docs, support tables, and task outcomes.
- Record 2525A/B, national/vendor extensions, restricted artwork, tactical inference, C2 formats, PNG, and 3D exclusions.

## Out of scope

- Declaring operational/doctrinal correctness beyond the selected public symbology standards.

## Acceptance criteria

- Every declared inventory row/applicable combination/mapping has current automated evidence and traceable provenance.
- Independent expert/reference review confirms the exact edition claims and representative output within tolerances.
- No undocumented fallback, unreviewed generated artifact, redistributed restricted source, or unbounded path remains.

## Required tests

- Exhaustive inventories, generated provenance, parser/formatter, translation, point/tactical render/edit/hit/export matrices.
- Independent reference corpus, fuzz/limits/lifecycle/native/publication/offline/API/docs and expert observation record.

## Validation

Run module and all approved rendering/corpus/performance/native/publication/offline lanes, qualityGate, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer/external expert approves source rights/provenance, inventories/mappings, reference licenses,
coverage reports, tolerances/differences, deliberate exclusions, and exact public wording.
