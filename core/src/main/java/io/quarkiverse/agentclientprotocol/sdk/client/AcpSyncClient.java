package io.quarkiverse.agentclientprotocol.sdk.client;

import io.quarkiverse.agentclientprotocol.sdk.spec.schema.v1.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Synchronous ACP client that wraps {@link AcpAsyncClient} with blocking calls.
 *
 * <p>Implements {@link AutoCloseable} for use with try-with-resources.
 * The constructor eagerly connects to the agent process and blocks until ready.
 *
 * <p>Example usage:
 * <pre>{@code
 * try (AcpSyncClient client = AcpClient.sync(transport)
 *         .sessionUpdateConsumer(n -> handleUpdate(n.update()))
 *         .build()) {
 *     var init = client.initialize();
 *     var session = client.newSession(new NewSessionRequest(".", List.of()));
 *     var response = client.prompt(new PromptRequest(
 *             List.of(new TextContent("Hello")), session.sessionId()));
 * }
 * }</pre>
 *
 * @see AcpClient
 * @see AcpAsyncClient
 */
public class AcpSyncClient implements AutoCloseable {

    private static final Logger logger = LoggerFactory.getLogger(AcpSyncClient.class);
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(10);

    private final AcpAsyncClient delegate;

    /**
     * Creates a sync client wrapping the given async client.
     * Eagerly connects to the agent process, blocking up to 30 seconds.
     *
     * @param delegate the async client to wrap
     */
    AcpSyncClient(AcpAsyncClient delegate) {
        this.delegate = delegate;
        this.delegate.connect().await().atMost(Duration.ofSeconds(30));
    }

    @Override
    public void close() {
        closeGracefully();
    }

    /**
     * Gracefully shuts down the client and the agent process.
     *
     * @return {@code true} if shutdown completed within the timeout, {@code false} otherwise
     */
    public boolean closeGracefully() {
        try {
            logger.debug("Gracefully closing ACP sync client");
            delegate.closeGracefully().await().atMost(DEFAULT_CLOSE_TIMEOUT);
            return true;
        } catch (RuntimeException e) {
            logger.warn("Client didn't close within timeout", e);
            return false;
        }
    }

    /**
     * Performs the ACP initialization handshake (blocking).
     *
     * @param request the initialization parameters
     * @return the agent's capabilities and metadata
     */
    public InitializeResponse initialize(InitializeRequest request) {
        return delegate.initialize(request).await().indefinitely();
    }

    /**
     * Performs the ACP initialization handshake with default capabilities (blocking).
     *
     * @return the agent's capabilities and metadata
     */
    public InitializeResponse initialize() {
        return delegate.initialize().await().indefinitely();
    }

    /**
     * Creates a new agent session (blocking).
     *
     * @param request the session parameters
     * @return the session ID and available modes
     */
    public NewSessionResponse newSession(NewSessionRequest request) {
        return delegate.newSession(request).await().indefinitely();
    }

    /**
     * Sends a user prompt and blocks until the agent completes its response.
     *
     * @param request the prompt content and session ID
     * @return the stop reason when the agent finishes
     */
    public PromptResponse prompt(PromptRequest request) {
        return delegate.prompt(request).await().indefinitely();
    }

    /**
     * Sets a session configuration option, e.g. the model (blocking).
     *
     * @param request the config option ID, value, and session ID
     * @return the updated config options
     */
    public SetSessionConfigOptionResponse setConfigOption(SetSessionConfigOptionRequest request) {
        return delegate.setConfigOption(request).await().indefinitely();
    }

    /**
     * Sends a cancellation notification for the current prompt turn (blocking).
     *
     * @param notification the session ID to cancel
     */
    public void cancel(CancelNotification notification) {
        delegate.cancel(notification).await().indefinitely();
    }
}
