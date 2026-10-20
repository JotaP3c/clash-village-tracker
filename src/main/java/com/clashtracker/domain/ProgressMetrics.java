package com.clashtracker.domain;

import java.math.BigDecimal;
import java.util.List;

public record ProgressMetrics(
        long permanentCompletedSteps,
        long permanentTotalSteps,
        BigDecimal permanentProgress,
        long superchargeCompletedSteps,
        long superchargeTotalSteps,
        BigDecimal superchargeProgress,
        long overallCompletedSteps,
        long overallTotalSteps,
        BigDecimal overallCataloguedProgress,
        BigDecimal coverage,
        int unknownEntityCount,
        int potentiallyMissingEntityCount,
        int unknownUpgradeStepCount,
        boolean fullCertified,
        String fullCertificationMessage,
        List<CategoryProgress> categories) {

    public ProgressMetrics {
        categories = List.copyOf(categories);
    }
}
