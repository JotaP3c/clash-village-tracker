package com.clashtracker.importer;

import com.clashtracker.importer.raw.RawVillageSnapshot;
import com.clashtracker.util.Hashing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class SnapshotImporter {
    private final ObjectMapper mapper;

    public SnapshotImporter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public LoadedSnapshot load(Path path) throws InputDataException {
        requireReadableFile(path, "snapshot");
        try {
            byte[] bytes = Files.readAllBytes(path);
            RawVillageSnapshot raw = mapper.readValue(bytes, RawVillageSnapshot.class);
            return new LoadedSnapshot(raw, path.toAbsolutePath().normalize(), Hashing.sha256(bytes));
        } catch (JsonProcessingException exception) {
            throw new InputDataException("Malformed snapshot JSON at %s: %s"
                    .formatted(path, exception.getOriginalMessage()), exception);
        } catch (IOException exception) {
            throw new InputDataException("Cannot read snapshot file %s: %s"
                    .formatted(path, exception.getMessage()), exception);
        }
    }

    private static void requireReadableFile(Path path, String label) throws InputDataException {
        if (path == null) {
            throw new InputDataException("Missing " + label + " path");
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new InputDataException("O arquivo de %s não existe ou não pode ser lido: %s"
                    .formatted(label, path.toAbsolutePath()));
        }
    }
}
