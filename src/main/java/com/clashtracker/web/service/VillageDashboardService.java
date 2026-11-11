package com.clashtracker.web.service;

import com.clashtracker.application.ReportApplication;
import com.clashtracker.application.ReportRequest;
import com.clashtracker.application.VillageAnalysis;
import com.clashtracker.domain.CalculatedStep;
import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.CategoryProgress;
import com.clashtracker.domain.DurationSummary;
import com.clashtracker.domain.ProgressMetrics;
import com.clashtracker.domain.QueueWorkload;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.importer.InputDataException;
import com.clashtracker.web.config.TrackerProperties;
import com.clashtracker.web.dto.CategoryResponse;
import com.clashtracker.web.dto.DashboardResponse;
import com.clashtracker.web.dto.SnapshotResponse;
import com.clashtracker.web.dto.UpgradeResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the read-only dashboard by running the existing offline analysis pipeline in memory
 * and mapping its immutable domain result into transport DTOs. No business rule is recomputed here:
 * all figures come straight from {@code CalculationEngine} via {@link ReportApplication#analyze}.
 */
@Service
public class VillageDashboardService {

    private static final double SECONDS_PER_DAY = 86_400.0;

    private final ReportApplication reportApplication;
    private final TrackerProperties properties;

    public VillageDashboardService(ReportApplication reportApplication, TrackerProperties properties) {
        this.reportApplication = reportApplication;
        this.properties = properties;
    }

    /** Runs the offline analysis against the configured historical snapshot and catalog. */
    public VillageAnalysis analyze() throws InputDataException {
        Path snapshot = Path.of(properties.getSnapshotPath());
        Path catalog = Path.of(properties.getCatalogPath());
        Path previous = properties.getPreviousSnapshotPath() == null
                || properties.getPreviousSnapshotPath().isBlank()
                ? null : Path.of(properties.getPreviousSnapshotPath());
        // Output directory is unused by analyze(); it never writes. Pass a placeholder path.
        ReportRequest request = new ReportRequest(snapshot, catalog, previous,
                properties.getGoldPassBoost(), Path.of("output"), true);
        return reportApplication.analyze(request);
    }

    public DashboardResponse dashboard() throws InputDataException {
        VillageAnalysis analysis = analyze();
        VillageSnapshot snapshot = analysis.snapshot();
        CalculationResult calculation = analysis.calculation();
        ProgressMetrics progress = calculation.progress();
        DurationSummary durations = calculation.durations();
        QueueWorkload workload = calculation.workloads();

        DashboardResponse.VillageInfo village = new DashboardResponse.VillageInfo(
                snapshot.playerTag(), snapshot.playerName(), snapshot.townHallLevel(),
                snapshot.observedAt() == null ? null : snapshot.observedAt().toString(), true,
                analysis.goldPassBoost().doubleValue(), snapshot.permanentBuilderCapacity(),
                snapshot.goblinBuilderTemporary());

        DashboardResponse.ProgressSummary progressSummary = new DashboardResponse.ProgressSummary(
                percent(progress.overallCataloguedProgress()), progress.overallCompletedSteps(),
                progress.overallTotalSteps(),
                Math.max(0, progress.overallTotalSteps() - progress.overallCompletedSteps()),
                percent(progress.permanentProgress()), progress.permanentCompletedSteps(),
                progress.permanentTotalSteps(), percent(progress.superchargeProgress()),
                progress.superchargeCompletedSteps(), progress.superchargeTotalSteps(),
                progress.fullCertified(), progress.fullCertificationMessage());

        DashboardResponse.DurationSummaryDto durationDto = new DashboardResponse.DurationSummaryDto(
                durations.scenarioKnownSeconds(), days(durations.scenarioKnownSeconds()),
                durations.baseKnownSeconds(), durations.discountedFutureKnownSeconds(),
                durations.observedRemainingSeconds(), durations.exactTotalAvailable(),
                durations.missingStepKeys().size());

        double builderDays = days(workload.builderSeconds());
        int capacity = Math.max(1, workload.permanentBuilderCapacity());
        DashboardResponse.BuildersSummary builders = new DashboardResponse.BuildersSummary(
                workload.permanentBuilderCapacity(), workload.goblinBuilderTemporary(),
                builderDays, days(workload.laboratorySeconds()), days(workload.petSeconds()),
                // Lower bound only: total builder work spread across permanent builders. This is
                // NOT a confirmed completion date (ignores dependencies, slots, and resources).
                round2(builderDays / capacity));

        List<DashboardResponse.CategorySummary> categories = new ArrayList<>();
        for (CategoryProgress category : progress.categories()) {
            categories.add(new DashboardResponse.CategorySummary(category.category(),
                    category.completedSteps(), category.totalSteps(), category.remainingSteps(),
                    percent(category.progress()), category.entityCount(), category.unknownEntityCount()));
        }

        DashboardResponse.DataQuality dataQuality = new DashboardResponse.DataQuality(
                percent(progress.coverage()), progress.unknownEntityCount(),
                progress.potentiallyMissingEntityCount(), progress.unknownUpgradeStepCount(),
                analysis.issues().size());

        return new DashboardResponse(analysis.status().name(), analysis.message(), village,
                progressSummary, durationDto, builders, categories, dataQuality);
    }

    public SnapshotResponse snapshot() throws InputDataException {
        VillageAnalysis analysis = analyze();
        VillageSnapshot snapshot = analysis.snapshot();
        List<SnapshotResponse.EntityView> entities = new ArrayList<>();
        for (VillageEntity entity : snapshot.entities()) {
            entities.add(toEntityView(entity));
        }
        return new SnapshotResponse(snapshot.playerTag(), snapshot.playerName(),
                snapshot.townHallLevel(),
                snapshot.observedAt() == null ? null : snapshot.observedAt().toString(), true,
                snapshot.permanentBuilderCapacity(), snapshot.goblinBuilderTemporary(),
                snapshot.declaredCategories(), entities);
    }

    public CategoryResponse categories() throws InputDataException {
        VillageAnalysis analysis = analyze();
        VillageSnapshot snapshot = analysis.snapshot();
        ProgressMetrics progress = analysis.calculation().progress();

        Map<String, List<SnapshotResponse.EntityView>> entitiesByCategory = new LinkedHashMap<>();
        for (VillageEntity entity : snapshot.entities()) {
            entitiesByCategory.computeIfAbsent(entity.rawCategory(), ignored -> new ArrayList<>())
                    .add(toEntityView(entity));
        }

        List<CategoryResponse.CategoryDetail> details = new ArrayList<>();
        for (CategoryProgress category : progress.categories()) {
            List<SnapshotResponse.EntityView> entities =
                    entitiesByCategory.getOrDefault(category.category(), List.of());
            details.add(new CategoryResponse.CategoryDetail(category.category(),
                    category.completedSteps(), category.totalSteps(), category.remainingSteps(),
                    percent(category.progress()), category.entityCount(),
                    category.unknownEntityCount(), entities));
        }
        return new CategoryResponse(details);
    }

    public UpgradeResponse upgrades() throws InputDataException {
        VillageAnalysis analysis = analyze();
        List<CalculatedStep> steps = analysis.calculation().steps();
        List<UpgradeResponse.UpgradeRow> rows = new ArrayList<>();
        int inProgress = 0;
        for (CalculatedStep step : steps) {
            if (step.count() <= 0) {
                continue;
            }
            if (step.activeCount() > 0) {
                inProgress++;
            }
            boolean durationKnown = step.baseDurationSecondsPerUnit() != null;
            rows.add(new UpgradeResponse.UpgradeRow(
                    step.rawCategory(), step.rawItemName(), step.stage(),
                    step.key().upgradeType().name(), step.key().fromLevel(), step.key().toLevel(),
                    step.count(), step.activeCount(), step.futureCount(), step.queueKind().name(),
                    step.baseDurationSecondsPerUnit(), daysOrNull(step.baseDurationSecondsPerUnit()),
                    step.discountedDurationSecondsPerUnit(),
                    daysOrNull(step.discountedDurationSecondsPerUnit()),
                    step.observedRemainingSecondsTotal(), durationKnown, step.source(),
                    step.confidence()));
        }
        return new UpgradeResponse(rows.size(), inProgress, rows);
    }

    private static SnapshotResponse.EntityView toEntityView(VillageEntity entity) {
        String name = entity.displayNamePtBr() != null && !entity.displayNamePtBr().isBlank()
                ? entity.displayNamePtBr() : entity.rawName();
        return new SnapshotResponse.EntityView(entity.entityKey(), name, entity.rawCategory(),
                entity.quantity(), entity.currentLevel(), entity.maxLevel(), entity.countAtMax(),
                entity.maxSupercharge(), entity.sc1Completed(), entity.sc2Completed(),
                entity.sc3Completed(),
                entity.representation() == null ? null : entity.representation().name(),
                entity.confidence(), entity.notes());
    }

    private static double percent(BigDecimal ratio) {
        if (ratio == null) {
            return 0.0;
        }
        return ratio.multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private static double days(long seconds) {
        return round2(seconds / SECONDS_PER_DAY);
    }

    private static Double daysOrNull(Long seconds) {
        return seconds == null ? null : round2(seconds / SECONDS_PER_DAY);
    }

    private static double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
