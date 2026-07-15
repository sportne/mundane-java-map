package io.github.mundanej.map.architecture;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

final class NativeJniMetadataPolicy {
    private static final String COMPLETE_SNAPSHOT_SHA256 =
            "e157529154d77540417d081d988a153c5715cfe6081de5ba2cefa2c7533cddea";
    private static final Pattern BROAD_REGISTRATION =
            Pattern.compile(
                    "(?i)(?:all(?:declared|queried|public)|queryall(?:declared|public))"
                            + ".*(?:method|field|constructor|class).*|"
                            + ".*(?:method|field|constructor|class).*"
                            + "(?:all(?:declared|queried|public)|queryall(?:declared|public)).*");
    private static final Map<String, ClassSpec> RASTER_COMPATIBILITY =
            Map.of(
                    "com.sun.imageio.plugins.jpeg.JPEGImageReader",
                    new ClassSpec(
                            Set.of(),
                            Set.of(
                                    signature("readInputData", "byte[]", "int", "int"),
                                    signature("skipInputBytes", "long"),
                                    signature("warningOccurred", "int"),
                                    signature("warningWithMessage", "java.lang.String"),
                                    signature(
                                            "setImageData",
                                            "int",
                                            "int",
                                            "int",
                                            "int",
                                            "int",
                                            "byte[]"),
                                    signature("acceptPixels", "int", "boolean"),
                                    signature("passStarted", "int"),
                                    signature("passComplete"),
                                    signature("pushBack", "int"),
                                    signature("skipPastImage", "int"))),
                    "sun.awt.image.ByteComponentRaster",
                    new ClassSpec(
                            Set.of("data", "dataOffsets", "pixelStride", "scanlineStride", "type"),
                            Set.of()),
                    "javax.imageio.plugins.jpeg.JPEGHuffmanTable",
                    new ClassSpec(Set.of("lengths", "values"), Set.of()),
                    "javax.imageio.plugins.jpeg.JPEGQTable",
                    new ClassSpec(Set.of("qTable"), Set.of()));

    private NativeJniMetadataPolicy() {}

    static List<String> rasterCompatibilityViolations(String metadata) {
        List<String> violations = new ArrayList<>();
        Object parsed;
        try {
            parsed = new JsonReader(metadata).read();
        } catch (IllegalArgumentException failure) {
            return List.of("Invalid JNI JSON: " + failure.getMessage());
        }
        if (!(parsed instanceof List<?> entries)) {
            return List.of("JNI metadata root must be an array");
        }

        Map<String, ClassShape> classes = new LinkedHashMap<>();
        for (int index = 0; index < entries.size(); index++) {
            Object entry = entries.get(index);
            if (!(entry instanceof Map<?, ?> rawClass)) {
                violations.add("JNI class entry " + index + " must be an object");
                continue;
            }
            Map<String, Object> classObject =
                    stringObject(rawClass, "JNI class entry " + index, violations);
            rejectBroadFlags(classObject, "JNI class entry " + index, violations);
            String name = stringValue(classObject.get("name"));
            if (name == null) {
                violations.add("JNI class entry " + index + " must have one string name");
                continue;
            }
            ClassShape shape = classShape(name, classObject, violations);
            if (classes.putIfAbsent(name, shape) != null) {
                violations.add("Duplicate JNI class entry: " + name);
            }
        }

        for (Map.Entry<String, ClassSpec> expected : RASTER_COMPATIBILITY.entrySet()) {
            ClassShape actual = classes.get(expected.getKey());
            if (actual == null) {
                violations.add("Missing raster JNI compatibility class: " + expected.getKey());
                continue;
            }
            Set<String> expectedKeys =
                    expected.getValue().methods().isEmpty()
                            ? Set.of("name", "fields")
                            : Set.of("name", "methods");
            if (!actual.keys().equals(expectedKeys)) {
                violations.add(
                        "Unexpected raster JNI keys for "
                                + expected.getKey()
                                + ": "
                                + actual.keys());
            }
            if (!actual.fields().equals(expected.getValue().fields())) {
                violations.add(
                        "Unexpected raster JNI fields for "
                                + expected.getKey()
                                + ": "
                                + actual.fields());
            }
            if (!actual.methods().equals(expected.getValue().methods())) {
                violations.add(
                        "Unexpected raster JNI method signatures for "
                                + expected.getKey()
                                + ": "
                                + actual.methods());
            }
        }
        String snapshot =
                classes.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .map(entry -> entry.getKey() + '|' + entry.getValue().normalized())
                        .collect(java.util.stream.Collectors.joining("\n"));
        String actualSnapshot = sha256(snapshot);
        if (!actualSnapshot.equals(COMPLETE_SNAPSHOT_SHA256)) {
            violations.add(
                    "Unexpected complete JNI metadata snapshot: "
                            + actualSnapshot
                            + " instead of "
                            + COMPLETE_SNAPSHOT_SHA256);
        }
        return List.copyOf(violations);
    }

