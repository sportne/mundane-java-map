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
