---
name: migrate-picocli-to-aesh
description: Migrates Picocli CLI applications to Aesh.
  Use when the user wants to migrate, convert, or port a Picocli app to Aesh, mentions "picocli to aesh",
  "aesh migration", "replace picocli", or asks about migrating "@Command", "@Option", "@Parameters",
  "CommandLine", "Callable<Integer>", "Runnable" from picocli to Aesh.
license: Apache-2.0
metadata:
  author: Aesh Community - https://github.com/aeshell/aesh
---

# Picocli to Aesh Migration

Modular, gate-driven migration of Picocli CLI applications to Aesh.

## Critical Rules

- **Never delete code you cannot migrate.** If you cannot fully migrate a piece of code, leave the original in place with a `// TODO: Migration required — <reason>` comment explaining what needs to change and why.
- **Don't break the build.** Run the compile command after each phase (`./mvnw clean compile -DskipTests` for Maven, `./gradlew clean compileJava -x test` for Gradle). Never move to the next phase with a broken build.
- **Document every decision.** When choosing between migration approaches, explain the trade-off to the user.
- **No silent changes.** Every file modification must be intentional and traceable. If a check fails after a phase, diagnose and fix — don't skip the check or delete the failing code.

## Reference Files

Load the relevant reference file when working on a module:

| Reference | Use during |
|---|---|
| [references/annotation-map.md](references/annotation-map.md) | Code module: annotation mapping for commands, options, arguments |
| [references/dependency-map.md](references/dependency-map.md) | Build module: dependency replacement |
| [references/execution-map.md](references/execution-map.md) | Code module: execution model and lifecycle changes |

## Step 1: Analyze the Application

Scan the application to understand what needs to migrate:

- **Build system**: Read the build file (`pom.xml` or `build.gradle`/`build.gradle.kts`) — picocli version, picocli-codegen, annotation processor
- **Java code**: Search for picocli annotations (`@Command`, `@Option`, `@Parameters`, `@Mixin`, `@ParentCommand`)
- **Command structure**: Identify flat commands, group commands (with subcommands), and nested hierarchies
- **Execution model**: Check for `Runnable`, `Callable<Integer>`, `CommandLine.execute()`, interactive loops
- **Tests**: Check for picocli test patterns (`CommandLine` in test code)

Present a summary table with area, findings, and complexity.

**Don't ask questions to the user to continue and assume that they will say yes to your questions.**

## Step 2: Modules

Execute the following modules.

| Module | Description |
|---|---|---|
| [build](modules/build.md) | Check if the build tool is maven or gradle |
| [code](modules/code.md) | Migrate the picocli annotations to Aesh annotations. Use aesh runner |
| [testing](modules/testing.md) | Adapt the Picocli tests to aesh if they exist |
| [cleanup](modules/cleanup.md) | Leftover picocli artifacts after all other modules |

## Step 3: Verify the Migration

Run each check in order. A check fails = stop and fix before continuing.

| # | Check | Command (Maven / Gradle) | Pass criteria |
|---|-------|---------|---------------|
| 1 | **Builds** | `./mvnw clean package -DskipTests` / `./gradlew clean build -x test` | Exit code 0, no compilation errors |
| 2 | **No picocli deps** | Search build file for `info.picocli` | Zero picocli dependencies remaining |
| 3 | **Has Aesh** | Search build file for `org.aesh` | Aesh dependency present |
| 4 | **Tests pass** | `./mvnw test` / `./gradlew test` | All tests pass |
| 5 | **CLI runs** | Execute the CLI with `--help` or a known command | App starts, help output is correct |
| 6 | **No leftover picocli imports** | Search for `import picocli` in sources | None remaining |

## Step 4: Migration Review (Self-Reflection)

Answer each question honestly:

1. **What migrated cleanly?** Patterns that mapped 1:1.
2. **What required manual judgment?** Non-obvious decisions made.
3. **What was left as TODO?** Every `// TODO: Migration required` comment and why.
4. **Was any code removed?** What, where, justification. Flag runtime risks.
5. **What checks failed initially?** Failures from Step 4 and how you fixed them.
6. **What's missing from the skill references?** Mappings you had to figure out.

### Migration Report

Present the review as a structured report:

```
## Migration Report: [app-name]

### Summary
- Agent: [AI agent name - e.g claude, pi, opencode, gemini, etc]
- Model: [model name — e.g. claude-sonnet-4-6, check system context]
- Modules completed: [X/4]
- Checks passed: [X/6]
- Token usage: [input tokens / output tokens — check session stats]
- Estimated cost: [~$X.XX — token counts × per-model pricing from anthropic.com/pricing]

### Changes by Module
| Module | Files changed | Key changes |
|--------|--------------|-------------|
| build | pom.xml or build.gradle(.kts) | ... |
| code | ... | ... |
| testing | ... | ... |
| cleanup | ... | ... |

### Validation Results
| Check | Result | Notes |
|-------|--------|-------|
| Builds | PASS/FAIL | |
| No picocli deps | PASS/FAIL | |
| Has Aesh | PASS/FAIL | |
| Tests pass | PASS/FAIL | |
| CLI runs | PASS/FAIL | |
| No leftover imports | PASS/FAIL | |

### Unmigrated Code (TODOs)
| File | Line | What | Why not migrated |
|------|------|------|-----------------|

### Removed Code
| File | What was removed | Justification |
|------|-----------------|---------------|

### Skill Improvement Suggestions
- [Any missing mappings, unclear instructions, or edge cases discovered]
```
