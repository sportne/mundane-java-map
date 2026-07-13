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
or general WKT semantic engine. The tokenizer accepts bracketed WKT1 identifiers, bounded bare
identifier scalar arguments, quoted strings, commas, and finite decimal numbers, with at most 16
nesting levels and 512 tokens. Insignificant ASCII whitespace and numeric lexical differences compare
equal; keyword matching is ASCII-case-insensitive, while quoted names and child order are exact.
Extra/reordered nodes simply produce retained unknown metadata.

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
still verifies that the supplied definition is exactly registered. G5-002 lands no inactive encoding
value; G5-006 adds the approved `DbfEncoding` wither/private field without invalidating these factories.

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

The format's public types and every package-private implementation peer share
`io.github.mundanej.map.io.shapefile`. Java package-private access does not cross subpackages, and the
project has no JPMS export boundary that would make public `internal.*` types private to the module.
Consequently this format does not create misleading `internal.shp`, `internal.shx`, `internal.dbf`,
`internal.cpg`, or `internal.prj` packages. Behavior remains separated by package-private
`Shapefile*`/`Shp*`/`Shx*`/`Dbf*`/`Cpg*`/`Prj*` classes and source files in the one package; only
`Shapefiles`, `ShapefileOpenOptions`, `ShapefileLimits`, and, from G5-006, `DbfEncoding` are externally
accessible. Future ownership reserves those behavior-specific files but creates no empty classes. One
integrator owns edits to the shared opener, source, cursor
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
`operation=probe|open|size|read|close` in that lexical order. A known requested read position is the byte
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

### DBF/CPG attributes and encoding (G5-006)

#### Delivery and public boundary

G5-006 replaces the staged `profile=dbf|cpg` branches after G5-002's SHP opening. It is independently
implementable from G5-002 and does not require SHX: when G5-003 is already integrated, the shared
facade may compose its previously validated optional index count into the eager check below; without
it, every count mismatch uses the sequential path. G5-006 does not alter SHP geometry, record IDs,
source order, SHX addressing, PRJ staging, or the viewer command. A missing
DBF now follows the approved recoverable policy instead of the staged branch; a present DBF becomes
the dataset's only attribute table and is never discarded after a parse or alignment failure.

The format module adds one public enum and completes the existing options value:

```text
DbfEncoding = UTF_8 | ISO_8859_1 | WINDOWS_1252 | IBM437 | IBM850

ShapefileOpenOptions
  dbfEncodingOverride() -> Optional<DbfEncoding>
  withDbfEncodingOverride(DbfEncoding) -> ShapefileOpenOptions
  withoutDbfEncodingOverride() -> ShapefileOpenOptions
```

The override defaults absent, participates in value equality and bounded `toString`, and composes
with every earlier wither. `DbfEncoding` is a closed format token, not a `Charset` wrapper, decoder
SPI, or alias registry. Null input follows the existing parameter-named `NullPointerException` rule.
No DBF parser, row, field, channel, or sidecar type is public, and nothing changes
`mundane-map-api`.

Every implementation peer remains package-private in
`io.github.mundanej.map.io.shapefile`. `DbfReader` validates and opens one `DbfTable`; `CpgReader`
recognizes the bounded byte token; and `DbfValueDecoder` converts selected scalar slices. The table
owns its channel, captured size/layout, selected encoding, immutable schema, and one packed field
plan: physical names in `String[]`, type codes in `byte[]`, and stride-four `int[]` entries for row
offset, width, decimal count, and supported-schema ordinal (`-1` for unsupported). These are ordinary
behavior-specific files in the existing package, not an `internal.dbf` subpackage or generic reader
framework.

Missing DBF and a valid DBF with no supported fields both expose a present empty
`AttributeSchema`; present DBF otherwise exposes one nullable field per supported descriptor in
physical order. This intentionally changes G5-002's temporary absent-schema metadata. The source
retains no feature count because null/deleted records still prevent a cheap exact count. Returned
records use the G4 canonical scalar values and immutable ordered map; their exact ID remains
`record:<physical ordinal>`.

#### Opening transaction and field plan

After the earlier SHP and optional SHX work, opening executes this fixed DBF/CPG sequence:

1. If DBF is absent, append `SHAPEFILE_DBF_MISSING`, install the present empty schema, and retain no
   DBF handle. If CPG is present, append `SHAPEFILE_CPG_WITHOUT_DBF` without opening or interpreting
   it. Continue to the still-staged PRJ branch.
2. If DBF is present, check cancellation, open its positional channel, capture size, enforce
   `componentBytes`, and validate a fixed 32-byte header. Derive field count only when
   `headerLength == 32 + 32 * fieldCount + 1`; enforce the configured row/field/width ceilings, hard
   unsigned DBF sizes, checked Java capacities, and exact file layout
   `headerLength + rowCount * recordLength` followed by either EOF or one `0x1a` byte and EOF.
3. When independently completed G5-003 behavior is present and its valid SHX already supplies the
   exact physical SHP count, compare it with DBF `rowCount` now. A mismatch terminates before
   descriptor allocation or warnings. A G5-002-only implementation, ignored/missing SHX, or a
   concurrently developed branch adds no eager SHP scan and uses the required sequential checks.
4. Prospectively charge the opening allocation, then stream 32-byte descriptors in physical order.
   Validate names, types, widths, decimal counts, version-specific status bytes, the `0x0d`
   terminator, and the exact checked row-width sum. Duplicate detection compares prior bounded ASCII
   names under ASCII case folding without another map. Unsupported descriptors retain their width
   and physical offset, are omitted from schema, and append one warning in descriptor order.
5. Read at most `maximumCpgBytes` plus one detection byte, close the CPG handle during opening, and
   resolve encoding using the approved fixed aliases and LDID table. Build the immutable schema and
   continue through the still-staged PRJ check. DBF remains transaction-owned until every opening
   stage and the final cancellation checkpoint succeeds; only then are SHP and DBF transferred
   together to the source.

The `0x03`, `0x04`, and `0x05` header/status rules, supported descriptor shapes, scalar mappings, and
ignored bytes are exactly the G5-001 profile; this slice does not infer newer dBASE semantics from an
`F` field. The opening allocation includes reusable header/descriptor/suffix scratch, exact CPG bytes,
packed plan arrays, reference slots, every constructed name, temporary field references, and retained
schema slots before the corresponding allocation. Duplicate comparison and token recognition use
bounded existing storage rather than a hash map or retained CPG string. Allocation arithmetic is
prospective and cumulative under `scope=shapefileOpen`; a failed open refunds nothing and closes CPG,
DBF, then SHP in reverse acquisition order.

A temporary CPG close failure after successful read is terminal `SHAPEFILE_IO_FAILED` at component
`cpg` with no byte offset and exact context `causeKind=<bounded token>`, `operation=close`. If read or
another earlier stage already failed, that failure remains primary and the close throwable is
suppressed without a second diagnostic. The same temporary-sidecar rule is available to later PRJ
work; ordinary cursor/source-owned handle cleanup remains `SOURCE_CLOSE_FAILED` as previously defined.

Opening warnings preserve the complete encounter order established by prior tasks: recovered SHX
warning first when applicable; then missing DBF/CPG-without-DBF, or unsupported DBF fields in
descriptor order; then CPG and LDID resolution warnings. PRJ diagnostics remain later. G4 warning
retention/omission applies to this one ordered stream without changing parser decisions.

