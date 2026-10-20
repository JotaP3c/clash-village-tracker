package com.clashtracker.application;

import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.report.excel.WorkbookValidationResult;

import java.util.List;

public record ReportExecutionResult(
        ExecutionStatus status,
        String message,
        List<ValidationIssue> issues,
        CalculationResult calculation,
        WorkbookValidationResult workbookValidation,
        List<String> artifacts) {

    public ReportExecutionResult {
        issues = List.copyOf(issues);
        artifacts = List.copyOf(artifacts);
    }
}
