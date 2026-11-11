package com.clashtracker.domain;

import java.time.LocalDate;

public record ActiveUpgrade(
        String entityKey,
        String rawItemName,
        QueueKind queueKind,
        String rawType,
        int quantity,
        Integer fromLevel,
        Integer targetLevel,
        Long observedRemainingSeconds,
        String stage,
        String confidence,
        LocalDate observedAt,
        String sourceReference) {

    public ActiveUpgrade {
        stage = stage == null ? "" : stage;
        confidence = confidence == null ? "unknown" : confidence;
        sourceReference = sourceReference == null ? "" : sourceReference;
    }
}