#### Finite encoding resolution

Selection is caller override, recognized CPG, recognized LDID, then `WINDOWS_1252`. CPG is parsed
directly from bounded bytes using G5-001's exact BOM, ASCII whitespace, one-token, alias, and near-miss
rules. A malformed or unknown CPG emits `SHAPEFILE_CPG_INVALID` and is not also a conflict. After a
selection exists, each recognized lower-priority hint that differs emits its own conflict in physical
CPG-then-LDID order. An unknown nonzero LDID is silent when a higher choice exists; without one it
participates only in the final fallback warning. An override is inert when DBF is absent.

UTF-8 and ISO-8859-1 use reporting decoders obtained only from the exact `StandardCharsets`
constants. Windows-1252, IBM437, and IBM850 use reviewed private 256-code-unit lookup strings
generated into source from the G5-001 mappings. The five undefined Windows-1252 entries carry one
private invalid sentinel. Each single-byte input indexes the table as unsigned; no replacement,
platform default, `Charset.forName`, provider lookup, locale, reflection, or automatic discovery is
used. Tests pin all 256 entries and a committed checksum for each table, so source generation is not
a build/runtime dependency.

#### Positional row alignment and projection

`openCursor` validates `ONLY` names against the now-present schema before claiming the one-cursor
slot. It then builds a packed projection in physical-field order with each selected field's requested
output position. `NONE` selects no fields and needs no value decoder. Reordered `ONLY` still decodes
physical slices in field order for deterministic warnings, then assembles the immutable result in
request order. `ALL` publishes schema order. Because unsupported fields are absent from the known
schema, they cannot be selected.

The cursor reserves one maximum-selected-width byte scratch, one matching character scratch, selected
value/reference slots, and exact projection arrays. It never reads a complete DBF row merely to
project a subset. When metadata has no `DbfTable`, the cursor allocates none of that DBF state and
follows the existing complete SHP validation/filter/publication path with the known empty schema and
empty attributes; it performs no DBF ordinal, count, marker, value, encoding, or size work. The
following alignment sequence applies only when a `DbfTable` is present. For each trusted SHP frame it:

1. charges physical/query examination, requires the matching DBF physical ordinal, computes the row
   and field offsets with checked `long` arithmetic, and reads the one deletion marker positionally;
2. fully validates the SHP payload and geometry even when the matching row is deleted, the shape is
   null, or the query later filters it, so DBF never conceals malformed SHP;
3. publishes nothing for null shapes or deleted rows and decodes no values for either outcome;
4. applies the inclusive geometry-envelope query, decoding no DBF value for a filtered live record;
5. for a live match, reads only selected supported field slices in physical order, converts them under
   the approved type rules, records selected-field warnings in that order, and stores values at their
   output positions; then
6. constructs the normal `FeatureRecord`, charges G4 returned payload independently, checks
   cancellation immediately before current-state publication, and yields it.

An unselected invalid value cannot warn. Blank values become `AttributeNull` without warning;
malformed selected values become `AttributeNull` with exactly one warning and do not realign or reject
the row. Text preserves leading/interior spaces and right-trims only the approved trailing spaces.
Numeric/date/logical parsing is locale-independent; checked manual parsing is preferred where it
avoids a temporary string, and any required temporary string is charged before construction.

Without SHX, DBF too-few is discovered before the first unmatched SHP ordinal; DBF too-many is
discovered at exact SHP exhaustion before returning false. A deliberately early cursor close does
not scan ahead merely to discover an excess DBF row. With valid SHX, both mismatches fail at open.
Clean cursor open and exhaustion compare current DBF size with its captured size after the analogous
SHP check. Same-size external mutation remains unsupported snapshot behavior, but every consumed
marker/value byte is still validated.

#### Counters, cancellation, diagnostics, and cleanup

Cursor parser allocation charges projection/scratch arrays once and every decoded string, scalar,
temporary value slot/map entry, and discarded intermediate cumulatively. Successful or malformed
text charges every decoded UTF-16 unit actually produced before a result/substitution; single-byte
decoders charge before writing each character and the reporting UTF-8 decoder charges its produced
buffer position even on malformed input. G4 query accounting separately charges the published record
and canonical attribute map. No filtered, deleted, invalid, or yielded value refunds either counter.

Cancellation is polled before/after every DBF/CPG I/O and allocation, between descriptors/rows/fields,
within at most 4,096 controlled bytes/characters/value operations, before a substitution warning, and
before publication. Cancellation or a known format/source failure releases operation scratch and the
one-cursor slot, leaving the serialized source reusable. Source close first closes a live cursor,
then DBF, then SHP; it continues after failure and retains the first cleanup failure with later ones
suppressed. CPG never survives opening. Repeated close and report/value lifetime remain exactly G4.

The stable condition refinements are:

| Condition | Code and location | Exact context |
| --- | --- | --- |
| Unsupported version/status, invalid lengths/layout/terminator/suffix, or captured short read | `SHAPEFILE_DBF_HEADER_INVALID` at the first responsible DBF header/layout byte | `field=version|transaction|encryption|mdxFlag|rowCount|headerLength|recordLength|terminator|fileLayout` and, only when needed, `reason=unsupported|nonZero|mismatch|truncated|trailingData` |
| Invalid or duplicate descriptor name, supported width/decimal error, or row-sum mismatch | `SHAPEFILE_DBF_FIELD_INVALID` at the descriptor field, with physical field index and name only after validation | `reason=nameEmpty|nameUnterminated|nameNonAscii|nameWhitespace|nameDuplicate|width|decimals|rowLayout` |
| Unsupported descriptor | warning `SHAPEFILE_DBF_FIELD_UNSUPPORTED` at its type byte and field index/name | empty |
| Invalid live/deleted marker | `SHAPEFILE_DBF_RECORD_MARKER_INVALID` at the physical row marker | empty |
| Invalid selected scalar | warning `SHAPEFILE_DBF_VALUE_INVALID` at the field slice with physical row/index/name | `reason=embeddedZero|encoding|syntax|overflow|scale|nonFinite|logical|date` |
| Known SHX/DBF count mismatch | `SHAPEFILE_DBF_RECORD_COUNT_MISMATCH` at DBF row-count byte 4 | `dbfRows=<count>`, `shpRecords=<count>` |
| Sequential DBF ends first | same code at first missing DBF row with the unmatched physical ordinal | `dbfRows=<count>`, `requiredOrdinal=<ordinal>` |
| Sequential SHP ends first | same code at the first excess DBF row | `dbfRows=<count>`, `shpRecords=<examined count>` |
| Empty, non-ASCII, multi-token, or unknown CPG | warning `SHAPEFILE_CPG_INVALID` at first invalid byte, or byte zero for empty/unknown | `reason=empty|nonAscii|multipleTokens|unknown` |
| Recognized lower hint differs | warning `SHAPEFILE_ENCODING_CONFLICT` at component `cpg` or DBF LDID byte 29 | `ignored=<DbfEncoding>`, `selected=<DbfEncoding>` |
| No recognized selection | warning `SHAPEFILE_ENCODING_FALLBACK` at DBF LDID byte 29 | `selected=WINDOWS_1252` |

