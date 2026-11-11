package com.clashtracker.persistence;

import com.clashtracker.application.ExecutionStatus;
import com.clashtracker.domain.CalculatedStep;
import com.clashtracker.domain.CatalogStep;
import com.clashtracker.report.ReportModel;
import com.clashtracker.report.excel.WorkbookValidationResult;
import com.clashtracker.util.AtomicFiles;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ArtifactPersistence {
    private final ObjectMapper mapper;

    public ArtifactPersistence(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public List<String> persist(ReportModel model, ExecutionStatus status, Path outputDirectory,
                                Path generatedWorkbook, WorkbookValidationResult workbookValidation) throws IOException {
        Path output = outputDirectory.toAbsolutePath().normalize();
        Files.createDirectories(output);
        List<String> artifacts = new ArrayList<>();

        Path report = output.resolve("report.xlsx");
        AtomicFiles.replace(generatedWorkbook, report);
        artifacts.add(report.toString());

        Path normalized = output.resolve("normalized-snapshot.json");
        writeJsonAtomic(normalized, model.snapshot());
        artifacts.add(normalized.toString());

        Path pending = output.resolve("pending-validation.json");
        writeJsonAtomic(pending, model.issues());
        artifacts.add(pending.toString());

        Map<String, Object> summary = summary(model, status, workbookValidation);
        Path summaryPath = output.resolve("report-summary.json");
        writeJsonAtomic(summaryPath, summary);
        artifacts.add(summaryPath.toString());

        if (model.diff() != null) {
            Path diff = output.resolve("diff.json");
            writeJsonAtomic(diff, model.diff());
            artifacts.add(diff.toString());
        }

        Path log = output.resolve("execution-summary.log");
        String logText = logText(model, status, workbookValidation, artifacts);
        writeBytesAtomic(log, logText.getBytes(StandardCharsets.UTF_8));
        artifacts.add(log.toString());

        persistImmutableHistory(model, summary, output.resolve("history"));
        return List.copyOf(artifacts);
    }

    private void persistImmutableHistory(ReportModel model, Map<String, Object> summary, Path historyRoot) throws IOException {
        Files.createDirectories(historyRoot);
        String date = model.snapshot().observedAt() == null ? "unknown-date" : model.snapshot().observedAt().toString();
        String id = date + "-" + model.snapshot().sourceSha256().substring(0, 12);
        Path target = historyRoot.resolve(id);
        if (Files.exists(target)) {
            return;
        }
        Path staging = historyRoot.resolve("." + id + "." + UUID.randomUUID() + ".tmp");
        Files.createDirectory(staging);
        mapper.writeValue(staging.resolve("normalized-snapshot.json").toFile(), model.snapshot());
        mapper.writeValue(staging.resolve("report-summary.json").toFile(), summary);
        try {
            Files.move(staging, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException ignored) {
            // Another identical execution persisted the same immutable fingerprint first.
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            try {
                Files.move(staging, target);
            } catch (FileAlreadyExistsException ignored) {
                // Idempotent duplicate; the existing immutable entry wins.
            }
        }
    }

    private Map<String, Object> summary(ReportModel model, ExecutionStatus status,
                                        WorkbookValidationResult workbookValidation) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", status.name());
        summary.put("snapshotDate", model.snapshot().observedAt());
        summary.put("snapshotSha256", model.snapshot().sourceSha256());
        summary.put("historicalSnapshot", true);
        summary.put("entityCount", model.snapshot().entities().size());
        summary.put("declaredQuantity", model.snapshot().entities().stream().mapToInt(entity -> entity.quantity()).sum());
        summary.put("queueRowCount", model.snapshot().activeUpgrades().size());
        summary.put("catalogVersion", model.catalog().catalogVersion());
        summary.put("catalogExplicitlyVersioned", model.catalog().explicitlyVersioned());
        summary.put("catalogScopeComplete", model.catalog().scopeComplete());
        summary.put("catalogRowCount", model.catalog().steps().size());
        summary.put("knownBaseDurationRows", model.catalog().steps().stream()
                .filter(step -> step.definition().baseDurationSeconds() != null).count());
        summary.put("unknownBaseDurationRows", model.catalog().steps().stream()
                .filter(step -> step.definition().baseDurationSeconds() == null).count());
        summary.put("knownDurationOccurrences", model.catalog().steps().stream()
                .filter(step -> step.definition().baseDurationSeconds() != null)
                .mapToInt(step -> step.state().count()).sum());
        summary.put("unknownDurationOccurrences", model.catalog().steps().stream()
                .filter(step -> step.definition().baseDurationSeconds() == null)
                .mapToInt(step -> step.state().count()).sum());
        summary.put("durations", model.calculation().durations());
        summary.put("workloads", model.calculation().workloads());
        summary.put("progress", model.calculation().progress());
        summary.put("validationIssueCount", model.issues().size());
        summary.put("workbookValidation", workbookValidation);
        summary.put("diffGenerated", model.diff() != null);
        return summary;
    }

    private static String logText(ReportModel model, ExecutionStatus status,
                                  WorkbookValidationResult workbookValidation, List<String> artifacts) {
        long errors = model.issues().stream().filter(issue -> issue.severity().name().equals("ERROR")).count();
        long warnings = model.issues().stream().filter(issue -> issue.severity().name().equals("WARNING")).count();
        return """
                Clash Village Tracker — offline execution summary
                status=%s
                historicalSnapshotDate=%s
                snapshotSha256=%s
                entities=%d
                declaredQuantity=%d
                queueRows=%d
                catalogVersion=%s
                catalogRows=%d
                validationErrors=%d
                validationWarnings=%d
                exactTotalAvailable=%s
                missingStepKeys=%d
                fullCertified=%s
                workbookSheets=%d
                workbookFormulas=%d
                workbookTables=%d
                workbookCharts=%d
                chartLinkedToFormulaCells=%s
                artifacts=%s
                """.formatted(status, model.snapshot().observedAt(), model.snapshot().sourceSha256(),
                model.snapshot().entities().size(),
                model.snapshot().entities().stream().mapToInt(entity -> entity.quantity()).sum(),
                model.snapshot().activeUpgrades().size(), model.catalog().catalogVersion(),
                model.catalog().steps().size(), errors, warnings,
                model.calculation().durations().exactTotalAvailable(),
                model.calculation().durations().missingStepKeys().size(),
                model.calculation().progress().fullCertified(), workbookValidation.sheetNames().size(),
                workbookValidation.formulaCount(), workbookValidation.tableCount(),
                workbookValidation.chartCount(), workbookValidation.chartLinkedToFormulaCells(), artifacts);
    }

    private void writeJsonAtomic(Path target, Object value) throws IOException {
        Path temporary = AtomicFiles.temporarySibling(target);
        mapper.writeValue(temporary.toFile(), value);
        AtomicFiles.replace(temporary, target);
    }

    private static void writeBytesAtomic(Path target, byte[] value) throws IOException {
        Path temporary = AtomicFiles.temporarySibling(target);
        Files.write(temporary, value);
        AtomicFiles.replace(temporary, target);
    }
}
