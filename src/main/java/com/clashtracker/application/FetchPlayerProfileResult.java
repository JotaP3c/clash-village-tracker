package com.clashtracker.application;

import com.clashtracker.integration.supercell.SupercellPlayerObservation;

import java.nio.file.Path;

public record FetchPlayerProfileResult(
        SupercellPlayerObservation observation,
        Path artifact) {
}
