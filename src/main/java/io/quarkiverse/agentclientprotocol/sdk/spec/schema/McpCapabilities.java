package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * MCP capabilities supported by the agent
 */
public record McpCapabilities(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("http") Boolean http,
        @JsonProperty("sse") Boolean sse) {
}