Missing-sidecar codes retain the G5-001 empty contexts. DBF positional EOF within a layout already
validated against captured size uses `SHAPEFILE_DBF_HEADER_INVALID/field=fileLayout/reason=truncated`
at the first missing byte; other JDK failures use the existing bounded `SHAPEFILE_IO_FAILED` mapping.
Raw bytes, field values, type bytes, CPG tokens, paths, charset class names, and localized messages
never enter a diagnostic. The first terminal condition wins; warning substitution never makes a later
record structural failure recoverable.

#### Evidence and viewer behavior

Hand-built paired SHP/DBF/CPG fixtures cover all three accepted header versions and ignored/status
bytes; zero, supported, mixed, and unsupported schemas; every scalar's blank/boundary/invalid form;
deletion/null/filter alignment; `ALL`, `NONE`, and reordered `ONLY`; physical warning order; both count
mismatch discovery paths; every alias, near miss, override/conflict/fallback branch; all 256 manual
table entries; parser/query/text limit minus/equal/plus one; short reads, size mutation, cancellation,
reuse, and reverse primary/suppressed cleanup. Tests prove malformed unselected or filtered values do
not warn and that full SHP validation still occurs for deleted rows.

The unchanged shapefile viewer opens one paired fixture containing non-ASCII text, typed attributes,
an unsupported field, and a deleted row. Source assertions prove schema/value order, stable IDs,
deletion suppression, query projection, and file release; a lightweight viewer assertion proves the
surviving geometry still fits/renders without adding attribute UI or cartographic behavior.
G5-006 validation runs the focused format/viewer checks, the normal gate, and whitespace. Corpus,
fuzz, Native Image, rendering-regression, and performance lanes remain owned by later tasks.

### PRJ retention and recognized CRS (G5-007)

#### Delivery boundary and metadata outcomes

G5-007 replaces only the staged `profile=prj` branch after G5-002's component snapshot. It depends
on the existing G4 CRS values and G5-002 opener/options, not on SHX or DBF: an integrated branch runs
PRJ after whatever earlier optional sidecar phases it contains and preserves their warning order; a
G5-002-only branch can implement the same PRJ phase directly. One integrator still owns the shared
facade/open transaction. No SHP geometry, ID, query, attribute, index, or source-order behavior
changes.

There is no new public API. The existing `ShapefileOpenOptions.crsOverride()` remains the only caller
declaration, and source metadata uses the existing immutable `CrsMetadata`. The package-private
`PrjReader`, `PrjTokenizer`, and `PrjRecognizer` are same-package peers in
`io.github.mundanej.map.io.shapefile`; they are not a public WKT model, parser SPI, registry extension,
or `internal.prj` subpackage. Raw WKT never becomes a `CrsRegistry` alias.

Successful opening selects exactly one metadata result:

| PRJ state | No override | Override present |
| --- | --- | --- |
| Missing | absent CRS, no warning | recognized override, no retained definition or warning |
| ASCII-blank | absent CRS plus `SHAPEFILE_PRJ_BLANK` | recognized override plus the same warning; blank text is not retained |
| Recognized | canonical recognized metadata with exact retained text | same result when definitions match; different definitions terminate `SHAPEFILE_CRS_CONFLICT` |
| Syntactically valid unknown | unknown metadata with exact retained text plus `SHAPEFILE_PRJ_CRS_UNRECOGNIZED` | recognized override retaining the exact text plus `SHAPEFILE_PRJ_OVERRIDE_USED` |
| Invalid | terminal `SHAPEFILE_PRJ_INVALID` | same terminal result; override never conceals hostile input |

Recognized metadata has no invented declared identifier: it contains the canonical G4 definition,
empty declared identifier, and retained PRJ text. Unknown metadata likewise has empty declared
identifier and retained text. Override-only metadata has the override definition and no retained
definition. The two recognized definitions come from exact
`CrsRegistry.level1().resolve("EPSG:4326"|"EPSG:3857")` calls; the format neither reconstructs nor
modifies their axes, units, domains, or canonical identifiers.

#### Bounded read, decode, and syntax representation

PRJ resolution is the final sidecar phase. The opener checks cancellation, opens the snapshotted PRJ
positionally, captures its size, and enforces `prjBytes` before allocation. It reads exactly the
captured bytes into one charged array. A non-throwing EOF before that boundary is terminal
`SHAPEFILE_PRJ_INVALID/reason=truncated` at the first missing byte. It then rechecks size; a changed
value is terminal `SHAPEFILE_PRJ_INVALID/reason=sizeChanged` at byte zero before temporary close or
decode. Only an unchanged complete read proceeds to close the channel and decode before
metadata/source publication. A leading `EF BB BF` is consumed only as an optional UTF-8 BOM; the retained definition
is the exact strict UTF-8 decode after it, including all remaining case and whitespace. An incomplete
BOM is malformed UTF-8/syntax, and a later U+FEFF has no special status.

Decoding uses a `StandardCharsets.UTF_8` decoder with malformed and unmappable actions `REPORT` into
one prospectively charged `char[]` capped at 16,385 units. More than G4's 16,384 retained UTF-16 units
terminates before String construction; equality is accepted. The final retained String is separately
charged at two bytes per UTF-16 unit. A byte array larger than `maximumPrjBytes` uses the shared
`SOURCE_LIMIT_EXCEEDED/prjBytes`; retained text over the G4 character cap uses the inherited
`CRS_RETAINED_DEFINITION_TOO_LONG`. Neither case is downgraded to unrecognized metadata.

After strict decode, syntax is scanned over the original UTF-8 bytes after the BOM so every token can
retain an exact component-byte span without a second text-to-byte map. The tokenizer owns only:

```text
byte[512] tokenKinds
int[1024] tokenStartEndByteOffsets
byte[16] grammarStack
```

A separate `tokenCount` scalar is the used length; it is not a reason to resize or allocate per token.
The fixed arrays and stack are charged once before scanning.

All punctuation is tokenized, so the 512-token ceiling counts identifiers, strings, numbers,
brackets, and commas. Depth is checked before pushing the seventeenth node. Each token starts only
after capacity, `parserAllocationBytes`, and cancellation checks; no partially accepted token array
is published. The arrays are opening temporaries discarded after recognition.

The accepted syntax is one root node consuming the whole non-whitespace input:

```text
node       := identifier '[' argument (',' argument)* ']'
argument   := node | bare-identifier | quoted-string | decimal
identifier := ASCII letter or '_' followed by ASCII letters, digits, or '_'
bare-identifier := identifier not followed by '['
decimal    := sign? (digits+ ('.' digits*)? | '.' digits+) ([Ee] sign? digits+)?
```

ASCII `0x09` through `0x0d` and space `0x20` are the complete insignificant-whitespace/blank set.
Quoted strings permit valid UTF-8 scalar content and doubled `""` as an embedded quote; U+0000
through U+001F, U+007F, and an unterminated quote are invalid. Parentheses, braces, semicolons, equals
signs, comments, multiple roots, trailing tokens, empty argument lists, and every other token form are
outside this profile. Bare identifiers make ordinary bounded WKT1 such as `AXIS["X",EAST]`
syntactically valid but do not make it recognized. `.5` and `5.` are valid decimals; an exponent
marker/sign without at least one following digit is not. Decimal syntax is validated by a bounded
byte scan. Exact numeric comparison
normalizes sign, leading/trailing zeroes, point position, and exponent with saturating checked
counters, so lexical differences compare equal without constructing a numeric object or expanding a
large exponent.

