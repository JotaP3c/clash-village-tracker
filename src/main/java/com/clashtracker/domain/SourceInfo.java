package com.clashtracker.domain;

import java.time.LocalDate;

public record SourceInfo(String sourceType, String sourceReference, LocalDate observedAt, String confidence) {
    public SourceInfo {
        sourceType = sourceType == null || sourceType.isBlank() ? "UNKNOWN" : sourceType;
        sourceReference = sourceReference == null ? "" : sourceReference;
        confidence = confidence == null || confidence.isBlank() ? "unknown" : confidence;
    }
}
