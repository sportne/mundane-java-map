# G12 — MIL-STD-2525 symbology design

Project index: [DESIGN.md](../DESIGN.md).

## Purpose and sequencing

G12 is the first standards-based symbology gate. It adds a bounded MIL-STD-2525E point-symbol
profile before the general OGC SE and MapLibre style gates. The authoritative baseline is
MIL-STD-2525E with Change 1 incorporated, dated 2025-03-02, as listed by the US Department of
Defense ASSIST catalog:

- <https://quicksearch.dla.mil/qsdocdetails.aspx?ident_number=114934>

This gate does not claim complete MIL-STD-2525 conformance. The standard spans icon-based symbols,
control measures, planning graphics, METOC, signals intelligence, cyberspace, and extensive text and
graphical modifiers. G12 deliberately delivers one useful, testable point-symbol profile and names
every excluded family.

## Existing foundation

G2 supplies immutable marker/composite symbols, packed vector paths, placement, opacity, catalogs,
explicit renderer registration, tolerant render regression, and Native Image resource evidence.
G11-020 through G11-024 supply the eventual binding-owned portrayal and point-label pass. G12 reuses
those contracts; it does not add a military renderer to AWT or a second map styling pipeline.

The production module is `mundane-map-symbology-milstd2525`, not `mundane-map-io-*`: a SIDC is a
symbol identifier and the module resolves it into existing symbol values rather than opening a map
data format. The module is JDK-only and AWT-free. Its types never enter `mundane-map-api`.

## Approved supported profile

G12-001 approved the following deliberately finite profile on 2026-07-23:

- Revision E Change 1 only, represented by SIDC version `15`. Older 15-character identifiers,
  versions other than `15`, and APP-6 translation are unsupported.
- Exact 30-position hexadecimal SIDCs. Input accepts ASCII `0-9`, `A-F`, and `a-f`, canonicalizes
  letters to uppercase, and retains all fourteen information elements across Sets A, B, and C.
- Reality context (`0`) only. Exercise, simulation, restricted-target, and no-strike contexts parse
  and remain inspectable but classify as unsupported.
- Pending (`0`), Unknown (`1`), Assumed Friend (`2`), Friend (`3`), Neutral (`4`), Suspect/Joker
  (`5`), and Hostile/Faker (`6`) standard identities.
- Land Unit (`10`), Land Equipment (`15`), and Activities (`40`) symbol sets only.
- Present (`0`) and Planned/Anticipated/Suspect (`1`) status only. Operational-condition status
  values `2` through `5` parse but classify as unsupported.
- No headquarters/task-force/dummy (`0`) and no amplifying descriptor (`00`). Nonzero values parse
  but classify as unsupported; G12 does not render echelon, mobility, headquarters, task force, or
  feint/dummy amplifiers.
- The finite entity and sector-modifier inventory below.
- No common sector modifiers: Set C positions 21 and 22 must both be `0`.
- The frame-shape position must agree exactly with the symbol set: Land Unit `3`, Land Equipment
  `4`, or Activity/Event `8`.
- Reserved positions 24 through 27 must be `0000`. Positions 28 through 30 are retained, but only
  `000` is supported because this profile performs no GENC, NATO, or country-name lookup.
- Caller-controlled logical-pixel size and ordinary G2 anchor, offset, rotation, opacity, and
  screen-relative/map-relative placement behavior.

The profile intentionally permits a useful distinction between syntax and support. Any 30-position
hexadecimal value parses. `MilitarySymbolProfile.assess` then reports the first unsupported field in
SIDC order. Resolution never silently substitutes another entity. A supported frame may be returned
as a degraded display only when the version, context, identity, symbol set, status, amplifier,
frame-shape, reserved, and country fields are supported and only the entity or sector modifier is
unknown. The result carries a stable warning; strict resolution instead fails.

### Approved entity inventory

