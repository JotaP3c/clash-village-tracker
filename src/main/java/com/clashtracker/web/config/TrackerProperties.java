package com.clashtracker.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * External configuration for the read-only API. Defaults point at the repository's historical
 * snapshot and catalog so the application runs end-to-end out of the box, offline, without a
 * database. These are historical files; nothing here is treated as live data.
 */
@ConfigurationProperties(prefix = "tracker")
public class TrackerProperties {

    /** Path to the historical village snapshot JSON (default: the 2026-10-19 reference snapshot). */
    private String snapshotPath = "data/snapshot_2026-10-19.json";

    /** Path to the upgrade-steps catalog JSON. */
    private String catalogPath = "data/upgrade_steps.json";

    /** Optional previous snapshot for diffing; null/blank disables the diff. */
    private String previousSnapshotPath;

    /** Gold Pass time reduction applied to eligible future steps (0..1). */
    private BigDecimal goldPassBoost = new BigDecimal("0.20");

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public void setSnapshotPath(String snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public String getCatalogPath() {
        return catalogPath;
    }

    public void setCatalogPath(String catalogPath) {
        this.catalogPath = catalogPath;
    }

    public String getPreviousSnapshotPath() {
        return previousSnapshotPath;
    }

    public void setPreviousSnapshotPath(String previousSnapshotPath) {
        this.previousSnapshotPath = previousSnapshotPath;
    }

    public BigDecimal getGoldPassBoost() {
        return goldPassBoost;
    }

    public void setGoldPassBoost(BigDecimal goldPassBoost) {
        this.goldPassBoost = goldPassBoost;
    }
}
