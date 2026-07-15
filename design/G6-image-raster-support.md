# G6 — PNG/JPEG raster support design

Project index: [DESIGN.md](../DESIGN.md).

## Bounded PNG/JPEG raster source (G6-001)

### Vertical-slice boundary

G6-001 creates `mundane-map-io-image` only when it can open, read, and render bounded PNG and JPEG
files through the G4 `RasterSource` contract. The module owns encoded-file probing, format limits,
the source lifecycle, and stable image diagnostics. It depends on API plus only the G4 accounting
and resampling algorithms it uses from core; it has no AWT or `java.desktop` dependency. AWT owns the
one JDK `ImageIO` decoder and the already approved RGBA-to-Java2D conversion. The raster-viewer
example composes the two through explicit registration.

This task adds no world-file name, affine transform, interpolation option, cache, GeoTIFF concept,
remote locator, image writer, background loader, or native resource. A file without caller-supplied
bounds/CRS is still a useful directly readable `RasterSource`, but G4 correctly refuses to attach it
to a map. The G6-001 viewer deliberately supplies normalized bounds for demonstration; it does not
call that georeferencing. G6-002 replaces that example-only placement when a world file is present.

### Public surface and ownership

The format module has exactly four public types in `io.github.mundanej.map.io.image`:

```text
RasterImages
  open(Path path, SourceIdentity identity, ImageOpenOptions options,
       EncodedRasterDecoderRegistry decoders) -> RasterSource
  open(Path path, SourceIdentity identity, ImageOpenOptions options,
       EncodedRasterDecoderRegistry decoders, CancellationToken cancellation) -> RasterSource

ImageOpenOptions(
    ImageSourceLimits imageLimits,
    RasterSourceLimits requestLimits,
    ImagePlacement placement)
  defaults()
  withImageLimits(...)
  withRequestLimits(...)
  withPlacement(...)

ImagePlacement
  unplaced()
  axisAligned(Envelope mapBounds, Optional<CrsMetadata> crs)
  axisAligned(Envelope mapBounds, CrsMetadata crs)

ImageSourceLimits(
    long maximumEncodedBytes,
    long maximumHeaderBytes,
    int maximumWidth,
    int maximumHeight,
    long maximumPixels,
    int maximumLogicalChannels)
  defaults()
  withMaximum...(value)
```

`RasterImages` is a non-instantiable static facade and returns only the format-neutral source. No
path, channel, header, decoder implementation, image metadata subtype, or AWT value leaks from the
format. Options, placement, and limits are deeply immutable. `unplaced` publishes absent bounds/CRS
and is directly readable but not attachable under G4. `axisAligned` requires positive-area bounds and
may retain absent/unknown CRS metadata; bounds without a recognized CRS can be real but not
renderable. It is a working G4 placement, not an affine abstraction or empty world-file placeholder.
G6-002 may add a world-file placement only when it implements the real affine path.

The default image limits are:

| Image-open ceiling | Default |
| --- | ---: |
| Encoded file bytes | 33,554,432 |
| Bytes examined before a JPEG frame header | 1,048,576 |
| Width or height | 16,384 |
| Source pixels | 16,777,216 |
| Encoded channels/components | 4 |

Every maximum is positive; dimensions/channels are positive `int` values and byte/pixel
ceilings are positive `long` values. There is no unlimited sentinel, property override, platform
default, or mutable global. G4's request limits own decoded/intermediate and published byte policy;
G6 does not duplicate those ceilings. The chosen defaults allow the complete-decode reservation below
to fit beneath G4 defaults. Equality is accepted, plus one fails, and overflow records the approved
`Long.MAX_VALUE` requested value.

The source owns one read-only `FileChannel` from successful open until close. Holding the same handle
avoids reopening a replacement path between metadata and a read. Reads require external serialization,
use the channel synchronously, and retain no decoded image or result. `close()` marks closed before
closing the channel, is idempotent, and maps its first channel-close failure to `SOURCE_CLOSE_FAILED`.
Metadata, limits, and opening diagnostics survive close; reads after close are lifecycle failures.
There is no finalizer, cleaner, shutdown hook, retry thread, or implicit cache.

### Toolkit-neutral decoder boundary

Four small API types are justified by the unavoidable AWT/I/O dependency split:

```text
EncodedRasterFormat = PNG | JPEG

EncodedRasterDecoder
  decode(InputStream borrowedInput, EncodedRasterDecodeContext context) -> RgbaPixelBuffer

EncodedRasterDecodeContext
  sourceIdentity() -> SourceIdentity
  format() -> EncodedRasterFormat
  encodedByteLength() -> long
  width(), height(), channelCount(), bitsPerSample()
  sourceWindow() -> RasterWindow
  outputWidth(), outputHeight()
  checkpoint()
  claimReservedIntermediateBytes(long bytes)

EncodedRasterDecoderRegistry
  builder()
    register(EncodedRasterFormat format, EncodedRasterDecoder decoder)
    build()
  formats() -> immutable declaration-ordered list
  find(format) -> Optional<EncodedRasterDecoder>
```

The context is an operation-scoped borrowed capability created by the image source over one G4
`RasterRequestAccounting`. The input is a bounded positional view of the owned file channel, begins
at byte zero, and is closed only by the source after decode. A decoder must not retain or close either
borrowed value, manufacture another source identity, or publish before its last checkpoint. It must
claim the portion of the source's already charged reservation covering each decoder-owned buffer
before allocation where the JDK exposes it. A claim is bookkeeping only and never charges G4 a
second time. Over-claim, non-positive claim, or a successful return with unclaimed capacity is a
decoder contract `IllegalStateException`. G4's documented before/after rule remains the honest
boundary for opaque `ImageIO` allocations.

The source is the sole accounting owner: it validates the window/output, charges the complete pixels
actually examined, charges one intermediate reservation, and charges published bytes once before
invoking the decoder. G6-001 has fixed G4 nearest behavior and exposes no interpolation type through
this context. G6-003 may add a source-compatible default context method returning `NEAREST` only when
it adds the request interpolation contract and real bilinear behavior.

Decoder implementations are immutable and thread-safe even though each `RasterSource` remains
externally serialized; one immutable registry may be shared by several sources. A successful buffer
is independently owned, has the exact requested output dimensions, and remains valid after the
operation/source closes.

The registry and its single-use builder are instance-owned and immutable after build. Registration
identity is the enum value, not a class token or filename suffix. Duplicate registration throws a
narrow nested registration exception with stable code `RASTER_DECODER_DUPLICATE` and exact context
`format`, `firstIndex`, and `duplicateIndex`; malformed/null configuration remains a parameter-named
programmer failure. A missing entry is not discovered elsewhere: `RasterImages.open` terminates with
`IMAGE_DECODER_NOT_REGISTERED`, component `decoder`, and context `format`. The same decoder instance
may be explicitly registered for both formats. No static mutable registry, service descriptor,
annotation, classpath scan, resource enumeration, fallback registry, or class-key dispatch exists.

AWT exposes one final factory:

```text
AwtRasterDecoders.level1() -> EncodedRasterDecoderRegistry
```

Each call constructs a fresh immutable registry and registers one explicitly constructed
`ImageIoRasterDecoder` for PNG and JPEG in source order. The decoder implementation can remain
package-private; consumers normally need the boundary, not the Java2D class. Tests can register a
small explicit fake decoder without importing AWT into the image module.

### Encoded profile and header plan

The final filename must have ASCII-case-insensitive `.png`, `.jpg`, or `.jpeg`; an unsuitable suffix
is a direct argument failure before I/O. Signature bytes remain authoritative and must agree with the
suffix, or open fails with `IMAGE_FORMAT_MISMATCH`. A package-private fixed-buffer probe reads
absolute positions from the owned channel and allocates no segment payload while scanning. It
captures only the facts needed to validate limits and decode: format, dimensions,
encoded component count, sample depth, progressive/interlaced flag, and encoded length. After the
probe determines the boundary, it owns one exact immutable header snapshot: PNG bytes `[0,33)`
(signature through IHDR CRC), or JPEG bytes `[0,endOfSofSegment)` including skipped segment payloads.
The JPEG snapshot length is at most `maximumHeaderBytes` and is checked before its sole byte-array
allocation. Later reads compare these bytes exactly; no hash, collision assumption, decoded metadata,
public header tree, or retained text is involved.

The PNG profile requires:

- exact eight-byte signature;
- first chunk length 13 and type `IHDR`, with a valid IHDR CRC;
- positive big-endian dimensions;
- the bounded eight-bit-or-less pairs: grayscale depths 1/2/4/8, truecolor depth 8, indexed depths
  1/2/4/8, grayscale-alpha depth 8, and truecolor-alpha depth 8;
- compression and filter methods zero and interlace method zero or one; and
- logical channel counts 1, 3, 4, 2, and 4 respectively (indexed reserves possible palette alpha).

Sixteen-bit PNG is `IMAGE_PROFILE_UNSUPPORTED` in Level 1 rather than a silent down-conversion. This
keeps the complete-decode allocation and sample semantics bounded; adding it later requires a named
profile change and evidence.

The probe stops after the fixed IHDR. Palette/IDAT/chunk-order, compression, and later CRC failures
remain decoder failures in this first slice; G6-004 adds the hostile matrix rather than a second PNG
parser. Animated PNG, arbitrary metadata interpretation, EXIF-style orientation, and color-profile
policy are unsupported/non-claimed. Output is the JDK decoder's default sRGB RGBA interpretation;
metadata never changes map placement.

The JPEG profile requires:

- exact SOI bytes, marker fill handling, and checked big-endian segment lengths;
- a supported SOF before SOS/EOI within the header-byte limit;
- baseline sequential SOF0 or progressive SOF2, eight-bit sample precision, positive dimensions,
  and exactly one grayscale or three color components with unique IDs, nonzero horizontal/vertical
  sampling nibbles, and quantization selectors 0 through 3; and
- dimensions/components agreeing with every duplicate fact the selected JDK reader reports.

CMYK/YCCK, arithmetic, lossless, differential, hierarchical, and malformed pre-SOF marker profiles
are rejected rather than guessed or reduced. APP/COM payloads are skipped by length and never
interpreted; EXIF orientation is not applied. G6-001 decodes image index zero and makes no trailing-
data or concatenated-JPEG rejection claim because its bounded probe intentionally stops at the first
supported SOF. The complete encoded length is still bounded and hard-fenced. G6-004 decides the
entropy/EOI/trailing-data policy with hostile fixtures rather than pretending the first-slice probe
validated it. The fixed subset keeps color/allocation behavior testable without promising general
JPEG metadata support.

Open validation order is: direct arguments/options/registry structure; channel open and encoded size;
format suffix/signature agreement; physical header fields; dimensions/product/channels/format
limits; placement values; decoder lookup; channel-size recheck; immutable metadata publication.
The cancellable overload checkpoints before/after every positional read and within 4,096 scanned
bytes; the convenience uses `CancellationToken.none()`. Failure closes the channel, preserves the
original error, and suppresses close failure. `RasterSourceMetadata.width/height` are the encoded
dimensions; map bounds and CRS are exactly the placement values, never inferred from filename, pixel
dimensions, coordinates, metadata tags, or range.

