package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Embed a terminal created with `terminal/create` by its id.
 * 
 * The terminal must be added before calling `terminal/release`.
 * 
 * See protocol docs: [Terminal](https://agentclientprotocol.com/protocol/terminals)
 */
public record Terminal(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("terminalId") String terminalId) {
    public Terminal(String terminalId) {
        this(null, terminalId);
    }
}
