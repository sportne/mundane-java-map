# GeoTIFF interoperability corpus provenance

These four synthetic files were generated specifically for mundane-java-map and contain no copied
geographic or personal data. Their deterministic pixel/sample formulas and resulting files are
project-authored material distributed under the repository's Apache License 2.0 (`LICENSE` at the
repository root).

The complete TIFF containers, IFDs, GeoKeys, segment tables, and encoded segments were written by
the external GDAL 3.13.0 GTiff driver. The recipe pins the official Linux/amd64 image manifest and
configuration digests and executes the writer with networking disabled. No project parser or TIFF
normalizer participates in generation.

The image-pinned CPython 3.14.4 interpreter only orchestrates GDAL calls and writes deterministic
sample buffers; it does not author or normalize the TIFF structure. GDAL's CRS construction uses its
internal PROJ 9.8.1 runtime plus the linked PROJ/proj-data 9.7.1 packages. `toolchain.tsv` pins these
components and the complete writer/container/codec stack: GDAL, libgeotiff, libtiff, zlib, and
libdeflate. The referenced complete installed-package license records are checked in under
`licenses/`, including GDAL's MIT terms and the Debian/Ubuntu copyright records for Python, PROJ,
each codec, and each container component.

Reproduce into a new empty directory from the repository root:

```bash
modules/mundane-map-io-geotiff/src/test/resources/geotiff-corpus/recipes/gdal-3.13.0-geotiff.sh /tmp/geotiff-corpus-output
sha256sum /tmp/geotiff-corpus-output/*.tif
```

The manifest is the authority for paths, byte lengths, SHA-256 values, toolchain identity, artifact
license, tool-license paths, and supported-profile coverage. Regeneration with another toolchain is
a corpus change requiring new hashes and maintainer review. The normal build never runs the recipe,
starts Docker, accesses a network, or requires GDAL.