### ImageIO decoder and bounded read

`ImageIoRasterDecoder` is the only production class in G6-001 allowed to reference `javax.imageio`,
`java.awt.image`, or Java2D. The image source supplies a package-private positional `InputStream` over
the retained channel with its own `long` position; it caps each file read at 4,096 bytes, checkpoints
between reads, and treats the open-time captured encoded length as hard logical EOF. Single/bulk
`read`, `skip`, and `available` cannot observe or advance past that fence even if the channel grows;
negative/overflow positions are impossible, and `mark/reset` is unsupported. It ignores `close()`
until the source closes the operation. The decoder wraps that
borrowed stream in a `MemoryCacheImageInputStream`. It never changes the parent channel position or
copies the full file eagerly. Reader/image-stream/operation-stream resources are disposed/closed in
reverse order for success, known failure, cancellation, and unexpected failure.

Application decoder selection has already happened in the explicit registry. While constructing
`AwtRasterDecoders.level1()`, the unavoidable JDK `ImageIO` SPI lookup snapshots exactly one PNG and
one JPEG `ImageReaderSpi` whose provider class belongs to the named `java.desktop` module and whose
declared names match the fixed format. Zero eligible readers is
`RASTER_DECODER_JDK_READER_UNAVAILABLE`; more than one is
`RASTER_DECODER_JDK_READER_AMBIGUOUS`. The immutable decoder creates/disposes a fresh reader from
the retained SPI per call, calls `setInput(imageInput, true, true)` (seek forward and ignore
metadata), uses image index zero and a default `ImageReadParam`, and verifies reader dimensions before
decode. It never requests metadata. It never calls `ImageIO.scanForPlugins`, mutates the global registry,
accepts a provider from options or the application class/module path, names an internal `com.sun.*`
class, or uses reflection/`ServiceLoader`. This is a bounded bridge to JDK codecs, not application
plug-in discovery. G6-005 must prove that the same bridge is reachable in Native Image; broad
reachability metadata is not added here.

Fixtures place large ordinary and compressed ancillary PNG/JPEG metadata within the encoded limit and
prove the standard readers do not expose/decompress it through this ignore-metadata path or exceed the
fixed reservation. A provider that cannot honor ignore-metadata semantics for the accepted profile is
not eligible for the Level 1 adapter. G6-004 remains responsible for the broader decompression-hostile
matrix; G6-001 does not claim to parse every ancillary chunk itself.

Before opening the operation stream, the source validates the strict window/output, charges the full
image pixel count actually decoded, and prospectively charges intermediate capacity
`encodedBytes + 8 * fullImagePixels + 4 * outputPixels` plus published capacity
`4 * outputPixels`. Those terms conservatively reserve the memory image-stream cache, accepted JDK
image backing, and final RGBA builder for the <=8-bit profile. Overflow/limit failure occurs before
the opaque call. The built-in decoder claims exactly the encoded, backing, and output terms before
their corresponding stages; those claims consume the reservation without charging it again. After
decode the adapter checks cancellation, exact format/dimensions, and actual
`DataBuffer` bank/type capacity; backing greater than `8 * fullImagePixels` is
`IMAGE_DECODE_FAILED/reason=bufferCapacity` and is discarded. This after-check is the explicit G4
exception for an opaque JDK allocation, not a claim that codec-private transient memory is measurable.

G6-001 decodes one complete image for correctness. It then allocates exactly one final
`RgbaPixelBuffer.Builder` inside the prior reservation and samples the strict requested window with
G4's pixel-center nearest formula. `BufferedImage.getRGB`
at each selected absolute coordinate yields default non-premultiplied ARGB; conversion is exactly
`rgba = (argb << 8) | (argb >>> 24)`. The loop checkpoints at least per output row and within 4,096
pixels, then once before transferring the builder. The returned buffer has the exact requested output
shape. No full RGBA copy, `ImageIcon`, retained `BufferedImage`, hidden resample cache, or Java2D draw
occurs inside the format module.

After the decoder returns, the source independently requires a non-null buffer, exact requested
width/height, a fully claimed intermediate reservation, and the final cancellation checkpoint before
building `RasterRead`. Contract mismatch is `IllegalStateException`, not a hostile-file diagnostic.
The source—not an injected decoder—owns the single published-byte charge and result publication.

Every read first checks source state/token, confirms captured file size and the exact header
snapshot still match, validates/tightens request limits, and checks cancellation before decoder
work. It rechecks size after decode. A changed size is `IMAGE_FILE_LENGTH_MISMATCH`; a changed header
is `IMAGE_DECODE_MISMATCH/field=headerSnapshot`; no stale result is published. Same-size changes
outside the probed header are caller-visible file mutation and receive no cache/identity guarantee in
this uncached slice. Success returns an empty warning report. A known decoder/read failure releases
operation resources but leaves the open source reusable; unexpected runtime/error behavior follows
the G4 cleanup rule.

### Diagnostics

Direct invalid options, bounds, registry construction, request shape, lifecycle, and null values use
ordinary programmer exceptions. File/codec failures use one `SourceException` report and these stable
codes without raw paths, bytes, metadata text, or localized provider messages:

| Code | Component | Stable context |
| --- | --- | --- |
| `IMAGE_FORMAT_MISMATCH` | `image` | `extension`, `signature` |
| `IMAGE_HEADER_INVALID` | `image` | `format`, `field`, `reason` |
| `IMAGE_PROFILE_UNSUPPORTED` | `image` | `format`, `field`, `actual` |
| `IMAGE_DECODER_NOT_REGISTERED` | `decoder` | `format` |
| `IMAGE_DECODE_FAILED` | `decoder` | `format`, `reason`, optional `causeKind` |
| `IMAGE_DECODE_MISMATCH` | `decoder` | `field`, `expected`, `actual` |
| `IMAGE_FILE_LENGTH_MISMATCH` | `image` | `capturedBytes`, `actualBytes`, `reason` |
| `IMAGE_IO_FAILED` | `image` or `decoder` | `operation`, `causeKind` |

Header locations carry an absolute zero-based byte offset when one is known. Limits reuse
`SOURCE_LIMIT_EXCEEDED` with `scope=imageOpen|rasterRead` and the existing exact limit/requested/
maximum keys. Cancellation and close reuse `SOURCE_CANCELLED` and `SOURCE_CLOSE_FAILED`. Encounter
order is signature/header before decoder selection; a decoder mismatch precedes opaque decode; read
failure remains primary and cleanup is suppressed. Unexpected `RuntimeException`/`Error` is not
relabelled as hostile input.

Registry construction is source-independent configuration. Its bounded exception codes are
`RASTER_DECODER_DUPLICATE`, `RASTER_DECODER_JDK_READER_UNAVAILABLE`, and
`RASTER_DECODER_JDK_READER_AMBIGUOUS`; contexts use only `format`, declaration indexes, and eligible
count as applicable. Provider class names/messages are never contract data.

### Runnable viewer and verification

`examples/raster-viewer` is a non-published support application with strict command:

```text
raster-viewer <image.png-or-jpeg>
```

The loader opens the path off the EDT with fixed non-sensitive identity, `AwtRasterDecoders.level1()`,
and explicit EPSG:3857 normalized bounds `[0,0,1,1]`. The window/status text calls this
"normalized demonstration placement — not georeferenced." It attaches the source transactionally as
owned, fits with 16 logical pixels, and creates/shows/closes the `MapView` only on the EDT. Argument,
open, and attachment failure close the current owner exactly once. The example never calls ImageIO,
constructs a `BufferedImage` from the file, invents a CRS from metadata, or contains format branches.

Two tiny checked-in, repository-authored BSD fixtures cover the working codecs: an RGBA PNG with
opaque/translucent regions and a high-quality RGB JPEG with large separated color regions. Adjacent
test provenance records the generation command/tool version, exact dimensions/profile, byte length,
and SHA-256. They are test/example evidence, not native resources or a corpus. PNG source-read tests
assert exact intrinsic/output dimensions, row direction, alpha, strict subwindows, and nearest
up/downsampling. JPEG assertions use per-channel tolerance 20 away from block boundaries. A fixed
offscreen 256-by-160 identity-CRS view checks transformed bounds, background outside the normalized
extent, representative interior regions, and a bounded non-background count; it never compares a
whole image.

Format tests cover every accepted PNG bit-depth/color-type pair at header level, baseline/progressive
JPEG and grayscale/color profiles, matching/mismatched suffix and signature, malformed/truncated
headers, unsupported profiles, all limit minus/equal/plus-one and overflow cases, missing/duplicate
decoder registration, channel seek/reset, mutation, cancellation before/after opaque decode and
during conversion, reusable source after known failure, successful/failed/double close, and retained metadata/results. Injected
channels/decoders prove primary/suppressed cleanup without a public filesystem SPI.

Architecture tests add the working image module to the one project inventory; prove its API/core-only,
JDK-only, AWT-free graph; confine ImageIO/Java2D references to the exact AWT decoder; and reject
service descriptors, provider registration/scan calls, reflection, resource enumeration, external
libraries, background work, caches, or native mechanisms. Publication staging includes the new image
binary/sources/Javadocs with only approved project dependencies and excludes example/test fixtures.
The focused validation adds architecture checks and `publicationDryRun` because this task introduces
a public module. It does not invoke render-regression, native, corpus, or performance lanes.

### Simplicity check

The slice has one static format facade, one options value, one format-limits value, one source, one
fixed header probe, one operation-scoped decoder context, one immutable explicit registry, and one AWT
decoder. The decoder boundary exists only because the required JDK codec is in `java.desktop` while
the format module must remain toolkit-neutral. It does not become a generic codec framework: formats
are the two Level 1 enum values, the registry enables explicit replacement rather than new format
claims, and the source is still the only owner of metadata, limits, lifecycle, and diagnostics.

No alternate image model, layer kind, color hierarchy, plugin manager, cache, affine placeholder,
provider abstraction, or background loader is needed for the observable slice. The complete-decode
implementation is intentionally simple and bounded; G6-003 may optimize through the already-real
request context, and G6-004 may add evidence-backed caches without changing the format-neutral source
contract.

### G6-001 implementation closeout

The implemented slice matches this boundary without adding a parallel raster model. The format
module exposes only `RasterImages`, `ImageOpenOptions`, `ImagePlacement`, and `ImageSourceLimits`;
all channels, probes, snapshots, and accounting contexts remain private. The unavoidable API seam is
the fixed PNG/JPEG enum, operation context, decoder, and immutable explicit registry. AWT owns the
only ImageIO reader selection and pixel conversion, while architecture tests enforce the exact
`java.desktop` qualification and keep the format module AWT-free.

Two checksummed repository-authored Base64 fixtures exercise exact PNG alpha and tolerant JPEG color
through the production decoder. The viewer consumes the same `RasterSource` path with an explicit
normalized EPSG:3857 placement labelled not georeferenced. Publication staging includes the new
module and excludes the viewer/test fixtures. This preserves the intended extension points for
G6-002 through G6-004: placement, request controls, and caching can grow around a working bounded
source rather than replacing speculative scaffolding.

## World-file affine georeferencing (G6-002)

### Placement model and invariants

