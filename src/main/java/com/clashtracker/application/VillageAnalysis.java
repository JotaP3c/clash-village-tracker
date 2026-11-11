package com.clashtracker.application;

import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.SnapshotDiff;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageSnapshot;

import java.math.BigDecimal;
import java.util.List;

/**
 * In-memory outcome of the offline analysis pipeline (load, validate, normalize, reconcile,
 * calculate) WITHOUT writing any artifact to disk. It is the read-only projection consumed by the
 * REST layer so a dashboard request does not regenerate the Excel workbook or history on every
 * call. The heavier {@link ReportApplication#generate(ReportRequest)} path still produces files.
 */
public record VillageAnalysis(
        ExecutionStatus status,
        String message,
        VillageSnapshot snapshot,
        GameCatalog catalog,
        CalculationResult calculation,
        SnapshotDiff diff,
        BigDecimal goldPassBoost,
        List<ValidationIssue> issues) {

    public VillageAnalysis {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public boolean failed() {
        return status == ExecutionStatus.FAILED;
    }
}
