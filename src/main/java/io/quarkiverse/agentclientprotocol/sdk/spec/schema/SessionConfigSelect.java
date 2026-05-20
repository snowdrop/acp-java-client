package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * A single-value selector (dropdown) session configuration option payload.
 */
public record SessionConfigSelect(
        @JsonProperty("currentValue") String currentValue,
        @JsonProperty("options") Object options) {
}