Lexing and grammar state advance together from low to high byte offset and stop at the first failure.
An invalid token byte wins at that byte before grammar interpretation. Once a token's lexical form is
known, the 513th-token check wins at its start before storage/grammar, and a seventeenth-level opening
bracket wins before the grammar push. Otherwise an unexpected valid token reports its start: this
covers missing commas, unexpected punctuation, trailing tokens, and multiple roots. An empty argument
list or missing argument reports the closing bracket; at end of input, an unterminated quote, missing
argument, missing root bracket, or otherwise incomplete production reports the captured EOF offset.
For an invalid exponent, a non-digit where the first exponent digit is required reports that byte,
while a missing digit at EOF reports EOF. These rules fix one precedence without a recovery parse.

This grammar proves bounded structural validity only. It does not assign general WKT semantics or
retain an AST. A valid tree that fails the two matchers is unknown, not malformed.

#### Exact recognition and override arbitration

`PrjRecognizer` compares token kinds, nesting, child order, case-insensitive ASCII keywords, exact
case-sensitive quoted names, and normalized decimal values directly against two fixed token trees:

- `EPSG:4326`: `GEOGCS["GCS_WGS_1984", DATUM["D_WGS_1984",
  SPHEROID["WGS_1984",6378137,298.257223563]], PRIMEM["Greenwich",0],
  UNIT["Degree",0.0174532925199433]]`.
- `EPSG:3857`: `PROJCS["WGS_1984_Web_Mercator_Auxiliary_Sphere", <the exact GEOGCS above>,
  PROJECTION["Mercator_Auxiliary_Sphere"], PARAMETER["False_Easting",0],
  PARAMETER["False_Northing",0], PARAMETER["Central_Meridian",0],
  PARAMETER["Standard_Parallel_1",0], PARAMETER["Auxiliary_Sphere_Type",0],
  UNIT["Meter",1]]`.

Insignificant ASCII whitespace, keyword case, and equivalent numeric spelling may differ. Quoted
names, child order, and tree shape may not. An `AUTHORITY`, `AXIS`, extension, alternate name,
additional/reordered child, or otherwise valid near miss is retained unknown. No identifier,
coordinate range, filename, WKT name, or caller registry is consulted heuristically.

Recognition precedes override arbitration. A canonical recognized definition equal to the override
is clean; inequality is terminal even if the view could transform between them. Unknown plus override
retains the text as provenance but takes semantics only from the explicit override. Unknown without
override remains non-renderable metadata and later follows G4's normal
`CRS_DEFINITION_UNKNOWN`; missing/blank without override follows `CRS_METADATA_MISSING`.

#### Diagnostics, cleanup, and evidence

Earlier SHX/DBF/CPG warnings retain their established order. PRJ contributes at most its warnings in
this order after complete syntax/recognition: blank, or unrecognized followed by override-used instead
of unrecognized when an override wins. There is never both `SHAPEFILE_PRJ_CRS_UNRECOGNIZED` and
`SHAPEFILE_PRJ_OVERRIDE_USED` for one input.

| Condition | Location and code | Exact context |
| --- | --- | --- |
| ASCII-blank retained input | `prj`, byte after BOM; warning `SHAPEFILE_PRJ_BLANK` | empty |
| Exact positional read reaches EOF before captured size | `prj`, first missing byte; `SHAPEFILE_PRJ_INVALID` | `reason=truncated` |
| Size after a complete read differs from captured size | `prj`, byte zero; `SHAPEFILE_PRJ_INVALID` | `actualBytes=<new size>`, `capturedBytes=<open size>`, `reason=sizeChanged` |
| Invalid UTF-8 | `prj`, first malformed input byte; `SHAPEFILE_PRJ_INVALID` | `reason=encoding` |
| Invalid token/grammar | `prj`, first responsible token byte or EOF; same code | `reason=syntax` |
| Seventeenth nesting level | `prj`, opening bracket; same code | `reason=nesting` |
| 513th token | `prj`, token start; same code | `reason=tokens` |
| Decoded definition reaches the 16,385th UTF-16 unit | component `prj`, no byte offset; `CRS_RETAINED_DEFINITION_TOO_LONG` | `maximum=16384`, `requested=16385` |
| Valid unknown tree | `prj`, byte after BOM; warning `SHAPEFILE_PRJ_CRS_UNRECOGNIZED` | empty |
| Override accepts unknown tree | same location; warning `SHAPEFILE_PRJ_OVERRIDE_USED` | `selected=<canonical identifier>` |
| Recognized tree conflicts with override | `prj`, root identifier; `SHAPEFILE_CRS_CONFLICT` | `declared=<PRJ canonical identifier>`, `override=<override canonical identifier>` |

Limits/cancellation retain the shared shapes. Thrown PRJ open/size/read failures are
`SHAPEFILE_IO_FAILED` with `operation=...`; a temporary close failure after successful read uses
`operation=close`, no byte offset, and the bounded `causeKind`. With an earlier primary failure the
close throwable is suppressed. A PRJ failure then closes transaction-owned DBF, if present, and SHP
in reverse order. On success, the PRJ handle/byte/token/decoder temporaries are gone before the final
cancellation check atomically transfers source handles and immutable metadata. No source state is
partially updated, and metadata remains readable after source close.

Cancellation is checked before/after open, size, read, decode, temporary close, token/matcher stages,
within 4,096 controlled bytes/characters/tokens, and before warning/metadata/source publication.
Cleanup never polls cancellation. There is no memory mapping, retained token tree, cache, WKT
registry, external library, reflection, resource scan, or AWT dependency.

Fixtures pin exact retention with/without BOM; both canonical trees; keyword/whitespace/numeric
equivalence; every quoted-name/constant/order/extra near miss; Unicode retained-unknown text; all
override rows; missing/blank/invalid/unknown distinctions; encoded, retained-character, token, depth,
and allocation boundaries; byte-accurate malformed UTF-8/syntax locations; short reads, size mutation,
cancellation, temporary-close failure, reverse cleanup, and metadata survival after close.

The viewer becomes `shapefile-viewer <path.shp> [EPSG:4326|EPSG:3857]`: one argument exercises PRJ
metadata; the optional second remains the explicit override. Argument count and an optional exact
registry key are validated before Swing scheduling; absence never selects a default CRS. The package-
visible helper becomes `createMapView(Path, Optional<CrsDefinition>)`. It opens the source with the
corresponding options and always constructs the existing fixed view with a fresh Level 1 registry,
EPSG:4326 map coordinates, and EPSG:3857 display coordinates. A recognized 4326 source therefore uses
the registered forward operation and a recognized 3857 source the identity operation.

The helper owns the opened source until `ownedFeature` transfers it to an unattached binding; the
binding owns it until successful installation; the view owns it afterward. Cleanup is stage-exact:
failure before view construction closes the source; failure with a constructed view but before binding
transfer closes the source then the view; failure while an unattached binding owns the source closes
the binding then the view; and failure after installation, including fit, closes only the owning view.
The startup failure remains primary and cleanup failures are suppressed in that listed order. A
successfully returned view owns the chain and window teardown closes it on the EDT. Thus one-argument
missing/unknown PRJ produces G4's exact
`CRS_METADATA_MISSING`/`CRS_DEFINITION_UNKNOWN` attachment failure without leaking the already opened
source.

Temporary 4326 and 3857 fixtures prove fit/render through registered direct operations, an equal
override, conflict rejection, retained unknown inspection, and normal G4 missing/unknown attachment
diagnostics. G5-007 validation runs the focused format/core/viewer checks, normal gate, and whitespace;
no corpus, native, fuzz, rendering-regression, or performance lane is introduced.

