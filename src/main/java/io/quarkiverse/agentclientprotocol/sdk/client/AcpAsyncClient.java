package io.quarkiverse.agentclientprotocol.sdk.client;

import io.quarkiverse.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.*;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.Error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.smallrye.mutiny.Uni;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Mutiny-based asynchronous ACP client.
 *
 * <p>Communicates with an ACP agent over a {@link StdioAcpClientTransport} using
 * JSON-RPC 2.0 over stdio. All protocol methods return {@link Uni} for non-blocking
 * composition.
 *
 * <p>Inbound JSON-RPC messages are routed to either:
 * <ul>
 *   <li>Pending request futures (responses matched by {@code id})</li>
 *   <li>The session update consumer (notifications on {@code session/update})</li>
 * </ul>
 *
 * <p>Session update notifications are deserialized using a discriminator-based
 * routing: the {@code sessionUpdate} field in the update JSON selects the target
 * record type (e.g. {@link ContentChunk}, {@link ToolCall}, {@link Plan}).
 *
 * @see AcpClient
 * @see AcpSyncClient
 */
public class AcpAsyncClient {

    private static final Logger logger = LoggerFactory.getLogger(AcpAsyncClient.class);

    private static final Map<String, Class<?>> SESSION_UPDATE_TYPES = Map.ofEntries(
            Map.entry("agent_message_chunk", ContentChunk.class),
            Map.entry("agent_thought_chunk", ContentChunk.class),
            Map.entry("user_message_chunk", ContentChunk.class),
            Map.entry("tool_call", ToolCall.class),
            Map.entry("tool_call_update", ToolCallUpdate.class),
            Map.entry("plan", Plan.class),
            Map.entry("available_commands_update", AvailableCommandsUpdate.class),
            Map.entry("current_mode_update", CurrentModeUpdate.class),
            Map.entry("config_option_update", ConfigOptionUpdate.class),
            Map.entry("session_info_update", SessionInfoUpdate.class)
    );

    private final StdioAcpClientTransport transport;
    private final ObjectMapper mapper;
    private final Duration requestTimeout;
    private final Duration promptTimeout;
    private final Consumer<SessionNotification> sessionUpdateConsumer;

    private final AtomicInteger requestIdCounter = new AtomicInteger(0);
    private final ConcurrentHashMap<Integer, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    AcpAsyncClient(StdioAcpClientTransport transport, Duration requestTimeout,
                   Duration promptTimeout, Consumer<SessionNotification> sessionUpdateConsumer) {
        this.transport = transport;
        this.mapper = transport.getMapper();
        this.requestTimeout = requestTimeout;
        this.promptTimeout = promptTimeout;
        this.sessionUpdateConsumer = sessionUpdateConsumer;

        transport.setInboundMessageHandler(this::handleIncoming);
    }

    // ===== Lifecycle =====

    /**
     * Starts the underlying transport, launching the agent process.
     *
     * @return a {@link Uni} that completes when the transport is ready
     */
    public Uni<Void> connect() {
        return transport.connect();
    }

    /**
     * Gracefully shuts down the transport and the agent process.
     *
     * @return a {@link Uni} that completes when shutdown is finished
     */
    public Uni<Void> closeGracefully() {
        return transport.closeGracefully();
    }

    // ===== ACP methods =====

    /**
     * Sends an {@code initialize} request to perform the ACP handshake.
     *
     * @param request the initialization parameters including protocol version and client capabilities
     * @return a {@link Uni} emitting the agent's capabilities and metadata
     */
    public Uni<InitializeResponse> initialize(InitializeRequest request) {
        return sendRequest("initialize", request)
                .map(result -> mapper.convertValue(result, InitializeResponse.class));
    }

    /**
     * Sends an {@code initialize} request with default client capabilities
     * (filesystem read/write and terminal support, protocol version 1).
     *
     * @return a {@link Uni} emitting the agent's capabilities and metadata
     */
    public Uni<InitializeResponse> initialize() {
        return initialize(new InitializeRequest(
                null,
                new ClientCapabilities(null, new FileSystemCapabilities(null, true, true), true),
                null,
                1
        ));
    }

    /**
     * Creates a new agent session.
     *
     * @param request the session parameters including working directory and MCP servers
     * @return a {@link Uni} emitting the session ID and available modes
     */
    public Uni<NewSessionResponse> newSession(NewSessionRequest request) {
        return sendRequest("session/new", request)
                .map(result -> mapper.convertValue(result, NewSessionResponse.class));
    }

    /**
     * Sends a user prompt to the agent within an existing session.
     * Session updates are streamed via the {@code sessionUpdateConsumer}.
     *
     * @param request the prompt content and session ID
     * @return a {@link Uni} emitting the stop reason when the agent finishes
     */
    public Uni<PromptResponse> prompt(PromptRequest request) {
        Uni<JsonNode> uni = (promptTimeout != null)
                ? sendRequest("session/prompt", request, promptTimeout)
                : sendRequestNoTimeout("session/prompt", request);
        return uni.map(result -> mapper.convertValue(result, PromptResponse.class));
    }

    /**
     * Sets a session configuration option (e.g. the model).
     *
     * @param request the config option ID, value, and session ID
     * @return a {@link Uni} emitting the updated config options
     */
    public Uni<SetSessionConfigOptionResponse> setConfigOption(SetSessionConfigOptionRequest request) {
        return sendRequest("session/set_config_option", request)
                .map(result -> mapper.convertValue(result, SetSessionConfigOptionResponse.class));
    }

