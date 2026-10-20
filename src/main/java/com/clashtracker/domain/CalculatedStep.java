package com.clashtracker.domain;

public record CalculatedStep(
        CatalogStepKey key,
        String rawCategory,
        String rawItemName,
        String stage,
        int count,
        int activeCount,
        int futureCount,
        QueueKind queueKind,
        Long baseDurationSecondsPerUnit,
        Long discountedDurationSecondsPerUnit,
        long observedRemainingSecondsTotal,
        long baseKnownSeconds,
        long scenarioKnownSeconds,
        boolean exactForRow,
        String source,
        String confidence) {
}
