package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Text provided to or from an LLM.
 */
public record TextContent(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("annotations") Annotations annotations,
        @JsonProperty("text") String text) {
    public TextContent(String text) {
        this(null, null, text);
    }
}
