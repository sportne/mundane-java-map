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

## Supported profile proposal

The profile checkpoint in G12-001 must approve the exact code tables and redistributable evidence
before implementation. The proposed baseline is:

- Revision E Change 1 only; older 15-character identifiers and APP-6 translation are rejected.
- Exact 30-position hexadecimal SIDCs, normalized to uppercase, retaining all fourteen information
  elements across Sets A, B, and C.
- Icon-based point symbols from finite committed inventories for Land Unit, Land Equipment, and
  Activities symbol sets.
- The standard-identity frames, present/planned status treatment, amplifying descriptors, entity,
  entity type/subtype, and graphical sector modifiers required by that finite inventory.
- One light-background and one dark-background palette whose actual colors remain within the
  standard's allowed ranges and are pinned by the profile decision.
- Caller-controlled logical-pixel size and ordinary G2 marker placement.
- Stable fallback that can retain a recognized frame while reporting an unrecognized entity only
  when the approved standard rule permits that degraded display.

Out of profile are control measures and other multipoint tactical graphics; METOC; space, air, sea,
subsurface, SIGINT, and cyberspace symbol sets; text amplifiers; country-name lookup; echelon/task-
force/HQ text layout; dynamic extensions or Symbol Set Management Committee updates; legacy
revisions; APP-6 equivalence; symbol editing; and automatic network/catalog updates.

## Public module surface

The intended module-owned API is deliberately small:

```text
MilitarySymbolId
  parse(String) -> MilitarySymbolId
  canonical() -> String
  typed accessors for the fourteen retained elements

MilitarySymbolProfile
  standard2525EChange1()
  supports(MilitarySymbolId) -> boolean

MilitarySymbolPalette
  lightBackground()
  darkBackground()

MilitarySymbols
  resolve(MilitarySymbolId, MarkerPlacement, MilitarySymbolPalette) -> MarkerSymbol
```

Names may be refined at G12-001, but the ownership and direction may not: parsing and profile data
stay in the module, and successful resolution returns ordinary immutable API symbols. There is no
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

G12-004 integrates resolved markers with the completed G11 portrayal path by selecting a SIDC from
one explicitly named feature attribute. Parsing or resolution happens once per captured record and
the same result is used for paint and hit testing. Text modifiers remain excluded rather than being
forced into G11's ordinary point-label contract.

## Limits, diagnostics, and security

SIDCs are fixed-size scalar input, but catalog resolution is still bounded. Construction fixes
maximum composite children, vector commands, and lookup-table entries, and validates all paths before
publication. The resolver accepts no file, URL, XML, JSON, font, image, or arbitrary extension data.

Diagnostics use stable codes with canonical SIDC and field position/value details where safe:

- `MIL2525_SIDC_LENGTH`
- `MIL2525_SIDC_CHARACTER`
- `MIL2525_SIDC_VERSION`
- `MIL2525_SYMBOL_SET_UNSUPPORTED`
- `MIL2525_ENTITY_UNSUPPORTED`
- `MIL2525_MODIFIER_UNSUPPORTED`
- `MIL2525_RENDER_LIMIT`

Malformed syntax, unsupported profile content, and internal inventory inconsistency are separate.
Unsupported values never silently select a semantically different symbol.

## Evidence and conformance wording

Hand-built unit fixtures pin field boundaries and parsing. Reference fixtures must be transcribed or
generated with recorded provenance and redistribution permission; standard pages or figures are not
copied into the repository merely because the document is publicly downloadable. Independent review
compares a named matrix of SIDCs against the authoritative standard.

Rendering tests assert frame class, layer order, normalized bounds, anchor, palette regions, and
modifier presence with tolerances. They do not claim pixel identity or complete 2525E conformance.
The gallery includes supported and degraded/unsupported examples and names the exact supported
profile. Native verification uses literal identifiers and code-owned vectors, with no resource scan.

## Task sequence

1. G12-001 approves the exact profile, tables, legal posture, and conformance wording.
2. G12-002 creates the working module with canonical SIDC parsing and support classification.
3. G12-003 renders standard-identity frames and a first entity end to end.
4. G12-004 completes the finite land/activity inventory, graphical modifiers, and portrayal binding.
5. G12-005 hardens the inventory and approves the reference matrix and gallery.
6. G12-006 closes publication, consumer, and Linux Native Image evidence.

G13 may not begin until G12-006 closes, making the requested standards order explicit.
