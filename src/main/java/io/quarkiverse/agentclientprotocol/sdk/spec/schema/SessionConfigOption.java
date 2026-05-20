package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * A session configuration option selector and its current state.
 */
public record SessionConfigOption(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("category") Object category,
        @JsonProperty("description") String description,
        @JsonProperty("id") String id,
        @JsonProperty("name") String name) {
    public SessionConfigOption(String id, String name) {
        this(null, null, null, id, name);
    }
}
