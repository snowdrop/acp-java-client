---
name: gates-to-check
description: Analyze a Java application using a modular, gate-driven approach. 
  Trigger this skill when user asks to analyse a java application.
license: Apache-2.0
metadata:
  author: Quarkus Community - https://github.com/quarkusio/quarkus
---

# Gates to check

Modular, gate-driven instructions to analyze a java Application

## Step 1: Execute the Modules

- Execute the instructions of the modules according to the following Decision Gate Table
- Always log which Module and Gate check is evaluated and the status

### Decision Gate Table

For each module, evaluate whether it applies to this project. A module executes only when its gate is **PASS**.
Inspect the project to determine the gate result — do not rely on blind grep commands; use your understanding of the codebase.

| Module                       | Gate Check       | Gate Result                                                         |
|------------------------------|------------------|---------------------------------------------------------------------|
| [jdk](modules/module-1.md)   | JDK 21+ required | **ALWAYS** -- stop analyze if < 21                                  |
| [build](modules/module-2.md) | build system     | **PASS** if pom.xml or build.gradle(.kts) files; **SKIP** otherwise |

### Execution Protocol

```
FOR module IN [module-1, module-2]:

  1. EVALUATE — inspect the project for the gate condition
  2. DECIDE
     IF gate == ALWAYS → proceed to step 3
     IF gate == PASS   → proceed to step 3
     IF gate == SKIP   → log "Module {name}: SKIPPED — {reason}", mark checkbox, continue
  3. LOAD — read the module file
  5. EXECUTE — follow the module instructions, adapting to the chosen strategy
  6. LOG — mark checkbox as done
```

### Analysis Report

Present the review as a structured report:

```
## Analysis Report: [app-name]

### Summary
- JDK: version, Apache MAven or gradle: version
- Agent: [AI agent name - e.g claude, pi, opencode, gemini, etc]
- Model: [model name — e.g. claude-sonnet-4-6, check system context]
- Modules completed: [X/2]
- Checks passed: [X/2]
- Token usage: [input tokens / output tokens — check session stats]
- Estimated cost: [~$X.XX — token counts × per-model pricing from anthropic.com/pricing]
```