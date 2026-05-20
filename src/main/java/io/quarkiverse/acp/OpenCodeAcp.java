package io.quarkiverse.acp;

import io.quarkiverse.agentclientprotocol.sdk.client.AcpClient;
import io.quarkiverse.agentclientprotocol.sdk.client.AcpSyncClient;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.AgentParameters;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.*;

import java.util.List;
import java.util.Map;

/**
 * CLI example demonstrating the ACP (Agent Client Protocol) synchronous client.
 *
 * <p>Connects to the {@code opencode acp} agent over stdio, initializes a session,
 * sends a prompt, and streams session updates (thoughts, messages, tool calls, plans)
 * to the console.
 *
 * <p>Usage:
 * <pre>{@code
 * # Uses the default prompt
 * java OpenCodeAcp
 *
 * # Custom prompt passed as arguments
 * java OpenCodeAcp What is 66+1000?
 * }</pre>
 */
public class OpenCodeAcp {

    /** Default prompt used when no arguments are provided. */
    private static final String DEFAULT_PROMPT = "Say Hello in 5 languages";

    /**
     * Entry point. Connects to the OpenCode ACP agent, sends a prompt, and prints
     * streamed session updates.
     *
     * @param args optional prompt text; if provided, all arguments are joined with spaces
     */
    public static void main(String[] args) {
        String prompt = args.length > 0 ? String.join(" ", args) : DEFAULT_PROMPT;
        // 1. Configure agent parameters
        var params = AgentParameters.builder("opencode")
                .arg("acp")
                .build();

        // 2. Create transport
        var transport = new StdioAcpClientTransport(params);

        // 3. Build sync client with session update consumer
        try (AcpSyncClient client = AcpClient.sync(transport)
                .sessionUpdateConsumer(notification -> {
                    String updateType = notification.meta() != null
                            ? (String) notification.meta().get("sessionUpdate") : null;
                    handleSessionUpdate(updateType, notification.update());
                })
                .build()) {

            // 4. Initialize — handshake with the agent
            var initResponse = client.initialize();
            System.out.println("Connected to agent!");
            System.out.printf("Agent: %s v%s (%s)%n",
                    initResponse.agentInfo().name(),
                    initResponse.agentInfo().version(),
                    initResponse.agentInfo().title());
            System.out.println("Capabilities: " + initResponse.agentCapabilities());
            System.out.println("Auth methods: " + initResponse.authMethods());

            // 5. Create a session
            var session = client.newSession(new NewSessionRequest(".", List.of()));
            var sessionId = session.sessionId();
            System.out.println("Session created: " + sessionId);

            // 6. Send a prompt
            System.out.println("\nSending prompt...");
            var response = client.prompt(new PromptRequest(
                    List.of(new TextContent(prompt)),
                    sessionId
            ));

            System.out.println("\nDone! Stop reason: " + response.stopReason());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
        System.exit(0);
    }

    /**
     * Handles a session update notification by dispatching on the update's runtime type.
     *
     * @param updateType the {@code sessionUpdate} discriminator string (e.g. {@code "agent_message_chunk"})
     * @param update     the deserialized update object
     */
    private static void handleSessionUpdate(String updateType, Object update) {
        if (update == null) {
            System.out.println("[Update] null");
            return;
        }

        switch (update) {
            case ContentChunk chunk -> {
                if ("agent_thought_chunk".equals(updateType)) {
                    System.out.println("[Thought] " + extractText(chunk.content()));
                } else {
                    System.out.print(extractText(chunk.content()));
                }
            }
            case Plan plan -> {
                System.out.println("[Plan] " + plan.entries().size() + " steps:");
                plan.entries().forEach(e -> System.out.println("  - " + e.content() + " [" + e.status() + "]"));
            }
            case ToolCall tool -> {
                System.out.println("[ToolCall] " + tool.title() + " (" + tool.kind() + ") - " + tool.status());
            }
            case ToolCallUpdate toolUpdate -> {
                System.out.println("[ToolUpdate] " + toolUpdate.title() + " - " + toolUpdate.status());
            }
            case AvailableCommandsUpdate commands -> {
                System.out.println("[Commands] Available:");
                commands.availableCommands()
                        .forEach(c -> System.out.println("  /" + c.name() + " - " + c.description()));
            }
            case CurrentModeUpdate mode -> System.out.println("[Mode] " + mode.currentModeId());
            default -> {
                if ("usage_update".equals(updateType) && update instanceof Map<?,?> map) {
                    System.out.printf("%n%n[Usage] used=%s size=%s cost=%s%n",
                            map.get("used"), map.get("size"), map.get("cost"));
                } else {
                    System.out.println("[Update] " + updateType + ": " + update);
                }
            }
        }
    }

    /**
     * Extracts the text value from a content object.
     * The content is typically deserialized as a {@code Map} with a {@code "text"} key.
     *
     * @param content the raw content object from a {@link ContentChunk}
     * @return the text string, or an empty string if unavailable
     */
    private static String extractText(Object content) {
        if (content instanceof Map<?,?> map) {
            Object text = map.get("text");
            return text != null ? text.toString() : content.toString();
        }
        return content != null ? content.toString() : "";
    }
}
