package com.clashtracker.domain;

public record CatalogStepKey(
        String catalogVersion,
        String entityKey,
        UpgradeType upgradeType,
        int fromLevel,
        int toLevel) {

    public String logicalId() {
        return catalogVersion + "|" + entityKey + "|" + upgradeType + "|" + fromLevel + "|" + toLevel;
    }
}