Codes are the six positions comprising entity, entity type, and entity subtype. A zero suffix retains
the less-specific approved icon; the resolver does not infer or walk to an unlisted parent.

| Symbol set | Code | Approved name | Normative table |
| --- | --- | --- | --- |
| Land Unit `10` | `121100` | Infantry | Appendix A, table A-XXIII |
| Land Unit `10` | `120500` | Armor/Mechanized | Appendix A, table A-XXIII |
| Land Unit `10` | `130300` | Field Artillery | Appendix A, table A-XXIII |
| Land Unit `10` | `140700` | Engineer | Appendix A, table A-XXIII |
| Land Unit `10` | `161300` | Medical | Appendix A, table A-XXIII |
| Land Equipment `15` | `110100` | Rifle | Appendix A, table A-XXIX |
| Land Equipment `15` | `110200` | Machine Gun | Appendix A, table A-XXIX |
| Land Equipment `15` | `120200` | Tank | Appendix A, table A-XXIX |
| Land Equipment `15` | `140200` | Medical | Appendix A, table A-XXIX |
| Land Equipment `15` | `140800` | Cross Country Truck | Appendix A, table A-XXIX |
| Activities `40` | `120000` | Civil Disturbance | Appendix A, table A-XLVIII |
| Activities `40` | `131500` | Law Enforcement Operation | Appendix A, table A-XLVIII |
| Activities `40` | `140000` | Fire Event | Appendix A, table A-XLVIII |
| Activities `40` | `170103` | Earthquake Epicenter | Appendix A, table A-XLVIII |
| Activities `40` | `170202` | Flood | Appendix A, table A-XLVIII |

### Approved symbol-set sector modifiers

`00` (Unspecified) is valid in both sectors for every supported set. The following nonzero values are
the complete additional inventory. Within one symbol set, the approved sector-1 set and approved
sector-2 set form a Cartesian product, and every pair applies to every approved entity in that symbol
set. For example, Land Unit permits `{00,25,77} × {00,02}`. Cross-symbol-set values and all values
outside the listed sets are unsupported.

| Symbol set | Sector | Value | Approved name | Normative table |
| --- | ---: | --- | --- | --- |
| Land Unit `10` | 1 | `25` | Fire Direction Center | Appendix A, table A-XXIV |
| Land Unit `10` | 1 | `77` | Support | Appendix A, table A-XXIV |
| Land Unit `10` | 2 | `02` | Arctic | Appendix A, table A-XXV |
| Land Equipment `15` | 1 | `13` | Tank-width Mine Plow | Appendix A, table A-XXX |
| Land Equipment `15` | 2 | `06` | Tractor Trailer | Appendix A, table A-XXXI |
| Activities `40` | 1 | `17` | Incident | Appendix A, table A-XLIX |
| Activities `40` | 2 | `04` | Meeting | Appendix A, table A-L |

Inventory rows are explicit immutable Java constants. Table construction rejects duplicate
symbol-set/code keys, invalid paths, role mismatches, unreachable entries, or values outside this
profile before publishing a resolver.

### Approved palettes and frame behavior

Filled symbols use the standard's empirically validated table XV endpoints:

| Identity group | Light-background fill (dark endpoint) | Dark-background fill (light endpoint) |
| --- | --- | --- |
| Pending/Unknown | `#E1DC00` | `#FFFF80` |
| Friend/Assumed Friend | `#006B8C` | `#80E1FF` |
| Neutral | `#00A000` | `#AAFFAA` |
| Suspect | `#FFBC01` | `#FFE599` |
| Hostile | `#C80000` | `#FF8080` |

