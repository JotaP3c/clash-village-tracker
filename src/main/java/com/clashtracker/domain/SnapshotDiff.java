package com.clashtracker.domain;

import java.time.LocalDate;
import java.util.List;

public record SnapshotDiff(
        String previousFingerprint,
        String currentFingerprint,
        LocalDate previousObservedAt,
        LocalDate currentObservedAt,
        List<DiffEntry> changes) {

    public SnapshotDiff {
        changes = List.copyOf(changes);
    }
}