G6-002 adds one real format-neutral affine placement rather than placing coefficients in
`RasterRequest`, stretching an affine image to its envelope, or teaching the image format about
Java2D. API adds two immutable values:

```text
RasterAffineTransform
  of(double a, double d, double b, double e, double c, double f)
  a(), d(), b(), e(), c(), f()
  gridToMap(double columnCenter, double rowCenter) -> Coordinate
  mapToGrid(Coordinate mapCoordinate) -> Coordinate

RasterGridPlacement
  axisAligned(Envelope exactBounds)
  affine(RasterAffineTransform transform)
  kind() -> AXIS_ALIGNED | AFFINE
  axisAlignedBounds() -> Optional<Envelope>
  affineTransform() -> Optional<RasterAffineTransform>
```

The world-file equation is exact:

```text
mapX = A * columnCenter + B * rowCenter + C
mapY = D * columnCenter + E * rowCenter + F
```

Integer grid coordinates are pixel centers. Full raster outer corners are
`(-0.5,-0.5)`, `(width-0.5,-0.5)`, `(width-0.5,height-0.5)`, and
`(-0.5,height-0.5)`. A window uses the same half-cell convention. `RasterAffineTransform` stores the
six canonicalized finite coefficients and a private precomputed inverse; equality/hash/toString use
only the public coefficients.

Construction rejects a zero or unrepresentable linear transform without an arbitrary epsilon. Let
`scale = max(abs(A),abs(B),abs(D),abs(E))`; it must be positive and finite. Compute the determinant
from the four coefficients divided by `scale`, then derive inverse coefficients as normalized inverse
terms divided once more by `scale`. Zero/non-finite normalized determinant, inverse coefficient, or
inverse translation fails. This avoids overflowing `A*E-B*D` solely because units are large. Forward
and inverse methods require finite input, use checked `Math.fma` composition, and throw
`ArithmeticException` if a finite output cannot be represented. Signed zero is canonical positive
zero.

The final tagged placement stores exactly one payload. `axisAlignedBounds()` is present only for
`AXIS_ALIGNED`; `affineTransform()` is present only for `AFFINE`. Returned values are immutable and
the absent accessor never fabricates a default.

`RasterSourceMetadata` retains `mapBounds()` and gains
`Optional<RasterGridPlacement> gridPlacement()`. Its existing five-argument constructor remains
source-compatible: an existing present `mapBounds` creates an axis-aligned placement with the same
exact envelope, while absent bounds creates absent placement. One new factory derives the redundant
envelope rather than making callers reproduce it:

```text
RasterSourceMetadata.withPlacement(identity, width, height, placement, crs)
```

The factory stores the exact axis envelope or derives the affine envelope from the four outer corners
and dimensions. Unplaced metadata continues through the old absent-bounds constructor. No public path
accepts both placement and bounds, so callers cannot create contradictory state. For affine placement,
the represented NW-to-NE and NW-to-SW edge differences must be finite/nonzero and have a nonzero
scaled determinant after translation; the derived envelope must have strictly positive finite x/y
spans. This rejects a mathematically invertible transform whose tiny basis collapses after adding a
large translation. Axis-aligned rasters retain every G4 exact edge
and ULP outcome; they are not converted to approximate affine coefficients. `mapBounds` remains the
conservative fit/report envelope, while `gridPlacement` is the painting/window authority. There is no
public quadrilateral, matrix hierarchy, ground-control-point model, or CRS operation in this value.

### World-file placement request and sidecar snapshot

The working G6-001 `ImagePlacement` gains only implemented variants:

```text
worldFile()
worldFile(Optional<CrsMetadata> crs)
worldFile(CrsMetadata crs)
```

This is an opener instruction. A world file contains no CRS; absent remains absent, unknown remains
unknown, and recognized caller metadata is retained exactly. No PRJ/WKT, filename/range guess,
default EPSG value, or transformation is added. Non-world-file modes do not inspect sidecars.

For the image's exact lexical stem, the finite candidate order is:

```text
PNG:  .pngw .PNGW .pgw .PGW .wld .WLD
JPG:  .jpgw .JPGW .jgw .JGW .wld .WLD
JPEG: .jpegw .JPEGW .jgw .JGW .wld .WLD
```

Each suffix replaces the image suffix and is resolved as a direct sibling; the opener never lists a
directory. Mixed-case sidecar suffixes are unsupported. Lower/upper names that the filesystem reports
as the same file collapse to one candidate. No candidate is `IMAGE_WORLD_FILE_MISSING`; more than one
distinct file is `IMAGE_WORLD_FILE_AMBIGUOUS`; failure while probing or proving identity is the
existing `IMAGE_IO_FAILED` with `operation=probe|identity`. There is no priority/fallback among long,
short, and `.wld` variants, so adding a second file cannot silently change which transform wins.

The primary image open/header/format limits succeed before the requested sidecar is snapshotted. The
sidecar outcome then precedes decoder lookup. A bad image therefore wins over missing world data, and
a requested missing/malformed world file wins over a missing decoder. A candidate appearing after the
finite snapshot is ignored; the selected file is opened through the same package-private file seam,
read and closed during the opening transaction, and never retained by the source.

The snapshot fixes candidate selection, not a filesystem lock. A replacement completed before the
selected path is opened is accepted and its successfully read bytes become authoritative. Removal or
I/O failure before open/read fails without rescan or fallback. The read uses one captured size and one
returned byte sequence; a concurrent same-size rewrite may therefore yield the old bytes, new bytes,
or a mixed sequence whose deterministic parse succeeds/fails according to those bytes. Detecting such
same-size mutation is explicitly unsupported and the size recheck does not claim otherwise.

### Bounded six-line parser

`ImageSourceLimits` adds positive `maximumWorldFileBytes = 4,096` and
`maximumWorldFileLineBytes = 256`. The selected file's captured size is checked before its sole byte
array, read exactly to that hard fence, size-rechecked, and closed. Both bounds use the existing
`imageOpen` prospective limit behavior; equality passes and plus one fails.

The per-line count includes every byte from line start through the last byte before LF, CRLF, or EOF,
including indentation and trailing SP/TAB. LF and both CR/LF bytes are excluded. The first content
byte beyond the ceiling is the limit location; a CR after exactly 256 content bytes is instead parsed
as a terminator and must be followed by LF. Whole-file bytes include all terminators.

The existing six-value G6-001 constructor remains source-compatible and delegates the two new fields
to those defaults. A new eight-value constructor exposes all limits in established field order;
accessors, both new withers, value equality/hash/string, `defaults()`, and every existing wither retain
all eight values. The two world-file ceilings are appended after the existing six constructor
arguments. No reflective/default-field migration is used.

Input is strict US-ASCII with no BOM. The exact grammar is:

```text
decimal := [+-]? (digits+ ('.' digits*)? | '.' digits+) ([eE][+-]? digits+)?
line    := (SP | TAB)* decimal (SP | TAB)* (LF | CRLF | EOF on line six)
file    := exactly six nonblank lines, with at most one final line ending
```

Bare CR, blank/extra line, comment, multiple token, hex, locale comma, control/non-ASCII byte,
`NaN`, `Infinity`, malformed exponent, missing value, and extra trailing content fail. Per-line bytes
are checked before substring/String construction. After lexical validation, `Double.parseDouble`
must produce a finite value; signed zero is canonicalized. Physical line order maps exactly to
`A,D,B,E,C,F`. Raw text and numbers are not retained or emitted in diagnostics.

Cancellation is checked before/after candidate probe, same-file identity, sidecar open/size/
allocation/read/close, within 4,096 controlled bytes, during transform/corner construction, and before
metadata/source publication. Cleanup itself never polls. World-file failure closes its temporary
handle and the still-transaction-owned image channel; the operation failure stays primary and cleanup
failures are suppressed. A successfully published source holds immutable coefficients, so later
sidecar mutation/removal has no effect.

### Affine window planning

Core extends `RasterGridWindows` by placement kind. Axis-aligned dispatch remains byte-for-byte the G4
binary-search behavior. For affine `mapBounds(metadata, window)`, transform the four window outer
corners `(column-0.5,row-0.5)` through `(column+width-0.5,row+height-0.5)` and return their finite
envelope.

Affine `visibleWindow` is one fixed-purpose primitive algorithm:

1. intersect the visible display envelope with the stored affine `mapBounds`; empty/line/point contact
   returns empty before inverse work;
2. enumerate that intersection exactly as NW `(minX,maxY)`, NE `(maxX,maxY)`, SE `(maxX,minY)`, SW
   `(minX,minY)` and inverse-transform in that perimeter order into grid-center coordinates;
   non-finite/unrepresentable inverse arithmetic throws `ArithmeticException` rather than pretending
   the geometry is empty;
3. scale the inverse vertices and raster boundaries by one common power of two chosen from their
   maximum absolute magnitude, then clip that convex parallelogram against the equally scaled form of
   raster rectangle `[-0.5,width-0.5] x [-0.5,height-0.5]` with a four-edge
   Sutherland-Hodgman pass using fixed local
   arrays for at most eight vertices and convex interpolation in the scaled coordinates;
4. return empty for zero-area, line, or point intersection before integer rounding; and
5. scale the clipped bounds back (requiring finite values within the raster rectangle) and derive a
   conservative half-open integer cell range using one
   `nextDown`/`nextUp` outward step around the half-cell formulas and clamping only the view-derived
   result to `[0,width] x [0,height]`.

The exact x formulas (and analogously y) are:

```text
start = clamp(floor(nextDown(clippedMin + 0.5)), 0, width)
end   = clamp(ceil(nextUp(clippedMax + 0.5)), 0, width)
```

The common power-of-two normalization avoids overflow without changing line intersections. In those
scaled coordinates, subtract the first vertex before the fixed shoelace cross sums; the finite
absolute area must be strictly positive before the integer formulas run. A representable thin
positive intersection therefore remains positive, while a collapsed floating result is honestly
empty rather than repaired with an epsilon.

The clip accepts either winding (a negative affine determinant reverses it) but never reorders
vertices. After each clip edge it removes consecutive exact duplicate vertices and does not retain a
closing duplicate; fewer than three distinct vertices is empty. Non-finite normalization,
intersection, rescaling, or shoelace arithmetic throws `ArithmeticException`, never a clean empty
result.

The outward step may include at most one fringe cell per edge; the component clip removes its paint.
Direct `RasterSource.read` remains strict and never clips/repairs a caller's window. Checked long index
math handles large valid dimensions. This is not a public polygon, topology, warp, Java2D `Area`,
densification, tolerance, or per-pixel inverse scan.

Tests first rerun every G4 axis edge, `nextUp`/`nextDown`, collapsed-cell, touching, and huge-dimension
oracle unchanged. Affine cases cover full/partial/disjoint/touching views, corner-only and edge-crossing
overlap, negative coefficients, rotation, shear, thin positive intersections, one-cell rasters,
conservative fringe bounds, and non-finite inverse/corner/envelope failure.

### True-parallelogram AWT placement

After a successful contained-window read, AWT maps output-image edge coordinates to absolute grid
center coordinates:

```text
columnCenter = window.column - 0.5 + imageX * window.width / outputWidth
rowCenter    = window.row    - 0.5 + imageY * window.height / outputHeight
```