Light-background frames, icons, and modifiers use opaque `#000000`; dark-background components use
opaque `#FFFFFF`. Caller opacity multiplies all component alpha. Present frames are solid. Planned
frames are represented by explicit bounded path segments so the toolkit-neutral model needs no dash
extension. Pending, Assumed Friend, and Suspect use the approved uncertain-identity segmented frame
and do not add a second planned-status treatment. Frame geometry is normalized to the standard's
virtual bounding octagon and anchored at its center. Land Equipment framing follows the standard's
optional-frame rule: this profile always frames it so identity and supported status remain visible.
Natural-event activity icons are still placed in the Activity/Event frame because this bounded
profile prioritizes consistent identity/status display; this is documented as a profile deviation
and prevents a complete-conformance claim.

Out of profile are control measures and other multipoint tactical graphics; METOC; space, air, sea,
subsurface, SIGINT, and cyberspace symbol sets; text amplifiers; country-name lookup; echelon/task-
force/HQ layout; operational-condition bars; common modifiers; dynamic extensions or Symbol Set
Management Committee updates; legacy revisions; APP-6 equivalence; symbol editing; and automatic
network/catalog updates.

## Public module surface

The intended module-owned API is deliberately small:

```text
MilitarySymbolId
  parse(String) -> MilitarySymbolId
  canonical() -> String
  typed accessors for the fourteen retained elements

MilitarySymbolProblem
  code, field, one-based start/end positions, bounded offending value

MilitarySymbolException
  problem() -> MilitarySymbolProblem

MilitarySymbolSupport
  SUPPORTED | DEGRADED_ENTITY | DEGRADED_MODIFIER | UNSUPPORTED

MilitarySymbolAssessment
  support() -> MilitarySymbolSupport
  problem() -> Optional<MilitarySymbolProblem>

MilitarySymbolProfile
  standard2525EChange1()
  assess(MilitarySymbolId) -> MilitarySymbolAssessment

MilitarySymbolPalette
  lightBackground()
  darkBackground()

MilitarySymbols
  resolveStrict(MilitarySymbolId, MarkerPlacement, MilitarySymbolPalette) -> Symbol
  resolveStrict(MilitarySymbolId, MarkerPlacement, MilitarySymbolPalette, opacity) -> Symbol
  resolveDegraded(MilitarySymbolId, MarkerPlacement, MilitarySymbolPalette)
      -> MilitarySymbolResolution
  resolveDegraded(MilitarySymbolId, MarkerPlacement, MilitarySymbolPalette, opacity)
      -> MilitarySymbolResolution

MilitarySymbolResolution
  symbol() -> Symbol
  problem() -> Optional<MilitarySymbolProblem>
```

G12-003's compatibility review changed the return contract from `MarkerSymbol` to `Symbol`.
`CompositeSymbol` deliberately represents any homogeneous role and therefore does not implement
`MarkerSymbol`; returning the common `Symbol` contract preserves the existing G2 composition model
without inventing a military renderer or an unsafe wrapper. The opacity overloads expose the caller
control required by the approved profile while the three-argument methods retain a convenient
full-opacity default. Other names are fixed for G12 unless a later compatibility review explicitly
amends this decision. Parsing and profile data stay in the module, and successful resolution returns
ordinary immutable API symbols.
There is no
global registry, service loader, classpath scan, reflection, mutable catalog, or AWT callback.

`MilitarySymbolId` stores the canonical 30 ASCII hexadecimal positions in packed immutable form and
decodes fields by fixed offsets. It preserves syntactically valid but unsupported values so parsing,
support classification, and rendering failure remain distinct. Equality and hashing use the exact
canonical identifier.

## Rendering model

Frames, icons, and graphical modifiers are normalized toolkit-neutral vector paths. Resolution
builds an ordered marker-role `CompositeSymbol` using the caller's placement and selected palette.
The module does not rasterize glyphs, ship a font, or install a custom renderer. Frame, icon, and
modifier layers use the existing G2 renderer and opacity rules.

The finite code inventory is explicit generated Java data or checked source constants. A build-time
generator may be used only if its deterministic input is committed and its output is reviewed; no
production runtime resource discovery is allowed. A missing table entry yields a structured module
diagnostic rather than a guessed icon.

