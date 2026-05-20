package io.quarkiverse.agentclientprotocol.sdk.client;

import io.quarkiverse.agentclientprotocol.sdk.client.transport.StdioAcpClientTransport;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.SessionNotification;

import java.time.Duration;
import java.util.function.Consumer;

/**
 * Factory for creating ACP clients.
 *
 * <p>Use {@link #sync(StdioAcpClientTransport)} for blocking operations
 * or {@link #async(StdioAcpClientTransport)} for non-blocking Uni-based operations.
 */
public final class AcpClient {

    private AcpClient() {}

    /**
     * Creates a builder for a synchronous (blocking) ACP client.
     *
     * @param transport the stdio transport to use
     * @return a {@link SyncBuilder} for configuring and building the client
     */
    public static SyncBuilder sync(StdioAcpClientTransport transport) {
        return new SyncBuilder(transport);
    }

    /**
     * Creates a builder for an asynchronous (Mutiny {@link io.smallrye.mutiny.Uni}-based) ACP client.
     *
     * @param transport the stdio transport to use
     * @return an {@link AsyncBuilder} for configuring and building the client
     */
    public static AsyncBuilder async(StdioAcpClientTransport transport) {
        return new AsyncBuilder(transport);
    }

    /** Builder for configuring and creating an {@link AcpSyncClient}. */
    public static class SyncBuilder {
        private final StdioAcpClientTransport transport;
        private Duration requestTimeout = Duration.ofSeconds(30);
        private Consumer<SessionNotification> sessionUpdateConsumer;

        private SyncBuilder(StdioAcpClientTransport transport) {
            this.transport = transport;
        }

        /**
         * Sets the timeout for individual JSON-RPC requests. Defaults to 30 seconds.
         *
         * @param timeout the request timeout duration
         * @return this builder
         */
        public SyncBuilder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        public SyncBuilder sessionUpdateConsumer(Consumer<SessionNotification> consumer) {
            this.sessionUpdateConsumer = consumer;
            return this;
        }

        public AcpSyncClient build() {
            AcpAsyncClient async = new AcpAsyncClient(transport, requestTimeout, sessionUpdateConsumer);
            return new AcpSyncClient(async);
        }
    }

    public static class AsyncBuilder {
        private final StdioAcpClientTransport transport;
        private Duration requestTimeout = Duration.ofSeconds(30);
        private Consumer<SessionNotification> sessionUpdateConsumer;

        private AsyncBuilder(StdioAcpClientTransport transport) {
            this.transport = transport;
        }

        public AsyncBuilder requestTimeout(Duration timeout) {
            this.requestTimeout = timeout;
            return this;
        }

        public AsyncBuilder sessionUpdateConsumer(Consumer<SessionNotification> consumer) {
            this.sessionUpdateConsumer = consumer;
            return this;
        }

        public AcpAsyncClient build() {
            return new AcpAsyncClient(transport, requestTimeout, sessionUpdateConsumer);
        }
    }
}
