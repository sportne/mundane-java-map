# G17 — Project hardening

G17 improves documentation, build feedback, public Javadocs, and verification depth without adding
a map capability or widening a format, platform, projection, symbology, Native Image, or release
claim. Specialized corpus, rendering, performance, Native Image, publication, SQLite deployment,
and offline-repository evidence remain distinct from ordinary unit coverage.

## G17-001 documentation currency and consistency audit

The 2026-07-26 audit compared user and developer documentation with `settings.gradle`,
`build.gradle`, all production and example directories, current tests, four GitHub Actions
workflows, published-resource notices, provenance files, the roadmap, the design index, and the task
index. Source and executable build configuration were authoritative where historical prose differed.

| Audited fact | Reconciled result |
| --- | --- |
| Java baseline | Consumers and compilation require Java 21; Java 17 or newer may launch Gradle, which selects the Java 21 toolchain. |
| Project inventory | 18 published modules and 20 runnable examples match the explicit settings inventory. |
| Runtime boundaries | JDK-only production modules, AWT isolation, two optional Jackson adapters, two optional Linux JVM-only Xerial adapters, and the JVM-only HTTP client remain explicit. |
| Format/profile wording | Implemented Level 1 and bounded Level 2 profiles are distinguished from deferred projections and JTS/PROJ/GDAL adapters. |
| World wrap | G16 is complete; repetition remains explicit, horizontal, bounded, Web-Mercator-specific, and disabled by default. |
| Verification | The normal gate and every separate corpus, rendering, performance, stress, native, publication, SQLite-platform, and offline-isolation lane have current names and boundaries. |
| Provenance | Root documentation links the project license, optional-adapter notices, and principal independently sourced or generated fixture notices. |
| Navigation | Local Markdown targets and the complete task-card index are machine checked; an accidental link-like timestamp was clarified. |

`DocumentationConsistencyTest` now checks repository-local Markdown targets, complete task indexing,
the published-module and runnable-example inventories, and Gradle commands in operational README
files. It reads the explicit Gradle project inventory, runs offline in the architecture-test
project, and is part of `qualityGate`. The final source-versus-documentation comparison found no
known stale capability, command, inventory, support, provenance, or limitation statement in the
current normative documentation. Historical task evidence remains unchanged except for links or
status records needed to keep it navigable.

## G17-002 build and CI duration reduction

### Environment and method

The controlled local measurements used commit `b6f9474d0951df4cf2d300b9b6c709567938d43c` as the
before revision on Ubuntu 24.04 WSL2, Linux 5.15.167.4 x86-64, an Intel Core i9-14900KF exposed as 32
processors, 31 GiB RAM, OpenJDK 21.0.11, and Gradle 9.5.1. Temurin 25.0.3 was already present in the
Gradle toolchain cache. Each timed sample ran a separate `./gradlew clean` preparation, then the
measured command with the same dependency and local build caches. The first sample in each series
therefore models clean project outputs with restored caches, as used after a checkout when
`gradle/actions/setup-gradle` restores state; it is not described as an empty or cold cache. Later
samples record warm-output variance.

A second controlled series placed detached before and exact uncommitted after snapshots beside each
other on the same `/tmp` filesystem. Every counted sample used a separate `clean`, disabled both the
build and configuration caches, and set `--max-workers=4`. Two passing samples per revision provide
the cold-output median. An exploratory default-worker sample exhausted the 512 MiB daemon heap, and
an exploratory sample on `/mnt/d` timed out because it did not share the baseline filesystem; neither
is counted. Dependency artifacts and the verified Gradle distribution remained cached, so this is a
cold build/configuration comparison, not a claim about network download time.

