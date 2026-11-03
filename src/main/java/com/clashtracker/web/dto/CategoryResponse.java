package com.clashtracker.web.dto;

import java.util.List;

/**
 * Per-category progress with the entities that belong to each category, so the frontend can render
 * a category navigation with the relevant level/supercharge detail.
 */
public record CategoryResponse(
        List<CategoryDetail> categories) {

    public record CategoryDetail(
            String category,
            long completedSteps,
            long totalSteps,
            long remainingSteps,
            double progressPercent,
            int entityCount,
            int unknownEntityCount,
            List<SnapshotResponse.EntityView> entities) {
    }
}
