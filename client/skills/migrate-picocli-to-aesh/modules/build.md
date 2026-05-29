# Module: Build System

Migrate the build descriptor from picocli to Aesh dependencies.

## Gate condition

Detect the build tool by checking which files exist at the project root:

| File | Build tool | Sub-module |
|---|---|---|
| `pom.xml` | Maven | [build-maven.md](build-maven.md) |
| `build.gradle` or `build.gradle.kts` | Gradle | [build-gradle.md](build-gradle.md) |

Load [references/dependency-map.md](../references/dependency-map.md) before starting.

Then load and execute the matching sub-module above. After the sub-module completes, return here and continue with the Watch Out section below.

## What to do

- [ ] Replace picocli dependency with Aesh dependency
- [ ] Replace picocli annotation processor with Aesh annotation processor (if present)
- [ ] Remove picocli-specific plugins or codegen dependencies
- [ ] Remove picocli shell extensions (`picocli-shell-jline3`, `picocli-jansi-graalvm`, etc.)
- [ ] Compile: `./mvnw clean compile -DskipTests` (Maven) or `./gradlew clean compileJava -x test` (Gradle)

## Watch out

- **Annotation processor**: If the project used `picocli-codegen`, replace with `aesh-processor` for equivalent compile-time metadata generation. No code changes needed.
- **GraalVM native image**: If the project has a GraalVM native-image configuration for picocli reflection, the Aesh annotation processor may eliminate the need for it entirely.
- **Build tool wrapper**: If the project has `mvnw`/`gradlew`, always use `./mvnw` or `./gradlew` instead of system-installed `mvn` or `gradle`.
- **Version property**: Define `aesh.version` as a build property. Look up the latest stable Aesh release.