### Bounds, diagnostics, and deterministic fuzzing (G5-008)

#### Hardening boundary

G5-008 closes cross-component safety gaps after every Level 1 shape and sidecar slice exists. It adds
no format, public API, limit, recovery behavior, production fuzzer, parser layer, or Gradle command.
The approved G5-001 profile and G5-002 through G5-007 executable stage orders remain normative; this
task repairs an implementation when evidence disagrees rather than redefining malformed input to make
a test pass. Valid fixtures keep exactly equal observable metadata, records, warnings, and order.

Production changes are limited to checked arithmetic, prospective accounting/checkpoints, stable
diagnostic mapping, cleanup, and architecture fixes in the existing same-package shapefile peers.
Test-only `ShapefileAdversarialFixtures` extends the established byte builders, while test-only
`ShapefileMutationHarness` invokes only public `Shapefiles.open`, `FeatureSource`, and `FeatureCursor`
behavior. Neither is shared with production or G5-009's future corpus lane. There is no generic
binary-reader base, alternate parser, property-test/fuzz dependency, reflection, or generated source.

#### Uniform bounds and failure precedence

One audit enumerates every use of the twelve `ShapefileLimits` fields, each hard format/Java capacity,
and every G4 query ceiling. Every untrusted signed/unsigned count, word conversion, component/record
end, offset, part/ring/field span, array length, token capacity, and logical allocation term uses
prospective checked arithmetic before seek, slice, loop, allocation, or ownership transfer. Overflow
uses the already approved outcome for that stage and reports requested `Long.MAX_VALUE` when the
shared limit shape applies; no wraparound value participates in a later diagnostic.

The audit does not introduce one global error-ranking function. These rules compose the earlier task
orders and are asserted at their boundaries:

1. Public argument/lifecycle checks remain ordinary Java failures. Initial cancellation wins before
   any probe, I/O, allocation, or format diagnostic.
2. Component snapshot order and ambiguity precede channel work. SHP remains required; recoverable
   SHX handling cannot conceal an SHP I/O/format failure.
3. After opening a component, captured byte-size/hard layout checks precede reading a field. A count
   is trusted only after its containing prefix is complete and valid; its configured ceiling and Java
   capacity then precede derived-length reads and allocations.
4. Within a component, the exact physical/stage order from its owning task chooses among multiple
   malformed fields. Cross-component outcomes retain SHP, SHX, DBF, CPG, PRJ encounter order.
5. Cancellation observed at an approved checkpoint retains prior bounded warnings and appends one
   terminal `SOURCE_CANCELLED`; cleanup never changes it. A custom token exception remains unclassified.
6. Cleanup continues in reverse ownership order. A known primary format/cancellation failure keeps
   cleanup throwables suppressed; cleanup failure without another primary uses only
   `SOURCE_CLOSE_FAILED` or the approved temporary-sidecar close mapping.

Limit fixtures use a minimally valid carrier for `maximum - 1`, `maximum`, and `maximum + 1`; equality
must succeed when no independent hard constraint is lower. Where a hard format/Java maximum makes a
configured boundary unreachable, tests pin that earlier hard failure and separately exercise the
configurable ceiling below it. Coverage includes component/record bytes, physical records, parts,
points, topology comparisons, DBF fields/width, CPG/PRJ bytes, decoded characters, and parser
allocation. Allocation cases exercise byte, int, long/double, char/String, reference-slot, map-entry,
scalar, and decimal charges, including defensive/intermediate copies and filtered/discarded values;
charges remain cumulative and are never inferred from heap use.

The separate G4 query matrix uses `FeatureSourceLimits`/`FeatureQueryLimits`, not
`ShapefileLimits`, and covers records examined, records returned, coordinates returned, attribute
values returned, decoded text characters returned, conservatively owned payload bytes, and retained
warnings at one below/equal/one above. The warning boundary asserts the exact retained prefix and
`omittedWarningCount`. Fixtures distinguish format `decodedTextCharacters` spent while parsing from
G4 returned decoded-text/payload accounting, even when both reject the same selected DBF value.

#### Exact hostile fixture matrix

Targeted fixtures, not mutation-family assertions, are the oracle for exact code, severity, encounter
order, location, context, warning omission, and terminal precedence:

| Area | Required hostile cases |
| --- | --- |
| Component transaction | required-missing SHP; lower/upper sidecar ambiguity and identity failure through the existing injectable filesystem seam on every host, plus real distinct-file evidence where supported; open/size/read/temporary-close faults at each acquisition stage; reverse partial cleanup |
| SHP framing | every fixed-header boundary; negative/swapped/truncated word lengths; record-header truncation; ordinal mismatch; exact/short/overlong record payload; appended data; targeted same-size mutation of a later validated field without claiming general snapshot detection |
| SHP geometry | every supported/unsupported/mismatched code; non-finite/signed-zero/bounds cases; hostile point/part/ring counts; truncated coordinates; invalid multipart tables; degenerate line/ring; polygon contact/orphan/equal-innermost and topology ceiling |
| SHX | captured size/header/endian/entry overflow; decreasing, duplicate, gapped, overlapping, out-of-file addresses; SHP length/count mismatch; SHX versus SHP I/O classification; whole-index fallback only |
| DBF | all header/status versions; unsigned count/length overflow; descriptor/name/width/decimal/row-sum errors; terminator/suffix/truncated row; marker and count mismatch; every invalid selected scalar and unsupported-field warning |
| CPG | byte limit; empty/unknown/multiple tokens; BOM truncation; non-ASCII; all alias/LDID/override conflict branches and undefined single-byte values |
| PRJ | byte/character/token/depth ceilings; BOM; representative valid 2/3/4-byte scalars cut at every continuation boundary; isolated/bad continuations, illegal leads, overlong forms, surrogate encodings, and values above U+10FFFF; invalid token/grammar/EOF; valid unknown/near-match; override equality/conflict; size mutation and temporary close |
| Cross-sidecar | warning-cap omission and SHX-before-DBF/CPG-before-PRJ order; DBF/SHX count disagreement; null/deleted alignment; earlier recovered warning retained before later terminal error |

For truncation matrices, the builder cuts at every byte of fixed headers/descriptors and immediately
before/inside/after each variable table or payload class, rather than exhaustively cutting a large
coordinate body. Endian cases swap one field at a time so the expected first failure remains
unambiguous. Targeted diagnostics compare exact public fields and never compare message or chained
cause text.

#### Bounded deterministic mutation harness

The breadth harness uses five committed 64-bit seeds, one per component family:

```text
SHP 0x5348502D47353038    SHX 0x5348582D47353038
DBF 0x4442462D47353038    CPG 0x4350472D47353038
PRJ 0x50524A2D47353038
```

One local `java.util.Random(seed)` instance, whose algorithm is specified by the JDK contract, is
constructed explicitly per seed. No default/global random source, time, process state, filesystem
order, locale, or parallel execution affects case creation. Catalogs are fixed `List.of`/primitive
arrays and enum declaration order; selection never iterates a map, set, directory, or provider list.

