/**
 * Immutable bounded local map workspace persistence without live object serialization.
 *
 * <p>The module reads and writes one strict UTF-8 {@code .mmap.xml} version 1 grammar. A document
 * stores component-independent viewport state, ordered local source references, exact external
 * symbol names, and raster presentation. It never stores feature/raster data, credentials, runtime
 * limits, caches, selections, tools, edit history, or arbitrary Java objects.
 *
 * <p>{@link io.github.mundanej.map.workspace.WorkspaceLimits} bounds input/output bytes, logical
 * operation allocation, XML structure, values, aggregate text, and layer count. XML parsing rejects
 * DTDs, entities, processing instructions, external access, unknown grammar, and malformed UTF-8.
 * Stable {@link io.github.mundanej.map.workspace.WorkspaceProblem} values intentionally omit raw
 * XML, local paths, provider messages, credentials, and localized exception text.
 *
 * <p>{@link io.github.mundanej.map.workspace.WorkspaceFiles#read(java.nio.file.Path,
 * io.github.mundanej.map.workspace.WorkspaceLimits)} accepts only a regular non-symbolic-link local
 * file and snapshots it before parsing. {@link io.github.mundanej.map.workspace.WorkspaceOpener}
 * resolves portable relative references beneath the workspace's real parent using caller-declared
 * finite path profiles. These guards reduce accidental and hostile path traversal but do not turn a
 * trusted opener into a sandbox: applications own opener policy and must not reinterpret the
 * guarded path, inspect ambient credentials, or follow unvalidated references. Concurrent
 * filesystem replacement after the final identity check remains an operating-system boundary.
 *
 * <p>Source and catalog registration is explicit and instance-owned; there is no reflection,
 * classpath scanning, or automatic plugin discovery. Opening is all-or-nothing and cancellation is
 * observed between bounded phases. A successful {@link
 * io.github.mundanej.map.workspace.WorkspaceSession} owns every opened source and closes them once
 * in reverse layer order. Views must borrow those sources and close or detach their bindings before
 * the session is closed.
 *
 * <p>{@link io.github.mundanej.map.workspace.WorkspaceFiles#write(java.nio.file.Path,
 * io.github.mundanej.map.workspace.WorkspaceDocument,
 * io.github.mundanej.map.workspace.WorkspaceLimits)} emits canonical bytes to a private
 * same-directory temporary file, forces file content, and requires atomic replacement. It does not
 * silently fall back to a non-atomic move, create parent directories, preserve input formatting, or
 * provide migration, backup, locking, file watching, or durability beyond the completed atomic name
 * replacement.
 */
package io.github.mundanej.map.workspace;
