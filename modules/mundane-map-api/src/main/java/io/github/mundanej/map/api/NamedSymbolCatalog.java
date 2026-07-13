package io.github.mundanej.map.api;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** An immutable declaration-ordered exact-name symbol catalog. */
public final class NamedSymbolCatalog implements Iterable<NamedSymbol> {
    private final List<NamedSymbol> entries;
    private final Map<String, Symbol> byName;

    private NamedSymbolCatalog(List<NamedSymbol> entries) {
        Objects.requireNonNull(entries, "entries");
        this.entries = List.copyOf(entries);
        LinkedHashMap<String, Symbol> lookup = new LinkedHashMap<>();
        LinkedHashMap<String, Integer> firstIndexes = new LinkedHashMap<>();
        for (int index = 0; index < this.entries.size(); index++) {
            NamedSymbol entry = Objects.requireNonNull(this.entries.get(index), "entry");
            Integer first = firstIndexes.putIfAbsent(entry.name(), index);
            if (first != null) {
                LinkedHashMap<String, String> context = new LinkedHashMap<>();
                context.put("name", entry.name());
                context.put("firstIndex", Integer.toString(first));
                context.put("duplicateIndex", Integer.toString(index));
                throw new SymbolException(
                        SymbolException.CATALOG_DUPLICATE,
                        "Symbol catalog contains a duplicate name",
                        context);
            }
            lookup.put(entry.name(), entry.symbol());
        }
        byName = Map.copyOf(lookup);
    }

    /** Creates a catalog from declaration-ordered entries. */
    public static NamedSymbolCatalog of(List<NamedSymbol> entries) {
        return new NamedSymbolCatalog(entries);
    }

    /** Returns the declaration-ordered immutable entries. */
    public List<NamedSymbol> entries() {
        return entries;
    }

    /** Returns the number of entries. */
    public int size() {
        return entries.size();
    }

    /** Finds an exact valid name without fallback. */
    public Optional<Symbol> find(String name) {
        return Optional.ofNullable(byName.get(NamedSymbol.requireName(name)));
    }

    /** Requires an exact valid name without fallback. */
    public Symbol require(String name) {
        String validName = NamedSymbol.requireName(name);
        Symbol symbol = byName.get(validName);
        if (symbol == null) {
            throw new SymbolException(
                    SymbolException.CATALOG_MISSING,
                    "Symbol catalog name is missing",
                    Map.of("name", validName));
        }
        return symbol;
    }

    @Override
    public Iterator<NamedSymbol> iterator() {
        return entries.iterator();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof NamedSymbolCatalog catalog && entries.equals(catalog.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "NamedSymbolCatalog" + entries;
    }
}
