package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * An HTTP header to set when making requests to the MCP server.
 */
public record HttpHeader(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("name") String name,
        @JsonProperty("value") String value) {
    public HttpHeader(String name, String value) {
        this(null, name, value);
    }
}
