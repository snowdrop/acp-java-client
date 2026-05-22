# ACP Client - JBang Commands

Reference commands for running the ACP Java Client with each supported agent and provider.

## Prerequisites

- **Build the uber-jar** first with `mvn clean install`
- Load the environment variables with `dotenv -x .env` or `export KEY=VAR` or `set -x KEY VAR` using the appropriate mechanism of your shell.
- Have the acp-client installed: `jbang app install --name acp-client io.quarkiverse.ai:acp-java-client:0.1.0-SNAPSHOT:runner`
- Have the acp compatible installed. See [Agents and providers](README.md#agents-and-providers)

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
or use the default `claude-opus-4-6`
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
