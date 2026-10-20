package com.clashtracker.reconciliation;

import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageSnapshot;

import java.util.List;

public record ReconciliationResult(VillageSnapshot snapshot, List<ValidationIssue> issues) {
    public ReconciliationResult {
        issues = List.copyOf(issues);
    }
}
