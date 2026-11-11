package com.clashtracker.application;

import com.clashtracker.calculation.CalculationEngine;
import com.clashtracker.catalog.CatalogImporter;
import com.clashtracker.catalog.LoadedCatalog;
import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.Severity;
import com.clashtracker.domain.SnapshotDiff;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.history.SnapshotDiffService;
import com.clashtracker.importer.InputDataException;
import com.clashtracker.importer.LoadedSnapshot;
import com.clashtracker.importer.SnapshotImporter;
import com.clashtracker.normalization.CatalogNormalizer;
import com.clashtracker.normalization.SnapshotNormalizer;
import com.clashtracker.persistence.ArtifactPersistence;
import com.clashtracker.reconciliation.ReconciliationResult;
import com.clashtracker.reconciliation.SnapshotReconciler;
import com.clashtracker.report.ReportModel;
import com.clashtracker.report.excel.ExcelReportWriter;
import com.clashtracker.report.excel.WorkbookSemanticValidator;
import com.clashtracker.report.excel.WorkbookValidationResult;
import com.clashtracker.util.AtomicFiles;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.clashtracker.validation.CatalogValidator;
import com.clashtracker.validation.CrossValidator;
import com.clashtracker.validation.SnapshotValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ReportApplication {
    private final SnapshotImporter snapshotImporter;
    private final CatalogImporter catalogImporter;
    private final SnapshotValidator snapshotValidator = new SnapshotValidator();
    private final CatalogValidator catalogValidator = new CatalogValidator();
    private final SnapshotNormalizer snapshotNormalizer = new SnapshotNormalizer();
    private final CatalogNormalizer catalogNormalizer = new CatalogNormalizer();
    private final CrossValidator crossValidator = new CrossValidator();
    private final SnapshotReconciler reconciler = new SnapshotReconciler();
    private final CalculationEngine calculationEngine = new CalculationEngine();
    private final SnapshotDiffService diffService = new SnapshotDiffService();
    private final ExcelReportWriter excelWriter = new ExcelReportWriter();
    private final WorkbookSemanticValidator workbookValidator = new WorkbookSemanticValidator();
    private final ArtifactPersistence persistence;

    public ReportApplication(ObjectMapper mapper) {
        this.snapshotImporter = new SnapshotImporter(mapper);
        this.catalogImporter = new CatalogImporter(mapper);
        this.persistence = new ArtifactPersistence(mapper);
    }

    public CatalogValidationResult validateCatalog(Path catalogPath) throws InputDataException {
        LoadedCatalog loaded = catalogImporter.load(catalogPath);
        List<ValidationIssue> issues = new ArrayList<>(catalogValidator.validateRaw(loaded));
        if (hasErrors(issues)) {
            return new CatalogValidationResult(ExecutionStatus.FAILED, null, issues);
        }
        GameCatalog catalog = catalogNormalizer.normalize(loaded);
        issues.addAll(catalogValidator.validateNormalized(catalog));
        return new CatalogValidationResult(status(issues, true), catalog, issues);
    }

    /**
     * Runs the full offline pipeline up to the calculation stage WITHOUT writing any artifact to
     * disk. Returns a read-only projection suitable for serving a web dashboard. The Excel workbook
     * and historical artifacts are produced only by {@link #generate(ReportRequest)}.
     */
    public VillageAnalysis analyze(ReportRequest request) throws InputDataException {
        LoadedSnapshot loadedSnapshot = snapshotImporter.load(request.snapshotPath());
        LoadedCatalog loadedCatalog = catalogImporter.load(request.catalogPath());
        List<ValidationIssue> issues = new ArrayList<>();
        issues.addAll(snapshotValidator.validate(loadedSnapshot.raw()));
        issues.addAll(catalogValidator.validateRaw(loadedCatalog));
        if (hasErrors(issues)) {
            return analysisFailed("Structural validation failed; no analysis was produced", issues);
        }

        VillageSnapshot incoming = snapshotNormalizer.normalize(loadedSnapshot);
        GameCatalog catalog = catalogNormalizer.normalize(loadedCatalog);
        issues.addAll(catalogValidator.validateNormalized(catalog));
        issues.addAll(crossValidator.validate(incoming, catalog));
        if (hasErrors(issues)) {
            return analysisFailed("Cross-source structural validation failed; no analysis was produced", issues);
        }

        VillageSnapshot previous = null;
        SnapshotDiff diff = null;
        if (request.previousSnapshotPath() != null) {
            LoadedSnapshot loadedPrevious = snapshotImporter.load(request.previousSnapshotPath());
            List<ValidationIssue> previousIssues = snapshotValidator.validate(loadedPrevious.raw());
            issues.addAll(previousIssues);
            if (hasErrors(previousIssues)) {
                return analysisFailed("Previous snapshot is structurally invalid; no diff was produced", issues);
            }
            previous = snapshotNormalizer.normalize(loadedPrevious);
            diff = diffService.compare(previous, incoming);
        }

        ReconciliationResult reconciliation = reconciler.reconcile(previous, incoming);
        issues.addAll(reconciliation.issues());
        VillageSnapshot reconciled = reconciliation.snapshot();
        if (hasErrors(issues)) {
            return analysisFailed("Reconciliation produced a structural conflict; no analysis was produced", issues);
        }

        CalculationResult calculation = calculationEngine.calculate(
                reconciled, catalog, request.goldPassBoost(), issues);
        ExecutionStatus status = status(issues, calculation.durations().exactTotalAvailable());
        String message = status == ExecutionStatus.SUCCESS
                ? "Offline analysis produced successfully"
                : "Offline analysis produced with documented pending validation";
        return new VillageAnalysis(status, message, reconciled, catalog, calculation, diff,
                request.goldPassBoost(), issues);
    }

    public ReportExecutionResult generate(ReportRequest request) throws InputDataException, IOException {
        VillageAnalysis analysis = analyze(request);
        if (analysis.failed()) {
            return failed(analysis.message(), analysis.issues());
        }

        List<ValidationIssue> issues = new ArrayList<>(analysis.issues());
        ExecutionStatus status = analysis.status();
        ReportModel reportModel = new ReportModel(analysis.snapshot(), analysis.catalog(),
                analysis.calculation(), issues, analysis.diff(), request.goldPassBoost());

        Path output = request.outputDirectory().toAbsolutePath().normalize();
        Files.createDirectories(output);
        Path target = output.resolve("report.xlsx");
        Path temporaryWorkbook = AtomicFiles.temporarySibling(target);
        excelWriter.write(reportModel, temporaryWorkbook);
        WorkbookValidationResult validation = workbookValidator.validate(temporaryWorkbook);
        List<String> artifacts = persistence.persist(reportModel, status, output, temporaryWorkbook, validation);
        String message = status == ExecutionStatus.SUCCESS
                ? "Offline report generated successfully"
                : "Offline report generated with documented pending validation";
        return new ReportExecutionResult(status, message, issues, analysis.calculation(), validation, artifacts);
    }

    private static VillageAnalysis analysisFailed(String message, List<ValidationIssue> issues) {
        return new VillageAnalysis(ExecutionStatus.FAILED, message, null, null, null, null, null, issues);
    }

    private static ExecutionStatus status(List<ValidationIssue> issues, boolean exactTotalAvailable) {
        if (hasErrors(issues)) {
            return ExecutionStatus.FAILED;
        }
        boolean warnings = issues.stream().anyMatch(issue -> issue.severity() == Severity.WARNING);
        return warnings || !exactTotalAvailable ? ExecutionStatus.PARTIAL_SUCCESS : ExecutionStatus.SUCCESS;
    }

    private static boolean hasErrors(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
    }

    private static ReportExecutionResult failed(String message, List<ValidationIssue> issues) {
        return new ReportExecutionResult(ExecutionStatus.FAILED, message, issues, null, null, List.of());
    }
}
