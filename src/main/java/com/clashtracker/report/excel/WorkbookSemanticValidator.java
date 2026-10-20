package com.clashtracker.report.excel;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WorkbookSemanticValidator {
    public WorkbookValidationResult validate(Path workbookPath) throws IOException {
        try (InputStream input = Files.newInputStream(workbookPath);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            List<String> names = new ArrayList<>();
            int formulaCount = 0;
            int tableCount = 0;
            int chartCount = 0;
            boolean linkedChart = false;

            for (int index = 0; index < workbook.getNumberOfSheets(); index++) {
                XSSFSheet sheet = workbook.getSheetAt(index);
                names.add(sheet.getSheetName());
                for (Row row : sheet) {
                    for (Cell cell : row) {
                        if (cell.getCellType() == CellType.FORMULA) {
                            formulaCount++;
                        }
                    }
                }
                for (XSSFTable table : sheet.getTables()) {
                    tableCount++;
                    if (table.getStartCellReference() == null || table.getEndCellReference() == null) {
                        throw new IOException("Excel table has no valid area: " + table.getName());
                    }
                }
                XSSFDrawing drawing = sheet.getDrawingPatriarch();
                if (drawing != null) {
                    for (XSSFChart chart : drawing.getCharts()) {
                        chartCount++;
                        String xml = chart.getCTChart().xmlText();
                        if (xml.contains("$F$4:$F$5") || xml.contains("$F$4')") || xml.contains("$F$5')")) {
                            linkedChart = true;
                        }
                    }
                }
            }

            if (!names.equals(ExcelReportWriter.REQUIRED_SHEETS)) {
                throw new IOException("Workbook sheets differ from the required contract: " + names);
            }
            if (formulaCount < 10) {
                throw new IOException("Workbook has too few formulas: " + formulaCount);
            }
            if (tableCount < 2) {
                throw new IOException("Workbook must contain Overview and Steps Excel Tables");
            }
            if (chartCount < 1 || !linkedChart) {
                throw new IOException("Dashboard pie chart is missing or not linked to formula cells F4:F5");
            }
            XSSFSheet overview = workbook.getSheet("Visão Geral");
            XSSFSheet steps = workbook.getSheet("Etapas por Nível");
            if (overview.getTables().stream().noneMatch(table -> "OverviewTable".equals(table.getName()))
                    || steps.getTables().stream().noneMatch(table -> "StepsTable".equals(table.getName()))) {
                throw new IOException("Expected Excel Tables were not found by name");
            }
            if (!workbook.getForceFormulaRecalculation()) {
                throw new IOException("Workbook does not force formula recalculation");
            }
            return new WorkbookValidationResult(names, formulaCount, tableCount, chartCount,
                    linkedChart, workbook.getForceFormulaRecalculation());
        }
    }
}
