package io.quarkiverse.acp.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class JSonSchemaGenerator {

    private static final String PACKAGE = "io.quarkiverse.agentclientprotocol.sdk.spec.schema";
    private static final Path OUTPUT_DIR = Path.of("src", "main", "java");

    private static JsonNode allDefs;

    public static void main(String[] args) throws IOException {
        ObjectMapper mapper = new ObjectMapper();

        try (InputStream is = JSonSchemaGenerator.class.getResourceAsStream("/schema/acp/v1/schema.json")) {
            JsonNode root = mapper.readTree(is);
            allDefs = root.get("$defs");

            Path packageDir = OUTPUT_DIR.resolve(PACKAGE.replace('.', '/'));
            Files.createDirectories(packageDir);

            Iterator<Map.Entry<String, JsonNode>> fields = allDefs.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String name = entry.getKey();
                JsonNode schema = entry.getValue();

                String javaCode = generateType(name, schema);
                if (javaCode != null) {
                    Files.writeString(packageDir.resolve(name + ".java"), javaCode);
                    System.out.println("Generated: " + name);
                }
            }
        }
    }

    private static String generateType(String name, JsonNode schema) {
        // String type aliases -> skip (just use String)
        if (isStringAlias(schema)) {
            return null;
        }
        // Integer type aliases -> skip (just use Integer)
        if (isIntegerAlias(schema)) {
            return null;
        }
        // String enum (has "enum" array)
        if (schema.has("enum") && "string".equals(getSimpleType(schema))) {
            return generateStringEnum(name, schema);
        }
        // oneOf with const values -> string enum
        if (schema.has("oneOf") && isConstEnum(schema.get("oneOf"))) {
            return generateOneOfEnum(name, schema);
        }
        // Object type -> record
        if ("object".equals(getSimpleType(schema)) && schema.has("properties")) {
            return generateRecord(name, schema);
        }
        // anyOf union types -> skip (use Object or sealed interface in future)
        return null;
    }

    // --- String Enum ---
    private static String generateStringEnum(String name, JsonNode schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(PACKAGE).append(";\n\n");
        sb.append("import com.fasterxml.jackson.annotation.JsonValue;\n\n");
        appendJavadoc(sb, schema, "");
        sb.append("public enum ").append(name).append(" {\n");

        JsonNode values = schema.get("enum");
        for (int i = 0; i < values.size(); i++) {
            String val = values.get(i).asText();
            String enumConst = toEnumConstant(val);
            sb.append("    ").append(enumConst).append("(\"").append(val).append("\")");
            sb.append(i < values.size() - 1 ? ",\n" : ";\n");
        }

        sb.append("\n    private final String value;\n\n");
        sb.append("    ").append(name).append("(String value) {\n");
        sb.append("        this.value = value;\n");
        sb.append("    }\n\n");
        sb.append("    @JsonValue\n");
        sb.append("    public String getValue() {\n");
        sb.append("        return value;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    // --- oneOf const enum (e.g., StopReason, ToolCallStatus) ---
    private static String generateOneOfEnum(String name, JsonNode schema) {
        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(PACKAGE).append(";\n\n");
        sb.append("import com.fasterxml.jackson.annotation.JsonValue;\n\n");
        appendJavadoc(sb, schema, "");
        sb.append("public enum ").append(name).append(" {\n");

        JsonNode oneOf = schema.get("oneOf");
        List<String> entries = new ArrayList<>();
        for (JsonNode option : oneOf) {
            if (option.has("const")) {
                String val = option.get("const").asText();
                String enumConst = toEnumConstant(val);
                entries.add("    " + enumConst + "(\"" + val + "\")");
            }
        }
        sb.append(String.join(",\n", entries)).append(";\n");

        sb.append("\n    private final String value;\n\n");
        sb.append("    ").append(name).append("(String value) {\n");
        sb.append("        this.value = value;\n");
        sb.append("    }\n\n");
        sb.append("    @JsonValue\n");
        sb.append("    public String getValue() {\n");
        sb.append("        return value;\n");
        sb.append("    }\n");
        sb.append("}\n");
        return sb.toString();
    }

    // --- Record ---
    private static String generateRecord(String name, JsonNode schema) {
        JsonNode properties = schema.get("properties");
        Set<String> required = new LinkedHashSet<>();
        if (schema.has("required")) {
            for (JsonNode r : schema.get("required")) {
                required.add(r.asText());
            }
        }

        // Collect all fields
        List<FieldInfo> allFields = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> props = properties.fields();
        while (props.hasNext()) {
            Map.Entry<String, JsonNode> prop = props.next();
            String propName = prop.getKey();
            JsonNode propSchema = prop.getValue();
            String javaType = resolveJavaType(propSchema);
            String fieldName = toJavaFieldName(propName);
            boolean isRequired = required.contains(propName);
            allFields.add(new FieldInfo(propName, fieldName, javaType, isRequired));
        }

        // Separate required and optional fields
        List<FieldInfo> requiredFields = allFields.stream().filter(f -> f.required).toList();
        List<FieldInfo> optionalFields = allFields.stream().filter(f -> !f.required).toList();

        // Collect imports
        Set<String> imports = new TreeSet<>();
        imports.add("com.fasterxml.jackson.annotation.JsonProperty");
        for (FieldInfo f : allFields) {
            if (f.javaType.startsWith("List<")) {
                imports.add("java.util.List");
            }
            if (f.javaType.startsWith("Map<") || f.javaType.equals("Map<String, Object>")) {
                imports.add("java.util.Map");
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("package ").append(PACKAGE).append(";\n\n");
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("\n");

        appendJavadoc(sb, schema, "");
        sb.append("public record ").append(name).append("(\n");

        // All fields as record components
        for (int i = 0; i < allFields.size(); i++) {
            FieldInfo f = allFields.get(i);
            sb.append("        @JsonProperty(\"").append(f.jsonName).append("\") ").append(f.javaType).append(" ").append(f.fieldName);
            sb.append(i < allFields.size() - 1 ? ",\n" : "") ;
        }
        sb.append(") {\n");

        // Convenience constructor with only required fields (if there are optional fields)
        if (!optionalFields.isEmpty() && !requiredFields.isEmpty()) {
            sb.append("    public ").append(name).append("(");
            for (int i = 0; i < requiredFields.size(); i++) {
                FieldInfo f = requiredFields.get(i);
                sb.append(f.javaType).append(" ").append(f.fieldName);
                if (i < requiredFields.size() - 1) sb.append(", ");
            }
            sb.append(") {\n");
            sb.append("        this(");
            for (int i = 0; i < allFields.size(); i++) {
                FieldInfo f = allFields.get(i);
                sb.append(f.required ? f.fieldName : "null");
                if (i < allFields.size() - 1) sb.append(", ");
            }
            sb.append(");\n");
            sb.append("    }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }

    // --- Type resolution ---
    private static String resolveJavaType(JsonNode propSchema) {
        // $ref
        if (propSchema.has("$ref")) {
            return refToClassName(propSchema.get("$ref").asText());
        }

        // allOf with single $ref
        if (propSchema.has("allOf")) {
            JsonNode allOf = propSchema.get("allOf");
            if (allOf.size() == 1 && allOf.get(0).has("$ref")) {
                String refType = refToClassName(allOf.get(0).get("$ref").asText());
                return resolveTypeAlias(refType);
            }
        }

        // anyOf with $ref + null -> nullable reference
        if (propSchema.has("anyOf")) {
            JsonNode anyOf = propSchema.get("anyOf");
            for (JsonNode option : anyOf) {
                if (option.has("$ref")) {
                    String refType = refToClassName(option.get("$ref").asText());
                    return resolveTypeAlias(refType);
                }
            }
        }

        // array
        if (propSchema.has("type") && isTypeOrContains(propSchema, "array")) {
            if (propSchema.has("items")) {
                String itemType = resolveJavaType(propSchema.get("items"));
                return "List<" + boxType(itemType) + ">";
            }
            return "List<Object>";
        }

        // simple types
        String type = getSimpleType(propSchema);
        if (type != null) {
            return switch (type) {
                case "string" -> "String";
                case "integer" -> "Integer";
                case "number" -> "Double";
                case "boolean" -> "Boolean";
                case "object" -> {
                    if (propSchema.has("additionalProperties")) {
                        yield "Map<String, Object>";
                    }
                    yield "Object";
                }
                default -> "Object";
            };
        }

        return "Object";
    }

    // Resolve type aliases (string/integer typedefs) to their Java primitive wrapper
    private static String resolveTypeAlias(String typeName) {
        if (allDefs.has(typeName)) {
            JsonNode def = allDefs.get(typeName);
            if (isStringAlias(def)) return "String";
            if (isIntegerAlias(def)) return "Integer";
        }
        return typeName;
    }

    private static String refToClassName(String ref) {
        // "#/$defs/Foo" -> "Foo"
        return ref.substring(ref.lastIndexOf('/') + 1);
    }

    private static String boxType(String type) {
        return switch (type) {
            case "int" -> "Integer";
            case "double" -> "Double";
            case "boolean" -> "Boolean";
            default -> type;
        };
    }

    // --- Helpers ---
    private static boolean isStringAlias(JsonNode schema) {
        return "string".equals(getSimpleType(schema)) && !schema.has("enum");
    }

    private static boolean isIntegerAlias(JsonNode schema) {
        return "integer".equals(getSimpleType(schema)) && !schema.has("enum");
    }

    private static boolean isConstEnum(JsonNode options) {
        for (JsonNode option : options) {
            if (option.has("const") && "string".equals(getSimpleType(option))) {
                return true;
            }
        }
        return false;
    }

    private static String getSimpleType(JsonNode schema) {
        if (!schema.has("type")) return null;
        JsonNode typeNode = schema.get("type");
        if (typeNode.isTextual()) return typeNode.asText();
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (!"null".equals(t.asText())) return t.asText();
            }
        }
        return null;
    }

    private static boolean isTypeOrContains(JsonNode schema, String type) {
        JsonNode typeNode = schema.get("type");
        if (typeNode.isTextual()) return type.equals(typeNode.asText());
        if (typeNode.isArray()) {
            for (JsonNode t : typeNode) {
                if (type.equals(t.asText())) return true;
            }
        }
        return false;
    }

    private static String toJavaFieldName(String jsonName) {
        if (jsonName.startsWith("_")) {
            return jsonName.substring(1);
        }
        return jsonName;
    }

    private static String toEnumConstant(String value) {
        // e.g., "end_turn" -> "END_TURN", "assistant" -> "ASSISTANT"
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase().replace("-", "_");
    }

    private static void appendJavadoc(StringBuilder sb, JsonNode schema, String indent) {
        if (schema.has("description")) {
            String desc = schema.get("description").asText();
            sb.append(indent).append("/**\n");
            for (String line : desc.split("\n")) {
                sb.append(indent).append(" * ").append(line).append("\n");
            }
            sb.append(indent).append(" */\n");
        }
    }

    private record FieldInfo(String jsonName, String fieldName, String javaType, boolean required) {}
}
