package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Request to wait for a terminal command to exit.
 */
public record WaitForTerminalExitRequest(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("terminalId") String terminalId) {
    public WaitForTerminalExitRequest(String sessionId, String terminalId) {
        this(null, sessionId, terminalId);
    }
}
