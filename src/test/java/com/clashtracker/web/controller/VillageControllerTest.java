package com.clashtracker.web.controller;

import com.clashtracker.application.ReportApplication;
import com.clashtracker.importer.InputDataException;
import com.clashtracker.util.JsonSupport;
import com.clashtracker.web.config.TrackerProperties;
import com.clashtracker.web.dto.DashboardResponse;
import com.clashtracker.web.service.OfficialProfileService;
import com.clashtracker.web.service.VillageDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contract tests for the read-only web controllers. The controllers are instantiated directly (no
 * Spring context is required) to verify they delegate to the service and that input failures are
 * translated into a 422 response without leaking a stack trace.
 */
class VillageControllerTest {

    private static VillageController controllerWithRealData() {
        TrackerProperties properties = propertiesForRealData();
        VillageDashboardService service =
                new VillageDashboardService(new ReportApplication(JsonSupport.createMapper()), properties);
        OfficialProfileService officialProfileService = new OfficialProfileService(
                properties, JsonSupport.createMapper(), java.util.Optional.empty());
        return new VillageController(service, officialProfileService);
    }

    private static TrackerProperties propertiesForRealData() {
        Path moduleRoot = locateModuleRoot();
        TrackerProperties properties = new TrackerProperties();
        properties.setSnapshotPath(moduleRoot.resolve("data/snapshot_2026-10-19.json").toString());
        properties.setCatalogPath(moduleRoot.resolve("data/upgrade_steps.json").toString());
        return properties;
    }

    @Test
    void healthEndpointReportsOfflineHistoricalMode() {
        Map<String, Object> health = new HealthController().health();

        assertEquals("UP", health.get("status"));
        assertEquals("clash-village-tracker", health.get("application"));
        assertEquals("offline-historical", health.get("mode"));
        assertNotNull(health.get("time"));
    }

    @Test
    void dashboardEndpointReturnsTheHistoricalVillage() throws Exception {
        DashboardResponse dashboard = controllerWithRealData().dashboard();

        assertEquals("#U8J89QP2", dashboard.village().playerTag());
        assertEquals(18, dashboard.village().townHallLevel());
        assertFalse(dashboard.progress().fullCertified());
    }

    @Test
    void villageEndpointsAllRespondForTheHistoricalDataset() throws Exception {
        VillageController controller = controllerWithRealData();

        assertNotNull(controller.dashboard());
        assertNotNull(controller.snapshot());
        assertFalse(controller.categories().categories().isEmpty());
        assertTrue(controller.upgrades().totalRows() > 0);
    }

    @Test
    void missingSnapshotFileProducesInputDataErrorMappedTo422() throws Exception {
        TrackerProperties properties = new TrackerProperties();
        Path missing = Files.createTempDirectory("cvt-missing").resolve("does-not-exist.json");
        properties.setSnapshotPath(missing.toString());
        properties.setCatalogPath(missing.toString());
        VillageController controller = new VillageController(
                new VillageDashboardService(new ReportApplication(JsonSupport.createMapper()), properties),
                new OfficialProfileService(properties, JsonSupport.createMapper(), java.util.Optional.empty()));

        // The controller surfaces the checked InputDataException...
        InputDataException thrown = assertThrows(InputDataException.class, controller::dashboard);

        // ...and the advice maps it to a clean 422 body without a stack trace.
        ResponseEntity<Map<String, Object>> response = new ApiExceptionHandler().handleInputData(thrown);
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("INPUT_DATA_ERROR", response.getBody().get("error"));
    }

    private static Path locateModuleRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++) {
            if (Files.exists(current.resolve("data/snapshot_2026-10-19.json"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of("").toAbsolutePath();
    }
}