Case indexes are local to the printed family token. Every family uses indexes `0..31` for 32 mutation
cases. SHP uses `32..51` for 20 generated combinations; SHX, DBF, CPG, and PRJ each use `32..50` for
19. This is exactly 256 logical cases. Each case is executed twice in fresh directories from the same
replay descriptor, for 512 parser runs.
Each dataset has at most five components, 65,536 aggregate encoded bytes, 64 records/parts/fields,
4,096 points/decoded characters, 20,000 topology comparisons, and 1 MiB parser allocation under
explicit tighter options. One non-parameterized harness test method owns all 512 runs and has one
JUnit 5 aggregate deadline:
`@Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)`.
There is no per-case or per-seed timeout multiplier. Timeout fails the test process path; it never becomes a source
diagnostic or performance claim. The fixed case/work ceilings are the primary runtime bound.

Mutation operators are a finite enum: single/multiple bit flip; overwrite a selected byte, endian
word, integer, or double with zero/ones/min/max/non-finite constants; reverse one endian field;
truncate at a selected structural/data position; append bounded bytes; replace a trusted length/count/
offset; delete an optional sidecar; select its approved lower- or upper-case spelling; and cross-wire
one SHX/DBF from a second valid baseline. Generated combinations choose from finite valid shape/component templates
and those same field/operator catalogs. They do not synthesize bytes by reimplementing parser grammar.

A replay descriptor contains only family token, its committed seed, local zero-based case index,
baseline ID, component token, operator,
up to two offsets, and fixed scalar operands. It is printed in a bounded assertion message and a
test-only `replay(String family, int caseIndex)` entry reconstructs exactly one case and rejects an
unknown family or out-of-range index. Family seed plus local case index is the authoritative recipe:
replay advances a fresh specified `Random` through the same declaration-ordered preceding choices,
then verifies the descriptor's summary fields before execution. There is no automatic shrinker or
failed-byte artifact. A discovered defect is manually reduced, reviewed, and committed as a named
targeted fixture before the code fix; the breadth case remains as additional evidence.

#### Public outcome oracle and lifecycle evidence

Each breadth case runs the full public opening/query path with fixed source identity, its generated
open-options choice, source bounds absent, `AttributeSelection.ALL`, no tighter query limits, and
`CancellationToken.none()` for both open and cursor. A normalized outcome is phase-structured:

- opening success stores metadata plus the opening report's ordered entries and omitted count;
- opening failure stores only the opening `SourceException` report entries and its omitted count;
- cursor success/failure stores yielded IDs/geometries/ordered attributes plus the final cursor or
  cursor-exception report entries and its separate omitted count; and
- targeted cleanup cases alone may store a close-exception report and its separate omitted count.

Mutually exclusive phases remain distinct; their diagnostic entries are never concatenated into one
report or one omitted count. Normalization excludes message, stack, exception class/cause text,
temporary path, and timing. The two executions must be equal.

The only accepted outcomes are:

- complete success whose immutable values satisfy schema, finite geometry, stable unique IDs/source
  order, configured limits, and clean exhaustion;
- success with only an approved recoverable warning/fallback and otherwise the same invariants; or
- `SourceException` with one approved terminal code last after bounded warnings and deterministic
  cleanup.

The test-only allowlist uses full string equality and pins severity. The only `WARNING` codes are:

```text
SHAPEFILE_SHX_MISSING                 SHAPEFILE_SHX_IGNORED
SHAPEFILE_DBF_MISSING                 SHAPEFILE_DBF_FIELD_UNSUPPORTED
SHAPEFILE_DBF_VALUE_INVALID           SHAPEFILE_CPG_WITHOUT_DBF
SHAPEFILE_CPG_INVALID                 SHAPEFILE_ENCODING_CONFLICT
SHAPEFILE_ENCODING_FALLBACK           SHAPEFILE_PRJ_BLANK
SHAPEFILE_PRJ_CRS_UNRECOGNIZED        SHAPEFILE_PRJ_OVERRIDE_USED
```

The only terminal `ERROR` codes are:

```text
SHAPEFILE_COMPONENT_MISSING           SHAPEFILE_COMPONENT_AMBIGUOUS
SHAPEFILE_IO_FAILED                   SHAPEFILE_HEADER_INVALID
SHAPEFILE_FILE_LENGTH_MISMATCH        SHAPEFILE_SHAPE_TYPE_UNSUPPORTED
SHAPEFILE_RECORD_NUMBER_INVALID       SHAPEFILE_RECORD_LENGTH_INVALID
SHAPEFILE_RECORD_TYPE_MISMATCH        SHAPEFILE_COORDINATE_NON_FINITE
SHAPEFILE_BOUNDS_MISMATCH             SHAPEFILE_PART_TABLE_INVALID
SHAPEFILE_RING_INVALID                SHAPEFILE_RING_TOPOLOGY_AMBIGUOUS
SHAPEFILE_DBF_HEADER_INVALID          SHAPEFILE_DBF_FIELD_INVALID
SHAPEFILE_DBF_RECORD_MARKER_INVALID   SHAPEFILE_DBF_RECORD_COUNT_MISMATCH
SHAPEFILE_PRJ_INVALID                 SHAPEFILE_CRS_CONFLICT
SOURCE_LIMIT_EXCEEDED                 SOURCE_CANCELLED
SOURCE_CLOSE_FAILED                   CRS_RETAINED_DEFINITION_TOO_LONG
```

Every other `SOURCE_*`, `CRS_*`, or format code is rejected; there is no prefix match. The breadth
catalog has a named input/options case for every listed warning and may accept those warnings. Its
terminal subset is the table above **except** `SHAPEFILE_COMPONENT_MISSING`,
`SHAPEFILE_COMPONENT_AMBIGUOUS`, `SHAPEFILE_IO_FAILED`, `SOURCE_CANCELLED`, and
`SOURCE_CLOSE_FAILED`, plus `CRS_RETAINED_DEFINITION_TOO_LONG`; occurrence of any excluded code in
ordinary fresh-file breadth work is a test failure. Those six codes are accepted only by,
respectively, the named required-SHP, filesystem-seam ambiguity, injected-I/O,
scheduled-cancellation, injected-cleanup, and PRJ 16,385th-character boundary targeted fixtures, each
of which asserts its exact phase report rather than using the breadth oracle.

The completed gate specifically rejects staging-only `SHAPEFILE_PROFILE_NOT_IMPLEMENTED`. Any other
`RuntimeException`, hang, nondeterministic normalized outcome, or contract violation fails
with the replay descriptor. The harness does not catch or relabel `Error`; `OutOfMemoryError`,
`StackOverflowError`, and other fatal failures therefore fail normally. It does not accept a broad
diagnostic family as a substitute for targeted exact fixtures: a mutation may end only at a
condition-reachable breadth code, and its repeated result must be identical.

Every run uses try/finally ownership, closes an early/live cursor and returned source, then immediately
deletes its temporary files. Targeted tests separately prove failure/cancellation cursor-slot release,
fresh-cursor repetition/reuse, source close with an abandoned cursor, temporary-sidecar close, and
primary/suppressed ordering through the JDK seam. After each seed group, a fresh clean sentinel source
with default registry, encoding, limits, and options must return its exact baseline outcome, proving
that hostile input leaves no mutable global state.

Architecture checks continue deriving the format's JDK-only, AWT-free, native-targeted prohibition
set and additionally ensure mutation/fixture helpers occur only in test output. G5-008 validation is
only `:modules:mundane-map-io-shapefile:check`, `qualityGate`, and `git diff --check` as listed in the
task. It neither creates nor invokes `shapefileCorpus`, native, rendering-regression, or performance
lanes.

### Shapefile corpus and viewer completion (G5-009)

