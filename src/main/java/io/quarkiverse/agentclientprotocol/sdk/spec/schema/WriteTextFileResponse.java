package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Response to `fs/write_text_file`
 */
public record WriteTextFileResponse(
        @JsonProperty("_meta") Map<String, Object> meta) {
}
