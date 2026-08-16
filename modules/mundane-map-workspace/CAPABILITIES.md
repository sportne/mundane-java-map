# Workspace capability intent

`mundane-map-workspace` owns the project's native, versioned, bounded map-project persistence. It is
not a geospatial interchange standard. OGC Web Map Context, OGC OWS Context, QGIS projects, and other
application formats are deliberate exclusions rather than implied compatibility claims.

Two native forms share one canonical XML manifest and semantic model:

- `.mmap.xml` is readable, diffable, source-control-friendly, and normally references guarded external
  resources; and
- `.mmapz` is a custom ZIP package that may embed complete authorized resource sets for portability.

Canonical hardened XML is retained because the JDK provides a directly constructed streaming parser,
the module remains JDK-only, canonical output is deterministic, and v1 migration stays direct. XML is
an implementation and lifecycle choice, not a standards claim.

The README describes released v1 behavior. Target rows below become release claims only as their G19
cards close.

## Version and form matrix

| Surface | Released profile | Approved completion target | Card |
| --- | --- | --- | --- |
| Document | Strict canonical UTF-8 `.mmap.xml` v1 | Canonical hardened XML v2 with complete state inventory, namespaces/versioning, immutable domain, and deterministic read/write | G19-170 |
| References | Guarded relative local feature/raster paths and symbol names | Typed local, packaged, and authorized remote source/style/catalog/resource references with identities and non-secret policy | G19-171 |
| Map composition | Viewport, ordered feature/raster layers, limited symbols/presentation | Complete public layer/source order, visibility, CRS, viewport, wrap, portrayal, labels, raster/elevation and adapter options | G19-172 |
| User/edit state | Not persisted | Committed editable content/references, selection IDs, active tool kind and stable preferences; restored tools are idle | G19-173 |
| Migration/extensions | No migration; unknown grammar rejected | Deterministic v1 migration, version negotiation, bounded optional extension infoset/codecs, downgrade loss reports | G19-174 |
| Portable package | Unsupported | Custom `.mmapz` ZIP with canonical v2 manifest, explicit embed/reference resources, media types, complete resource sets and limits | G19-175 |
| Integrity/trust | Atomic XML write only | Mandatory SHA-256 entry/resource/package facts and explicit caller verifier; no built-in PKI or encryption | G19-176 |
| Save/recovery | Same-directory atomic replace | Locks/leases, durable transactional save, backups, conflict detection and bounded crash recovery for both forms | G19-177 |
| Open/lifecycle | Transactional local open and owned source session | Generation-safe package/external/remote authorization, cancellation, replacement, exact ownership and failure-robust cleanup | G19-178 |
| Evidence | Project v1 fixtures | Full state/package/migration/security/fault/platform/interoperability-with-project-releases closeout | G19-179 |

## Persisted-state boundary

Persisted committed state includes:

- workspace identity, title/description, format/minor version and safe extensions;
- display/map CRS, viewport, horizontal wrap and deterministic view preferences;
- ordered groups/layers, visibility, opacity/blend/scale constraints, source binding identity/options,
  portrayal/rules/labels, raster/elevation presentation, caches as policy rather than cache contents;
- registered styles, symbols, icons, fonts and other explicit catalog/resource identities;
- committed editable content or its guarded source reference, editable-binding configuration, selection
  identities, active tool kind, stable measurement/edit/navigation preferences and safe UI-neutral state;
- explicit HTTPS endpoints, service/resource selections, non-secret request policy and optional opaque
  credential aliases resolved by the host; and
- package/reference policy, content media types/digests and registered adapter-specific bounded state.

Deliberately transient state includes undo/redo history, unfinished edits/measurements, pointer gestures,
hover, capture, transient previews/overlays, pending queries/tasks, workers/executors, open streams/sessions,
decoded caches, browser/AWT renderer state, timing data and private protocols. A restored tool is idle.

Credentials and secrets are never persisted: no username/password, token, cookie, API key, private key,
client certificate, credential-store path, ambient lookup instruction or package-granted authority.

## XML, versions, and extensions

- Parse one owned bounded UTF-8 snapshot using a directly constructed hardened JDK StAX factory. Reject
  DTDs, entities, processing instructions, external resolution, non-UTF-8 declarations, duplicate/
  ambiguous core fields, invalid Unicode, depth/count/text/attribute/owned-byte/work overflow and any
  provider behavior outside the frozen profile.
- Canonical output fixes namespace prefixes, element/attribute order, number/boolean/enum/path/URI/digest
  spelling, whitespace, line endings, escaping and omission/default rules. It is semantically idempotent
  and byte-deterministic for the same value, but does not preserve source lexical formatting.
- V2 explicitly inventories every public state value as stored, derived, transient or forbidden. Minor
  versions may add optional content; incompatible required changes require a new major version.
- Unknown required core content fails closed. Unknown optional extension elements/attributes are retained
  as a bounded namespace-aware immutable infoset and written back semantically. Typed handling uses a
  directly registered immutable codec with namespace/name/version/cost/collision rules. Extensions never
  grant filesystem, network, credential, executable, parser or decoder authority.
