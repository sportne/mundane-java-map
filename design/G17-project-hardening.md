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
