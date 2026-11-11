package com.clashtracker.importer.raw;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record RawQueueEntry(
        String item,
        String type,
        Integer qty,
        @JsonProperty("from_level") Integer fromLevel,
        @JsonProperty("to_level") Integer toLevel,
        @JsonProperty("remaining_days") BigDecimal remainingDays,
        String stage,
        String confidence) {
}