G12-003 initially limited graphical resolution to Infantry `121100` without sector modifiers and
used `MIL2525_RENDER_LIMIT` for the remaining approved catalog. G12-004 removes that temporary limit:
all 15 entities and all seven nonzero sector modifiers now have explicit project-authored paths.
The `java-test-fixtures` artifact publishes project-authored first-slice SIDCs to module, AWT, and
later example tests without exposing example constants in the production API.

G12-004 integrates resolved markers with the completed G11 portrayal path by selecting a SIDC from
one explicitly named feature attribute. Parsing or resolution happens once per captured record and
the same result is used for paint and hit testing. Text modifiers remain excluded rather than being
forced into G11's ordinary point-label contract.

The binding adapter is a generated finite `CategoricalSymbolSelector`: it contains exactly 980
canonical SIDCs (15 entities across the approved identities, statuses, and per-set modifier
products), projects only its named text attribute, and omits missing, non-text, malformed, or
unsupported values. The generic selector bound is 1,024 so this finite table fits without a callback,
reflection, or military type leaking into the API. Applications that need a structured problem
parse and resolve the attribute through `MilitarySymbolId` and `MilitarySymbols` before binding.
Every supported SIDC in this finite profile contains only decimal digits, so lowercase normalization
is currently an identity operation; a future profile that admits hexadecimal letters must add an
explicit canonicalizing selector rather than silently relying on exact categorical matching. The
compiled portrayal keys categorical and graduated tables by `SymbolRole`, not by their potentially
large selector value, so feature resolution does not re-hash the 980-rule declaration.

## Limits, diagnostics, and security

SIDCs are fixed-size scalar input, but catalog resolution is still bounded. Construction fixes
maximum composite children, vector commands, and lookup-table entries, and validates all paths before
publication. The resolver accepts no file, URL, XML, JSON, font, image, or arbitrary extension data.

`MilitarySymbolId.parse` throws `MilitarySymbolException` only for null, length, or character
failures. Null uses `MIL2525_SIDC_NULL`, field `sidc`, positions `0..0`, and an empty offending value.
Length uses the same field and `0..0` positions with only the decimal length as its offending value;
character failures identify the one-based position and single character. Profile assessment never
throws for a syntactically valid SIDC. Strict resolution throws the same exception type with the
assessment problem; degraded resolution succeeds only for `DEGRADED_ENTITY` or
`DEGRADED_MODIFIER`. Problems contain no source path or arbitrary input beyond the fixed-width
offending field.

Support assessment uses this exact precedence after syntax validation:

1. version (positions 1–2);
2. context (3);
3. standard identity (4);
4. symbol set (5–6);
5. status (7);
6. headquarters/task-force/dummy (8);
7. amplifying descriptor (9–10);
8. entity (11–16);
9. sector 1 modifier (17–18);
10. sector 2 modifier (19–20);
11. common-modifier selectors (21–22);
12. frame shape and symbol-set agreement (23);
13. reserved positions (24–27);
14. country/entity code (28–30).

Pending, Assumed Friend, and Suspect with status `1` are supported but render the same uncertain
segmented frame as status `0`, because the standard says their status is not displayed. No other
cross-field combination is special. A syntactically valid unsupported entity assesses as
`DEGRADED_ENTITY`; an unsupported modifier assesses as `DEGRADED_MODIFIER`; but either becomes
`UNSUPPORTED` if any later non-degradable field also fails. When both entity and modifier are
unknown, the entity problem wins. Stable codes and fields are:

