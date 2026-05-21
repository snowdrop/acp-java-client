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
| `client` | `acp-client` | CLI example (`AcpAgentCli`), skills, and sandbox. Depends on `core`                                              |

## Prerequisites

- [JDK 21+](https://openjdk.org/)
- [Apache Maven 3.9+](https://maven.apache.org/)
- Any ACP-compatible agent: [OpenCode ACP](https://opencode.ai/docs/acp/), [Claude ACP agent](https://www.npmjs.com/package/@agentclientprotocol/claude-agent-acp), [Pi ACP](https://www.npmjs.com/package/pi-acp)

## Usage

Compile the project and run the `mvn exec:exec` command from the `client` module:
```shell
mvn clean install
mvn exec:exec -pl client                                   # Default prompt: Say Hello
mvn exec:exec -pl client -Dprompt="What is 6+6?"
```
and look within your terminal to the response that you got:
```shell
[INFO] --- exec:3.6.3:exec (default-cli) @ acp-client-cli ---
11:11:57,492 INFO  [StdioAcpClientTransport] ACP agent starting
11:11:57,522 INFO  [StdioAcpClientTransport] ACP agent started                                                                                                                                                   
11:11:58,435 INFO  [AcpAgentCli] Connected to the ACP agent: OpenCode - v1.15.4                                                                                                                                  
11:11:58,613 INFO  [AcpAgentCli] Session created: ses_1b631ae8bffegMSoAYKMCI6cUc                                                                                                                                 
11:11:58,619 INFO  [AcpAgentCli] [Commands] Available:                                                                                                                                                           
11:11:58,622 INFO  [AcpAgentCli] Model: opencode/big-pickle                                                                                                                                                      
11:11:58,623 INFO  [AcpAgentCli] Sending prompt: Say Hello                                                                                                                                                       
Here is the AI response:                                                                                                                                                                                         
Hello
11:12:00,661 INFO  [AcpAgentCli] [Usage] used=8081 size=200000 cost={amount=0, currency=USD}
11:12:00,665 INFO  [AcpAgentCli] Done! Stop reason: END_TURN                                                                                                                                                     
11:12:00,676 INFO  [StdioAcpClientTransport] ACP agent process stopped (exit code 143)                                                                                                                           
[INFO] ------------------------------------------------------------------------ 
```

### Parameters

| Parameter          | Description                                                    | Default        |
|--------------------|----------------------------------------------------------------|----------------|
| `-Dprompt`         | The prompt text to send to the agent                           | `Say Hello`    |
| `-Dprovider`       | The provider name for env variable checks (see below)          | `opencode-zen` |
| `-Dmodel`          | The model to use (see available models in session config)      | ``             |
| `-DacpAgentBinary` | The agent command (binary) to launch                           | `opencode`     |
| `-DacpAgentArgs`   | Comma-separated arguments passed to the agent command          | `acp`          |
| `-DrequestTimeout` | Timeout in seconds for steps: initialize, create session, etc  | `30`           |
| `-DpromptTimeout`  | Timeout in seconds for prompt requests; unset means no timeout | no timeout     |
| `-DlogLevel`       | Log level: `INFO`, `DEBUG`, `TRACE`, `WARNING`, `SEVERE`       | `INFO`         |
| `-DpermissionMode` | How to respond to agent permission requests (see below)        | `allow_always` |

The parameters must be passed to the maven command as such:
```shell
mvn exec:exec -pl client \
  -Dprompt="Read the skills/dummy/SKILL.md instructions and say hello at the root of the project. Show the hello messages part of the response too."

mvn exec:exec -pl client \
  -Dprovider="anthropic-vertex-ai" \
  -Dmodel="claude-opus-4-6" \
  -DacpAgentBinary="claude-agent-acp" \
  -Dprompt="Read the skills/dummy/SKILL.md instructions and say hello at the root of the project. Show the hello messages part of the response too."
```

## Providers

The required environment variables are automatically checked based on the `-Dprovider` value. The client will exit with an error if any are missing.

| Provider (`-Dprovider`)  | Environment variables                                                                                                   | Documentation                                                                        |
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
export GOOGLE_APPLICATION_CREDENTIALS=$home/.config/gcloud/application_default_credentials.json
export VERTEX_LOCATION=<google-location>
export GOOGLE_CLOUD_PROJECT=<google-project>

mvn exec:exec -pl client \
  -DacpAgentBinary="opencode" \
  -DacpAgentArgs="acp" \
  -Dprovider="google-vertex-ai" \
  -Dmodel="google-vertex-anthropic/claude-opus-4-6@default"
```

### claude acp and Google Vertex AI provider

```shell
export CLAUDE_ML_REGION=<google-location>
export CLAUDE_CODE_USE_VERTEX=1
export ANTHROPIC_VERTEX_PROJECT_ID=<google-project>

mvn exec:exec -pl client \
  -DacpAgentBinary="claude-agent-acp" \
  -Dprovider="anthropic-vertex-ai" \
  -Dmodel="claude-opus-4-6"
```

### pi acp

```shell
mvn exec:exec -pl client \
  -DacpAgentBinary="pi-acp" \
  -Dprompt="Say Hello"
```

## Permissions

When an agent needs to perform a sensitive operation (e.g. writing a file, running a command), it sends a `session/request_permission` request. The client responds automatically based on the `-DpermissionMode` value:

| Mode           | Behavior                                            |
|----------------|-----------------------------------------------------|
| `allow_always` | Accept and remember the choice (default)            |
| `allow_once`   | Accept only this time                               |
| `reject_once`  | Reject only this time                               |
| `reject_always`| Reject and remember the choice                      |

Example:
```shell
mvn exec:exec -pl client -DpermissionMode=allow_once -Dprompt="Create a Java HelloWorld class"
```

## Logging

The project uses [SLF4J](https://www.slf4j.org/) with [JBoss Log Manager](https://github.com/jboss-logging/jboss-logmanager) as the logging backend. By default, only `INFO`-level messages are shown (connection status, prompt lifecycle). Session update details (thoughts, tool calls, plans, commands, usage) and protocol internals are logged at `DEBUG` or `TRACE` level.

Log levels are configured in `client/src/main/resources/logging.properties`. You can also override them on the command line.

### Log levels

| Level | What you see |
|-------|-------------|
| `INFO` (default) | Connected to agent, sending prompt, stop reason |
| `DEBUG` | + agent thoughts, tool calls, plans, commands, mode changes, usage, capabilities, session ID |
| `TRACE` | + raw JSON-RPC messages sent/received by the transport |
