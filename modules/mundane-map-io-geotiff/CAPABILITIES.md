# GeoTIFF adapter capability intent

`mundane-map-io-geotiff` is the project's JDK-only adapter for bounded georeferenced imagery,
multiband raster data, and numeric elevation grids. Its approved target includes local and guarded
HTTP reading plus builder-driven creation of conventional tiled GeoTIFF and Cloud Optimized GeoTIFF
(COG). It is not intended to become a general TIFF document/image-publishing library.

Writer output is deterministic and geospatially explicit. The builder validates an already selected
grid, sample model, CRS, placement, no-data/mask policy, and overview plan; it never silently chooses
a CRS, reprojects, resamples, color-converts, quantizes, or derives vertical meaning. Callers may use
the core warping pipeline before encoding.

The root README describes released behavior. Target rows below become release claims only when the
corresponding G19 cards close.

## Standards and conformance boundary

| Standard/profile | Approved role | Conformance intent | Deliberate exclusions |
| --- | --- | --- | --- |
| TIFF Revision 6.0 | Underlying bounded image container for GeoTIFF | Read the declared common geospatial tag/sample/codec surface; write deterministic tiled subsets needed by GeoTIFF/COG | General multipage documents, facsimile workflows, annotations, desktop-print metadata, and arbitrary private tags |
| BigTIFF | 64-bit-offset container extension | Read and write the pinned public BigTIFF profile with the same geospatial semantics and limits | Treating BigTIFF as an OGC GeoTIFF 1.1 TIFF-conformance claim where that standard specifically requires TIFF 6.0 |
| [OGC GeoTIFF 1.1 / OGC 19-008r4](https://docs.ogc.org/is/19-008r4/19-008r4.html) | Normative georeferencing/CRS baseline | Implement every applicable declared reader/writer requirements class and test; state classic-TIFF conformance separately from BigTIFF interoperability | Runtime authority lookup, silent CRS approximation, and claiming normative 3D/compound support where GeoTIFF 1.1 only gives informative recommendations |
| [OGC COG 1.0 / OGC 21-026](https://docs.ogc.org/is/21-026/21-026.html) | Optimized layout and range-access profile | Read/validate and write GeoTIFF Tiles, Overviews, Keys, and Optimized GeoTIFF classes; consume HTTP Range through the guarded transport | Operating an HTTP range server, generic URL opening, and claiming server-side HTTP Range conformance |

## TIFF container, storage, and codec matrix

| Area | Released profile | Approved completion target | Writer target | Card |
| --- | --- | --- | --- | --- |
| Container | Classic TIFF, both byte orders, one sorted IFD | Classic TIFF and pinned BigTIFF, bounded IFD/SubIFD graphs, explicit primary dataset and associated overview/mask selection | Classic TIFF or BigTIFF chosen explicitly/through checked size planning | G19-070 |
| Image organization | Strips or tiles, chunky, north-up orientation | Strips/tiles, chunky/planar, orientations 1–8, reduced-resolution SubIFDs, transparency masks, bounded associated metadata | Tiled chunky/planar layouts needed by the declared sample profile; canonical orientation 1 | G19-070, G19-071 |
| Compression | None, Deflate, PackBits | None, PackBits, LZW, Deflate/Adobe Deflate, and common TIFF JPEG decoding; stable rejection of old-style JPEG and undeclared vendor codecs | None, LZW, or Deflate; no JPEG encoder | G19-071, G19-078 |
| Predictors | Predictor 1 only | Horizontal predictor and floating-point predictor where valid for the sample/codec combination | Canonical none/horizontal/floating selection for LZW/Deflate | G19-071, G19-078 |
| Random access | Whole local file snapshot, then intersecting block decode | File/channel windows, selected IFD/overview, block cache, cancellation, mutation checks, and bounded reads without whole-file retention | N/A | G19-075 |
| HTTP/COG | Unsupported | Validator-consistent guarded byte ranges, coalesced metadata/block planning, and structural COG validation | Optimized IFD/tile/overview/mask ordering and range-efficient layout | G19-076, G19-079 |

CCITT fax compression, old-style JPEG compression, JBIG, and vendor codecs such as LERC, WebP, and
Zstandard are outside the initial common geospatial profile. Adding one requires a separately pinned
public specification, a bounded JDK implementation or explicit optional adapter, redistributable
interoperability evidence, and an updated matrix; it is not implied by generic TIFF tag acceptance.

## Sample, color, band, and elevation matrix

| Surface | Released profile | Approved completion target | Writer target | Card |
| --- | --- | --- | --- | --- |
| Display imagery | 8-bit WhiteIsZero/BlackIsZero/RGB with optional alpha | Packed bilevel/2/4-bit and 8/16-bit grayscale/RGB/palette; associated/unassociated alpha; common YCbCr and CMYK decode; explicit ICC/chromaticity precedence and output conversion | Lossless grayscale, RGB/RGBA, and palette/indexed output when exactly representable | G19-072 |
| Bands/sample formats | Chunky 8-bit display samples; one signed/float elevation band | Bounded unsigned/signed integer and IEEE floating profiles, homogeneous/declared per-band widths, chunky/planar layouts, raw band selection and lossless numeric access | Explicit homogeneous supported band plans; reject unrepresentable heterogeneous/lossy requests | G19-072, G19-077 |
| Masks/no-data | Display alpha and one GDAL no-data scalar for elevation | ExtraSamples alpha, transparency-mask IFD, bounded GDAL no-data convention, NaN, and explicit precedence among alpha/mask/no-data | Explicit alpha/mask/no-data plan with no inferred sentinel | G19-072, G19-074 |
| Elevation | One signed 16/32-bit or float32/64 band with caller-provided unit | Raw scale/offset/no-data semantics, vertical metadata retention, exact unit/datum policy, and warped/windowed elevation output | Signed/float elevation bands with caller-declared units, no-data, scale/offset, and supported vertical metadata | G19-074 |
| Orientation/resolution | Basic north-up display; no overview choice | Exact orientation normalization and deterministic overview selection by request resolution/work | Writer stores canonical orientation and caller-provided or explicitly generated overview pyramid | G19-070, G19-075, G19-079 |

Raw numeric access is distinct from display conversion. Unsupported color/profile conversions must
not destroy the ability to retrieve supported raw bands, and display snapshots must document their
precision, transfer-function, alpha, rounding, and color-space policy.

## GeoTIFF georeferencing and CRS matrix

| Area | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Raster-to-model transform | ModelPixelScale + ModelTiepoint or ModelTransformation subset | Complete mutually exclusive tag rules, multiple tiepoints where applicable, PixelIsArea/PixelIsPoint, affine placement, and exact control-point validation | G19-073 |
| GeoKey directory | Narrow sorted directory and ASCII citations | Complete GeoTIFF 1.1 key-directory/header/location/count/value rules, DOUBLE/ASCII parameters, sorting, duplicates, private/unknown retention policy, and backward compatibility | G19-073 |
| Horizontal CRS | EPSG:4326 and EPSG:3857 | Declared GeoTIFF geographic/projected/geocentric CRS, units, datums, ellipsoids, prime meridians, coordinate operations, and user-defined definitions representable by the core CRS model | G19-073 |
| Vertical/3D | Caller supplies elevation unit; vertical keys rejected | Preserve and interpret supported vertical keys and the documented Annex D interoperability recommendations without presenting them as normative GeoTIFF 1.1 conformance | G19-074 |
| Unsupported operations | Terminal unsupported profile | Preserve well-formed metadata and expose stable operation-unavailable status; never approximate a datum/projection silently | G19-073, G19-074 |

## Builder and publication policy

- One immutable builder writes one geospatial raster dataset plus its associated mask and overview
  IFDs. It does not author unrelated pages/subdatasets in the same TIFF.
- Required inputs are the raster/band source, exact sample model, CRS or explicit user-defined CRS,
  PixelIsArea/PixelIsPoint placement, no-data/mask meaning, and any vertical semantics. Defaults may
  choose byte order, tile dimensions, lossless Deflate, predictor, software identifier, classic versus
  BigTIFF after checked sizing, and a deterministic overview policy.
- Conventional mode writes a valid tiled GeoTIFF without promising optimized byte-range ordering.
  COG mode additionally satisfies the declared OGC COG 1.0 file conformance classes.
- Encoding uses a private sibling staging file, flushes, reopens through the production reader,
  compares the declared dataset, and commits under an explicit create/replace policy. Failure and
  cancellation preserve an existing destination and remove staging.
- JPEG is decode-only because the project's common JPEG capability intentionally has no encoder.
  Lossless GeoTIFF/COG output remains fully supported through None, LZW, and Deflate.
- G19-079 closes the broader profile only after independent tools read emitted classic/BigTIFF and
  COG outputs, the project reads independently emitted fixtures, applicable OGC tests pass, and this
  matrix agrees with Javadocs and public support wording.
