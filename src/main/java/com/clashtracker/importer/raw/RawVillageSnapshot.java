package com.clashtracker.importer.raw;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record RawVillageSnapshot(
        LocalDate date,
        @JsonProperty("player_tag") String playerTag,
        @JsonProperty("player_name") String playerName,
        @JsonProperty("town_hall") Integer townHall,
        @JsonProperty("builders_permanent") Integer buildersPermanent,
        @JsonProperty("builder_goblin_temporary") Boolean builderGoblinTemporary,
        @JsonProperty("gold_pass_time_reduction") BigDecimal goldPassTimeReduction,
        List<String> categories,
        List<RawVillageItem> items,
        List<RawQueueEntry> queue,
        String note) {
}
