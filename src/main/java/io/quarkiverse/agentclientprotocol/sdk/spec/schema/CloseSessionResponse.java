package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Response from closing a session.
 */
public record CloseSessionResponse(
        @JsonProperty("_meta") Map<String, Object> meta) {
}
