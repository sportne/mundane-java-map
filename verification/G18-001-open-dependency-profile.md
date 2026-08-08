# G18-001 open dependency profile

Review date: 2026-08-08

This is the approved dependency and mechanism inventory for the reusable Vaadin adapter. Resolution
used Gradle 9.5.1 on Java 21.0.11 against Maven Central with
`platform("com.vaadin:vaadin-bom:25.2.3")` and the sole direct adapter dependency
`com.vaadin:flow-server`. The BOM deliberately resolves Flow 25.2.4. No `com.vaadin:vaadin`
aggregate, Flow UI component set, Spring integration, Hilla runtime, Vaadin Map, TestBench,
commercial kit, or browser map engine is accepted.

## Resolved adapter production graph

| Coordinate | SHA-256 of JAR | License | Runtime role |
| --- | --- | --- | --- |
| `com.vaadin:flow-server:25.2.4` | `0bc39676491e2c5e32e752b89c4491ad7a3e00be08ec6aa460538f372e042ebb` | Apache-2.0 | Flow component, element, event, lifecycle, and local frontend-resource integration |
| `com.vaadin:flow-push:25.2.4` | `ca44d7d4a27e819708643c65ecfc133c12afeefbe679a6d4f6a312543b26c48b` | Apache-2.0 | Flow transport classes and bundled push client; G18 does not use push for animation frames |
| `com.vaadin.external.atmosphere:atmosphere-runtime:3.0.5.slf4jvaadin1` | `4dfa763a8c284bff0e1577969f98d976fdc76ad8876094a5c43d94baada41d98` | Apache-2.0 / CDDL-1.0 dual notice | Transitive Flow transport runtime |
| `com.vaadin.external:gentyref:1.2.0.vaadin1` | `7fbb7aaa015944f08c0fcf394e4e4e6854ee5a4714c10835adab8d0e4c15c46a` | Apache-2.0 | Generic-type reflection helper used by Flow |
| `tools.jackson.core:jackson-core:3.1.5` | `9431b7fa2673bbb618c11d865fe15e13222fd182a214ff998cb7e56afd8f35d2` | Apache-2.0 | Flow protocol parsing/generation internals; not part of the map protocol API |
| `tools.jackson.core:jackson-databind:3.1.5` | `3a2338d996fd3056791df8d335fa9ba8a62a706ed4245ecf81b3e583df37d08a` | Apache-2.0 | Flow internal value binding; private scene values remain closed and validated |
| `com.fasterxml.jackson.core:jackson-annotations:2.22` | `21ddb598807d3a51a876704eb979d9296e1c6a6f47ab1826ff88c6d6a127a2d0` | Apache-2.0 | Compatibility annotations used by Flow |
| `org.jsoup:jsoup:1.22.2` | `596785996d3c6df16f544c232d10a9a1d88783b2a4740cf5251cb616f2be704d` | MIT | Flow server-side HTML handling; never parses source attributes as HTML |
| `org.jspecify:jspecify:1.0.0` | `1fad6e6be7557781e4d33729d49ae1cdc8fdda6fe477bb0cc68ce351eafdfbab` | Apache-2.0 | Nullness annotations |
| `org.slf4j:slf4j-api:2.0.18` | `44508fd1576500688c790b190acdd16fec4f8c79a3e0b900afd70503cf055f55` | MIT | Logging facade; no backend is imposed by the adapter |

The dependency-management-only artifacts are `com.vaadin:vaadin-bom:25.2.3` (POM SHA-256
`5180af32975d24c7d338c63d4261f4b0aa4b920e39682dc9ee083e745eb27ac9`, Apache-2.0) and
`tools.jackson:jackson-bom:3.1.5` (POM SHA-256
`14fdbdcbb6a6023cadbffaadc878d66c5cf94f51c8162b9ffefc257e742412f4`, Apache-2.0). Provided servlet, annotation, OSGi, and Vaadin license
checker dependencies are not in the published adapter runtime graph; the consuming application
supplies its servlet container.

## Build tool and frontend inventory

The approved application plugin marker is
`com.vaadin.flow:com.vaadin.flow.gradle.plugin:25.2.4`, resolved only through the Gradle Plugin
Portal (POM SHA-256 `dfb9089d50cadcbde3a91a631499cf60528f9212ff616699cf1a5c08a6bf725e`).
Its implementation is `com.vaadin:flow-gradle-plugin:25.2.4` (JAR SHA-256
`a50e6eedd6c7baac405f9ce49128cbb4be3a2e61bc086ba19a4f116227d6410d`, identical on the Plugin
Portal and Maven Central, Apache-2.0). This Flow-only plugin avoids the aggregate `com.vaadin`
plugin's Hilla and Vaadin development/production bundles. Its complete 25-artifact resolved graph,
checksums, licenses, and roles are frozen in `G18-001-flow-plugin-inventory.tsv`; none is an adapter
publication dependency.

