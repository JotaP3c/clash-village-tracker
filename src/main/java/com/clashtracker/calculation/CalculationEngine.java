package com.clashtracker.calculation;

import com.clashtracker.domain.ActiveUpgrade;
import com.clashtracker.domain.CalculatedStep;
import com.clashtracker.domain.CalculationResult;
import com.clashtracker.domain.CatalogStep;
import com.clashtracker.domain.CategoryProgress;
import com.clashtracker.domain.DurationSummary;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.ProgressMetrics;
import com.clashtracker.domain.QueueKind;
import com.clashtracker.domain.QueueWorkload;
import com.clashtracker.domain.Severity;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.normalization.LegacyKeyFactory;
import com.clashtracker.util.TimeValues;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CalculationEngine {
    public CalculationResult calculate(VillageSnapshot snapshot, GameCatalog catalog,
                                       BigDecimal goldPassBoost, List<ValidationIssue> issues) {
        requireBoost(goldPassBoost);
        Map<String, List<VillageEntity>> entitiesByLegacyKey = new HashMap<>();
        for (VillageEntity entity : snapshot.entities()) {
            entitiesByLegacyKey.computeIfAbsent(entity.entityKey(), ignored -> new ArrayList<>()).add(entity);
        }

        List<CalculatedStep> calculated = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        long baseKnownTotal = 0;
        long discountedFutureTotal = 0;
        long observedTotal = 0;
        long scenarioTotal = 0;
        long builderSeconds = 0;
        long laboratorySeconds = 0;
        long petSeconds = 0;
        int unknownOccurrences = 0;

        for (CatalogStep step : catalog.steps()) {
            VillageEntity entity = resolveEntity(step,
                    entitiesByLegacyKey.get(step.definition().key().entityKey()));
            if (step.definition().key().upgradeType() == UpgradeType.SUPERCHARGE
                    && (entity == null || entity.maxSupercharge() == null
                    || entity.maxSupercharge() < step.definition().key().toLevel())) {
                continue;
            }
            int count = step.state().count();
            ActiveMatch active = matchActive(step, snapshot.activeUpgrades());
            int activeCount = Math.min(count, active.count());
            long observed = active.observedSeconds();
            int observedCount = active.observedCount();

            if (activeCount == 0 && step.state().inProgress() && step.state().observedRemainingSeconds() != null) {
                activeCount = count;
                observedCount = count;
                observed = Math.multiplyExact(step.state().observedRemainingSeconds(), count);
            }
            int futureCount = Math.max(0, count - activeCount);
            Long base = step.definition().baseDurationSeconds();
            Long discounted = base == null ? null : TimeValues.applyBoost(base, goldPassBoost);
            long rowBaseKnown = base == null ? 0 : Math.multiplyExact(base, count);
            long rowDiscountedFuture = discounted == null ? 0 : Math.multiplyExact(discounted, futureCount);
            long rowScenario = Math.addExact(rowDiscountedFuture, observed);
            boolean exact = (count == 0 || base != null) && observedCount >= activeCount;
            if (!exact) {
                String reason = base == null ? "base duration unknown" : "active observed balance missing";
                missing.add(step.definition().key().logicalId() + " (" + reason + ", count=" + count + ")");
            }
            if (base == null) {
                unknownOccurrences += count;
            }

            QueueKind queue = queueKind(step, entity);
            calculated.add(new CalculatedStep(
                    step.definition().key(), step.definition().rawCategory(), step.definition().rawItemName(),
                    step.definition().stage(), count, activeCount, futureCount, queue, base, discounted,
                    observed, rowBaseKnown, rowScenario, exact, step.definition().source(),
                    step.definition().confidence()));
            baseKnownTotal = Math.addExact(baseKnownTotal, rowBaseKnown);
            discountedFutureTotal = Math.addExact(discountedFutureTotal, rowDiscountedFuture);
            observedTotal = Math.addExact(observedTotal, observed);
            scenarioTotal = Math.addExact(scenarioTotal, rowScenario);
            switch (queue) {
                case BUILDER -> builderSeconds = Math.addExact(builderSeconds, rowScenario);
                case LABORATORY -> laboratorySeconds = Math.addExact(laboratorySeconds, rowScenario);
                case PET -> petSeconds = Math.addExact(petSeconds, rowScenario);
            }
        }

        calculated.sort(Comparator
                .comparing(CalculatedStep::rawCategory, Comparator.nullsLast(String::compareTo))
                .thenComparing(CalculatedStep::rawItemName, Comparator.nullsLast(String::compareTo))
                .thenComparingInt(row -> row.key().fromLevel()));

        ProgressMetrics progress = calculateProgress(snapshot, catalog, issues, unknownOccurrences);
        DurationSummary durations = new DurationSummary(baseKnownTotal, discountedFutureTotal,
                observedTotal, scenarioTotal, missing.isEmpty(), missing);
        QueueWorkload workload = new QueueWorkload(builderSeconds, laboratorySeconds, petSeconds,
                snapshot.permanentBuilderCapacity(), snapshot.goblinBuilderTemporary());
        return new CalculationResult(calculated, durations, progress, workload);
    }

    private static ProgressMetrics calculateProgress(VillageSnapshot snapshot, GameCatalog catalog,
                                                     List<ValidationIssue> issues, int unknownStepCount) {
        long permanentCompleted = 0;
        long permanentTotal = 0;
        long scCompleted = 0;
        long scTotal = 0;
        int knownEntities = 0;
        int unknownEntities = 0;
        int potentiallyMissing = 0;
        Map<String, MutableCategory> categoryValues = new LinkedHashMap<>();

        for (VillageEntity entity : snapshot.entities()) {
            MutableCategory category = categoryValues.computeIfAbsent(entity.rawCategory(), key -> new MutableCategory());
            category.entityCount++;
            boolean permanentKnown = entity.currentLevel() != null && entity.maxLevel() != null;
            boolean superchargeKnown = entity.maxSupercharge() != null && entity.sc1Completed() != null
                    && entity.sc2Completed() != null && entity.sc3Completed() != null;
            if (permanentKnown && superchargeKnown) {
                knownEntities++;
            } else {
                unknownEntities++;
                potentiallyMissing += entity.quantity();
                category.unknownEntityCount++;
            }

            if (permanentKnown) {
                int atMax = Math.min(entity.countAtMax(), entity.quantity());
                int belowMax = entity.quantity() - atMax;
                long entityCompleted = Math.addExact(
                        Math.multiplyExact((long) entity.maxLevel(), atMax),
                        Math.multiplyExact((long) entity.currentLevel(), belowMax));
                long entityTotal = Math.multiplyExact((long) entity.maxLevel(), entity.quantity());
                permanentCompleted = Math.addExact(permanentCompleted, entityCompleted);
                permanentTotal = Math.addExact(permanentTotal, entityTotal);
                category.completed += entityCompleted;
                category.total += entityTotal;
            }

            if (superchargeKnown) {
                long entityScCompleted = (long) entity.sc1Completed()
                        + entity.sc2Completed() + entity.sc3Completed();
                long entityScTotal = Math.multiplyExact((long) entity.maxSupercharge(), entity.quantity());
                scCompleted = Math.addExact(scCompleted, entityScCompleted);
                scTotal = Math.addExact(scTotal, entityScTotal);
                category.completed += entityScCompleted;
                category.total += entityScTotal;
            }
        }

        long overallCompleted = Math.addExact(permanentCompleted, scCompleted);
        long overallTotal = Math.addExact(permanentTotal, scTotal);
        BigDecimal coverage = ratio(knownEntities, snapshot.entities().size());
        List<CategoryProgress> categories = new ArrayList<>();
        for (Map.Entry<String, MutableCategory> entry : categoryValues.entrySet()) {
            MutableCategory value = entry.getValue();
            categories.add(new CategoryProgress(entry.getKey(), value.completed, value.total,
                    Math.max(0, value.total - value.completed), ratio(value.completed, value.total),
                    value.entityCount, value.unknownEntityCount));
        }

        boolean hasErrors = issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR);
        boolean hasPendingRows = catalog.steps().stream().anyMatch(step -> step.state().count() > 0);
        boolean completeCoverage = coverage.compareTo(BigDecimal.ONE) == 0;
        boolean allKnownProgressComplete = overallTotal > 0 && overallCompleted == overallTotal;
        boolean full = catalog.explicitlyVersioned() && catalog.scopeComplete() && completeCoverage
                && !hasErrors && unknownStepCount == 0 && !hasPendingRows && allKnownProgressComplete;
        String message;
        if (full) {
            message = "FULL absoluto certificado para o escopo e versão do catálogo.";
        } else if (!catalog.explicitlyVersioned() || !catalog.scopeComplete()) {
            message = "FULL não certificado: catálogo legacy-unversioned e/ou escopo incompleto.";
        } else if (!completeCoverage) {
            message = "FULL não certificado: cobertura de dados incompleta.";
        } else if (unknownStepCount > 0) {
            message = "FULL não certificado: existem durações obrigatórias desconhecidas.";
        } else if (hasPendingRows) {
            message = "FULL não certificado: existem etapas pendentes.";
        } else {
            message = "FULL não certificado: há erros ou progresso incompleto.";
        }

        return new ProgressMetrics(permanentCompleted, permanentTotal, ratio(permanentCompleted, permanentTotal),
                scCompleted, scTotal, ratio(scCompleted, scTotal), overallCompleted, overallTotal,
                ratio(overallCompleted, overallTotal), coverage, unknownEntities, potentiallyMissing,
                unknownStepCount, full, message, categories);
    }

    private static VillageEntity resolveEntity(CatalogStep step, List<VillageEntity> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (step.definition().key().upgradeType() == UpgradeType.PERMANENT) {
            String categoryKey = LegacyKeyFactory.categoryKey(step.definition().rawCategory());
            List<VillageEntity> matching = candidates.stream()
                    .filter(entity -> categoryKey.equals(entity.categoryKey()))
                    .toList();
            return matching.size() == 1 ? matching.get(0) : null;
        }
        List<VillageEntity> eligible = candidates.stream()
                .filter(entity -> entity.maxSupercharge() != null
                        && entity.maxSupercharge() >= step.definition().key().toLevel())
                .toList();
        return eligible.size() == 1 ? eligible.get(0) : null;
    }

    private static ActiveMatch matchActive(CatalogStep step, List<ActiveUpgrade> jobs) {
        int count = 0;
        int observedCount = 0;
        long observedSeconds = 0;
        for (ActiveUpgrade job : jobs) {
            if (!step.definition().key().entityKey().equals(job.entityKey())) {
                continue;
            }
            boolean matches;
            if (step.definition().key().upgradeType() == UpgradeType.SUPERCHARGE) {
                String stage = job.stage() == null ? "" : job.stage().toUpperCase(Locale.ROOT);
                matches = stage.contains(step.definition().stage().toUpperCase(Locale.ROOT));
            } else {
                matches = java.util.Objects.equals(job.fromLevel(), step.definition().key().fromLevel())
                        && java.util.Objects.equals(job.targetLevel(), step.definition().key().toLevel());
            }
            if (!matches) {
                continue;
            }
            count += job.quantity();
            if (job.observedRemainingSeconds() != null) {
                observedCount += job.quantity();
                observedSeconds = Math.addExact(observedSeconds,
                        Math.multiplyExact(job.observedRemainingSeconds(), job.quantity()));
            }
        }
        return new ActiveMatch(count, observedCount, observedSeconds);
    }

    private static QueueKind queueKind(CatalogStep step, VillageEntity entity) {
        String category = entity == null ? step.definition().rawCategory() : entity.categoryKey();
        if (category == null) {
            return QueueKind.BUILDER;
        }
        String normalized = category.toUpperCase(Locale.ROOT);
        if (normalized.contains("PET")) {
            return QueueKind.PET;
        }
        if (normalized.contains("TROOP") || normalized.contains("SPELL") || normalized.contains("SIEGE")
                || normalized.contains("TROPA") || normalized.contains("FEITI") || normalized.contains("MÁQUINA")) {
            return QueueKind.LABORATORY;
        }
        return QueueKind.BUILDER;
    }

    private static BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private static void requireBoost(BigDecimal boost) {
        if (boost == null || boost.compareTo(BigDecimal.ZERO) < 0 || boost.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("goldPassBoost must be between 0 and 1");
        }
    }

    private record ActiveMatch(int count, int observedCount, long observedSeconds) {
    }

    private static final class MutableCategory {
        private long completed;
        private long total;
        private int entityCount;
        private int unknownEntityCount;
    }
}
