package com.clashtracker.integration.supercell;

import java.time.Instant;

public record SupercellPlayerObservation(
        Instant observedAt,
        String source,
        SupercellPlayerProfile player) {
}
