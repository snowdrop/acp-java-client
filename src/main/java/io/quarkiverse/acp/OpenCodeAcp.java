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
import java.util.logging.Level;
import java.util.logging.LogManager;

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

    /** Buffer for accumulating streamed thought chunks into a single log line. */
    private static final StringBuilder thoughtBuffer = new StringBuilder();

    /** Tracks whether agent message text was printed, so we can add a newline before the next log line. */
    private static volatile boolean messageOutputPending = false;

    /** Default prompt used when no arguments are provided. */
    private static final String DEFAULT_PROMPT = "Say Hello";

    /** Default model used when no {@code -Dmodel=...} is provided. */
    private static final String DEFAULT_MODEL = "opencode/big-pickle";

    /**
     * Entry point. Connects to the OpenCode ACP agent, sends a prompt, and prints
     * streamed session updates.
     *
     * @param args optional prompt text; if provided, all arguments are joined with spaces
     */
    public static void main(String[] args) {
        // Override log level if -DlogLevel=... is provided
        // Only applies to application loggers, not JDK internals
        String logLevel = System.getProperty("logLevel");
        if (logLevel != null && !logLevel.isEmpty()) {
            Level level = Level.parse(logLevel.toUpperCase());
            // Set handler level so it doesn't filter out messages
            java.util.logging.Logger rootLogger = LogManager.getLogManager().getLogger("");
            for (var handler : rootLogger.getHandlers()) {
                handler.setLevel(level);
            }
            // Only adjust application loggers
            java.util.logging.Logger.getLogger("io.quarkiverse").setLevel(level);
        }

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

        String sysPropAgent = System.getProperty("acpAgentBinary");
        String acpAgentBinary = (sysPropAgent != null && !sysPropAgent.isEmpty()) ? sysPropAgent : "opencode";
        String sysPropAgentArgs = System.getProperty("acpAgentArgs");
        String acpAgentArgs = (sysPropAgentArgs != null && !sysPropAgentArgs.isEmpty()) ? sysPropAgentArgs : "acp";
        String sysPropProvider = System.getProperty("provider");
        String provider = (sysPropProvider != null && !sysPropProvider.isEmpty()) ? sysPropProvider : "opencode-zen";
        String sysPropPermission = System.getProperty("permissionMode");
        String permissionMode = (sysPropPermission != null && !sysPropPermission.isEmpty()) ? sysPropPermission : "allow_always";

        // 0. Check for required env variables based on the provider
        checkProviderEnv(provider);

        // 1. Configure agent parameters
        var builder = AgentParameters.builder(acpAgentBinary);
        for (String a : acpAgentArgs.split(",")) {
            String trimmed = a.trim();
            if (!trimmed.isEmpty()) {
                builder.arg(trimmed);
            }
        }
        var params = builder.build();

        // 2. Create transport
        var transport = new StdioAcpClientTransport(params);

        // 3. Build sync client with session update consumer and permission handler
        final String permMode = permissionMode;
        try (AcpSyncClient client = AcpClient.sync(transport)
                .requestTimeout(requestTimeout)
                .promptTimeout(promptTimeout)
                .sessionUpdateConsumer(notification -> {
                    String updateType = notification.meta() != null
                            ? (String) notification.meta().get("sessionUpdate") : null;
                    handleSessionUpdate(updateType, notification.update());
                })
                .permissionRequestHandler(request -> handlePermissionRequest(request, permMode))
                .build()) {

            // 4. Initialize — handshake with the agent
            var initResponse = client.initialize();
            var agentInfo = initResponse.agentInfo();
            String title = agentInfo.title();
            String connectedMsg = (title != null && !title.isEmpty())
                    ? String.format("Connected to the ACP agent: %s - v%s - %s", agentInfo.name(), agentInfo.version(), title)
                    : String.format("Connected to the ACP agent: %s - v%s", agentInfo.name(), agentInfo.version());
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

            flushThoughts();
            if (messageOutputPending) {
                System.out.println();
                messageOutputPending = false;
            }
            logger.info("Done! Stop reason: {}", response.stopReason());

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

        // Flush buffered thoughts when a different update type arrives
        if (!"agent_thought_chunk".equals(updateType)) {
            flushThoughts();
        }

        // Add a newline after message output before the next log line
        if (!"agent_message_chunk".equals(updateType) && messageOutputPending) {
            System.out.println();
            messageOutputPending = false;
        }

        switch (update) {
            case ContentChunk chunk -> {
                if ("agent_thought_chunk".equals(updateType)) {
                    thoughtBuffer.append(extractText(chunk.content()));
                } else {
                    // Agent message text is always printed (primary output)
                    System.out.print(extractText(chunk.content()));
                    messageOutputPending = true;
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
    /**
     * Flushes accumulated thought chunks as a single log line.
     */
    private static void flushThoughts() {
        if (!thoughtBuffer.isEmpty()) {
            logger.debug("[Thought] {}", thoughtBuffer.toString().strip());
            thoughtBuffer.setLength(0);
        }
    }

    private static String extractText(Object content) {
        if (content instanceof Map<?,?> map) {
            Object text = map.get("text");
            return text != null ? text.toString() : content.toString();
        }
        return content != null ? content.toString() : "";
    }

    /**
     * Handles a permission request from the agent by selecting the option
     * matching the configured permission mode.
     *
     * @param request        the permission request with available options
     * @param permissionMode the desired permission mode (e.g. {@code "allow_always"}, {@code "allow_once"})
     * @return the response with the selected option
     */
    private static RequestPermissionResponse handlePermissionRequest(RequestPermissionRequest request, String permissionMode) {
        var toolCall = request.toolCall();
        logger.info("[Permission] {} requests: {}", toolCall.title(), toolCall.kind());

        // Find the option matching the configured permission mode
        String selectedOptionId = request.options().stream()
                .filter(o -> o.kind().getValue().equals(permissionMode))
                .findFirst()
                .map(PermissionOption::optionId)
                // Fallback: first allow option, then first option
                .orElseGet(() -> request.options().stream()
                        .filter(o -> o.kind() == PermissionOptionKind.ALLOW_ALWAYS
                                || o.kind() == PermissionOptionKind.ALLOW_ONCE)
                        .findFirst()
                        .map(PermissionOption::optionId)
                        .orElse(request.options().getFirst().optionId()));

        logger.info("[Permission] Responded with: {}", permissionMode);
        return new RequestPermissionResponse(new SelectedPermissionOutcome(selectedOptionId));
    }

    /**
     * Checks that the required environment variables are set for the given provider.
     * Exits with an error if any are missing.
     *
     * <p>Supported providers:
     * <ul>
     *   <li>{@code opencode-zen} &rarr; OpenCode Zen (no env vars required)</li>
     *   <li>{@code google-vertex-ai} &rarr; Google Vertex AI</li>
     *   <li>{@code anthropic-vertex-ai} &rarr; Anthropic via Vertex AI</li>
     *   <li>{@code anthropic} &rarr; Anthropic API</li>
     *   <li>{@code openai} &rarr; OpenAI API</li>
     * </ul>
     *
     * @param provider the provider name
     */
    private static void checkProviderEnv(String provider) {
        switch (provider) {
            case "google-vertex-ai" -> {
                requireEnv("GOOGLE_APPLICATION_CREDENTIALS", "Google Vertex AI");
                requireEnv("VERTEX_LOCATION", "Google Vertex AI");
                requireEnv("GOOGLE_CLOUD_PROJECT", "Google Vertex AI");
            }
            case "anthropic-vertex-ai" -> {
                requireEnv("ANTHROPIC_VERTEX_PROJECT_ID", "Anthropic Vertex AI");
                requireEnv("ANTHROPIC_MODEL", "Anthropic Vertex AI");
                requireEnv("CLAUDE_CODE_USE_VERTEX", "Anthropic Vertex AI");
                requireEnv("CLOUD_ML_REGION", "Anthropic Vertex AI");
            }
            case "anthropic" -> requireEnv("ANTHROPIC_API_KEY", "Anthropic");
            case "openai" -> requireEnv("OPENAI_API_KEY", "OpenAI");
            case "opencode-zen" -> { /* no env vars required */ }
            default -> logger.warn("Unknown provider '{}', skipping env variable checks", provider);
        }
    }

    private static void requireEnv(String varName, String provider) {
        String value = System.getenv(varName);
        if (value == null || value.isBlank()) {
            System.err.println("ERROR: " + varName + " environment variable is not set (required for " + provider + " provider).");
            System.exit(1);
        }
    }
}
