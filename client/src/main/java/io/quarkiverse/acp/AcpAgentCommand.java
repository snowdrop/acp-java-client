package io.quarkiverse.acp;

import io.quarkiverse.agentclientprotocol.sdk.client.AcpClient;
import io.quarkiverse.agentclientprotocol.sdk.client.AcpSyncClient;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.AgentParameters;
import io.quarkiverse.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.v1.*;
import io.quarkus.picocli.runtime.annotations.TopCommand;
import org.jboss.logging.Logger;
import picocli.CommandLine;
import picocli.AutoComplete.GenerateCompletion;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * Picocli CLI command for any ACP-compatible agent (OpenCode, Claude, Pi, Gemini, etc.).
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
 * # Using a known agent (resolves binary and args automatically)
 * acp-client --agent claude --provider vertex-ai --model claude-opus-4-6 --prompt "Say hello"
 *
 * # Using a custom agent binary
 * acp-client --agent-binary my-agent --agent-args "serve" --prompt "Say hello"
 * }</pre>
 */
@TopCommand
@CommandLine.Command(
        name = "acp-client",
        mixinStandardHelpOptions = true,
        version = "0.1.0-SNAPSHOT",
        description = "CLI client for any ACP-compatible agent (OpenCode, Claude, Pi, Gemini, etc.)",
        subcommands = GenerateCompletion.class
)
public class AcpAgentCommand implements Runnable {

    private static final Logger logger = Logger.getLogger(AcpAgentCommand.class);

    // ── Agent registry ──────────────────────────────────────────────────────
    // Maps friendly agent names to their binary and default arguments.

    private record AgentDef(String binary, String args) {}

    private static final Map<String, AgentDef> AGENT_REGISTRY = Map.of(
            "opencode", new AgentDef("opencode", "acp"),
            "claude",   new AgentDef("claude-agent-acp", null),
            "pi",       new AgentDef("pi-acp", null),
            "gemini",   new AgentDef("gemini", "--acp")
    );

    // ── Provider env-var requirements per agent + provider ──────────────────
    // Key format: "agent:provider". Checked before launching the agent.

    private static final Map<String, List<String>> PROVIDER_ENV_VARS = Map.ofEntries(
            Map.entry("opencode:zen",       List.of()),
            Map.entry("opencode:vertex-ai", List.of("GOOGLE_APPLICATION_CREDENTIALS", "VERTEX_LOCATION", "GOOGLE_CLOUD_PROJECT")),
            Map.entry("claude:vertex-ai",   List.of("ANTHROPIC_VERTEX_PROJECT_ID", "CLAUDE_CODE_USE_VERTEX", "CLOUD_ML_REGION")),
            Map.entry("pi:vertex-ai",       List.of("GOOGLE_APPLICATION_CREDENTIALS", "GOOGLE_CLOUD_PROJECT", "CLOUD_ML_REGION")),
            Map.entry("gemini:vertex-ai",   List.of("GOOGLE_CLOUD_PROJECT"))
    );

    // ── Instance state ──────────────────────────────────────────────────────

    private final StringBuilder thoughtBuffer = new StringBuilder();
    private volatile boolean messageOutputPending = false;
    private volatile UsageUpdate lastUsage = null;

    // ── CLI options ─────────────────────────────────────────────────────────

    @CommandLine.Option(names = {"-a", "--agent"},
            description = "ACP agent: opencode, claude, pi, gemini [env: ACP_AGENT]")
    String agent;

    @CommandLine.Option(names = {"--agent-binary"},
            description = "Override agent binary path (for custom agents) [env: ACP_AGENT_BINARY]")
    String acpAgentBinary;

    @CommandLine.Option(names = {"--agent-args"},
            description = "Override agent arguments (for custom agents) [env: ACP_AGENT_ARGS]")
    String acpAgentArgs;

    @CommandLine.Option(names = {"-p", "--prompt"},
            description = "The prompt text to send to the agent [env: ACP_PROMPT]")
    String prompt;

    @CommandLine.Option(names = {"-m", "--model"},
            description = "The model to use, e.g. claude-opus-4-6 (resolved per agent/provider) [env: ACP_MODEL]")
    String model;

    @CommandLine.Option(names = {"--provider"},
            description = "Provider: zen, vertex-ai [env: ACP_PROVIDER]")
    String provider;

