package com.clashtracker.web.dto;

import java.util.List;

/**
 * Read-only dashboard projection returned by the API. All figures derive from the offline
 * calculation of the historical snapshot and versioned catalog. Progress is a cataloged
 * step-count metric, never an official Supercell percentage and never time/resource spent.
 */
public record DashboardResponse(
        String status,
        String message,
        VillageInfo village,
        ProgressSummary progress,
        DurationSummaryDto durations,
        BuildersSummary builders,
        List<CategorySummary> categories,
        DataQuality dataQuality) {

    public record VillageInfo(
            String playerTag,
            String playerName,
            int townHallLevel,
            String snapshotDate,
            boolean historicalSnapshot,
            double goldPassBoost,
            int permanentBuilders,
            boolean goblinBuilderTemporary) {
    }

    public record ProgressSummary(
            double overallProgressPercent,
            long overallCompletedSteps,
            long overallTotalSteps,
            long overallRemainingSteps,
            double permanentProgressPercent,
            long permanentCompletedSteps,
            long permanentTotalSteps,
            double superchargeProgressPercent,
            long superchargeCompletedSteps,
            long superchargeTotalSteps,
            boolean fullCertified,
            String fullCertificationMessage) {
    }

    public record DurationSummaryDto(
            long scenarioKnownSeconds,
            double scenarioKnownBuilderDays,
            long baseKnownSeconds,
            long discountedFutureKnownSeconds,
            long observedRemainingSeconds,
            boolean exactTotalAvailable,
            int unknownStepKeyCount) {
    }

    public record BuildersSummary(
            int permanentBuilderCapacity,
            boolean goblinBuilderTemporary,
            double builderQueueDays,
            double laboratoryQueueDays,
            double petQueueDays,
            double estimatedBuilderCalendarDaysLowerBound) {
    }

    public record CategorySummary(
            String category,
            long completedSteps,
            long totalSteps,
            long remainingSteps,
            double progressPercent,
            int entityCount,
            int unknownEntityCount) {
    }

    public record DataQuality(
            double coveragePercent,
            int unknownEntityCount,
            int potentiallyMissingEntityCount,
            int unknownUpgradeStepCount,
            int validationIssueCount) {
    }
}
