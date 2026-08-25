package com.smarthealthdog.backend.dto.sunlight;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LightSampleBatchResponse(

        @JsonProperty("received_count")
        int receivedCount,

        @JsonProperty("saved_count")
        int savedCount,

        @JsonProperty("duplicate_count")
        int duplicateCount

) {
}