    @CommandLine.Option(names = {"--request-timeout"},
            description = "Timeout in seconds for requests (initialize, create session, etc.) [env: ACP_REQUEST_TIMEOUT]")
    Integer requestTimeout;

    @CommandLine.Option(names = {"--prompt-timeout"},
            description = "Timeout in seconds for prompt requests; 0 means no timeout [env: ACP_PROMPT_TIMEOUT]")
    Integer promptTimeout;

    @CommandLine.Option(names = {"--permission-mode"},
            description = "How to respond to agent permission requests: allow_always, allow_once, reject_once, reject_always [env: ACP_PERMISSION_MODE]")
    String permissionMode;

    @CommandLine.Option(names = {"-b", "--backup"},
            description = "Backup workspace to target/workdirs before running: yes, no (default: yes). Only applies to Maven/Gradle projects [env: ACP_BACKUP]")
    String backup;

    @CommandLine.Option(names = {"--backup-project-name"},
            description = "Name of the project used in the backup directory: target/workdirs/<name>_<timestamp> (default: current directory name) [env: ACP_BACKUP_PROJECT_NAME]")
    String backupProjectName;

    @CommandLine.Option(names = {"--wks", "--workspace-path"},
            description = "Absolute path to the project/workspace directory used as CWD for the session. If not set, defaults to the directory where the command is executed [env: WORKSPACE_PATH]")
    String workspacePath;

    @CommandLine.Option(names = {"-s", "--skill-path"},
            description = "Absolute path to a skills folder to add as additional directory [env: SKILL_PATH]")
    String skillPath;

    @CommandLine.Option(names = {"-l", "--log-level"},
            description = "Log level: INFO, DEBUG, TRACE, WARNING, SEVERE [env: ACP_LOG_LEVEL]")
    String logLevel;

