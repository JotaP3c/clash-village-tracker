package com.clashtracker.domain;

import java.math.BigDecimal;

public record CategoryProgress(
        String category,
        long completedSteps,
        long totalSteps,
        long remainingSteps,
        BigDecimal progress,
        int entityCount,
        int unknownEntityCount) {
}
