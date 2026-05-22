# Java Library for Agent Client Protocol (ACP)

The [Agent Client Protocol](https://agentclientprotocol.com/) (ACP) is an open standard for communication between clients and AI coding agents. It defines a JSON-RPC 2.0-based protocol over stdio that lets clients initialize sessions, send prompts, receive streamed updates (thoughts, messages, tool calls, plans), and manage the agent lifecycle.

This project is a Java library for ACP, built with standard `java.util.concurrent` APIs (`CompletableFuture`, `ScheduledExecutorService`) for async operations and [Jackson](https://github.com/FasterXML/jackson) for JSON processing. It provides both synchronous and asynchronous APIs to interact with any ACP-compatible agent (e.g. [OpenCode](https://opencode.ai/)) using stdio.

The project implements the [ACP Schema Specification v1](https://agentclientprotocol.com/specification). The JSON schema definition is bundled at `schema/src/main/resources/schema/acp/v1/schema.json` and Java records are generated from it using `JSonSchemaGenerator` (a custom code generator included in the `schema` module). See [CONTRIBUTING.md](CONTRIBUTING.md) for details on regenerating schema classes.

## Project structure

The project is organized as a multi-module Maven build:

| Module   | Artifact ID     | Description                                                                                                      |
|----------|-----------------|------------------------------------------------------------------------------------------------------------------|
| `schema` | `acp-schema`    | ACP JSON Schema (`v1`), generated Java records/enums, and `JSonSchemaGenerator` code generator                   |
| `core`   | `acp-core` | ACP implementation library: `AcpClient`, `AcpAsyncClient`, `AcpSyncClient`, stdio transport. Depends on `schema` |
| `client` | `acp-client` | Picocli CLI (`AcpAgentCommand`), skills, and sandbox. Depends on `core`. Built as Quarkus uber-jar              |

## Prerequisites

- [JDK 21+](https://openjdk.org/)
- [Apache Maven 3.9+](https://maven.apache.org/)
- Any ACP-compatible agent: [OpenCode ACP](https://opencode.ai/docs/acp/), [Claude ACP agent](https://www.npmjs.com/package/@agentclientprotocol/claude-agent-acp), [Pi ACP](https://www.npmjs.com/package/pi-acp)
- (Optional) [JBang](https://www.jbang.dev/) for running the CLI via catalog

## Build

Compile the project and build the uber-jar:
```shell
mvn clean install
```

The uber-jar is produced at `client/target/acp-client-0.1.0-SNAPSHOT-runner.jar`.

## Usage

### Running with `java -jar`

```shell
# Default prompt: "Say Hello" with OpenCode agent
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar

# Custom prompt
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar --prompt "What is 6+6?"

# With a specific provider and model
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --provider anthropic-vertex-ai \
  --model claude-opus-4-6 \
  --agent-binary claude-agent-acp \
  --prompt "Say hello"
```

### Running with JBang

A [JBang catalog](https://www.jbang.dev/documentation/guide/latest/alias_catalogs.html) is provided at the project root. After building:

```shell
# Run from the project root using the local catalog
jbang acp-client --prompt "What is 6+6?"

# Or install the catalog for easier access
jbang catalog add --name acp .
jbang acp-client@acp --prompt "Say hello"
```

### Running with Quarkus dev mode

```shell
mvn quarkus:dev -pl client -Dquarkus.args="--prompt 'Say Hello'"
```

### Example output

```shell
11:11:57,492 INFO  [StdioAcpClientTransport] ACP agent starting
11:11:57,522 INFO  [StdioAcpClientTransport] ACP agent started
11:11:58,435 INFO  [AcpAgentCommand] Connected to the ACP agent: OpenCode - v1.15.4
11:11:58,613 INFO  [AcpAgentCommand] Session created: ses_1b631ae8bffegMSoAYKMCI6cUc
11:11:58,619 INFO  [AcpAgentCommand] [Commands] Available:
11:11:58,622 INFO  [AcpAgentCommand] Model: opencode/big-pickle
11:11:58,623 INFO  [AcpAgentCommand] Sending prompt: Say Hello
Here is the AI response:
Hello
11:12:00,661 INFO  [AcpAgentCommand] [Usage] used=8081 size=200000 cost={amount=0, currency=USD}
11:12:00,665 INFO  [AcpAgentCommand] Done! Stop reason: END_TURN
11:12:00,676 INFO  [StdioAcpClientTransport] ACP agent process stopped (exit code 143)
```

## CLI options

All options support environment variable fallback. Precedence: **CLI argument > environment variable > default value**.

| Option               | Env Variable          | Description                                                     | Default        |
|----------------------|-----------------------|-----------------------------------------------------------------|----------------|
| `-p`, `--prompt`     | `ACP_PROMPT`          | The prompt text to send to the agent                            | `Say Hello`    |
| `--provider`         | `ACP_PROVIDER`        | The provider name for env variable checks (see below)           | `opencode-zen` |
| `-m`, `--model`      | `ACP_MODEL`           | The model to use (see available models in session config)       |                |
| `--agent-binary`     | `ACP_AGENT_BINARY`    | The agent command (binary) to launch                            | `opencode`     |
| `--agent-args`       | `ACP_AGENT_ARGS`      | Comma-separated arguments passed to the agent command           | `acp`          |
| `--request-timeout`  | `ACP_REQUEST_TIMEOUT` | Timeout in seconds for steps: initialize, create session, etc.  | `30`           |
| `--prompt-timeout`   | `ACP_PROMPT_TIMEOUT`  | Timeout in seconds for prompt requests; 0 means no timeout      | `0`            |
| `--permission-mode`  | `ACP_PERMISSION_MODE` | How to respond to agent permission requests (see below)         | `allow_always` |
| `-h`, `--help`       |                       | Show help message and exit                                      |                |
| `-V`, `--version`    |                       | Print version info and exit                                     |                |

### Examples

```shell
# Using CLI arguments
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --prompt "Read the skills/dummy/SKILL.md instructions and say hello."

# Using environment variables
export ACP_PROVIDER=anthropic-vertex-ai
export ACP_MODEL=claude-opus-4-6
export ACP_AGENT_BINARY=claude-agent-acp
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --prompt "Execute the java-project-discovery skill."

# Analyze a java project
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --provider anthropic-vertex-ai \
  --model claude-opus-4-6 \
  --agent-binary claude-agent-acp \
  --prompt "Execute the **java-project-discovery** skill. Inspect the workspace root directory, determine the build setup, target Java version, and framework configurations, and return the structured JSON output."
```

## Providers

The required environment variables are automatically checked based on the `--provider` value. The client will exit with an error if any are missing.

| Provider (`--provider`)  | Environment variables                                                                                                   | Documentation                                                                        |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `opencode-zen` (default) | none                                                                                                                    | [OpenCode Zen](https://opencode.ai/docs/zen/)                                       |
| `google-vertex-ai`       | `GOOGLE_APPLICATION_CREDENTIALS`, `VERTEX_LOCATION`, `GOOGLE_CLOUD_PROJECT`                                             | [Google Vertex AI](https://opencode.ai/docs/providers/#google-vertex-ai)             |
| `anthropic-vertex-ai`    | `ANTHROPIC_VERTEX_PROJECT_ID`, `ANTHROPIC_MODEL`, `CLAUDE_CODE_USE_VERTEX`, `CLOUD_ML_REGION`                           | [Anthropic Vertex AI](https://docs.anthropic.com/en/docs/build-with-claude/vertex-ai)|
| `anthropic`              | `ANTHROPIC_API_KEY`                                                                                                     | [Anthropic](https://docs.anthropic.com/)                                             |
| `openai`                 | `OPENAI_API_KEY`                                                                                                        | [OpenAI](https://platform.openai.com/docs/)                                         |

> [!NOTE]
> By default, opencode agent picks up the free model available on [Zen](https://opencode.ai/docs/zen/): big-pickle

### opencode acp and Google Vertex AI provider

```shell
export GOOGLE_APPLICATION_CREDENTIALS=$HOME/.config/gcloud/application_default_credentials.json
export VERTEX_LOCATION=<google-location>
export GOOGLE_CLOUD_PROJECT=<google-project>

java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --agent-binary opencode \
  --agent-args acp \
  --provider google-vertex-ai \
  --model "google-vertex-anthropic/claude-opus-4-6@default"
```

### claude acp and Google Vertex AI provider

```shell
export CLOUD_ML_REGION=<google-location>
export CLAUDE_CODE_USE_VERTEX=1
export ANTHROPIC_VERTEX_PROJECT_ID=<google-project>

java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --agent-binary claude-agent-acp \
  --provider anthropic-vertex-ai \
  --model claude-opus-4-6
```

### pi acp

```shell
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --agent-binary pi-acp \
  --prompt "Say Hello"
```

## Permissions

When an agent needs to perform a sensitive operation (e.g. writing a file, running a command), it sends a `session/request_permission` request. The client responds automatically based on the `--permission-mode` value:

| Mode           | Behavior                                            |
|----------------|-----------------------------------------------------|
| `allow_always` | Accept and remember the choice (default)            |
| `allow_once`   | Accept only this time                               |
| `reject_once`  | Reject only this time                               |
| `reject_always`| Reject and remember the choice                      |

Example:
```shell
java -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --permission-mode allow_once \
  --prompt "Create a Java HelloWorld class"
```

## Logging

The project uses Quarkus logging (backed by [JBoss Log Manager](https://github.com/jboss-logging/jboss-logmanager)). By default, only `INFO`-level messages are shown (connection status, prompt lifecycle). Session update details (thoughts, tool calls, plans, commands, usage) and protocol internals are logged at `DEBUG` or `TRACE` level.

Log levels are configured in `client/src/main/resources/application.properties`. You can also override them on the command line:

```shell
# Enable debug logging
java -Dquarkus.log.category.\"io.quarkiverse\".level=DEBUG \
  -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --prompt "Say Hello"

# Enable trace logging (raw JSON-RPC messages)
java -Dquarkus.log.category.\"io.quarkiverse\".level=TRACE \
  -jar client/target/acp-client-0.1.0-SNAPSHOT-runner.jar \
  --prompt "Say Hello"
```

### Log levels

| Level | What you see |
|-------|-------------|
| `INFO` (default) | Connected to agent, sending prompt, stop reason |
| `DEBUG` | + agent thoughts, tool calls, plans, commands, mode changes, usage, capabilities, session ID |
| `TRACE` | + raw JSON-RPC messages sent/received by the transport |
