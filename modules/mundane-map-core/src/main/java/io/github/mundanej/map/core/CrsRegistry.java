package io.github.mundanej.map.core;

import io.github.mundanej.map.api.CrsDefinition;
import io.github.mundanej.map.api.CrsException;
import io.github.mundanej.map.api.CrsMetadata;
import io.github.mundanej.map.api.CrsProblem;
import io.github.mundanej.map.api.Envelope;
import io.github.mundanej.map.api.Projection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Instance-owned immutable registry of exact CRS keys and direct operations. */
public final class CrsRegistry {
    private static final List<String> EPSG_4326_ALIASES =
            List.of("urn:ogc:def:crs:EPSG::4326", "http://www.opengis.net/def/crs/EPSG/0/4326");
    private static final List<String> EPSG_3857_ALIASES =
            List.of("urn:ogc:def:crs:EPSG::3857", "http://www.opengis.net/def/crs/EPSG/0/3857");

    private final Map<String, CrsDefinition> definitionsByKey;
    private final Map<OperationKey, CrsOperation> operations;

    private CrsRegistry(
            Map<String, CrsDefinition> definitionsByKey,
            Map<OperationKey, CrsOperation> operations) {
        this.definitionsByKey = Map.copyOf(definitionsByKey);
        this.operations = Map.copyOf(operations);
    }

    /** Returns an empty explicit registry builder. */
    public static Builder builder() {
        return new Builder();
    }

    /** Returns a builder pre-populated with the Level 1 definitions and Web Mercator operation. */
    public static Builder builderWithLevel1() {
        return builder()
                .registerDefinition(CrsDefinitions.EPSG_4326, EPSG_4326_ALIASES)
                .registerDefinition(CrsDefinitions.EPSG_3857, EPSG_3857_ALIASES)
                .registerProjection(new WebMercatorProjection());
    }

    /** Returns a fresh immutable Level 1 registry. */
    public static CrsRegistry level1() {
        return builderWithLevel1().build();
    }

    /** Resolves one exact canonical key or deliberately registered alias. */
    public CrsDefinition resolve(String exactKey) {
        Objects.requireNonNull(exactKey, "exactKey");
        CrsDefinition definition = definitionsByKey.get(exactKey);
        if (definition == null) {
            throw failure(
                    "CRS_REGISTRY_KEY_UNKNOWN",
                    "No coordinate reference system is registered for the exact key",
                    boundedKeyContext(exactKey));
        }
        return definition;
    }

    /** Resolves one direct directional operation or strict identity. */
    public CrsOperation operation(CrsDefinition source, CrsDefinition target) {
        CrsDefinition registeredSource = requireRegistered(source);
        CrsDefinition registeredTarget = requireRegistered(target);
        if (registeredSource.equals(registeredTarget)) {
            return CrsOperation.identity(registeredSource);
        }
        CrsOperation operation =
                operations.get(
                        new OperationKey(
                                registeredSource.canonicalIdentifier(),
                                registeredTarget.canonicalIdentifier()));
        if (operation == null) {
            throw failure(
                    "CRS_TRANSFORM_UNAVAILABLE",
                    "No direct coordinate operation is registered",
                    Map.of(
                            "sourceCrs",
                            registeredSource.canonicalIdentifier(),
                            "targetCrs",
                            registeredTarget.canonicalIdentifier()));
        }
        return operation;
    }

    /** Resolves a source-metadata operation without guessing missing or unknown definitions. */
    public CrsOperation operationFromMetadata(
            Optional<CrsMetadata> sourceMetadata, CrsDefinition target) {
        Objects.requireNonNull(sourceMetadata, "sourceMetadata");
        Objects.requireNonNull(target, "target");
        if (sourceMetadata.isEmpty()) {
            throw failure(
                    "CRS_METADATA_MISSING",
                    "A coordinate operation requires source CRS metadata",
                    Map.of("targetCrs", target.canonicalIdentifier()));
        }
        CrsMetadata metadata = sourceMetadata.orElseThrow();
        if (metadata.definition().isEmpty()) {
            throw failure(
                    "CRS_DEFINITION_UNKNOWN",
                    "Unknown CRS metadata does not authorize a coordinate operation",
                    Map.of("targetCrs", target.canonicalIdentifier()));
        }
        return operation(metadata.definition().orElseThrow(), target);
    }

    private CrsDefinition requireRegistered(CrsDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        CrsDefinition registered = definitionsByKey.get(definition.canonicalIdentifier());
        if (!definition.equals(registered)) {
            throw failure(
                    "CRS_DEFINITION_MISMATCH",
                    "The CRS definition does not exactly match its registered canonical definition",
                    Map.of("actualCrs", definition.canonicalIdentifier()));
        }
        return registered;
    }

    private static boolean contains(Envelope outer, Envelope inner) {
        return inner.minX() >= outer.minX()
                && inner.minY() >= outer.minY()
                && inner.maxX() <= outer.maxX()
                && inner.maxY() <= outer.maxY();
    }

    private static CrsException failure(String code, String message, Map<String, String> context) {
        return new CrsException(new CrsProblem(code, message, context));
    }

    private static Map<String, String> boundedKeyContext(String key) {
        return !key.isBlank() && key.length() <= 256 ? Map.of("key", key) : Map.of();
    }

    private record OperationKey(String source, String target) {}

