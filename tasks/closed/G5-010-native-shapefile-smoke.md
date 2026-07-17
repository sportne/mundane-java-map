# G5-010 — Native shapefile smoke

Status: Complete
Depends on: G5-009
Gate: G5
Type: HITL

## Goal

Read, query, and render a representative shapefile plus one stable malformed-record diagnostic in
the actual GraalVM Native Image smoke application.

## Context

G5-009 completes JVM and corpus behavior. Native compatibility is an architectural requirement and
must exercise the real parser/source/rendering path, not a class-loading placeholder.

## Scope

- `modules/mundane-map-native-tests` shapefile fixtures and smoke assertions
- Native-test dependencies and explicit resource declarations
- Native compatibility fixes within `mundane-map-io-shapefile` that do not change its profile
- Focused architecture tests for the exact resources and prohibited native-targeted mechanisms
- G5 holistic design closeout and any consistency corrections that preserve approved behavior

## Out of scope

- Broad corpus execution inside the native executable
- Reflection configuration as a substitute for explicit construction/registration
- Performance claims or native parsing libraries

## Acceptance criteria

- The native executable opens a supported fixture containing representative geometry and attributes,
  executes a bounded feature query, and renders through the offscreen AWT path.
- The smoke validates semantic results such as feature count, envelope/topology, selected attribute,
  and the fixed tolerant shell/hole/multipart/fill/outline render oracle rather than only process
  exit.
- A separate malformed record produces the same stable diagnostic code and record/offset context as
  the JVM path: `SHAPEFILE_RECORD_LENGTH_INVALID` at SHP record 1, byte 104, with the exact declared/
  remaining-byte context fixed by the 108-byte fixture.
- A Windows-1252 attribute and undefined-byte null-substitution diagnostic prove the committed manual
  single-byte table is reachable and identical under Native Image.
- Fixtures/resources are declared explicitly and work without classpath scanning or implicit
  resource discovery.
- One small valid dataset is copied byte-for-byte from the approved G5-009 corpus into an exact native
  resource inventory with matching lengths/SHA-256 and exact `BSD-3-Clause` provenance. The native
  binary includes neither the corpus manifest/license copy nor unrelated datasets; the project root
  license remains the redistribution notice.
- The five source components, selected manifest authority, referenced corpus license, and root
  `LICENSE` are exact Gradle inputs to the copy/JVM checks, while no corpus source-set task or
  completeness test enters the normal gate.
- The resource loader uses literal names, bounded reads, checksum verification, a fixed temporary
  file layout for the Path opener, and known-path reverse deletion without directory walking.
- Source/cursor/file resources close on success and failure.
- Native-targeted code uses no reflection, dynamic proxies, Java serialization, JNI, `Unsafe`,
  internal JDK APIs, or automatic plugin discovery.
- **HITL checkpoint — G5 native shapefile approval:** a maintainer reviews a successful Linux Java 21
  GraalVM `nativeSmoke` run (local or CI), its exact tool versions/resource inventory, and its valid/
  malformed semantic assertions plus fixture provenance/license disposition before completion.
- G5 closeout confirms one public format facade/options/limits model, one source/cursor path, and one
  shared limits/diagnostics/lifecycle profile remain sufficient; no second parser, format registry,
  native-only behavior, or speculative abstraction survives the audit.

## Required tests

- JVM tests of the expanded smoke entrypoint for valid and malformed fixtures.
- Resource-presence tests that fail before native compilation if a required fixture is omitted.
- JVM tests pin exact resource-config text, selected corpus role/license/provenance and root-license
  equality, inventory paths/lengths/hashes, bounded copy failures including partial writes, the
  tolerant render oracle, stable result summary, and complete temporary cleanup.
- Architecture tests reject wildcard metadata, discovery/enumeration, arbitrary algorithm/provider
  selection, and every already prohibited mechanism across the shapefile and native-support outputs;
  the only allowed digest construction is literal JDK `SHA-256` in the fixed support workspace.
- The actual Native Image build-and-run lane on Java 21.

## Validation

```bash
./gradlew :modules:mundane-map-native-tests:check --console=plain
./gradlew nativeSmoke --console=plain
./gradlew qualityGate --console=plain
git diff --check
```

## Notes

Keep the native fixture minimal and independently licensed. Do not hide reachability problems with
broad metadata; make registrations and resource inclusion explicit. Do not invoke `shapefileCorpus`,
embed its inventory, add performance assertions, or infer Windows/macOS Native Image support.

Completed on 2026-07-14. The architecture-policy slice distinguishes the checked-in resource
inventory from the five files copied into processed resources, pins the seven literal Native Image
resource patterns, and rejects resource walking, arbitrary security-provider use, and digest
construction outside the fixed Shapefile workspace. Focused native-support and architecture checks
passed with 35 native-support JVM tests; the post-review `qualityGate` passed with 168 tasks.

The successful Linux x86-64 lane used GraalVM CE Java 21.0.2+13.1 and `native-image 21.0.2` on Linux
5.15.167.4 WSL2. An initial invocation under OpenJDK 21.0.11 failed before compilation because the
Native Image Java launcher was unavailable; rerunning the same command with the installed Java 21
GraalVM selected in `JAVA_HOME` built the 43.50 MB executable in 28.1 seconds and ended with
`mundane-map native smoke: OK`. This is Linux evidence only.

The exact added runtime resources are `polygon-smoke.shp` (`5c2862ee…fe37`), `.shx`
(`a1b843a5…791c`), `.dbf` (`aeff5d58…add`), `.cpg` (`ae03d345…33a2`), `.prj`
(`4f01f36b…1394`), and `malformed-record.shp` (`9ed470ac…07a`). JVM and native execution both
validated two ordered EPSG:3857 polygon records, shell/hole and multipart topology, `Café`, undefined
Windows-1252 null substitution with the record-2/field-1/byte-129 warning, and tolerant fill/outline
rendering. The malformed path produced `SHAPEFILE_RECORD_LENGTH_INVALID` at SHP record 1, byte 104,
with `declaredBytes=20`, `remainingBytes=0`, and `reason=outOfFile`.

The positive five-file subset retains manifest authority `GENERATED`,
`generated:pyshp-profile-v1`, `pyshp-2.3.1`, and `BSD-3-Clause`; its referenced license bytes equal
the repository root `LICENSE`. The project maintainer's explicit advance approval of every G3-G5
HITL checkpoint accepts this tool/resource/semantic/provenance evidence as the named **G5 native
shapefile approval**. G5 closeout retains one public facade/options/limits model, one source/cursor
path, shared diagnostics/lifecycle limits, and no native-only parser, registry, public abstraction,
external runtime dependency, or native acceleration.
