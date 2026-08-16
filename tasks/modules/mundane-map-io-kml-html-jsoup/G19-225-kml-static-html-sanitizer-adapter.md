# G19-225 — KML static HTML sanitizer adapter

Status: Proposed
Depends on: G19-113
Gate: G19
Type: HITL

## Goal

Add a working optional Jsoup-backed adapter that parses and sanitizes the approved static HTML subset used by
KML descriptions and balloons without weakening the JDK-only core module.

## Context

HTML is not XML and a correct tolerant parser/sanitizer is a substantial separate concern. Core preserves
bounded markup and safely renders plain text; useful formatted balloons need an established HTML parser.

## Scope

- Create `mundane-map-io-kml-html-jsoup` only with working public APIs/tests and an exact locked Jsoup dependency,
  license/checksum, publication/offline/native boundary, and architecture rules.
- Pin accepted HTML parsing behavior and an allowlist for static text formatting, lists, tables, safe links,
  catalog-authorized images, selected attributes, URL schemes, and bounded inline presentation values.
- Remove/reject scripts, event attributes, forms, frames, objects/embeds, active CSS, data exfiltration, refresh,
  unsafe URLs, ambient images/fonts/stylesheets, and namespace tricks; expose immutable sanitized output.
- Resolve images only through the KML/KMZ/explicit catalog authority and bound input/output bytes, nodes/depth,
  attributes/text, CSS/value work, URLs/images, diagnostics, and sanitizer cost.
- Make dependency presence explicit; core behavior remains secure escaped text when the adapter is absent.

## Out of scope

- General browser DOM/CSS, JavaScript, forms, media, remote fetching, arbitrary SVG/MathML, and HTML authoring.

## Acceptance criteria

- Allowed malformed/common HTML sanitizes deterministically; disallowed active content cannot survive or fetch resources.
- The optional dependency does not enter JDK-only modules and works through explicit registration only.
- All hostile/deep/large markup and URL/resource cases terminate at prospective limits.

## Required tests

- Element/attribute/CSS/URL/image/table/list/entity/malformed-HTML allowlist matrix and sanitizer goldens.
- XSS/event/script/form/frame/object/CSS/URI obfuscation, parser differentials, bombs, dependency/offline/publication tests.

## Validation

Run the adapter check and KML integration tests, offline/publication/dependency lanes,
`./gradlew qualityGate --console=plain`, and `git diff --check`.

## Notes

HITL checkpoint: a maintainer approves the exact allowlist, dependency/license, security corpus, and sanitized visuals.
