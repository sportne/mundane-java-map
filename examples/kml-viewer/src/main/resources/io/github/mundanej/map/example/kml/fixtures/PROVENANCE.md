# KML fixture provenance

`simplekml-static-profile.kml` contains synthetic, non-sensitive coordinates authored for this
project. It was serialized as KML 2.2 with
[`simplekml` 1.3.6](https://github.com/eisoldt/simplekml/tree/v1.3.6), whose generator is licensed
LGPL-3.0-or-later. The generated fixture is distributed under this repository's BSD-3-Clause
license; it contains no upstream sample data.

Generation used a new `simplekml.Kml` value with one point, one two-position line, and one polygon
with a hole. Names, description, coordinates, and document `open` were assigned as the literal
synthetic values retained in the file. `kml.kml(format=True)` produced the KML text. The deterministic
post-processing expression `re.sub(r' id="[0-9]+"', '', text)` removed only simplekml's generated
object identifiers because the approved static profile retains IDs only on Placemarks supplied by
the source author. The result was written as UTF-8 with LF line endings and no further normalization.

The pinned PyPI 1.3.6 source distribution used for generation has SHA-256
`cda687be2754395fcab664e908ebf589facd41e8436d233d2be37a69efb1c536`.

| Fixture | Bytes | SHA-256 |
| --- | ---: | --- |
| `simplekml-static-profile.kml` | 1330 | `32fc9de3e4cc1a09254f01a3b922a406b2237f79c3c6dc403ede3b5c7f37e2f2` |

The maintainer's advance approval for every HITL task in this execution sequence covers the
**G10 KML fixture provenance approval** checkpoint for this synthetic, independently serialized
fixture and its recorded LGPL-3.0-or-later tool/BSD-3-Clause output disposition.
