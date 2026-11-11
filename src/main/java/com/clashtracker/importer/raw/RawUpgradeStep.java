package com.clashtracker.importer.raw;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public record RawUpgradeStep(
        String category,
        String item,
        @JsonProperty("from_level") Integer fromLevel,
        @JsonProperty("to_level") Integer toLevel,
        String stage,
        Integer count,
        @JsonProperty("base_days") BigDecimal baseDays,
        @JsonProperty("pass_days") BigDecimal passDays,
        @JsonProperty("in_progress") Boolean inProgress,
        @JsonProperty("remaining_now_days") BigDecimal remainingNowDays,
        String source,
        String confidence) {
}
