# Joint military symbology capability intent

`mundane-map-symbology-milstd2525` is the project's JDK-only, toolkit-neutral implementation of
current U.S. and NATO joint military symbology for map display, validation, catalog lookup, tactical-
graphic construction, editing, and explicit cross-edition translation. The authoritative completion
targets are MIL-STD-2525E Change 1 and NATO APP-06 Edition E Version 1.

The module also reads, validates, and loss-audits translation from MIL-STD-2525D Change 1,
MIL-STD-2525C, and APP-06 Edition D Version 1. Current-edition identifiers are emitted by default;
legacy output is permitted only when every selected field/graphic is losslessly representable in the
requested edition. Earlier 2525A/B editions are deliberately excluded absent a concrete future need.

This is a symbology implementation, not a command-and-control data model, tactical decision engine,
order-of-battle database, classification system, or authorization to infer military facts. The root
README describes released behavior. Target rows become release claims only as their G19 cards close.

## Standards and provenance boundary

| Standard/profile | Approved use | Claim boundary |
| --- | --- | --- |
| [MIL-STD-2525E Change 1](https://quicksearch.dla.mil/qaDocDetails.aspx?ident_number=114934), 2 March 2025 | Authoritative U.S. SIDC, point-symbol, amplifier/modifier, and tactical-graphic inventory/display rules | Full declared distribution-A/public profile, subject to verified source/artwork provenance and recorded corrigenda |
| NATO APP-06 Edition E Version 1 | Authoritative NATO joint military symbology and differences from 2525E | Full accessible approved profile with exact provenance; differences are modeled, not silently treated as aliases |
| MIL-STD-2525D Change 1 and MIL-STD-2525C | Legacy input validation, catalog identification, explicit directional translation, and lossless-only legacy output | No claim that legacy and E semantics are equivalent; A/B excluded |
| NATO APP-06 Edition D Version 1 | Legacy NATO input/translation and lossless-only legacy output | Superseded edition supported for interchange, not used as the current portrayal authority |
| ISO 3166-1 country/entity codes where referenced by the selected edition | SIDC country/entity field validation and naming | Pinned code-list snapshot with explicit unknown/reserved/update policy, not an ambient online registry |

- Every generated inventory/table/path/template records source edition, page/table/row or equivalent stable
  citation, extraction/normalization tool version, reviewed source hash, generator hash, generated-output hash,
  distribution/licensing status, and manual-review record.
- Production uses immutable generated data and explicit registration. It never scans PDFs/resources/classpaths,
  reflects over handlers, downloads standards, or chooses a provider implicitly.
- Restricted or unverified artwork/data is never committed or redistributed. Where rules can lawfully be
  reimplemented, project-authored geometry retains traceable rule provenance rather than copied artwork.

## Identifier and catalog matrix

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Editions | 30-position 2525E Change 1 syntax, small strict profile | Edition-tagged 2525E C1, APP-06E, 2525D C1, APP-06D, and 15-character 2525C identifier values/parsers/formatters | G19-121 |
| SIDC fields | Many fields syntactically exposed but most values rejected | Complete version/context/scheme, identity, symbol set/battle dimension, status, HQ/task force/feint/dummy, echelon/amplifier, entity/function, modifiers, frame shape, country/entity, order-of-battle, and edition-specific fields | G19-121 |
| Catalog | 15 entities, 7 sector modifiers | Exhaustive valid/reserved/deprecated point and tactical entries, hierarchy, names/descriptions, geometry kind, control-point rules, applicable fields/modifiers, replacements, and cross-edition metadata | G19-120, G19-122 |
| Lookup | Exact small table | Lookup by canonical identifier, edition, code, hierarchy, normalized name/keyword, symbol set, geometry kind, and applicability with deterministic ordering and bounded result/work limits | G19-122 |
| Encoding | Canonical 30-position string only | Deterministic current and lossless legacy serialization with strict representability preflight and no silent defaulting | G19-121, G19-127 |

## Point-symbol matrix

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Symbol sets | Land Unit, Land Equipment, Activities examples | Every point-rendered symbol set/entity/type/subtype in the approved 2525E/APP-06E inventories, including framed/unframed and special-layout families | G19-123 |
| Frames/identity/status | Narrow identity/status/frame subset | Complete standard identity frames, status/presence, alternate frame shapes, anticipated/planned styling, context and edition differences | G19-123 |
| Icons | Project-authored paths for 15 entities | Complete deterministic vector icon layers, fills/knockouts, installation/mobility/top/inside behavior, and edition-specific variants | G19-123 |
| Graphic amplifiers | Seven sector examples | Complete echelon, HQ/task force/feint/dummy, mobility, auxiliary equipment, direction-of-movement, operational condition, engagement bar, sonar confidence, and applicable graphic amplifiers | G19-124 |
| Text modifiers | Not modeled | Complete applicable text fields, formatting, validation, placement zones/order, collision/fallback, Unicode/font policy, country/order-of-battle, and multi-line behavior | G19-124 |
| Palettes | Small approved palette | Standard light/dark/medium fill/line palettes, monochrome/unframed options, alpha/background policy, accessibility/contrast modes only where standards permit | G19-123, G19-129 |

## Tactical-graphics matrix

| Area | Approved completion target | Card |
| --- | --- | --- |
| Inventory/control points | Every 2525E/APP-06E tactical graphic/control measure with min/max/exact point count, ordering, closed/open, parameter, and edition rules | G19-125 |
| Geometry construction | Lines, areas, axes, boundaries, corridors, sectors, range fans, obstacles, fire-support, maneuver, airspace, maritime/subsurface, CBRN, and other catalogued rule families using geodesic/projected algorithms as specified | G19-125 |
| Decoration/fill | Arrowheads, teeth, ticks, arcs, fans, echelon/identity marks, phase/boundary decorations, patterns, fills, integral text, and scale-dependent detail | G19-126 |
| Labels/modifiers | Standard tactical labels, locations, repetitions, orientation, leader/offset rules, collision, and edition differences | G19-126 |
| Editing | Immutable control-point/parameter model, validation, deterministic handles, insert/move/delete constraints, preview/commit, undo integration, and stale-generation rejection | G19-126 |
| Spatial behavior | Projection/CRS, dateline/world wrap, poles, geodesic distances/azimuths, clipping, hit testing, selection, export, and declared numerical/visual tolerances | G19-125, G19-126 |

## Translation contract

- Translation is directional and produces a structured result: exact, exact with canonical normalization,
  conditionally representable, lossy with enumerated losses, ambiguous with candidates, or unmapped with reason.
- Exact mappings round-trip to the same edition semantics. Lossy/ambiguous mappings never proceed without an
  explicit caller policy, and the result retains source edition/identifier plus the complete loss audit.
- Point entities, tactical graphics, standard identities/statuses, amplifiers, modifiers, country/entity codes,
  geometry/control points, parameters, and portrayal differences are assessed separately.
- Output formatters preflight all edition constraints before emitting text. They never truncate, substitute a
  visually similar symbol, or clear unsupported fields silently.

## Rendering and export contract

- Resolution produces immutable toolkit-neutral vector symbols/tactical-graphic portrayal and structured label/
  editing metadata. AWT, Vaadin, and deterministic SVG export consume the same authoritative geometry/order.
- Coordinate-free point-symbol output can be requested at explicit size/palette/options for UI legends and
  catalogs. Raster images may be rendered in memory by AWT; this module does not add a PNG encoder or sprite format.
- Rendering is deterministic for the same edition, SIDC, modifiers, control points, scale, viewport, registered
  fonts, and options. Unsupported/invalid/over-budget input fails before partial symbol/scene publication.
- Work is bounded prospectively by catalog results, text/code points, modifier count, path commands, control points,
  generated segments, geodesic operations, label candidates, primitives, pixels, hit/edit work, and owned bytes.

## Deliberate exclusions

- MIL-STD-2525A/B, unapproved national/vendor extensions, restricted/unverified artwork, tactical inference,
  C2 message/order formats, entity tracking, doctrinal validation beyond symbol applicability, and automatic
  conversion of arbitrary military data into symbols.
- Approximate legacy output, visually similar substitution, implicit standard-edition detection when ambiguous,
  runtime PDF parsing, system fonts, hidden native/rendering dependencies, and general 3D battlefield visualization.

## Completion evidence

- Exhaustively enumerate every declared inventory row and valid/applicable field combination from reproducible
  generated tables; verify reserved/deprecated paths and table provenance/checksums.
- Compare point/tactical output against independently licensed/current reference implementations and reviewed
  official examples within per-family geometry, stroke, color, text, and placement tolerances.
- Exercise cross-edition translation coverage, parser/formatter round trips, malformed/hostile input, geometry/
  text/work limits, rendering/edit/hit/world-wrap, native/publication/offline, and API/documentation inventories.
- G19-129 closes the module only when this matrix, exact edition statements, implementation, generated provenance,
  public docs, diagnostics, examples, and external expert evidence agree.
