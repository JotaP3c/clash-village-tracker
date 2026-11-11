package com.clashtracker.domain;

import java.util.List;

public record CalculationResult(
        List<CalculatedStep> steps,
        DurationSummary durations,
        ProgressMetrics progress,
        QueueWorkload workloads) {

    public CalculationResult {
        steps = List.copyOf(steps);
    }
}
