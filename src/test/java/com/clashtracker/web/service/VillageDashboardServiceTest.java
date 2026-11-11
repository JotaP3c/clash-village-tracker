package com.clashtracker.web.service;

import com.clashtracker.application.ReportApplication;
import com.clashtracker.util.JsonSupport;
import com.clashtracker.web.config.TrackerProperties;
import com.clashtracker.web.dto.CategoryResponse;
import com.clashtracker.web.dto.DashboardResponse;
import com.clashtracker.web.dto.SnapshotResponse;
import com.clashtracker.web.dto.UpgradeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies that the read-only web service maps the offline analysis into DTOs correctly, using the
 * repository's real historical snapshot and catalog. These assertions check business meaning
 * (village identity, progress bounds, supercharge presence), not just getters.
 */
class VillageDashboardServiceTest {

    private VillageDashboardService service;

    @BeforeEach
    void setUp() {
        // Resolve data files relative to the module root regardless of the test working directory.
        Path moduleRoot = locateModuleRoot();
        TrackerProperties properties = new TrackerProperties();
        properties.setSnapshotPath(moduleRoot.resolve("data/snapshot_2026-10-19.json").toString());
        properties.setCatalogPath(moduleRoot.resolve("data/upgrade_steps.json").toString());
        ReportApplication application = new ReportApplication(JsonSupport.createMapper());
        service = new VillageDashboardService(application, properties);
    }

    @Test
    void dashboardReportsHistoricalVillageIdentity() throws Exception {
        DashboardResponse dashboard = service.dashboard();

        assertEquals("#U8J89QP2", dashboard.village().playerTag());
        assertEquals(18, dashboard.village().townHallLevel());
        assertEquals(6, dashboard.village().permanentBuilders());
        assertTrue(dashboard.village().historicalSnapshot());
        assertEquals("2026-10-19", dashboard.village().snapshotDate());
        assertEquals(0.20, dashboard.village().goldPassBoost(), 1e-9);
    }

    @Test
    void overallProgressStaysWithinBoundsAndIsNotCertifiedWithUnknowns() throws Exception {
        DashboardResponse dashboard = service.dashboard();

        double overall = dashboard.progress().overallProgressPercent();
        assertTrue(overall > 0.0 && overall <= 100.0, "progress must be a percentage");
        // The legacy catalog is incomplete, so a confirmed fully-maxed state must NOT be claimed.
        assertFalse(dashboard.progress().fullCertified());
        assertTrue(dashboard.progress().overallCompletedSteps() <= dashboard.progress().overallTotalSteps());
    }

    @Test
    void superchargesAreExposedSeparatelyFromPermanentUpgrades() throws Exception {
        UpgradeResponse upgrades = service.upgrades();

        long superchargeRows = upgrades.upgrades().stream()
                .filter(row -> "SUPERCHARGE".equals(row.upgradeType()))
                .count();
        long permanentRows = upgrades.upgrades().stream()
                .filter(row -> "PERMANENT".equals(row.upgradeType()))
                .count();
        assertTrue(superchargeRows > 0, "the catalog has pending supercharge steps");
        assertTrue(permanentRows > 0, "the catalog has pending permanent steps");
    }

    @Test
    void categoriesCarryTheirEntities() throws Exception {
        CategoryResponse categories = service.categories();

        assertFalse(categories.categories().isEmpty());
        boolean anyWithEntities = categories.categories().stream()
                .anyMatch(category -> !category.entities().isEmpty());
        assertTrue(anyWithEntities, "category detail must include its entities");
    }

    @Test
    void snapshotKeepsCompletedLevelDistinctFromTargetForSmasher() throws Exception {
        SnapshotResponse snapshot = service.snapshot();

        // The Smasher guardian is upgrading 3 -> 4 in the historical snapshot; its completed level
        // must remain 3 (an in-progress upgrade never advances the completed level).
        SnapshotResponse.EntityView smasher = snapshot.entities().stream()
                .filter(entity -> "Smasher".equalsIgnoreCase(entity.name()))
                .findFirst()
                .orElse(null);
        assertNotNull(smasher, "the snapshot includes the Smasher guardian");
        assertEquals(3, smasher.currentLevel());
        assertEquals(5, smasher.maxLevel());
    }

    private static Path locateModuleRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++) {
            if (Files.exists(current.resolve("data/snapshot_2026-10-19.json"))) {
                return current;
            }
            current = current.getParent();
        }
        // Fall back to the working directory; the test will surface a clear failure if data is absent.
        return Path.of("").toAbsolutePath();
    }
}
