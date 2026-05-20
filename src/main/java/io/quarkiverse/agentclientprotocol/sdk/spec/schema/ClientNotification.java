package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ClientNotification(
        @JsonProperty("method") String method,
        @JsonProperty("params") Object params) {
    public ClientNotification(String method) {
        this(method, null);
    }
}
