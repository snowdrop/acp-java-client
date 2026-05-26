# ACP Client - JBang Commands

Reference commands for running the ACP Java Client with each supported agent and provider.

## Table of contents

- [Prerequisites](#prerequisites)
- [OpenCode](#opencode)
  - [OpenCode + Zen](#opencode--zen-free-model-no-env-vars-needed)
  - [OpenCode + Vertex AI](#opencode--vertex-ai)
- [Claude Code](#claude-code)
  - [Claude Code + Vertex AI](#claude-code--vertex-ai)
- [Gemini CLI](#gemini-cli)
- [Pi](#pi)
  - [Pi + Vertex AI](#pi--vertex-ai)
- [Using --skill-path](#using---skill-path)
- [Using --backup and --workspace-name](#using---backup-and---workspace-name)
- [Using --log-level](#using---log-level)

## Prerequisites

- **Build the uber-jar** first with `mvn clean install`
- Load the environment variables with `dotenv -x .env` or `export KEY=VAR` or `set -x KEY VAR` using the appropriate mechanism of your shell.
- Have an ACP compatible client installed: pi, opencode, claude, etc. See [Agents and providers](README.md#agents-and-providers)
- Install the JBang Java `acp-client`: `jbang app install --name acp-client io.quarkiverse.ai:acp-java-client:0.1.0-SNAPSHOT:runner`

## OpenCode

### OpenCode + Zen (free model, no env vars needed)

```shell
acp-client \
  --prompt "Say Hello"
```

```shell
acp-client \
  --prompt "Read the skills/dummy/SKILL.md instructions and say hello at the root of the project."
```

### OpenCode + Vertex AI

```shell
export GOOGLE_APPLICATION_CREDENTIALS=$HOME/.config/gcloud/application_default_credentials.json
export VERTEX_LOCATION=<google-location>
export GOOGLE_CLOUD_PROJECT=<your-gcp-project>
```

You can specify the model 
```shell
acp-client \
  --provider vertex-ai \
  --model claude-opus-4-6 \
  --prompt "Say Hello"
```

or use the default `claude-opus-4-6`
```shell
acp-client \
  --provider vertex-ai \
  --prompt "Execute the **java-project-discovery** skill. Inspect the workspace root directory, determine the build setup, target Java version, and framework configurations, and return the structured JSON output."
```

## Claude Code

### Claude Code + Vertex AI

```shell
export ANTHROPIC_VERTEX_PROJECT_ID=<your-gcp-project>
export CLAUDE_CODE_USE_VERTEX=1
export CLOUD_ML_REGION=<google-location>
```

```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  --model claude-opus-4-6 \
  --prompt "Say Hello"
```
or use the default model: `claude-opus-4-6` using the provider `vertex-ai`
```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  --prompt "Read the skills/dummy/SKILL.md instructions and say hello at the root of the project."
```

```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  --prompt "Execute the **java-project-discovery** skill. Inspect the workspace root directory, determine the build setup, target Java version, and framework configurations, and return the structured JSON output."
```

## Gemini CLI

### Gemini CLI (uses Google Cloud SDK authentication)

```shell
acp-client \
  --agent gemini \
  --prompt "Say Hello"
```

```shell
acp-client \
  --agent gemini \
  --prompt "Read the skills/dummy/SKILL.md instructions and say hello at the root of the project."
```

## Pi

### Pi + Vertex AI

```shell
export GOOGLE_APPLICATION_CREDENTIALS=$HOME/.config/gcloud/application_default_credentials.json
export GOOGLE_CLOUD_PROJECT=<your-gcp-project>
export CLOUD_ML_REGION=<google-location>
```

```shell
acp-client \
  --agent pi \
  --provider vertex-ai \
  --prompt "Say Hello"
```

```shell
acp-client \
  --agent pi \
  --provider vertex-ai \
  --prompt "Read the skills/dummy/SKILL.md instructions and say hello at the root of the project."
```

## Using --skill-path

The `--skill-path` option (or `SKILL_PATH` env var) passes a skills folder as an additional directory to the agent session. This allows the agent to access skill definitions stored outside the current workspace.

```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  --skill-path /path/to/skills \
  --prompt "Execute the **java-project-discovery** skill."
```

Using an environment variable:
```shell
export SKILL_PATH=/path/to/skills
acp-client \
  --agent claude \
  --provider vertex-ai \
  --prompt "Execute the **java-project-discovery** skill."
```

## Using --backup and --workspace-name

The `--backup` option (`-b`) creates a copy of the workspace under `target/workdirs/` before the agent runs. The `--workspace-name` option (`-w`) controls the directory name used in the backup.

```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  --backup yes \
  --workspace-name my-todo-app \
  --prompt "Refactor the REST endpoints to use Quarkus REST."
```

To disable backup:
```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  --backup no \
  --prompt "Say Hello"
```

## Using --log-level

The `--log-level` option (`-l`) controls log verbosity. Use `DEBUG` to see agent thoughts, tool calls, and usage details. Use `TRACE` to see raw JSON-RPC messages.

```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  -l DEBUG \
  --prompt "Review the pom.xml and propose improvements"
```

```shell
acp-client \
  --agent claude \
  --provider vertex-ai \
  -l TRACE \
  --prompt "Say Hello"
```