- `MIL2525_SIDC_NULL`
- `MIL2525_SIDC_LENGTH`
- `MIL2525_SIDC_CHARACTER`
- `MIL2525_SIDC_VERSION`
- `MIL2525_CONTEXT_UNSUPPORTED`
- `MIL2525_IDENTITY_UNSUPPORTED`
- `MIL2525_SYMBOL_SET_UNSUPPORTED`
- `MIL2525_STATUS_UNSUPPORTED`
- `MIL2525_HQ_TASK_FORCE_DUMMY_UNSUPPORTED`
- `MIL2525_AMPLIFYING_DESCRIPTOR_UNSUPPORTED`
- `MIL2525_ENTITY_UNSUPPORTED`
- `MIL2525_MODIFIER_UNSUPPORTED`
- `MIL2525_COMMON_MODIFIER_UNSUPPORTED`
- `MIL2525_FRAME_SHAPE_MISMATCH`
- `MIL2525_RESERVED_NONZERO`
- `MIL2525_COUNTRY_UNSUPPORTED`
- `MIL2525_RENDER_LIMIT`

Malformed syntax, unsupported profile content, and internal inventory inconsistency are separate.
Unsupported values never silently select a semantically different symbol.

## Evidence, legal posture, and conformance wording

The authoritative source is the active Distribution Statement A document downloaded from the DoD
ASSIST Quick Search record `114934` on 2026-07-23:

- title: `MIL-STD-2525E Change 1 — Joint Military Symbology`;
- document date: 2025-03-02;
- SIDC version value: `15`;
- pages: 749;
- ASSIST image token: `5795656`;
- raw SHA-256 of the reviewed transient PDF:
  `3ed1d34f9a391ee48a0178d7aa30f196d3789f4398d2ef38114a47127a3ba142`;
- normalized-text SHA-256:
  `b3265b621b33247af78f656a22f77e86730379fa06c20b02423ef547b0dfb7ac`;
- source catalog:
  <https://quicksearch.dla.mil/qsdocdetails.aspx?ident_number=114934>.

ASSIST stamps each generated PDF page with its retrieval time, so the raw digest identifies the
reviewed transient file but is not expected to be reproducible. With `LC_ALL=C` and Poppler
`pdftotext` 24.02.0, the normalized digest is reproduced exactly by:

```bash
pdftotext -layout mil-std-2525e-change1.pdf - \
  | sed -E '/Downloaded:/d;/Check the source/d;s/[[:space:]]+$//' \
  | sha256sum
```

The POSIX whitespace expression deliberately removes trailing form-feed page breaks as well as
horizontal whitespace. Two independent downloads on 2026-07-23 produced the recorded normalized
digest with that pipeline.

The repository does not redistribute that PDF or extracted standard figures. Code tables must be
hand-transcribed factual identifiers with table citations. Vector paths must be independently
hand-authored from the standard's geometric construction rules and visually compared during
G12-005; they may not be embedded or traced copies of standard artwork. Every committed reference
fixture must be project-authored source text under the repository license and record its generating
SIDC and cited table. G12-004 and G12-005 must add an artifact-level provenance manifest confirming
those facts for every path and fixture. No third-party symbol library, font, SVG bundle, or
screenshot may be imported. Distribution Statement A permits public distribution of the standard;
it does not by itself license derived artwork under the project license.

Hand-built unit fixtures pin field boundaries and parsing. Independent review compares a named
matrix of SIDCs against the authoritative standard without committing the source document.

Rendering tests assert frame class, layer order, normalized bounds, anchor, palette regions, and
modifier presence with tolerances. They do not claim pixel identity or complete 2525E conformance.
The gallery includes supported and degraded/unsupported examples and names the exact supported
profile. Native verification uses literal identifiers and code-owned vectors, with no resource scan.

The only approved public wording is:

> Implements the MundaneJ supported MIL-STD-2525E Change 1 icon-based point-symbol profile for a
> finite Land Unit, Land Equipment, and Activities inventory. It is not a complete
> MIL-STD-2525 implementation or conformance claim.

Documentation must list the supported tables and exclusions near that statement. “MIL-STD-2525
compliant,” “full 2525E,” and equivalent broad claims are prohibited.

