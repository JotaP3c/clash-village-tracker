package com.clashtracker.reconciliation;

import com.clashtracker.domain.Severity;
import com.clashtracker.domain.SourceInfo;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SnapshotReconciler {
    public ReconciliationResult reconcile(VillageSnapshot previous, VillageSnapshot incoming) {
        if (previous == null) {
            return new ReconciliationResult(incoming, List.of());
        }
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, VillageEntity> previousByKey = index(previous.entities());
        Map<String, VillageEntity> merged = new LinkedHashMap<>();

        for (VillageEntity current : incoming.entities()) {
            String key = current.identityKey();
            VillageEntity old = previousByKey.remove(key);
            if (old == null) {
                merged.put(key, current);
                continue;
            }
            Integer currentLevel = mergeNonDecreasing(old.currentLevel(), current.currentLevel(),
                    current.entityKey(), "currentLevel", issues);
            Integer maxLevel = preferIncoming(old.maxLevel(), current.maxLevel(), current.entityKey(),
                    "maxLevel", issues);
            if (maxLevel != null && currentLevel != null && maxLevel < currentLevel) {
                issues.add(issue(Severity.ERROR, "MAX_BELOW_RECONCILED_LEVEL", current.entityKey(),
                        "Reconciled maximum is lower than the preserved completed level"));
                maxLevel = old.maxLevel() != null && old.maxLevel() >= currentLevel ? old.maxLevel() : maxLevel;
            }
            merged.put(key, mergeEntity(old, current, currentLevel, maxLevel, issues));
        }
        for (Map.Entry<String, VillageEntity> omitted : previousByKey.entrySet()) {
            merged.put(omitted.getKey(), omitted.getValue());
            issues.add(issue(Severity.WARNING, "KNOWN_ENTITY_PRESERVED", omitted.getValue().entityKey(),
                    "Incoming source omitted a previously known entity; it was preserved for review"));
        }
        return new ReconciliationResult(incoming.withEntities(new ArrayList<>(merged.values())), issues);
    }

    private static Integer mergeNonDecreasing(Integer oldValue, Integer incomingValue, String entityKey,
                                               String field, List<ValidationIssue> issues) {
        String preservedCode = "currentLevel".equals(field)
                ? "KNOWN_LEVEL_PRESERVED" : "KNOWN_VALUE_PRESERVED";
        String regressionCode = "currentLevel".equals(field)
                ? "LEVEL_REGRESSION_CONFLICT" : "VALUE_REGRESSION_CONFLICT";
        if (incomingValue == null) {
            if (oldValue != null) {
                issues.add(issue(Severity.INFO, preservedCode, entityKey,
                        "Incoming source omitted %s; previous known value was preserved".formatted(field)));
            }
            return oldValue;
        }
        if (oldValue != null && incomingValue < oldValue) {
            issues.add(issue(Severity.WARNING, regressionCode, entityKey,
                    "Incoming %s %d is lower than previous %d; previous value was preserved"
                            .formatted(field, incomingValue, oldValue)));
            return oldValue;
        }
        return incomingValue;
    }

    private static Integer preferIncoming(Integer oldValue, Integer incomingValue, String entityKey,
                                          String field, List<ValidationIssue> issues) {
        if (incomingValue != null) {
            return incomingValue;
        }
        if (oldValue != null) {
            String code = "maxLevel".equals(field) ? "KNOWN_MAX_PRESERVED" : "KNOWN_VALUE_PRESERVED";
            issues.add(issue(Severity.INFO, code, entityKey,
                    "Incoming source omitted %s; previous known value was preserved".formatted(field)));
        }
        return oldValue;
    }

    private static VillageEntity mergeEntity(VillageEntity old, VillageEntity current,
                                              Integer currentLevel, Integer maxLevel,
                                              List<ValidationIssue> issues) {
        String source = blank(current.source()) ? old.source() : current.source();
        String confidence = blank(current.confidence()) ? old.confidence() : current.confidence();
        String notes = blank(current.notes()) ? old.notes() : current.notes();
        Integer maxSc = preferIncoming(old.maxSupercharge(), current.maxSupercharge(), current.entityKey(),
                "maxSupercharge", issues);
        Integer sc1 = mergeNonDecreasing(old.sc1Completed(), current.sc1Completed(), current.entityKey(),
                "sc1Completed", issues);
        Integer sc2 = mergeNonDecreasing(old.sc2Completed(), current.sc2Completed(), current.entityKey(),
                "sc2Completed", issues);
        Integer sc3 = mergeNonDecreasing(old.sc3Completed(), current.sc3Completed(), current.entityKey(),
                "sc3Completed", issues);
        SourceInfo provenance = current.provenance() == null ? old.provenance() : current.provenance();
        return new VillageEntity(current.entityKey(), current.rawName(), current.displayNamePtBr(),
                current.rawCategory(), current.categoryKey(), current.quantity(), currentLevel, maxLevel,
                current.countAtMax(), maxSc, sc1, sc2, sc3, current.representation(), source,
                confidence, notes, provenance);
    }

    private static Map<String, VillageEntity> index(List<VillageEntity> entities) {
        Map<String, VillageEntity> result = new LinkedHashMap<>();
        for (VillageEntity entity : entities) {
            result.putIfAbsent(entity.identityKey(), entity);
        }
        return result;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ValidationIssue issue(Severity severity, String code, String entityKey, String message) {
        return new ValidationIssue(severity, code, "$.entities[" + entityKey + "]", message);
    }
}
