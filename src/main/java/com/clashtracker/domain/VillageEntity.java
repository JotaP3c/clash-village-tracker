package com.clashtracker.domain;

public record VillageEntity(
        String entityKey,
        String rawName,
        String displayNamePtBr,
        String rawCategory,
        String categoryKey,
        int quantity,
        Integer currentLevel,
        Integer maxLevel,
        int countAtMax,
        Integer maxSupercharge,
        Integer sc1Completed,
        Integer sc2Completed,
        Integer sc3Completed,
        Representation representation,
        String source,
        String confidence,
        String notes,
        SourceInfo provenance) {

    public VillageEntity {
        source = source == null ? "" : source;
        confidence = confidence == null ? "unknown" : confidence;
        notes = notes == null ? "" : notes;
    }

    /**
     * Stable key inside this application. It is derived from legacy category/name data and is not
     * an official Supercell identifier.
     */
    public String identityKey() {
        return categoryKey + "|" + entityKey;
    }

    public VillageEntity withLevels(Integer mergedCurrentLevel, Integer mergedMaxLevel) {
        return new VillageEntity(entityKey, rawName, displayNamePtBr, rawCategory, categoryKey,
                quantity, mergedCurrentLevel, mergedMaxLevel, countAtMax, maxSupercharge,
                sc1Completed, sc2Completed, sc3Completed, representation, source, confidence,
                notes, provenance);
    }
}
