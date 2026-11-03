package com.clashtracker.web.service;

import com.clashtracker.application.VillageAnalysis;
import com.clashtracker.importer.InputDataException;
import com.clashtracker.web.persistence.PersistedSnapshotSummary;
import com.clashtracker.web.persistence.SnapshotRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Imports the configured historical snapshot into SQL Server and exposes the stored history. It
 * reuses the normalized snapshot produced by the offline pipeline, so no parsing or business rule
 * is duplicated. All methods require persistence to be enabled; otherwise they report it clearly.
 */
@Service
public class SnapshotImportService {

    private final VillageDashboardService dashboardService;
    private final Optional<SnapshotRepository> snapshotRepository;

    public SnapshotImportService(VillageDashboardService dashboardService,
                                 Optional<SnapshotRepository> snapshotRepository) {
        this.dashboardService = dashboardService;
        this.snapshotRepository = snapshotRepository;
    }

    public boolean persistenceEnabled() {
        return snapshotRepository.isPresent();
    }

    /** Imports the configured snapshot; returns the stored id (idempotent on re-import). */
    public long importConfiguredSnapshot() throws InputDataException {
        SnapshotRepository repository = requireRepository();
        VillageAnalysis analysis = dashboardService.analyze();
        if (analysis.failed() || analysis.snapshot() == null) {
            throw new InputDataException("Snapshot analysis failed; nothing was imported: "
                    + analysis.message());
        }
        return repository.importSnapshot(analysis.snapshot());
    }

    public List<PersistedSnapshotSummary> listSnapshots(String playerTag) {
        return requireRepository().listSnapshots(playerTag);
    }

    public int countSnapshots() {
        return requireRepository().countSnapshots();
    }

    private SnapshotRepository requireRepository() {
        return snapshotRepository.orElseThrow(() -> new IllegalStateException(
                "Persistence is disabled. Set tracker.db.enabled=true to use the database."));
    }
}
