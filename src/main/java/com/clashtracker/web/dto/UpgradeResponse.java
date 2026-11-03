package com.clashtracker.web.dto;

import java.util.List;

/**
 * Pending upgrade steps derived from the catalog and snapshot. Each row keeps its upgrade type
 * (permanent vs supercharge), queue, counts, and known/unknown duration. Durations are expressed
 * in seconds (precise) and days (display). A null day value means the duration is unknown.
 */
public record UpgradeResponse(
        int totalRows,
        int inProgressRows,
        List<UpgradeRow> upgrades) {

    public record UpgradeRow(
            String category,
            String item,
            String stage,
            String upgradeType,
            int fromLevel,
            int toLevel,
            int count,
            int activeCount,
            int futureCount,
            String queue,
            Long baseDurationSecondsPerUnit,
            Double baseDurationDaysPerUnit,
            Long discountedDurationSecondsPerUnit,
            Double discountedDurationDaysPerUnit,
            long observedRemainingSecondsTotal,
            boolean durationKnown,
            String source,
            String confidence) {
    }
}
