package com.petsitting.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.Map;
import com.petsitting.BookingService;

/** Typed tool contracts. Sensitive address details stay in the authenticated UI. */
public final class AgentDtos {
    private AgentDtos() {}
    public record PetOption(@JsonProperty("ID") String id,@JsonProperty("NAME") String name,
        @JsonProperty("SPECIES") String species,@JsonProperty("NOTES") String notes) {}
    public record AddressOption(@JsonProperty("ID") String id,@JsonProperty("REGION") String region,
        @JsonProperty("CONTACT") String contact) {}
    public record SlotOption(@JsonProperty("ID") String id,@JsonProperty("SITTER_NAME") String sitterName,
        @JsonProperty("STARTS_AT") OffsetDateTime startsAt,@JsonProperty("ENDS_AT") OffsetDateTime endsAt,
        @JsonProperty("PRICE_CENTS") int priceCents) {
        public static SlotOption from(Map<String,Object> row) {
            return new SlotOption((String)row.get("ID"),(String)row.get("SITTER_NAME"),
                BookingService.time(row.get("STARTS_AT")),BookingService.time(row.get("ENDS_AT")),
                ((Number)row.get("PRICE_CENTS")).intValue());
        }
    }
    public record DraftSummary(@JsonProperty("ID") String id,@JsonProperty("PET_NAME") String petName,
        @JsonProperty("SITTER_NAME") String sitterName,@JsonProperty("STARTS_AT") OffsetDateTime startsAt,
        @JsonProperty("ENDS_AT") OffsetDateTime endsAt,@JsonProperty("PRICE_CENTS") int priceCents) {
        public static DraftSummary from(Map<String,Object> row) {
            return new DraftSummary((String)row.get("ID"),(String)row.get("PET_NAME"),(String)row.get("SITTER_NAME"),
                BookingService.time(row.get("STARTS_AT")),BookingService.time(row.get("ENDS_AT")),
                ((Number)row.get("PRICE_CENTS")).intValue());
        }
    }
    public record ChatResponse(String reply,Map<String,Object> draft,long durationMs,String runId) {
        @SuppressWarnings("unchecked") public static ChatResponse from(Map<String,Object> result) {
            return new ChatResponse((String)result.get("reply"),(Map<String,Object>)result.get("draft"),
                ((Number)result.get("durationMs")).longValue(),(String)result.get("runId"));
        }
    }
    public record ToolTrace(String id,int sequence,String toolName,String argumentsJson,String resultJson,
        String outcome,String errorCode,long durationMs,OffsetDateTime createdAt) {}
}
