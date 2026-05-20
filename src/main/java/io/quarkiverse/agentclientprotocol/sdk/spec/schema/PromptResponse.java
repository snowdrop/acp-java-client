package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Response from processing a user prompt.
 * 
 * See protocol docs: [Check for Completion](https://agentclientprotocol.com/protocol/prompt-turn#4-check-for-completion)
 */
public record PromptResponse(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("stopReason") StopReason stopReason) {
    public PromptResponse(StopReason stopReason) {
        this(null, stopReason);
    }
}
