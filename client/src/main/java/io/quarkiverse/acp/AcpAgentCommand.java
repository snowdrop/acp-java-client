package io.quarkiverse.acp;

import io.quarkiverse.agentclientprotocol.sdk.client.AcpClient;
import io.quarkiverse.agentclientprotocol.sdk.client.AcpSyncClient;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.AgentParameters;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.v1.*;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Picocli CLI command for any ACP-compatible agent (OpenCode, Claude, Pi, etc.).
 *
 * <p>Connects to an ACP agent over stdio, initializes a session,
 * sends a prompt, and streams session updates (thoughts, messages, tool calls, plans)
 * to the console.
 *
 * <p>Each option can also be set via an environment variable (shown in brackets).
 * Precedence: CLI argument &gt; environment variable &gt; default value.
 *
 * <p>Usage:
 * <pre>{@code
 * # Using the uber-jar
 * java -jar acp-client-runner.jar --prompt "What is 6+6?"
 *
 * # With a specific provider and model
 * java -jar acp-client-runner.jar \
 *   --provider anthropic-vertex-ai \
 *   --model claude-opus-4-6 \
 *   --agent-binary claude-agent-acp \
 *   --prompt "Say hello"
 * }</pre>
 */
@CommandLine.Command(
        name = "acp-client",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "CLI client for any ACP-compatible agent (OpenCode, Claude, Pi, etc.)"
)
public class AcpAgentCommand implements Runnable {

    private static final Logger logger = Logger.getLogger(AcpAgentCommand.class);

    /** Buffer for accumulating streamed thought chunks into a single log line. */
    private final StringBuilder thoughtBuffer = new StringBuilder();

    /** Tracks whether agent message text was printed, so we can add a newline before the next log line. */
    private volatile boolean messageOutputPending = false;

    @CommandLine.Option(names = {"-p", "--prompt"},
            description = "The prompt text to send to the agent [env: ACP_PROMPT]")
    String prompt;

    @CommandLine.Option(names = {"-m", "--model"},
            description = "The model to use (see available models in session config) [env: ACP_MODEL]")
    String model;

    @CommandLine.Option(names = {"--provider"},
            description = "The provider name: opencode-zen, google-vertex-ai, anthropic-vertex-ai, anthropic, openai [env: ACP_PROVIDER]")
    String provider;

    @CommandLine.Option(names = {"--agent-binary"},
            description = "The agent command (binary) to launch [env: ACP_AGENT_BINARY]")
    String acpAgentBinary;

    @CommandLine.Option(names = {"--agent-args"},
            description = "Comma-separated arguments passed to the agent command [env: ACP_AGENT_ARGS]")
    String acpAgentArgs;

    @CommandLine.Option(names = {"--request-timeout"},
            description = "Timeout in seconds for requests (initialize, create session, etc.) [env: ACP_REQUEST_TIMEOUT]")
    Integer requestTimeout;

    @CommandLine.Option(names = {"--prompt-timeout"},
            description = "Timeout in seconds for prompt requests; 0 means no timeout [env: ACP_PROMPT_TIMEOUT]")
    Integer promptTimeout;

    @CommandLine.Option(names = {"--permission-mode"},
            description = "How to respond to agent permission requests: allow_always, allow_once, reject_once, reject_always [env: ACP_PERMISSION_MODE]")
    String permissionMode;

