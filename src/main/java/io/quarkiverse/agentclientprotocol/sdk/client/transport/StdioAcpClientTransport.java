package io.quarkiverse.agentclientprotocol.sdk.client.transport;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import io.quarkiverse.agentclientprotocol.sdk.spec.schema.TextContent;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.mutiny.subscription.MultiEmitter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Mutiny-based ACP stdio transport.
 *
 * Communicates with an agent process using standard input/output streams.
 * Messages are exchanged as newline-delimited JSON-RPC over stdin/stdout.
 */
public class StdioAcpClientTransport {

    private static final Logger logger = LoggerFactory.getLogger(StdioAcpClientTransport.class);

    private final AgentParameters params;
    private final ObjectMapper mapper;

    private Process process;
    private volatile boolean isClosing = false;

    // Outbound: client → agent (JSON-RPC requests)
    private MultiEmitter<? super JsonNode> outboundEmitter;
    private Cancellable outboundSubscription;

    // Thread pools — daemon threads so JVM can exit if closeGracefully() isn't called
    private final ExecutorService inboundExecutor;
    private final ExecutorService outboundExecutor;
    private final ExecutorService errorExecutor;

    private Consumer<String> stdErrorHandler = error -> logger.info("STDERR: {}", error);
    private Consumer<JsonNode> inboundMessageHandler;

    public StdioAcpClientTransport(AgentParameters params) {
        this(params, createDefaultMapper());
    }

    private static ObjectMapper createDefaultMapper() {
        ObjectMapper mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);

        // ACP server requires "type" discriminator on content objects
        SimpleModule module = new SimpleModule("AcpContentTypes");
        module.addSerializer(TextContent.class, new TextContentSerializer());
        mapper.registerModule(module);
        return mapper;
    }

    /**
     * Custom serializer that adds the required "type": "text" field
     * when serializing TextContent for the ACP protocol.
     */
    static class TextContentSerializer extends StdSerializer<TextContent> {
        TextContentSerializer() { super(TextContent.class); }

        @Override
        public void serialize(TextContent value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeStartObject();
            gen.writeStringField("type", "text");
            gen.writeStringField("text", value.text());
            if (value.annotations() != null) {
                gen.writeObjectField("annotations", value.annotations());
            }
            gen.writeEndObject();
        }
    }

    public StdioAcpClientTransport(AgentParameters params, ObjectMapper mapper) {
        this.params = params;
        this.mapper = mapper;

        this.inboundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "acp-client-inbound");
            t.setDaemon(true);
            return t;
        });
        this.outboundExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "acp-client-outbound");
            t.setDaemon(true);
            return t;
        });
        this.errorExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "acp-client-error");
            t.setDaemon(true);
            return t;
        });
    }

    public void setInboundMessageHandler(Consumer<JsonNode> handler) {
        this.inboundMessageHandler = handler;
    }

    public void setStdErrorHandler(Consumer<String> handler) {
        this.stdErrorHandler = handler;
    }

    public Uni<Void> connect() {
        return Uni.createFrom().item(() -> {
            logger.info("ACP agent starting");

            List<String> fullCommand = new ArrayList<>();
            fullCommand.add(params.getCommand());
            fullCommand.addAll(params.getArgs());

            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(fullCommand);
            processBuilder.environment().putAll(params.getEnv());

            try {
                this.process = processBuilder.start();
            } catch (IOException e) {
                throw new RuntimeException("Failed to start process: " + fullCommand, e);
            }

            if (process.getInputStream() == null || process.getOutputStream() == null) {
                process.destroy();
                throw new RuntimeException("Process input or output stream is null");
            }

            startInboundProcessing();
            startOutboundProcessing();
            startErrorProcessing();

            logger.info("ACP agent started");
            return null;
        }).replaceWithVoid();
    }

    public Uni<Void> sendMessage(JsonNode message) {
        return Uni.createFrom().voidItem()
                .invoke(() -> {
                    if (outboundEmitter != null) {
                        outboundEmitter.emit(message);
                    }
                });
    }

    private void startInboundProcessing() {
        inboundExecutor.execute(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String line;
                while (!isClosing && (line = reader.readLine()) != null) {
                    try {
                        logger.trace("RECV: {}", line);
                        JsonNode message = mapper.readTree(line);
                        if (inboundMessageHandler != null) {
                            inboundMessageHandler.accept(message);
                        }
                    } catch (Exception e) {
                        if (!isClosing) {
                            logger.error("Error processing inbound message: {}", line, e);
                        }
                        break;
                    }
                }
            } catch (IOException e) {
                if (!isClosing) {
                    logger.error("Error reading from input stream", e);
                }
            } finally {
                isClosing = true;
            }
        });
    }

    private void startOutboundProcessing() {
        this.outboundSubscription = Multi.createFrom().<JsonNode>emitter(e -> this.outboundEmitter = e)
                .emitOn(outboundExecutor)
                .subscribe().with(
                        message -> {
                            if (message != null && !isClosing) {
                                try {
                                    String jsonMessage = mapper.writeValueAsString(message);
                                    jsonMessage = jsonMessage.replace("\r\n", "\\n")
                                            .replace("\n", "\\n")
                                            .replace("\r", "\\n");
                                    logger.trace("SEND: {}", jsonMessage);

                                    var os = process.getOutputStream();
                                    synchronized (os) {
                                        os.write(jsonMessage.getBytes(StandardCharsets.UTF_8));
                                        os.write("\n".getBytes(StandardCharsets.UTF_8));
                                        os.flush();
                                    }
                                } catch (IOException e) {
                                    if (!isClosing) {
                                        logger.error("Error writing outbound message", e);
                                    }
                                }
                            }
                        },
                        error -> {
                            if (!isClosing) {
                                logger.error("Outbound subscription error", error);
                            }
                        }
                );
    }

    private void startErrorProcessing() {
        errorExecutor.execute(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while (!isClosing && (line = reader.readLine()) != null) {
                    stdErrorHandler.accept(line);
                }
            } catch (IOException e) {
                if (!isClosing) {
                    logger.error("Error reading from error stream", e);
                }
            }
        });
    }

    public Uni<Void> closeGracefully() {
        return Uni.createFrom().voidItem()
                .invoke(() -> {
                    isClosing = true;
                    logger.debug("Initiating graceful shutdown");

                    if (outboundSubscription != null) outboundSubscription.cancel();
                    if (outboundEmitter != null) outboundEmitter.complete();

                    if (process != null) {
                        logger.debug("Sending TERM to process");
                        process.destroy();
                        try {
                            boolean exited = process.waitFor(5, TimeUnit.SECONDS);
                            if (exited) {
                                int exitCode = process.exitValue();
                                if (exitCode == 0 || exitCode == 143 || exitCode == 137) {
                                    logger.info("ACP agent process stopped (exit code {})", exitCode);
                                } else {
                                    logger.warn("Process terminated with code {}", exitCode);
                                }
                            } else {
                                logger.warn("Process did not exit within timeout, forcing kill");
                                process.destroyForcibly();
                            }
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            logger.debug("Interrupted while waiting for process exit");
                        }
                    }

                    inboundExecutor.shutdownNow();
                    outboundExecutor.shutdownNow();
                    errorExecutor.shutdownNow();
                    try {
                        inboundExecutor.awaitTermination(2, TimeUnit.SECONDS);
                        outboundExecutor.awaitTermination(2, TimeUnit.SECONDS);
                        errorExecutor.awaitTermination(2, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    logger.debug("Graceful shutdown completed");
                });
    }

    public ObjectMapper getMapper() {
        return mapper;
    }
}
