# GPX fixture provenance

`gpxpy-waypoint-track.gpx` contains synthetic, non-sensitive coordinates authored for this project.
It was serialized as GPX 1.1 with
[`gpxpy` 1.6.2](https://github.com/tkrajina/gpxpy/tree/1.6.2), whose serializer is licensed
Apache-2.0. The generated fixture is distributed under this repository's BSD-3-Clause license; it
contains no upstream sample data.

Generation used a new `gpxpy.gpx.GPX` value with one waypoint and one named track containing a
three-point segment and a two-point dateline-crossing segment. Coordinates, elevations, timestamps,
names, description, track number, and type were assigned as the literal synthetic values retained in
the file, then `gpx.to_xml(version="1.1")` produced the checked-in bytes.

| Fixture | SHA-256 |
| --- | --- |
| `gpxpy-waypoint-track.gpx` | `9ba7ee5166f37d2ce817b4eff012c614f86d143829dc5df3719baa34c5b21a86` |

The maintainer's advance approval for all HITL tasks in this execution sequence covers the
**G10 GPX fixture provenance approval** checkpoint for this synthetic, independently serialized
fixture.
