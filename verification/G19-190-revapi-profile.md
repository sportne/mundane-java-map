# G19-190 Revapi analysis profile

The architecture test suite pins `org.revapi:revapi:0.15.1` and
`org.revapi:revapi-java:0.28.4` as build/test-only inputs. They are absent from every production,
published, and Native Image runtime graph. The mechanically enforced coordinates, filenames, and
SHA-256 values are in `G19-190-revapi-artifacts.tsv`; any graph or byte change fails
`verifyRevapiProfile` before classifications can be accepted.

## Reviewed license inventory

| Components | License |
| --- | --- |
| Revapi core, Java analyzer/SPI, Classif and Classif DSL | Apache-2.0 |
| Jackson annotations/core/databind | Apache-2.0 |
| NetworkNT JSON Schema Validator | Apache-2.0 |
| Apache Commons Lang and Apache Log4j API/core | Apache-2.0 |
| JBoss DMR | Apache-2.0 or LGPL-2.1-only (dual licensed) |
| ANTLR runtime | BSD-3-Clause |
| Joni and JCodings | MIT |
| SLF4J API | MIT |

The older transitive libraries are isolated analysis implementation details. No logging provider,
schema parser, JSON type, or Revapi type crosses into project production code or public API. A tool
upgrade requires an explicitly approved regenerated checksum profile, renewed license review, the
complete synthetic old/new JAR classification suite, offline repository verification, and this
document's update.

## Configuration policy

Revapi is the released-JAR binary/source difference engine. Before the first public release, the
repository instead uses reviewed deterministic public/protected `javap` signatures supplemented by
record, sealed, enum, annotation, generic, exception, and parameter shape. Those manifests state
`PROVISIONAL` and `UNPUBLISHED`; they cannot contain invented Maven provenance. Once an artifact is
published, its manifest must atomically change to `RELEASE` with the exact immutable coordinate and
SHA-256 values for its JAR, POM, and Gradle module metadata.

Project policy does not accept Revapi's normal leading-zero relaxation. A pre-1.0 patch must remain
compatible; a reviewed breaking change requires the next `0.MINOR.0`. At and after 1.0, breaking,
compatible-addition, and implementation-only changes require major, minor, and patch releases.
Strict project rules treat enum/sealed exhaustiveness, record shape, overload ambiguity, generics,
checked exceptions, nullness/annotations/constants, and leaked public types as governed even when
binary linkage alone would not reject a change.

The empty `api-compatibility-exceptions.tsv` is intentional. Future rows must name one artifact,
one difference code, one exact element, an expiry version, rationale, migration/replacement,
release-note link, and maintainer approval. Wildcards and blanket ignores are invalid.