- V1-to-v2 migration is deterministic and audited field-by-field. Every downgrade produces a structured
  loss/transformation report and proceeds only under explicit caller policy; silent loss is forbidden.

## Resource and authority contract

- Every resource has a stable workspace ID, declared kind, media/profile, source owner, location mode,
  content identity where known, adapter/options version, and dependency relationships. IDs are not paths.
- Local references are normalized guarded relative paths beneath explicit caller roots with symlink,
  case/Unicode alias, replacement and multi-file sidecar policies. Absolute paths are not portable and
  require an explicit host policy if retained at all.
- Remote references are inert HTTPS URI plus service selection and non-secret request/cache policy. Open
  never fetches until the host authorizes the authority and supplies any credential for an opaque alias.
  Workspace data cannot broaden redirects, hosts, headers, TLS/proxy policy or registered decoders.
- Registered format adapters declare the complete resource closure for packaging (for example Shapefile
  sidecars). A workspace cannot infer sidecars, partially package a declared set or reinterpret bytes.
- Resource dependency graphs, cycles, missing/incompatible versions, duplicate identities, aliasing,
  limits and authorization are preflighted before opening any source or changing live application state.

## Portable `.mmapz` package contract

- `.mmapz` is a custom documented ZIP application format, not a standard. It contains exactly one
  canonical v2 workspace manifest plus a bounded manifest of entries/resources, media types, sizes,
  SHA-256 digests, relationships and package-format version.
- Each resource is explicitly `EMBED` or `REFERENCE`. Embedded resources copy the complete adapter-
  declared local resource set as opaque bytes; referenced resources retain the guarded local/remote
  reference. Remote resources are never automatically downloaded for embedding.
- Entry names are canonical relative paths. Reject absolute/backslash/dot/empty/control/ambiguous Unicode,
  duplicate/case-fold/normalization aliases, undeclared entries, symlinks/special files, encrypted entries,
  unsupported compression/method/features, ZIP64 or data-descriptor ambiguity outside the frozen profile,
  and local/central-directory inconsistencies.
- Prospectively bound archive bytes, entries, names/metadata, per-entry compressed/inflated bytes, ratio,
  aggregate inflated/owned/temp bytes, nesting, resource sets, manifests/digests, I/O and verification work.
  Extract only through private confined storage and never expose partially verified content.
- Mandatory SHA-256 digests detect corruption, not authorship. An explicit bounded caller verifier may
  approve canonical manifest/entry identity and digest facts. There is no built-in signature syntax,
  encryption, key/certificate/trust store, revocation, timestamping or ambient trust discovery.

## Transaction, locking, and lifecycle contract

- Read/open is all-or-nothing. It resolves/authorizes/preflights every reference and package entry before
  installing a workspace; opening sources follows explicit serialized adapter lanes and reverse-order
  failure cleanup. Cancellation, replacement and close cannot strand source claims or resources.
- Save snapshots immutable committed state, validates all references/extensions/resources, writes canonical
  XML/package bytes to private same-directory storage, forces content and directory metadata as supported,
  verifies the staged artifact and atomically installs it. It never weakens to a non-atomic move silently.
- Define cross-process lock/lease identity, stale-owner policy, optimistic source-generation/fingerprint
  conflict checks and explicit force/save-as behavior. No workspace metadata can choose arbitrary lock paths.
- Backup retention is bounded and policy-driven; backups are complete verified prior artifacts. Recovery
  inventories only recognized temp/backup/journal names, validates content/identity/generation and requires
  explicit selection when more than one valid outcome exists. Existing data is never silently repaired.
- Aggregate primary failures with cleanup/close/rollback failures while preserving the stable public cause.
  Disk-full, permission, I/O, cancellation, crash-phase, concurrent save/open/replace and session/application
  shutdown paths leave one valid previous/current artifact or an explicit bounded recovery choice.

## Deliberate exclusions

- OGC WMC/OWS Context, QGIS projects/packages, ArcGIS projects, ISO metadata packages, BagIt/RO-Crate,
  GeoPackage-as-project, generic ZIP/document containers and claims of third-party workspace compatibility.
- Runtime object serialization, Java serialization, arbitrary XML/JSON/object graphs, private renderer/browser
  protocols, live sessions, secrets, ambient authority, executable scripts/plugins/macros and dependency discovery.
- Built-in signing/encryption/PKI, automatic remote embedding, hidden transcoding/reprojection/repair, or
  treating a digest as authenticity.

## Completion evidence

- Maintain a field-by-field public state inventory and schema/version/extension/package requirement matrix.
  Cross-read/write canonical v1/v2 fixtures from every supported project release and prove migration/loss reports.
- Test every source/layer/portrayal/edit/tool/resource/reference/package/extension state with all registered
  adapters, examples, AWT and Vaadin applications, while keeping adapter dependencies explicit and optional.
- Run XML/ZIP/path/URI/digest/extension hostile corpora, fuzz seeds, limits, cancellation, ownership,
  concurrency, lock/conflict, disk-full/crash/recovery, deterministic output and supported filesystem/OS evidence.
- G19-179 closes the module only when this matrix, implementation, public Javadocs, support wording,
  examples, migrations, packages and evidence agree.