## Task sequence

1. G12-001 approves the exact profile, tables, legal posture, and conformance wording.
2. G12-002 creates the working module with canonical SIDC parsing and support classification.
3. G12-003 renders standard-identity frames and a first entity end to end.
4. G12-004 completes the finite land/activity inventory, graphical modifiers, and portrayal binding.
5. G12-005 hardens the inventory and approves the reference matrix and gallery.
6. G12-006 closes publication, consumer, and Linux Native Image evidence.

G13 may not begin until G12-006 closes, making the requested standards order explicit.

## G12-005 completion record

The approved reference evidence is project-authored and deliberately smaller than the 980-entry
runtime combination table. A checksummed 22-row TSV independently names every approved entity and
graphical-modifier row, records its cited Appendix-A family, and contains only factual identifiers
and generated SIDCs. The artifact provenance manifest names that file, its SHA-256 digest, every
filled and segmented frame path, every entity path, every modifier path, and the shared literal
fixture. No standard artwork or third-party symbol asset is committed.

The existing runnable symbol gallery now contains four additional tabs: all fifteen entities,
fourteen identity/status combinations, all seven modifier families plus light-background and
explicit degraded/unsupported diagnostic examples, and a dedicated dark-background palette view.
The unsupported case displays an explicitly labeled gallery diagnostic marker rather than a
military symbol. The window names the bounded MIL-STD-2525E Change 1 point-symbol profile and does
not imply broader conformance. Gallery construction remains on the EDT and routes every resolved
case through the ordinary toolkit-neutral symbol tree and AWT renderer.

Hardening mutates each of the thirty SIDC positions deterministically and pins diagnostic
precedence, strict-resolution failure, bounded problem values, oversized input rejection, and
non-ASCII rejection. Structural inventory/path fingerprints remain independent of production table
iteration. The rendering lane subtracts a separately resolved frame-only baseline to prove every
entity icon contributes bounded center-region ink, then uses bounds, visible-ink, palette-region,
and center-crossing tolerances across the complete identity/status/palette matrix; it does not
compare platform-specific pixels. The G12 reference-matrix/gallery checkpoint was approved under
the maintainer's explicit execution approval on 2026-07-23 with these bounded claims.

## G12-006 completion record

The module remains one JDK-only, AWT-free production artifact with one compile dependency on
`mundane-map-api`. Gradle's shared test-fixtures variants remain available to project tests but are
explicitly excluded from Maven publication. The staged binary inventory contains the twelve
approved package classes, `META-INF/LICENSE`, the manifest, and exactly the declared provenance and
reference-matrix resources. Publication verification also checks the POM, sources, Javadocs,
license bytes, SHA-256 sidecars, and absence of unexpected primary artifacts.

The clean Java 21 consumer resolves only staged Maven coordinates. It parses a literal supported
SIDC, resolves it directly and through the finite portrayal, paints the ordinary symbol through
`MapView`, and pins the unsupported-context diagnostic. The shared native executable uses the same
literal success and failure paths, includes the degraded-entity result, and needs no reflection,
service loading, runtime table discovery, or military-specific Native Image resource metadata.

On 2026-07-23, `publicationDryRun consumerSmoke` passed and printed
`mundane-map consumer smoke: OK`. The actual `nativeSmoke` build and executable passed on Ubuntu
24.04.1 WSL2 Linux x86-64 using GraalVM Community Java 21.0.2; the executable printed
`mundane-map native smoke: OK`. This evidence supports only that Linux configuration. It does not
support Windows, macOS, other architectures, or a complete-standard claim.

The holistic G12 review retains the simplest boundary that meets the profile: one packed parser,
one explicit finite catalog, independently authored toolkit-neutral paths, and the existing generic
portrayal and AWT renderers. No adapter SPI, military renderer, reflection configuration, generated
table framework, or runtime asset discovery is introduced.
