# Architecture-governance capability intent

`mundane-map-architecture-tests` owns mechanical governance of the repository's declared project graph,
public artifact/API compatibility, Java module identity, forbidden implementation mechanisms, documentation
consistency and release-policy evidence. It is a non-published support module; it does not make runtime
decisions for applications or become a production dependency.

The released checks strongly enforce dependency/category/native/JDK-only boundaries and selected workflow,
manifest and documentation rules. G19 completion adds verified-release API baselines, version policy and
honest JPMS/module-path contracts without weakening classpath, native or optional-adapter behavior.

## Governance matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Project graph | Central category/release/native/published inventory plus runtime-edge checks | One authoritative artifact/module graph covering projects, scopes, optionality and published identity | G19-191, G19-192 |
| Public API compatibility | Compile/tests/Javadocs; no prior-release signature comparison | Verified released baselines, strict binary/source reports and reviewed change declarations | G19-190 |
| Comparison engine | Project-specific structural checks only | Pinned build-only Revapi Java analysis plus focused stricter Java-language rules | G19-190 |
| Version semantics | Project version exists; no mechanically enforced change policy | Patch compatibility before 1.0; reviewed pre-1.0 minor breaks; conventional SemVer at/after 1.0 | G19-190, G19-192 |
| JPMS identity | No module descriptors or stable automatic-module-name policy | Explicit descriptors for module-path-clean published graphs; stable automatic modules with consumer tests otherwise | G19-191 |
| Encapsulation | Java packages and Gradle dependency boundaries | Exact exports/requires/uses/provides/qualified opens, split-package/service checks and no false strong-module claim | G19-191 |
| Release evidence | Publication/offline/native/check lanes | Baseline provenance, reproducible reports, migration notes, version enforcement and publication-consumer matrices | G19-192 |

## JPMS policy

Every published artifact has one stable globally unique Java module name derived from project ownership,
not from an unstable dependency filename. JDK-only runtime modules and optional adapters whose complete
runtime graph is demonstrably module-path safe provide explicit `module-info.java` descriptors. Descriptors
export only supported API packages, declare the narrowest correct `requires`/`requires transitive`, and use
explicit `uses`/`provides` for registered service contracts.

An optional adapter whose third-party dependency graph still relies on unnamed-module/classpath or broad
reflection behavior instead publishes a stable `Automatic-Module-Name`. It must pass real module-path
consumer tests as an automatic module, and its documentation states that it is not strongly encapsulated.
Vaadin, Xerial SQLite, Jackson and future ecosystem adapters are decided from their pinned resolved graphs,
not granted automatic exemptions by name. A dependency upgrade re-evaluates eligibility for an explicit
descriptor.

No descriptor uses unqualified broad `opens`. Any qualified reflective opening names the exact package and
consumer module, has a tested need and cannot expose unrelated internal packages. Split packages, duplicate
module names, unstable automatic dependency names, concealed public signature types, service/provider drift,
resource lookup assumptions and classpath/module-path behavior differences fail verification.

Examples, tests, benchmarks, build logic and other non-published support projects do not acquire a public
JPMS contract. Test-only descriptors/fixture modules may exercise consumers without turning support packages
into exported API.

## Baseline and evidence principles

- Each published artifact names its latest compatible immutable released coordinate in a checked-in baseline
  manifest with version, SHA-256, POM/module metadata and provenance. A developer cannot silently select
  “latest,” regenerate the baseline from the current tree or substitute a local artifact.
- Reports distinguish JVM binary linkage, Java source recompilation, generic/signature/annotation/API-shape,
  module graph and services/resources. Passing one category never implies all.
- Intentional changes require a reviewed machine-readable declaration scoped to exact symbols/rules, an
  expiry/version, rationale, migration/replacement and release-note link. Blanket ignore lists are prohibited.
- Synthetic fixture artifacts prove every allowed/forbidden change classification and tool upgrade behavior.
  External compatibility tooling, if adopted, is pinned, checksum/license governed and offline reproducible.
- Architecture rules must remain deterministic, bounded and diagnosable; they do not scan ambient classpaths,
  networks, local repositories or unregistered releases.

Normal connected verification resolves only the exact manifested release from approved repositories and
checks all bytes/metadata before analysis. The existing offline-repository workflow stages those same release
artifacts and compatibility-tool graphs, then proves the gate with network access unavailable. Release JARs
are not copied into Git.

