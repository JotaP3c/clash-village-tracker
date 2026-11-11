package com.clashtracker.domain;

import java.util.List;

public record DurationSummary(
        long baseKnownSeconds,
        long discountedFutureKnownSeconds,
        long observedRemainingSeconds,
        long scenarioKnownSeconds,
        boolean exactTotalAvailable,
        List<String> missingStepKeys) {

    public DurationSummary {
        missingStepKeys = List.copyOf(missingStepKeys);
    }
}
