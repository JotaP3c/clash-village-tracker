package com.clashtracker.web.persistence;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Lightweight projection of a stored village snapshot row. Keeps the observation date (DATE
 * precision) distinct from the import instant, so a historical snapshot is never shown as live.
 */
public record PersistedSnapshotSummary(
        long id,
        String playerTag,
        String playerName,
        int townHallLevel,
        LocalDate observedDate,
        String observedPrecision,
        String sourceType,
        String sourceSha256,
        int entityCount,
        OffsetDateTime importedAt) {
}
