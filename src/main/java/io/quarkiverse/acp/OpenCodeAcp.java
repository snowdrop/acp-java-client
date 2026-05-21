package io.quarkiverse.acp;

import io.quarkiverse.agentclientprotocol.sdk.client.AcpClient;
import io.quarkiverse.agentclientprotocol.sdk.client.AcpSyncClient;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.AgentParameters;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
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

    private static final Logger logger = LoggerFactory.getLogger(OpenCodeAcp.class);

    /** Default prompt used when no arguments are provided. */
    private static final String DEFAULT_PROMPT = "Say Hello in 5 languages";

    /** Default model used when no {@code -Dmodel=...} is provided. */
    /*
      ┌─────────────────────────────────┬─────────────────────────────────────┐
      │              Value              │                Name                 │
      ├─────────────────────────────────┼─────────────────────────────────────┤
      │ opencode/big-pickle             │ OpenCode Zen/Big Pickle             │
      ├─────────────────────────────────┼─────────────────────────────────────┤
      │ opencode/qwen3.6-plus-free      │ OpenCode Zen/Qwen3.6 Plus Free      │
      ├─────────────────────────────────┼─────────────────────────────────────┤
      │ opencode/nemotron-3-super-free  │ OpenCode Zen/Nemotron 3 Super Free  │
      ├─────────────────────────────────┼─────────────────────────────────────┤
      │ opencode/minimax-m2.5-free      │ OpenCode Zen/MiniMax M2.5 Free      │
      ├─────────────────────────────────┼─────────────────────────────────────┤
      │ opencode/deepseek-v4-flash-free │ OpenCode Zen/DeepSeek V4 Flash Free │
      └─────────────────────────────────┴─────────────────────────────────────┘
       Using as provider: Google Vertex AI
       model: google-vertex-anthropic/claude-opus-4-6@default
     */
    private static final String DEFAULT_MODEL = "opencode/big-pickle";

    /**
     * Entry point. Connects to the OpenCode ACP agent, sends a prompt, and prints
     * streamed session updates.
     *
     * @param args optional prompt text; if provided, all arguments are joined with spaces
     */
    public static void main(String[] args) {
        String sysPropPrompt = System.getProperty("prompt");
        String prompt = (sysPropPrompt != null && !sysPropPrompt.isEmpty())
                ? sysPropPrompt
                : (args.length > 0 ? String.join(" ", args) : DEFAULT_PROMPT);

        String sysPropModel = System.getProperty("model");
        String model = (sysPropModel != null && !sysPropModel.isEmpty())
                ? sysPropModel : DEFAULT_MODEL;

        String sysPropRequestTimeout = System.getProperty("requestTimeout");
        Duration requestTimeout = (sysPropRequestTimeout != null && !sysPropRequestTimeout.isEmpty())
                ? Duration.ofSeconds(Long.parseLong(sysPropRequestTimeout)) : Duration.ofSeconds(30);

        String sysPropPromptTimeout = System.getProperty("promptTimeout");
        Duration promptTimeout = (sysPropPromptTimeout != null && !sysPropPromptTimeout.isEmpty())
                ? Duration.ofSeconds(Long.parseLong(sysPropPromptTimeout)) : null;

        // 0. Check for required API key before starting
        checkAIApiKey();

        // 1. Configure agent parameters
        var params = AgentParameters.builder("opencode")
                .arg("acp")
                .build();

        // 2. Create transport
        var transport = new StdioAcpClientTransport(params);

        // 3. Build sync client with session update consumer
        try (AcpSyncClient client = AcpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .promptTimeout(promptTimeout)
                .sessionUpdateConsumer(notification -> {
                    String updateType = notification.meta() != null
                            ? (String) notification.meta().get("sessionUpdate") : null;
                    handleSessionUpdate(updateType, notification.update());
                })
                .build()) {

            // 4. Initialize — handshake with the agent
            var initResponse = client.initialize();
            var agentInfo = initResponse.agentInfo();
            String title = agentInfo.title();
            String connectedMsg = (title != null && !title.isEmpty())
                    ? String.format("Connected to agent: %s - v%s - %s", agentInfo.name(), agentInfo.version(), title)
                    : String.format("Connected to agent: %s - v%s", agentInfo.name(), agentInfo.version());
            logger.info(connectedMsg);
            logger.debug("Capabilities: {}", initResponse.agentCapabilities());
            logger.debug("Auth methods: {}", initResponse.authMethods());

            // 5. Create a session
            var session = client.newSession(new NewSessionRequest(".", List.of()));
            var sessionId = session.sessionId();
            logger.info("Session created: {}", sessionId);
            // 6. Set the model
            var configResponse = client.setConfigOption(
                    new SetSessionConfigOptionRequest("model", sessionId, model));
            if (configResponse.configOptions() != null) {
                configResponse.configOptions().stream()
                        .filter(opt -> "model".equalsIgnoreCase(opt.id()))
                        .findFirst()
                        .ifPresent(opt -> logger.info("Model: {}", opt.currentValue()));
            }

            // 7. Send a prompt
            logger.info("Sending prompt: {}", prompt);
            System.out.println("Here is the AI response:");
            var response = client.prompt(new PromptRequest(
                    List.of(new TextContent(prompt)),
                    sessionId
            ));

            logger.info("\nDone! Stop reason: {}", response.stopReason());

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
            logger.debug("[Update] null");
            return;
        }

        switch (update) {
            case ContentChunk chunk -> {
                if ("agent_thought_chunk".equals(updateType)) {
                    logger.info("[Thought] {}", extractText(chunk.content()));
                } else {
                    // Agent message text is always printed (primary output)
                    System.out.print(extractText(chunk.content()));
                }
            }
            case Plan plan -> {
                logger.info("[Plan] {} steps:", plan.entries().size());
                plan.entries().forEach(e -> logger.info("  - {} [{}]", e.content(), e.status()));
            }
            case ToolCall tool -> {
                logger.info("[ToolCall] {} ({}) - {}", tool.title(), tool.kind(), tool.status());
            }
            case ToolCallUpdate toolUpdate -> {
                logger.info("[ToolUpdate] {} - {}", toolUpdate.title(), toolUpdate.status());
            }
            case AvailableCommandsUpdate commands -> {
                logger.info("[Commands] Available:");
                commands.availableCommands()
                        .forEach(c -> logger.debug("  /{} - {}", c.name(), c.description()));
            }
            case ConfigOptionUpdate configUpdate -> {
                if (configUpdate.configOptions() != null) {
                    configUpdate.configOptions().stream()
                            .filter(opt -> "model".equalsIgnoreCase(opt.id()))
                            .findFirst()
                            .ifPresent(opt -> logger.info("Model changed: {}", opt.currentValue()));
                }
                logger.info("[Config] {}", configUpdate.configOptions());
            }
            case CurrentModeUpdate mode -> logger.info("[Mode] {}", mode.currentModeId());
            default -> {
                if ("usage_update".equals(updateType) && update instanceof Map<?,?> map) {
                    logger.info("[Usage] used={} size={} cost={}", map.get("used"), map.get("size"), map.get("cost"));
                } else {
                    logger.info("[Update] {}: {}", updateType, update);
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

    /**
     * Verify AI_API_KEY is set before attempting to connect.
     */
    private static void checkAIApiKey() {

        String apiKey = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        String location = System.getenv("VERTEX_LOCATION");
        String project = System.getenv("GOOGLE_CLOUD_PROJECT");

        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("""
                    ERROR: GOOGLE_APPLICATION_CREDENTIALS environment variable is not set.
                    """);
            System.exit(1);
        }

        if (location == null || location.isBlank()) {
            System.err.println("""
                    ERROR: VERTEX_LOCATION environment variable is not set.
                    """);
            System.exit(1);
        }

        if (project == null || project.isBlank()) {
            System.err.println("""
                    ERROR: GOOGLE_CLOUD_PROJECT environment variable is not set.
                    """);
            System.exit(1);
        }
    }
}
