package com.clashtracker.domain;

public record PendingStepState(
        CatalogStepKey key,
        int count,
        boolean inProgress,
        Long observedRemainingSeconds,
        Long legacyPassDurationSeconds) {
}
