package com.clashtracker.catalog;

import com.clashtracker.importer.InputDataException;
import com.clashtracker.importer.raw.RawUpgradeStep;
import com.clashtracker.util.Hashing;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class CatalogImporter {
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "category", "item", "from_level", "to_level", "stage", "count",
            "base_days", "pass_days", "in_progress", "remaining_now_days", "source", "confidence");
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "(?i)(api[_-]?token|authorization\\s*[:=]|bearer\\s+[a-z0-9._-]{8,})");

    private final ObjectMapper mapper;

    public CatalogImporter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public LoadedCatalog load(Path path) throws InputDataException {
        requireReadable(path);
        try {
            byte[] bytes = Files.readAllBytes(path);
            String text = new String(bytes, StandardCharsets.UTF_8);
            if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
                text = text.substring(1);
            }
            String lowerName = path.getFileName().toString().toLowerCase(Locale.ROOT);
            List<RawUpgradeStep> rows;
            String format;
            if (lowerName.endsWith(".json")) {
                rows = mapper.readValue(bytes, new TypeReference<>() { });
                format = "JSON";
            } else if (lowerName.endsWith(".csv")) {
                rows = parseCsv(text, path);
                format = "CSV";
            } else {
                throw new InputDataException("Unsupported catalog format for %s; use .json or .csv".formatted(path));
            }
            return new LoadedCatalog(rows, path.toAbsolutePath().normalize(), format,
                    Hashing.sha256(bytes), TOKEN_PATTERN.matcher(text).find());
        } catch (JsonProcessingException exception) {
            throw new InputDataException("Malformed catalog JSON at %s: %s"
                    .formatted(path, exception.getOriginalMessage()), exception);
        } catch (IOException exception) {
            throw new InputDataException("Cannot read catalog file %s: %s"
                    .formatted(path, exception.getMessage()), exception);
        }
    }

    private static List<RawUpgradeStep> parseCsv(String text, Path path) throws InputDataException {
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(false)
                .build();
        try (CSVParser parser = new CSVParser(new StringReader(text), format)) {
            if (!parser.getHeaderMap().keySet().containsAll(REQUIRED_HEADERS)) {
                throw new InputDataException("Catalog CSV %s is missing required headers; found %s"
                        .formatted(path, parser.getHeaderMap().keySet()));
            }
            List<RawUpgradeStep> rows = new ArrayList<>();
            for (CSVRecord row : parser) {
                try {
                    rows.add(new RawUpgradeStep(
                            text(row, "category"), text(row, "item"), integer(row, "from_level"),
                            integer(row, "to_level"), text(row, "stage"), integer(row, "count"),
                            decimal(row, "base_days"), decimal(row, "pass_days"),
                            bool(row, "in_progress"), decimal(row, "remaining_now_days"),
                            text(row, "source"), text(row, "confidence")));
                } catch (RuntimeException exception) {
                    throw new InputDataException("Invalid catalog CSV value at record %d in %s: %s"
                            .formatted(row.getRecordNumber(), path, exception.getMessage()), exception);
                }
            }
            return rows;
        } catch (IOException exception) {
            throw new InputDataException("Cannot parse catalog CSV %s: %s".formatted(path, exception.getMessage()), exception);
        }
    }

    private static String text(CSVRecord row, String name) {
        String value = row.get(name);
        return value == null ? null : value;
    }

    private static Integer integer(CSVRecord row, String name) {
        String value = row.get(name);
        return value == null || value.isBlank() ? null : Integer.valueOf(value.trim());
    }

    private static BigDecimal decimal(CSVRecord row, String name) {
        String value = row.get(name);
        return value == null || value.isBlank() ? null : new BigDecimal(value.trim());
    }

    private static Boolean bool(CSVRecord row, String name) {
        String value = row.get(name);
        return value == null || value.isBlank() ? null : Boolean.valueOf(value.trim());
    }

    private static void requireReadable(Path path) throws InputDataException {
        if (path == null || !Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw new InputDataException("O arquivo de catálogo não existe ou não pode ser lido: "
                    + (path == null ? "<missing>" : path.toAbsolutePath()));
        }
    }
}