Before an artifact's first public release, a reviewed checked-in deterministic API-signature/module snapshot
may be its explicit provisional baseline. It records the candidate version/profile and cannot masquerade as
a published artifact. Successful publication verification atomically replaces the provisional reference with
the actual immutable coordinate/digest facts. A release process advances a baseline only after repository,
POM/module/checksum/signature, consumer and reproducibility verification succeeds.

## API comparison engine

The build uses pinned Apache-2.0 Revapi components, including the Java analyzer, as the primary released-JAR
API difference engine. They are build/test/offline-repository inputs only and never enter production runtime,
published dependency or native-image graphs. Exact coordinates, transitive graph, licenses and SHA-256
artifacts are governed alongside other build tools.

Revapi reports raw typed differences and source/binary/semantic severities; project code maps those stable
difference codes through the approved compatibility/version policy. The project configuration overrides
Revapi's default pre-1.0 behavior so patch releases cannot silently break, and no analyzer classification
automatically grants an exception.

Project-owned checks supplement Revapi for explicit/automatic JPMS metadata, enum and sealed exhaustiveness,
record shape, overload/source ambiguity fixtures, stable constants/annotations/nullness policy, services/
resources and public dependency leakage. Synthetic old/new JAR
fixtures pin expected classification and detect analyzer/configuration drift before a tool upgrade is accepted.

## Version policy

Before 1.0, `0.MINOR.0` may contain reviewed breaking public changes. `0.MINOR.PATCH` remains binary and
source compatible with the corresponding minor baseline. A pre-1.0 break still requires an exact scoped
declaration, rationale, replacement/migration guidance and release-note entry; the leading zero is not an
excuse for unreported drift.

At and after 1.0, the project follows ordinary semantic versioning: a major release may break the governed
contract, a minor release may add compatible API and deprecate existing API, and a patch release may fix
implementation/behavior without breaking documented API or valid existing usage. Pre-release identifiers
do not establish compatibility with later pre-releases unless an explicit release-train policy says so;
the final release is checked against its declared stable baseline.

Deprecation normally remains for at least one subsequent minor release before a major-version removal and
names the supported replacement/migration. A narrowly scoped security or correctness emergency exception
requires maintainer approval, explicit affected symbols/behavior, severity/rationale, migration and release
note; it cannot be a permanent or blanket compatibility suppression.

The release gate calculates the minimum required version change from API/module/behavior declarations and
rejects both an insufficient bump and undocumented compatibility loss. It also rejects accidental baseline
selection, version regression, reused released coordinates and mismatch between artifact, POM, module,
manifest, documentation and release-note versions.

## Strict Java API classification

The governed surface includes public and protected classes, interfaces, annotations, records, enums, members,
constructors and inherited contracts in exported/supported packages, plus their generic signatures, type-use
annotations, checked exceptions, constant values, default values, deprecation and documented nullness.

Removal, access/finality/static changes, incompatible types/signatures/bounds/variance, record component
addition/removal/reorder/type change, abstract interface method addition, incompatible default method,
checked-exception widening/addition, nullness narrowing, relevant runtime annotation removal/change, constant/
annotation-default change and package/module export/readability narrowing are breaking.

Adding an enum constant or permitted sealed subtype is treated as source-breaking because valid exhaustive
switches may cease to compile or cover the domain. A new overload is compatible only when fixture-backed
resolution proves it does not make previously valid calls ambiguous or select a different target. New types/
members and default methods are compatible only when they do not conflict with inheritance, erasure, bridge,
implementation, linkage or documented behavior. Binary compatibility alone is never sufficient.

## Behavioral ownership boundary

This support module does not build a generalized behavioral-compatibility manifest or attempt to infer
semantic equivalence from bytecode. Stable diagnostics, defaults, ordering, limits, ownership, threading,
lifecycle, determinism and protocol/format behavior remain documented and regression-tested by the module
that owns each contract. A release note still discloses material behavior changes, but the architecture gate
does not duplicate every owning-module specification.

## Task decomposition

G19-190 governs released Java API/SemVer, G19-191 governs JPMS/module-path behavior, and G19-192 integrates
those checks with release evidence and closes this support module.

## Deliberate exclusions

- Treating internal/test/example packages as public, promising compatibility for reflection into internals,
  Java serialization compatibility, automatic discovery/scanning, or arbitrary third-party dependency APIs.
- Claiming strong JPMS encapsulation for an artifact whose dependency graph cannot actually run that way.
- Replacing review of behavioral/specification changes with a bytecode-signature tool result.
