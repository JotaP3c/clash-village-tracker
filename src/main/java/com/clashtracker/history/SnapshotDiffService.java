package com.clashtracker.history;

import com.clashtracker.domain.DiffEntry;
import com.clashtracker.domain.SnapshotDiff;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotDiffService {
    public SnapshotDiff compare(VillageSnapshot previous, VillageSnapshot current) {
        Map<String, VillageEntity> oldByKey = index(previous);
        List<DiffEntry> changes = new ArrayList<>();
        for (VillageEntity entity : current.entities()) {
            String identity = entity.identityKey();
            VillageEntity old = oldByKey.remove(identity);
            if (old == null) {
                changes.add(new DiffEntry(identity, "entity", null, entity.rawName(),
                        "CHANGE_CONFIRMED", "Entity appears in current snapshot"));
                continue;
            }
            compareValue(identity, "currentLevel", old.currentLevel(), entity.currentLevel(), changes, true);
            compareValue(identity, "maxLevel", old.maxLevel(), entity.maxLevel(), changes, false);
            compareValue(identity, "quantity", old.quantity(), entity.quantity(), changes, false);
            compareValue(identity, "sc1Completed", old.sc1Completed(), entity.sc1Completed(), changes, true);
            compareValue(identity, "sc2Completed", old.sc2Completed(), entity.sc2Completed(), changes, true);
            compareValue(identity, "sc3Completed", old.sc3Completed(), entity.sc3Completed(), changes, true);
        }
        for (VillageEntity missing : oldByKey.values()) {
            changes.add(new DiffEntry(missing.identityKey(), "entity", missing.rawName(), null,
                    "CONFLICT", "Previously known entity is absent from current raw snapshot"));
        }
        return new SnapshotDiff(previous.sourceSha256(), current.sourceSha256(), previous.observedAt(),
                current.observedAt(), changes);
    }

    private static void compareValue(String key, String field, Object oldValue, Object newValue,
                                     List<DiffEntry> changes, boolean regressionSensitive) {
        if (java.util.Objects.equals(oldValue, newValue)) {
            return;
        }
        String classification = "CHANGE_CONFIRMED";
        if (regressionSensitive && oldValue instanceof Number oldNumber && newValue instanceof Number newNumber
                && newNumber.longValue() < oldNumber.longValue()) {
            classification = "CONFLICT";
        }
        changes.add(new DiffEntry(key, field, oldValue, newValue, classification,
                "Field changed from %s to %s".formatted(oldValue, newValue)));
    }

    private static Map<String, VillageEntity> index(VillageSnapshot snapshot) {
        Map<String, VillageEntity> result = new LinkedHashMap<>();
        for (VillageEntity entity : snapshot.entities()) {
            result.put(entity.identityKey(), entity);
        }
        return result;
    }
}