Raw samples are checked in at
[`verification/G17-002-timings.tsv`](../verification/G17-002-timings.tsv), and the categorized
Gradle profile extraction is retained at
[`verification/G17-002-profile-timings.tsv`](../verification/G17-002-profile-timings.tsv).
The prior reviewed GitHub observations and current specialized-lane observations are distinguished
in [`verification/G17-002-lane-timings.tsv`](../verification/G17-002-lane-timings.tsv); approximate
G10-044 remote observations are not presented as controlled samples.
`/usr/bin/time` measured the Gradle client wall clock and maximum resident set; its user/system
fields exclude daemon CPU and are retained only as raw environment observations. Separate Gradle
`--profile` runs categorized compilation, unit test, Javadoc, Checkstyle, SpotBugs, JaCoCo, and
Spotless work. The profile table labels any unavailable category instead of reconstructing it.
Specialized-lane durations remain job-local evidence and are not added together as a portable
threshold. The exact post-change GitHub correlation remains part of the pending exact-commit
checkpoint rather than being inferred from the local filesystem.

### Critical path and selected change

The main workflow previously ran `qualityGate` for both Java 21 and Java 25. Compilation is fixed at
Java 21, while `-Pmap.testJavaVersion=25` changes the JUnit launcher. The additional supported-JDK
outcome is therefore that every normal suite compiles against Java 21 and passes on Java 25.
Formatting, Checkstyle, SpotBugs, JaCoCo reporting/thresholds, and Javadocs are task-based,
Java-independent verification already owned by Java 21. The architecture-test JUnit suite and its
declared dependency checks still run on Java 25 because they are part of the normal suite. A dry
graph contained 905 tasks for the Java 25 `qualityGate` and 345 for the dedicated test aggregate.

The workflow now has one Java 21 `quality` job running the complete `qualityGate` and one
`test-java-25` job running `supportedJdkTests`. That declarative aggregate depends on the included
build test and every inventory project's normal `test` task. Both the included build and project
tests use an explicit Java 25 launcher, while Java compilation remains on the Java 21 toolchain.
JaCoCo instrumentation and report finalization are enabled only when tests use the Java 21 baseline;
the Java 21 `qualityGate` still owns every report and coverage threshold.

| Measurement | Before Java 25 `qualityGate` | After Java 25 `supportedJdkTests` | Change |
| --- | ---: | ---: | ---: |
| Warm-cache median | 34.66 s | 7.42 s | -78.6% |
| Cold build/configuration median (4 workers) | 179.47 s | 50.97 s | -71.6% |
| Scheduled tasks | 905 | 345 | -560 (-61.9%) |
| Formatting/static-analysis/coverage/Javadoc tasks | repeated | 0 | retained in Java 21 owner |

The clean preparation itself is intentionally outside the timed command. One exploratory
`clean qualityGate` invocation failed because parallel cleanup and verification have no declared
ordering; the controlled protocol therefore uses two invocations, as a fresh checkout does. This is
recorded evidence rather than a new quality threshold.

### Verification equivalence and rejected alternatives

[`G17-002-verification-manifest.tsv`](../verification/G17-002-verification-manifest.tsv) is the
authoritative machine-readable lane inventory. `VerificationManifestTest` proves its exact lane set,
trigger policy, owner files, owning jobs, complete normalized commands, Java 21/25 split, and
absence of a second Java 25 `qualityGate`. Negative mutations remove every retained command in turn;
each is reported missing by the same scheduler check, so removing a command or job fails the
architecture-test project and therefore the normal gate.

The following alternatives were tested or reviewed and rejected:

- Java 25 `checkAll` was the first conservative candidate. It removed 42 Javadoc/aggregate tasks,
  but its warm median improved only 7.3% and its controlled cold median only 3.6% because it repeated
  every other Java-independent check. Those measurements remain in the timing tables as
  `rejected-checkAll`;
- deleting or path-filtering required main quality, coverage, architecture, rendering, corpus,
  performance, native, publication, SQLite, or offline evidence would weaken the manifest;
- combining Java 21 and Java 25 sequentially in one job would lengthen the critical path and lose
  independent failure attribution;
