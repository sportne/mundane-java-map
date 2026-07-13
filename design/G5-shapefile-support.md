# G5 — Read-only shapefile support design

Project index: [DESIGN.md](../DESIGN.md).

## Supported Level 1 profile

### Supported-profile decision (G5-001)

#### Primary references and scope

The Level 1 profile is based on the
[ESRI Shapefile Technical Description (July 1998)](https://downloads.esri.com/support/whitepapers/ao_/shapefile.pdf),
the Borland Developer Support layouts reproduced in
[OGC CDB Annex C, Shapefile dBASE III guidance](https://docs.ogc.org/bp/16-070r3/16-070r3.pdf), and
Esri's documented [shapefile code-page behavior](https://support.esri.com/en-us/knowledge-base/read-and-write-shapefile-and-dbase-files-encoded-in-var-000013192).
Those references describe a wider and sometimes implementation-dependent ecosystem. This section is
the normative `mundane-java-map` subset when a reference permits more than one behavior.

G5 is read-only. The eventual `mundane-map-io-shapefile` module directly implements G4's
`FeatureSource`, uses only JDK and project modules, and contains every path, channel, endian reader,
format limit, and format diagnostic. No `Path`, channel, DBF-specific value, WKT parser type, or future
third-party adapter type enters `mundane-map-api`. Consumers explicitly call the format opener; there
is no provider interface, `ServiceLoader`, classpath scan, reflection, mutable global registry, or
automatic format detection. The format module never depends on AWT.

This task adds no production module or API. The following contracts are designs for the owning G5
implementation slices, not empty scaffolding to land with G5-001.

#### Dataset name and component selection

The eventual opener takes an explicit `SourceIdentity`, `.shp` `Path`, and immutable format open
options. It never derives a diagnostic source ID from a path. The final path element must end in ASCII
case-insensitive `.shp`; the preceding lexical stem is otherwise exact. Sidecars must be siblings with
that exact stem and one of the finite lower- or upper-case names `.shx`/`.SHX`, `.dbf`/`.DBF`,
`.cpg`/`.CPG`, or `.prj`/`.PRJ`. Mixed-case extensions and case-insensitive stem guessing are outside
Level 1. This is a fixed set of direct path probes, not directory scanning.

When both case variants identify different files, opening terminates with
`SHAPEFILE_COMPONENT_AMBIGUOUS`; aliases that `Files.isSameFile` proves identify one file collapse to
one component. Component existence/open and identity checks are performed once in an opening
transaction. A component that appears or changes after that snapshot is not silently substituted.
Failure while deciding whether two candidates identify one file is the same terminal ambiguity rather
than permission to pick one. The source does not lock files against external mutation; later size,
truncation, or I/O disagreement follows the component policy below.

Component policy after its owning implementation task has landed is:

| Component | Level 1 policy |
| --- | --- |
| SHP | Required. Missing, unreadable, or malformed input terminates opening. |
| SHX | Optional derived address index. Missing emits `SHAPEFILE_SHX_MISSING` and uses sequential SHP access. A present unreadable, malformed, or inconsistent index emits `SHAPEFILE_SHX_IGNORED`, is closed/discarded whole, and uses the same sequential path. |
| DBF | Optional attribute table. Missing emits `SHAPEFILE_DBF_MISSING`, exposes a present empty schema, and yields empty attributes. A present malformed or inconsistent DBF is terminal; it is never discarded in favor of attribute-free records. |
| CPG | Optional DBF encoding hint. Without DBF it emits `SHAPEFILE_CPG_WITHOUT_DBF` and is ignored. Missing CPG simply continues the encoding precedence. |
| PRJ | Optional CRS declaration. Missing PRJ is clean absent CRS unless the caller supplied an explicit CRS override. |

The recovery difference is deliberate: SHX is reproducible addressing metadata whose sequential
correctness oracle remains the SHP stream, while a present DBF is the dataset's only attribute and
deletion-row authority. Every fallback remains observable in the bounded opening report.

Opening behavior and diagnostic encounter order are fixed:

1. Probe SHP and the SHX, DBF, CPG, PRJ candidate pairs in that order. The first missing required SHP
   or ambiguous component terminates before policy warnings are emitted.
2. Open and validate SHP. Missing SHP uses `SHAPEFILE_COMPONENT_MISSING`; any other SHP I/O uses
   terminal `SHAPEFILE_IO_FAILED` with component `shp`.
3. Resolve SHX. Its missing/ignored warning is first. SHX-only I/O is recovered as
   `SHAPEFILE_SHX_IGNORED` with `reason=io` and bounded `causeKind`, never also
   `SHAPEFILE_IO_FAILED`.
4. Resolve DBF, then CPG encoding. A missing DBF warning precedes
   `SHAPEFILE_CPG_WITHOUT_DBF`; no encoding fallback is selected when there is no DBF. Present DBF or
   CPG I/O is terminal `SHAPEFILE_IO_FAILED` with its component.
5. Resolve PRJ last. Present PRJ I/O is terminal `SHAPEFILE_IO_FAILED` with component `prj`.

A terminal report retains bounded warnings from completed earlier phases in this order, then its one
error. Later phases are not attempted. This component-specific exception replaces any blanket rule
that all JDK I/O is terminal.

Until a sidecar/shape slice lands, the opener rejects a discovered but not-yet-implemented profile
element with `SHAPEFILE_PROFILE_NOT_IMPLEMENTED`; it does not accept and ignore it. G5-002 may create
the module only with working null/point/multipoint SHP behavior. G5-003, G5-004, G5-005, G5-006, and
G5-007 progressively replace that staged diagnostic with the approved SHX, polyline, polygon,
DBF/CPG, and PRJ behavior. Z/M and MultiPatch remain unsupported after the gate closes.

#### SHP geometry and record profile

The only accepted shape codes are:

| Code | Meaning | Published geometry |
| ---: | --- | --- |
| `0` | Null Shape | No `FeatureRecord`; the physical record is examined and still aligns with DBF/SHX. |
| `1` | Point | `PointGeometry` |
| `3` | PolyLine | `LineStringGeometry` or `MultiLineStringGeometry` according to part count |
| `5` | Polygon | `PolygonGeometry` or `MultiPolygonGeometry` after the classification below |
| `8` | MultiPoint | `MultiPointGeometry` |

Header codes `11`, `13`, `15`, `18`, `21`, `23`, `25`, `28`, and `31` terminate opening with
`SHAPEFILE_SHAPE_TYPE_UNSUPPORTED`. The reader never strips Z or M ordinates to manufacture a 2D
feature. Header type `0` is accepted only for an all-null file and exposes absent extent. For every
other supported header type, each record is either null or exactly that non-null type; another non-null
code is terminal `SHAPEFILE_RECORD_TYPE_MISMATCH`.

The 100-byte header requires file code `9994`, version `1000`, five reserved words that are all zero,
a supported type,
and checked mixed-endian fields. The non-negative big-endian length in 16-bit words must convert to an
even byte count equal to the opened file size, at least 100 and no greater than the effective component
limit. Extra bytes and a short declaration both fail. Header XY bounds are finite and ordered. A type
zero file has the conventional zero box but publishes no extent; another supported type publishes its
validated box as a conservative metadata extent. The four header Z/M doubles at bytes 68..99 are
unused by the accepted 2D codes: their raw bits are read as part of the fixed header but never decoded,
validated, retained, or exposed, so nonzero and non-finite bit patterns have no semantics.

Physical record headers start at byte 100. Record numbers must be exactly positive sequential ordinals
`1..N`; the source-local feature ID is exact `record:<decimal ordinal>` and the display name is empty.
Content lengths use checked word-to-byte conversion, must fit wholly inside both the declared file and
per-record limit, and must equal the exact length derived from the supported payload counts. There is
no tolerated trailing record data, implicit Z/M tail, corrupt-record resynchronization, or partial
record recovery. A malformed record terminates that cursor after deterministic cleanup; records
already returned remain valid under G4, while MapView's staging transaction publishes none of that
failed layer operation.

Every decoded XY ordinate and XY bound is finite and canonicalizes either signed zero to positive
`0.0` before construction, comparison, extent calculation, or publication. Distinct-coordinate and
ring-closure tests then use exact primitive-pair equality, so `-0.0` and `0.0` denote the same point.
Stored record bounds are finite, ordered, and must contain the exact
coordinate envelope; a conservative larger box is accepted without a warning. The main header box
must contain each decoded non-null record envelope. Bounds are never trusted to skip validation of a
record payload. Exact geometry and physical record order are preserved in packed G4 values. Null
records and later DBF-deleted rows count as examined but yield no nullable/empty geometry and no
warning.

MultiPoint requires at least one point. PolyLine requires at least one part, at least two coordinate
entries per part, and at least two distinct coordinate pairs in each whole part; this rejects a wholly
zero-length part. Consecutive identical vertices inside an otherwise non-degenerate part are accepted
and retained. Polygon requires at least one ring. Multipart tables require first offset zero, strictly
increasing offsets, every offset below the point count, and no empty trailing part. Count, table, and
coordinate byte arithmetic is checked before allocation; a structurally invalid table rejects the
whole record.

Polygon rings retain their coordinate order and exact repeated closing coordinate. Each has at least
four entries including closure, exact first/last equality, and finite nonzero signed area over the
whole ring. Consecutive identical vertices, including a zero-length interior edge, are accepted and
retained when the whole ring remains nonzero-area. Clockwise rings are shells and counterclockwise
rings are holes, following the ESRI profile; the reader does not reverse or repair orientation. Shells
become polygon components in their original ring order. Nested clockwise islands remain separate
polygon components.

Hole association is bounded and exact for the promised relationship. For each hole, the classifier
checks shells in source order. One charged bounds-relation test rejects closed-disjoint envelopes,
distinguishes strict containment, and retains non-strict overlap/touch for contact checking. Strict
containment receives the charged point-in-ring test; every non-disjoint hole/shell relation receives
the charged hole-edge/shell-edge pairs needed to reject crossing or touching. Each bounds test,
point-versus-shell-edge step, and segment-pair test prospectively charges one
`topologyComparisons` unit. A shell is a candidate only when the hole is strictly inside with no edge
or vertex contact; the candidate with smallest absolute area wins, and holes retain source order
within it. Any intersecting/touching hole-shell relation, an orphan hole, equal competing innermost
shells, or another undecidable association terminates the record with
`SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS`. The counter checks cancellation within 4,096 comparisons and
terminates with `SOURCE_LIMIT_EXCEEDED` before comparison `maximum + 1`.

This is deliberately a structural polygon profile. It does not perform shell-shell, hole-hole, or
within-ring simplicity analysis beyond whole-ring area and the hole-association checks above. A ring
that self-crosses away from the association test, or shell-shell and hole-hole peers that overlap, is
therefore
published in source order and rendered by G4's existing even-odd component rules without a claim of
OGC/simple-polygon validity or repair. Dedicated fixtures make that acceptance boundary observable;
G5 does not label such input clean according to a broader topology standard it never implements. The
task goal is limited to rejecting violations of this stated structural profile. General validity,
repair, and overlay require a later evidence-backed geometry adapter rather than an unbounded parser
algorithm.

#### SHX address-index profile

SHX has the same 100-byte mixed-endian header and then exactly eight bytes per entry. Version, shape
type, and numeric XY header bounds must agree with SHP (`-0.0` and `0.0` compare equal); file length
must equal the actual `100 + 8 * entryCount` bytes. Each positive big-endian word offset/length is
checked before conversion. Offsets are at least byte 100, strictly increasing, aligned to a real SHP
record header, wholly in the SHP file, and paired with the exact SHP content length. Entry count must
equal the physical SHP record count.

Any SHX structural, I/O, or cross-file failure rejects the complete index with one
`SHAPEFILE_SHX_IGNORED` warning whose stable `reason` is `io`, `header`, `length`, `entry`, or
`shpMismatch`; this includes an SHX-only component-byte, entry-count, arithmetic, or packed-index
allocation preflight failure, which uses `length` or `entry` and allocates nothing. Because the index is
discarded before source ownership, that recovered preflight does not also publish
`SOURCE_LIMIT_EXCEEDED`. It never publishes a partially trusted index. A valid index is stored in
immutable packed primitive offsets/lengths within open-time limits and may address records internally.
It is not a spatial index: queries still examine records in physical order, validate the selected SHP
payload, apply G4's envelope predicate, and return the same features/diagnostics as sequential access.
No new public random-record API is justified by Level 1.

#### DBF table, schema, and row profile

Level 1 accepts the common memo-free dBASE III/IV/5 compatibility header bytes `0x03`, `0x04`, and
`0x05`, with the 32-byte header and 32-byte field descriptors documented in OGC CDB Annex C. Any memo,
SQL, Visual FoxPro, encryption, or other version byte is unsupported. The `F` descriptor is accepted as
an explicit shapefile compatibility extension even with a `0x03` header; no other later-version field
semantics are inferred. DBT, MDX, indexes, field properties, and transactions remain outside Level 1.

Common header bytes have one exact treatment: update-date bytes 1..3 are retained nowhere and ignored
because they do not affect reading, while little-endian unsigned row count, header length, and record
length at bytes 4..11 are validated. The remaining treatment is version-specific:

- For `0x03`, bytes 12..28 and 30..31 are reserved/LAN bytes and ignored. Byte 29 is interpreted only
  as the explicit Esri shapefile LDID compatibility extension; no dBASE III transaction, encryption,
  or MDX semantics are invented.
- For `0x04` and `0x05`, bytes 12..13, 16..27, and 30..31 are reserved and ignored; byte 14 must be
  zero (complete transaction), byte 15 zero (not encrypted), and byte 28 zero or one and ignored
  because no production MDX is opened. Byte 29 is the documented LDID hint.

Header length must be exactly `32 + 32 * fieldCount + 1`, including one `0x0d` terminator; zero fields
are permitted and produce an empty schema. Record length is exactly one deletion byte plus the checked
sum of descriptor widths. The format's unsigned 65,535-byte header/row ceilings and the configured
limits both apply. After the declared rows, either EOF or one byte `0x1a` followed by EOF is accepted;
other trailing bytes are terminal.

Within each descriptor, bytes 0..10 contain a NUL-terminated maximum ten-byte printable ASCII field
name and byte 11 its type. The four-byte field displacement at 12..15 is ignored for every version and
never used for slicing; width at byte 16 and decimal count at byte 17 are authoritative. For `0x03`,
bytes 18..31 are reserved/work-area bytes and ignored. For `0x04`/`0x05`, bytes 18..30 are ignored and
byte 31 may be zero or one but is ignored because no field index is opened. Ignored bytes are covered
by version-specific fixtures so accepting them cannot accidentally add semantics.

Field names preserve exact spelling, contain no leading/trailing spaces, and are unique under ASCII
case folding. An empty, unterminated, malformed, or duplicate name is terminal; fields are never
renamed. Every descriptor has positive width and participates in the exact row sum. For supported
types, `C` is width `1..254` with decimal count zero; `N` and `F` are width `1..20`, decimal count zero
or, when positive, at most `width - 2`; `L` is width one/decimal zero; and `D` is width eight/decimal
zero. An invalid supported descriptor is terminal `SHAPEFILE_DBF_FIELD_INVALID`. An unsupported type's
decimal count is ignored but its positive width and row contribution remain validated.

Supported fields appear once in descriptor order in a present `AttributeSchema`, are nullable, and map
as follows:

| Code | Width/profile | G4 type and value rule |
| --- | --- | --- |
| `C` | `1..254` bytes | `TEXT`. All spaces or all zero bytes become `AttributeNull`; otherwise right-trim spaces and decode the complete remaining slice with the selected encoding, preserving leading/interior spaces. A mixed embedded zero byte is invalid. |
| `N`, decimals `0` | `1..20` bytes | `INTEGER`. Trim ASCII spaces and parse strict `[+-]?[0-9]+` into `Long`. |
| `N`, decimals `>0` | `1..20` bytes with a structurally possible decimal count | `DECIMAL`. Trim and parse a locale-independent plain decimal without exponent into `BigDecimal`; an optional fraction has no more digits than the descriptor count. |
| `F` | `1..20` bytes | `FLOATING`. Trim and parse an ASCII decimal with optional exponent into a finite `Double`; `NaN`, infinities, and hexadecimal forms are never accepted. |
| `L` | exactly one byte | `LOGICAL`. `T/t/Y/y` is true, `F/f/N/n` false, and space or `?` null. |
| `D` | exactly eight bytes | `DATE`. Spaces or `00000000` are null; otherwise eight ASCII digits are resolved strictly as `yyyyMMdd` into `LocalDate`. |

Blank numeric/floating fields are also null. A malformed/unmappable supported scalar does not affect
row framing: it becomes `AttributeNull` and emits one `SHAPEFILE_DBF_VALUE_INVALID` warning with the
physical row and field location. No partial replacement string or saturated numeric value is
published. Unsupported descriptor codes are structurally sliced, omitted from schema and records, and
emit one opening `SHAPEFILE_DBF_FIELD_UNSUPPORTED` warning per field. Memo pointers are not followed.

A live row marker is ASCII space; `*` is deleted; every other marker is terminal. A deleted row
consumes the corresponding SHP physical record but yields no feature and no warning. A null SHP record
likewise consumes its DBF row. Discarded/null/deleted rows validate framing and marker but need not
decode field values. `AttributeSelection.NONE` does not decode values; `ONLY` decodes only requested
supported fields, so value warnings are deliberately query-dependent. The structural row and count
checks always run.

SHP and DBF align by physical ordinal, not by yielded-feature count or an attribute key. Too few DBF
rows terminate before the unmatched SHP record; too many terminate when SHP exhausts. If a valid SHX
makes the disagreement knowable at open, the same `SHAPEFILE_DBF_RECORD_COUNT_MISMATCH` occurs then.
Level 1 shapefile metadata leaves `featureCount` absent because null/deleted records prevent a cheap
exact count without an eager scan. DBF contents never replace the structural `record:<ordinal>` ID.

#### CPG and DBF encoding profile

Format-local `DbfEncoding` is the explicit enum `UTF_8`, `ISO_8859_1`, `WINDOWS_1252`, `IBM437`, and
`IBM850`. UTF-8 uses `StandardCharsets.UTF_8` with a reporting decoder and ISO-8859-1 uses
`StandardCharsets.ISO_8859_1`. The other three are read-only single-byte profiles implemented by one
private immutable 256-code-unit lookup string per encoding. A byte indexes the string as unsigned;
Windows-1252's undefined bytes `0x81`, `0x8d`, `0x8f`, `0x90`, and `0x9d` map to one private invalid
sentinel and cause the whole selected field to use the normal invalid-value diagnostic/null
substitution. IBM437 and IBM850 define all 256 entries. The tables are generated once into source from
the Unicode Consortium's vendor mappings for
[Windows-1252](https://www.unicode.org/Public/MAPPINGS/VENDORS/MICSFT/WINDOWS/CP1252.TXT),
[IBM437](https://www.unicode.org/Public/MAPPINGS/VENDORS/MICSFT/PC/CP437.TXT), and
[IBM850](https://www.unicode.org/Public/MAPPINGS/VENDORS/MICSFT/PC/CP850.TXT), reviewed by checksum,
never exposed or mutated, and tested exhaustively against committed expected code-point arrays.
Production performs no `Charset.forName`, charset-provider lookup, or replacement decode. Resolution
order is:

1. an explicit caller-supplied `DbfEncoding` override;
2. a recognized CPG token;
3. an explicitly mapped DBF language-driver byte; then
4. `WINDOWS_1252` with `SHAPEFILE_ENCODING_FALLBACK`.

CPG is at most 256 bytes, ASCII with an optional UTF-8 BOM, trimmed only of leading/trailing ASCII
whitespace, and contains one token. Matching is ASCII-case-insensitive. The accepted aliases are
`UTF-8`, `UTF8`, and `65001`; `ISO-8859-1`, `ISO8859-1`, and Esri's numeric `88591`;
`WINDOWS-1252`, `CP1252`, and `1252`; `IBM437`, `CP437`, and `437`; and `IBM850`, `CP850`, and `850`.
LDID `0x01` maps to IBM437, `0x02` to IBM850, and `0x03` or `0x57` to Windows-1252; zero is absent and
every other value is unknown. The similar token `28591` is deliberately not a Level 1 alias and
follows the unknown-CPG warning/fallback path.

An override wins over every file hint. A recognized CPG wins over LDID. After selecting the encoding,
recognized lower-priority hints are compared independently in physical encounter order: CPG, then the
DBF LDID. An equal hint emits nothing. Each differing hint emits exactly one
`SHAPEFILE_ENCODING_CONFLICT` at component `cpg` or `dbf` with empty record/field/offset location and
exact context `selected=<DbfEncoding>` and `ignored=<DbfEncoding>`. Two lower hints that equal each
other but both differ from an override therefore emit two warnings rather than being deduplicated;
they are two contradictory file declarations. Malformed/unknown CPG first emits
`SHAPEFILE_CPG_INVALID` and falls through, so it never also emits a conflict. An unknown nonzero LDID
does not conflict with a higher recognized choice; when no higher choice exists it leads to the one
final `SHAPEFILE_ENCODING_FALLBACK`. Thus all CPG diagnostics precede all LDID conflict/fallback
diagnostics. Decoder malformed/unmappable action is always `REPORT`, with the field-level null
substitution above. No platform default, locale, heuristic detector, provider, or arbitrary code-page
alias participates. G5-010's native fixture includes one non-ASCII Windows-1252 value and one
undefined-byte diagnostic so the manual table path is not JVM-only evidence.

#### PRJ retention, recognition, and explicit override

PRJ is at most 65,536 encoded bytes, strict UTF-8 with an optional BOM, and must also fit G4's 16,384
UTF-16-character retained-definition bound. The exact decoded content after BOM removal is retained;
it is not trimmed or normalized for metadata. All-ASCII-whitespace content emits
`SHAPEFILE_PRJ_BLANK` and behaves as absent. Invalid encoding, unbalanced syntax, over-limit nesting/
tokens, or unsupported token syntax is terminal `SHAPEFILE_PRJ_INVALID`. A bounded syntactically valid
but unrecognized definition is retained as `CrsMetadata.unknown` and emits
`SHAPEFILE_PRJ_CRS_UNRECOGNIZED`.

The recognizer is one fixed format-local tokenizer and two structural matchers, not a public registry
or general WKT semantic engine. The tokenizer accepts only bracketed WKT1 identifiers, quoted strings,
commas, and finite decimal numbers, with at most 16 nesting levels and 512 tokens. Insignificant ASCII
whitespace and numeric lexical differences compare equal; keyword matching is ASCII-case-insensitive,
while quoted names and child order are exact. Extra/reordered nodes simply produce retained unknown
metadata.

The only recognized trees are:

- EPSG:4326: `GEOGCS` name `GCS_WGS_1984`, `DATUM` `D_WGS_1984`, `SPHEROID`
  `WGS_1984` with `6378137` and `298.257223563`, `PRIMEM` `Greenwich` at `0`, and `UNIT` `Degree`
  at `0.0174532925199433`.
- EPSG:3857: `PROJCS` name `WGS_1984_Web_Mercator_Auxiliary_Sphere`, the exact nested WGS 84
  `GEOGCS` above, `PROJECTION` `Mercator_Auxiliary_Sphere`, ordered parameters `False_Easting = 0`,
  `False_Northing = 0`, `Central_Meridian = 0`, `Standard_Parallel_1 = 0`, and
  `Auxiliary_Sphere_Type = 0`, then `UNIT` `Meter = 1`.

No `AUTHORITY`, axis, extension, datum alias, reordered parameter, EPSG database lookup, coordinate-
range guess, or raw-string registry alias is accepted as recognized Level 1 semantics.

Open options may carry one explicit recognized G4 CRS override so the pre-PRJ G5-002 viewer can render
a caller-declared dataset without guessing. A present PRJ is still bounded and validated. The override
wins when PRJ is missing or unknown; the latter emits `SHAPEFILE_PRJ_OVERRIDE_USED` while retaining the
text. An exactly matching recognized PRJ is clean. A differently recognized PRJ terminates with
`SHAPEFILE_CRS_CONFLICT`; explicit input never silently conceals a known contradiction.

#### Limits and allocation accounting

Format-local immutable `ShapefileLimits` has these Level 1 defaults:

| Ceiling | Default |
| --- | ---: |
| Bytes per SHP, SHX, or DBF component | 1,073,741,824 |
| Physical SHP records / SHX entries / DBF rows | 1,000,000 |
| SHP record content bytes | 67,108,864 |
| Parts or rings in one record | 100,000 |
| Points in one record | 1,000,000 |
| Polygon topology comparisons in one cursor operation | 10,000,000 |
| DBF fields | 255 |
| DBF field width | 254 |
| CPG bytes | 256 |
| PRJ encoded bytes | 65,536 |
| Decoded DBF characters in one cursor operation | 16,777,216 |
| Aggregate parser allocation in opening or one cursor operation | 268,435,456 |

All configurable ceilings are positive, equality is accepted, plus one is rejected, and there is no
unlimited sentinel, property override, or mutable default. Hard format constraints such as checked
signed word lengths, Java array indexing, the DBF row-width field, supported per-type widths, and G4's
retained-definition/value bounds still apply when a caller raises a configurable maximum.

Opening and each cursor have independent cumulative format counters. Opening charges retained header/
schema/text/index arrays and strings; a cursor charges every record buffer, part/point array, decoded
value, temporary classifier structure, and discarded intermediate before allocation or ownership
transfer. Released or skipped values do not refund a charge. Sources stream/channel-read bounded
regions and never read or map an entire SHP/DBF merely because the component-size limit permits it.
G4 `FeatureQueryAccounting` independently charges examined/published records and payload. Format
allocation, query work, returned payload, decoded text, warnings, and cancellation therefore compose
without one counter pretending to measure the others.

The parser-allocation counter is logical capacity, not JVM object size. It permits only these charged
production representations:

| Parser-owned value | Logical charge |
| --- | ---: |
| `byte[]` or heap byte-buffer backing | one byte per capacity element |
| `int[]` | four bytes per capacity element |
| `long[]` or `double[]` | eight bytes per capacity element |
| `char[]` or `String` | two bytes per character |
| reference array/list slot | eight bytes per capacity element |
| map entry | sixteen reference bytes plus separately owned key/value contents |
| `AttributeNull`/`Boolean` | one byte |
| `Long`/finite `Double`/`LocalDate` | eight bytes |
| `BigDecimal` | four scale bytes plus `max(1, ceil(abs(unscaled).bitLength / 8))` |

Owned schema/list/map strings and entries use the same table. A transferred array is charged once per
owned occurrence; a second copy charges again. Shared enum singletons, the private static encoding
lookup strings, diagnostic storage (bounded independently), caller-owned options, JDK `Path` values
(whether caller-supplied or derived), and JDK channel objects are excluded. Derived paths are resource
descriptors rather than retained parser payload and are discarded after component selection. As in G4,
fixed immutable carrier headers and their scalar reference fields have
no object-size charge; every separately owned string, scalar payload, primitive array, and container
slot reachable through them is charged by the table. Direct buffers and object-per-point/part/index/
classifier structures are not permitted. Ring bounds, signed areas, source indexes, candidate indexes,
and classification results use packed primitive arrays charged by the table. An implementation that
needs another parser-owned representation must first amend this design and its boundary tests; it
cannot estimate an object header or silently exempt the allocation. Thus limit-minus/equal/plus-one
fixtures have the same outcome on every JVM.

Every count, word/byte conversion, seek offset, slice, sum, and product is prospectively checked. Apart
from the explicitly discarded SHX preflight above, a format ceiling uses G4's
`SOURCE_LIMIT_EXCEEDED` report shape with a stable `scope`/`limit` name; arithmetic overflow reports
requested `Long.MAX_VALUE`. Cancellation uses `SOURCE_CANCELLED` and the G4 checkpoint cadence before
I/O/allocation, between records/stages, within 4,096 controlled primitive units, and before record
publication.

The only format scopes are `shapefileOpen` and `shapefileCursor`. Stable limit names are
`componentBytes`, `physicalRecords`, `recordBytes`, `parts`, `points`, `topologyComparisons`,
`dbfFields`, `dbfFieldWidth`, `cpgBytes`, `prjBytes`, `decodedTextCharacters`, and
`parserAllocationBytes`. These exact tokens occupy G4's existing `scope` and `limit` context entries.

#### Diagnostic and recovery matrix

Every format diagnostic follows G4's bounds/order. Component is exactly `shp`, `shx`, `dbf`, `cpg`,
or `prj`; record number is the positive physical on-disk ordinal after validation; part/field indexes
and absolute component byte offsets are zero-based; field name appears only after valid decoding. No
path, raw byte/value, CPG token, PRJ text, localized exception message, or external exception type is
copied into context. Required/present SHP, DBF, CPG, and PRJ JDK I/O failures map once to terminal
`SHAPEFILE_IO_FAILED` with a bounded `causeKind`; SHX I/O is the one previously specified recoverable
`SHAPEFILE_SHX_IGNORED` warning and never produces both codes.

The stable Level 1 codes are grouped by condition:

| Area | Codes |
| --- | --- |
| Component/open | `SHAPEFILE_COMPONENT_MISSING`, `SHAPEFILE_COMPONENT_AMBIGUOUS`, `SHAPEFILE_PROFILE_NOT_IMPLEMENTED`, `SHAPEFILE_IO_FAILED` |
| SHP framing/geometry | `SHAPEFILE_HEADER_INVALID`, `SHAPEFILE_FILE_LENGTH_MISMATCH`, `SHAPEFILE_SHAPE_TYPE_UNSUPPORTED`, `SHAPEFILE_RECORD_NUMBER_INVALID`, `SHAPEFILE_RECORD_LENGTH_INVALID`, `SHAPEFILE_RECORD_TYPE_MISMATCH`, `SHAPEFILE_COORDINATE_NON_FINITE`, `SHAPEFILE_BOUNDS_MISMATCH`, `SHAPEFILE_PART_TABLE_INVALID`, `SHAPEFILE_RING_INVALID`, `SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS` |
| SHX | `SHAPEFILE_SHX_MISSING`, `SHAPEFILE_SHX_IGNORED` |
| DBF | `SHAPEFILE_DBF_MISSING`, `SHAPEFILE_DBF_HEADER_INVALID`, `SHAPEFILE_DBF_FIELD_INVALID`, `SHAPEFILE_DBF_FIELD_UNSUPPORTED`, `SHAPEFILE_DBF_RECORD_MARKER_INVALID`, `SHAPEFILE_DBF_VALUE_INVALID`, `SHAPEFILE_DBF_RECORD_COUNT_MISMATCH` |
| Encoding/CRS | `SHAPEFILE_CPG_WITHOUT_DBF`, `SHAPEFILE_CPG_INVALID`, `SHAPEFILE_ENCODING_CONFLICT`, `SHAPEFILE_ENCODING_FALLBACK`, `SHAPEFILE_PRJ_BLANK`, `SHAPEFILE_PRJ_INVALID`, `SHAPEFILE_PRJ_CRS_UNRECOGNIZED`, `SHAPEFILE_PRJ_OVERRIDE_USED`, `SHAPEFILE_CRS_CONFLICT` |
| Shared lifecycle/limits | `SOURCE_LIMIT_EXCEEDED`, `SOURCE_CANCELLED`, `SOURCE_CLOSE_FAILED` |

Only these conditions recover:

- valid null shapes and DBF-deleted rows skip cleanly;
- missing DBF/SHX, ignored SHX, absent/invalid/unknown lower-priority encoding hints, unsupported DBF
  fields, invalid scalar substitutions, blank/unknown PRJ, and an explicit override of unknown PRJ
  continue with the warning named above; and
- every other hostile format condition terminates its open/cursor operation.

Unexpected implementation `RuntimeException`/`Error` remains unclassified after deterministic cleanup.
There is no best-effort geometry repair, corrupt-record skip, DBF row realignment, partial SHX use, or
silent diagnostic fallback.

#### Resource, query, and Native Image boundary

Opening acquires channels in component order SHP, SHX, DBF and cleans a partial open in exact reverse
order. Bounded CPG/PRJ bytes are read and their channels closed during opening. A valid SHX may likewise
be fully loaded and closed; SHP and present DBF channels remain source-owned. The source/cursor then
follows G4's one-live-cursor, external-serialization, failure reuse, idempotent reverse close, and
primary/suppressed failure rules. No finalizer, `Cleaner`, memory mapping, `Unsafe` unmap, thread,
prefetch, hidden cache, or cross-thread close is added.

Queries preserve physical SHP order. They validate framing and account for every physical record,
align DBF before omission, apply the supported geometry mapping, then use G4's inclusive envelope and
attribute-selection semantics. SHX changes addressing only. Cursor cancellation/failure releases all
operation buffers and leaves an otherwise open source reusable as G4 specifies.

All construction is direct and all character/PRJ profiles are fixed tables. Native-targeted code uses
no reflection, classpath scanning, dynamic proxy, Java serialization, JNI, native library, internal
JDK API, implicit resource enumeration, or automatic plugin/charset discovery. G5-010 must still prove
the real parser/query/render success path and one matching diagnostic in an actual Native Image.

#### Fixture and review boundary

Hand-built fixtures are byte-level test data or test builders, never production generators. The matrix
must cover all five accepted shape codes; every rejected Z/M/MultiPatch code; header/record endian and
length boundaries; ignored Z/M header bits; signed-zero canonicalization; null interleaving;
conservative/non-containing boxes; valid/invalid part tables; shell, hole, island, orphan, touching,
ambiguous, and explicitly structural-only polygons; missing/valid/ambiguous/corrupt sidecars; each
accepted DBF version's reserved/status/index-byte rules; every DBF field/blank/substitution/deletion/
unsupported/name/count case; every encoding precedence and accepted/near-miss alias branch;
recognized/unknown/blank/conflicting PRJ; limit minus/equal/plus one; cancellation; and partial-open/
cursor cleanup. Fixture builders use checked fixed sizes and cannot become an alternate parser.

The G5-001 HITL checkpoint approves exactly this 2D/Z-M policy, optional-sidecar recovery, DBF value
substitution, encoding precedence/fallback, PRJ recognizers, and default limits before G5-002 begins.
Later task designs may choose class/package decomposition and algorithms that preserve these outcomes;
they do not reopen the format profile.

## Working SHP source

### Point/multipoint sequential slice (G5-002)

#### Public format surface

G5-002 creates the format module only with a working file-backed source. Its entire public package
`io.github.mundanej.map.io.shapefile` contains three final immutable/directly constructed types:

```text
Shapefiles
  open(SourceIdentity identity,
       Path shpPath,
       ShapefileOpenOptions options) -> FeatureSource
  open(SourceIdentity identity,
       Path shpPath,
       ShapefileOpenOptions options,
       CancellationToken cancellation) -> FeatureSource

ShapefileOpenOptions
  defaults()
  featureSourceLimits() -> FeatureSourceLimits
  shapefileLimits() -> ShapefileLimits
  crsOverride() -> Optional<CrsDefinition>
  withFeatureSourceLimits(FeatureSourceLimits) -> ShapefileOpenOptions
  withShapefileLimits(ShapefileLimits) -> ShapefileOpenOptions
  withCrsOverride(CrsDefinition) -> ShapefileOpenOptions
  withoutCrsOverride() -> ShapefileOpenOptions

ShapefileLimits
  defaults()
  maximumComponentBytes() -> long
  withMaximumComponentBytes(long) -> ShapefileLimits
  maximumPhysicalRecords() -> long
  withMaximumPhysicalRecords(long) -> ShapefileLimits
  maximumRecordBytes() -> long
  withMaximumRecordBytes(long) -> ShapefileLimits
  maximumParts() -> long
  withMaximumParts(long) -> ShapefileLimits
  maximumPoints() -> long
  withMaximumPoints(long) -> ShapefileLimits
  maximumTopologyComparisons() -> long
  withMaximumTopologyComparisons(long) -> ShapefileLimits
  maximumDbfFields() -> long
  withMaximumDbfFields(long) -> ShapefileLimits
  maximumDbfFieldWidth() -> long
  withMaximumDbfFieldWidth(long) -> ShapefileLimits
  maximumCpgBytes() -> long
  withMaximumCpgBytes(long) -> ShapefileLimits
  maximumPrjBytes() -> long
  withMaximumPrjBytes(long) -> ShapefileLimits
  maximumDecodedTextCharacters() -> long
  withMaximumDecodedTextCharacters(long) -> ShapefileLimits
  maximumParserAllocationBytes() -> long
  withMaximumParserAllocationBytes(long) -> ShapefileLimits
```

The no-token opener delegates with `CancellationToken.none()`. The opening token is operation-local,
polled throughout opening, and never retained by the source. The facade returns the format-neutral
`FeatureSource`; Level 1 has no public concrete parser/source subtype, record-address API, component
path object, shape enum, or reader SPI. A consumer already holds its immutable options, so no format-
specific limit getter is added to the returned source.

`ShapefileLimits` lands complete with the twelve approved ceilings and exact defaults from G5-001:
component bytes, physical records, record bytes, parts, points, topology comparisons, DBF fields,
field width, CPG bytes, PRJ bytes, decoded text characters, and parser allocation bytes. The final
class uses private construction, `defaults()`, value equality, bounded `toString`, and the exact
`long` accessors/withers above. One scalar type keeps prospective arithmetic uniform even where a hard
format or Java-array ceiling is lower. Each wither requires a positive value; the type has no builder,
map of string knobs, unlimited sentinel, or system-property defaults. G5-002 enforces component bytes,
physical records, record bytes, points, and parser allocation. Other fields are validated/stored now
because the approved one-value profile is the shared
parallel G5 boundary, but their Javadocs name the task in which input begins to exercise them.

`ShapefileOpenOptions` is likewise a private-constructor immutable value with value equality. Defaults
combine G4's default `FeatureSourceLimits`, `ShapefileLimits.defaults()`, and absent override. A CRS
override is a `CrsDefinition`, which by construction is a recognized geographic/projected definition;
the format stores recognized metadata with that definition and no invented PRJ provenance. MapView
still verifies that the supplied definition is exactly registered. G5-006 may add a `DbfEncoding`
wither/private field without invalidating these factories; no inactive encoding value lands now.

Null public inputs use parameter-named `NullPointerException`. A path with no final filename or without
ASCII-case-insensitive `.shp`, a non-positive limit-wither argument, or another unsuitable direct
option uses `IllegalArgumentException` before path probes. Hostile files, cancellation, and I/O use G4
`SourceException` reports. Every public type has complete Javadocs and no path appears in its
`toString`.

#### Module, publication, and package graph

The implementation task adds these real projects together:

```text
modules/mundane-map-io-shapefile
  api -> mundane-map-api
  implementation -> mundane-map-core

examples/shapefile-viewer
  implementation -> mundane-map-api, mundane-map-core,
                    mundane-map-awt, mundane-map-io-shapefile
```

The format module receives one entry in G0's authoritative project inventory: JDK-only runtime,
Level 1, published, and native-targeted. The example receives one support entry: checked,
non-published, and outside the production/native-runtime graph. Settings parity, checked/published
inputs, dry-run artifact output, architecture-test input, Level 1 release membership, and native-
target inputs are derived from those entries; no secondary root list or allowlist is edited. The
example is not added to the native-smoke application until G5-010. `publicationDryRun` must stage the
format POM, JAR, sources, and Javadocs with only API/core project dependencies and no AWT/external
runtime dependency.

The three public types and every package-private implementation peer share
`io.github.mundanej.map.io.shapefile`. Java package-private access does not cross subpackages, and the
project has no JPMS export boundary that would make public `internal.*` types private to the module.
Consequently this format does not create misleading `internal.shp`, `internal.shx`, `internal.dbf`,
`internal.cpg`, or `internal.prj` packages. Behavior remains separated by package-private
`Shapefile*`/`Shp*`/`Shx*`/`Dbf*`/`Cpg*`/`Prj*` classes and source files in the one package; only the
three named public types are externally accessible. Future ownership reserves those behavior-specific
files but creates no empty classes. One integrator owns edits to the shared opener, source, cursor
switch, authoritative inventory entries, and diagnostic encounter order; logical parallelism does not
make those shared files path-safe.

Derived architecture checks treat the format inventory entry as Level 1/native-targeted, permit only
API/core/JDK dependencies, and reject `java.desktop`, AWT/Swing, service/discovery metadata,
reflection, dynamic proxies, serialization, JNI, `Unsafe`, internal JDK APIs, memory mapping, and
format-to-example dependencies. No generic I/O-module abstraction is introduced.

#### Component snapshot and opening transaction

Production uses one fixed package-private JDK file-access boundary for existence probes,
`Files.isSameFile`, opening a positional read channel, size, and close. It is explicitly constructed by
`Shapefiles`, is not configurable in public options, and has only a test implementation for identity,
short-read, I/O, and cleanup-failure evidence. This narrow resource seam is not a provider or plugin
interface.

Opening is a single transaction:

1. Validate public arguments, then check cancellation before any allocation, probe, or I/O.
2. Snapshot the exact caller SHP path and the finite lower/upper SHX, DBF, CPG, PRJ sibling pairs from
   G5-001 in that order. Missing required SHP wins before sidecar ambiguity. For each pair, one present
   candidate is selected, aliases collapse only after `isSameFile`, and two distinct/undecidable
   candidates terminate with `SHAPEFILE_COMPONENT_AMBIGUOUS` before a channel opens. Check
   cancellation immediately before and after every existence probe and `isSameFile` call.
3. Open only the caller's SHP, capture its size, enforce `componentBytes`, charge/read the fixed header,
   and validate it as below. Check cancellation immediately before and after open, size, and every
   positional read.
4. A valid header type 3 or 5 terminates with staged `SHAPEFILE_PROFILE_NOT_IMPLEMENTED`; permanent
   unsupported header types use `SHAPEFILE_SHAPE_TYPE_UNSUPPORTED`.
5. After a valid current header, the first discovered sidecar in SHX, DBF, CPG, PRJ order terminates
   with staged `SHAPEFILE_PROFILE_NOT_IMPLEMENTED`. Its bytes are never opened or parsed. An override
   cannot conceal a discovered PRJ.
6. Perform a final cancellation check and transfer the channel to the source only after every stage
   succeeds. Failure closes an acquired channel once; close failure is suppressed under the original
   throwable/report cause and does not create a second terminal diagnostic. Cleanup itself never polls
   cancellation or abandons a close because cancellation has become requested.

The staged diagnostic has the component and otherwise only a byte offset for SHP header type (`32`).
Its exact context is `profile=polyline|polygon|shx|dbf|cpg|prj`. Component ambiguity has empty context;
the lower/upper choice is already fixed by the component and no filename is copied. A clean sidecar-
free G5-002 source has an empty opening report and absent schema. It does not pre-emit missing SHX/DBF
warnings or a placeholder empty DBF schema: G5-003 and G5-006 begin those approved policies. This
staged behavior is an explicit 0.x delivery boundary, not the final G5 component policy.

Opening cancellation uses `SOURCE_CANCELLED`. Missing SHP uses `SHAPEFILE_COMPONENT_MISSING`; other
Component probe and SHP open/read/size failures use `SHAPEFILE_IO_FAILED` with the exact bounded I/O
vocabulary below. Opening allocation uses the `shapefileOpen` logical table from G5-001 and includes
every fixed/scratch buffer even when later
discarded. A successful source retains only the channel, captured size/header values, immutable
metadata/limits, and empty opening report—never the token or a header byte buffer.

#### Fixed SHP header

One exact 100-byte read validates fields at their physical offsets:

- big-endian file code `9994` at 0 and five zero reserved words at 4, 8, 12, 16, and 20;
- non-negative big-endian word length at 24, checked to an even byte count equal to the captured size,
  at least 100 and within `componentBytes`;
- little-endian version `1000` at 28 and shape type at 32;
- accepted current types 0, 1, and 8; staged types 3 and 5; every other type permanently unsupported;
- finite little-endian XY bounds at 36, 44, 52, and 60, canonicalized for signed zero and ordered; and
- raw bytes 68 through 99 consumed but never decoded as Z/M doubles.

Type zero requires all four canonical XY bounds equal positive zero and publishes absent extent. Type
1 or 8 publishes the validated conservative XY box. Feature count and schema are absent. The optional
source CRS is the exact override. Header validation does not scan records to prove an all-null file,
count features, shrink an extent, or inspect payloads.

Header diagnostics use the exact condition table below; `shapeType` is deliberately not a
`SHAPEFILE_HEADER_INVALID` field because every integer type is current, staged, or unsupported.
Raw coordinate/header bits and paths never enter context.

#### Positional source and cursor state

The package-private source implements G4's exact `FeatureSource` state and owns one positional channel
for its lifetime. It requires external serialization, allows one live cursor, and never mutates shared
channel position. A cursor owns a checked `long nextOffset` beginning at 100, expected positive ordinal
beginning at one, query/token, fresh format/query counters, small heap scratch buffers, and current
record. A new cursor always restarts at 100; no parser state/cache survives an operation.

Before claiming the cursor slot, `openCursor` checks source/query/token, cancellation, tighter query
limits, and `channel.size() == capturedSize`. Because schema is absent, `ALL` and `NONE` yield empty
attributes and dynamic-schema `ONLY` may omit every requested name. A size change terminates with the
same file-length diagnostic before payload I/O. Same-size external mutation is unsupported snapshot
behavior; fields are still validated as read, but the source does not hash or lock the file.

`advance()` invalidates current and loops over physical records until one publishes or exact EOF is
reached:

1. Check cancellation. If `nextOffset == capturedSize`, recheck channel size, enter `EXHAUSTED`,
   release the slot, and return false. Fewer than eight remaining bytes is record-length failure.
2. Read the eight-byte big-endian header positionally. The on-disk number must equal the expected
   ordinal; content words convert with checked arithmetic to at least four bytes wholly contained in
   the captured file and within `recordBytes`.
3. Once that trusted frame exists, prospectively charge `physicalRecords`, call
   `FeatureQueryAccounting.recordExamined()`, and advance the expected next offset. An unframeable
   header does not count; null, filtered, and later-invalid payloads do.
4. Read the little-endian shape code. Null must have exactly four content bytes and skips cleanly. A
   non-null code must exactly equal the accepted header; any supported-other, Z/M, or MultiPatch code
   is `SHAPEFILE_RECORD_TYPE_MISMATCH`.
5. Decode/validate the complete Point or MultiPoint payload, build immutable geometry/record, then
   apply the inclusive query-envelope predicate. A filtered record is discarded only after full
   validation.
6. For a match, use exact ID `record:<ordinal>`, empty name/attributes, call `recordReturned` with zero
   retained-container slots, check cancellation immediately before setting current, publish, and
   return true.

Point content is exactly 20 bytes and contains X/Y at content offsets 4/12. Both are finite,
signed-zero canonicalized, and inside the main header extent. MultiPoint content is exactly
`40 + 16 * pointCount` checked bytes: finite/ordered/canonical record bounds at offsets 4..35, positive
little-endian count at 36, then source-ordered XY pairs. The count and `2 * count` must fit
`points`/Java arrays/allocation limits before allocation. Every coordinate is finite, canonical, inside
the record box, and its computed exact envelope is inside both record and main boxes. Record boxes may
be conservatively larger.

For an accepted MultiPoint, the executable order is: require at least the 40-byte declared prefix;
read that prefix; require a positive count; enforce `points` and Java-array capacity; compute and match
the exact derived content size; preflight allocation; then validate the record box and coordinate
payload. Reading a box is not acceptance: non-finite/order/containment checks occur only at that final
validation stage, so an earlier count or size failure wins deterministically.

The reader uses reusable fixed heap buffers for headers/prefixes and a 16-byte coordinate scratch,
never a whole-record byte array. MultiPoint uses one packed `double[]`; because the existing immutable
`CoordinateSequence` defensively copies it, both arrays are prospectively charged before construction,
then the parser array is released. No zero-copy API or object-per-point representation is added merely
to save this known bounded copy. Record ID strings and every other parser-owned value use the approved
logical charge table.

Cancellation is checked before/after every channel operation, between records, within coordinate loops
at no more than 4,096 primitive units, and before publication. Exact positional short read before the
captured boundary is `SHAPEFILE_RECORD_LENGTH_INVALID`; another `IOException` is
`SHAPEFILE_IO_FAILED`. At successful exhaustion the second size check prevents appended data from
being silently ignored. Failure/cancellation enters its distinct cursor terminal state, invalidates
current, releases buffers and the source slot once, and leaves the source/channel reusable. Repeated
advance/current then follows G4 lifecycle errors. Early/repeated cursor close is safe and never closes
the source channel.

Source close first transitions closed, closes a live cursor, then its channel once, aggregating cleanup
failure under `SOURCE_CLOSE_FAILED` exactly as G4 specifies. Metadata, limits, and reports survive.
There is no full-file read, mapping, direct buffer, finalizer, `Cleaner`, background work, prefetch, or
hidden cache.

#### Exact malformed-input and I/O diagnostics

The first applicable row wins. The table defines exact context key/value sets; publication order is
always G4's lexicographic key order, regardless of explanatory order below. `-` means empty context.
Decimal counters/sizes are bounded by G4's scalar formatting rules.

| Condition | Code | Location | Context |
| --- | --- | --- | --- |
| Required SHP is absent at the initial snapshot | `SHAPEFILE_COMPONENT_MISSING` | component `shp` | - |
| Two sidecar case variants are distinct or identity is undecidable | `SHAPEFILE_COMPONENT_AMBIGUOUS` | affected component | - |
| Captured SHP size is below 100 | `SHAPEFILE_HEADER_INVALID` | `shp`, byte 0 | `field=headerSize`, `expectedBytes=100`, `actualBytes=<captured size>` |
| Header read reaches EOF before 100 bytes despite the captured size | `SHAPEFILE_HEADER_INVALID` | `shp`, first missing byte | `field=truncated`, `expectedBytes=100`, `actualBytes=<bytes read>` |
| File code or one reserved word is invalid | `SHAPEFILE_HEADER_INVALID` | `shp`, first failing field | `field=fileCode|reserved` |
| Signed header file-length word is negative | `SHAPEFILE_HEADER_INVALID` | `shp`, byte 24 | `field=fileLength`, `reason=negative` |
| Header-declared file length differs from captured open size | `SHAPEFILE_FILE_LENGTH_MISMATCH` | `shp`, byte 24 | `declaredBytes=<header bytes>`, `actualBytes=<captured bytes>` |
| Version is not 1000 | `SHAPEFILE_HEADER_INVALID` | `shp`, byte 28 | `field=version` |
| Staged header type | `SHAPEFILE_PROFILE_NOT_IMPLEMENTED` | `shp`, byte 32 | `profile=polyline|polygon` |
| Permanently unsupported header type | `SHAPEFILE_SHAPE_TYPE_UNSUPPORTED` | `shp`, byte 32 | `shapeType=<decimal>` |
| Header XY value is non-finite, unordered, or nonzero for type zero | `SHAPEFILE_HEADER_INVALID` | `shp`, first failing XY field | `field=bounds`, `reason=nonFinite|unordered|nonZeroNull` |
| Channel size at cursor open/exhaustion differs from captured open size | `SHAPEFILE_FILE_LENGTH_MISMATCH` | `shp`, byte 24 | `declaredBytes=<captured bytes>`, `actualBytes=<current channel bytes>` |
| Fewer than eight captured bytes remain for a record header | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=truncatedHeader`, `expectedBytes=8`, `actualBytes=<remaining bytes>` |
| Record-header positional read reaches EOF despite eight captured bytes | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=truncatedHeader`, `expectedBytes=8`, `actualBytes=<bytes read>` |
| On-disk record number differs from the expected ordinal | `SHAPEFILE_RECORD_NUMBER_INVALID` | `shp`, expected record, record start | `expected=<ordinal>`, `actual=<on-disk decimal>` |
| Content word count is below two | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=invalidWords`, `actualWords=<decimal>` |
| Checked record-end arithmetic overflows | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=overflow` |
| Declared content extends beyond captured EOF | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=outOfFile`, `declaredBytes=<content bytes>`, `remainingBytes=<after header>` |
| Declared content exceeds configured `recordBytes` | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, `recordStart + 4` | `limit=recordBytes`, `maximum=<limit>`, `requested=<declared content bytes>`, `scope=shapefileCursor` |
| Shape-code positional read reaches EOF inside a captured frame | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, content start | `reason=truncatedPayload`, `expectedBytes=4`, `actualBytes=<content bytes read>` |
| Null payload has the wrong exact byte count | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=unexpectedSize`, `expectedBytes=4`, `actualBytes=<declared content bytes>` |
| Non-null record type differs from the accepted header type | `SHAPEFILE_RECORD_TYPE_MISMATCH` | `shp`, expected record, content start | `expected=<header type>`, `actual=<record type>` |
| Accepted Point payload has the wrong exact byte count | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=unexpectedSize`, `expectedBytes=20`, `actualBytes=<declared content bytes>` |
| Point positional read reaches EOF after its accepted type/size | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, first missing payload byte | `reason=truncatedPayload`, `expectedBytes=20`, `actualBytes=<content bytes read>` |
| Accepted MultiPoint payload is shorter than its 40-byte prefix | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=truncatedPrefix`, `expectedBytes=40`, `actualBytes=<declared content bytes>` |
| MultiPoint prefix read reaches EOF after its declared-size preflight | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, first missing payload byte | `reason=truncatedPayload`, `expectedBytes=40`, `actualBytes=<content bytes read>` |
| MultiPoint count is zero or negative | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `contentStart + 36` | `reason=pointCount` |
| MultiPoint count exceeds configured `points` | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, `contentStart + 36` | `limit=points`, `maximum=<limit>`, `requested=<count>`, `scope=shapefileCursor` |
| MultiPoint ordinate count cannot fit a Java array after configured limits pass | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `contentStart + 36` | `reason=arrayCapacity` |
| MultiPoint payload differs from the checked size derived from its count | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `reason=unexpectedSize`, `expectedBytes=<derived content bytes>`, `actualBytes=<declared content bytes>` |
| Prospective Point allocation exceeds `parserAllocationBytes` | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, content start | `limit=parserAllocationBytes`, `maximum=<limit>`, `requested=<prospective bytes>`, `scope=shapefileCursor` |
| Prospective MultiPoint allocation exceeds `parserAllocationBytes` | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, `contentStart + 36` | `limit=parserAllocationBytes`, `maximum=<limit>`, `requested=<prospective bytes>`, `scope=shapefileCursor` |
| MultiPoint coordinate read reaches EOF after count/size/allocation preflight | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, first missing payload byte | `reason=truncatedPayload`, `expectedBytes=<derived content bytes>`, `actualBytes=<content bytes read>` |
| Point/MultiPoint coordinate or record-box ordinate is non-finite | `SHAPEFILE_COORDINATE_NON_FINITE` | `shp`, expected record, offending eight-byte field | `axis=x|y` |
| Record box is unordered, coordinate is outside record/file box, or computed envelope is outside either | `SHAPEFILE_BOUNDS_MISMATCH` | `shp`, expected record, first offending box/ordinate field | `bounds=record|file` |

An opaque JDK operation that throws `NoSuchFileException`, `AccessDeniedException`,
`ClosedChannelException`, or another `IOException` maps `causeKind` respectively to `notFound`,
`accessDenied`, `closed`, or `other`; subclass matching follows that order. It produces
`SHAPEFILE_IO_FAILED` with the affected component and exact context keys `causeKind=<token>` and
`operation=probe|open|size|read` in that lexical order. A known requested read position is the byte
offset; otherwise the byte offset is absent. `isSameFile` failure is the already specified component
ambiguity instead. Opening cleanup failure is suppressed beneath the primary failure, and ordinary
source close uses G4's `SOURCE_CLOSE_FAILED`, so neither creates a second format diagnostic.

Limit/cancellation codes and context remain the G4/G5-001 shared shapes. Record locations always use
the trusted expected physical ordinal, never an invalid raw record number; byte offsets are absolute
within component `shp`. No raw coordinate value/bits, path, localized I/O text, or malformed payload
is copied. This slice has no successful cursor warning: diagnostics are empty or one terminal error.

#### Record evidence

Focused fixtures are independently written byte-level test builders, not production parsing helpers.
They cover header fields/endian/size; arbitrary ignored Z/M bits; signed zeros; empty/all-null type 0;
Point/MultiPoint with interleaved nulls; IDs/order/query filtering; conservative/rejected boxes; every
staged/permanent/mismatched type; zero/negative/maximum counts and Java-array capacity; exact/short/overlong payloads;
applicable format and G4 limits at minus/equal/plus one; allocation of both coordinate arrays;
cancellation at each checkpoint; cursor/source states and reuse; channel mutation; I/O/close cleanup;
and exact diagnostics. Path fixtures cover lower/upper/mixed-case names, same-file hard-link aliases,
distinct ambiguity, staged priority, and a valid-header sidecar failure that proves channel cleanup.
The alias test is required in the Linux lane and conditional only where the filesystem genuinely cannot
create a hard link; distinct ambiguity remains cross-platform.

#### Runnable viewer and observable slice

The example command is explicit and has no file chooser or CRS guess:

```text
shapefile-viewer <path.shp> <EPSG:4326|EPSG:3857>
```

Argument count/key validation happens before Swing scheduling. The exact key resolves through an
explicit Level 1 `CrsRegistry`; the definition becomes the source override, while the view uses
EPSG:4326 map coordinates and EPSG:3857 display. The example supplies constant source/layer identity,
explicit built-in marker/line/fill symbols, and an owned feature binding. A sidecar-free supplied SHP
is required in this slice; discovered PRJ still stages rather than being hidden by the CLI override.

A package-visible `createMapView(Path, CrsDefinition)` opens the source, attaches it transactionally,
fits metadata extent, and returns a view that owns the source and must be closed. The helper owns the
source until `ownedFeature` succeeds; that unattached binding owns it after factory transfer; and the
view owns the binding only after successful installation. On failure the helper closes whichever of
source, unattached binding, or view owns the chain at that exact stage. Permanent window teardown calls
`MapView.close()` on the EDT. A test-authored temporary MultiPoint/Null fixture proves
argument loading, fit, the real source query, tolerant offscreen marker rendering, and file release.
There is no bundled production corpus, format code in AWT, or viewer-specific parser path.

G5-002 implementation validation runs the new module/example checks, the existing normal gate,
publication dry run, and whitespace. Native, corpus, fuzz, and performance evidence remain their
separate later tasks.

### SHX indexed-address slice (G5-003)

#### Internal index and ownership

G5-003 replaces only the staged `profile=shx` branch behind the existing `Shapefiles` facade. It adds
no public type, opener overload, feature-count promise, random-record method, query option, or spatial
index abstraction. Missing or discarded SHX continues through G5-002's sequential source; a valid
SHX changes only the private way that the same cursor addresses physical records.

One package-private `ShxReader` peer produces an all-or-nothing `ShxIndex` consumed by package-private
opener/source peers in that same package. The index owns one
`long[]` in physical entry order. Each element packs the positive raw big-endian offset word in the
high 32 bits and the positive raw content-length word in the low 32 bits; package-private accessors
perform checked word-to-byte conversion and never expose the array. Packing the two format integers
directly avoids an object per entry, a second primitive array, and premature spatial metadata. The
source owns either this immutable index or no index. No SHX channel, header buffer, entry buffer, file
token, or partially filled array survives opening.

The valid index is source state, not cursor state. Every cursor starts at entry zero and keeps only an
entry ordinal in addition to G5-002's existing query/accounting state. Source close applies the normal
closed-state checks to all later indexed access; there is no index-specific close operation or file
handle to release. Metadata, the opening report, and already published records remain valid after
close.

#### SHX opening phase and bounded preflight

The G5-002 opening transaction keeps its component snapshot and validated SHP channel. Immediately
after SHP header validation, SHX resolution becomes:

1. Check cancellation. If the snapshotted SHX is absent, append one `SHAPEFILE_SHX_MISSING` warning
   and select the sequential source.
2. If present, open the selected SHX positionally and capture its size. Reuse the opening
   transaction's already charged 100-byte scratch buffer after the retained SHP header values have
   been copied out; that one buffer serves the SHX header, each SHX entry, and each SHP frame read.
   Check cancellation immediately before and after open, size, and every positional read.
3. Apply captured-size invariants before inspecting header fields: require at least 100 bytes, no more
   than `componentBytes`, an exact trailing multiple of eight, and checked derivation of
   `entryCount = (size - 100) / 8`. These failures are `reason=length` and win over malformed header
   bytes because no safe complete header/entry layout exists.
4. Read the complete header and validate fields in physical order: file code, five reserved words,
   length word against captured size, version, shape type against retained SHP type, then local and
   cross-file XY bounds. Only after the header succeeds, require `entryCount` to fit the configured
   `physicalRecords` ceiling, Java array capacity, checked `8 * entryCount` logical-byte charge, and
   remaining `parserAllocationBytes`; then allocate the packed array.
5. Stream each eight-byte SHX entry while positionally reading the corresponding eight-byte SHP
   record frame. Validate the complete ordered address relationship below and fill the packed element
   only after that entry succeeds.
6. Require the checked end of the last addressed SHP frame to equal the captured SHP size, then
   recheck that the SHX channel size still equals its captured size. Check cancellation before/after
   that size call and immediately before treating the complete array as valid, close the SHX channel,
   then check cancellation again before transferring the index and still-open SHP channel to the
   source.

Successful, missing, and recovered-invalid SHX resolution all continue to the existing DBF, CPG, and
PRJ staged phases in that order. Thus a missing/ignored SHX warning precedes a later staged sidecar
error and is retained in that terminal report, subject to G4's warning cap. A valid SHX is silent.
G5-003 does not begin the missing-DBF policy, create a placeholder schema, or inspect another sidecar's
bytes.

Every SHX-only failure closes and discards its channel, buffers, and complete/partial array, appends
exactly one `SHAPEFILE_SHX_IGNORED` warning, and selects the unchanged sequential path. This recovery
includes SHX open/size/read/close I/O, malformed header/length/entry data, an SHX-specific limit or
allocation preflight failure, and a structural mismatch against SHP. It never also emits
`SHAPEFILE_IO_FAILED` or `SOURCE_LIMIT_EXCEEDED`. An SHP size/read I/O failure during cross-check is
still terminal `SHAPEFILE_IO_FAILED` for component `shp`; it is not blamed on SHX. A malformed SHP
frame encountered only while checking an otherwise readable SHX discards the index with
`reason=shpMismatch`; the sequential cursor remains the correctness oracle and later reports that SHP
problem through its normal terminal diagnostic.

An exact positional SHP-frame read that reaches EOF without throwing is malformed framing, not JDK
I/O: recover with `SHAPEFILE_SHX_IGNORED`, component `shx`, `reason=shpMismatch`, no record number,
and byte offset at that SHX entry's content-length field. A thrown `IOException` from the SHP channel
is instead terminal `SHAPEFILE_IO_FAILED`, component `shp`, at the requested SHP frame byte offset,
with G5-002's lexical `causeKind`, `operation=read` context. The file-access seam exposes an ordinary
short read/EOF separately from an injected `IOException`, so fault tests pin both branches.

Cancellation is never recovered as an ignored index. It closes/discards SHX state and terminates
opening with `SOURCE_CANCELLED`, retaining only an earlier warning already encountered. A custom token
failure propagates unchanged after cleanup. Cleanup never polls cancellation. A close failure below a
primary cancellation, SHP failure, unexpected throwable, or already selected
`header|length|entry|shpMismatch` rejection is suppressed and does not replace that primary outcome.
An SHX close failure after otherwise successful validation is itself recovered as `reason=io` and
prevents index publication.

#### Header and entry validation order

SHX uses the same exact 100-byte mixed-endian layout as SHP:

- big-endian file code `9994` at byte 0 and five zero reserved words at 4, 8, 12, 16, and 20;
- a non-negative big-endian word length at 24 whose checked byte value equals the captured SHX size;
- little-endian version `1000` at 28 and shape type at 32; and
- finite, ordered little-endian XY bounds at 36, 44, 52, and 60, with signed zero canonicalized before
  comparison. Raw bytes 68 through 99 are consumed but never decoded or compared.

After captured-size invariants, header fields are checked strictly in their physical order. Invalid
file code/reserved words or a version other than `1000` is `reason=header`; the non-negative declared
length is `reason=length` when it disagrees with captured SHX size. Because the retained SHP header
already has version `1000`, no separate version-mismatch branch exists. Any SHX shape code unequal to
the retained valid SHP shape code is `reason=shpMismatch`, even if that SHX code would otherwise be
unsupported. XY values are first checked locally for finiteness/order (`reason=header`) and then their
canonical box is compared with SHP (`reason=shpMismatch`). An entry-count, Java-capacity, or packed-
allocation ceiling checked after the complete header is `reason=entry`. SHX has its own declared
length and it is never compared numerically with the different SHP declared length.

The streamed entries and SHP frames are checked in this exact order for each one-based expected
ordinal:

1. Read the SHX offset and content-length words as signed big-endian integers; the offset must be
   positive and the content length must be at least two words, enough for the required shape code.
2. Convert each word to bytes with checked multiplication by two. The first offset must be exactly 50
   words (byte 100); later raw offsets must be strictly increasing. Every offset is therefore even,
   outside the file header, and representable.
3. Require the converted offset to equal the exact next SHP record-header position derived from the
   prior validated SHP frame. This equality, rather than a loose in-file range check, rejects gaps,
   overlap, duplicates, and reordered entries.
4. At that SHP offset, positionally read exactly eight bytes. Decode only the big-endian SHP content
   word count; it must be at least two, convert without overflow, end within captured SHP size, and
   equal the SHX content word count.
5. Set the next expected SHP position to the checked end of that record and then publish the packed
   entry into the still-private array.

The preflight deliberately does not validate an SHP record number, shape code, bounds, coordinate,
part table, or payload. Those remain cursor work, so the indexed and sequential modes share one
payload decoder and one observable validation order. After the final entry, exact equality between
the derived next SHP position and SHP EOF proves both entry-count agreement and absence of an
unindexed trailing frame. An empty SHX is valid only when the SHP ends at byte 100.

The one opening scratch buffer and packed array are cumulatively charged with G5-001's logical
primitive sizes before their original allocation; reuse never charges the scratch buffer twice. Reads
use the package-private G5-002 file-access seam; no memory map, direct buffer, full-file byte array,
prefetch, cache, background scan, or provider is introduced. Cancellation is checked between entries,
before and after both component reads, before the packed allocation, within controlled loops at no
more than 4,096 primitive units, and before any opening result is published.

#### Indexed cursor equivalence and mutation behavior

When an index is present, `advance()` still visits every packed entry in ascending physical order and
performs G5-002's record-examined, null-skip, payload validation, envelope filtering, ID, result-order,
and query-limit steps unchanged. SHX is not a spatial index and never prunes a viewport query. The
cursor obtains the next packed offset/length, reads the SHP record header again at that offset, and
requires the on-disk record number to equal the expected ordinal and its current content length to
equal the packed value before dispatching to the same Point/MultiPoint decoder. This second validation
detects same-size file mutation after opening rather than trusting preflight forever.

An indexed record-number disagreement uses G5-002's existing `SHAPEFILE_RECORD_NUMBER_INVALID`. After
that check, the current content word follows G5-002's exact order: minimum two words, checked byte/end
arithmetic, captured-file containment, then configured `recordBytes`. Existing framing or
`SOURCE_LIMIT_EXCEEDED` outcomes win. Only a current length that is structurally valid and bounded is
compared with the packed length, before physical-record/query charges or payload work. A valid current
length that differs from the packed index terminates with
`SHAPEFILE_RECORD_LENGTH_INVALID` at the SHP content-length field and exact context
`actualBytes=<current>`, `expectedBytes=<packed>`, and `reason=indexMismatch`. A short read or other SHP
payload/framing problem uses the existing SHP diagnostic. There is no mid-cursor warning, index
discard, retry, or sequential fallback: retrying after records have been returned could duplicate or
reorder results. Cursor failure/cancellation releases its slot and buffers while leaving the source
reusable for a fresh cursor under the same immutable opening snapshot.

The size equality check at cursor open and indexed exhaustion remains mandatory. Missing/ignored SHX
uses the byte-for-byte G5-002 sequential loop. Valid indexed and sequential fixtures must therefore
yield identical IDs, geometries, query accounting, physical order, exhaustion behavior, and terminal
payload diagnostics; their only intentional observable difference is the source opening report.

#### Stable SHX diagnostics

SHX warnings use component `shx`, never copy a path, raw word, raw header value, localized exception
message, or payload. `SHAPEFILE_SHX_MISSING` has no byte offset and empty context. Every ignored-index
warning has exactly `reason=io|header|length|entry|shpMismatch`; only `reason=io` also has
`causeKind=notFound|accessDenied|closed|other`, using G5-002's subclass order. No operation name is
added to this recovered warning.

The location is the earliest trusted place that selects the reason: byte zero for captured-size
preflight; the first failing header field for header/length/cross-header failure; or the failing SHX
entry field for local entry or cross-file mismatch. Failure to read an SHX entry to its captured
boundary uses the first missing SHX byte. Non-throwing EOF during its corresponding SHP frame read uses
the SHX content-length field as specified above. A final SHP-EOF/count mismatch uses the byte
immediately after the last SHX entry. SHX warnings never set `recordNumber`, because preflight
deliberately has not validated an on-disk SHP record number. SHX I/O uses a known requested byte offset
when available and otherwise omits it.

A non-throwing SHX header read that reaches EOF before 100 bytes despite captured size at least 100 is
`SHAPEFILE_SHX_IGNORED`, component `shx`, `reason=length`, at the first missing SHX byte, with no
record number or `causeKind`. A final SHX size recheck that differs from the captured size has the same
warning/reason at byte zero. These are structural length failures; only a thrown `IOException` uses
`reason=io` and `causeKind`.

Reason selection is deterministic: captured-size invariants; header code/reserved, length, version,
shape equality, local bounds, then bounds equality; entry-count/allocation preflight; local entry
validity; SHP comparison; and finally the EOF/count check. A recovered warning is part of the
immutable opening report and is not repeated by every cursor. Later DBF/CPG/PRJ tasks preserve it
before their own warnings or terminal error.

#### Evidence and viewer behavior

Independent byte-level fixtures cover lower/upper SHX selection; missing SHX; exact empty/one/many
indexes; first/last legal offsets; all five reserved words; arbitrary ignored Z/M bits; signed-zero
bounds equality; size and length minus/equal/plus one; trailing bytes; entry count and packed
allocation minus/equal/plus one; negative/zero/overflow words; duplicate/decreasing/gapped/overlapping
offsets; SHP content/count/header disagreement; short reads; same-size mutation; and each exact warning
reason/location/context. Fault-injected tests cover SHX versus SHP I/O classification, cancellation at
every resource/read/allocation/publication checkpoint, custom-token propagation, close failure,
partial-open reverse cleanup, and absence of a retained SHX channel or partial array.

Equivalence tests run the same interleaved Null/Point/MultiPoint query with a valid index, no index,
and every recovered-index category. They compare physical accounting, stable IDs/order, geometry,
filtering, exhaustion, source reuse, early/repeated cursor close, source close with a live cursor, and
post-close metadata/report survival. A valid index plus mutated record number/content length proves
that cursor-time verification terminates without fallback or duplicate publication.

The existing `shapefile-viewer` command and CRS argument remain unchanged. Its temporary fixture is
run once with a valid SHX and once without one, proving equal fit/render behavior, empty versus missing
opening reports, and file release. No bundled corpus, UI index control, random-record example, or
spatial performance claim is added. G5-003 validation runs the focused format and viewer checks, the
normal gate, and whitespace; corpus, fuzz, native, and performance lanes remain later owners.

### PolyLine multipart slice (G5-004)

#### Slice boundary and private type graph

G5-004 replaces only the staged `profile=polyline` branch: a valid SHP header type `3` becomes a
current supported type and publishes its validated header XY box as the conservative source extent.
Header type `5` remains staged as `profile=polygon`; all Z/M and MultiPatch codes remain permanently
unsupported. A type-3 file still accepts exact four-byte null records, while every non-null record
must have shape code `3` under G5-002's existing record-type check.

The public `Shapefiles`, `ShapefileOpenOptions`, `ShapefileLimits`, `FeatureSource`, query, metadata,
and viewer command surfaces do not change. All new implementation peers remain package-private in
`io.github.mundanej.map.io.shapefile`:

```text
ShpMultipartReader -> validated ShpMultipartPayload
PolylineDecoder    -> LineStringGeometry | MultiLineStringGeometry
```

`ShpMultipartReader` owns the common PolyLine/Polygon prefix, count, part-table, coordinate, bounds,
and cancellation mechanics through a two-phase private boundary. `preflight(...)` uses fixed scratch
only and returns scalar-only `ShpMultipartPlan` values: counts, exact byte positions/size, validated
record box, the exact internal `minimumCoordinatesPerPart`, and caller-supplied aggregate/span
diagnostic code/reason constants. PolyLine supplies minimum two,
`SHAPEFILE_PART_TABLE_INVALID/insufficientPoints` for the aggregate, and
`SHAPEFILE_PART_TABLE_INVALID/tooShort` for a span; Polygon's owning design supplies its own fixed
scalars. The reader never infers shape semantics
from the minimum.
The shape decoder computes and prospectively charges common arrays plus its own complete output/
classifier copies. Only after that succeeds does `materialize(plan, ...)` allocate/fill common arrays
and return a cursor-confined `ShpMultipartPayload` owning one canonical packed `double[]`, one
fencepost `int[]`, the record box, and exact coordinate envelope. Arrays are read-only until API
factories defensively copy them and are visible only to same-package decoders in that operation.

This split lets `PolylineDecoder` reserve line geometry copies, while G5-005 reserves polygon
classifier/repacking/geometry copies, without callbacks, a decoder registry, a polygon switch inside
the reader, or a later accounting-policy rewrite. The reader validates only common counts, spans,
finite coordinates, bounds, and canonical packed storage. `PolylineDecoder` separately rejects a
wholly degenerate part and maps the payload to G4 geometry. G5-004 adds no ring policy, generic binary-
parser framework, or empty future helper.

This task depends only on G5-002 and is independently testable with SHP-only fixtures. If G5-003 has
already landed, its sequential and indexed cursor paths dispatch the same validated frame to
`PolylineDecoder`; indexed record-number/length revalidation still occurs before payload dispatch.
If it has not landed, G5-004 neither references nor anticipates an SHX type. One integrator resolves
the shared cursor switch when the parallel changes meet; SHX is not a prerequisite for PolyLine
correctness.

#### Exact record layout and preflight

After G5-002 has validated the record frame, charged the physical record, recorded it as examined,
read shape code `3`, and established the header/record type match, the payload is exactly:

```text
content bytes 0..3                              little-endian shape code 3
content bytes 4..35                             little-endian Xmin, Ymin, Xmax, Ymax
content bytes 36..39                            little-endian signed NumParts
content bytes 40..43                            little-endian signed NumPoints
content bytes 44..(44 + 4 * NumParts - 1)       NumParts little-endian signed point-start indexes
content bytes (44 + 4 * NumParts)..end          NumPoints little-endian X/Y pairs
exact content bytes                              44 + 4 * NumParts + 16 * NumPoints
```

The decoder uses this deterministic order:

1. Require at least the 44-byte prefix and read it completely. Require positive `NumParts`, then
   apply the configured `parts` ceiling; require positive `NumPoints`, then apply the configured
   `points` ceiling.
2. Check that `NumParts + 1`, `2 * NumPoints`, and every byte/array expression fits checked `long`
   arithmetic and Java array capacity. Require `NumPoints >= 2 * NumParts`; this aggregate test is
   only a necessary line-part preflight, not a substitute for reading the table.
3. Derive `44 + 4 * NumParts + 16 * NumPoints` with checked arithmetic and require exact equality to
   the declared content size. No tail, implicit Z/M values, or partial coordinate payload is accepted.
4. Validate the record box in Xmin, Ymin, Xmax, Ymax order and return the scalar-only preflight plan.
   `PolylineDecoder` adds the common parser arrays and its exact immutable line-geometry copies,
   prospectively charges the complete variable total, and passes the accepted plan back for
   materialization before the first variable allocation.
5. Allocate the private fencepost array, stream each part start in source order, and validate/store it.
   The first start is exactly zero; later starts are strictly increasing; every start is below
   `NumPoints`; and every adjacent span, including the last span ending at the appended `NumPoints`
   fence, contains at least two coordinate pairs.
6. Allocate one packed parser coordinate array and stream source-ordered pairs. Canonicalize signed
   zero, reject the first non-finite or out-of-box ordinate (record box before file box), compute the
   exact envelope. Accepted duplicate vertices remain in the packed array unchanged apart from
   signed-zero canonicalization.
7. Require the computed coordinate envelope to remain inside both record and file boxes (already
   established point-by-point). `PolylineDecoder` then scans each fence span and rejects a part with no
   pair distinct from its canonical first pair, constructs the immutable API geometry, applies the inclusive query-
   envelope test only now, and publishes through G5-002's unchanged ID, accounting, cancellation, and
   cursor-state sequence.

Thus framing/type failures retain G5-002 precedence. When present, G5-003's current indexed-length
comparison also precedes this decoder. Inside the decoder, prefix/count/limit/capacity/derived-size
failures precede record-box, part-table, and coordinate failures in the order above. A filtered
PolyLine is still fully decoded and validated, so a malformed record outside the requested envelope
cannot be hidden by either its declared record box or the query. The reader never uses header or
record bounds to skip payload work.

The table and coordinate arrays are validated as they are filled; the phrase "before allocation" in
the planning card means counts, derived sizes, Java capacities, configured limits, and the complete
prospective allocation charge precede variable allocation. It does not require a redundant first
pass over hostile bytes. Every value read into an allocated array is validated before that array is
published, and a failure releases the cursor's operation state without exposing a partial payload.

#### Packed storage, line semantics, and accounting

The file's `NumParts` starts become one fencepost array of length `NumParts + 1`; the final element is
exact `NumPoints`. No per-part coordinate array, list, point object, or copied slice is created. For
one part, `PolylineDecoder` constructs `LineStringGeometry` over the one immutable
`CoordinateSequence`. For more than one part it calls
`MultiLineStringGeometry.of(sequence, fenceposts)`, preserving part and vertex order. That G4 factory
clones the offsets, so the parser's private offsets can be discarded; it never bridges adjacent parts.

Each part has at least two entries and at least two distinct canonical coordinate pairs. The decoder
compares every later pair with the part's first pair using exact primitive equality after signed-zero
canonicalization. A part consisting solely of `(x, y), (x, y)` or more repetitions is rejected;
`A, A, B`, `A, B, B`, and nonconsecutive repeated vertices are accepted and retained. G5-004 makes no
claim about line simplicity, self-intersection, repeated edges, direction, or zero-length interior
segments.

The cursor's fixed reusable prefix scratch grows from 40 to 44 bytes and retains the existing
record-header and 16-byte coordinate scratch; a four-byte part-start scratch may share the prefix
backing region. Fixed capacities are charged once when the cursor is created. After prefix preflight,
the prospective variable charge is exact:

- `4 * (NumParts + 1)` bytes for the parser fenceposts;
- `16 * NumPoints` bytes for the parser ordinates;
- another `16 * NumPoints` bytes for `CoordinateSequence`'s defensive clone; and
- for multipart only, another `4 * (NumParts + 1)` bytes for the immutable multiline offset clone.

Checked cumulative addition against `parserAllocationBytes` occurs before the parser fencepost
allocation; equality succeeds and plus one fails. The `parts` and `points` limit checks occur before
hard Java-capacity checks, matching G5-002's configurable-limit precedence. No allocation is refunded
after a malformed table, filtered result, or failure. G4 query accounting independently charges the
eventual immutable geometry only for a returned record.

Cancellation is checked before and after the prefix, every part-table read, and every coordinate
read; immediately before the variable-allocation charge and each allocation; within validation loops
at no more than 4,096 primitive integers/ordinates; at every part fence; during the line-specific
degeneracy scan at the same cadence; before API construction; and
before current-record publication. A custom token throwable propagates after cleanup. A malformed
record terminates that cursor without resynchronization, releases its slot and operation arrays once,
and leaves the externally serialized source reusable for a fresh cursor. Source/cursor close, short
reads, thrown I/O, same-size mutation limitations, and final size rechecks remain exactly G5-002's.

#### PolyLine diagnostics

The first applicable condition below wins after the shared G5-002/G5-003 frame checks. Locations use
the trusted expected record ordinal, zero-based part index, and absolute SHP byte offset. Context keys
are exactly those shown and are emitted in G4 lexical order; no coordinate value or malformed bytes
are copied.

| Condition | Code | Location | Context |
| --- | --- | --- | --- |
| Declared PolyLine content is shorter than its prefix | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `actualBytes=<declared>`, `expectedBytes=44`, `reason=truncatedPrefix` |
| Prefix read reaches EOF after frame preflight | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, first missing payload byte | `actualBytes=<content bytes read>`, `expectedBytes=44`, `reason=truncatedPayload` |
| `NumParts` is zero or negative | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, `contentStart + 36` | `reason=partCount` |
| `NumParts` exceeds `parts` | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, `contentStart + 36` | `limit=parts`, `maximum=<limit>`, `requested=<count>`, `scope=shapefileCursor` |
| `NumPoints` is zero or negative | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, `contentStart + 40` | `reason=pointCount` |
| `NumPoints` exceeds `points` | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, `contentStart + 40` | `limit=points`, `maximum=<limit>`, `requested=<count>`, `scope=shapefileCursor` |
| Fencepost array cannot fit after configured limits pass | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `contentStart + 36` | `reason=arrayCapacity` |
| Ordinate array cannot fit after configured limits pass | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `contentStart + 40` | `reason=arrayCapacity` |
| `NumPoints` is below checked `2 * NumParts` | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, `contentStart + 40` | `reason=insufficientPoints` |
| Declared content differs from the checked derived size | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `recordStart + 4` | `actualBytes=<declared>`, `expectedBytes=<derived>`, `reason=unexpectedSize` |
| Prospective variable storage exceeds the allocation ceiling | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, `contentStart + 36` | `limit=parserAllocationBytes`, `maximum=<limit>`, `requested=<cumulative bytes after charge>`, `scope=shapefileCursor` |
| Record-box ordinate is non-finite | `SHAPEFILE_COORDINATE_NON_FINITE` | `shp`, expected record, offending box field | `axis=x|y` |
| Record box is unordered | `SHAPEFILE_BOUNDS_MISMATCH` | `shp`, expected record, first offending box field | `bounds=record` |
| Part-start read reaches EOF | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, first missing payload byte | `actualBytes=<content bytes read>`, `expectedBytes=<derived>`, `reason=truncatedPayload` |
| First part start is not zero | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, part 0, its table field | `reason=firstNotZero` |
| Later part start is not greater than its predecessor | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, offending part, its table field | `reason=notIncreasing` |
| Part start is at or beyond `NumPoints` | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, offending part, its table field | `reason=outOfRange` |
| Adjacent fenceposts leave fewer than two entries | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, short part, its table field | `reason=tooShort` |
| Coordinate read reaches EOF | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, first missing payload byte | `actualBytes=<content bytes read>`, `expectedBytes=<derived>`, `reason=truncatedPayload` |
| Coordinate ordinate is non-finite | `SHAPEFILE_COORDINATE_NON_FINITE` | `shp`, expected record, offending ordinate field | `axis=x|y` |
| Coordinate is outside record or file box | `SHAPEFILE_BOUNDS_MISMATCH` | `shp`, expected record, first offending ordinate field | `bounds=record|file` |
| A complete part has fewer than two distinct pairs | `SHAPEFILE_PART_TABLE_INVALID` | `shp`, expected record, degenerate part, its table field | `reason=degenerate` |

For the last part, `tooShort` is located at its own stored start field even though the synthetic final
fence is `NumPoints`; part `i`'s table field is exact `contentStart + 44 + 4 * i`. Duplicate starts
select `notIncreasing`; a negative later start also selects `notIncreasing` before `outOfRange`, while
a positive start at or above `NumPoints` selects `outOfRange`. Record-box checks precede table checks,
and within the coordinate stream finiteness, record containment, then file containment precede the
later PolyLine-specific degeneracy scan. Existing shared I/O, cancellation, record-length, and limit
report shapes are not duplicated or widened.

The signed 32-bit part/point fields cannot overflow these checked `long` expressions: even both at
`Integer.MAX_VALUE` produce at most 42,949,672,984 derived content bytes. Checked arithmetic remains
mandatory for consistency, but maximum counts reach configured limits, Java-array capacity,
derived-size mismatch, or cumulative allocation-limit handling rather than an unreachable decoder-
specific `reason=overflow`.

#### Evidence and viewer behavior

Independent byte fixtures cover one part, several parts, interleaved nulls, exact part/vertex order,
signed-zero equivalence, accepted interior/consecutive duplicates, self-crossing lines, and a wholly
degenerate part. Boundary matrices exercise prefix/count/derived-size arithmetic, parts/points/
allocation minus-equal-plus-one, Java capacities, every table reason, short table/coordinate reads,
non-finite fields, conservative and violated boxes, cancellation checkpoints, cursor failure/reuse,
and the exact diagnostics above. Source integration pins `record:<ordinal>` identity, singular versus
multipart fencepost mapping, full validation before filtering, stable order, and absence of per-part
objects. A type-5 header remains staged and a type-5 record in a type-3 file remains a record mismatch.

The unchanged `shapefile-viewer <path.shp> <EPSG:4326|EPSG:3857>` command uses its existing explicit
line symbol. A temporary SHP-only type-3 fixture contains a null, one single-part line, and one
multipart line. Query tests assert both geometry values; offscreen tests sample each visible part and
the background gap that would be painted by an erroneous bridge, then verify metadata fit and file
release. Tests do not compare a complete image or bundle a corpus. When G5-003 is present, one paired
valid-SHX fixture additionally proves indexed/sequential decoder equivalence; the core G5-004 fixture
and task dependency remain SHX-independent.

G5-004 validation runs the focused format and viewer checks, the normal gate, and whitespace. It does
not run corpus, render-regression, native, fuzz, or performance lanes owned by later tasks.

### Polygon holes and multipart slice (G5-005)

#### Slice boundary and private decoder

G5-005 replaces only the staged `profile=polygon` branch. A valid SHP header type `5` becomes a
current supported type and publishes its validated XY box as the conservative source extent. A
type-5 file accepts exact four-byte null records; every non-null record must have shape code `5`
under the existing record-type check. PolyLine and the point shapes are unchanged, and Z/M and
MultiPatch remain unsupported.

No public API, option, limit, module, command, or diagnostic code is added. The only new production
peer is package-private `PolygonDecoder` in `io.github.mundanej.map.io.shapefile`; there is no
`internal.shp.polygon` subpackage, public format geometry, ring abstraction, topology service, or
decoder registry. It reuses G5-004's same-package `ShpMultipartReader`, `ShpMultipartPlan`, and
`ShpMultipartPayload`:

```text
ShpMultipartReader.preflight(
    ...,
    minimumCoordinatesPerPart = 4,
    aggregateMinimumCode = SHAPEFILE_RING_INVALID,
    aggregateMinimumReason = tooShort,
    spanMinimumCode = SHAPEFILE_RING_INVALID,
    spanMinimumReason = tooShort)
    -> scalar ShpMultipartPlan
PolygonDecoder reserves the complete polygon operation
ShpMultipartReader.materialize(plan, ...)
    -> validated source-order ShpMultipartPayload
PolygonDecoder
    -> PolygonGeometry | MultiPolygonGeometry
```

The common reader continues to own record layout, count/limit/capacity checks, exact derived size,
record and file bounds, part starts, coordinate reads, signed-zero canonicalization, and short-read
handling. `PolygonDecoder` owns ring closure, signed area/orientation, hole association, output order,
and polygon-specific diagnostics. The reader does not acquire a shape switch or callback. Its
scalar plan carries the caller-selected minimum plus the exact aggregate/span diagnostic code and
reason scalars; the common reader never infers a shape from the number four. PolyLine supplies
`SHAPEFILE_PART_TABLE_INVALID/reason=insufficientPoints` for aggregate failure and
`SHAPEFILE_PART_TABLE_INVALID/reason=tooShort` for a materialized span. Polygon supplies
`SHAPEFILE_RING_INVALID/reason=tooShort` for both. These fixed package constants are passed explicitly,
not selected by a reader switch, callback, registry, or arbitrary external configuration.

If G5-003 is present, indexed and sequential cursors dispatch the same validated frame to this
decoder; indexed record-number and length checks still precede payload work. G5-005 otherwise has no
SHX dependency. One owner integrates the cursor switch when parallel branches meet.

#### Record validation and precedence

Polygon uses the exact G5-004 multipart layout and exact content size
`44 + 4 * NumParts + 16 * NumPoints`. After frame/type checks and the fixed 44-byte prefix, processing
is deterministic:

1. Require positive `NumParts`, apply `parts`, require positive `NumPoints`, and apply `points`.
2. Check `NumParts + 1`, `2 * NumPoints`, every byte expression, and every required array capacity.
   Polygon additionally requires checked `POLYGON_EXACT_LIMBS * NumParts` to fit one Java `int[]`;
   this shape-specific capacity failure precedes aggregate ring-size, derived-size, and allocation-
   budget checks. Then require `NumPoints >= 4 * NumParts` with checked arithmetic as the necessary
   aggregate ring-size preflight.
3. Require the declared content size to equal the checked derived size and validate the record box.
4. Prospectively charge the complete count-derived polygon reservation below. Only then may the
   common reader allocate its fenceposts and packed coordinates.
5. Materialize the source-order part table and coordinates. Part zero starts at zero, later starts
   strictly increase, every start is below `NumPoints`, and every span through the synthetic final
   fence has at least four entries. Coordinates are finite, canonical, and inside both record and file
   boxes exactly as in G5-004.
6. Validate every ring in source order before classifying any hole. Require exact canonical first/
   last coordinate equality, then compute the bounded signed-area representation below and require a
   nonzero result. A negative signed area is a clockwise shell; a positive area is a counterclockwise
   hole. Orientation is never reversed or repaired.
7. Classify holes in source order against shells in source order using the exact bounded algorithm
   below. Any unclassifiable relationship terminates the record and cursor; no ring, record, or
   diagnostic is skipped.
8. Build source-stable polygon groups, construct the immutable G4 geometry, then apply the inclusive
   query-envelope test. Only after full validation and filtering does the existing cursor publish
   `record:<ordinal>` and charge returned G4 payload.

Shared frame, length, count, limit, capacity, bounds, table, coordinate, and I/O failures therefore
precede all ring failures. Across rings, closure/area validation completes in source order before the
first association comparison, so a later malformed ring cannot be hidden by an earlier orphan hole.
A filtered polygon is still completely validated; header/record boxes never bypass payload or
topology work.

#### Exact binary64 area and predicates

Each ring retains all source coordinates including its repeated close and any consecutive duplicate
vertices. Its shoelace terms visit edges `start..end - 2`, including the final edge into the repeated
close. Orientation, zero-area, equality, point tests, and segment contact use the exact values of the
accepted binary64 ordinates; no common scaling, rounded subtraction, epsilon, `BigInteger`,
`BigDecimal`, or per-edge object is permitted.

`PolygonDecoder` decomposes each finite double from raw bits into sign, an unsigned significand of at
most 53 bits, and a power of two in the exact identity `value = sign * significand * 2^exponent`.
Subnormal zero/nonzero and normal values are handled directly; canonical zero contributes nothing.
A coordinate product therefore has at most 106 significant bits and an exponent from -2148 upward.
The decoder multiplies the two significands into four base-2^32 limbs and adds/subtracts that shifted
product into a fixed sign/magnitude accumulator.

The exact bound is closed and independent of input magnitudes: a product's lowest possible bit is
2^-2148; a cross-product difference is below 2^2049; and summing fewer than `Integer.MAX_VALUE`
edges is below 2^2080. `POLYGON_EXACT_LIMBS = 133` 32-bit limbs therefore covers 4,256 bits and the
entire exponent range with margin. Carry/borrow beyond that fixed segment is an implementation
invariant failure, never a hostile-input diagnostic. Tests derive these bounds independently and use
extreme normal/subnormal coordinates.

Ring `i` accumulates every exact `x[j] * y[j+1] - x[j+1] * y[j]` directly into segment
`i * POLYGON_EXACT_LIMBS` of one packed `int[]`. Zero magnitude is `reason=zeroArea`; the exact sign is
clockwise/counterclockwise orientation. Absolute area comparison scans two fixed segments from their
highest limb down, so equality and smallest-shell selection are exact without reconstructing a
floating value. The existing packed ownership/role array carries the sign/role state; no area object
or per-ring limb array exists.

An orientation predicate clears one fixed 133-limb scratch segment and accumulates the algebraically
exact six raw products for `orient(a,b,c)`: `cross(b,c) - cross(a,c) + cross(a,b)`. Its exact sign/zero
drives ray crossings and segment intersection. Closed-interval coordinate comparisons use the
original canonical doubles. `PolygonDecoder` polls cancellation before every orientation predicate
and counts every limb clear/read/write/carry/borrow plus each ring edge as controlled primitive work.
Area accumulation, area comparison, predicate scratch work, and later packed grouping therefore poll
at no more than 4,096 such primitive units, independently of the higher-level topology-comparison
counter. These operations are exact for the supplied finite binary64 coordinates; they make no
arbitrary-real or general topology claim.

#### Bounded shell and hole classification

Ring bounds, signed area representations, ownership, group cursors, and output order are packed
primitive arrays indexed by the source ring number. Shells are polygon components in source-ring
order, including clockwise shells nested inside another shell or hole. A nested clockwise island is
therefore a separate polygon component, not a hole reversal or inferred hierarchy.

For each counterclockwise hole, inspect every clockwise shell in source order:

1. Prospectively charge one `topologyComparisons` unit and classify the closed ring envelopes as
   disjoint, overlapping/touching, or strict containment. Strict containment is exactly
   `hole.minX > shell.minX`, `hole.minY > shell.minY`, `hole.maxX < shell.maxX`, and
   `hole.maxY < shell.maxY`. Closed-disjoint bounds perform no edge work. A non-strict overlap can
   never become a candidate, but still enters the contact phase so ordinary bound-touch/crossing is
   not mislabeled orphan.
2. For strict containment only, test the hole's first coordinate against the shell with an even-odd
   horizontal ray. Edges are visited once in ring order and prospectively charged once each. For an
   edge `(a,b)`, upward `a.y <= p.y < b.y` with positive exact orientation, or downward
   `b.y <= p.y < a.y` with negative orientation, toggles inside. Exact zero whose point lies on the
   closed edge is immediate `reason=contact`. Retain the final inside/outside result.
3. For every non-disjoint envelope relation, compare every hole edge with every shell edge, charging
   before each pair. Exact endpoint equality, an exact-zero orientation on a closed segment,
   collinear overlap, or a proper crossing is contact and terminates the record with
   `reason=contact`. With no contact, the shell is a candidate only when bounds were strict and the
   retained point result was inside.

Among candidates, the shell with the smallest comparable absolute area wins. An equal candidate marks
a tie; a later strictly smaller candidate replaces the winner and clears that tie. A tie still set
after the final shell produces `reason=equalInnermost`; no candidate produces `reason=orphan`. A
contact discovered in any overlapping shell relation is terminal even if a different shell could
contain the hole.
Holes retain source order within the selected shell.

The topology counter is cumulative for the cursor, not reset per ring or record. Before any comparison
that would exceed the configured maximum, failure reports requested `maximum + 1`; equality succeeds.
If the current total and configured maximum are both `Long.MAX_VALUE`, the prospective increment is
detected before arithmetic and fails with the common saturated `requested=Long.MAX_VALUE` sentinel;
the equality of those two context values does not turn overflow into acceptance.
One package-private static production seam on `PolygonDecoder`,
`checkTopologyIncrement(SourceIdentity, DiagnosticLocation, current, maximum) -> long`, owns this
prospective arithmetic and exact report construction. On success it returns `current + 1`, which the
classifier uses as its new cumulative total. It throws `SourceException` before addition when
`current == Long.MAX_VALUE`, reporting saturated `requested=Long.MAX_VALUE`; otherwise it throws when
the computed value exceeds the configured maximum. Ordinary classification calls it; a direct unit
test sets both counters to `Long.MAX_VALUE` without requiring an impossible SHP fixture or adding a
helper type.
Cancellation is checked before each classification phase and at most every 4,096 charged comparisons.
The stricter limb/edge cadence above can poll more frequently inside one comparison. Ring validation
and the later linear grouping loops use the same 4,096-primitive cadence even though they do not
consume topology units. No shell-shell, hole-hole, or within-ring simplicity comparisons are added.

After classification, linear counting placement avoids a second quadratic loop. The ownership array
stores each shell's polygon ordinal and each hole's source-shell index; one packed count/cursor array
and one packed source-ring-order array place each shell first and its holes afterward. Shell groups
remain in shell source order, and holes in a group remain in hole source order.

#### Geometry mapping and allocation reservation

One shell maps to `PolygonGeometry`; more than one maps to packed `MultiPolygonGeometry`. For the
singular value, the decoder allocates one primitive slice per final exterior/hole ring and lets each
`CoordinateSequence` make its required defensive copy; only the final immutable hole reference list
is retained. For multipart output, it allocates one reordered packed ordinate array, ring fenceposts,
and polygon-ring fenceposts and passes them through the G4 defensive factories. The parser creates no
point object, candidate object, ring carrier, per-edge object, or list of parser rings. The unavoidable
public `PolygonGeometry` coordinate values are not treated as parser classifier objects.

Because the shell count is unknown before materialization, allocation acceptance cannot depend on a
later favorable classification. Let `p = NumParts` and `n = NumPoints`. Before the first common
variable allocation, checked arithmetic reserves all of these cumulative logical capacities:

- common payload: `4 * (p + 1)` fencepost bytes plus `16 * n` packed-coordinate bytes;
- polygon classifier: `32 * p` ring-bound bytes, checked
  `4 * POLYGON_EXACT_LIMBS * p` exact-area bytes, three `4 * p` ownership/count/order arrays, and one
  fixed `4 * POLYGON_EXACT_LIMBS` predicate scratch; and
- the larger possible public-output branch: `32 * n` bytes for input/reordered ordinates and their
  immutable coordinate copies, plus `16 * (p + 1)` bytes covering parser/API ring and polygon
  fenceposts. This offset allowance also exceeds the singular branch's at-most `8 * (p - 1)` retained
  hole-reference slots and any parser reference slots used to assemble that immutable list.

The reservation is derived only from accepted counts, is charged once to the existing cumulative
`parserAllocationBytes` counter, and is never refunded after a malformed, filtered, or singular
record. Actual allocations must fit their reserved category; an implementation may reuse a packed
array or consume less than the worst-case branch but may not allocate an uncharged representation.
This deliberately accepts some conservative rejection near the ceiling in exchange for deterministic
pre-allocation safety. Configured `parts` and `points`, checked arithmetic, and Java capacities retain
their G5-004 precedence before this allocation check.

Cancellation is additionally checked before reservation, every variable allocation, every output
copy/factory call, and publication. A failure discards all operation arrays, terminates and releases
the cursor slot once, and leaves the serialized source available to a new cursor. No charge refund,
resynchronization, partial geometry, or recoverable record skip occurs.

#### Polygon diagnostics

The first applicable condition below wins after shared G5-002/G5-003 framing and G5-004 common
prefix/count/size/bounds checks. Source part indexes and byte offsets are zero-based; record number is
the trusted expected physical ordinal. Context keys are exactly those shown and retain G4 lexical
ordering.

| Condition | Code | Location | Context |
| --- | --- | --- | --- |
| Packed exact-area limb array cannot fit after configured limits | `SHAPEFILE_RECORD_LENGTH_INVALID` | `shp`, expected record, `contentStart + 36` | `reason=arrayCapacity` |
| `NumPoints` is below checked `4 * NumParts` | `SHAPEFILE_RING_INVALID` | `shp`, expected record, `contentStart + 40` | `reason=tooShort` |
| Prospective complete polygon storage exceeds allocation | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, `contentStart + 36` | `limit=parserAllocationBytes`, `maximum=<limit>`, `requested=<cumulative bytes after reservation>`, `scope=shapefileCursor` |
| A materialized part span has fewer than four entries | `SHAPEFILE_RING_INVALID` | `shp`, expected record, short ring, its part-table field | `reason=tooShort` |
| Ring first and last canonical pairs differ | `SHAPEFILE_RING_INVALID` | `shp`, expected record, offending ring, last coordinate X field | `reason=open` |
| Exact signed-area accumulator is zero | `SHAPEFILE_RING_INVALID` | `shp`, expected record, offending ring, its part-table field | `reason=zeroArea` |
| Next topology comparison exceeds its ceiling | `SOURCE_LIMIT_EXCEEDED` | `shp`, expected record, current hole ring, its part-table field | `limit=topologyComparisons`, `maximum=<limit>`, `requested=<maximum + 1 saturated at Long.MAX_VALUE>`, `scope=shapefileCursor` |
| Hole touches or crosses an overlapping shell | `SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS` | `shp`, expected record, hole ring, its part-table field | `reason=contact` |
| Hole has no candidate shell | `SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS` | `shp`, expected record, hole ring, its part-table field | `reason=orphan` |
| Hole has equal smallest-area candidate shells | `SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS` | `shp`, expected record, hole ring, its part-table field | `reason=equalInnermost` |

Part-table field offsets remain `contentStart + 44 + 4 * partIndex`; the last-coordinate X field is
the coordinate-area start plus `16 * (ringEnd - 1)`. An aggregate too-short failure has no part index
because no table entry has yet identified the first short ring. For an individual short span, the
first such source ring wins. Closure wins over zero area for a ring; all structural ring failures win
over association. During association, hole order, shell order, bounds, point edges, and edge pairs
fix the diagnostic and limit/cancellation position. No coordinates, areas, malformed bytes, or shell
candidate identifiers enter context.

Structurally accepted self-crossing rings and overlapping shell-shell or hole-hole peers remain inside
G5-001's explicit non-validity boundary. A self-crossing ring whose exact whole-ring area is zero still
fails `zeroArea`; a nonzero one is retained. Polygon rendering uses the existing component-local
even-odd rule: each shell plus its assigned holes is one fill path, and distinct polygon components
are painted independently in shell order. The reader never unions all record rings into one path.

#### Evidence and viewer behavior

Hand-built SHP-only fixtures cover one shell, one shell with holes, several disjoint shells, multiple
holes, holes preceding shells, nested clockwise islands, canonical signed-zero closure, consecutive
duplicates, and stable singular/multipart packed order. Negative matrices cover every short/open/
zero-area reason, all-hole input, orphan/contact/equal-innermost holes, non-finite and bounds failures,
truncated table/coordinates, count/capacity/allocation boundaries, topology minus/equal/plus one,
cancellation at every phase, cursor cleanup/reuse, and full validation of an off-query malformed
record. Separate fixtures pin accepted nonzero-area self-crossing rings and overlapping shell-shell or
hole-hole peers without calling them generally valid.

The unchanged `shapefile-viewer <path.shp> <EPSG:4326|EPSG:3857>` command uses its explicit fill
symbol for a temporary type-5 fixture. Source assertions prove record identity, source-stable shell/
hole order, singular versus packed multipart mapping, fit, and file release. Offscreen assertions
sample a shell interior, assigned-hole interior, disjoint-shell interior, gap, and nested-island
interior. They also use path winding where helpful to prove each component's even-odd path is local;
there is no whole-image golden or bundled corpus. A paired valid SHX fixture, when G5-003 is present,
pins indexed/sequential equality without making SHX a dependency.

G5-005 validation runs the focused format and viewer checks, the normal gate, and whitespace. It does
not run corpus, render-regression, native, deterministic fuzz, or performance lanes owned later.
