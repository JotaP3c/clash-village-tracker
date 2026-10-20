package com.clashtracker.domain;

public record UpgradeStepDefinition(
        CatalogStepKey key,
        String rawCategory,
        String rawItemName,
        String stage,
        Long baseDurationSeconds,
        String source,
        String confidence,
        String durationOrigin) {

    public UpgradeStepDefinition {
        source = source == null ? "" : source;
        confidence = confidence == null ? "unknown" : confidence;
        durationOrigin = durationOrigin == null ? "UNKNOWN" : durationOrigin;
    }
}
