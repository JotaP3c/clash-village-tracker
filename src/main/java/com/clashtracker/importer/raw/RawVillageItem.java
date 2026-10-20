package com.clashtracker.importer.raw;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RawVillageItem(
        String category,
        String name,
        Integer quantity,
        @JsonProperty("current_level") Integer currentLevel,
        @JsonProperty("max_level") Integer maxLevel,
        @JsonProperty("count_at_max") Integer countAtMax,
        @JsonProperty("max_supercharge") Integer maxSupercharge,
        @JsonProperty("sc1_completed") Integer sc1Completed,
        @JsonProperty("sc2_completed") Integer sc2Completed,
        @JsonProperty("sc3_completed") Integer sc3Completed,
        String confidence,
        String notes,
        String source) {
}