    private static ClassShape classShape(
            String className, Map<String, Object> classObject, List<String> violations) {
        Set<String> fields = new LinkedHashSet<>();
        Object rawFields = classObject.get("fields");
        if (rawFields != null) {
            if (!(rawFields instanceof List<?> fieldEntries)) {
                violations.add("JNI fields must be an array for " + className);
            } else {
                for (Object entry : fieldEntries) {
                    if (!(entry instanceof Map<?, ?> rawField)) {
                        violations.add("JNI field must be an object for " + className);
                        continue;
                    }
                    Map<String, Object> field =
                            stringObject(rawField, "JNI field for " + className, violations);
                    rejectBroadFlags(field, "JNI field for " + className, violations);
                    String fieldName = stringValue(field.get("name"));
                    if (fieldName == null) {
                        violations.add("JNI field must have one string name for " + className);
                    } else if (!fields.add(fieldName)) {
                        violations.add("Duplicate JNI field entry: " + className + '#' + fieldName);
                    }
                    if (!field.keySet().equals(Set.of("name"))) {
                        violations.add("Unexpected JNI field shape for " + className);
                    }
                }
            }
        }

        Set<MethodSignature> methods = new LinkedHashSet<>();
        Object rawMethods = classObject.get("methods");
        if (rawMethods != null) {
            if (!(rawMethods instanceof List<?> methodEntries)) {
                violations.add("JNI methods must be an array for " + className);
            } else {
                for (Object entry : methodEntries) {
                    if (!(entry instanceof Map<?, ?> rawMethod)) {
                        violations.add("JNI method must be an object for " + className);
                        continue;
                    }
                    Map<String, Object> method =
                            stringObject(rawMethod, "JNI method for " + className, violations);
                    rejectBroadFlags(method, "JNI method for " + className, violations);
                    String methodName = stringValue(method.get("name"));
                    List<String> parameters = stringList(method.get("parameterTypes"));
                    if (methodName == null || parameters == null) {
                        violations.add(
                                "JNI method must have a string name and parameterTypes for "
                                        + className);
                    } else {
                        MethodSignature signature = new MethodSignature(methodName, parameters);
                        if (!methods.add(signature)) {
                            violations.add(
                                    "Duplicate JNI method overload entry: "
                                            + className
                                            + '#'
                                            + signature);
                        }
                    }
                    if (!method.keySet().equals(Set.of("name", "parameterTypes"))) {
                        violations.add("Unexpected JNI method shape for " + className);
                    }
                }
            }
        }
        return new ClassShape(
                Set.copyOf(classObject.keySet()), Set.copyOf(fields), Set.copyOf(methods));
    }

    private static void rejectBroadFlags(
            Map<String, Object> object, String location, List<String> violations) {
        object.keySet().stream()
                .filter(key -> BROAD_REGISTRATION.matcher(key).matches())
                .forEach(
                        key ->
                                violations.add(
                                        "Broad JNI registration flag " + key + " in " + location));
    }