    /** Single-use explicit registry builder. */
    public static final class Builder {
        private final Map<String, CrsDefinition> definitionsByKey = new LinkedHashMap<>();
        private final Map<String, Integer> definitionDeclarationIndexes = new LinkedHashMap<>();
        private final Map<OperationKey, CrsOperation> operations = new LinkedHashMap<>();
        private final Map<OperationKey, Integer> operationDeclarationIndexes =
                new LinkedHashMap<>();
        private int nextDefinitionDeclarationIndex;
        private int nextProjectionDeclarationIndex;
        private boolean consumed;

        private Builder() {}

        /** Registers a canonical definition and an exact alias list. */
        public Builder registerDefinition(CrsDefinition definition, List<String> exactAliases) {
            requireUsable();
            Objects.requireNonNull(definition, "definition");
            Objects.requireNonNull(exactAliases, "exactAliases");
            List<String> aliases = List.copyOf(exactAliases);
            ArrayList<String> keys = new ArrayList<>(aliases.size() + 1);
            keys.add(definition.canonicalIdentifier());
            keys.addAll(aliases);
            Map<String, Integer> newIndexes = new LinkedHashMap<>();
            for (int offset = 0; offset < keys.size(); offset++) {
                String key = Objects.requireNonNull(keys.get(offset), "key");
                requireKeyShape(key);
                int conflictingIndex = nextDefinitionDeclarationIndex + offset;
                Integer existingIndex = definitionDeclarationIndexes.get(key);
                if (existingIndex == null) {
                    existingIndex = newIndexes.get(key);
                }
                if (existingIndex != null) {
                    throw duplicateKey(key, existingIndex, conflictingIndex);
                }
                newIndexes.put(key, conflictingIndex);
            }
            newIndexes.forEach(
                    (key, index) -> {
                        definitionsByKey.put(key, definition);
                        definitionDeclarationIndexes.put(key, index);
                    });
            nextDefinitionDeclarationIndex += keys.size();
            return this;
        }

        /** Registers one strict reversible projection and both directional views. */
        public Builder registerProjection(Projection projection) {
            requireUsable();
            Objects.requireNonNull(projection, "projection");
            CrsDefinition source = requireEndpoint(projection.sourceCrs());
            CrsDefinition target = requireEndpoint(projection.targetCrs());
            if (source.equals(target)) {
                throw failure(
                        "CRS_DEFINITION_MISMATCH",
                        "A registered projection must have distinct endpoints",
                        Map.of("sourceCrs", source.canonicalIdentifier()));
            }
            if (!contains(source.coordinateDomain(), projection.sourceDomain())
                    || !contains(target.coordinateDomain(), projection.targetDomain())) {
                throw failure(
                        "CRS_DEFINITION_MISMATCH",
                        "Projection domains must be contained by their endpoint definitions",
                        Map.of(
                                "sourceCrs",
                                source.canonicalIdentifier(),
                                "targetCrs",
                                target.canonicalIdentifier()));
            }
            OperationKey forwardKey =
                    new OperationKey(source.canonicalIdentifier(), target.canonicalIdentifier());
            OperationKey inverseKey =
                    new OperationKey(target.canonicalIdentifier(), source.canonicalIdentifier());
            int declarationIndex = nextProjectionDeclarationIndex;
            requireAvailableOperation(forwardKey, declarationIndex);
            requireAvailableOperation(inverseKey, declarationIndex);
            operations.put(forwardKey, CrsOperation.forward(projection));
            operations.put(inverseKey, CrsOperation.inverse(projection));
            operationDeclarationIndexes.put(forwardKey, declarationIndex);
            operationDeclarationIndexes.put(inverseKey, declarationIndex);
            nextProjectionDeclarationIndex++;
            return this;
        }

        /** Consumes this builder and returns an immutable isolated registry. */
        public CrsRegistry build() {
            requireUsable();
            consumed = true;
            return new CrsRegistry(definitionsByKey, operations);
        }

        private static void requireKeyShape(String key) {
            if (key.isBlank() || key.length() > 256) {
                throw new IllegalArgumentException(
                        "CRS registry keys must be non-blank and at most 256 characters");
            }
        }

        private static CrsException duplicateKey(
                String key, Integer existingIndex, int conflictingIndex) {
            return failure(
                    "CRS_REGISTRY_KEY_DUPLICATE",
                    "A CRS canonical key or alias is already registered",
                    Map.of(
                            "conflictingDeclarationIndex",
                            Integer.toString(conflictingIndex),
                            "existingDeclarationIndex",
                            Integer.toString(
                                    Objects.requireNonNull(existingIndex, "existingIndex")),
                            "key",
                            key));
        }

        private CrsDefinition requireEndpoint(CrsDefinition definition) {
            Objects.requireNonNull(definition, "definition");
            CrsDefinition registered = definitionsByKey.get(definition.canonicalIdentifier());
            if (!definition.equals(registered)) {
                throw failure(
                        "CRS_DEFINITION_MISMATCH",
                        "Projection endpoint is not exactly registered",
                        Map.of("actualCrs", definition.canonicalIdentifier()));
            }
            return registered;
        }

        private void requireAvailableOperation(OperationKey key, int conflictingIndex) {
            Integer existingIndex = operationDeclarationIndexes.get(key);
            if (existingIndex != null) {
                throw failure(
                        "CRS_REGISTRY_TRANSFORM_DUPLICATE",
                        "A direct CRS operation is already registered",
                        Map.of(
                                "conflictingDeclarationIndex",
                                Integer.toString(conflictingIndex),
                                "existingDeclarationIndex",
                                Integer.toString(existingIndex),
                                "sourceCrs",
                                key.source(),
                                "targetCrs",
                                key.target()));
            }
        }

        private void requireUsable() {
            if (consumed) {
                throw new IllegalStateException("CRS registry builder has already been consumed");
            }
        }
    }
}
