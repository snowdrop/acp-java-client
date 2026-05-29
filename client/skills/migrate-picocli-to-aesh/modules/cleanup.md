# Module: Cleanup

Remove leftover picocli artifacts that survived the per-module migration: orphaned imports, unused dependencies, and stale configuration.

## What to do

- [ ] Remove leftover picocli imports from all Java files
- [ ] Remove unused picocli dependencies from the build file (`pom.xml` or `build.gradle(.kts)`)
- [ ] Remove picocli-specific configuration files (e.g., GraalVM reflection config for picocli)
- [ ] Remove picocli AutoComplete code (if present)
- [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

## Leftover picocli imports

Search all Java files for remaining picocli imports:

```bash
grep -rn "import picocli" src/
```

For each hit:
- If the class has an Aesh equivalent → replace the import (use annotation-map.md)
- If it's an unused import → delete it
- If it cannot be migrated → leave with `// TODO: Migration required` comment

## Unused picocli dependencies

Check the build file for picocli dependencies that are no longer referenced in the code:

- `picocli` (core) → should already be replaced by `aesh`
- `picocli-codegen` → should already be replaced by `aesh-processor` (or removed)
- `picocli-shell-jline3` → remove (Aesh has built-in shell support)
- `picocli-jansi-graalvm` → remove (Aesh handles terminals natively)
- Any other `info.picocli` dependency → remove

## GraalVM reflection configuration

If the project has a GraalVM `reflect-config.json` or similar file with picocli-specific entries:

- If the Aesh annotation processor is being used → these entries are likely no longer needed. The processor generates direct `new` calls.
- Remove entries referencing `picocli.*` classes.
- Keep entries for application classes that still need reflection.

## AutoComplete code

If the project has picocli `AutoComplete` generation code:

```bash
grep -rn "AutoComplete\|picocli.*generate" src/
```

Remove it — Aesh auto-generates completion scripts via `--aesh-completion` and `--aesh-completion-install` flags with no extra code needed.
