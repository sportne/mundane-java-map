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

The 100-byte header requires file code `9994`, version `1000`, zero reserved words, a supported type,
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
checks shells in source order, first by strict bounding-box containment, then by a point-in-ring test,
then by all hole-edge/shell-edge pairs needed to reject crossing or touching. Each bounding-box test,
point-versus-shell-edge step, and segment-pair test prospectively charges one
`topologyComparisons` unit. A shell is a candidate only when the hole is strictly inside with no edge
or vertex contact; the candidate with smallest absolute area wins, and holes retain source order
within it. Hole-to-candidate-shell edge or vertex touching, an orphan hole, equal competing innermost
shells, or another undecidable association terminates the record with
`SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS`. The counter checks cancellation within 4,096 comparisons and
terminates with `SOURCE_LIMIT_EXCEEDED` before comparison `maximum + 1`.

This is deliberately a structural polygon profile. It does not perform shell-shell, hole-hole, or
within-ring simplicity analysis beyond whole-ring area and the hole-association checks above. A ring
that self-crosses away from the association test, or peer shells/holes that overlap, is therefore
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
lookup strings, diagnostic storage (bounded independently), caller-owned paths/options, and JDK channel
objects are excluded. As in G4, fixed immutable carrier headers and their scalar reference fields have
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
