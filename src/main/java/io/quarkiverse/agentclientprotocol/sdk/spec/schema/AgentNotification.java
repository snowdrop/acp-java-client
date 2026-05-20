package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AgentNotification(
        @JsonProperty("method") String method,
        @JsonProperty("params") Object params) {
    public AgentNotification(String method) {
        this(method, null);
    }
}