- sharing compiled artifacts between JDK jobs would add provenance and cache-key complexity for a
  smaller gain than removing duplicated Javadocs declaratively; and
- portable duration thresholds were rejected because runner, filesystem, cache, and daemon
  variance dominate small local differences.

Fresh clones still download locked build dependencies and run every owning task, so their wall clock
also includes network and runner effects deliberately absent from the local cold series. Warm
iterations use the existing build and configuration caches. Corpus lanes keep their deliberately
empty homes and offline reruns; offline repository, Native Image, SQLite deployment,
publication/consumer, and million-track evidence keep their existing isolation and explicit cost.
Native Image and SQLite timing/outcome baselines are runner-specific and are therefore left to the
permitted exact-commit workflows rather than approximated on WSL2.

### Why the build still takes time

Gradle profile task durations are parallel sums and therefore do not add up to wall clock. They are
useful for locating work, while the controlled command timings remain the before/after evidence.
The remaining cost has several distinct causes:

| Area | Why it costs time now | Safe next optimization to test |
| --- | --- | --- |
| Startup and configuration | Gradle loads the included `build-logic` build, convention plugins, 40-plus projects, toolchains, and task models. A non-reused profile spent about 8.4 seconds in startup but only 0.7 seconds configuring projects. | Preserve the configuration cache, daemon, and stable inputs. Profile plugin application before attempting configuration avoidance; configuration itself is not the present critical path. |
| Compilation | Clean builds invoke many Java compiler processes across production, test, fixture, corpus, rendering, native, and performance source sets. All use Java 21 release checking, `-Xlint:all`, Error Prone, and warnings-as-errors. | Avoid `clean` for iteration, retain local/CI build caches, and improve cache-key stability. Measure worker count and daemon heap together. Combining modules merely to remove compiler startup would trade away architecture boundaries. |
| Unit tests | Every project has an independently isolated JUnit task, and SQLite tests start native-backed adapters. Many small tasks make process and classpath setup visible even when individual tests are fast. | Measure bounded test forks or safe test-suite grouping, and cache fixture construction. Do not merge tests that need distinct process, platform, or classloader isolation. |
| Spotless | Java formatting uses Google Java Format, while every project also starts Greclipse for Gradle scripts. Greclipse/OSGi resolution is conspicuous on clean runs and currently emits a non-fatal background classloader warning after some successful builds. | Investigate one root-owned Gradle-script formatting task, a shared formatter service, or a formatter/version change that removes repeated Greclipse startup. Keep the exact check in the Java 21 quality owner. |
| Checkstyle | Main, test, specialized source sets, and published public API declarations are scanned separately. Its observed parallel sum is comparatively small. | Keep it as-is unless profiling a larger source tree changes the ranking; shared immutable configuration and cacheable inputs already offer the useful optimization. |
| SpotBugs | Bytecode analysis runs for main and test output in every project. It is CPU- and heap-intensive, and in the after profile had a 57.8-second parallel sum even though parallel execution hid much of that wall time. | Verify build-cache hits, tune worker count/heap, and investigate whether unchanged test-bytecode analysis can be reused. Do not reduce detector coverage merely to shorten the lane. |
| JaCoCo | Java 21 tests run with instrumentation, then XML and HTML reports plus coverage verification read execution data and class files for each project. | Ensure report and threshold tasks share the same execution data and remain cacheable. The Java 25 supported-runtime lane now avoids instrumentation because Java 21 owns coverage evidence. |
| Javadocs | Strict doclint and warnings-as-errors start Javadoc for every published module. The before profile recorded a 63.5-second parallel sum. This work is independent of the Java 25 runtime launcher. | The selected change runs it once on Java 21. A future aggregate documentation check could reduce process startup, but per-module Javadoc jars must still be produced and verified for publication. |
| Rendering, corpora, and performance | These lanes deliberately load fixtures, render images, parse real format corpora, run memory/performance probes, use offline reruns, and retain diagnostic artifacts. The combined local validation took 149.4 seconds. | Cache only immutable primed dependencies and fixture preparation. Preserve offline execution, forced evidence regeneration, tolerant rendering assertions, and descriptive rather than portable timing thresholds. |
| Offline repository | The task stages a complete Maven repository, copies the source tree, creates an empty Gradle home, copies a verified wrapper, disables discovery/downloads, and runs the full quality gate in a no-daemon child. It took 298 seconds locally. | Cache or artifact the same-commit staged repository and make source-copy inputs more selective, while retaining a genuinely empty consumer home and one-repository/no-network proof. |
| Publication and consumer | Every published module creates POM, metadata, sources, Javadocs, and reproducible archives, publishes them to a staged repository, then a separate consumer build resolves and runs them. The local batch took 38.3 seconds. | Reuse same-commit verified publication artifacts between staging and consumer jobs. Never let the consumer resolve workspace outputs or an untracked external repository. |
| Native Image and SQLite matrix | Native Image performs closed-world analysis and native compilation. SQLite evidence builds/stages two adapters, runs separate rejection/success JVMs on two glibc runners, and launches an Alpine container for musl rejection. Runner provisioning is also material. | Share provenance-checked staged inputs across matrix jobs and preserve the platform/process probes. Native build caches and a smaller smoke reachability surface may help, but neither lane should be folded into the normal JVM gate. |

