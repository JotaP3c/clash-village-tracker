package com.clashtracker.validation;

import com.clashtracker.domain.CatalogStep;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.Severity;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.normalization.LegacyKeyFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CrossValidator {
    public List<ValidationIssue> validate(VillageSnapshot snapshot, GameCatalog catalog) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, List<VillageEntity>> entities = new HashMap<>();
        for (VillageEntity entity : snapshot.entities()) {
            entities.computeIfAbsent(entity.entityKey(), ignored -> new ArrayList<>()).add(entity);
            if (entity.currentLevel() != null && entity.maxLevel() != null && entity.maxLevel() < entity.currentLevel()) {
                issues.add(issue(Severity.ERROR, "MAX_BELOW_OBSERVED_LEVEL",
                        "$.entities[" + entity.identityKey() + "].maxLevel",
                        "Maximum level is lower than the observed completed level"));
            }
        }

        Set<String> permanentTransitions = new HashSet<>();
        for (int index = 0; index < catalog.steps().size(); index++) {
            CatalogStep step = catalog.steps().get(index);
            VillageEntity entity = resolveEntity(step, entities.get(step.definition().key().entityKey()));
            if (step.definition().key().upgradeType() == UpgradeType.PERMANENT) {
                String categoryKey = LegacyKeyFactory.categoryKey(step.definition().rawCategory());
                permanentTransitions.add(transitionKey(categoryKey, step.definition().key().entityKey(),
                        step.definition().key().fromLevel(), step.definition().key().toLevel()));
            } else if (entity == null) {
                issues.add(issue(Severity.WARNING, "SC_ENTITY_NOT_OBSERVED",
                        "$.catalog.steps[" + index + "]",
                        "Supercharge step has no unambiguous matching snapshot entity"));
            } else if (entity.maxSupercharge() == null) {
                issues.add(issue(Severity.WARNING, "SC_ELIGIBILITY_UNKNOWN",
                        "$.catalog.steps[" + index + "]",
                        "Supercharge eligibility is UNKNOWN for " + entity.rawName()));
            } else if (entity.maxSupercharge() < step.definition().key().toLevel()) {
                issues.add(issue(Severity.ERROR, "SC_INCOMPATIBLE_WITH_ELIGIBILITY",
                        "$.catalog.steps[" + index + "]",
                        "Supercharge stage exceeds observed eligibility for " + entity.rawName()));
            }
        }

        for (VillageEntity entity : snapshot.entities()) {
            if (entity.currentLevel() == null || entity.maxLevel() == null || entity.currentLevel() >= entity.maxLevel()) {
                continue;
            }
            for (int from = entity.currentLevel(); from < entity.maxLevel(); from++) {
                String key = transitionKey(entity.categoryKey(), entity.entityKey(), from, from + 1);
                if (!permanentTransitions.contains(key)) {
                    issues.add(issue(Severity.WARNING, "MISSING_CATALOG_TRANSITION",
                            "$.entities[" + entity.identityKey() + "]",
                            "Catalog has no pending transition " + from + "→" + (from + 1)
                                    + " for " + entity.rawName()));
                }
            }
        }
        return List.copyOf(issues);
    }

    private static VillageEntity resolveEntity(CatalogStep step, List<VillageEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (step.definition().key().upgradeType() == UpgradeType.PERMANENT) {
            String categoryKey = LegacyKeyFactory.categoryKey(step.definition().rawCategory());
            List<VillageEntity> matching = candidates.stream()
                    .filter(entity -> categoryKey.equals(entity.categoryKey()))
                    .toList();
            return matching.size() == 1 ? matching.get(0) : null;
        }
        List<VillageEntity> eligible = candidates.stream()
                .filter(entity -> entity.maxSupercharge() != null
                        && entity.maxSupercharge() >= step.definition().key().toLevel())
                .toList();
        return eligible.size() == 1 ? eligible.get(0) : null;
    }

    private static String transitionKey(String categoryKey, String entityKey, int from, int to) {
        return categoryKey + "|" + entityKey + "|" + from + "|" + to;
    }

    private static ValidationIssue issue(Severity severity, String code, String path, String message) {
        return new ValidationIssue(severity, code, path, message);
    }
}