    private static Map<String, Object> stringObject(
            Map<?, ?> raw, String location, List<String> violations) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : raw.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                violations.add(location + " has a non-string key");
                continue;
            }
            result.put(key, entry.getValue());
        }
        return result;
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) {
            return null;
        }
        List<String> result = new ArrayList<>();
        for (Object element : values) {
            if (!(element instanceof String string)) {
                return null;
            }
            result.add(string);
        }
        return List.copyOf(result);
    }

    private static MethodSignature signature(String name, String... parameterTypes) {
        return new MethodSignature(name, List.of(parameterTypes));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException failure) {
            throw new AssertionError(failure);
        }
    }

    private record ClassSpec(Set<String> fields, Set<MethodSignature> methods) {}

    private record ClassShape(Set<String> keys, Set<String> fields, Set<MethodSignature> methods) {
        private String normalized() {
            return "keys="
                    + keys.stream().sorted().collect(java.util.stream.Collectors.joining(","))
                    + "|fields="
                    + fields.stream().sorted().collect(java.util.stream.Collectors.joining(","))
                    + "|methods="
                    + methods.stream()
                            .map(MethodSignature::toString)
                            .sorted()
                            .collect(java.util.stream.Collectors.joining(","));
        }
    }

    private record MethodSignature(String name, List<String> parameterTypes) {
        private MethodSignature {
            parameterTypes = List.copyOf(parameterTypes);
        }

        @Override
        public String toString() {
            return name + '(' + String.join(",", parameterTypes) + ')';
        }
    }

    private static final class JsonReader {
        private final String input;
        private int offset;

        private JsonReader(String input) {
            this.input = input;
        }

        private Object read() {
            Object value = value();
            whitespace();
            if (offset != input.length()) {
                fail("Trailing content");
            }
            return value;
        }

        private Object value() {
            whitespace();
            if (offset >= input.length()) {
                fail("Unexpected end of input");
            }
            return switch (input.charAt(offset)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            offset++;
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (take('}')) {
                return result;
            }
            do {
                whitespace();
                if (offset >= input.length() || input.charAt(offset) != '"') {
                    fail("Expected object key");
                }
                String key = string();
                whitespace();
                expect(':');
                if (result.containsKey(key)) {
                    fail("Duplicate object key " + key);
                }
                result.put(key, value());
                whitespace();
            } while (take(','));
            expect('}');
            return result;
        }

        private List<Object> array() {
            offset++;
            List<Object> result = new ArrayList<>();
            whitespace();
            if (take(']')) {
                return result;
            }
            do {
                result.add(value());
                whitespace();
            } while (take(','));
            expect(']');
            return result;
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (offset < input.length()) {
                char next = input.charAt(offset++);
                if (next == '"') {
                    return result.toString();
                }
                if (next == '\\') {
                    if (offset >= input.length()) {
                        fail("Unterminated escape");
                    }
                    char escape = input.charAt(offset++);
                    switch (escape) {
                        case '"', '\\', '/' -> result.append(escape);
                        case 'b' -> result.append('\b');
                        case 'f' -> result.append('\f');
                        case 'n' -> result.append('\n');
                        case 'r' -> result.append('\r');
                        case 't' -> result.append('\t');
                        case 'u' -> result.append(unicode());
                        default -> fail("Invalid escape");
                    }
                } else {
                    if (next < 0x20) {
                        fail("Unescaped control character");
                    }
                    result.append(next);
                }
            }
            fail("Unterminated string");
            return "";
        }

        private char unicode() {
            if (offset + 4 > input.length()) {
                fail("Incomplete unicode escape");
            }
            try {
                char value = (char) Integer.parseInt(input.substring(offset, offset + 4), 16);
                offset += 4;
                return value;
            } catch (NumberFormatException failure) {
                fail("Invalid unicode escape");
                return 0;
            }
        }

        private Object number() {
            int start = offset;
            while (offset < input.length()
                    && "-+0123456789.eE".indexOf(input.charAt(offset)) >= 0) {
                offset++;
            }
            if (start == offset) {
                fail("Expected value");
            }
            try {
                return Double.valueOf(input.substring(start, offset));
            } catch (NumberFormatException failure) {
                fail("Invalid number");
                return 0;
            }
        }

        private Object literal(String literal, Object value) {
            if (!input.startsWith(literal, offset)) {
                fail("Invalid literal");
            }
            offset += literal.length();
            return value;
        }

        private void whitespace() {
            while (offset < input.length() && Character.isWhitespace(input.charAt(offset))) {
                offset++;
            }
        }

        private boolean take(char expected) {
            if (offset < input.length() && input.charAt(offset) == expected) {
                offset++;
                return true;
            }
            return false;
        }

        private void expect(char expected) {
            if (!take(expected)) {
                fail("Expected " + expected);
            }
        }

        private void fail(String message) {
            throw new IllegalArgumentException(message + " at offset " + offset);
        }
    }
}