    @Override
    public void run() {
        // Resolve options: CLI arg > env var > default
        prompt = resolveOption(prompt, "ACP_PROMPT", "Say Hello");
        model = resolveOption(model, "ACP_MODEL", null);
        provider = resolveOption(provider, "ACP_PROVIDER", "opencode-zen");
        acpAgentBinary = resolveOption(acpAgentBinary, "ACP_AGENT_BINARY", "opencode");
        acpAgentArgs = resolveOption(acpAgentArgs, "ACP_AGENT_ARGS", "acp");
        permissionMode = resolveOption(permissionMode, "ACP_PERMISSION_MODE", "allow_always");

        String reqTimeoutStr = resolveOption(
                requestTimeout != null ? requestTimeout.toString() : null,
                "ACP_REQUEST_TIMEOUT", "30");
        Duration reqTimeout = Duration.ofSeconds(Long.parseLong(reqTimeoutStr));

        String promptTimeoutStr = resolveOption(
                promptTimeout != null ? promptTimeout.toString() : null,
                "ACP_PROMPT_TIMEOUT", "0");
        long promptTimeoutSecs = Long.parseLong(promptTimeoutStr);
        Duration pTimeout = promptTimeoutSecs > 0 ? Duration.ofSeconds(promptTimeoutSecs) : null;

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
                .requestTimeout(reqTimeout)
                .promptTimeout(pTimeout)
                .sessionUpdateConsumer(notification -> {
                    String updateType = notification.meta() != null
                            ? (String) notification.meta().get("sessionUpdate") : null;
                    handleSessionUpdate(updateType, notification.update());
                })
                .permissionRequestHandler(request -> handlePermissionRequest(request, permMode))
                .build()) {

            // 4. Initialize - handshake with the agent
            var initResponse = client.initialize();
            var agentInfo = initResponse.agentInfo();
            String title = agentInfo.title();
            String connectedMsg = (title != null && !title.isEmpty())
                    ? String.format("Connected to the ACP agent: %s - v%s - %s", agentInfo.name(), agentInfo.version(), title)
                    : String.format("Connected to the ACP agent: %s - v%s", agentInfo.name(), agentInfo.version());
            logger.info(connectedMsg);
            logger.debugf("Capabilities: %s", initResponse.agentCapabilities());
            logger.debugf("Auth methods: %s", initResponse.authMethods());

            // 5. Create a session
            var session = client.newSession(new NewSessionRequest(System.getProperty("user.dir"), List.of()));
            var sessionId = session.sessionId();
            logger.infof("Session created: %s", sessionId);

            // 6. Set the model (only if explicitly provided)
            if (model != null && !model.isEmpty()) {
                var configResponse = client.setConfigOption(
                        new SetSessionConfigOptionRequest("model", sessionId, model));
                if (configResponse.configOptions() != null) {
                    configResponse.configOptions().stream()
                            .filter(opt -> "model".equalsIgnoreCase(opt.id()))
                            .findFirst()
                            .ifPresent(opt -> logger.infof("Model: %s", opt.currentValue()));
                }
            }

            // 7. Send a prompt
            logger.infof("Sending prompt: %s", prompt);
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
            logger.infof("Done! Stop reason: %s", response.stopReason());

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Resolves an option value with precedence: CLI arg &gt; env var &gt; default.
     *
     * @param cliValue     the value from the CLI option (may be null)
     * @param envVar       the environment variable name to check
     * @param defaultValue the default value if neither CLI nor env var is set
     * @return the resolved value
     */
    private static String resolveOption(String cliValue, String envVar, String defaultValue) {
        if (cliValue != null && !cliValue.isEmpty()) {
            return cliValue;
        }
        String envValue = System.getenv(envVar);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }
        return defaultValue;
    }

    /**
     * Handles a session update notification by dispatching on the update's runtime type.
     *
     * @param updateType the {@code sessionUpdate} discriminator string (e.g. {@code "agent_message_chunk"})
     * @param update     the deserialized update object
     */
    private void handleSessionUpdate(String updateType, Object update) {
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
                logger.infof("[Plan] %d steps:", plan.entries().size());
                plan.entries().forEach(e -> logger.infof("  - %s [%s]", e.content(), e.status()));
            }
            case ToolCall tool -> {
                logger.infof("[ToolCall] %s (%s) - %s", tool.title(), tool.kind(), tool.status());
            }
            case ToolCallUpdate toolUpdate -> {
                logger.infof("[ToolUpdate] %s - %s", toolUpdate.title(), toolUpdate.status());
            }
            case AvailableCommandsUpdate commands -> {
                logger.info("[Commands] Available:");
                commands.availableCommands()
                        .forEach(c -> logger.debugf("  /%s - %s", c.name(), c.description()));
            }
            case ConfigOptionUpdate configUpdate -> {
                if (configUpdate.configOptions() != null) {
                    configUpdate.configOptions().stream()
                            .filter(opt -> "model".equalsIgnoreCase(opt.id()))
                            .findFirst()
                            .ifPresent(opt -> logger.infof("Model changed: %s", opt.currentValue()));
                }
                logger.infof("[Config] %s", configUpdate.configOptions());
            }
            case CurrentModeUpdate mode -> logger.infof("[Mode] %s", mode.currentModeId());
            default -> {
                if ("usage_update".equals(updateType) && update instanceof Map<?,?> map) {
                    logger.infof("[Usage] used=%s size=%s cost=%s", map.get("used"), map.get("size"), map.get("cost"));
                } else {
                    logger.infof("[Update] %s: %s", updateType, update);
                }
            }
        }
    }

    /**
     * Flushes accumulated thought chunks as a single log line.
     */
    private void flushThoughts() {
        if (!thoughtBuffer.isEmpty()) {
            logger.debugf("[Thought] %s", thoughtBuffer.toString().strip());
            thoughtBuffer.setLength(0);
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
     * Handles a permission request from the agent by selecting the option
     * matching the configured permission mode.
     *
     * @param request        the permission request with available options
     * @param permissionMode the desired permission mode (e.g. {@code "allow_always"}, {@code "allow_once"})
     * @return the response with the selected option
     */
    private static RequestPermissionResponse handlePermissionRequest(RequestPermissionRequest request, String permissionMode) {
        var toolCall = request.toolCall();
        logger.infof("[Permission] %s requests: %s", toolCall.title(), toolCall.kind());

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

        logger.infof("[Permission] Responded with: %s", permissionMode);
        return new RequestPermissionResponse(new SelectedPermissionOutcome(selectedOptionId));
    }

    /**
     * Checks that the required environment variables are set for the given provider.
     * Exits with an error if any are missing.
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
            default -> logger.warnf("Unknown provider '%s', skipping env variable checks", provider);
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