In practice the fastest feedback comes from running the narrow project test during development,
then `supportedJdkTests` when validating an additional JDK, then `checkAll`, and finally the full
Java 21 `qualityGate`. The isolated/platform lanes remain slower because their value is specifically
that they do not reuse normal-process assumptions.

HITL checkpoint: on 2026-07-26 the maintainer approved the reviewed Option 2 schedule after reviewing
the measured Java 21 full-`qualityGate` and Java 25 `supportedJdkTests` trade-off, build-duration
causes, follow-up options, and retained verification manifest. Exact-commit GitHub timings will be
correlated after the task commits are pushed.

## G17-003 comprehensive public Javadocs

Strict Java 21 documentation generation and missing-Javadoc enforcement now apply to every
hand-authored main source set in the 41-project inventory and to the included `build-logic` build,
not only the 18 published modules. The `javadocAll` aggregate covers those sources once and is the
documentation dependency of `qualityGate`; it uses UTF-8, every doclint group, warnings-as-errors,
deterministic no-timestamp output, and no network documentation links.

The shared public-API Checkstyle policy requires package documentation and non-empty Javadocs for
public/protected types, methods, constructors, fields, enum constants, and annotation elements. It
also checks type parameters and record components at the type declaration, and method parameters
and non-void returns plus declared or directly thrown failures on ordinary methods and constructors.
No annotation implicitly exempts a hand-authored type; in particular, Checkstyle's default
`Generated` exemptions are explicitly cleared. Compact record constructors retain invariant-focused
comments without duplicating the record component tags already enforced on the record declaration.
Overrides may use inherited documentation; generated Gradle adapter sources remain outside the
hand-authored build-support source tree.

The audit added package contracts to all 20 runnable examples and the two non-published production
support packages, documented the build task inputs/outputs and explicit Gradle-managed constructors,
completed example entry-point and factory contracts, and completed the performance bridge's units
and ownership descriptions. Published-module documentation already satisfied the earlier presence
policy; the stronger parameter, return, record-component, non-empty-description, and deprecation
rules found and closed the remaining gaps without changing runtime behavior.

`BuildConfigurationTest` proves the all-source-set scheduling and independently mutates documented
fixtures for missing package, public/protected type, constructor, method, field, enum constant,
annotation element, record component, type/value parameter, return, non-empty description,
deprecation, checked/unchecked failure, and annotation-exemption coverage. Publication verification
compares every staged binary top-level type with its source entry and every externally visible
top-level or nested type with its generated Javadoc page; corruption tests remove each artifact in
turn. `javadocAll`, the all-project public-API scan, publication staging, consumer resolution, and
`qualityGate` provide the executable documentation and artifact evidence.