#### Corpus boundary and roles

G5-009 adds interoperability evidence after the complete hostile-input profile is stable. It creates
no production module/API, parser option, supported shape/encoding/CRS, or recovery rule. Unit fixtures
from G5-002 through G5-008 remain the exact boundary and malformed-input tests; corpus entries prove
that independently produced bytes reach those same public outcomes. Corpus tests use only the public
`Shapefiles`/`FeatureSource` surface and never receive package-private parser access.

The checked-in corpus lives only under
`modules/mundane-map-io-shapefile/src/shapefileCorpusTest/resources/shapefile-corpus/`. Its companion
Java tests use a dedicated `shapefileCorpusTest` source set. Neither resources nor tests enter the
main/test JAR, publication, Native Image resources, normal `test`, `check`, `checkAll`, or
`qualityGate`. There is no empty corpus module or root data hierarchy.

Every dataset has one declared role:

- `CURATED` is an unchanged or legally permitted excerpt obtained from an identified real GIS tool
  or data publisher. At least one successful dataset has this role and was not produced with any
  project fixture builder. Its origin, version/date when known, redistribution terms, and any allowed
  transformation/excerpt are explicit.
- `GENERATED` is purpose-built or deliberately corrupted coverage whose external creation tool and
  version, checked recipe, or parent-plus-exact-derivation statement is recorded. Generated artifacts
  are committed bytes; the test run does not invoke GIS software, rerun a generator, or mutate a
  positive corpus entry.

Hand-built unit fixtures and deterministic G5-008 cases are never promoted implicitly. Adding or
replacing corpus bytes requires the same manifest, expectation, checksum, license, and HITL review as
the initial set. The complete corpus is at most 16 datasets, 512 KiB per dataset, and 4 MiB across all
component files, keeping checkout and manual inspection proportionate.

The initial inventory uses these stable dataset IDs; a case may carry additional tags but none may be
silently dropped or renamed:

```text
curated-point-utf8-4326
generated-multipoint-null-ibm437
generated-multipart-line-ibm850
generated-polygon-hole-windows1252-3857
generated-point-iso88591-unknown-prj
generated-point-fallback-deleted
generated-pointz-rejected
generated-corrupt-shx
generated-corrupt-dbf
```

The first case is the independently GIS-produced successful `CURATED` artifact. The next five cover
the remaining successful geometry/encoding/CRS/override profiles. The final three are generated
negative/derivative cases whose `parentId` and exact corruption recipe are recorded. Exact expectation
tags, not the names alone, prove every Level 1 requirement.

#### Manifest, provenance, and completeness

UTF-8 `manifest.tsv` is the sole inventory. It uses LF, one header, literal tab separators, no quoting,
and rows sorted by `(datasetId, componentPath)`. Every field is non-empty except `parentId`; values may
not contain controls or tabs. `componentPath`/`licensePath` alone are normalized relative `/` paths:
they reject a leading slash, backslash, empty/`.`/`..` segment, drive prefix, or URI scheme. Columns are exact:

```text
datasetId  role  componentPath  byteLength  sha256  origin  toolAndVersion  licenseId  licensePath  parentId  coverageTags  expectationId
```

`componentPath` is relative beneath `data/<datasetId>/`; SHP and sidecars share exact base name
`<datasetId>` and use one approved component extension/case. `sha256` is 64 lowercase hexadecimal digits over
the committed bytes and `byteLength` is decimal. `origin` is a stable public source URL/citation for
curated data or a bounded generation/derivation reference for generated data; tests record it but
never dereference it. `licenseId` is an SPDX identifier where available or `LicenseRef-<token>`;
`licensePath` points beneath `licenses/` to committed plain text. `coverageTags` is a comma-separated,
sorted, unique list from the closed vocabulary with no whitespace. Rows of one dataset must repeat
identical provenance/role/license/parent/tags/expectation fields.

Each dataset has exactly one `.shp` row and at most one `.shx`, `.dbf`, `.cpg`, and `.prj` row. A
nonblank `parentId` resolves to exactly one different dataset; unknown parents, self-reference, and
cycles fail. Such a row's `origin` is an exact nonblank `derive:<recipe-token>` value, and the derived
dataset must repeat the parent's `licenseId` and `licensePath`. A transformation needing different or
additional terms is not admitted until its licensing is represented by a separately reviewed corpus
case; the initial derivative negatives add no multi-license model.

The corpus `Test` task declares the one source resource directory as an input and passes that exact
project-relative root to its fork. The JDK-only manifest test walks only this root, never a consumer,
home, arbitrary classpath, or environment-selected directory. It rejects duplicate IDs/paths, unsorted rows,
unknown roles/tags, missing components/licenses, byte/checksum disagreement, unreferenced data/license
files, unsafe paths, component-case ambiguity, inconsistent dataset fields, unknown expectation IDs,
and generated parent cycles. Manifest and expectation inventory are the only intentional files not
self-listed. SHA-256 uses `MessageDigest.getInstance("SHA-256")`, an exact JDK algorithm name, with no
download, signature service, environment key, or trust-on-first-use behavior.

Coverage tags form a closed test-owned vocabulary. Completeness requires the positive profile tags
for null, point, multipoint, polyline, polygon, multipart, hole, valid SHX, every supported DBF scalar,
UTF-8/ISO-8859-1/Windows-1252/IBM437/IBM850, CPG/LDID/fallback/explicit-override selection, and
recognized EPSG:4326/EPSG:3857. Negative entries require permanent Z/M rejection, ignored corrupt SHX,
terminal corrupt DBF, and retained-unknown PRJ. One small dataset may cover several tags, but every tag
must map to at least one exact assertion; tag presence alone never passes compatibility.

#### Exact public outcome oracle

`CorpusExpectations` is a finite test-only map keyed one-to-one by `expectationId`; it is reviewed
source, not deserialized executable behavior. For each dataset it fixes:

- open options, including an explicit encoding/CRS override only when that case is testing it;
- success or the exact terminal phase/code;
- source identity-independent metadata: extent, absent feature count, ordered nullable schema, and
  missing/recognized/unknown CRS state with exact retained PRJ text where applicable;
- ordered physical record IDs, exact geometry class and packed coordinates/part/ring/polygon fences,
  envelopes, and ordered typed attributes; and
- ordered diagnostics by code, severity, location, context, and omitted count, excluding message and
  cause text.

Before parsing, each dataset is copied directly from the exact source-root files whose length and
checksum were just verified into a fresh temporary directory; processed/classpath resource copies are
never parser input or an alternate checksum oracle.
The test opens it with a fixed `SourceIdentity`, queries absent bounds with `AttributeSelection.ALL`,
iterates to exhaustion, compares the complete outcome, closes cursor/source, and immediately deletes
the copy. A source-open failure likewise leaves the directory deletable. Success values are compared
directly; no geometry hash, parser-specific dump, snapshot update mode, broad diagnostic family, or
image golden can approve a changed outcome.

Every successful dataset containing valid SHX is also copied without SHX and reopened. Metadata,
records, geometry, attributes, cursor diagnostics, and exhaustion must match exactly; only the opening
report changes by the approved missing-SHX warning. The corrupt-SHX dataset must equal its clean
sequential parent after the exact ignored-index warning. This is correctness evidence, not a timing or
random-access claim.

