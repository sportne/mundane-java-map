# G19-136 — GeoJSON interoperability and capability closeout

Status: Proposed
Depends on: G19-133, G19-135
Gate: G19
Type: HITL

## Goal

Close the approved RFC 7946, RFC 8142, and controlled legacy-input profile with external interoperability evidence.

## Context

Feature completeness requires more than implementing additional parser branches. The normative matrix, public claims,
limits, diagnostics, examples, dependency boundary, and independent producer/consumer behavior must agree.

## Scope

- Build and review a requirement-by-requirement RFC 7946 and RFC 8142 conformance matrix, including errata and selected
  interoperability recommendations, with evidence or an explicit exclusion for every row.
- Assemble provenance-recorded independent document/sequence/legacy-CRS corpora covering every approved object surface.
- Add malformed, differential, property-based, fuzz, limit-boundary, cancellation, lifecycle, and cleanup campaigns.
- Verify strict/legacy modes, no ambient linked-CRS access, semantic foreign-member retention, deterministic writing,
  transactional output, feature-source projection, CRS, world-wrap, AWT/Vaadin, and native behavior.
- Verify Jackson remains isolated in the optional adapter and complete offline, publication, dependency/license, JPMS,
  Javadoc, package/root documentation, examples, and support matrices.
- Record deliberate exclusions and the non-claims for RFC 8785, TopoJSON, JSON-FG, and source-lexical preservation.

## Out of scope

- Adding a new GeoJSON-family dialect during closeout or broadening the approved legacy-input surface.

## Acceptance criteria

- Every row of `modules/mundane-map-io-geojson-jackson/CAPABILITIES.md` is implemented, tested, or explicitly excluded.
- Multiple independent tools round-trip the complete strict document and text-sequence corpus within documented semantics.
- Public documentation makes no broader claim than the reviewed evidence and all quality/publication/offline gates pass.

## Required tests

- Full normative/corpus/interoperability matrix across documents, sequences, legacy inputs, all limits, and failure paths.
- Native Image, offline repository, publication dry run, API/Javadoc, dependency boundary, viewer/render, and fuzz lanes.

## Validation

Run the module/corpus/interoperability suites, `./gradlew qualityGate --console=plain`, applicable separate offline/native/
publication lanes, and `git diff --check`.

## Notes

HITL checkpoint: an external GeoJSON reviewer approves the requirement matrix, fixtures, deviations, and support wording.