## G17-004 aggregate code coverage

The shared Java convention now enforces an exact `0.80` minimum for JaCoCo's existing
`INSTRUCTION/COVEREDRATIO` metric at `BUNDLE` scope. No source, class, package, generated pattern,
or low-coverage project was removed from the governed population. The architecture-test project
remains in the inventory but has no executable production instructions and therefore no numeric
ratio. Each normal Java 21 test run produces XML and HTML reports before the aggregate verification
attached to `check`; Java 25 remains a supported-runtime test lane without duplicate instrumentation.

The checked
[`G17-004-coverage-baseline.tsv`](../verification/G17-004-coverage-baseline.tsv) records the
pre-change missed and covered instruction counts for all 41 governed projects and identifies the
uncovered behavior by risk. Thirteen projects began below the target. Their new deterministic tests
exercise portrayal rule selection and interpolation, viewer entry-point diagnostics, EDT
confinement, asynchronous success/interruption/error propagation, ownership transfer and rejected
installation cleanup, real bounded SQLite loading, scale and zoom limits, pointer/status updates,
and bounded live-track command lifecycles. Small package-private seams separate UI installation or
future completion from window construction so those public behaviors can be asserted without
display-dependent tests; they do not alter the public API or exclude bytecode from coverage.

`BuildConfigurationTest` constructs one five-method fixture at exactly eight of ten covered
instructions and proves that the 80% boundary succeeds, XML and HTML reports are generated, and
coverage verification remains in the `check` graph. A second fixture covers six of ten instructions
and proves that the standard failure identifies the bundle, instruction ratio, actual `0.60`, and
required `0.80`. Full-project verification then proves every executable bundle meets the same rule.

## G17-005 per-source-file code coverage

The shared Java convention now inventories every hand-authored production Java, Groovy, and Gradle
source and requires each executable physical file to reach the same exact `0.80`
`INSTRUCTION/COVEREDRATIO` floor. This rule is additive to the G17-004 bundle threshold: aggregate
coverage can no longer conceal an untested adapter, value type, example entry point, native smoke
scenario, performance launcher, or build-support task.

`VerifySourceFileCoverage` reads the JaCoCo XML with secure parser settings, maps every reported
class—including nested, record, enum, and lambda classes—back to a physical source, and compares
that population with the source-tree inventory. It emits all violations together with stable
project-relative paths and six-decimal actual and required ratios. Files without executable
instructions remain visible with `NO_INSTRUCTIONS` status. Generated build-logic adapters are
accepted only through an exact checked key inventory; the six precompiled convention plugins use
explicit aliases to their hand-authored Groovy scripts.

Every governed project writes deterministic sorted reports at
`build/reports/jacoco/test/source-coverage.csv` and
`build/reports/jacoco/test/source-coverage.md`, alongside the authoritative JaCoCo XML. Build
logic combines its ordinary tests with JaCoCo-instrumented TestKit child builds so convention
plugins and task implementations are governed rather than exempted. Functional fixtures prove the
exact 80% boundary, below-threshold diagnostics, uncovered-source detection, no-instruction
handling, deterministic output, class/source mappings, generated provenance, and attachment to
`check`.

The behavioral expansion closes the previously hidden gaps through assertions over resource
limits, UTF-8 encoding, filesystem races and cleanup, immutable values, database lifecycle,
MapLibre filter compilation, raster sampling, GUI failure propagation, native scenarios, maximum
DTED publication, and performance/example entry points. No production file, package, example,
adapter, record, exception, or build-support source is excluded, and specialized corpus,
rendering-regression, performance, native, publication, SQLite, and offline lanes retain their
independent evidence roles.
