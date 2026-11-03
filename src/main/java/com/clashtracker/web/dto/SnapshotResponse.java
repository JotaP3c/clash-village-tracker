package com.clashtracker.web.dto;

import java.util.List;

/**
 * Normalized village snapshot projection: the entities as they were observed in the historical
 * snapshot, with completed level, max level, counts and supercharge progress per entity.
 */
public record SnapshotResponse(
        String playerTag,
        String playerName,
        int townHallLevel,
        String observedAt,
        boolean historicalSnapshot,
        int permanentBuilders,
        boolean goblinBuilderTemporary,
        List<String> declaredCategories,
        List<EntityView> entities) {

    public record EntityView(
            String entityKey,
            String name,
            String category,
            int quantity,
            Integer currentLevel,
            Integer maxLevel,
            int countAtMax,
            Integer maxSupercharge,
            Integer sc1Completed,
            Integer sc2Completed,
            Integer sc3Completed,
            String representation,
            String confidence,
            String notes) {
    }
}
