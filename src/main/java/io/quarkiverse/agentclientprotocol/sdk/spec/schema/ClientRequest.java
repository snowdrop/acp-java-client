package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientRequest(
        @JsonProperty("id") Object id,
        @JsonProperty("method") String method,
        @JsonProperty("params") Object params) {
    public ClientRequest(Object id, String method) {
        this(id, method, null);
    }
}
