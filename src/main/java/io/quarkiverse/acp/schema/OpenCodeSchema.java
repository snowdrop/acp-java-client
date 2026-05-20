package io.quarkiverse.acp.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class OpenCodeSchema {

    private OpenCodeSchema() {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AiMessageEvent(
            String type,
            long timestamp,
            @JsonProperty("sessionID") String sessionId,
            Part part
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(
            String id,
            @JsonProperty("messageID") String messageId,
            @JsonProperty("sessionID") String sessionId,
            String type,
            String text,
            TimeRange time
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TimeRange(long start, long end) {}
}
