package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * The user selected one of the provided options.
 */
public record SelectedPermissionOutcome(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("optionId") String optionId) {
    public SelectedPermissionOutcome(String optionId) {
        this(null, optionId);
    }
}
