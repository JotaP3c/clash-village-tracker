package com.clashtracker.web.service;

import com.clashtracker.application.SupercellCaptureMetadata;
import com.clashtracker.web.config.TrackerProperties;
import com.clashtracker.web.dto.OfficialProfileResponse;
import com.clashtracker.web.persistence.OfficialCollectionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Reads the most recent official-API collection written by the {@code fetch-player} command and
 * projects it for the dashboard. It never calls the Supercell API itself and never fabricates data:
 * if there is no collection, it returns an explicit "unavailable" response. When a SQL Server
 * connection is available, each read also registers the collection in an auditable index (the raw
 * bytes stay on disk).
 */
@Service
public class OfficialProfileService {

    private static final Logger LOGGER = LoggerFactory.getLogger(OfficialProfileService.class);

    private final TrackerProperties properties;
    private final ObjectMapper mapper;
    private final Optional<OfficialCollectionRepository> collectionRepository;

    public OfficialProfileService(TrackerProperties properties, ObjectMapper mapper,
                                  Optional<OfficialCollectionRepository> collectionRepository) {
        this.properties = properties;
        this.mapper = mapper;
        this.collectionRepository = collectionRepository;
    }

    public OfficialProfileResponse latest() {
        Path collectionsRoot = Path.of(properties.getCollectionsPath());
        if (!Files.isDirectory(collectionsRoot)) {
            return OfficialProfileResponse.unavailable(
                    "Nenhuma coleta encontrada. Rode 'fetch-player' com seu CLASH_API_TOKEN para gerar uma.");
        }

        Optional<Path> latest = latestCollectionDirectory(collectionsRoot);
        if (latest.isEmpty()) {
            return OfficialProfileResponse.unavailable(
                    "Nenhuma coleta válida em " + collectionsRoot + ".");
        }

        Path directory = latest.get();
        Path metadataFile = directory.resolve("metadata.json");
        if (!Files.isRegularFile(metadataFile)) {
            return OfficialProfileResponse.unavailable(
                    "A coleta mais recente não contém metadata.json (coleta incompleta).");
        }

        try {
            SupercellCaptureMetadata metadata =
                    mapper.readValue(metadataFile.toFile(), SupercellCaptureMetadata.class);
            OfficialProfileResponse.PlayerProfile profile = readProfile(directory);
            collectionRepository.ifPresent(repository -> safeRegister(repository, metadata, directory));
            return new OfficialProfileResponse(
                    true,
                    "Coleta oficial mais recente carregada. A API pública não traz construções, "
                            + "Sobrecargas, filas nem cronômetros.",
                    metadata.collectionId() == null ? null : metadata.collectionId().toString(),
                    metadata.requestedPlayerTag(),
                    metadata.returnedPlayerTag(),
                    metadata.httpStatus(),
                    metadata.outcome(),
                    metadata.receivedAt() == null ? null : metadata.receivedAt().toString(),
                    metadata.bodySha256(),
                    profile);
        } catch (IOException exception) {
            LOGGER.warn("Failed to read official collection metadata at {}", directory, exception);
            return OfficialProfileResponse.unavailable(
                    "Falha ao ler a coleta mais recente: " + exception.getMessage());
        }
    }

    private OfficialProfileResponse.PlayerProfile readProfile(Path directory) {
        Path profileFile = directory.resolve("player-profile.json");
        if (!Files.isRegularFile(profileFile)) {
            return null; // Rejected responses keep evidence without a valid profile.
        }
        try {
            JsonNode root = mapper.readTree(profileFile.toFile());
            JsonNode player = root.path("player");
            return new OfficialProfileResponse.PlayerProfile(
                    text(player, "tag"),
                    text(player, "name"),
                    integer(player, "townHallLevel"),
                    integer(player, "expLevel"),
                    arraySize(player, "troops"),
                    arraySize(player, "spells"),
                    arraySize(player, "heroes"),
                    arraySize(player, "heroEquipment"));
        } catch (IOException exception) {
            LOGGER.warn("Failed to read player profile at {}", profileFile, exception);
            return null;
        }
    }

    private void safeRegister(OfficialCollectionRepository repository,
                              SupercellCaptureMetadata metadata, Path directory) {
        try {
            Path profileFile = directory.resolve("player-profile.json");
            repository.registerIfAbsent(metadata,
                    Files.isRegularFile(profileFile) ? profileFile.toString() : null);
        } catch (RuntimeException exception) {
            // The database index is best-effort; the on-disk evidence remains the source of truth.
            LOGGER.warn("Could not register collection {} in the database index", metadata.collectionId(),
                    exception);
        }
    }

    private static Optional<Path> latestCollectionDirectory(Path root) {
        try (var stream = Files.list(root)) {
            List<Path> directories = stream.filter(Files::isDirectory).toList();
            return directories.stream().max(Comparator.comparing(path -> lastModified(path)));
        } catch (IOException exception) {
            LOGGER.warn("Failed to list collections at {}", root, exception);
            return Optional.empty();
        }
    }

    private static long lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException exception) {
            return 0L;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static Integer integer(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || !value.isNumber() ? null : value.asInt();
    }

    private static int arraySize(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isArray() ? value.size() : 0;
    }
}
