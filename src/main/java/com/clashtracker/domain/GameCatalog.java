package com.clashtracker.domain;

import java.util.List;

public record GameCatalog(
        String catalogVersion,
        boolean explicitlyVersioned,
        boolean scopeComplete,
        Integer townHallLevel,
        List<CatalogStep> steps,
        String sourcePath,
        String sourceFormat,
        String sourceSha256,
        boolean containsTokenMaterial) {

    public GameCatalog {
        steps = List.copyOf(steps);
    }
}
