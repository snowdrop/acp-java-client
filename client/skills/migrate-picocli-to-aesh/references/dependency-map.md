# Picocli to Aesh Dependency Map

## Maven Dependencies

### Core Dependency

**Remove:**
```xml
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli</artifactId>
    <version>${picocli.version}</version>
</dependency>
```

**Add:**
```xml
<dependency>
    <groupId>org.aesh</groupId>
    <artifactId>aesh</artifactId>
    <version>${aesh.version}</version>
</dependency>
```

### Annotation Processor (Optional — for maximum startup performance)

**Remove:**
```xml
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli-codegen</artifactId>
    <version>${picocli.version}</version>
    <scope>provided</scope>
</dependency>
```

Also remove any `annotationProcessorPaths` entry for `picocli-codegen` in `maven-compiler-plugin`.

**Add:**
```xml
<dependency>
    <groupId>org.aesh</groupId>
    <artifactId>aesh-processor</artifactId>
    <version>${aesh.version}</version>
    <scope>provided</scope>
</dependency>
```

No code changes needed — the Aesh processor runs at compile time and the runtime auto-detects generated metadata.

### Shell Extensions

| Picocli | Aesh | Notes |
|---|---|---|
| `picocli-shell-jline3` | Built into `aesh` | Aesh has native readline/shell support |
| `picocli-jansi-graalvm` | Not needed | Aesh handles terminal natively |

### Quarkus Integration

| Picocli | Aesh | Notes |
|---|---|---|
| `quarkus-picocli` | `quarkus-aesh` (if available) or use Aesh directly | Check Quarkus extension catalog |

## Gradle Dependencies

### Core Dependency

**Remove:**
```groovy
implementation 'info.picocli:picocli:${picocliVersion}'
annotationProcessor 'info.picocli:picocli-codegen:${picocliVersion}'
```

**Add:**
```groovy
implementation 'org.aesh:aesh:${aeshVersion}'
compileOnly 'org.aesh:aesh-processor:${aeshVersion}'
annotationProcessor 'org.aesh:aesh-processor:${aeshVersion}'
```

## Version Properties

Define `aesh.version` as a build property. Use the latest stable Aesh release.

**Maven:**
```xml
<properties>
    <aesh.version>LATEST</aesh.version>
</properties>
```

**Gradle:**
```groovy
ext {
    aeshVersion = 'LATEST'
}
```