It then applies `RasterAffineTransform` and the existing display-world-to-screen viewport transform.
The composed Java2D `AffineTransform` draws the image once on a disposable child using the existing
nearest/SRC_OVER policy and component clip. Axis-aligned placement retains the exact G4 edge-rectangle
path. G6-002 MapView output dimensions still equal the selected window dimensions; the general formula
is real because direct requests already permit another output shape and G6-003 changes view planning.

An affine raster is placed within its declared source/display CRS; it is not reprojected. Recognized
source and display definitions must still be exactly equal. A different recognized CRS remains
`CRS_RASTER_WARP_UNSUPPORTED`; missing/unknown metadata retains the existing attachment failures; an
out-of-domain affine envelope reaches the existing strict CRS envelope diagnostic. Direct source reads
remain possible without attachable CRS. Fit uses the four-corner affine envelope without reading.

Raster alpha, reports, success/failure/cancellation arbitration, base order, no-hit behavior, combined
allocation preflight, and owned/borrowed lifecycle remain G4. No affine branch changes decoder
registration, caches a transformed image, or adds another layer type.

### Diagnostics, viewer, and evidence

World-file diagnostics add only:

| Code | Location/context |
| --- | --- |
| `IMAGE_WORLD_FILE_MISSING` | component `worldFile`, empty location/context |
| `IMAGE_WORLD_FILE_AMBIGUOUS` | component `worldFile`; exact `candidate0..candidateN`, `candidateCount` |
| `IMAGE_WORLD_FILE_INVALID` | first byte/EOF; `reason`, and assigned `coefficient` when applicable |
| `IMAGE_WORLD_FILE_TRANSFORM_INVALID` | component `worldFile`, no byte offset; exact `reason` only |

Invalid parser reasons are exactly `empty`, `encoding`, `lineCount`, `number`, `nonFinite`,
`truncated`, and `sizeChanged`. Exceeding either whole-file or per-line bytes is instead
`SOURCE_LIMIT_EXCEEDED` with `scope=imageOpen` and `limit=worldFileBytes|worldFileLineBytes`; it never
also emits `lineLength`. Transform-invalid reasons are exactly `singular`, `inverseNonFinite`,
`cornerNonFinite`, `envelopeNonFinite`, and `envelopeNonPositive`. Individual lexical/non-finite
coefficient failures use the assigned line's start/first offending byte and include `coefficient`;
derived transform reasons never guess a responsible coefficient or byte.

An ambiguity context has decimal `candidateCount` and contiguous `candidate0` through `candidateN-1` in
distinct-file declaration order. Each value is the first declaration token representing that
same-file alias group, such as `longLower` or `shortUpper`; at most six candidates fit the global
context bound. Existing `IMAGE_IO_FAILED` permits component `worldFile` and operation
`probe|identity|open|size|read|close`. No candidate filename/path, coefficient value, raw line, or
provider text enters a report. A successful world-file open has no warning.

The example commands are exact:

```text
raster-viewer <image>
raster-viewer <image> --world-file EPSG:4326|EPSG:3857
```

The first retains G6-001 normalized, explicitly non-georeferenced placement. The second requests one
sidecar and declares the CRS; no automatic fallback occurs. Argument/registry validation precedes
open. The loader remains off EDT; owned attachment/fit/window/close remain on EDT; the example never
parses coefficients or branches on PNG versus JPEG.

Tiny checksummed BSD fixtures include north-up PNG, rotated/sheared PNG with distinct alpha cells,
projected JPEG with large tolerant color regions, and geographic EPSG:4326 within domain. Parser tests
exercise every candidate/case/same-file/ambiguity outcome; grammar/line/byte/number boundary; finite,
singular, inverse, corner, envelope, and size-change path; cancellation and primary/suppressed cleanup.
CRS tests cover missing, unknown, mismatch, exact geographic/projected, and domain rejection.

Offscreen evidence maps known pixel centers through the public affine value and public MapView
conversion, asserts tolerant 3-by-3 regions (exact PNG, JPEG tolerance 20), proves an envelope point
outside the transformed parallelogram stays white, contains non-white pixels within the transformed
outer-corner envelope plus a small antialias allowance, and bounds total paint. Fit and file deletion
after open/startup/view-close failures are explicit. No whole-image golden is used.

Architecture tests keep affine values/algorithms in API/core `java.base`, keep io-image AWT-free, and
leave the exact ImageIO qualification solely in the approved AWT decoder. No sidecar discovery,
directory list, retained handle, cache, thread, new provider behavior, reflection, service descriptor,
or native metadata is added. G6-005 owns actual native resource/reachability evidence. Focused checks,
`qualityGate`, and whitespace are the complete G6-002 validation; publication, native, render-
regression, corpus, and performance lanes do not run.

### G6-002 simplicity check

One tagged placement preserves exact G4 axis semantics and adds the one affine variant required by a
working world-file slice. One finite sidecar snapshot, six-number parser, invertible transform, fixed
four-corner clip, and true affine draw are sufficient. Bounds cannot contradict placement; the
envelope is never mistaken for the parallelogram; CRS is never guessed; and no request, decoder,
polygon, warp, or persistence framework is introduced.

### G6-002 implementation closeout

The implemented slice retains the design's single placement authority. `RasterGridPlacement` carries
either the exact G4 envelope or one immutable six-coefficient transform, and
`RasterSourceMetadata.withPlacement` alone derives the redundant affine envelope from pixel outer
corners. Existing metadata construction still creates the exact axis payload, while unplaced sources
remain unplaced. Core dispatches on that tag without changing the established axis-edge search or
repairing direct requests.

The format module adds no public parser or sidecar abstraction. Its existing opener transaction takes
one finite direct-sibling snapshot, collapses same-file aliases, rejects ambiguity, reads one bounded
temporary channel, parses strict ASCII while that channel remains cleanup-owned, and publishes only
immutable coefficients. This ordering preserves operation failures as primary and cleanup failures as
suppressed. Caller CRS metadata is retained literally; filesystem names and numeric contents never
enter diagnostics, and no fallback, `.prj`, directory listing, provider discovery, or retained
sidecar handle exists.

MapView continues to own one raster snapshot and one draw. Axis placement uses its exact rectangle;
affine placement composes source-window edges, the public pixel-center transform, and the existing
world-to-screen viewport into one disposable Java2D transform. The fixed component clip leaves the
conservative window fringe unpainted. Real PNG and JPEG tests, the explicit viewer mode, architecture
checks, and tolerant offscreen evidence all consume this path. G6-003 can evolve request output and
interpolation without replacing placement, and no cache, warp, reprojection, polygon API, or alternate
raster layer was introduced.

## Raster requests and rendering controls (G6-003)

### Dependency and request evolution

G2-005 owns the toolkit-neutral `RasterInterpolation = NEAREST | BILINEAR` value because decoded raster
icons need it first, and G2-006 owns the `renderRegression` lane. G6-003 therefore depends on G2-006
as well as G6-002; it reuses the enum rather than defining a duplicate. The G4 request evolves
source-compatibly:

```text
RasterRequest(
    RasterWindow sourceWindow,
    int outputWidth,
    int outputHeight,
    RasterInterpolation interpolation,
    Optional<RasterRequestLimits> tighterLimits)
```

The existing constructor without interpolation delegates to `NEAREST`. Null interpolation is a
parameter-named programmer failure. Cancellation remains solely on
`RasterSource.read(request,cancellation)`, so a request is immutable/reusable. Opacity is presentation
state and never enters a request, read/result, decoder/cache identity, or source metadata.

G6-001's `EncodedRasterDecodeContext` gains one source-compatible default
`interpolation() -> NEAREST`; the concrete image context overrides it from the request. Existing
G6-001 decoders/fakes also inherit
`supportsInterpolation(mode) -> mode == NEAREST`; the Level 1 AWT decoder explicitly returns true for
both enum values. The image source checks support before decode. False terminates with
`IMAGE_DECODER_INTERPOLATION_UNSUPPORTED`, component `decoder`, and exact context `format` and
`interpolation`; a throwing/nondeterministic capability method is an implementation contract failure.
Thus an already-compiled nearest-only decoder cannot silently return nearest pixels for a bilinear
request. `RasterRead` still echoes the exact source window and output dimensions; interpolation need
not be repeated in the result.

### Exact shared resampling math

Core adds one final JDK-only algorithm utility `RasterResampling`, not an SPI/pixel-source hierarchy.
It exposes checked axis plans and RGBA blending used by `SyntheticRasterSource` and AWT's image
decoder:

```text
nearestIndex(outputIndex, sourceSize, outputSize) -> int
bilinearAxis(outputIndex, sourceSize, outputSize) -> AxisWeights
bilinearRgba(nw, ne, sw, se, xWeights, yWeights) -> int
```

`AxisWeights` is one immutable nested core value containing lower/upper indexes, their non-negative
integer weights, and the positive denominator. Callers add the strict source-window origin after
planning; no callback, array, image, or toolkit value enters core.

Nearest remains the G4 rule exactly:

```text
floor(((2 * outputIndex + 1) * sourceSize) / (2 * outputSize))
```

All multiply/add operations are checked in `long`; exact half-cell ties select the greater/right or
bottom cell. Bilinear defines the output-center position relative to source cell centers with:

```text
numerator   = (2 * outputIndex + 1) * sourceSize - outputSize
denominator = 2 * outputSize
position    = numerator / denominator
```

Positions at/below zero clamp both indexes to zero. Positions at/above `sourceSize-1` clamp both to
the final index. Otherwise lower is floor division, upper is lower+1, upper weight is the non-negative
remainder, and lower weight is denominator minus remainder. A singleton source axis repeats its only
sample. The operation is window-local: indexes never escape the strict requested window, so its outer
cell centers extend to the output edge without hidden filter padding.

Bilinear blending uses the Cartesian products of x/y weights in straight sRGB component space but
premultiplies color by alpha for the accumulation. With total weight `W`:

```text
alphaNumerator   = sum(weight * alpha)
outputAlpha      = roundHalfUp(alphaNumerator / W)
channelNumerator = sum(weight * alpha * channel)
outputChannel    = roundHalfUp(channelNumerator / alphaNumerator)
```

When `alphaNumerator` is zero or rounded `outputAlpha` is zero, the result is canonical transparent
black. Otherwise channels and alpha are clamped only after exact checked `long` arithmetic. This
prevents hidden RGB in transparent pixels from bleeding into visible output and avoids
floating/platform differences. Nearest copies the exact
unpremultiplied `0xRRGGBBAA` sample. Synthetic source tests make both modes part of the generic G4
contract rather than an image-only behavior.

Before loops, checked arithmetic validates all axis denominators/products, four taps per output pixel,
and worst-case weight/alpha/channel sums against `long`. The four-tap count is derived bounded work,
not another public limit. Cancellation checkpoints run at least per output row and within 4,096
output pixels or source taps.

### Bounded ImageIO region and subsampling plan

Every image read retains G6-001's conservative full-image source-pixel charge and intermediate
reservation because setting an ImageIO region is not proof that a compressed codec avoided full
decode. The source still validates the strict window, output shape, header/content state, and limits
before the explicit decoder.

