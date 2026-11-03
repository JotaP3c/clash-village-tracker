package com.clashtracker.web.service;

import com.clashtracker.application.ReportApplication;
import com.clashtracker.util.JsonSupport;
import com.clashtracker.web.config.TrackerProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * When persistence is disabled (no SQL Server configured), the import service must report it
 * clearly and must not pretend to have stored anything.
 */
class SnapshotImportServiceTest {

    @Test
    void persistenceDisabledIsReportedAndImportFails() {
        VillageDashboardService dashboard = new VillageDashboardService(
                new ReportApplication(JsonSupport.createMapper()), new TrackerProperties());
        SnapshotImportService service = new SnapshotImportService(dashboard, Optional.empty());

        assertFalse(service.persistenceEnabled());
        assertThrows(IllegalStateException.class, service::importConfiguredSnapshot);
        assertThrows(IllegalStateException.class, service::countSnapshots);
    }
}
