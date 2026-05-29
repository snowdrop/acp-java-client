# Sub-module: Build System (Gradle)

Gradle-specific build migration steps. Called from [build.md](build.md).

## What to do

- [ ] Replace picocli dependency with Aesh
- [ ] Replace picocli-codegen annotation processor with aesh-processor (if present)
- [ ] Remove picocli shell extensions
- [ ] Update version properties
- [ ] Compile: `./gradlew clean compileJava -x test`

## build.gradle Reference Snippets

**Remove** picocli dependencies:
```groovy
// DELETE these
implementation 'info.picocli:picocli:${picocliVersion}'
annotationProcessor 'info.picocli:picocli-codegen:${picocliVersion}'

// DELETE if present
implementation 'info.picocli:picocli-shell-jline3:${picocliVersion}'
```

**Add** Aesh dependencies:
```groovy
implementation 'org.aesh:aesh:${aeshVersion}'

// Optional: annotation processor for maximum startup performance
compileOnly 'org.aesh:aesh-processor:${aeshVersion}'
annotationProcessor 'org.aesh:aesh-processor:${aeshVersion}'
```

**Update** version properties:
```groovy
// REMOVE
ext {
    picocliVersion = '...'
}

// ADD
ext {
    aeshVersion = 'LATEST'  // Use the latest stable Aesh release
}
```

## build.gradle.kts Reference Snippets (Kotlin DSL)

**Remove:**
```kotlin
implementation("info.picocli:picocli:${picocliVersion}")
kapt("info.picocli:picocli-codegen:${picocliVersion}")
// or
annotationProcessor("info.picocli:picocli-codegen:${picocliVersion}")
```

**Add:**
```kotlin
implementation("org.aesh:aesh:${aeshVersion}")
compileOnly("org.aesh:aesh-processor:${aeshVersion}")
annotationProcessor("org.aesh:aesh-processor:${aeshVersion}")
```