The AWT adapter always passes the strict `RasterWindow` as `ImageReadParam.setSourceRegion`. For
nearest only, each axis independently uses integer source subsampling when:

```text
sourceAxisSize % outputAxisSize == 0
factor = sourceAxisSize / outputAxisSize
offset = floor(factor / 2)
```

That factor/offset produces exactly the G4 nearest indexes. An ineligible axis uses factor 1/offset 0;
project code resamples it afterward. Every bilinear request uses the source region but no ImageIO
subsampling because discarded neighbor samples would change the oracle. The decoder validates the
returned region/subsample dimensions before accepting the image. Package-private test probes may
observe requested hints, but public metadata/diagnostics never claim codec work was avoided.

The adapter converts JDK default non-premultiplied ARGB samples to RGBA and applies
`RasterResampling` only where an axis was not already sampled exactly. It uses one final
`RgbaPixelBuffer.Builder`, retains no full RGBA matrix/cache, and keeps the G6-001
`setInput(...,true,true)`, provider, reservation-claim, hard-fence, cleanup, and final source result
validation rules. PNG results are exact; JPEG decode tolerance is applied to source-region evidence,
while project resampling itself remains deterministic.

### Affine viewport output planning

G6-002 continues to choose the strict visible source window by inverse clipping. G6-003 chooses only
the MapView request's output density. Transform one grid-column basis vector and one grid-row basis
vector through the retained placement and current viewport into logical screen space. For selected
window dimensions `W,H`:

```text
plannedWidth  = cap(W, columnBasisLength)
plannedHeight = cap(H, rowBasisLength)
```

`cap(size,basisLength)` requires a finite non-negative norm. If the norm is at least 1, it returns
`size` without multiplying; otherwise it returns `max(1, ceil(size * basisLength))`, whose product is
already bounded by `size`. A huge finite zoom-in therefore caps at source resolution instead of
failing an irrelevant intermediate int conversion. This is
screen-density downsampling only: MapView never invents source detail during zoom-in. Direct callers
may still request any G4-valid output size, including upsampling. The explicit plan is not silently
reduced to satisfy limits; window/output/pixel/byte failure follows normal diagnostics.

After deterministic source resampling, Java2D maps output edges to the selected axis rectangle or
affine parallelogram exactly as G6-002 and always uses nearest-neighbor for the final draw. Selecting
Java2D bilinear here would filter twice and make results platform-dependent. Component clipping may
discard conservative fringe paint; it does not alter the strict request.

### Immutable raster presentation state

AWT adds one final immutable value:

```text
RasterRenderOptions(RasterInterpolation interpolation, double opacity)
  defaults() -> NEAREST, 1.0
  withInterpolation(...)
  withOpacity(...)
```

Opacity must be finite in `[0,1]`; it is rejected rather than clamped. Existing borrowed/owned raster
binding factories use defaults, and overloads accept an initial options snapshot. Accepted `-0.0` is
canonicalized to `+0.0` before equality/hash/storage. MapView owns the
current snapshot for each installed raster binding and exposes EDT-only
`setRasterRenderOptions(layerId, options)`. It rejects a missing/non-raster ID, does not replace,
reclaim, reopen, or close the source, and schedules one full repaint. Options are captured once per
paint transaction, so interpolation and opacity cannot mix across a pass.

Opacity zero performs no source read, conversion, or draw, but still participates in fit/base order.
Because no source operation occurred, it preserves the binding's prior report/availability rather
than fabricating clean recovery. Nonzero opacity uses one disposable graphics child with
`AlphaComposite.SRC_OVER` and constant `(float) opacity`; packed pixel alpha and layer opacity compose
once. Source pixels stay unmodified and opacity does not affect decoder/cache identity.

The raster viewer adds a `NEAREST`/`BILINEAR` selector and 0–100% opacity control. EDT events replace
only the view-owned immutable options and repaint; loading, world-file parsing, CRS, and source
ownership remain unchanged/off EDT. Status shows current controls plus normalized versus world-file
placement. The example never calls ImageIO or implements interpolation/affine math.

### Cancellation, accounting, and verification

The G4 atomic operation remains: plan and validate, decode, exact resample, build immutable read,
convert RGBA-to-ARGB, win `ACTIVE -> SUCCEEDED`, then draw. Cancellation checks occur before decoder,
immediately after opaque ImageIO, within nearest/bilinear loops, before buffer transfer, within AWT
conversion, and immediately before success publication. Failure/cancellation discards every partial
value, draws nothing, publishes one terminal report, and leaves a known-failure image source reusable.

Prospective charges cover the unchanged conservative decode reservation, final RGBA output, separate
AWT ARGB output, and any fixed axis/planner scratch. The tap count is checked work but does not double-
charge source pixels. A successful decoder/read must still return the exact window/output shape.
Opacity affects only the final composite.

API tests cover request constructor compatibility, enum/null validation, tighter limits, and strict
windows. Core tests cover nearest ties/up/down cases; bilinear identity/1x1/1xN/Nx1/quarter weights,
edge clamp, transparent-color bleed, alpha rounding, overflow, window locality, and synthetic parity.
Image tests use a controlled reader to assert source region, independently eligible nearest factors/
offsets, no bilinear subsampling, returned-shape mismatch, and equality with extracted-matrix oracles.

Affine tests cover rotated/sheared partial views, screen-density output, thin intersections, mapped
corners, clipping/background, and fit. Rendering covers opacity 0/0.5/1, translucent pixels, single
SrcOver composition, no double filter, and mixed vector/raster order. Cancellation is injected at
every controlled stage. Viewer tests prove options update without source/binding replacement and
close once. Offscreen/render-regression assertions use tolerant regions/bounds and exact project math,
never whole-image identity.

Architecture tests keep API/core/io-image AWT-free, confine ImageIO/Java2D/render options to AWT, and
reject cache, background/discovery, affine-as-CRS, or native metadata additions. `renderRegression`
runs separately after focused checks because this task changes rendering and G2-006 already created
the lane. `qualityGate` and whitespace then run; native, publication, corpus, and performance lanes do
not.

### G6-003 simplicity check

One existing two-value interpolation enum, one core integer math utility, one source-compatible
request/context evolution, and one immutable AWT options value cover the slice. Resampling occurs once;
opacity stays presentation-only; affine placement stays placement-only; and ImageIO hints never become
correctness claims. There is no filter SPI, generic pixel source, warp framework, cache, worker, or
replacement binding lifecycle.

## Raster cache, lifecycle, and hardening (G6-004)

### One cache and one policy

G6-004 retains exactly one cache: a private per-`ImageRasterSource` map of canonical, already decoded
and resampled toolkit-neutral `RgbaPixelBuffer` values. It adds no encoded-byte cache, `BufferedImage`
cache, AWT/ARGB render cache, shared decoder cache, disk cache, or cache hierarchy. G7 measures the
complete path before any other retained layer is justified.

The image module adds one public immutable value:

```text
ImageCachePolicy
  disabled()
  bounded(int maximumEntries, long maximumPixelBytes)
  defaults() -> bounded(8, 33_554_432)
  enabled() -> boolean
  maximumEntries() -> OptionalInt
  maximumPixelBytes() -> OptionalLong
```

Bounded values are positive; disabled is a distinct variant, never a zero/unlimited sentinel. This
intentionally supersedes G6-001's slice-local count of four public image types. The evolved options
shape is

```text
ImageOpenOptions(imageLimits, requestLimits, placement, cachePolicy)
```

and gains `withCachePolicy`; its existing three-value construction delegates to `defaults()`. The
defaults are a correctness/resource envelope, not a throughput promise. G6-001's no-retained-result
and externally-serialized statements likewise describe the earlier uncached source and are replaced
only for the concrete `ImageRasterSource` by the cache and monitor below.

The private key is exactly:

```text
ImageContentVersion(capturedLength, sha256)
RasterWindow
outputWidth
outputHeight
RasterInterpolation
```

The content version is identical for every entry in one source but remains explicit so stale entries
cannot survive future invalidation changes. `SourceIdentity` is not a key because IDs are source-local
and may repeat across instances. Format/decoder/profile/placement are immutable per source. Tighter
limits and cancellation are invocation policy; opacity, CRS, viewport, affine placement, diagnostics,
and ImageIO hint choice do not alter pixels and stay out of the key. Any later pixel-affecting option
must extend the key or invalidate the owner.

Entry weight is checked `4 * outputPixels`. One insertion-ordered `LinkedHashMap` implements
least-recently-successful use: a successful hit removes/reinserts its entry; unsuccessful/cancelled
lookups do not promote. Admission evicts oldest successful entries until both entry/byte budgets fit.
Equality fits. An entry larger than either budget bypasses caching without eviction or read failure.
No soft/weak reference, static/global state, timer, worker, future, executor, or background eviction
exists.

### Consumer ownership and limit preflight

The cache never returns its retained instance. On a hit, the source builds a fresh independently owned
buffer with cancellation checkpoints. On an admissible miss, the decoder-produced immutable buffer
becomes the retained entry and a fresh copy becomes the consumer result. If that extra miss-side copy
cannot fit the effective intermediate-byte ceiling, the source admits nothing and returns the decoder
buffer directly; caching never turns a successful uncached request into failure.

Before lookup, a disposable `RasterRequestAccounting` instance validates the complete uncached
G6-003 plan under the request's tighter limits: full-image source pixels, one 4,096-byte digest
scratch, two encoded-length terms
(the G6-001 memory image stream plus the G6-004 operation snapshot), eight bytes per full source pixel,
four final RGBA bytes per output pixel, and normal published output. It is independent of the real
operation counter, just like G4's render-path preflight, so a cached hit cannot bypass a limit that
would reject the same cold request. Actual hit accounting charges fixed digest scratch plus the fresh
four-byte-per-pixel intermediate output copy and published payload. Actual miss accounting follows the
preflight terms. Admission needs a second simultaneous four-byte-per-pixel intermediate output: if
that prospective cumulative charge exceeds the intermediate ceiling, admission is bypassed and the
decoder buffer itself becomes the published result. No accounting counter rolls back and no public
cache/accounting SPI is added.

Package-private immutable metrics snapshots exist only for same-package tests. All cumulative counters
saturate at `Long.MAX_VALUE`, update only when the read result commits after its final cancellation
checkpoint except for a version invalidation, and never affect behavior:

| Counter | Exact event |
| --- | --- |
| `hits` | A successful read returns a fresh copy of an existing enabled-cache entry. |
| `misses` | A successful enabled-cache read found no matching key; admissions and enabled bypasses also count here. |
| `admissions` | A successful miss inserts one retained entry. |
| `evictions` | An entry is removed solely to commit a successful admission. |
| `disabledBypasses` | A successful read decoded while its captured policy was disabled; it is not also a miss. |
| `oversizedBypasses` | A successful enabled miss exceeded an entry/byte budget and was not admitted. |
| `accountingBypasses` | A successful enabled miss could not charge the admission copy and was not admitted. |
| `invalidations` | One published-source version mismatch clears the cache, even when it was empty. |

The snapshot also exposes current entries and current retained pixel bytes. Close clears references but
does not count eviction or invalidation; failed/cancelled/contract-invalid reads change no
success-only counter. No public metrics, timing, logging, JMX, or observer is introduced.

