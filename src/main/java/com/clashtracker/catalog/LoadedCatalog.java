package com.clashtracker.catalog;

import com.clashtracker.importer.raw.RawUpgradeStep;

import java.nio.file.Path;
import java.util.List;

public record LoadedCatalog(
        List<RawUpgradeStep> rows,
        Path sourcePath,
        String sourceFormat,
        String sourceSha256,
        boolean containsTokenMaterial) {

    public LoadedCatalog {
        rows = List.copyOf(rows);
    }
}
