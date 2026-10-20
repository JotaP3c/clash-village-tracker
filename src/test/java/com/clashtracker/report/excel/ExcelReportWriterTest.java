package com.clashtracker.report.excel;

import com.clashtracker.TestFixtures;
import com.clashtracker.calculation.CalculationEngine;
import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.CatalogStep;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.report.ReportModel;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExcelReportWriterTest {
    @TempDir
    Path temp;

    @Test
    void workbookReopensWithRequiredSheetsTablesFormulasAndLinkedPieChart() throws Exception {
        VillageEntity entity = TestFixtures.entity("Bebê Dragão", "Tropas", 1, 9, 10, 0, 0, 0, 0, 0);
        VillageSnapshot snapshot = TestFixtures.snapshot(List.of(entity), List.of());
        CatalogStep step = TestFixtures.step("Bebê Dragão", "Tropas", UpgradeType.PERMANENT,
                9, 10, 1, 86_400L);
        GameCatalog catalog = TestFixtures.catalog(List.of(step));
        CalculationResult calculation = new CalculationEngine().calculate(snapshot, catalog,
                new BigDecimal("0.20"), List.of());
        ReportModel model = new ReportModel(snapshot, catalog, calculation, List.of(), null,
                new BigDecimal("0.20"));
        Path workbookPath = temp.resolve("report.xlsx");

        new ExcelReportWriter().write(model, workbookPath);
        WorkbookValidationResult validation = new WorkbookSemanticValidator().validate(workbookPath);

        assertEquals(ExcelReportWriter.REQUIRED_SHEETS, validation.sheetNames());
        assertTrue(validation.formulaCount() >= 10);
        assertEquals(2, validation.tableCount());
        assertEquals(1, validation.chartCount());
        assertTrue(validation.chartLinkedToFormulaCells());
        assertTrue(validation.forceFormulaRecalculation());

        try (InputStream input = Files.newInputStream(workbookPath);
             XSSFWorkbook workbook = new XSSFWorkbook(input)) {
            assertEquals("OverviewTable", workbook.getSheet("Visão Geral").getTables().get(0).getName());
            assertEquals("StepsTable", workbook.getSheet("Etapas por Nível").getTables().get(0).getName());
            assertEquals(CellType.FORMULA, workbook.getSheet("Dashboard").getRow(3).getCell(1).getCellType());
            assertTrue(workbook.getSheet("Dashboard").getRow(3).getCell(1).getCellFormula().contains("Q:Q"));
            assertEquals(CellType.FORMULA, workbook.getSheet("Etapas por Nível").getRow(1).getCell(12).getCellType());
            assertTrue(workbook.getSheet("Etapas por Nível").getRow(1).getCell(12).getCellFormula().contains("Configurações"));
            short inputColor = workbook.getSheet("Visão Geral").getRow(1).getCell(5)
                    .getCellStyle().getFillForegroundColor();
            short formulaColor = workbook.getSheet("Visão Geral").getRow(1).getCell(12)
                    .getCellStyle().getFillForegroundColor();
            assertEquals(IndexedColors.LIGHT_YELLOW.getIndex(), inputColor);
            assertFalse(inputColor == formulaColor);
        }
    }
}
