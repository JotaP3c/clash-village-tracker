package com.clashtracker.report;

import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.SnapshotDiff;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageSnapshot;

import java.math.BigDecimal;
import java.util.List;

public record ReportModel(
        VillageSnapshot snapshot,
        GameCatalog catalog,
        CalculationResult calculation,
        List<ValidationIssue> issues,
        SnapshotDiff diff,
        BigDecimal goldPassBoost) {

    public ReportModel {
        issues = List.copyOf(issues);
    }
}
