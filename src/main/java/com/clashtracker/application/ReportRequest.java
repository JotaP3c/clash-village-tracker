package com.clashtracker.application;

import java.math.BigDecimal;
import java.nio.file.Path;

public record ReportRequest(
        Path snapshotPath,
        Path catalogPath,
        Path previousSnapshotPath,
        BigDecimal goldPassBoost,
        Path outputDirectory,
        boolean offline) {
}
