# Contributing

Thank you for your interest in contributing to the ACP Java Client.

## Prerequisites

- [JDK 21+](https://openjdk.org/)
- [Apache Maven 3.9+](https://maven.apache.org/)
- [Git](https://git-scm.com/)

## Building locally

Clone the repository and build:

```shell
git clone <repository-url>
cd acp
mvn clean package
```

## Testing locally

Run the client against an ACP-compatible agent:

```shell
# Default: OpenCode with Zen (no API key needed)
mvn exec:exec

# With a specific agent and prompt
mvn exec:exec -DacpAgentBinary="claude-agent-acp" -Dprompt="Say Hello"

# With debug logging to see protocol details
mvn exec:exec -DlogLevel=DEBUG
```

See [README.md](README.md) for all available parameters, providers, and logging options.

## Submitting a pull request

1. Fork the repository and create a branch from `main`:
   ```shell
   git checkout -b my-feature
   ```

2. Make your changes and verify the build compiles:
   ```shell
   mvn clean package
   ```

3. Test your changes against at least one ACP agent:
   ```shell
   mvn exec:exec -Dprompt="Say Hello"
   ```

4. Commit your changes with a clear message describing what and why:
   ```shell
   git commit -m "Add support for XYZ"
   ```

5. Push your branch and open a pull request against `main`.

## Generating Java classes from the ACP JSON Schema

The project includes `JSonSchemaGenerator`, a custom code generator that produces Java records and enums from the ACP JSON Schema specification. It replaces the need for external tools like jsonschema2pojo.

### Schema location

The ACP JSON schema is located at:

```
src/main/resources/schema/acp/v1/schema.json
```

### Generated output

Java classes are generated into a versioned package matching the schema version:

```
src/main/java/io/quarkiverse/agentclientprotocol/sdk/spec/schema/v1/
```

The package name `io.quarkiverse.agentclientprotocol.sdk.spec.schema.v1` corresponds to the `v1` schema directory. When a `v2` spec is released, classes would be generated into `.schema.v2`.

### How to regenerate

To regenerate the Java classes after updating the schema file, run `JSonSchemaGenerator`:

```shell
mvn compile exec:java -Dexec.mainClass=io.quarkiverse.acp.schema.JSonSchemaGenerator
```

This reads `src/main/resources/schema/acp/v1/schema.json` from the classpath and generates one `.java` file per schema type under the `io.quarkiverse.agentclientprotocol.sdk.spec.schema.v1` package.

### What it generates

| Schema construct               | Java output                                          |
|--------------------------------|------------------------------------------------------|
| Object with properties         | Java `record` with `@JsonProperty` annotations       |
| String enum (`"enum"` array)   | Java `enum` with `@JsonValue`                        |
| oneOf with const values        | Java `enum` with `@JsonValue`                        |
| String/integer type aliases    | Skipped (mapped to `String`/`Integer` at call sites) |
| anyOf union types              | Skipped (use `Object` at call sites)                 |

### Updating to a newer ACP spec version

1. Download the latest `schema.json` from the [ACP specification](https://agentclientprotocol.com/specification)
2. Place it under `src/main/resources/schema/acp/<version>/schema.json` (e.g. `v2`)
3. Update the `PACKAGE` constant in `JSonSchemaGenerator.java` to match the new version
4. Regenerate the classes:
   ```shell
   mvn compile exec:java -Dexec.mainClass=io.quarkiverse.acp.schema.JSonSchemaGenerator
   ```
5. Review the generated classes for any breaking changes
6. Check if any manually amended records (e.g. `SessionConfigOption`, `SelectedPermissionOutcome`) need their custom fields re-applied, as the generator will overwrite them

> **Note:** Some generated records have been manually amended to add fields missing from the schema or to include discriminator properties. After regeneration, check the git diff carefully and re-apply any manual changes.