    /**
     * Closes an existing session.
     *
     * @param request the session ID to close
     * @return a {@link Uni} that completes when the session is closed
     */
    public Uni<CloseSessionResponse> closeSession(CloseSessionRequest request) {
        return sendRequest("session/close", request)
                .map(result -> mapper.convertValue(result, CloseSessionResponse.class));
    }

    /**
     * Sends a cancellation notification for the current prompt turn.
     *
     * @param notification the session ID to cancel
     * @return a {@link Uni} that completes when the notification is sent
     */
    public Uni<Void> cancel(CancelNotification notification) {
        return sendNotification("session/cancel", notification);
    }

    // ===== Internal =====

    /**
     * Sends a JSON-RPC 2.0 request and returns a {@link Uni} that resolves
     * when the matching response arrives or times out.
     *
     * @param method the JSON-RPC method name
     * @param params the request parameters
     * @return a {@link Uni} emitting the {@code result} node from the response
     */
    private Uni<JsonNode> sendRequest(String method, Object params) {
        return sendRequest(method, params, requestTimeout);
    }

    private Uni<JsonNode> sendRequest(String method, Object params, Duration timeout) {
        Uni<JsonNode> uni = sendRequestNoTimeout(method, params);
        if (timeout != null) {
            int id = requestIdCounter.get(); // last assigned id
            uni = uni.ifNoItem().after(timeout)
                    .failWith(() -> {
                        pendingRequests.remove(id);
                        return new RuntimeException("Request timeout for " + method + " (id=" + id + ")");
                    });
        }
        return uni;
    }

    /**
     * Sends a JSON-RPC 2.0 request without a timeout.
     * Used for long-running operations like prompts where the agent streams updates
     * while working and the response arrives only when the agent is done.
     */
    private Uni<JsonNode> sendRequestNoTimeout(String method, Object params) {
        int id = requestIdCounter.incrementAndGet();
        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(id, future);

        ObjectNode requestNode = mapper.createObjectNode();
        requestNode.put("jsonrpc", "2.0");
        requestNode.put("id", id);
        requestNode.put("method", method);
        requestNode.set("params", mapper.valueToTree(params));

        logger.debug(">> {} (id={})", method, id);

        return transport.sendMessage(requestNode)
                .chain(() -> Uni.createFrom().completionStage(future));
    }

    /**
     * Sends a JSON-RPC 2.0 notification (fire-and-forget, no response expected).
     *
     * @param method the JSON-RPC method name
     * @param params the notification parameters
     * @return a {@link Uni} that completes when the message is sent
     */
    private Uni<Void> sendNotification(String method, Object params) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("method", method);
        node.set("params", mapper.valueToTree(params));
        logger.debug(">> {} (notification)", method);
        return transport.sendMessage(node);
    }

    /**
     * Routes an incoming JSON-RPC message to the appropriate handler.
     * Responses are matched to pending requests by {@code id};
     * {@code session/update} notifications are deserialized and forwarded
     * to the session update consumer.
     *
     * @param node the raw JSON-RPC message from the agent
     */
    private void handleIncoming(JsonNode node) {
        // JSON-RPC response
        if (node.has("id") && (node.has("result") || node.has("error"))) {
            int id = node.get("id").asInt();
            CompletableFuture<JsonNode> future = pendingRequests.remove(id);
            if (future != null) {
                if (node.has("error") && !node.get("error").isNull()) {
                    Error error = mapper.convertValue(node.get("error"), Error.class);
                    future.completeExceptionally(new RuntimeException(
                            "JSON-RPC error " + error.code() + ": " + error.message()));
                } else {
                    future.complete(node.get("result"));
                }
            }
            return;
        }

        // JSON-RPC notification
        if (node.has("method") && sessionUpdateConsumer != null) {
            String method = node.get("method").asText();
            if ("session/update".equals(method) && node.has("params")) {
                try {
                    JsonNode paramsNode = node.get("params");
                    String sessionId = paramsNode.path("sessionId").asText(null);
                    JsonNode updateNode = paramsNode.get("update");

                    // Deserialize the update using the sessionUpdate discriminator
                    String updateType = updateNode != null ? updateNode.path("sessionUpdate").asText(null) : null;
                    Object typedUpdate = deserializeUpdate(updateNode, updateType);

                    // Store the update type in _meta so consumers can distinguish subtypes
                    Map<String, Object> meta = updateType != null ? Map.of("sessionUpdate", updateType) : null;
                    var notification = new SessionNotification(meta, sessionId, typedUpdate);
                    sessionUpdateConsumer.accept(notification);
                } catch (Exception e) {
                    logger.warn("Failed to deserialize session notification", e);
                }
            }
        }
    }

    /**
     * Deserializes a session update JSON node into the appropriate schema record type
     * based on the {@code sessionUpdate} discriminator field.
     *
     * @param updateNode the raw update JSON
     * @param updateType the discriminator value (e.g. {@code "tool_call"}, {@code "plan"})
     * @return the deserialized record, or a raw {@link Map} for unknown types
     */
    private Object deserializeUpdate(JsonNode updateNode, String updateType) {
        if (updateNode == null || updateNode.isNull()) {
            return null;
        }

        if (updateType == null) {
            return mapper.convertValue(updateNode, Object.class);
        }

        Class<?> targetClass = SESSION_UPDATE_TYPES.get(updateType);
        if (targetClass != null) {
            return mapper.convertValue(updateNode, targetClass);
        }

        // Unknown update type — return as a Map
        logger.debug("Unknown session update type: {}", updateType);
        return mapper.convertValue(updateNode, Object.class);
    }
}
