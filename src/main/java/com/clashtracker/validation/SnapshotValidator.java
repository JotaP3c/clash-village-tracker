package com.clashtracker.validation;

import com.clashtracker.domain.Severity;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.importer.raw.RawQueueEntry;
import com.clashtracker.importer.raw.RawVillageItem;
import com.clashtracker.importer.raw.RawVillageSnapshot;
import com.clashtracker.normalization.LegacyKeyFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public final class SnapshotValidator {
    public List<ValidationIssue> validate(RawVillageSnapshot snapshot) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (snapshot == null) {
            return List.of(issue(Severity.ERROR, "SNAPSHOT_NULL", "$", "Snapshot content is null"));
        }
        requiredText(snapshot.playerTag(), "$.player_tag", "PLAYER_TAG_MISSING", issues);
        requiredText(snapshot.playerName(), "$.player_name", "PLAYER_NAME_MISSING", issues);
        if (snapshot.date() == null) {
            issues.add(issue(Severity.ERROR, "OBSERVATION_DATE_MISSING", "$.date", "Snapshot observation date is required"));
        }
        if (snapshot.townHall() == null || snapshot.townHall() <= 0) {
            issues.add(issue(Severity.ERROR, "TOWN_HALL_INVALID", "$.town_hall", "Town hall must be a positive integer"));
        }
        if (snapshot.buildersPermanent() == null || snapshot.buildersPermanent() <= 0) {
            issues.add(issue(Severity.ERROR, "BUILDER_CAPACITY_INVALID", "$.builders_permanent", "Permanent builder capacity must be positive"));
        }
        BigDecimal boost = snapshot.goldPassTimeReduction();
        if (boost != null && (boost.compareTo(BigDecimal.ZERO) < 0 || boost.compareTo(BigDecimal.ONE) > 0)) {
            issues.add(issue(Severity.ERROR, "SNAPSHOT_BOOST_OUT_OF_RANGE", "$.gold_pass_time_reduction", "Gold Pass boost must be between 0 and 1"));
        }
        if (snapshot.categories() == null || snapshot.categories().isEmpty()) {
            issues.add(issue(Severity.ERROR, "CATEGORIES_MISSING", "$.categories", "Declared categories are required"));
        } else if (new HashSet<>(snapshot.categories()).size() != snapshot.categories().size()) {
            issues.add(issue(Severity.WARNING, "DUPLICATE_CATEGORY", "$.categories", "Declared category list contains duplicates"));
        }
        if (snapshot.items() == null) {
            issues.add(issue(Severity.ERROR, "ITEMS_MISSING", "$.items", "Items array is required"));
        } else {
            validateItems(snapshot.items(), issues);
        }
        if (snapshot.queue() == null) {
            issues.add(issue(Severity.ERROR, "QUEUE_MISSING", "$.queue", "Queue array is required (it may be empty)"));
        } else {
            validateQueue(snapshot, issues);
        }
        return List.copyOf(issues);
    }

    private static void validateItems(List<RawVillageItem> items, List<ValidationIssue> issues) {
        Map<String, RawVillageItem> seen = new HashMap<>();
        for (int index = 0; index < items.size(); index++) {
            RawVillageItem item = items.get(index);
            String path = "$.items[" + index + "]";
            if (item == null) {
                issues.add(issue(Severity.ERROR, "ITEM_NULL", path, "Item entry is null"));
                continue;
            }
            requiredText(item.category(), path + ".category", "ITEM_CATEGORY_MISSING", issues);
            requiredText(item.name(), path + ".name", "ITEM_NAME_MISSING", issues);
            Integer quantity = item.quantity();
            if (quantity == null || quantity < 1) {
                issues.add(issue(Severity.ERROR, "QUANTITY_INVALID", path + ".quantity", "Quantity must be at least 1"));
            }
            if (item.currentLevel() != null && item.currentLevel() < 0) {
                issues.add(issue(Severity.ERROR, "CURRENT_LEVEL_NEGATIVE", path + ".current_level", "Completed level cannot be negative"));
            }
            if (item.maxLevel() != null && item.maxLevel() < 0) {
                issues.add(issue(Severity.ERROR, "MAX_LEVEL_NEGATIVE", path + ".max_level", "Maximum level cannot be negative"));
            }
            if (item.currentLevel() != null && item.maxLevel() != null && item.currentLevel() > item.maxLevel()) {
                issues.add(issue(Severity.ERROR, "CURRENT_LEVEL_ABOVE_MAX", path + ".current_level", "Completed level is greater than the declared maximum"));
            }
            int countAtMax = requiredOrZero(item.countAtMax());
            if (item.countAtMax() == null || countAtMax < 0 || quantity != null && countAtMax > quantity) {
                issues.add(issue(Severity.ERROR, "COUNT_DISTRIBUTION_INCOHERENT", path + ".count_at_max", "count_at_max must be between 0 and quantity"));
            }
            if ((item.currentLevel() == null || item.maxLevel() == null) && countAtMax > 0) {
                issues.add(issue(Severity.WARNING, "COUNT_AT_MAX_WITH_UNKNOWN_LEVEL", path + ".count_at_max", "A count is marked at max while current or maximum level is unknown"));
            }
            validateSupercharges(item, countAtMax, path, issues);
            if (item.currentLevel() == null) {
                issues.add(issue(Severity.WARNING, "CURRENT_LEVEL_UNKNOWN", path + ".current_level", "Completed level is unknown and blocks complete coverage"));
            }
            if (item.maxLevel() == null) {
                issues.add(issue(Severity.WARNING, "MAX_LEVEL_UNKNOWN", path + ".max_level", "Maximum level is unknown and blocks complete coverage"));
            }
            String logicalKey = (item.category() == null ? "" : item.category()) + "|" + LegacyKeyFactory.fromLegacyName(item.name());
            RawVillageItem previous = seen.putIfAbsent(logicalKey, item);
            if (previous != null) {
                Severity severity = previous.equals(item) ? Severity.INFO : Severity.ERROR;
                String code = previous.equals(item) ? "DUPLICATE_ITEM_DEDUPLICATED" : "DUPLICATE_ITEM_CONFLICT";
                issues.add(issue(severity, code, path, "Duplicate logical item key: " + logicalKey));
            }
        }
    }

    private static void validateSupercharges(RawVillageItem item, int countAtMax, String path,
                                             List<ValidationIssue> issues) {
        Integer maxSc = item.maxSupercharge();
        Integer sc1 = item.sc1Completed();
        Integer sc2 = item.sc2Completed();
        Integer sc3 = item.sc3Completed();
        if (maxSc == null || sc1 == null || sc2 == null || sc3 == null) {
            issues.add(issue(Severity.WARNING, "SUPERCHARGE_STATE_UNKNOWN", path,
                    "Missing Supercharge value is UNKNOWN and must not be interpreted as zero"));
        }
        if (negative(maxSc) || negative(sc1) || negative(sc2) || negative(sc3)) {
            issues.add(issue(Severity.ERROR, "SUPERCHARGE_COUNT_NEGATIVE", path,
                    "Supercharge values cannot be negative"));
        }
        if (sc1 != null && sc2 != null && sc3 != null
                && (sc3 > sc2 || sc2 > sc1 || sc1 > countAtMax)) {
            issues.add(issue(Severity.ERROR, "SUPERCHARGE_DISTRIBUTION_INCOHERENT", path,
                    "Expected sc3 <= sc2 <= sc1 <= count_at_max"));
        }
        int completed = nullableValue(sc1) + nullableValue(sc2) + nullableValue(sc3);
        if (maxSc != null && maxSc == 0 && completed > 0) {
            issues.add(issue(Severity.ERROR, "SUPERCHARGE_NOT_ELIGIBLE", path,
                    "Observed supercharge exists for an item declared ineligible"));
        }
        if (maxSc != null && ((maxSc < 1 && nullableValue(sc1) > 0)
                || (maxSc < 2 && nullableValue(sc2) > 0)
                || (maxSc < 3 && nullableValue(sc3) > 0))) {
            issues.add(issue(Severity.ERROR, "SUPERCHARGE_STAGE_ABOVE_MAX", path,
                    "Completed Supercharge stage exceeds declared eligibility"));
        }
    }

    private static void validateQueue(RawVillageSnapshot snapshot, List<ValidationIssue> issues) {
        for (int index = 0; index < snapshot.queue().size(); index++) {
            RawQueueEntry job = snapshot.queue().get(index);
            String path = "$.queue[" + index + "]";
            if (job == null) {
                issues.add(issue(Severity.ERROR, "QUEUE_ENTRY_NULL", path, "Queue entry is null"));
                continue;
            }
            requiredText(job.item(), path + ".item", "QUEUE_ITEM_MISSING", issues);
            if (job.qty() == null || job.qty() < 1) {
                issues.add(issue(Severity.ERROR, "QUEUE_QUANTITY_INVALID", path + ".qty", "Queue quantity must be at least 1"));
            }
            if (job.fromLevel() != null && job.fromLevel() < 0 || job.toLevel() != null && job.toLevel() < 0) {
                issues.add(issue(Severity.ERROR, "QUEUE_LEVEL_NEGATIVE", path, "Queue levels cannot be negative"));
            }
            if (job.fromLevel() != null && job.toLevel() != null && job.toLevel() < job.fromLevel()) {
                issues.add(issue(Severity.ERROR, "TARGET_BELOW_CURRENT", path + ".to_level", "Target level cannot be lower than completed level"));
            }
            boolean supercharge = job.stage() != null && job.stage().toUpperCase().contains("SC");
            if (!supercharge && job.fromLevel() != null && job.toLevel() != null && job.toLevel().equals(job.fromLevel())) {
                issues.add(issue(Severity.ERROR, "TARGET_NOT_ADVANCING", path + ".to_level", "A permanent target must advance exactly from the completed level"));
            }
            if (job.toLevel() == null) {
                issues.add(issue(Severity.WARNING, "TARGET_LEVEL_UNKNOWN", path + ".to_level", "Queue target is unknown; completed level remains unchanged"));
            }
            if (job.remainingDays() != null && job.remainingDays().signum() < 0) {
                issues.add(issue(Severity.ERROR, "REMAINING_DURATION_NEGATIVE", path + ".remaining_days", "Observed remaining duration cannot be negative"));
            }
            if (job.remainingDays() != null && snapshot.date() == null) {
                issues.add(issue(Severity.ERROR, "TIMER_WITHOUT_OBSERVATION_DATE", path + ".remaining_days", "A real observed timer requires an observation date"));
            }
        }
    }

    private static boolean negative(Integer value) {
        return value != null && value < 0;
    }

    private static int nullableValue(Integer value) {
        return value == null ? 0 : value;
    }

    private static int requiredOrZero(Integer value) {
        return value == null ? 0 : value;
    }

    private static void requiredText(String value, String path, String code, List<ValidationIssue> issues) {
        if (value == null || value.isBlank()) {
            issues.add(issue(Severity.ERROR, code, path, "Required text value is missing"));
        }
    }

    private static ValidationIssue issue(Severity severity, String code, String path, String message) {
        return new ValidationIssue(severity, code, path, message);
    }
}
