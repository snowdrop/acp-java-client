package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Response to a permission request.
 */
public record RequestPermissionResponse(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("outcome") Object outcome) {
    public RequestPermissionResponse(Object outcome) {
        this(null, outcome);
    }
}
