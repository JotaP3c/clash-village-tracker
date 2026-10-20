package com.clashtracker.importer;

import com.clashtracker.importer.raw.RawVillageSnapshot;

import java.nio.file.Path;

public record LoadedSnapshot(RawVillageSnapshot raw, Path sourcePath, String sourceSha256) {
}
