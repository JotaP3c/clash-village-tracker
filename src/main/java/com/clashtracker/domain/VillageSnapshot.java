package com.clashtracker.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record VillageSnapshot(
        LocalDate observedAt,
        String playerTag,
        String playerName,
        int townHallLevel,
        int permanentBuilderCapacity,
        boolean goblinBuilderTemporary,
        BigDecimal snapshotGoldPassBoost,
        List<String> declaredCategories,
        List<VillageEntity> entities,
        List<ActiveUpgrade> activeUpgrades,
        String rawNote,
        String sourcePath,
        String sourceSha256) {

    public VillageSnapshot {
        declaredCategories = List.copyOf(declaredCategories);
        entities = List.copyOf(entities);
        activeUpgrades = List.copyOf(activeUpgrades);
        snapshotGoldPassBoost = snapshotGoldPassBoost == null ? BigDecimal.ZERO : snapshotGoldPassBoost;
        rawNote = rawNote == null ? "" : rawNote;
    }

    public VillageSnapshot withEntities(List<VillageEntity> mergedEntities) {
        return new VillageSnapshot(observedAt, playerTag, playerName, townHallLevel,
                permanentBuilderCapacity, goblinBuilderTemporary, snapshotGoldPassBoost,
                declaredCategories, mergedEntities, activeUpgrades, rawNote, sourcePath, sourceSha256);
    }
}
