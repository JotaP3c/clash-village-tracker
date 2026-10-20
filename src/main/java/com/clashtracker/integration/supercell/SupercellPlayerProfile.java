package com.clashtracker.integration.supercell;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Documented top-level fields of the official player resource. Array elements intentionally remain
 * opaque until their current schema is verified from an official contract or a genuine response.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SupercellPlayerProfile(
        String tag,
        String name,
        Integer townHallLevel,
        Integer expLevel,
        JsonNode troops,
        JsonNode spells,
        JsonNode heroes,
        JsonNode heroEquipment) {
}
