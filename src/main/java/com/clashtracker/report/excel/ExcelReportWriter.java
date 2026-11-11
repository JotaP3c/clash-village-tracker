package com.clashtracker.report.excel;

import com.clashtracker.domain.ActiveUpgrade;
import com.clashtracker.domain.CalculatedStep;
import com.clashtracker.domain.CategoryProgress;
import com.clashtracker.domain.DiffEntry;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.report.ReportModel;
import com.clashtracker.util.TimeValues;
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.SpreadsheetVersion;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.AreaReference;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xddf.usermodel.chart.ChartTypes;
import org.apache.poi.xddf.usermodel.chart.XDDFChartData;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSource;
import org.apache.poi.xddf.usermodel.chart.XDDFDataSourcesFactory;
import org.apache.poi.xddf.usermodel.chart.XDDFNumericalDataSource;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFChart;
import org.apache.poi.xssf.usermodel.XSSFClientAnchor;
import org.apache.poi.xssf.usermodel.XSSFDrawing;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFHyperlink;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFTable;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTTableStyleInfo;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExcelReportWriter {
    public static final List<String> REQUIRED_SHEETS = List.of(
            "Dashboard", "Visão Geral", "Defesas e SC", "Recursos", "Armadilhas e Muros",
            "Heróis Pets Guardiões", "Laboratório", "Fila Ativa", "Etapas por Nível",
            "Histórico e Diferenças", "Pendências", "Configurações", "Fontes");

    public void write(ReportModel model, Path target) throws IOException {
        Path parent = target.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Styles styles = createStyles(workbook);
            createOverview(workbook, model, styles);
            createCategorySheet(workbook, "Defesas e SC", model, styles, List.of("Defesas"));
            createCategorySheet(workbook, "Recursos", model, styles, List.of("Recursos", "Construções Auxiliares"));
            createCategorySheet(workbook, "Armadilhas e Muros", model, styles, List.of("Armadilhas", "Muros"));
            createCategorySheet(workbook, "Heróis Pets Guardiões", model, styles, List.of("Heróis", "Pets", "Guardiões"));
            createCategorySheet(workbook, "Laboratório", model, styles, List.of("Tropas", "Feitiços", "Máquinas de Cerco"));
            createQueue(workbook, model, styles);
            createSteps(workbook, model, styles);
            createHistory(workbook, model, styles);
            createPending(workbook, model, styles);
            createConfiguration(workbook, model, styles);
            createSources(workbook, model, styles);
            createDashboard(workbook, model, styles);
            workbook.setSheetOrder("Dashboard", 0);
            workbook.setForceFormulaRecalculation(true);
            try (OutputStream output = Files.newOutputStream(target)) {
                workbook.write(output);
            }
        }
    }

    private static void createDashboard(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Dashboard");
        sheet.createFreezePane(0, 2);
        Row title = sheet.createRow(0);
        Cell titleCell = title.createCell(0);
        titleCell.setCellValue("Clash Village Tracker — Dashboard offline");
        titleCell.setCellStyle(styles.title());
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 5));

        String[][] labels = {
                {"Etapas concluídas", "SUM('Visão Geral'!Q:Q)"},
                {"Etapas restantes", "SUM('Visão Geral'!S:S)"},
                {"Progresso catalogado", "IFERROR(B4/(B4+B5),0)"},
                {"Progresso permanente", "IFERROR(SUM('Visão Geral'!M:M)/SUM('Visão Geral'!N:N),0)"},
                {"Progresso SC", "IFERROR(SUM('Visão Geral'!O:O)/SUM('Visão Geral'!P:P),0)"},
                {"Cobertura", "IFERROR(AVERAGE('Visão Geral'!T:T),0)"}
        };
        for (int i = 0; i < labels.length; i++) {
            Row row = sheet.createRow(i + 3);
            label(row, 0, labels[i][0], styles.header());
            formula(row, 1, labels[i][1], styles.formula());
        }
        sheet.getRow(5).getCell(1).setCellStyle(styles.percentFormula());
        sheet.getRow(6).getCell(1).setCellStyle(styles.percentFormula());
        sheet.getRow(7).getCell(1).setCellStyle(styles.percentFormula());
        sheet.getRow(8).getCell(1).setCellStyle(styles.percentFormula());

        dashboardValue(sheet, 10, "Data da observação", String.valueOf(model.snapshot().observedAt()), styles);
        dashboardValue(sheet, 11, "Versão do catálogo", model.catalog().catalogVersion(), styles);
        dashboardValue(sheet, 12, "Filas ativas (linhas)", model.snapshot().activeUpgrades().size(), styles);
        Row unknown = sheet.createRow(13);
        label(unknown, 0, "Ocorrências com duração desconhecida", styles.header());
        formula(unknown, 1, "SUMIF('Etapas por Nível'!Q:Q,\"NÃO\",'Etapas por Nível'!H:H)", styles.formula());
        dashboardValue(sheet, 14, "Construtores permanentes", model.snapshot().permanentBuilderCapacity(), styles);
        dashboardValue(sheet, 15, "Goblin temporário", model.snapshot().goblinBuilderTemporary() ? "SIM (não permanente)" : "NÃO", styles);
        dashboardValue(sheet, 16, "FULL certificado", model.calculation().progress().fullCertified() ? "SIM" : "NÃO", styles);
        dashboardValue(sheet, 17, "Mensagem FULL", model.calculation().progress().fullCertificationMessage(), styles);
        dashboardValue(sheet, 18, "Carga construtor (segundos)", model.calculation().workloads().builderSeconds(), styles);
        dashboardValue(sheet, 19, "Carga laboratório (segundos)", model.calculation().workloads().laboratorySeconds(), styles);
        dashboardValue(sheet, 20, "Carga pets (segundos)", model.calculation().workloads().petSeconds(), styles);

        Row chartCompleted = sheet.getRow(3);
        label(chartCompleted, 4, "Concluído", styles.header());
        formula(chartCompleted, 5, "B4", styles.formula());
        Row chartRemaining = sheet.getRow(4);
        label(chartRemaining, 4, "Restante", styles.header());
        formula(chartRemaining, 5, "B5", styles.formula());
        createPieChart(sheet);

        int linkRow = 23;
        Row navigation = sheet.createRow(linkRow++);
        label(navigation, 0, "Navegação", styles.header());
        for (String target : List.of("Visão Geral", "Defesas e SC", "Laboratório", "Fila Ativa", "Etapas por Nível", "Pendências")) {
            Row row = sheet.createRow(linkRow++);
            Cell cell = row.createCell(0);
            cell.setCellValue("Abrir " + target);
            XSSFHyperlink hyperlink = workbook.getCreationHelper().createHyperlink(HyperlinkType.DOCUMENT);
            hyperlink.setAddress("'" + target + "'!A1");
            cell.setHyperlink(hyperlink);
            cell.setCellStyle(styles.link());
        }

        Row categoryHeader = sheet.createRow(31);
        headers(categoryHeader, styles.header(), "Categoria", "Concluído", "Total", "Restante", "Progresso", "Desconhecidos");
        int rowIndex = 32;
        for (CategoryProgress category : model.calculation().progress().categories()) {
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, category.category(), styles.normal());
            number(row, 1, category.completedSteps(), styles.number());
            number(row, 2, category.totalSteps(), styles.number());
            number(row, 3, category.remainingSteps(), styles.number());
            decimal(row, 4, category.progress().doubleValue(), styles.percent());
            number(row, 5, category.unknownEntityCount(), styles.number());
        }
        sheet.setAutoFilter(new CellRangeAddress(31, Math.max(31, rowIndex - 1), 0, 5));
        setWidths(sheet, 34, 18, 18, 18, 18, 18);
    }

    private static void createPieChart(XSSFSheet sheet) {
        XSSFDrawing drawing = sheet.createDrawingPatriarch();
        XSSFClientAnchor anchor = drawing.createAnchor(0, 0, 0, 0, 3, 6, 10, 20);
        XSSFChart chart = drawing.createChart(anchor);
        chart.setTitleText("Etapas concluídas × restantes");
        chart.setTitleOverlay(false);
        XDDFDataSource<String> categories = XDDFDataSourcesFactory.fromStringCellRange(
                sheet, new CellRangeAddress(3, 4, 4, 4));
        XDDFNumericalDataSource<Double> values = XDDFDataSourcesFactory.fromNumericCellRange(
                sheet, new CellRangeAddress(3, 4, 5, 5));
        XDDFChartData data = chart.createData(ChartTypes.PIE, null, null);
        XDDFChartData.Series series = data.addSeries(categories, values);
        series.setTitle("Etapas", null);
        chart.plot(data);
    }

    private static void createOverview(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Visão Geral");
        Row header = sheet.createRow(0);
        String[] columns = {"Chave técnica", "Categoria", "Nome PT-BR", "Representação", "Quantidade",
                "Nível concluído", "Nível máximo", "No máximo", "SC1", "SC2", "SC3", "SC máximo",
                "Permanente concluído", "Permanente total", "SC concluída", "SC total",
                "Etapas concluídas", "Etapas totais", "Restantes", "Cobertura", "Fonte", "Confiança", "Observações"};
        headers(header, styles.header(), columns);
        int rowIndex = 1;
        for (VillageEntity entity : model.snapshot().entities()) {
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, entity.entityKey(), styles.normal());
            text(row, 1, entity.rawCategory(), styles.normal());
            text(row, 2, entity.displayNamePtBr(), styles.normal());
            text(row, 3, entity.representation().name(), styles.normal());
            number(row, 4, entity.quantity(), styles.input());
            nullableNumber(row, 5, entity.currentLevel(), styles.input());
            nullableNumber(row, 6, entity.maxLevel(), styles.input());
            number(row, 7, entity.countAtMax(), styles.input());
            number(row, 8, entity.sc1Completed(), styles.input());
            number(row, 9, entity.sc2Completed(), styles.input());
            number(row, 10, entity.sc3Completed(), styles.input());
            number(row, 11, entity.maxSupercharge(), styles.input());
            int excelRow = row.getRowNum() + 1;
            formula(row, 12, "IF(OR(F" + excelRow + "=\"\",G" + excelRow + "=\"\"),0,F" + excelRow + "*(E" + excelRow + "-H" + excelRow + ")+G" + excelRow + "*H" + excelRow + ")", styles.formula());
            formula(row, 13, "IF(G" + excelRow + "=\"\",0,G" + excelRow + "*E" + excelRow + ")", styles.formula());
            formula(row, 14, "SUM(I" + excelRow + ":K" + excelRow + ")", styles.formula());
            formula(row, 15, "L" + excelRow + "*E" + excelRow, styles.formula());
            formula(row, 16, "M" + excelRow + "+O" + excelRow, styles.formula());
            formula(row, 17, "N" + excelRow + "+P" + excelRow, styles.formula());
            formula(row, 18, "MAX(0,R" + excelRow + "-Q" + excelRow + ")", styles.formula());
            formula(row, 19, "IF(AND(F" + excelRow + "<>\"\",G" + excelRow + "<>\"\"),1,0)", styles.percentFormula());
            text(row, 20, entity.source(), styles.wrap());
            text(row, 21, entity.confidence(), styles.normal());
            text(row, 22, entity.notes(), styles.wrap());
        }
        createTable(sheet, "OverviewTable", 0, Math.max(1, rowIndex - 1), columns.length - 1);
        configureTabularSheet(sheet, rowIndex, columns.length);
        setWidths(sheet, 28, 20, 28, 16, 12, 15, 13, 12, 9, 9, 9, 12, 18, 16, 13, 12, 16, 14, 12, 12, 42, 16, 55);
    }

    private static void createSteps(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Etapas por Nível");
        String[] columns = {"Chave da etapa", "Categoria", "Entidade", "Tipo", "De", "Para", "Etapa",
                "Quantidade", "Ativas", "Futuras", "Fila", "Base segundos/unidade", "Bônus segundos/unidade",
                "Saldo observado total", "Subtotal base", "Subtotal cenário", "Exato", "Fonte", "Verificação"};
        headers(sheet.createRow(0), styles.header(), columns);
        int rowIndex = 1;
        for (CalculatedStep step : model.calculation().steps()) {
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, step.key().logicalId(), styles.normal());
            text(row, 1, step.rawCategory(), styles.normal());
            text(row, 2, step.rawItemName(), styles.normal());
            text(row, 3, step.key().upgradeType().name(), styles.normal());
            number(row, 4, step.key().fromLevel(), styles.number());
            number(row, 5, step.key().toLevel(), styles.number());
            text(row, 6, step.stage(), styles.normal());
            number(row, 7, step.count(), styles.number());
            number(row, 8, step.activeCount(), styles.number());
            number(row, 9, step.futureCount(), styles.number());
            text(row, 10, step.queueKind().name(), styles.normal());
            nullableNumber(row, 11, step.baseDurationSecondsPerUnit(), styles.number());
            int excelRow = row.getRowNum() + 1;
            formula(row, 12, "IF(L" + excelRow + "=\"\",\"\",ROUND(L" + excelRow + "*(1-'Configurações'!$B$4),0))", styles.formula());
            number(row, 13, step.observedRemainingSecondsTotal(), styles.number());
            formula(row, 14, "IF(L" + excelRow + "=\"\",\"\",L" + excelRow + "*H" + excelRow + ")", styles.formula());
            formula(row, 15, "IF(AND(L" + excelRow + "=\"\",J" + excelRow + ">0),\"\",IF(M" + excelRow + "=\"\",0,M" + excelRow + "*J" + excelRow + ")+N" + excelRow + ")", styles.formula());
            text(row, 16, step.exactForRow() ? "SIM" : "NÃO", step.exactForRow() ? styles.normal() : styles.warning());
            text(row, 17, step.source(), styles.wrap());
            text(row, 18, step.confidence(), styles.normal());
        }
        createTable(sheet, "StepsTable", 0, Math.max(1, rowIndex - 1), columns.length - 1);
        configureTabularSheet(sheet, rowIndex, columns.length);
        setWidths(sheet, 52, 20, 28, 16, 8, 8, 20, 12, 10, 10, 14, 22, 24, 22, 18, 20, 10, 48, 18);
    }

    private static void createCategorySheet(XSSFWorkbook workbook, String name, ReportModel model,
                                            Styles styles, List<String> categories) {
        XSSFSheet sheet = workbook.createSheet(name);
        String[] columns = {"Nome", "Categoria", "Quantidade", "Nível concluído", "Nível máximo", "No máximo",
                "SC máximo", "SC1", "SC2", "SC3", "Representação", "Chave técnica", "Fonte", "Confiança", "Observações"};
        headers(sheet.createRow(0), styles.header(), columns);
        int rowIndex = 1;
        for (VillageEntity entity : model.snapshot().entities()) {
            if (!categories.contains(entity.rawCategory())) {
                continue;
            }
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, entity.displayNamePtBr(), styles.normal());
            text(row, 1, entity.rawCategory(), styles.normal());
            number(row, 2, entity.quantity(), styles.input());
            nullableNumber(row, 3, entity.currentLevel(), styles.input());
            nullableNumber(row, 4, entity.maxLevel(), styles.input());
            number(row, 5, entity.countAtMax(), styles.input());
            number(row, 6, entity.maxSupercharge(), styles.input());
            number(row, 7, entity.sc1Completed(), styles.input());
            number(row, 8, entity.sc2Completed(), styles.input());
            number(row, 9, entity.sc3Completed(), styles.input());
            text(row, 10, entity.representation().name(), styles.normal());
            text(row, 11, entity.entityKey(), styles.normal());
            text(row, 12, entity.source(), styles.wrap());
            text(row, 13, entity.confidence(), styles.normal());
            text(row, 14, entity.notes(), styles.wrap());
        }
        configureTabularSheet(sheet, rowIndex, columns.length);
        setWidths(sheet, 28, 22, 12, 16, 14, 12, 12, 9, 9, 9, 16, 30, 48, 16, 55);
    }

    private static void createQueue(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Fila Ativa");
        String[] columns = {"Entidade", "Fila", "Quantidade", "Nível concluído", "Alvo", "Etapa",
                "Saldo observado (s)", "Saldo formatado", "Data observada", "Confiança", "Origem"};
        headers(sheet.createRow(0), styles.header(), columns);
        int rowIndex = 1;
        for (ActiveUpgrade job : model.snapshot().activeUpgrades()) {
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, job.rawItemName(), styles.normal());
            text(row, 1, job.queueKind().name(), styles.normal());
            number(row, 2, job.quantity(), styles.number());
            nullableNumber(row, 3, job.fromLevel(), styles.number());
            nullableNumber(row, 4, job.targetLevel(), styles.number());
            text(row, 5, job.stage(), styles.normal());
            nullableNumber(row, 6, job.observedRemainingSeconds(), styles.number());
            text(row, 7, TimeValues.formatDuration(job.observedRemainingSeconds()), styles.normal());
            text(row, 8, String.valueOf(job.observedAt()), styles.normal());
            text(row, 9, job.confidence(), styles.normal());
            text(row, 10, job.sourceReference(), styles.wrap());
        }
        configureTabularSheet(sheet, rowIndex, columns.length);
        setWidths(sheet, 28, 16, 12, 16, 10, 20, 22, 20, 16, 16, 55);
    }

    private static void createHistory(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Histórico e Diferenças");
        String[] columns = {"Chave técnica", "Campo", "Anterior", "Atual", "Classificação", "Mensagem"};
        headers(sheet.createRow(0), styles.header(), columns);
        int rowIndex = 1;
        if (model.diff() == null) {
            Row row = sheet.createRow(rowIndex++);
            text(row, 0, "Sem snapshot anterior", styles.warning());
            text(row, 5, "Nenhuma diferença foi inventada; use --previous-snapshot para comparar.", styles.wrap());
        } else {
            for (DiffEntry change : model.diff().changes()) {
                Row row = sheet.createRow(rowIndex++);
                text(row, 0, change.entityKey(), styles.normal());
                text(row, 1, change.field(), styles.normal());
                text(row, 2, String.valueOf(change.previousValue()), styles.normal());
                text(row, 3, String.valueOf(change.currentValue()), styles.normal());
                text(row, 4, change.classification(), styles.normal());
                text(row, 5, change.message(), styles.wrap());
            }
        }
        configureTabularSheet(sheet, rowIndex, columns.length);
        setWidths(sheet, 32, 20, 20, 20, 22, 60);
    }

    private static void createPending(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Pendências");
        String[] columns = {"Severidade", "Código", "Caminho", "Mensagem"};
        headers(sheet.createRow(0), styles.header(), columns);
        int rowIndex = 1;
        List<ValidationIssue> sorted = new ArrayList<>(model.issues());
        sorted.sort(Comparator.comparing(ValidationIssue::severity).thenComparing(ValidationIssue::code).thenComparing(ValidationIssue::path));
        for (ValidationIssue issue : sorted) {
            Row row = sheet.createRow(rowIndex++);
            CellStyle issueStyle = issue.severity().name().equals("ERROR") ? styles.error()
                    : issue.severity().name().equals("WARNING") ? styles.warning() : styles.normal();
            text(row, 0, issue.severity().name(), issueStyle);
            text(row, 1, issue.code(), styles.normal());
            text(row, 2, issue.path(), styles.normal());
            text(row, 3, issue.message(), styles.wrap());
        }
        configureTabularSheet(sheet, rowIndex, columns.length);
        setWidths(sheet, 14, 38, 55, 90);
    }

    private static void createConfiguration(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Configurações");
        Row title = sheet.createRow(0);
        label(title, 0, "Configuração do cenário", styles.title());
        headers(sheet.createRow(2), styles.header(), "Parâmetro", "Valor", "Descrição");
        Row boost = sheet.createRow(3);
        text(boost, 0, "goldPassBoost", styles.normal());
        decimal(boost, 1, model.goldPassBoost().doubleValue(), styles.percentInput());
        text(boost, 2, "Fração de 0 a 1 aplicada apenas às durações-base futuras.", styles.wrap());
        Row builders = sheet.createRow(4);
        text(builders, 0, "permanentBuilderCapacity", styles.normal());
        number(builders, 1, model.snapshot().permanentBuilderCapacity(), styles.input());
        text(builders, 2, "Capacidade permanente; Goblin não é um sétimo permanente.", styles.wrap());
        Row goblin = sheet.createRow(5);
        text(goblin, 0, "goblinBuilderTemporary", styles.normal());
        text(goblin, 1, model.snapshot().goblinBuilderTemporary() ? "SIM" : "NÃO", styles.input());
        text(goblin, 2, "Indicador histórico sem janela de disponibilidade; não aumenta capacidade base.", styles.wrap());
        Row catalog = sheet.createRow(6);
        text(catalog, 0, "catalogVersion", styles.normal());
        text(catalog, 1, model.catalog().catalogVersion(), styles.normal());
        text(catalog, 2, "Catálogo sem versão explícita bloqueia certificação FULL.", styles.wrap());
        Row legend = sheet.createRow(8);
        text(legend, 0, "Amarelo", styles.input());
        text(legend, 1, "Entrada/editável", styles.normal());
        Row formulaLegend = sheet.createRow(9);
        text(formulaLegend, 0, "Azul", styles.formula());
        text(formulaLegend, 1, "Fórmula calculada", styles.normal());
        sheet.createFreezePane(0, 3);
        setWidths(sheet, 34, 24, 75);
    }

    private static void createSources(XSSFWorkbook workbook, ReportModel model, Styles styles) {
        XSSFSheet sheet = workbook.createSheet("Fontes");
        String[] columns = {"Tipo", "Entidade/etapa", "Referência", "Confiança", "Versão"};
        headers(sheet.createRow(0), styles.header(), columns);
        Map<String, String[]> unique = new LinkedHashMap<>();
        for (VillageEntity entity : model.snapshot().entities()) {
            String key = "snapshot|" + entity.entityKey() + "|" + entity.source();
            unique.putIfAbsent(key, new String[]{"SNAPSHOT MANUAL", entity.rawName(), entity.source(), entity.confidence(), "fotografia " + model.snapshot().observedAt()});
        }
        for (CalculatedStep step : model.calculation().steps()) {
            String key = "catalog|" + step.key().logicalId() + "|" + step.source();
            unique.putIfAbsent(key, new String[]{"CATÁLOGO LEGADO", step.rawItemName() + " " + step.stage(), step.source(), step.confidence(), model.catalog().catalogVersion()});
        }
        int rowIndex = 1;
        for (String[] source : unique.values()) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < source.length; i++) {
                text(row, i, source[i], i == 2 ? styles.wrap() : styles.normal());
            }
        }
        configureTabularSheet(sheet, rowIndex, columns.length);
        setWidths(sheet, 22, 38, 75, 18, 22);
    }

    private static void dashboardValue(XSSFSheet sheet, int zeroBasedRow, String label, Object value, Styles styles) {
        Row row = sheet.createRow(zeroBasedRow);
        label(row, 0, label, styles.header());
        if (value instanceof Number number) {
            number(row, 1, number.longValue(), styles.number());
        } else {
            text(row, 1, String.valueOf(value), styles.normal());
        }
    }

    private static void configureTabularSheet(XSSFSheet sheet, int rowCount, int columnCount) {
        sheet.createFreezePane(0, 1);
        if (rowCount > 1) {
            sheet.setAutoFilter(new CellRangeAddress(0, rowCount - 1, 0, columnCount - 1));
        }
    }

    private static void createTable(XSSFSheet sheet, String tableName, int firstRow, int lastRow, int lastColumn) {
        if (lastRow <= firstRow) {
            return;
        }
        AreaReference area = new AreaReference(new CellReference(firstRow, 0),
                new CellReference(lastRow, lastColumn), SpreadsheetVersion.EXCEL2007);
        XSSFTable table = sheet.createTable(area);
        table.setName(tableName);
        table.setDisplayName(tableName);
        CTTableStyleInfo style = table.getCTTable().isSetTableStyleInfo()
                ? table.getCTTable().getTableStyleInfo() : table.getCTTable().addNewTableStyleInfo();
        style.setName("TableStyleMedium2");
        style.setShowRowStripes(true);
        style.setShowColumnStripes(false);
    }

    private static void headers(Row row, CellStyle style, String... values) {
        for (int i = 0; i < values.length; i++) {
            label(row, i, values[i], style);
        }
    }

    private static void label(Row row, int column, String value, CellStyle style) {
        text(row, column, value, style);
    }

    private static void text(Row row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value == null ? "" : value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, long value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void number(Row row, int column, Number value, CellStyle style) {
        nullableNumber(row, column, value, style);
    }

    private static void decimal(Row row, int column, double value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    private static void nullableNumber(Row row, int column, Number value, CellStyle style) {
        Cell cell = row.createCell(column);
        if (value != null) {
            cell.setCellValue(value.doubleValue());
        }
        cell.setCellStyle(style);
    }

    private static void formula(Row row, int column, String expression, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellFormula(expression);
        cell.setCellStyle(style);
    }

    private static void setWidths(XSSFSheet sheet, int... widths) {
        for (int i = 0; i < widths.length; i++) {
            sheet.setColumnWidth(i, Math.min(255, widths[i]) * 256);
        }
    }

    private static Styles createStyles(XSSFWorkbook workbook) {
        XSSFFont headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerFont.setColor(IndexedColors.WHITE.getIndex());
        XSSFCellStyle header = workbook.createCellStyle();
        header.setFont(headerFont);
        header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        header.setAlignment(HorizontalAlignment.CENTER);
        header.setVerticalAlignment(VerticalAlignment.CENTER);
        header.setWrapText(true);
        borders(header);

        XSSFFont titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 16);
        titleFont.setColor(IndexedColors.DARK_BLUE.getIndex());
        XSSFCellStyle title = workbook.createCellStyle();
        title.setFont(titleFont);

        XSSFCellStyle normal = workbook.createCellStyle();
        normal.setVerticalAlignment(VerticalAlignment.TOP);
        borders(normal);
        XSSFCellStyle wrap = workbook.createCellStyle();
        wrap.cloneStyleFrom(normal);
        wrap.setWrapText(true);

        XSSFCellStyle number = workbook.createCellStyle();
        number.cloneStyleFrom(normal);
        number.setDataFormat(workbook.createDataFormat().getFormat("0"));

        XSSFCellStyle input = workbook.createCellStyle();
        input.cloneStyleFrom(normal);
        input.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
        input.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFCellStyle formula = workbook.createCellStyle();
        formula.cloneStyleFrom(normal);
        formula.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
        formula.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        formula.setDataFormat(workbook.createDataFormat().getFormat("0"));

        XSSFCellStyle percent = workbook.createCellStyle();
        percent.cloneStyleFrom(normal);
        percent.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        XSSFCellStyle percentFormula = workbook.createCellStyle();
        percentFormula.cloneStyleFrom(formula);
        percentFormula.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));
        XSSFCellStyle percentInput = workbook.createCellStyle();
        percentInput.cloneStyleFrom(input);
        percentInput.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));

        XSSFCellStyle warning = workbook.createCellStyle();
        warning.cloneStyleFrom(normal);
        warning.setFillForegroundColor(IndexedColors.LIGHT_ORANGE.getIndex());
        warning.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        XSSFCellStyle error = workbook.createCellStyle();
        error.cloneStyleFrom(normal);
        error.setFillForegroundColor(IndexedColors.ROSE.getIndex());
        error.setFillPattern(FillPatternType.SOLID_FOREGROUND);

        XSSFFont linkFont = workbook.createFont();
        linkFont.setUnderline(Font.U_SINGLE);
        linkFont.setColor(IndexedColors.BLUE.getIndex());
        XSSFCellStyle link = workbook.createCellStyle();
        link.cloneStyleFrom(normal);
        link.setFont(linkFont);
        return new Styles(title, header, normal, wrap, number, input, formula, percent,
                percentFormula, percentInput, warning, error, link);
    }

    private static void borders(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }

    private record Styles(CellStyle title, CellStyle header, CellStyle normal, CellStyle wrap,
                          CellStyle number, CellStyle input, CellStyle formula, CellStyle percent,
                          CellStyle percentFormula, CellStyle percentInput, CellStyle warning,
                          CellStyle error, CellStyle link) {
    }
}
