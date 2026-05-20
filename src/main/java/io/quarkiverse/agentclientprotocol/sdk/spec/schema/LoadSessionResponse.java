package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response from loading an existing session.
 */
public record LoadSessionResponse(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("configOptions") List<SessionConfigOption> configOptions,
        @JsonProperty("modes") SessionModeState modes) {
}
