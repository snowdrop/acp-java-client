package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Capabilities for the `session/list` method.
 * 
 * By supplying `{}` it means that the agent supports listing of sessions.
 */
public record SessionListCapabilities(
        @JsonProperty("_meta") Map<String, Object> meta) {
}
