package com.clashtracker.report.excel;

import java.util.List;

public record WorkbookValidationResult(
        List<String> sheetNames,
        int formulaCount,
        int tableCount,
        int chartCount,
        boolean chartLinkedToFormulaCells,
        boolean forceFormulaRecalculation) {

    public WorkbookValidationResult {
        sheetNames = List.copyOf(sheetNames);
    }
}