The corpus tests contain no mutation loop, randomized generation, cancellation sweep, injected I/O,
benchmark, native execution, or broad hostile matrix. Those remain G5-008, G7, G5-010, and the focused
unit suites. One corpus entry may intentionally fail, but an unexpected exception, checksum change,
missing expectation, resource leak, nondeterministic order, or message-dependent assertion fails the
lane.

#### Separate Gradle and CI lane

The shapefile module defines `shapefileCorpusTest` source/configuration sets extending only its normal
test JUnit dependencies plus main output, and one `Test` task of the same name. Corpus-owned tasks are
exactly `compileShapefileCorpusTestJava`, `processShapefileCorpusTestResources`,
`shapefileCorpusTestClasses`, `shapefileCorpusTest`, `checkstyleShapefileCorpusTest`, and
`spotbugsShapefileCorpusTest`; the root corpus lane runs all applicable compile/resource/test/static-
analysis tasks. The root defines:

```text
shapefileCorpus (group=verification)
  dependsOn :modules:mundane-map-io-shapefile:shapefileCorpusTest
            :modules:mundane-map-io-shapefile:checkstyleShapefileCorpusTest
            :modules:mundane-map-io-shapefile:spotbugsShapefileCorpusTest
```

The custom test task has stable locale/time-zone/encoding inputs, disables parallel forks, and uses no
network API or external process. It passes only its Gradle-declared project-relative source root; no
consumer/environment path is accepted. The root corpus task mechanically walks the task-dependency
closures of `checkAll`, `qualityGate`, root `check`, and each checked project's exact normal-gate roots
`check`, `spotlessCheck`, and `javadoc`, and fails if they contain any of those six corpus-owned tasks.
The module build explicitly detaches custom-source-
set Checkstyle/SpotBugs lifecycle wiring from normal `check`; the global `spotlessJavaCheck` may still
format-check `src/**/*.java`, but it neither compiles/runs corpus code nor reads corpus data. Conversely, the
root creator command executes the manifest and every expectation exactly once.

One non-verification helper, `primeShapefileCorpusDependencies`, resolves exactly the format module's
`shapefileCorpusTestCompileClasspath`, `shapefileCorpusTestRuntimeClasspath`,
`shapefileCorpusTestAnnotationProcessor`, `errorprone`, `checkstyle`, `spotbugs`, `spotbugsPlugins`,
`spotbugsSlf4j`, and `jacocoAgent` configurations without compiling or running corpus code. CI adds a
separately named `ubuntu-24.04`/Temurin Java 21 corpus job with a newly empty, job-local
`GRADLE_USER_HOME`. It runs that prime task online, then uses the same home for
`./gradlew --offline shapefileCorpus --rerun-tasks --console=plain`; the
normal JVM job remains unchanged. A corpus-lane bytecode check rejects `java.net`, `java.net.http`,
socket/datagram channel types, `ProcessBuilder`, and every `Runtime.exec` overload in its own test
output. No fixture, GIS tool, license, or expectation is fetched and no child process can be launched. Corpus failure blocks
G5/Level 1 release readiness but is visibly separate from the normal gate. Local completion follows
the task's exact command order: focused module/viewer checks, the newly created root corpus command,
`qualityGate`, then whitespace. No native, render-regression, fuzz, or performance command is folded
into this task.

#### Viewer completion and HITL evidence

The G5-007 command remains
`shapefile-viewer <path.shp> [EPSG:4326|EPSG:3857]`. G5-009 adds no format branching to AWT. The example
run task uses the root project directory as its fixed working directory so the repository-relative
HITL commands in the task are portable across subproject invocation. The application still receives
and validates the path as an ordinary caller argument.

Argument/registry validation, `Shapefiles.open`, and the bounded preview run synchronously on the
launching thread before Swing scheduling. A returned source is loader-owned. The loader opens one
`ALL` cursor, reads at most 21 published records, retains 20, uses the 21st only to set a
`preview truncated` flag, and closes the cursor before any attachment. Open/advance failure closes the
cursor if acquired and then the source; the operation failure remains primary. An otherwise successful
early cursor close failure becomes primary and source-close failure is suppressed. Only after clean
cursor close does the loader hand the source plus immutable preview to one EDT startup callable.

The loader wraps that callable in one `FutureTask`, queues it once with `EventQueue.invokeLater`, and
waits with `get()` until the task reaches a terminal owner state. `InterruptedException` only records
that interruption occurred and the wait continues; after success or failure the loader restores its
interrupt status. `ExecutionException` is unwrapped after EDT cleanup and the original
`RuntimeException` or `Error` is rethrown unchanged. If queue submission itself fails, the loader still
owns and closes the source. No future is cancelled and no second EDT runnable is submitted.

On the EDT, startup constructs the fixed view/panel, seeds the opening-report section, registers one
`MapSourceReportListener`, creates the owned binding, installs it, fits, and applies preview/list
selection state in that order. Registration before installation means no live source report is
missed, while opening diagnostics are never duplicated into the live-report section. All listener,
panel/list selection, binding/view mutation, window installation, and permanent close calls remain on
the EDT.

One explicit startup owner state prevents double cleanup. Failure before binding transfer closes the
loader-owned source then any constructed view; failure while an unattached binding owns the source
closes the binding then view; failure after installation, including fit or panel initialization,
closes only the owning view. The startup failure stays primary and cleanup failures are suppressed in
that order. The completed `FutureTask` proves ownership is either installed or fully cleaned before
the launching thread continues, so it never retries close.

The example
uses `FeatureSourceMetadata`, `FeatureRecord`, `DiagnosticReport`, and `MapSourceReportListener` to
show a generic bounded side panel containing source/CRS state, ordered schema, at most the first 20
record IDs, ordered attributes for the currently chosen preview record, and separate opening/latest
query diagnostic codes/locations/context.
The panel labels truncation instead of materializing the dataset. Values use bounded ordinary `toString` only for the approved canonical
types; binary/raw bytes, retained PRJ text, paths, and exception causes are not displayed.

Load failure returns no partial view and prints/presents the stable generic report once after the
stage-exact cleanup above. Live source reports replace prior live state in encounter order. Unknown PRJ
without an override reaches the expected generic CRS attachment failure; corrupt SHX stays viewable
with its warning; corrupt DBF and Z/M remain terminal. Temporary viewer tests prove all resources are
deletable after startup failure, view close, and window teardown.

Automated viewer tests use temporary small fixtures and invariant assertions: expected geometry role,
fit envelope, non-background tolerant regions, part separation, polygon-hole background, stable paint
presence, schema/value preview, non-ASCII decoded text, and diagnostic order. They do not copy the
corpus parser oracle into the example, require pixel identity, create a new render lane, or inspect
Java2D from the format module.

The named **G5 corpus and viewer approval** HITL checkpoint has two recorded outcomes before the task
may be Complete: the maintainer confirms every fixture's license/generation statement and specifically
each `CURATED` origin, excerpt/derivation permission, and redistribution terms,
and manually runs representative corpus paths for point/multipoint, multipart line, polygon hole,
all five decoded encodings, both recognized CRSs, corrupt-SHX warning, and unknown-PRJ behavior. Review
confirms geometry/fit, hole appearance, readable attribute preview, diagnostic presentation, and clean
window close on a supported desktop. The task notes record the reviewed commit, reviewer/date,
approve/reject outcome, Java/OS/window-system/scale, exact commands and dataset IDs, per-case result,
and unresolved blocker; the manifest remains factual provenance rather than mutable approval state.
