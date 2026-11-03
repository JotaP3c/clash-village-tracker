package com.clashtracker.web.controller;

import com.clashtracker.importer.InputDataException;
import com.clashtracker.web.persistence.PersistedSnapshotSummary;
import com.clashtracker.web.service.SnapshotImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Database-backed history endpoints (E2). When persistence is disabled these endpoints report it
 * with HTTP 503 rather than failing obscurely, so the offline dashboard keeps working.
 */
@RestController
@RequestMapping("/api/db")
public class PersistenceController {

    private final SnapshotImportService importService;

    public PersistenceController(SnapshotImportService importService) {
        this.importService = importService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        boolean enabled = importService.persistenceEnabled();
        return Map.of(
                "persistenceEnabled", enabled,
                "snapshotCount", enabled ? importService.countSnapshots() : 0);
    }

    @PostMapping("/import-snapshot")
    public ResponseEntity<Map<String, Object>> importSnapshot() throws InputDataException {
        if (!importService.persistenceEnabled()) {
            return disabled();
        }
        long id = importService.importConfiguredSnapshot();
        return ResponseEntity.ok(Map.of("importedSnapshotId", id));
    }

    @GetMapping("/snapshots")
    public ResponseEntity<?> snapshots(
            @RequestParam(name = "playerTag", defaultValue = "#U8J89QP2") String playerTag) {
        if (!importService.persistenceEnabled()) {
            return disabled();
        }
        List<PersistedSnapshotSummary> snapshots = importService.listSnapshots(playerTag);
        return ResponseEntity.ok(snapshots);
    }

    private static ResponseEntity<Map<String, Object>> disabled() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of(
                "error", "PERSISTENCE_DISABLED",
                "message", "Set tracker.db.enabled=true and configure the SQL Server connection."));
    }
}
