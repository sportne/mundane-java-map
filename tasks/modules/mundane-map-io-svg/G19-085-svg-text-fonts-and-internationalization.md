# G19-085 — SVG text, registered fonts, and internationalization

Status: Proposed
Depends on: G19-013, G19-080, G19-082, G19-083
Gate: G19
Type: HITL

## Goal

Render and preserve static SVG text with deterministic shaping, positioning, bidi/writing-mode, and
text-path behavior using only explicitly registered fonts.

## Context

The importer rejects all character data. Text is central to map symbols and exported maps, but system
font discovery would violate determinism, native constraints, and the closed-resource policy.

## Scope

- Add `text`, `tspan`, and `textPath` with XML whitespace, language, direction/bidi, writing mode,
  anchors, baselines, x/y/dx/dy/rotate lists, length adjustment, letter/word spacing, decoration, and
  text-on-path placement for the approved SVG 2 profile.
- Define an explicit font catalog, family/style/weight/stretch matching, deterministic fallback,
  missing-glyph behavior, and bounded embedded/catalog font parsing through approved font code.
- Integrate paint, markers where applicable, clipping/masking/opacity, transforms, accessibility,
  and exact/conservative glyph bounds without desktop font APIs.
- Preserve text semantics when possible and require explicit outline conversion only as a caller
  option; never silently substitute system fonts.
- Bound text code points, runs, bidi levels, glyphs, fallback attempts, font bytes/tables, path work,
  and shaping/placement operations.

## Out of scope

- System/browser font discovery, remote web fonts, editable DOM text, animation, and arbitrary HTML.

## Acceptance criteria

- Registered-font text matches pinned independent shapers/renderers under declared glyph/position
  tolerances across representative scripts and directions.
- Missing fonts/glyphs and unsupported shaping have explicit deterministic outcomes.
- Text shaping and text-path layout remain bounded for hostile Unicode/font/path input.

## Required tests

- Latin/combining/Arabic/Indic/CJK/emoji policy, bidi/vertical writing, tspan positioning, anchors/
  baselines/spacing/decoration, textPath, fallback/missing glyphs, malformed/hostile fonts, Unicode
  limits, cancellation, accessibility, and renderer comparisons.

## Validation

Run `./gradlew :modules:mundane-map-io-svg:check --console=plain`, text/font/rendering corpus lanes,
then `./gradlew qualityGate --console=plain` and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact text/font/shaping profile, redistributable font
corpus, script coverage, and visual tolerances before completion.