    @Override
    public void run() {
        // Configure log level if provided
        logLevel = resolveOption(logLevel, "ACP_LOG_LEVEL", null);
        if (logLevel != null && !logLevel.isEmpty()) {
            Level level = Level.parse(logLevel.toUpperCase());
            java.util.logging.Logger.getLogger("io.quarkiverse").setLevel(level);
            java.util.logging.Logger.getLogger("io.quarkiverse.acp").setLevel(level);
            java.util.logging.Logger.getLogger("io.quarkiverse.agentclientprotocol").setLevel(level);
            java.util.logging.Logger rootLogger = java.util.logging.Logger.getLogger("");
            if (rootLogger.getLevel().intValue() > level.intValue()) {
                rootLogger.setLevel(level);
            }
            for (var handler : rootLogger.getHandlers()) {
                if (handler.getLevel().intValue() > level.intValue()) {
                    handler.setLevel(level);
                }
            }
        }

        // Resolve options: CLI arg > env var > default
        prompt = resolveOption(prompt, "ACP_PROMPT", "Say Hello");
        permissionMode = resolveOption(permissionMode, "ACP_PERMISSION_MODE", "allow_always");

        // ── Resolve agent binary and args ────────────────────────────────
        agent = resolveOption(agent, "ACP_AGENT", "opencode");
        acpAgentBinary = resolveOption(acpAgentBinary, "ACP_AGENT_BINARY", null);
        acpAgentArgs = resolveOption(acpAgentArgs, "ACP_AGENT_ARGS", null);

        String binary;
        String args;
        if (acpAgentBinary != null) {
            // Explicit binary override — use it directly
            binary = acpAgentBinary;
            args = acpAgentArgs;
        } else {
            AgentDef agentDef = AGENT_REGISTRY.get(agent);
            if (agentDef != null) {
                binary = agentDef.binary();
                args = acpAgentArgs != null ? acpAgentArgs : agentDef.args();
            } else {
                System.err.println("ERROR: Unknown agent '" + agent
                        + "'. Known agents: " + AGENT_REGISTRY.keySet()
                        + ". Use --agent-binary for custom agents.");
                System.exit(1);
                return;
            }
        }

        // ── Resolve and normalize provider ───────────────────────────────
        provider = resolveOption(provider, "ACP_PROVIDER", "zen");
        provider = normalizeProvider(provider);

        // ── Resolve model name ───────────────────────────────────────────
        model = resolveOption(model, "ACP_MODEL", null);
        if (model != null) {
            model = resolveModelName(agent, provider, model);
        }

        // ── Timeouts ─────────────────────────────────────────────────────
        String reqTimeoutStr = resolveOption(
                requestTimeout != null ? requestTimeout.toString() : null,
                "ACP_REQUEST_TIMEOUT", "30");
        Duration reqTimeout = Duration.ofSeconds(Long.parseLong(reqTimeoutStr));

        String promptTimeoutStr = resolveOption(
                promptTimeout != null ? promptTimeout.toString() : null,
                "ACP_PROMPT_TIMEOUT", "0");
        long promptTimeoutSecs = Long.parseLong(promptTimeoutStr);
        Duration pTimeout = promptTimeoutSecs > 0 ? Duration.ofSeconds(promptTimeoutSecs) : null;

        // 0. Check for required env variables based on agent + provider
        checkProviderEnv(agent, provider);

        // 0b. Resolve workspace path: CLI/env > current directory
        workspacePath = resolveOption(workspacePath, "WORKSPACE_PATH", null);
        String sessionCwd = workspacePath != null ? workspacePath : System.getProperty("user.dir");
        logger.infof("Workspace CWD: %s", sessionCwd);

        // 0c. Backup workspace if requested and project is Maven/Gradle
        backup = resolveOption(backup, "ACP_BACKUP", "yes");
        backupProjectName = resolveOption(backupProjectName, "ACP_BACKUP_PROJECT_NAME", ".");
        if ("yes".equalsIgnoreCase(backup)) {
            Path backupDir = backupWorkspace(backupProjectName, Path.of(sessionCwd));
            if (backupDir != null) {
                sessionCwd = backupDir.toAbsolutePath().toString();
                logger.infof("CWD set to backup directory: %s", sessionCwd);
            }
        }
        final String cwd = sessionCwd;

        // 1. Configure agent parameters
        var paramBuilder = AgentParameters.builder(binary);
        if (args != null && !args.isEmpty()) {
            for (String a : args.split(",")) {
                String trimmed = a.trim();
                if (!trimmed.isEmpty()) {
                    paramBuilder.arg(trimmed);
                }
            }
        }
        var params = paramBuilder.build();

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

            // 4. Initialize — handshake with the agent
            var initResponse = client.initialize();
            var agentInfo = initResponse.agentInfo();
            String title = agentInfo.title();
            String connectedMsg = (title != null && !title.isEmpty())
                    ? String.format("Connected to the ACP agent: %s - v%s - %s", agentInfo.name(), agentInfo.version(), title)
                    : String.format("Connected to the ACP agent: %s - v%s", agentInfo.name(), agentInfo.version());
            logger.info(connectedMsg);
            logger.infof("Protocol version: %s", initResponse.protocolVersion());
            logger.debugf("Capabilities: %s", initResponse.agentCapabilities());
            logger.debugf("Auth methods: %s", initResponse.authMethods());

            // 5. Create a session
            var session = client.newSession(new NewSessionRequest(cwd, List.of()));
            var sessionId = session.sessionId();
            logger.infof("Session created: %s with CWD: %s", sessionId, cwd);

            // Log the agent's default model from session config
            if (session.configOptions() != null) {
                session.configOptions().stream()
                        .filter(opt -> "model".equalsIgnoreCase(opt.id()))
                        .findFirst()
                        .ifPresent(opt -> logger.infof("Agent model: %s", opt.currentValue()));
            }

            // 6. Set the model (only if explicitly provided)
            if (model != null && !model.isEmpty()) {
                try {
                    var configResponse = client.setConfigOption(
                            new SetSessionConfigOptionRequest("model", sessionId, model));
                    if (configResponse.configOptions() != null) {
                        configResponse.configOptions().stream()
                                .filter(opt -> "model".equalsIgnoreCase(opt.id()))
                                .findFirst()
                                .ifPresent(opt -> logger.infof("Model set to: %s", opt.currentValue()));
                    }
                } catch (RuntimeException e) {
                    if (e.getMessage() != null && e.getMessage().contains("-32601")) {
                        logger.warnf("Agent does not support session/set_config_option — skipping model configuration. "
                                + "The agent will use its default model.");
                    } else {
                        throw e;
                    }
                }
            }

            // 7. Send a prompt
            skillPath = resolveOption(skillPath, "SKILL_PATH", null);
            String effectivePrompt = prompt;
            if (skillPath != null) {
                effectivePrompt = prompt + "\n\nPlease read the skill file at: " + skillPath + " and follow its instructions.";
            }
            logger.infof("Sending prompt: %s", effectivePrompt);
            System.out.println("Here is the AI response:");
            var response = client.prompt(new PromptRequest(
                    List.of(new TextContent(effectivePrompt)),
                    sessionId
            ));

            flushThoughts();
            if (messageOutputPending) {
                System.out.println();
                messageOutputPending = false;
            }
            logger.infof("Done! Stop reason: %s", response.stopReason());
            if (response.usage() != null) {
                var u = response.usage();
                logger.infof("[Tokens detail] input=%s, output=%s, cached_read=%s, cached_write=%s",
                        u.get("inputTokens"), u.get("outputTokens"),
                        u.get("cachedReadTokens"), u.get("cachedWriteTokens"));

                // Combined summary: total tokens, window usage, and cost
                Object total = u.get("totalTokens");
                String windowInfo = "";
                String costInfo = "";
                if (lastUsage != null) {
                    if (lastUsage.size() != null) {
                        windowInfo = String.format(" / %s window (%s used)",
                                lastUsage.size(), lastUsage.used() != null ? lastUsage.used() : "?");
                    }
                    if (lastUsage.cost() != null) {
                        costInfo = String.format(" | cost: %s %s",
                                lastUsage.cost().get("amount"), lastUsage.cost().get("currency"));
                    }
                }
                logger.infof("[Summary] total_tokens=%s%s%s", total, windowInfo, costInfo);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ── Provider normalization ───────────────────────────────────────────────

    /**
     * Normalizes legacy provider names to their canonical form.
     * <ul>
     *   <li>{@code opencode-zen} &rarr; {@code zen}</li>
     *   <li>{@code google-vertex-ai}, {@code anthropic-vertex-ai} &rarr; {@code vertex-ai}</li>
     * </ul>
     */
    private static String normalizeProvider(String provider) {
        return switch (provider) {
            case "opencode-zen","zen"      -> "zen";
            case "vertex-ai","google-vertex-ai",
                 "anthropic-vertex-ai" -> "vertex-ai";
            default                  -> provider;
        };
    }

    // ── Model name resolution ────────────────────────────────────────────────

    /**
     * Resolves a simple model name to the full provider-specific model path.
     * If the model already contains a {@code /}, it is returned as-is.
     *
     * <p>Currently only OpenCode + vertex-ai transforms the name:
     * {@code claude-opus-4-6} &rarr; {@code google-vertex-anthropic/claude-opus-4-6@default}
     */
    private static String resolveModelName(String agent, String provider, String model) {
        // Already a fully-qualified model path — return as-is
        if (model.contains("/")) {
            return model;
        }
        if ("opencode".equals(agent) && "vertex-ai".equals(provider)) {
            return "google-vertex-anthropic/" + model + "@default";
        }
        return model;
    }

    // ── Option resolution ────────────────────────────────────────────────────

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

    // ── Session update handling ──────────────────────────────────────────────

    private void handleSessionUpdate(String updateType, Object update) {
        if (update == null) {
            logger.debug("[Update] null");
            return;
        }

        if (!"agent_thought_chunk".equals(updateType)) {
            flushThoughts();
        }

        if (!"agent_message_chunk".equals(updateType) && messageOutputPending) {
            System.out.println();
            messageOutputPending = false;
        }

        switch (update) {
            case ContentChunk chunk -> {
                if ("agent_thought_chunk".equals(updateType)) {
                    thoughtBuffer.append(extractText(chunk.content()));
                } else {
                    System.out.print(extractText(chunk.content()));
                    messageOutputPending = true;
                }
            }
            case Plan plan -> {
                logger.infof("[Plan] %d steps:", plan.entries().size());
                plan.entries().forEach(e -> logger.infof("  - %s [%s]", e.content(), e.status()));
            }
            case ToolCall tool ->
                logger.infof("[ToolCall] %s (%s) - %s", tool.title(), tool.kind(), tool.status());
            case ToolCallUpdate toolUpdate ->
                logger.infof("[ToolUpdate] %s - %s", toolUpdate.title(), toolUpdate.status());
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
            case UsageUpdate usage -> {
                lastUsage = usage;
                logger.debugf("[Usage] used=%s size=%s cost=%s", usage.used(), usage.size(), usage.cost());
            }
            default ->
                logger.infof("[Update] %s: %s", updateType, update);
        }
    }

    private void flushThoughts() {
        if (!thoughtBuffer.isEmpty()) {
            logger.debugf("[Thought] %s", thoughtBuffer.toString().strip());
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

    // ── Permission handling ──────────────────────────────────────────────────

    private static RequestPermissionResponse handlePermissionRequest(RequestPermissionRequest request, String permissionMode) {
        var toolCall = request.toolCall();
        logger.infof("[Permission] %s requests: %s", toolCall.title(), toolCall.kind());

        String selectedOptionId = request.options().stream()
                .filter(o -> o.kind().getValue().equals(permissionMode))
                .findFirst()
                .map(PermissionOption::optionId)
                .orElseGet(() -> request.options().stream()
                        .filter(o -> o.kind() == PermissionOptionKind.ALLOW_ALWAYS
                                || o.kind() == PermissionOptionKind.ALLOW_ONCE)
                        .findFirst()
                        .map(PermissionOption::optionId)
                        .orElse(request.options().getFirst().optionId()));

        logger.infof("[Permission] Responded with: %s", permissionMode);
        return new RequestPermissionResponse(new SelectedPermissionOutcome(selectedOptionId));
    }

    // ── Provider env-var validation ──────────────────────────────────────────

    /**
     * Checks required environment variables for the given agent + provider combination.
     */
    private static void checkProviderEnv(String agent, String provider) {
        String key = agent + ":" + provider;
        List<String> requiredVars = PROVIDER_ENV_VARS.get(key);

        if (requiredVars == null) {
            if (!"zen".equals(provider)) {
                logger.warnf("No env var requirements defined for agent '%s' with provider '%s'", agent, provider);
            }
            return;
        }

        for (String varName : requiredVars) {
            requireEnv(varName, provider);
        }
    }

    private static void requireEnv(String varName, String provider) {
        String value = System.getenv(varName);
        if (value == null || value.isBlank()) {
            System.err.println("ERROR: " + varName + " environment variable is not set (required for " + provider + " provider).");
            System.exit(1);
        }
    }

    // ── Workspace backup ────────────────────────────────────────────────────

    /**
     * Backs up the current workspace to {@code target/workdirs/<name>_<timestamp>}.
     * Only applies to Maven ({@code pom.xml}) or Gradle ({@code build.gradle} / {@code build.gradle.kts}) projects.
     * Build output, VCS, and dependency directories are excluded from the copy.
     *
     * @param name the workspace project name; {@code "."} resolves to the current directory name
     */
    /**
     * Backs up the workspace to target/workdirs and returns the backup directory path,
     * or {@code null} if the backup was skipped or failed.
     */
    private Path backupWorkspace(String name, Path workDir) {
        boolean isMaven = Files.exists(workDir.resolve("pom.xml"));
        boolean isGradle = Files.exists(workDir.resolve("build.gradle"))
                || Files.exists(workDir.resolve("build.gradle.kts"));

        if (!isMaven && !isGradle) {
            logger.info("Skipping workspace backup (not a Maven or Gradle project)");
            return null;
        }

        // Resolve "." to the current directory name
        String projectName = ".".equals(name) ? workDir.getFileName().toString() : name;
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm:ss"));
        Path backupDir = workDir.resolve("target").resolve("workdirs").resolve(projectName + "_" + timestamp);

        Set<String> excludes = Set.of("target", "build", ".git", ".gradle", ".idea", "node_modules",".claude", ".env");

        try {
            Files.walk(workDir)
                    .filter(path -> {
                        Path relative = workDir.relativize(path);
                        return relative.getNameCount() == 0
                                || !excludes.contains(relative.getName(0).toString());
                    })
                    .forEach(source -> {
                        Path dest = backupDir.resolve(workDir.relativize(source));
                        try {
                            if (Files.isDirectory(source)) {
                                Files.createDirectories(dest);
                            } else {
                                Files.createDirectories(dest.getParent());
                                Files.copy(source, dest);
                            }
                        } catch (IOException e) {
                            logger.warnf("Failed to copy %s: %s", source, e.getMessage());
                        }
                    });
            logger.infof("Workspace backed up to: %s", backupDir);
            return backupDir;
        } catch (IOException e) {
            logger.warnf("Failed to backup workspace: %s", e.getMessage());
            return null;
        }
    }
}
