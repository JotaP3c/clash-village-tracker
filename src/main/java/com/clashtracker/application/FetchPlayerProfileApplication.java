package com.clashtracker.application;

import com.clashtracker.integration.supercell.SupercellApiException;
import com.clashtracker.integration.supercell.SupercellPlayerClient;
import com.clashtracker.integration.supercell.SupercellPlayerObservation;
import com.clashtracker.integration.supercell.SupercellPlayerProfile;
import com.clashtracker.util.AtomicFiles;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class FetchPlayerProfileApplication {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);

    private final SupercellPlayerClient client;
    private final ObjectMapper mapper;
    private final Clock clock;

    public FetchPlayerProfileApplication(SupercellPlayerClient client, ObjectMapper mapper, Clock clock) {
        this.client = java.util.Objects.requireNonNull(client, "client");
        this.mapper = java.util.Objects.requireNonNull(mapper, "mapper");
        this.clock = java.util.Objects.requireNonNull(clock, "clock");
    }

    public FetchPlayerProfileResult fetch(String playerTag, Path outputDirectory)
            throws SupercellApiException, IOException {
        SupercellPlayerProfile profile = client.fetchPlayer(playerTag);
        Instant observedAt = clock.instant();
        SupercellPlayerObservation observation = new SupercellPlayerObservation(
                observedAt, "OFFICIAL_SUPERCELL_API:/v1/players/{playerTag}", profile);

        Path directory = outputDirectory.toAbsolutePath().normalize().resolve("supercell");
        Files.createDirectories(directory);
        String safeTag = profile.tag().replace("#", "").replaceAll("[^A-Za-z0-9_-]", "_");
        Path target = directory.resolve("player-" + safeTag + "-" + FILE_TIME.format(observedAt) + ".json");
        if (Files.exists(target)) {
            target = directory.resolve("player-" + safeTag + "-" + FILE_TIME.format(observedAt)
                    + "-" + UUID.randomUUID() + ".json");
        }
        Path temporary = AtomicFiles.temporarySibling(target);
        mapper.writeValue(temporary.toFile(), observation);
        AtomicFiles.replace(temporary, target);
        return new FetchPlayerProfileResult(observation, target);
    }
}
