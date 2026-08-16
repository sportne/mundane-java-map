# Image adapter capability intent

`mundane-map-io-image` is a bounded, toolkit-neutral decoder and raster-source adapter. Its intended
format role is deliberately narrower than a general image library: it decodes common interchange
PNG and JPEG files into the project's immutable raster model, applies explicit world-file placement,
and participates in the existing source, cache, cancellation, and diagnostic contracts.

Image encoding, transcoding, in-place metadata editing, and general-purpose image manipulation are
not goals for this module. Those exclusions are product decisions, not unimplemented promises.

The README describes the released capability. The target columns below describe the approved G19
completion boundary and become release claims only as their task cards close.

## Format policy

| Format or family | Module role | Approved target | Deliberate exclusions |
| --- | --- | --- | --- |
| PNG | Decode | The static/default image defined by W3C PNG Third Edition, across every valid static color-type, bit-depth, filter, and Adam7 combination | Animation playback, animation timing/compositing API, encoding, and editing |
| APNG carried in PNG | Decode static/default image only | Validate the PNG datastream and bounded ancillary chunks, ignore animation chunks, and expose the same static/default image a non-animation-capable conforming decoder displays | Animated frame delivery, playback, and APNG encoding |
| JPEG | Decode | Common 8-bit Huffman DCT interchange: baseline, extended sequential, and progressive processes; common sampling, restart, color, profile, and orientation conventions | Arithmetic, lossless, differential/hierarchical, non-8-bit precision, JPEG-LS, JPEG 2000, JPEG XL, encoding, and editing |
| World files | Read placement | Existing bounded axis-aligned and six-coefficient affine placement | CRS inference and world-file writing |

## Adjacent WebP adapter decision

WebP is not added to this toolkit-neutral module. The approved WebP path is a separately published,
optional `mundane-map-awt-image-webp-twelvemonkeys` adapter pinned to TwelveMonkeys ImageIO 3.14.0.
That adapter decodes static lossy VP8, lossless VP8L, and alpha-bearing WebP into the same neutral
immutable raster values, but its implementation is explicitly Java2D/ImageIO-based and is therefore
not part of the JDK-only format graph or the Native Image support claim.

The adapter constructs its reader explicitly rather than using ImageIO service discovery. It does not
install a global provider, scan the classpath, expose `BufferedImage`, or leak TwelveMonkeys types
through a public project contract. Animated WebP is rejected with a stable diagnostic; WebP writing,
metadata editing, transcoding, and a custom VP8/VP8L codec are deliberate exclusions. G19-227 through
G19-229 own this optional capability.

## PNG standards matrix

Normative baseline: [W3C PNG Third Edition](https://www.w3.org/TR/png-3/), 24 June 2025.

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Samples | Static images through 8 bits per sample | Every valid static color-type/bit-depth combination, including packed 1/2/4-bit samples and 16-bit samples | G19-040 |
| Scan organization | Non-interlaced subset | Non-interlaced and all seven Adam7 passes with exact filter reconstruction | G19-040 |
| Critical structure | Signature/IHDR/IDAT/IEND validation with explicit limits | Complete applicable chunk order, multiplicity, compression, filter, interlace, palette, and transparency rules | G19-040, G19-041 |
| Rendered color | Basic decoded samples | Normative `PLTE`, `tRNS`, `cHRM`, `gAMA`, `iCCP`, `sBIT`, `sRGB`, `cICP`, `mDCV`, and `cLLI` validation and precedence, with a documented output color/precision policy | G19-041 |
| Ancillary chunks | Bounded validation subset | Bounded known-chunk validation and conforming unknown-ancillary handling; no unbounded metadata retention | G19-041 |
| Animation chunks | Rejected | Treat `acTL`, `fcTL`, and `fdAT` as standardized ancillary animation data, ignore animation, and decode only the static/default PNG image | G19-041 |
| Conformance evidence | Project fixtures | Applicable PNG Third Edition decoder requirements, PngSuite, hostile corpora, and independent-decoder comparisons | G19-041, G19-044 |

“Static-only” describes the delivered image model. It does not redefine APNG as invalid PNG and it
does not promise animation-capable decoder conformance.

## JPEG standards matrix

Normative coding baseline: ITU-T T.81 / ISO/IEC 10918-1. Interchange conventions are pinned by the
implementation cards to JFIF (ITU-T T.871), Exif, Adobe APP14, and ICC profile specifications used by
the declared profile.

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Coding processes | 8-bit baseline and progressive subset | Common 8-bit Huffman baseline, extended sequential, and progressive DCT streams, including multi-scan and restart behavior | G19-042 |
| Sampling/tables | Bounded common fixtures | All conforming common-interchange component sampling and table-selection combinations within explicit coefficient/work limits | G19-042 |
| Color models | Grayscale and RGB-oriented subset | Grayscale, YCbCr, RGB, CMYK, and YCCK with deterministic JFIF/Adobe/component interpretation | G19-043 |
| Profiles/metadata | Structurally bounded markers; color/orientation mostly ignored | Bounded ICC assembly/application, Exif orientation, JFIF density, Adobe APP14 transform, and documented precedence | G19-043 |
| Unsupported coding families | Stable rejection | Continue stable rejection of arithmetic, lossless, differential/hierarchical, non-8-bit, JPEG-LS, JPEG 2000, and JPEG XL data | G19-042, G19-044 |
| Conformance evidence | Project fixtures | Independent common-interchange corpus, malformed-marker corpus, cross-decoder comparisons, and bounded-work evidence | G19-042 through G19-044 |

## Cross-format completion contract

G19-044 closes the core decoder surface only after both format matrices have stable public capability
reporting, bounded resource behavior, deterministic color/output rules, corpus provenance, and
independent interoperability evidence. The separate WebP adapter has its own G19-229 closeout and
does not broaden G19-044's PNG/JPEG or Native Image claims. Adding an encoder or another image family
requires a new explicit design decision and task card.
