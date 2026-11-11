package com.clashtracker.application;

import com.clashtracker.integration.supercell.SupercellApiException;
import com.clashtracker.integration.supercell.SupercellPlayerCapture;
import com.clashtracker.integration.supercell.SupercellPlayerClient;
import com.clashtracker.integration.supercell.SupercellPlayerObservation;
import com.clashtracker.util.AtomicFiles;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class FetchPlayerProfileApplication {
    private static final DateTimeFormatter FILE_TIME = DateTimeFormatter
            .ofPattern("yyyyMMdd'T'HHmmssSSS'Z'")
            .withZone(ZoneOffset.UTC);
    private static final String SOURCE = "OFFICIAL_SUPERCELL_API";
    private static final String SCHEMA_VERSION = "supercell-player-capture-v1";

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
        SupercellPlayerCapture capture = client.capturePlayer(playerTag);
        CollectionDirectory collection = createCollectionDirectory(outputDirectory, capture);
        Path collectionDirectory = collection.path();
        Path responseFile = collectionDirectory.resolve("response.bin");
        Path profileFile = capture.profile() == null ? null : collectionDirectory.resolve("player-profile.json");
        Path metadataFile = collectionDirectory.resolve("metadata.json");

        try {
            Files.write(responseFile, capture.body(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            SupercellPlayerObservation observation = null;
            if (capture.profile() != null) {
                observation = new SupercellPlayerObservation(capture.receivedAt(),
                        SOURCE + ":/v1/players/{playerTag}", capture.profile());
                writeJsonAtomically(profileFile, observation);
            }

            SupercellApiException failure = capture.failure();
            SupercellCaptureMetadata metadata = new SupercellCaptureMetadata(
                    SCHEMA_VERSION,
                    collection.id(),
                    capture.requestedPlayerTag(), capture.returnedPlayerTag(), SOURCE,
                    capture.endpoint().toString(), capture.httpStatus(), capture.receivedAt(), clock.instant(),
                    capture.contentType(), capture.charset(), capture.contentEncoding(),
                    capture.body().length, capture.bodySha256(), responseFile.getFileName().toString(),
                    profileFile == null ? null : profileFile.getFileName().toString(),
                    failure == null ? "PROFILE_PARSED" : "RESPONSE_REJECTED",
                    failure == null ? null : failure.failure().name(),
                    failure == null ? null : failure.getMessage());
            writeJsonAtomically(metadataFile, metadata); // Written last; its presence seals the collection.

            if (failure != null) {
                throw failure.withEvidenceDirectory(collectionDirectory);
            }
            return new FetchPlayerProfileResult(observation, profileFile, collectionDirectory,
                    responseFile, metadataFile, capture.bodySha256());
        } catch (SupercellApiException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new IOException("Não foi possível persistir a evidência da coleta em "
                    + collectionDirectory.toAbsolutePath().normalize(), exception);
        }
    }

    private CollectionDirectory createCollectionDirectory(Path outputDirectory, SupercellPlayerCapture capture)
            throws IOException {
        Path root = outputDirectory.toAbsolutePath().normalize().resolve("supercell").resolve("collections");
        Files.createDirectories(root);
        String safeTag = capture.requestedPlayerTag().replace("#", "")
                .replaceAll("[^A-Za-z0-9_-]", "_");
        for (int attempt = 0; attempt < 10; attempt++) {
            UUID id = UUID.randomUUID();
            Path candidate = root.resolve("player-" + safeTag + "-"
                    + FILE_TIME.format(capture.receivedAt()) + "-" + id);
            try {
                return new CollectionDirectory(id, Files.createDirectory(candidate));
            } catch (java.nio.file.FileAlreadyExistsException ignored) {
                // A fresh UUID is used for every attempt; existing collection data is never replaced.
            }
        }
        throw new IOException("Não foi possível reservar um identificador único para a coleta");
    }

    private void writeJsonAtomically(Path target, Object value) throws IOException {
        Path temporary = AtomicFiles.temporarySibling(target);
        try {
            mapper.writeValue(temporary.toFile(), value);
            AtomicFiles.replace(temporary, target);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private record CollectionDirectory(UUID id, Path path) {
    }
}