The adapter itself declares no npm dependency. Its only authored frontend input is the local
`META-INF/frontend/mundane-map-canvas.js` ES module and browser-standard Canvas APIs. The resolved
JAR graph contributes Flow's `META-INF/frontend/theme-util.js` and Flow Push's packaged
`META-INF/resources/VAADIN/static/push/vaadinPush*.js`; there is no `@vaadin/map`, OpenLayers,
Leaflet, MapLibre GL JS, remote sprite, remote font, or enabled analytics/telemetry behavior. Normal
`qualityGate` compiles and tests the adapter without running the Vaadin plugin, Node, a package
manager, or a browser download. For the application build, React routing, Tailwind, Workbox/PWA, and
all Vaadin UI component npm profiles are disabled. `G18-001-frontend-profile.json` is the exact
closed Flow default/Vaadin-router/Vite input profile (SHA-256
`a82c74284c1a524deb07f110ea2de247400c890dfd7b886866e82ca0dac3205d`). Its complete 512-entry npm
resolution, including exact versions, registry integrity hashes, licenses, and tarball URLs, is
frozen in `G18-001-frontend-build-inventory.tsv` (SHA-256
`2c1eb8b7072c9de17347a280c596874b34c704d8d97312c3bc8257d37a3bafff`). The resolution contains
only the approved SPDX set `0BSD`, `Apache-2.0`, `BSD-2-Clause`, `BSD-3-Clause`, `BlueOak-1.0.0`,
`CC-BY-4.0`, `ISC`, `MIT`, `MPL-2.0`, `Python-2.0`, and the recorded open compound expressions.
The unavoidable open `@vaadin/vaadin-usage-statistics:2.1.3` transitive package is installed with
scripts disabled during inventory and is disabled for every application build by the profile's
`"vaadin": {"disableUsageStatistics": true}` setting. Architecture/build checks require that
setting and reject its removal; no statistics collector runs or sends data.

When the application build is introduced it uses explicitly provisioned Node 24.14.0 with bundled
npm 11.9.0. The reviewed Linux x64 archive has SHA-256
`41cd79bb7877c81605a9e68ec4c91547774f46a40c67a17e34d7179ef11729df`; other supported build
platforms must use the corresponding signed Node release-manifest entry. It uses the frozen
inventory and download-disabled CI/offline settings. No task may allow Flow to
install Node or packages implicitly. Playwright and its explicitly installed browser binaries are
confined to the separate G18-060 lane.

## Services, scanning, and mechanisms

The resolved production JARs contain these service providers:

- Flow Server: `com.vaadin.experimental.FeatureFlagProvider` and
  `jakarta.servlet.ServletContainerInitializer`.
- Flow Push: packaged static push resources, with no service entry.
- Atmosphere: `jakarta.servlet.ServletContainerInitializer`, `org.atmosphere.inject.CDIProducer`,
  and `org.atmosphere.inject.Injectable`.
- Jackson Core and Databind: `tools.jackson.core.TokenStreamFactory` and
  `tools.jackson.databind.ObjectMapper`.

The build-only Roaster JDT JAR additionally declares exact services
`javax.tools.JavaCompiler`, `org.jboss.forge.roaster.spi.JavaParser`,
`org.jboss.forge.roaster.spi.FormatterProvider`,
`org.jboss.forge.roaster.spi.WildcardImportResolver`, and the shaded
`org.osgi.framework.connect.ConnectFrameworkFactory`, `org.osgi.framework.launch.FrameworkFactory`,
and `org.eclipse.equinox.plurl.Plurl` names under Roaster's relocated prefix. No service loads a map
source, portrayal, symbol catalog, or tool.

Flow performs framework reflection and explicit class/frontend-resource discovery; Atmosphere uses
servlet initialization and injection-provider discovery; Gentyref inspects Java generic types.
Those mechanisms are permitted only in this non-native Level 2 adapter/application boundary. The
architecture gate rejects them, Vaadin packages, and browser/private-protocol types from Level 1,
format, workspace, and AWT production modules. The adapter performs no classpath scanning of map
sources, catalogs, portrayals, or tools: all such values are registered or supplied explicitly.

## License and commercial-artifact decision

Every accepted artifact above is available under an approved open-source or open-content license
and requires no account, license key, paid service, runtime entitlement check, or source-data
subscription. The adapter and
example reject `com.vaadin:vaadin-map-flow`, `@vaadin/map`, every `vaadin-testbench` artifact,
Vaadin Charts, Grid Pro, Designer, Collaboration Engine, Kubernetes Kit, Observability Kit, SSO Kit,
AppSec Kit, Swing Kit, Copilot, and any coordinate whose license is not on the explicit allowlist.
Application graph verification fails before compilation if a prohibited group/artifact/package or
unknown license enters a resolvable configuration.

## Review disposition

Representative scene, viewport, stale-event, hostile-client, raster-resource, upload, detach, and
unsupported-symbol cases were reviewed against the approved protocol limits and diagnostic
precedence in the design. G18-010 through G18-061 retain the published dependency graph and ownership
order. The maintainer's 2026-08-08 instruction to complete G18-001 through G18-031 records approval
of this HITL profile.
