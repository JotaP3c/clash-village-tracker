package com.clashtracker.application;

import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.ValidationIssue;

import java.util.List;

public record CatalogValidationResult(ExecutionStatus status, GameCatalog catalog, List<ValidationIssue> issues) {
    public CatalogValidationResult {
        issues = List.copyOf(issues);
    }
}
