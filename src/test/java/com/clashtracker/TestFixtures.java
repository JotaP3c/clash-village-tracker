package com.clashtracker;

import com.clashtracker.domain.ActiveUpgrade;
import com.clashtracker.domain.CatalogStep;
import com.clashtracker.domain.CatalogStepKey;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.PendingStepState;
import com.clashtracker.domain.QueueKind;
import com.clashtracker.domain.Representation;
import com.clashtracker.domain.SourceInfo;
import com.clashtracker.domain.UpgradeStepDefinition;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.domain.VillageSnapshot;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class TestFixtures {
    public static final LocalDate DATE = LocalDate.of(2026, 10, 19);

    private TestFixtures() {
    }

    public static VillageEntity entity(String name, String category, int quantity,
                                       Integer current, Integer max, int countAtMax,
                                       int maxSc, int sc1, int sc2, int sc3) {
        String key = com.clashtracker.normalization.LegacyKeyFactory.fromLegacyName(name);
        return new VillageEntity(key, name, name, category,
                com.clashtracker.normalization.LegacyKeyFactory.categoryKey(category), quantity,
                current, max, countAtMax, maxSc, sc1, sc2, sc3,
                quantity > 1 ? Representation.AGGREGATED : Representation.INDIVIDUAL,
                "synthetic", "confirmed", "synthetic fixture",
                new SourceInfo("TEST", "synthetic", DATE, "confirmed"));
    }

    public static VillageSnapshot snapshot(List<VillageEntity> entities, List<ActiveUpgrade> jobs) {
        return new VillageSnapshot(DATE, "#TEST", "Fixture", 18, 6, true,
                new BigDecimal("0.20"), entities.stream().map(VillageEntity::rawCategory).distinct().toList(),
                entities, jobs, "synthetic", "synthetic.json", "a".repeat(64));
    }

    public static ActiveUpgrade job(String name, QueueKind kind, int quantity, Integer from,
                                    Integer target, Long remainingSeconds, String stage) {
        return new ActiveUpgrade(com.clashtracker.normalization.LegacyKeyFactory.fromLegacyName(name),
                name, kind, kind.name(), quantity, from, target, remainingSeconds, stage,
                "confirmed", DATE, "synthetic");
    }

    public static CatalogStep step(String name, String category, UpgradeType type,
                                   int from, int to, int count, Long baseSeconds) {
        return step(name, category, type, from, to, count, baseSeconds, false, null);
    }

    public static CatalogStep step(String name, String category, UpgradeType type,
                                   int from, int to, int count, Long baseSeconds,
                                   boolean inProgress, Long observedSeconds) {
        String version = "test-v1";
        CatalogStepKey key = new CatalogStepKey(version,
                com.clashtracker.normalization.LegacyKeyFactory.fromLegacyName(name), type, from, to);
        String stage = type == UpgradeType.SUPERCHARGE ? "SC" + to : "Nível permanente";
        return new CatalogStep(new UpgradeStepDefinition(key, category, name, stage,
                baseSeconds, "synthetic", "confirmed", baseSeconds == null ? "UNKNOWN" : "BASE"),
                new PendingStepState(key, count, inProgress, observedSeconds, null));
    }

    public static GameCatalog catalog(List<CatalogStep> steps) {
        return catalog(steps, true, true);
    }

    public static GameCatalog catalog(List<CatalogStep> steps, boolean versioned, boolean scopeComplete) {
        return new GameCatalog(versioned ? "test-v1" : "legacy-unversioned", versioned,
                scopeComplete, versioned ? 18 : null, steps, "synthetic", "JSON",
                "b".repeat(64), false);
    }
}
