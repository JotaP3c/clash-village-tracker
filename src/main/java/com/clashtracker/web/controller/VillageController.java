package com.clashtracker.web.controller;

import com.clashtracker.importer.InputDataException;
import com.clashtracker.web.dto.CategoryResponse;
import com.clashtracker.web.dto.DashboardResponse;
import com.clashtracker.web.dto.SnapshotResponse;
import com.clashtracker.web.dto.UpgradeResponse;
import com.clashtracker.web.service.VillageDashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only endpoints that project the historical village analysis for the dashboard. All data is
 * computed offline from the versioned snapshot and catalog; no live game call is made here.
 */
@RestController
@RequestMapping("/api/village")
public class VillageController {

    private final VillageDashboardService service;

    public VillageController(VillageDashboardService service) {
        this.service = service;
    }

    @GetMapping("/dashboard")
    public DashboardResponse dashboard() throws InputDataException {
        return service.dashboard();
    }

    @GetMapping("/snapshot")
    public SnapshotResponse snapshot() throws InputDataException {
        return service.snapshot();
    }

    @GetMapping("/categories")
    public CategoryResponse categories() throws InputDataException {
        return service.categories();
    }

    @GetMapping("/upgrades")
    public UpgradeResponse upgrades() throws InputDataException {
        return service.upgrades();
    }
}
