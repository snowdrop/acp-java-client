package io.quarkiverse.agentclientprotocol.sdk.spec.schema;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/**
 * Response from listing sessions.
 */
public record ListSessionsResponse(
        @JsonProperty("_meta") Map<String, Object> meta,
        @JsonProperty("nextCursor") String nextCursor,
        @JsonProperty("sessions") List<SessionInfo> sessions) {
    public ListSessionsResponse(List<SessionInfo> sessions) {
        this(null, null, sessions);
    }
}
