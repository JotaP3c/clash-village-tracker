package com.clashtracker.web.service;

import com.clashtracker.application.ReportApplication;
import com.clashtracker.util.JsonSupport;
import com.clashtracker.web.config.TrackerProperties;
import com.clashtracker.web.dto.DashboardResponse;
import com.clashtracker.web.dto.UpgradeResponse;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Deterministic business-rule regression for the read-only web mapping layer. Instead of relying on
 * the full historical dataset, each test writes a small, controlled snapshot + catalog (in the same
 * raw JSON shape as {@code data/*.json}) to a temp directory and asserts that the DTOs produced by
 * {@link VillageDashboardService} preserve the project's critical rules end-to-end
 * (importer -> normalizer -> calculation -> DTO).
 *
 * <p>Rules covered here (see AGENTS.md and docs/19_testes_e_pendencias.md):
 * six permanent builders with a temporary Goblin builder; the Gold Pass boost applied only once and
 * only to eligible future steps; an in-progress upgrade that never advances the completed level nor
 * re-discounts the observed timer; and unknown durations surfaced as unknown rather than zero.
 */
class VillageDashboardServiceRulesTest {

    @TempDir
    Path tempDir;

    private VillageDashboardService serviceFor(String snapshotJson, String catalogJson,
                                               String goldPassBoost) throws Exception {
        Path snapshot = Files.writeString(tempDir.resolve("snapshot.json"), snapshotJson,
                StandardCharsets.UTF_8);
        Path catalog = Files.writeString(tempDir.resolve("catalog.json"), catalogJson,
                StandardCharsets.UTF_8);
        TrackerProperties properties = new TrackerProperties();
        properties.setSnapshotPath(snapshot.toString());
        properties.setCatalogPath(catalog.toString());
        properties.setGoldPassBoost(new java.math.BigDecimal(goldPassBoost));
        return new VillageDashboardService(new ReportApplication(JsonSupport.createMapper()), properties);
    }

    private static String snapshot(String itemsJson) {
        return """
                {
                  "date": "2026-10-19",
                  "player_tag": "#U8J89QP2",
                  "player_name": "jaoo",
                  "town_hall": 18,
                  "builders_permanent": 6,
                  "builder_goblin_temporary": true,
                  "gold_pass_time_reduction": 0.2,
                  "categories": ["Defesas", "Guardiões"],
                  "items": [%s],
                  "queue": []
                }
                """.formatted(itemsJson);
    }

    private static String item(String category, String name, int quantity, int current, int max,
                               int countAtMax, int maxSc, int sc1, int sc2, int sc3, String notes) {
        return """
                {
                  "category": "%s", "name": "%s", "quantity": %d,
                  "current_level": %d, "max_level": %d, "count_at_max": %d,
                  "max_supercharge": %d, "sc1_completed": %d, "sc2_completed": %d, "sc3_completed": %d,
                  "confidence": "print", "notes": "%s", "source": "synthetic"
                }
                """.formatted(category, name, quantity, current, max, countAtMax, maxSc, sc1, sc2, sc3, notes);
    }

    private static String permanentStep(String category, String item, int from, int to, int count,
                                        Object baseDays, boolean inProgress, Object remainingDays) {
        return """
                {
                  "category": "%s", "item": "%s", "from_level": %d, "to_level": %d,
                  "stage": "Nível permanente", "count": %d, "base_days": %s, "pass_days": null,
                  "in_progress": %s, "remaining_now_days": %s, "source": "synthetic",
                  "confidence": "confirmado"
                }
                """.formatted(category, item, from, to, count, baseDays, inProgress, remainingDays);
    }

    @Test
    void goldPassBoostIsAppliedOnceOnlyToEligibleFutureStep() throws Exception {
        // One pending permanent step, 10 base days, not in progress, 20% boost.
        String snapshot = snapshot(item("Defesas", "Canhão", 1, 1, 2, 0, 0, 0, 0, 0, ""));
        String catalog = "[" + permanentStep("Defesas", "Canhão", 1, 2, 1, "10", false, "null") + "]";
        DashboardResponse dashboard = serviceFor(snapshot, catalog, "0.20").dashboard();

        // base 10 days = 864000 s; discounted once by 20% -> 691200 s. Must not be discounted twice.
        assertEquals(864_000L, dashboard.durations().baseKnownSeconds());
        assertEquals(691_200L, dashboard.durations().scenarioKnownSeconds());
        assertTrue(dashboard.durations().exactTotalAvailable());
    }

    @Test
    void observedTimerIsNotDiscountedAgainAndCompletedLevelStaysBehind() throws Exception {
        // Smasher completed level 3, upgrading to 4, with an observed remaining balance (2 days).
        String snapshot = snapshot(item("Guardiões", "Smasher", 1, 3, 5, 0, 0, 0, 0, 0,
                "3 concluído; melhorando para 4"));
        String catalog = "["
                + permanentStep("Guardiões", "Smasher", 3, 4, 1, "10", true, "2") + ","
                + permanentStep("Guardiões", "Smasher", 4, 5, 1, "12", false, "null") + "]";
        VillageDashboardService service = serviceFor(snapshot, catalog, "0.20");

        // Completed level stays 3 in the snapshot projection.
        assertEquals(3, service.snapshot().entities().stream()
                .filter(e -> "Smasher".equals(e.name())).findFirst().orElseThrow().currentLevel());

        UpgradeResponse upgrades = service.upgrades();
        UpgradeResponse.UpgradeRow active = upgrades.upgrades().stream()
                .filter(row -> row.fromLevel() == 3 && row.toLevel() == 4)
                .findFirst().orElseThrow();
        // Observed balance is 2 days = 172800 s, preserved exactly (no extra Gold Pass discount).
        assertEquals(172_800L, active.observedRemainingSecondsTotal());
        assertEquals(1, upgrades.inProgressRows());
    }

    @Test
    void unknownDurationIsSurfacedAsUnknownNotZero() throws Exception {
        String snapshot = snapshot(item("Defesas", "Torre do Mago", 1, 15, 17, 0, 0, 0, 0, 0, ""));
        String catalog = "["
                + permanentStep("Defesas", "Torre do Mago", 15, 16, 1, "4.475", false, "null") + ","
                + permanentStep("Defesas", "Torre do Mago", 16, 17, 1, "null", false, "null") + "]";
        VillageDashboardService service = serviceFor(snapshot, catalog, "0.20");

        DashboardResponse dashboard = service.dashboard();
        assertFalse(dashboard.durations().exactTotalAvailable(), "unknown duration blocks an exact total");
        assertTrue(dashboard.durations().unknownStepKeyCount() >= 1);

        UpgradeResponse.UpgradeRow unknownRow = service.upgrades().upgrades().stream()
                .filter(row -> row.fromLevel() == 16 && row.toLevel() == 17)
                .findFirst().orElseThrow();
        assertFalse(unknownRow.durationKnown(), "the 16->17 step has no known duration");
        assertNull(unknownRow.baseDurationDaysPerUnit(), "unknown duration is null, never zero");

        UpgradeResponse.UpgradeRow knownRow = service.upgrades().upgrades().stream()
                .filter(row -> row.fromLevel() == 15 && row.toLevel() == 16)
                .findFirst().orElseThrow();
        assertTrue(knownRow.durationKnown());
        assertNotNull(knownRow.baseDurationDaysPerUnit());
    }

    @Test
    void sixPermanentBuildersAndGoblinTemporaryAreReportedWithLowerBoundEstimate() throws Exception {
        // 12 identical builder jobs of 6 days each = 72 builder-days; lower bound over 6 builders = 12.
        String snapshot = snapshot(item("Defesas", "Canhão", 12, 1, 2, 0, 0, 0, 0, 0, ""));
        String catalog = "[" + permanentStep("Defesas", "Canhão", 1, 2, 12, "6", false, "null") + "]";
        DashboardResponse dashboard = serviceFor(snapshot, catalog, "0").dashboard();

        assertEquals(6, dashboard.builders().permanentBuilderCapacity());
        assertTrue(dashboard.builders().goblinBuilderTemporary());
        assertEquals(72.0, dashboard.builders().builderQueueDays(), 1e-6);
        // Lower bound only: 72 builder-days / 6 builders = 12 calendar days (not a confirmed date).
        assertEquals(12.0, dashboard.builders().estimatedBuilderCalendarDaysLowerBound(), 1e-6);
    }

    @Test
    void aggregatedCountAtMaxLimitsPendingPermanentUpgrades() throws Exception {
        // 4 Mortars, 2 already at max (count_at_max=2): only 2 remaining 17->18 permanent upgrades.
        String snapshot = snapshot(item("Defesas", "Morteiro", 4, 17, 18, 2, 2, 0, 0, 0,
                "2 no 18, 2 subindo"));
        String catalog = "[" + permanentStep("Defesas", "Morteiro", 17, 18, 2, "12.5", false, "null") + "]";
        UpgradeResponse upgrades = serviceFor(snapshot, catalog, "0.20").upgrades();

        UpgradeResponse.UpgradeRow mortar = upgrades.upgrades().stream()
                .filter(row -> "Morteiro".equals(row.item()) && "PERMANENT".equals(row.upgradeType()))
                .findFirst().orElseThrow();
        assertEquals(2, mortar.count(), "only the two below-max mortars are pending 17->18");
    }

    @Test
    void superchargeStepsRequireEligiblePermanentMaxAndStaySeparate() throws Exception {
        // Air Defense at permanent max with 2 supercharge stages; SC rows must appear and be typed.
        String snapshot = snapshot(item("Defesas", "Defesa Aérea", 4, 16, 16, 4, 2, 1, 0, 0,
                "uma SC1 concluída"));
        String catalog = "["
                + scStep("Defesa Aérea", 0, 1, 3, "2.75") + ","
                + scStep("Defesa Aérea", 1, 2, 4, "4.25") + "]";
        List<UpgradeResponse.UpgradeRow> rows = serviceFor(snapshot, catalog, "0.20").upgrades().upgrades();

        long scRows = rows.stream().filter(r -> "SUPERCHARGE".equals(r.upgradeType())).count();
        assertEquals(2, scRows, "both supercharge stages are planned for the eligible entity");
        assertTrue(rows.stream().allMatch(r -> "SUPERCHARGE".equals(r.upgradeType())));
    }

    private static String scStep(String item, int from, int to, int count, String baseDays) {
        return """
                {
                  "category": "Sobrecargas", "item": "%s", "from_level": %d, "to_level": %d,
                  "stage": "SC%d", "count": %d, "base_days": %s, "pass_days": null,
                  "in_progress": false, "remaining_now_days": null, "source": "synthetic",
                  "confidence": "confirmado"
                }
                """.formatted(item, from, to, to, count, baseDays);
    }
}
