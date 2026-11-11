package com.clashtracker.normalization;

import com.clashtracker.catalog.LoadedCatalog;
import com.clashtracker.domain.CatalogStep;
import com.clashtracker.domain.CatalogStepKey;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.PendingStepState;
import com.clashtracker.domain.UpgradeStepDefinition;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.importer.raw.RawUpgradeStep;
import com.clashtracker.util.TimeValues;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class CatalogNormalizer {
    public static final String LEGACY_VERSION = "legacy-unversioned";

    public GameCatalog normalize(LoadedCatalog loaded) {
        Map<String, CatalogStep> unique = new LinkedHashMap<>();
        for (RawUpgradeStep raw : loaded.rows()) {
            UpgradeType type = upgradeType(raw);
            CatalogStepKey key = new CatalogStepKey(
                    LEGACY_VERSION,
                    LegacyKeyFactory.fromLegacyName(raw.item()),
                    type,
                    raw.fromLevel(),
                    raw.toLevel());
            UpgradeStepDefinition definition = new UpgradeStepDefinition(
                    key, raw.category(), raw.item(), raw.stage(),
                    TimeValues.daysToSeconds(raw.baseDays()), raw.source(), raw.confidence(),
                    raw.baseDays() == null ? "UNKNOWN" : "LEGACY_BASE_DAYS");
            PendingStepState state = new PendingStepState(
                    key, raw.count(), Boolean.TRUE.equals(raw.inProgress()),
                    TimeValues.daysToSeconds(raw.remainingNowDays()),
                    TimeValues.daysToSeconds(raw.passDays()));
            unique.putIfAbsent(key.logicalId(), new CatalogStep(definition, state));
        }
        return new GameCatalog(
                LEGACY_VERSION, false, false, null,
                unique.values().stream().toList(), loaded.sourcePath().toString(),
                loaded.sourceFormat(), loaded.sourceSha256(), loaded.containsTokenMaterial());
    }

    private static UpgradeType upgradeType(RawUpgradeStep raw) {
        String stage = raw.stage() == null ? "" : raw.stage().toUpperCase(Locale.ROOT);
        String category = raw.category() == null ? "" : raw.category().toUpperCase(Locale.ROOT);
        return stage.startsWith("SC") || category.contains("SOBRECARG")
                ? UpgradeType.SUPERCHARGE : UpgradeType.PERMANENT;
    }
}
