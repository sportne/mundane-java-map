# Native Image support profile

This support module owns executable evidence for the published modules that the project marks as
GraalVM Native Image targets. It does not turn every optional adapter into a native-supported
dependency and it is not itself a published runtime module.

## Supported host matrix

Native support means that the pinned GraalVM/JDK toolchain builds and runs the project smoke and
hostile-input corpus on a native runner for each row below. Cross-compilation and emulation may be
useful development checks, but neither is sufficient evidence for an advertised host.

| Operating system | Architecture | Support intent | Required evidence |
| --- | --- | --- | --- |
| Linux | x86-64 | Required | Build and execute on a native x86-64 Linux runner |
| Linux | AArch64 | Required | Build and execute on a native AArch64 Linux runner |
| macOS | x86-64 | Required | Build and execute on an Intel macOS runner |
| macOS | AArch64 | Required | Build and execute on an Apple-silicon macOS runner |
| Windows | x86-64 | Required | Build and execute with the supported MSVC toolchain on a Windows x64 runner |
| Windows | AArch64 | Not claimed | No supported release evidence is promised |
| Other operating systems/architectures | Any | Not claimed | Best effort only; no compatibility promise |

Native images are host-built. The project does not claim general cross-compilation between operating
systems or architectures.

## Toolchain and CPU baseline

- The GraalVM distribution, JDK feature line, Native Build Tools plugin, native compiler/toolchain,
  runner image, and relevant system-library baseline are pinned in release evidence.
- Release images target GraalVM's compatibility-oriented CPU baseline rather than the build host's
  native instruction set. Host-specific `-march=native` artifacts are not portable releases.
- A toolchain upgrade is a reviewed support-profile change and must rerun every supported row.
- Native support is established by executing the image, not merely by completing image generation.

## Included capability profile

- Every production module marked `nativeTarget: true` in the project registry must have a reachable,
  assertion-bearing native scenario or an explicit evidence mapping showing where its behavior is
  exercised.
- The same semantic success, hostile-input, stable-diagnostic, bounded-work, ownership, and cleanup
  expectations apply on every supported host. Platform-specific fixtures supplement rather than
  replace the shared corpus.
- Charset, locale, timezone, filesystem/path, XML, compression, image codec, HTTP/TLS, resource,
  and process-boundary differences are tested where reachable from the supported module set.

## Closed-world boundary

- Reachability metadata and embedded resources are explicit, bounded, reviewed, and tested. Runtime
  classpath scanning, reflection discovery, serialization fallback, JNI, `Unsafe`, and implicit
  resource discovery remain prohibited by the production native-target rules.
- AWT is included only for the project's explicitly supported offscreen Java2D native scenarios; the
  module makes no native desktop-windowing claim.
- Jackson, Xerial SQLite, Vaadin, and other adapters marked non-native are excluded unless a later
  adapter-specific capability decision supplies its own supported graph and evidence.
- An accidental dependency or resource-registration expansion must fail mechanically before release.

## Distribution linkage

The project supports GraalVM's standard dynamically linked host executables. Linux release evidence
uses the pinned glibc-based runner/toolchain profile. Fully static musl images and mostly-static glibc
images are distinct deployment products and are not supported by this module.

## Explicit non-goals

- Native shared-library artifacts and C APIs.
- Fully static musl and mostly-static glibc executables or scratch/distroless-container guarantees.
- GraalVM polyglot/language-runtime embedding.
- Cross-compiled artifacts presented as native-host support evidence.
- Native support for an adapter merely because its JVM dependency happens to build in one local image.

## Completion rule

The native-test module is complete only when its decomposed G19 cards implement the frozen support
matrix, every supported runner executes equivalent assertions, exclusions are mechanically enforced,
and release evidence records the exact reproducible inputs and documented platform exceptions.
