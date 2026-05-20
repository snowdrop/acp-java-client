# Java Client - Agent Client Protocol (ACP)

The [Agent Client Protocol](https://agentclientprotocol.com/) (ACP) is an open standard for communication between clients and AI coding agents. It defines a JSON-RPC 2.0-based protocol over stdio that lets clients initialize sessions, send prompts, receive streamed updates (thoughts, messages, tool calls, plans), and manage the agent lifecycle.

This project is a Java client library for ACP, built with [SmallRye Mutiny](https://smallrye.io/smallrye-mutiny/) for reactive/async operations and [Jackson](https://github.com/FasterXML/jackson) for JSON processing. It provides both synchronous and asynchronous APIs to interact with any ACP-compatible agent (e.g. [OpenCode](https://opencode.ai/)).

## Prerequisites

- [JDK 21+](https://openjdk.org/)
- [Apache Maven 3.9+](https://maven.apache.org/)
- [OpenCode](https://opencode.ai/) (or any ACP-compatible agent)

## Usage

Execute the `mvn exec:exec` command: 
```shell
mvn clean package
mvn exec:exec
mvn exec:exec -Dprompt="What is 66+1000?"
mvn exec:exec -Dprompt="Read the skills/dummy/SKILL.md instructions and say hello at the root of the project. Show the hello messages part of the response too."
mvn exec:exec -Dprompt="Create a Java HelloWorld class"
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