### Exact content version and operation snapshot

After G6-001 header/profile limits and G6-002 sidecar snapshot, open checks the channel size, reads
exactly that complete bounded file into one operation-local byte array, requires the exact read count,
and checks the channel size again. It validates the full container below and computes literal
`MessageDigest.getInstance("SHA-256")`. The snapshot allocation remains fenced by
`maximumEncodedBytes`; the inflater never allocates the declared inflated size. A second positional
streaming fingerprint pass requires the captured size both before and after, consumes exactly the
captured byte count through a 4,096-byte scratch, and must match the snapshot digest before source
publication. The byte array is then discarded; the source retains only length plus a defensive
32-byte digest. This exact algorithm has no provider name/option and no public/diagnostic digest. G0
architecture tests allowlist only this literal production call; it does not authorize arbitrary
algorithm/provider lookup.

Every read fingerprint pass checks captured size, compares the exact header snapshot, consumes exactly
the captured bytes into SHA-256 through one fixed 4,096-byte scratch, then checks size again before
cache lookup. A successful hit has therefore observed baseline bytes with stable length and may return
the known baseline entry. On a miss, the source again checks
size, reads exactly the captured bytes into one operation-local snapshot, compares its exact header and
SHA, and checks size after the read. It decodes only from a `ByteArrayInputStream` over those verified
exact bytes and never from a concurrently changing channel. The snapshot's additional encoded length
is charged before allocation; the existing decoder still reserves its own memory image-stream
capacity. No snapshot survives the read or becomes an encoded cache.

Any before/after size or exact-read-count mismatch keeps `IMAGE_FILE_LENGTH_MISMATCH`; header mismatch
keeps `IMAGE_DECODE_MISMATCH/field=headerSnapshot`; same-length/header body mismatch is
`IMAGE_CONTENT_CHANGED`, component `image`, with exact context `reason=readFingerprint`. A different
second pass while opening uses `reason=openSnapshot`; a different miss snapshot after a successful
read fingerprint uses `reason=operationSnapshot`. A published source clears all entries before any
length, header, or digest read-time mismatch is thrown and increments invalidations once. An opening
mismatch simply fails/cleans the opening transaction because no source or cache exists yet. The source
never adopts changed bytes; an exact restoration may be read again, while a new version requires
reopen. World-file changes do not invalidate because G6-002 already owns immutable coefficient
snapshots.

`IMAGE_FILE_LENGTH_MISMATCH` uses its existing numeric captured/actual context and exact stage reason
`openSnapshot`, `readFingerprint`, or `operationSnapshot`. An early EOF reports the actual bytes read;
a before/after size mismatch reports the observed channel size. No digest or header byte enters
context.

The open/read checks establish an honest observational filesystem boundary. Pre/post sizes close
ordinary append/truncate races; the digest proves that the bytes observed by the pass equal the
baseline. It does not assert an atomic file snapshot or claim the final size check observed all content
at one instant. Under the local-source contract's non-adversarial assumption—no concurrent same-length
rewrite/ABA intended to race one pass—a success is linearizable at some point within that byte-
observation interval. A miss result is always decoded from its independently verified baseline
snapshot; a hit returns only a baseline-derived retained value. Cryptographic collision, adversarial
same-length/ABA mutation, and a malicious Java security provider are outside the contract;
paths/digests/provider messages never enter reports.

### Concrete source serialization and close

G6-004 strengthens only `ImageRasterSource`: one private monitor serializes each entire read and
close. Immutable metadata, limits, and reports remain independently readable; `isClosed` is safely
published. Concurrent identical reads are bounded because the first lock owner performs a miss and a
later identical request hits. Distinct reads serialize too. Different source instances may run
concurrently because each decoder call creates its own reader.

A read that acquires first may complete before a waiting close. A close that acquires first marks the
source closed, clears cache references, then closes its channel; every waiting/new read checks state
and its token immediately after lock acquisition and fails appropriately. Close is not cancellation
and does not interrupt opaque ImageIO. A close failure leaves state closed/cache empty and follows G4
primary/suppressed `SOURCE_CLOSE_FAILED` rules.

Failed/cancelled/partial/contract-invalid work never promotes, evicts, or admits. On a miss the order
is: decode exact snapshot, decide copy/admission eligibility, create the independent consumer copy if
admitting, construct the complete `RasterRead`, final cancellation checkpoint, perform required
evictions/insertion and counter updates, then return without further fallible work. On a hit the source
looks up without promotion, charges/copies into a fresh consumer buffer, constructs the complete
`RasterRead`, performs the final checkpoint, then remove/reinserts the retained entry and increments
`hits` before returning without further fallible work. Disabled, oversized, or accounting bypass
returns the decoder buffer directly after staging the same complete result/checkpoint/counter commit.
A content-version mismatch is the only failure that intentionally clears existing entries.
Cancellation after the final checkpoint may lose to successful publication under the unchanged G4
arbitration.

### Complete PNG physical/safety profile

`ImageSourceLimits` appends positive defaults `maximumContainerElements = 65,536` and
`maximumInflatedRasterBytes = 67,141,632`. Existing six- and eight-value constructors delegate the new
fields to defaults; one full ten-value constructor appends them, and all accessors/withers/value
operations preserve all ten. Limit equality passes; plus one/overflow uses
`SOURCE_LIMIT_EXCEEDED`, `scope=imageOpen`, `limit=containerElements|inflatedRasterBytes`.

The AWT-free image parser validates one complete PNG physical container and the Level 1
decode-affecting safety profile in order. It does not claim semantic validation of ignored metadata:

- signature and IHDR retain G6-001 rules; IHDR is first/exactly once;
- every chunk has checked bounds/type/CRC and increments the container-element count; all four type
  bytes are ASCII letters and the third reserved-property letter is uppercase;
- PLTE/tRNS presence/order/profile are exact; IDAT is one consecutive run; unknown critical chunks
  are unsupported;
- every other ancillary chunk is deliberately opaque: it may occur anywhere after IHDR and before
  IEND without a singleton/order claim, is bounded/CRC-checked/skipped, and cannot split an IDAT run;
- APNG `acTL`, `fcTL`, or `fdAT` is `IMAGE_PROFILE_UNSUPPORTED`, never silently frame zero;
- zero-length IEND occurs exactly once and ends the physical file; and
- concatenated IDAT payload is streamed through one `Inflater` solely as validation, with fixed
  scratch, no retained pixels, and guaranteed `end()` cleanup.

`PLTE` occurs at most once before `tRNS`/IDAT, has a nonzero length divisible by three and at most 256
entries, is required for indexed color with no more than `2^bitDepth` entries, is forbidden for
grayscale/grayscale-alpha, and may be ignored as a suggested palette for truecolor variants. `tRNS`
occurs at most once after any required palette and before IDAT: its length is exactly two for
grayscale, six for truecolor, and 1 through the palette-entry count for indexed color; it is forbidden
for the two alpha-bearing color types. Its sample values must fit the IHDR bit depth. These chunks are
validated but do not change the already fixed JDK sRGB RGBA decode policy.

The validator computes the exact filtered byte count for noninterlaced data or all seven Adam7 passes
from width/height/bit-depth/color profile, including one leading filter byte for each nonempty row in
each pass. While counting, it requires every row's filter byte to be 0 through 4. It rejects dictionary
requests, zlib data errors, premature end, extra compressed bytes/concatenated zlib members, or
inflated length other than exact, stopping at expected+1. The configured inflated ceiling covers the
maximum 64 MiB accepted sample payload plus bounded filter-row overhead. Cancellation is checked
within 4,096 encoded/inflated bytes and around every stage. Only IDAT is inflated; compressed
text/profile ancillary chunks are never interpreted/decompressed.

### Complete JPEG physical/safety profile

The AWT-free JPEG scanner continues G6-001 parsing through entropy-coded data to exactly one physical
EOI. The finite accepted state machine is:

| Phase | Accepted next marker/data | Transition and project-owned checks |
| --- | --- | --- |
| Start | `SOI` only | Exact `FFD8`, counted once; any later SOI is invalid. |
| Before SOF | APP0–APP15, COM, DQT, DHT, DRI, then one SOF0 or SOF2 | Length-bearing segments are complete; SOF retains G6-001 dimensions/component rules. |
| After SOF/before first scan | APP0–APP15, COM, DQT, DHT, DRI, or SOS | A second SOF is invalid; SOS begins entropy. |
| Entropy | ordinary bytes, exact `FF00` stuffing, RST0–RST7, or a non-stuffed boundary marker | Restart use obeys the current DRI and sequence below; a boundary enters between-scans or terminal EOI handling. |
| Between scans | APP0–APP15, COM, DQT, DHT, DRI, another SOS, or EOI | At least one completed SOS is required; no SOF/SOI or entropy byte is legal here. |
| Terminal | EOI | Exact `FFD9`, counted once, with physical EOF immediately afterward. |

All unlisted markers are rejected. DAC/arithmetic SOFs, DNL, JPG/JPGn extensions, TEM, lossless,
differential, hierarchical, and reserved markers use `IMAGE_PROFILE_UNSUPPORTED` with `format=JPEG`,
`field=marker`, and the stable uppercase marker mnemonic/hex byte. A supported marker in the wrong
phase, a second SOF/SOI, SOS before SOF, or EOI before a completed scan is
`IMAGE_CONTAINER_INVALID/reason=markerOrder`.

APP/COM payloads are bounded and skipped without interpretation. DRI has exact segment length four and
captures its unsigned 16-bit restart interval; zero disables restarts. DQT is parsed as one or more
tables with 8- or 16-bit precision, IDs 0 through 3, and exact 64-value payloads. DHT is parsed as one
or more DC/AC tables with class 0 or 1, IDs 0 through 3, 16 code-length counts, a checked symbol sum at
most 256, and exact payload exhaustion. Tables may repeat/replace and may appear in any non-start,
nonterminal header/between-scan phase listed above; whether a scan has every semantically required
table remains the JDK reader's codec check under the existing opaque-call reservation.

Each SOS has exact length `6 + 2 * componentCount`, uses 1 through the SOF component count unique
declared IDs, and validates table selectors in 0 through 3. For SOF0 it requires `Ss=0`, `Se=63`, and
`Ah=Al=0`. For SOF2 a DC scan requires `Ss=Se=0` and may contain one or more components; an AC scan
requires `1 <= Ss <= Se <= 63` and exactly one component. Each progressive scan is either initial
`Ah=0` or refinement `Ah=Al+1`, and both approximation nibbles are at most 13. It then requires at
least one entropy byte before the next boundary. Ordinary entropy bytes are accepted without Huffman
decoding. Exact `FF00` is the
only stuffed literal; repeated `FF` bytes may fill before a nonzero marker. RST markers are legal only
when the current DRI interval is nonzero, start at RST0 for each scan, and advance modulo eight; exact
MCU spacing remains the codec's job. All other standalone-marker appearances are invalid.

SOI, every length-bearing marker, SOS, every RST, and EOI count against
`maximumContainerElements`; fill bytes and `FF00` do not. Every segment length is at least two and
wholly within the snapshot. The scanner checkpoints within each 4,096 physical bytes, so marker-fill
and entropy-heavy files remain bounded by encoded bytes even where no marker count advances.

