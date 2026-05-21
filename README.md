# Java Client - Agent Client Protocol (ACP)

The [Agent Client Protocol](https://agentclientprotocol.com/) (ACP) is an open standard for communication between clients and AI coding agents. It defines a JSON-RPC 2.0-based protocol over stdio that lets clients initialize sessions, send prompts, receive streamed updates (thoughts, messages, tool calls, plans), and manage the agent lifecycle.

This project is a Java client library for ACP, built with [SmallRye Mutiny](https://smallrye.io/smallrye-mutiny/) for reactive/async operations and [Jackson](https://github.com/FasterXML/jackson) for JSON processing. It provides both synchronous and asynchronous APIs to interact with any ACP-compatible agent (e.g. [OpenCode](https://opencode.ai/)).

## Prerequisites

- [JDK 21+](https://openjdk.org/)
- [Apache Maven 3.9+](https://maven.apache.org/)
- Any ACP-compatible agent: [OpenCode](https://opencode.ai/), [Claude ACP agent](https://www.npmjs.com/package/@agentclientprotocol/claude-agent-acp), [Pi ACP](https://www.npmjs.com/package/pi-acp)

## Usage

Execute one of these commands to run some prompts: 
```shell
mvn clean package
mvn exec:exec
mvn exec:exec -Dprompt="What is 6+6?"
mvn exec:exec -Dprompt="What is 6+6?" -Dmodel="google-vertex-anthropic/claude-opus-4-6@default"
mvn exec:exec -Dprompt="Read the skills/dummy/SKILL.md instructions and say hello at the root of the project. Show the hello messages part of the response too."
mvn exec:exec -Dprompt="Create a Java HelloWorld class" -Dmodel="google-vertex-anthropic/claude-opus-4-6@default"
```

### Parameters

| Parameter          | Description                                                                | Default                    |
|--------------------|----------------------------------------------------------------------------|----------------------------|
| `-Dprompt`         | The prompt text to send to the agent                                       | `Say Hello in 5 languages` |
| `-Dmodel`          | The model to use (see available models in session config)                  | `opencode/big-pickle`      |
| `-Dprovider`       | The provider name for env variable checks (see below)                      | `opencode-zen`             |
| `-DacpAgentBinary` | The agent command (binary) to launch                                       | `opencode`                 |
| `-DacpAgentArgs`   | Comma-separated arguments passed to the agent command                      | `acp`                      |
| `-DrequestTimeout` | Timeout in seconds for short-lived RPC calls (initialize, session, config) | `30`                       |
| `-DpromptTimeout`  | Timeout in seconds for prompt requests; unset means no timeout             | no timeout                 |
| `-DlogLevel`       | Log level: `INFO`, `DEBUG`, `TRACE`, `WARNING`, `SEVERE`                   | `INFO`                     |
| `-DpermissionMode` | How to respond to agent permission requests (see below)                    | `allow_always`             |

Examples:
```shell
mvn exec:exec \
  -Dprompt="Read the skills/dummy/SKILL.md instructions and say hello at the root of the project. Show the hello messages part of the response too."

mvn exec:exec \
  -Dprovider="anthropic-vertex-ai" \
  -Dmodel="claude-opus-4-6" \
  -DacpAgentBinary="claude-agent-acp" \
  -Dprompt="Read the skills/dummy/SKILL.md instructions and say hello at the root of the project. Show the hello messages part of the response too."
```
and look within your terminal to the response that you got:
```shell
[INFO] --- exec:3.6.3:exec (default-cli) @ acp-client ---
19:00:32,320 INFO  [StdioAcpClientTransport] ACP agent starting
19:00:36,488 INFO  [StdioAcpClientTransport] ACP agent started
19:00:37,417 INFO  [OpenCodeAcp] Connected to agent: OpenCode - v1.15.4
19:00:37,601 INFO  [OpenCodeAcp] Session created: ses_1b9aafabaffe6MEp0se1ONNKdJ
19:00:37,601 INFO  [OpenCodeAcp] Sending prompt: Read the skills/dummy/SKILL.md instructions and say hello at the root of the project. Show the hello messages part of the response too.
Here is the AI response:
Created `HELLO.md` at the project root with 5 hello messages as instructed.

```
# Hello, World! 🌍

1. Hello, World!
2. Hello, Universe!
3. Hello, Galaxy!
4. Hello, Solar System!
5. Hello, Earth!
```19:00:56,158 INFO  [OpenCodeAcp] 
Done! Stop reason: END_TURN
19:00:56,168 INFO  [StdioAcpClientTransport] ACP agent process stopped (exit code 143)
[INFO] ------------------------------------------------------------------------
```
>[NOTE]
> By default, opencode agent picks up the free model available on [Zen](https://opencode.ai/docs/zen/): big-pickle

## Providers

The required environment variables are automatically checked based on the `-Dprovider` value. The client will exit with an error if any are missing.

| Provider (`-Dprovider`)  | Environment variables                                                                                                   | Documentation                                                                        |
|--------------------------|-------------------------------------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------------|
| `opencode-zen` (default) | none                                                                                                                    | [OpenCode Zen](https://opencode.ai/docs/zen/)                                       |
| `google-vertex-ai`       | `GOOGLE_APPLICATION_CREDENTIALS`, `VERTEX_LOCATION`, `GOOGLE_CLOUD_PROJECT`                                             | [Google Vertex AI](https://opencode.ai/docs/providers/#google-vertex-ai)             |
| `anthropic-vertex-ai`    | `ANTHROPIC_VERTEX_PROJECT_ID`, `ANTHROPIC_MODEL`, `CLAUDE_CODE_USE_VERTEX`, `CLOUD_ML_REGION`                           | [Anthropic Vertex AI](https://docs.anthropic.com/en/docs/build-with-claude/vertex-ai)|
| `anthropic`              | `ANTHROPIC_API_KEY`                                                                                                     | [Anthropic](https://docs.anthropic.com/)                                             |
| `openai`                 | `OPENAI_API_KEY`                                                                                                        | [OpenAI](https://platform.openai.com/docs/)                                         |

Example with Google Vertex AI:
```shell
export GOOGLE_APPLICATION_CREDENTIALS=~/.config/gcloud/application_default_credentials.json
export VERTEX_LOCATION=us-east1
export GOOGLE_CLOUD_PROJECT=my-project
mvn exec:exec -Dprovider="google-vertex-ai" -Dmodel="google-vertex-anthropic/claude-opus-4-6@default"
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
mvn exec:exec -DpermissionMode=allow_once -Dprompt="Create a Java HelloWorld class"
```

## Logging

The project uses [SLF4J](https://www.slf4j.org/) with [JBoss Log Manager](https://github.com/jboss-logging/jboss-logmanager) as the logging backend. By default, only `INFO`-level messages are shown (connection status, prompt lifecycle). Session update details (thoughts, tool calls, plans, commands, usage) and protocol internals are logged at `DEBUG` or `TRACE` level.

Log levels are configured in `src/main/resources/logging.properties`. You can also override them on the command line.

### Log levels

| Level | What you see |
|-------|-------------|
| `INFO` (default) | Connected to agent, sending prompt, stop reason |
| `DEBUG` | + agent thoughts, tool calls, plans, commands, mode changes, usage, capabilities, session ID |
| `TRACE` | + raw JSON-RPC messages sent/received by the transport |

### Enable DEBUG for everything

Edit `src/main/resources/logging.properties` and change:
```properties
logger.level=DEBUG
```
