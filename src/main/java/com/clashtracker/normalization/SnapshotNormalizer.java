package com.clashtracker.normalization;

import com.clashtracker.domain.ActiveUpgrade;
import com.clashtracker.domain.QueueKind;
import com.clashtracker.domain.Representation;
import com.clashtracker.domain.SourceInfo;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.importer.LoadedSnapshot;
import com.clashtracker.importer.raw.RawQueueEntry;
import com.clashtracker.importer.raw.RawVillageItem;
import com.clashtracker.importer.raw.RawVillageSnapshot;
import com.clashtracker.util.TimeValues;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotNormalizer {
    public VillageSnapshot normalize(LoadedSnapshot loaded) {
        RawVillageSnapshot raw = loaded.raw();
        Map<String, VillageEntity> uniqueEntities = new LinkedHashMap<>();
        for (RawVillageItem item : safe(raw.items())) {
            String key = LegacyKeyFactory.fromLegacyName(item.name());
            String categoryKey = LegacyKeyFactory.categoryKey(item.category());
            VillageEntity entity = new VillageEntity(
                    key,
                    item.name(),
                    item.name(),
                    item.category(),
                    categoryKey,
                    requiredValue(item.quantity()),
                    item.currentLevel(),
                    item.maxLevel(),
                    requiredValue(item.countAtMax()),
                    item.maxSupercharge(),
                    item.sc1Completed(),
                    item.sc2Completed(),
                    item.sc3Completed(),
                    requiredValue(item.quantity()) > 1 ? Representation.AGGREGATED : Representation.INDIVIDUAL,
                    item.source(),
                    item.confidence(),
                    item.notes(),
                    new SourceInfo("MANUAL_SNAPSHOT", loaded.sourcePath().toString(), raw.date(), item.confidence()));
            uniqueEntities.putIfAbsent(entity.identityKey(), entity);
        }

        List<ActiveUpgrade> jobs = new ArrayList<>();
        for (RawQueueEntry queue : safe(raw.queue())) {
            jobs.add(new ActiveUpgrade(
                    LegacyKeyFactory.fromLegacyName(queue.item()),
                    queue.item(),
                    queueKind(queue.type()),
                    queue.type(),
                    requiredValue(queue.qty()),
                    queue.fromLevel(),
                    queue.toLevel(),
                    TimeValues.daysToSeconds(queue.remainingDays()),
                    queue.stage(),
                    queue.confidence(),
                    raw.date(),
                    loaded.sourcePath().toString()));
        }

        return new VillageSnapshot(
                raw.date(), raw.playerTag(), raw.playerName(), requiredValue(raw.townHall()),
                requiredValue(raw.buildersPermanent()), Boolean.TRUE.equals(raw.builderGoblinTemporary()),
                raw.goldPassTimeReduction(), safe(raw.categories()),
                new ArrayList<>(uniqueEntities.values()), jobs, raw.note(),
                loaded.sourcePath().toString(), loaded.sourceSha256());
    }

    private static QueueKind queueKind(String type) {
        if (type == null) {
            return QueueKind.BUILDER;
        }
        String normalized = LegacyKeyFactory.fromLegacyName(type);
        if (normalized.contains("laboratorio")) {
            return QueueKind.LABORATORY;
        }
        if (normalized.contains("pet")) {
            return QueueKind.PET;
        }
        return QueueKind.BUILDER;
    }

    private static int requiredValue(Integer value) {
        if (value == null) {
            throw new IllegalStateException("Required numeric value was not validated before normalization");
        }
        return value;
    }

    private static <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
