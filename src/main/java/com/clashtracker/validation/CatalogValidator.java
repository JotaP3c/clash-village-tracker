package com.clashtracker.validation;

import com.clashtracker.catalog.LoadedCatalog;
import com.clashtracker.domain.CatalogStep;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.Severity;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.importer.raw.RawUpgradeStep;
import com.clashtracker.normalization.LegacyKeyFactory;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CatalogValidator {
    public List<ValidationIssue> validateRaw(LoadedCatalog loaded) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (loaded.containsTokenMaterial()) {
            issues.add(issue(Severity.ERROR, "CATALOG_CONTAINS_TOKEN", "$", "Catalog appears to contain API token or Authorization material"));
        }
        if (loaded.rows().isEmpty()) {
            issues.add(issue(Severity.ERROR, "CATALOG_EMPTY", "$", "Catalog must contain at least one step"));
        }
        Map<String, RawUpgradeStep> seen = new HashMap<>();
        for (int index = 0; index < loaded.rows().size(); index++) {
            RawUpgradeStep row = loaded.rows().get(index);
            String path = "$[" + index + "]";
            if (row == null) {
                issues.add(issue(Severity.ERROR, "CATALOG_ROW_NULL", path, "Catalog row is null"));
                continue;
            }
            boolean requiredMissing = blank(row.category()) || blank(row.item()) || blank(row.stage())
                    || row.fromLevel() == null || row.toLevel() == null || row.count() == null;
            if (requiredMissing) {
                issues.add(issue(Severity.ERROR, "CATALOG_REQUIRED_FIELD_MISSING", path, "category, item, stage, levels and count are required"));
                continue;
            }
            if (row.fromLevel() < 0 || row.toLevel() < 0) {
                issues.add(issue(Severity.ERROR, "CATALOG_LEVEL_NEGATIVE", path, "Catalog levels cannot be negative"));
            }
            if (row.toLevel() != row.fromLevel() + 1) {
                issues.add(issue(Severity.ERROR, "CATALOG_LEVEL_JUMP", path + ".to_level", "Each catalog transition must advance exactly +1"));
            }
            if (row.count() < 0) {
                issues.add(issue(Severity.ERROR, "CATALOG_COUNT_NEGATIVE", path + ".count", "Catalog count cannot be negative"));
            }
            negative(row.baseDays(), path + ".base_days", issues);
            negative(row.passDays(), path + ".pass_days", issues);
            negative(row.remainingNowDays(), path + ".remaining_now_days", issues);
            if (row.baseDays() == null) {
                issues.add(issue(Severity.WARNING, "DURATION_UNKNOWN", path + ".base_days", "Base duration is unknown; subtotal remains available but exact total is not"));
            }
            if (blank(row.source())) {
                issues.add(issue(Severity.WARNING, "STEP_SOURCE_MISSING", path + ".source", "Step source reference is missing"));
            }
            if (Boolean.TRUE.equals(row.inProgress()) && row.remainingNowDays() == null) {
                issues.add(issue(Severity.WARNING, "ACTIVE_STEP_WITHOUT_BALANCE", path + ".remaining_now_days", "Aggregated catalog state says in progress but has no observed balance; queue evidence is required"));
            }
            String key = logicalRawKey(row);
            RawUpgradeStep previous = seen.putIfAbsent(key, row);
            if (previous != null) {
                if (previous.equals(row)) {
                    issues.add(issue(Severity.INFO, "DUPLICATE_STEP_DEDUPLICATED", path, "Exact duplicate step was deduplicated: " + key));
                } else {
                    issues.add(issue(Severity.ERROR, "DUPLICATE_STEP_CONFLICT", path, "Conflicting rows share catalog step key: " + key));
                }
            }
        }
        issues.add(issue(Severity.WARNING, "CATALOG_UNVERSIONED", "$.catalogVersion", "Input has no catalog version; normalized version is legacy-unversioned and FULL certification is blocked"));
        issues.add(issue(Severity.WARNING, "CATALOG_SCOPE_INCOMPLETE", "$.scope", "Legacy input contains pending definitions only and does not declare complete TH/version scope"));
        return List.copyOf(issues);
    }

    public List<ValidationIssue> validateNormalized(GameCatalog catalog) {
        List<ValidationIssue> issues = new ArrayList<>();
        Map<String, CatalogStep> seen = new HashMap<>();
        for (int index = 0; index < catalog.steps().size(); index++) {
            CatalogStep step = catalog.steps().get(index);
            String key = step.definition().key().logicalId();
            CatalogStep previous = seen.putIfAbsent(key, step);
            if (previous != null && !previous.equals(step)) {
                issues.add(issue(Severity.ERROR, "DUPLICATE_STEP_CONFLICT", "$.steps[" + index + "]", "Conflicting normalized catalog key: " + key));
            }
        }
        return List.copyOf(issues);
    }

    private static String logicalRawKey(RawUpgradeStep row) {
        String stage = row.stage().toUpperCase(Locale.ROOT);
        UpgradeType type = stage.startsWith("SC") || row.category().toUpperCase(Locale.ROOT).contains("SOBRECARG")
                ? UpgradeType.SUPERCHARGE : UpgradeType.PERMANENT;
        return LegacyKeyFactory.fromLegacyName(row.item()) + "|" + type + "|" + row.fromLevel() + "|" + row.toLevel();
    }

    private static void negative(BigDecimal value, String path, List<ValidationIssue> issues) {
        if (value != null && value.signum() < 0) {
            issues.add(issue(Severity.ERROR, "DURATION_NEGATIVE", path, "Duration cannot be negative"));
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static ValidationIssue issue(Severity severity, String code, String path, String message) {
        return new ValidationIssue(severity, code, path, message);
    }
}