This task therefore closes G6-001's explicit trailing/concatenated JPEG non-claim. It does not parse
EXIF/ICC/color semantics or promise polling inside opaque ImageIO. Full scanners use only fixed
at-most-4,096-byte scratch plus the bounded open snapshot and introduce no generic binary-parser API.

Container failure uses `IMAGE_CONTAINER_INVALID`, component `image`, absolute byte offset when known,
and exact context `format` plus one reason:

```text
chunkType | chunkOrder | chunkLength | chunkCrc | palette | filter | missingData | missingEnd |
trailingData | markerOrder | segmentLength | table | scan | entropy | dictionary |
compressedData | decodedLength
```

Reason selection is deterministic:

At physical EOF, required data takes precedence over the end marker: PNG with no IDAT and no IEND, or
JPEG with no SOS and no EOI, is `missingData`; `missingEnd` applies only after required data/scan
exists. All other rows are evaluated at the first failing physical condition.

| Format condition | Reason |
| --- | --- |
| PNG nonletter/reserved-bit chunk type | `chunkType` |
| PNG physical order, duplicate singleton, or nonconsecutive IDAT | `chunkOrder` |
| PNG checked length/bounds/truncation | `chunkLength` |
| PNG CRC mismatch | `chunkCrc` |
| PNG PLTE/tRNS profile failure | `palette` |
| PNG inflated row filter outside 0–4 | `filter` |
| PNG missing IDAT / missing IEND / bytes after IEND | `missingData` / `missingEnd` / `trailingData` |
| PNG zlib dictionary request / `DataFormatException` or extra member / wrong inflated count | `dictionary` / `compressedData` / `decodedLength` |
| JPEG supported marker in the wrong phase | `markerOrder` |
| JPEG segment length/bounds/truncation | `segmentLength` |
| JPEG DQT/DHT/DRI structural payload failure | `table` |
| JPEG SOS field/component failure | `scan` |
| JPEG stuffing/restart/entropy transition failure | `entropy` |
| JPEG missing SOS / missing EOI / bytes after EOI | `missingData` / `missingEnd` / `trailingData` |

Unsupported animation/critical/profile cases remain `IMAGE_PROFILE_UNSUPPORTED`. Decoder failure
after a valid container remains `IMAGE_DECODE_FAILED`. Raw marker/chunk bytes, paths, digests, and
localized errors never enter diagnostics. Cache bypass/eviction emits no source report.

### Verification and simplicity

Cache tests pin every key inclusion/exclusion, exact budgets, successful-access LRU, bypasses,
metrics, duplicate source IDs without sharing, fresh result ownership, hit/miss equality for both
interpolations, opacity/placement exclusion, and disabled-versus-cached render equivalence. Mutation
tests cover length/header/body, open instability, miss snapshot change, clear/restoration/reopen, and
irrelevant sidecar changes. Race tests cover serialized identical/distinct reads, both read/close
orders, close failure, waiting cancellation, hit-copy cancellation, miss/decode/admission cancellation,
and success-only promotion/admission.

PNG tests cover chunk/CRC/order/IEND/trailing/APNG, PLTE/tRNS, exact noninterlaced/Adam7 lengths,
Inflater state, limits, truncation, and cancellation. JPEG tests cover baseline/progressive multi-scan,
stuffing/fill/restart, marker/segment limits, missing/duplicate EOI/SOI, concatenated/trailing data, and
decompression-heavy/corrupt cases. Architecture tests allow only the exact per-source cache and literal
SHA call, keep io-image AWT-free, and reject static/shared/AWT caches, soft refs, executors, discovery,
or public metrics.

Public Javadocs cover the policy variant/accessor semantics, default budgets, retained-cache ownership,
constructor compatibility, new limits, mutation boundary, and thread/lifecycle contract. API/value
tests pin `ImageCachePolicy` equality/hash/toString, empty versus present optional limits, null and
positive/equality/plus-one validation, every options/limits wither preserving unrelated fields, and
the old three-/six-/eight-value constructor defaults. Javadoc/doclint remains part of the module and
quality checks.

The fixed task card remains one vertical capability but implementation is reviewed in three internal
milestones: (A) the two physical/safety validators, new limits/diagnostics, and hostile fixture matrix;
(B) exact open/read snapshots, content versioning, serialized read/close lifecycle, and mutation/race
tests with caching disabled; then (C) policy, LRU/accounting/metrics, fresh-result integration, render
equivalence, and architecture rules. Each milestone runs the narrow image-module tests it introduces,
may be reviewed independently, and may not mark G6-004 complete or unblock G6-005 until all three plus
the final task validation pass together.

The focused image/AWT/architecture/viewer checks, `qualityGate`, and whitespace are complete. No
timing threshold, benchmark, render-regression, performance, native, publication, or corpus lane runs;
G6-005 exercises the hardened/cache path natively and G7 supplies performance evidence.

One source-owned canonical result cache, one policy, one version, and two finite format validators are
the complete hardening slice. There is no refresh API, public version, cache SPI, process cache,
metadata tree, writer, or concurrency framework. If implementation effort must be sequenced inside the
task, validators/versioning land before cache admission, but the task is complete only when the one
observable cached hardened source works end to end.

## Native Image raster smoke and G6 closeout (G6-005)

### One existing executable and explicit codec path

G6-005 extends the existing `mundane-map-native-tests` application, its one
`NativeSmokeMain.runSmoke()` assertion path, and its existing final
`mundane-map native smoke: OK` sentinel. It adds no native module, executable, JUnit image, scenario
registry, codec option, or CI job. The support module adds an explicit runtime dependency on
`mundane-map-io-image` and retains the G5 shapefile dependency. Its raster scenario constructs exactly
one `AwtRasterDecoders.level1()` registry and requires declaration order `PNG`, then `JPEG`; every open
receives that registry directly.

The application therefore selects the Level 1 decoder explicitly even though the confined AWT
adapter still performs G6-001's narrow standard-JDK `ImageReaderSpi` snapshot. No application
`ImageIO` call, provider class name, service descriptor, `scanForPlugins`, reflection configuration,
internal `com.sun.*` reference, tracing agent, metadata repository, or fallback is introduced. The
existing `metadataRepository.enabled=false`, `fallback=false`, and `--no-fallback` settings remain
exact. If the Java 21 GraalVM baseline cannot reach the standard JDK PNG/JPEG readers under these
constraints, the implementation task is Blocked rather than widened to a prohibited mechanism.
Any necessary compatibility correction must preserve the approved JVM semantics, remain inside the
owning G6 production module, add a focused JVM regression, and introduce no new public API or broad
initialization/reachability flag.

The top-level success path stays compile-time and sequential: run the approved G2 symbol scenario,
run the approved G5 shapefile scenario, then call one package-private
`NativeRasterSmokeScenario.run(...)`. There is no name-to-scenario map or reflective dispatch. I/O and
workspace/open/direct-request work runs off the EDT. `MapView` mutation, owned binding, offscreen
paint, and close marshal synchronously through the existing G1 EDT bridge; under the approved G4
architecture, paint performs its tiny synchronous source fingerprint/decode/read on that EDT. Thread
tests pin both sides of this boundary rather than claiming all source I/O is off-EDT. The Swing EDT
remains the only helper thread.

### Five fixed raster resources

The native support module adds exactly these root-BSD-3-Clause runtime resources:

```text
/io/github/mundanej/map/nativeimage/raster/png-affine-smoke.png
/io/github/mundanej/map/nativeimage/raster/png-affine-smoke.pgw
/io/github/mundanej/map/nativeimage/raster/jpeg-affine-smoke.jpg
/io/github/mundanej/map/nativeimage/raster/jpeg-affine-smoke.jgw
/io/github/mundanej/map/nativeimage/raster/malformed-idat-crc.png
```

Adjacent test-only provenance records pin the repository-authored generation program/tool version,
dimensions/profile, exact length, lowercase SHA-256, semantic pixels, and BSD disposition, but are not
main/native resources. The implementation records the reviewed length/SHA in package-private literal
resource constants and tests them against the bytes; it does not regenerate images during a build or
native run.

The PNG is a deterministic 4 by 4 RGBA image with constant 2-by-2 quadrants in row order:

```text
top-left     (220, 40, 40,255)    top-right    ( 30,180, 80,255)
bottom-left  ( 40, 90,220,255)    bottom-right (240,190, 30,128)
```

Its `.pgw` is the exact 21 ASCII bytes `20\n5\n4\n-18\n1000\n2000\n`. The pixel-center affine
coefficients are `A=20,D=5,B=4,E=-18,C=1000,F=2000`; the 4-by-4 outer-corner envelope is exactly
`[988,1934.5,1084,2026.5]`. Point `(990,1950)` is inside that envelope but outside the raster
parallelogram and is the fixed background probe.

The JPEG is a checked-in deterministic 16 by 12 baseline RGB image with constant 8-by-6 source
quadrants centered on target colors `(200,50,50)`, `(50,180,70)`, `(50,80,200)`, and `(220,190,40)`.
The checked bytes, not repeatability of a platform JPEG writer, are authoritative; source-cell probes
away from block boundaries allow 20 per channel. Its `.jgw` is exact ASCII
`12\n2\n-3\n-10\n3000\n1000\n`, giving `A=12,D=2,B=-3,E=-10,C=3000,F=1000` and outer envelope
`[2959.5,884,3187.5,1036]`.

The malformed PNG is a 70-byte, otherwise valid 1-by-1 RGBA structure whose sole IDAT CRC has exactly
one flipped bit. The CRC begins at absolute byte offset 54. Opening must terminate before source
publication with `IMAGE_CONTAINER_INVALID`, component `image`, byte offset 54, and exact context
`format=PNG`, `reason=chunkCrc`. This deliberately reaches G6-004 container hardening rather than the
earlier signature/header probe. It is one diagnostic fixture, not a native hostile corpus.

G2-007's single Java 21 `resource-config.json` gains five individually `\Q...\E`-quoted includes,
without lookup-leading slashes, under the unchanged `NativeSmokeMain` reachability condition. After
G6 it contains exactly 12 entries: the G2 raw icon, six G5 shapefile resources, and these five raster
resources. A JVM test compares normalized exact JSON and the complete processed main-resource tree.
Wildcards, directories, provenance/license copies, bundles, services, provider metadata, and any
unlisted resource fail the test.

### One private fixed-workspace engine

G6 avoids a second implementation of G5's resource-to-`Path` safety rules. Native-support internals
extract the existing mechanics into one package-private `NativeFixtureWorkspace` engine with two
compile-time-only factories: `openShapefile()` materializes only the six G5 resources and exposes
immutable `NativeShapefilePaths`; `openRaster()` materializes only the five G6 resources and exposes
immutable `NativeRasterPaths`. Each instance owns a separate temporary directory. Callers cannot
supply a resource name, filename, directory, expected hash, inventory, or algorithm. The G2 raw icon
retains its direct bounded stream loader because no public API requires a path for it.

`runSmoke()` owns the sequence exactly:

