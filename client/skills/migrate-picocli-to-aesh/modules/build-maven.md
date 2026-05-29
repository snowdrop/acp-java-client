# Sub-module: Build System (Maven)

Maven-specific build migration steps. Called from [build.md](build.md).

## What to do

- [ ] Replace picocli dependency with Aesh
- [ ] Replace picocli-codegen annotation processor with aesh-processor (if present)
- [ ] Remove picocli shell extensions
- [ ] Update version properties
- [ ] Compile: `./mvnw clean compile -DskipTests`

## pom.xml Reference Snippets

**Remove** picocli dependencies:
```xml
<!-- DELETE these -->
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli</artifactId>
    <version>${picocli.version}</version>
</dependency>

<!-- DELETE if present -->
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli-codegen</artifactId>
    <version>${picocli.version}</version>
    <scope>provided</scope>
</dependency>

<!-- DELETE if present -->
<dependency>
    <groupId>info.picocli</groupId>
    <artifactId>picocli-shell-jline3</artifactId>
    <version>${picocli.version}</version>
</dependency>
```

Also remove any `annotationProcessorPaths` entry for `picocli-codegen` in `maven-compiler-plugin`:
```xml
<!-- DELETE this annotationProcessorPath -->
<annotationProcessorPath>
    <groupId>info.picocli</groupId>
    <artifactId>picocli-codegen</artifactId>
    <version>${picocli.version}</version>
</annotationProcessorPath>
```

**Add** Aesh dependencies:
```xml
<dependency>
    <groupId>org.aesh</groupId>
    <artifactId>aesh</artifactId>
    <version>${aesh.version}</version>
</dependency>

<!-- Optional: annotation processor for maximum startup performance -->
<dependency>
    <groupId>org.aesh</groupId>
    <artifactId>aesh-processor</artifactId>
    <version>${aesh.version}</version>
    <scope>provided</scope>
</dependency>
```

**Add** version property (use the latest stable Aesh release):
```xml
<properties>
    <aesh.version>LATEST</aesh.version>
</properties>
```

**Remove** picocli version properties:
```xml
<!-- DELETE -->
<picocli.version>...</picocli.version>
```
