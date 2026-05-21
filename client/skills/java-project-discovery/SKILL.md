---
name: java-project-discovery
description: Analyzes the local workspace directory to identify the Java project's structural layout, build engine (Maven/Gradle), Java runtime version, and core framework dependencies without executing intrusive code.
---
# Skill: Java Project Discovery & Architectural Audit

## Purpose
Identify key project metrics to establish workspace context for the AI agent before initiating code modifications.

## Execution Protocol & Plan
1. **Locate Build Manifests:** Search the workspace root for `pom.xml`, `build.gradle`, or `build.gradle.kts`.
2. **Inspect Environment Context:** Parse file tokens to extract core coordinates, parent POMs, or plugins.
3. **Analyze Runtime Dependencies:** Search for ecosystem markers like Spring Boot, Quarkus, or Jakarta EE.
4. **Output Generation:** Return a structured JSON block mapping the technical stack.

## Expected Output Schema
Your final response must be contained entirely within a single markdown JSON code block following this exact template structure:
```json
{
  "buildEngine": "Maven | Gradle (Groovy) | Gradle (Kotlin) | Ant",
  "usesWrapper": true | false,
  "javaVersion": "String (e.g., 17, 21)",
  "coordinates": {
    "groupId": "String or null",
    "artifactId": "String or null",
    "version": "String or null"
  },
  "frameworks": ["String (e.g., Quarkus 3.15, Spring Boot 3.4)"],
  "multiModule": true | false,
  "modules": ["String paths of sub-modules if applicable"]
}