```text
run approved G2 symbol scenario
try (shapefileWorkspace = NativeFixtureWorkspace.openShapefile())
  run approved G5 scenario borrowing shapefileWorkspace.shapefilePaths()
try (rasterWorkspace = NativeFixtureWorkspace.openRaster())
  run G6 scenario borrowing rasterWorkspace.rasterPaths()
print success sentinel
```

Each scenario must close every cursor/source/view before its borrowed workspace scope ends. A symbol
failure creates no workspace. A shapefile open/scenario/close failure completes that workspace's
cleanup and prevents raster workspace creation. A raster failure cleans only its raster workspace.
Try-with-resources supplies the exact ordering: scenario failure stays primary and workspace-close
failure is suppressed; a close failure after an otherwise successful scenario is primary. Separate
inventory instances preserve every G5 resource-failure test and prevent a missing G6 file from
changing whether the approved G5 scenario can start.

Both factories preserve G5's exact mechanics: literal `Class.getResourceAsStream`, at most
`expectedLength + 1` bytes, exact length and literal SHA-256, flat unique local names, `CREATE_NEW`,
ownership recorded before first write, reverse known-path cleanup, no list/walk/enumeration, original
failure primary with cleanup suppressed, and idempotent close. The only digest call remains literal
`MessageDigest.getInstance("SHA-256")`; no provider/algorithm input or public loader is added. Refactoring
must leave the G5 scenario and its resource-failure tests semantically unchanged. After every source
and owned view closes, successful deletion of each instance's files/directory is part of the shared
smoke.

### Shared PNG/JPEG semantic scenario

The scenario creates one `CrsRegistry.level1()`, resolves exact key `EPSG:3857` to `mercator`, and
constructs source metadata exactly as
`CrsMetadata.recognized(mercator, Optional.of("EPSG:3857"), Optional.empty())`; no identifier parsing or
CRS guess occurs. Every render view is
`MapView(crsRegistry, mercator, mercator, SymbolRendererRegistry.builtIn())`, so both map/display
directions use the registered identity and the source CRS matches the raster-attachment boundary.

The PNG path opens the fixed file through `RasterImages` with that metadata in
`ImagePlacement.worldFile(...)`, default bounded limits/cache, and the explicit decoder registry. It
asserts the 4-by-4 metadata, exact affine
coefficients/envelope/canonical CRS, and clean opening report. An already-cancelled full-window read
must fail with exact `SOURCE_CANCELLED`, perform no successful cache admission, and leave the source
reusable.

Two identical full-window 4-by-4 to 2-by-2 `BILINEAR` reads then traverse G6-004 fingerprinting and the
default result cache. They return distinct consumer `RgbaPixelBuffer` instances with equal contents,
the exact request/result window and shape, clean reports, and exact output indexes
`(0,0)/(1,0)/(0,1)/(1,1)` equal to the top-left/top-right/bottom-left/bottom-right RGBA values above.
Native code does not reach into package-private cache metrics; focused JVM image tests prove the second
request is a hit, while the shared repeated native call proves that path is reachable and still returns
independent values.

The still-open PNG source transfers to one owned EPSG:3857 raster binding with bilinear presentation
and opacity `0.5`. A fixed 192-by-160 opaque-white offscreen view fits with 12 logical pixels. Public
affine/map-to-screen conversion locates safe quadrant probes; 3-by-3 majority samples allow 20 per
channel around the single expected source-alpha plus layer-opacity `SrcOver` composition. Exact
source-grid/world/opaque-output targets are:

| Source grid center | World point | Expected over-white RGB at opacity 0.5 |
| --- | --- | --- |
| `(0,0)` | `(1000,2000)` | `(238,148,148)` |
| `(3,0)` | `(1060,2015)` | `(143,218,168)` |
| `(0,3)` | `(1012,1946)` | `(148,173,238)` |
| `(3,3)` | `(1072,1961)` | `(251,239,199)` |

The 3-by-3 majority probe is centered on each public `mapToScreen(worldPoint)` result. The envelope-
only point `(990,1950)` remains white. Every nonwhite pixel is contained by the transformed envelope
expanded two logical pixels, and the total nonwhite count lies between 100 and 25,000. View close owns
and closes the source; a lifecycle assertion confirms a later read is rejected.

The JPEG path separately opens through its world file and the same registry, asserts the 16-by-12
metadata, exact transform/envelope/CRS, and clean report, then performs two identical full-window
16-by-12 to 4-by-3 `NEAREST` reads. Results are distinct but equal, have the exact shape, and their four
output indexes `(0,0)/(3,0)/(0,2)/(3,2)` are within 20 per channel of the declared
top-left/top-right/bottom-left/bottom-right targets. A separate 192-by-144 white view uses nearest
interpolation, opacity `1.0`, and 12-pixel fit padding. Its four 3-by-3 majority probes use:

| Source grid center | World point | Expected RGB |
| --- | --- | --- |
| `(2,2)` | `(3018,984)` | `(200,50,50)` |
| `(14,2)` | `(3162,1008)` | `(50,180,70)` |
| `(2,10)` | `(2994,904)` | `(50,80,200)` |
| `(14,10)` | `(3138,928)` | `(220,190,40)` |

Each probe centers on `mapToScreen` and allows 20 per channel. Fixed envelope-only point
`(2965,1020)`, envelope-plus-two containment, and a 100-to-25,000 nonwhite count prove decode,
placement, fit, and drawing without whole-image identity. Owned close is exact.

Finally, the scenario opens the malformed PNG unplaced through the same explicit registry and asserts
the exact terminal diagnostic above, no source publication, and no path/provider/raw-byte text. All
previously returned immutable metadata, reads, and reports remain readable after source/view/workspace
close. A failure throws one bounded invariant token from
`raster-registry`, `raster-png-metadata`, `raster-png-bilinear`, `raster-png-opacity`,
`raster-png-affine`, `raster-jpeg-decode`, `raster-jpeg-affine`, `raster-cache-ownership`,
`raster-cancel`, `raster-diagnostic`, or `raster-cleanup`; any failure prevents the final sentinel.

`NativeRasterSmokeScenario` and its assertion helper are shared unchanged by JVM and native execution.
JVM negative controls alter one expected coefficient, sample, opacity, diagnostic, length, or hash and
must fail the matching invariant. JVM tests also pin decoder order, off-EDT/EDT transfer, cancellation
reuse, distinct repeated results, exact resource inventory/metadata, primary/suppressed cleanup, and
post-close file deletion. Architecture tests scan native-support plus image production output and
preserve every Level 1 prohibition.

### Strict native lane and checkpoint

The implementation validation is intentionally separate: focused native-support/architecture JVM
checks, then the actual `nativeSmoke`, then `qualityGate` and whitespace. `nativeSmoke` must find a
Java 21 GraalVM `native-image`, build with no fallback, execute the same assertions, and print the
sentinel only after all G2/G5/G6 scenarios pass. Missing tooling, a build-only success, a JVM fallback,
or a skipped runtime is not evidence and prevents this HITL task from becoming Complete. Corpus,
render-regression, performance, publication, and consumer lanes do not run here.

The named checkpoint is **G6 native raster approval**. A maintainer records reviewed commit,
reviewer/date, Linux OS/architecture, GraalVM distribution, full Java 21 and `native-image` versions,
exact focused/native commands and final sentinel, registry order, PNG/JPEG decode/affine/cache-path
summary, the malformed diagnostic, fixture provenance/length/SHA/license disposition, cleanup result,
and pass/fail or CI URL. Other-platform evidence may be recorded as supplemental, but this task makes
no Windows, macOS, all-Linux, or cross-platform Native Image claim.

### G6 holistic simplicity closeout

The completed G6 boundary remains one small raster format slice:

- `mundane-map-io-image` is JDK-only/AWT-free and exposes only its static facade plus immutable
  open/placement/limit/cache-policy values returning format-neutral `RasterSource`; parsers, channels,
  versions, cache keys, and validators remain private;
- API owns the unavoidable toolkit-neutral decoder format/context/registry, affine/grid placement,
  request/interpolation, and packed RGBA contracts; core owns one affine-window plan and one exact
  resampling implementation; AWT alone owns the standard JDK ImageIO bridge, Java2D conversion,
  affine drawing, and presentation options;
- one explicit application registry selects two fixed codecs, one exact source snapshot/version and
  serialized lifecycle own one bounded decoded/resampled RGBA cache, and G7 measures before adding any
  higher render cache;
- world files locate pixel centers but never invent CRS, and the affine envelope is never substituted
  for the actual parallelogram; reprojection/warping and GeoTIFF remain Level 2; and
- normal fixtures, hostile tests, render regression, viewer, and native resources are separate
  consumers of one production path, not alternate decoders or parsers.

No external dependency, empty module, codec plug-in system, general image model, metadata tree, warp
framework, worker, discovery, native parser, or performance claim survives the gate. The decoder
boundary is retained only because `java.desktop` cannot enter the AWT-free format module; every other
abstraction owns at least one already working semantic/lifecycle boundary. Removing another piece
would lose bounded decoding, affine placement, deterministic sampling/presentation, hardening/cache
ownership, or native parity. G6 is therefore simple enough and no simpler while remaining ergonomic:
consumers supply one explicit registry and receive the same format-neutral raster source used by every
other layer.

## Approved Level 2 embedded-byte extension

G10-004 and G10-006 establish GeoPackage, MBTiles, and HTTP XYZ as demonstrated consumers of encoded
PNG/JPEG byte values. Shared implementation card G10-039 adds `RasterImages.decode(byte[], SourceIdentity,
EncodedRasterDecodeOptions, EncodedRasterDecoderRegistry, CancellationToken)`, returning one detached
native-size `RgbaPixelBuffer`. The helper defensively snapshots bytes and reuses the complete G6
container validation, limits, accounting, cancellation, and explicit decoder path. An optional
expected format supports MBTiles metadata; absent means signature-selected PNG/JPEG. Optional expected
width and height are jointly present or absent. A present size is checked after structural header
parsing and before complete-decode allocation; mismatch uses `IMAGE_DIMENSIONS_MISMATCH` with numeric
expected/actual dimensions. It has no suffix, placement, cache, temporary `RasterSource`, AWT type, or
discovery. This is a Level 2 additive consumer surface and does not alter the completed Level 1 file-
source or native claim. GeoPackage, MBTiles, and HTTP tasks depend on that working helper rather than
racing to add it independently. For encoded length `E` and native pixel count `P`, the helper's exact
primitive-payload charge is `2 * E + 16 * P`; fixed validation/container reference slots follow the
existing G4 table. Nested adapters may therefore reserve the helper without guessing at defensive-copy
or opaque-decoder capacity. Expected-format mismatch uses the additive
`IMAGE_EXPECTED_FORMAT_MISMATCH` code with exact `expectedFormat` and `signature` context rather than
fabricating the Level 1 file code's `extension`. Checked failures are limited to `SOURCE_CANCELLED`,
`SOURCE_LIMIT_EXCEEDED/scope=imageDecode`, the two byte-helper-specific expectation codes, and the
existing closed header/container/profile/decoder/decode/I/O image codes. File, world-file,
interpolation, cache, and close outcomes are impossible for this detached byte operation; unexpected
token/decoder runtime failures remain unchecked.
