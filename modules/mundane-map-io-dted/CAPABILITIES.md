# DTED adapter capability intent

`mundane-map-io-dted` is the project's JDK-only adapter for bounded Digital Terrain Elevation Data
(DTED) ingestion, regional terrain access, and create-new-cell interchange. Its approved completion
target is a strict MIL-PRF-89020B Level 0/1/2 reader and writer, not a general military-product
production or certification system.

The writer is deliberately builder-driven. It supplies deterministic, conservative defaults for
metadata that may honestly be unknown or derived, while requiring the caller to provide facts that
cannot safely be invented. Generated files may be structurally conforming and interoperable without
being certified, quality-controlled, classified, or approved for an operational distribution
program.

The root README describes released behavior. Target rows below become release claims only when the
corresponding G19 cards close.

## Standards and product boundary

Normative format baseline: [MIL-PRF-89020B, Digital Terrain Elevation Data
(DTED)](https://quicksearch.dla.mil/qsDocDetails.aspx?ident_number=110830), 23 May 2000, including
the active administrative/validation notices listed by ASSIST. Interoperability evidence also tracks
the NATO STANAG 3809 exchange-format relationship, but does not claim access to or conformance with
non-public requirements that are not included in the pinned project evidence.

| Surface | Released profile | Approved completion target | Deliberate exclusions |
| --- | --- | --- | --- |
| Cell reader | Strict eager one-degree Level 0/1/2 cell decoding | Complete declared fixed-record metadata plus eager and bounded random-access windows | Repair of malformed products and undisclosed proprietary variants |
| Regional access | One cell at a time | Explicit catalog, deterministic level/overlap selection, seam-aware windows, and bounded mosaics | Ambient recursive/network discovery and an unbounded global terrain database |
| Cell writer | None | Builder-driven, transactional creation of standard one-degree Level 0/1/2 cells | In-place mutation, optical-disc volume authoring, gazette/directory products, and certified production workflow |
| Terrain preparation | Caller supplies a compatible grid | Exact-grid validation; callers may use core reprojection/warping before writing | Implicit reprojection, resampling, datum conversion, void filling, or accuracy fabrication |
| Product assurance | Parser/corpus structural evidence | Round-trip and independent-reader/writer interoperability for the declared profile | Claiming NGA/NATO acceptance, source-data fitness, security authorization, or positional-accuracy certification |

## Reader and metadata matrix

| Record or behavior | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| UHL | Validates sentinel, origins, intervals, vertical accuracy grammar, security code, reference, counts, multiple-accuracy flag, and reserved bytes; retains only grid essentials | Immutable field-level values with explicit blank/unknown semantics and cross-record reconciliation | G19-060 |
| DSI | Validates the standard fixed frame and strict supported grid/product/datum subset; retains only level, grid, and partial state | Preserve classification/control/handling, references, edition/maintenance, producer/specification, datum, collection, corners, orientation, partial state, producer-use, and comments according to the pinned field matrix | G19-060 |
| ACC | Validates the fixed prefix, SRTM marker, and empty subregion profile; discards accuracy values | Preserve four accuracy values, marker, and bounded standard subregion descriptions; report unsupported edition data explicitly | G19-060 |
| Data records | Validates sentinel, sequence/counts, signed-magnitude elevations, voids, and checksums | Same strict validation through eager and random-access paths, with exact profile/window accounting | G19-061 |
| Edition variation | Requires `PRF89020B`, supported WGS84/vertical-datum values, north-up standard cells, and Level 0/1/2 grids | Pin accepted padding, declared B notices, SRTM producer fields, datum spellings, multiple-accuracy records, and legitimate producer variation without relaxing fixed-frame validation | G19-060, G19-066 |
| Regional semantics | None | Explicit cell identity, level selection, overlap precedence, shared-edge ownership, missing-cell policy, void propagation, cancellation, and cache ownership | G19-062, G19-063 |

## Builder and writer policy

The public builder targets one new standard one-degree cell per transaction. It accepts an immutable
compatible elevation grid or an explicitly bounded sample provider. Before opening a destination it
preflights origin, level, standard latitude-zone dimensions/intervals, WGS84 horizontal placement,
metre sample units, vertical datum, value range, void policy, metadata encodability, output length,
and all work/allocation limits.

| Builder value | Default/derivation policy | Why |
| --- | --- | --- |
| Southwest origin and DTED level | Required explicitly, or derived only when the supplied grid proves one exact standard cell and one unambiguous level | Choosing a cell or resolution changes the product and cannot be guessed |
| Horizontal datum, corners, orientation, intervals, and counts | Fixed/derived from the approved WGS84 one-degree Level 0/1/2 profile | These are structural consequences, not user prose |
| Vertical datum | Required explicitly unless future neutral metadata carries an exact supported value | Labelling ellipsoidal heights as MSL or EGM96 would be a material false claim |
| Security classification | `U` (unclassified), with blank control/release/handling fields | Conservative useful default; non-`U` markings require an explicit coherent builder value set |
| Product specification | `PRF89020B` and the pinned amendment/specification representation | The writer implements this declared profile |
| Edition and match/merge | Edition `01`, match/merge version `A`; caller may override with validated values | Valid initial-product defaults without implying prior maintenance history |
| Maintenance/match/merge/compilation dates and descriptions | Standard unknown/not-applicable representation unless supplied | Wall-clock defaults harm reproducibility and may invent provenance |
| Producer code | A documented library producer identifier; caller may replace it | Truthfully identifies the encoding implementation without claiming the terrain source |
| Unique reference, collection system, producer-use, and comments | Blank unless supplied | Unknown free text is preferable to fabricated provenance |
| Accuracy fields and subregions | `NA`, no multiple-accuracy subregions unless explicitly supplied | Accuracy must never be inferred from grid spacing or sample values |
| Partial-cell indicator and SRTM marker | Derived from the declared void/product profile; SRTM is never inferred merely from cell dimensions | Maintains cross-record consistency without inventing source lineage |
| Checksums, record sequence, signed magnitude, padding, and reserved bytes | Always generated canonically | Mechanical format obligations belong to the writer |

Builder overrides are immutable, bounded, ASCII/field-width validated, and reject internally
inconsistent security, accuracy, SRTM, datum, edition, and partial-cell combinations. The builder does
not offer a generic raw-header escape hatch.

## Publication and interoperability contract

- Encoding writes to a private sibling temporary file, flushes it, reopens it through the production
  reader, and publishes it atomically where the filesystem permits. Existing targets require an
  explicit replacement policy; cancellation or failure preserves the prior file and removes staging.
- Sample conversion is exact and checked. Non-integral metre values, values outside the standard
  signed-magnitude range, negative zero ambiguity, unsupported voids, and incompatible grids are
  rejected rather than rounded, clipped, filled, or resampled.
- Public metadata distinguishes absent, unknown, not applicable, and present values where the fixed
  record does. Security markings are descriptive data only and never grant access or permission.
- The conformance lane round-trips every supported level/profile, has independent tools read emitted
  cells, reads independently emitted cells, mutates every fixed-field family, and records fixture
  provenance and hashes.
- G19-066 closes the module only when package Javadocs, this matrix, root support wording, stable
  diagnostics, corpus evidence, and observed implementation behavior agree.
