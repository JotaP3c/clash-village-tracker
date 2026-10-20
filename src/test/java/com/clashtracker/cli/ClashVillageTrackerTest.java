package com.clashtracker.cli;

import com.clashtracker.util.Hashing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClashVillageTrackerTest {
    @TempDir
    Path temp;
    private ByteArrayOutputStream stdout;
    private ByteArrayOutputStream stderr;

    @BeforeEach
    void setUp() {
        stdout = new ByteArrayOutputStream();
        stderr = new ByteArrayOutputStream();
    }

    @Test
    void missingAndMalformedArgumentsReturnNonZeroWithHelp() {
        assertEquals(2, run());
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Uso:"));

        reset();
        assertEquals(2, run("report", "--snapshot", "x", "--catalog", "y", "--output", "z",
                "--gold-pass-boost", "not-a-number"));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("exige número decimal"));
    }

    @Test
    void missingFileAndMalformedJsonReturnNonZeroClearErrors() throws Exception {
        assertEquals(2, run("validate-catalog", "--catalog", temp.resolve("missing.json").toString()));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("não existe"));

        reset();
        Path malformed = temp.resolve("bad.json");
        Files.writeString(malformed, "{bad");
        assertEquals(2, run("validate-catalog", "--catalog", malformed.toString()));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("Malformed catalog JSON"));
    }

    @Test
    void structuralCatalogErrorReturnsValidationExitCode() throws Exception {
        Path catalog = temp.resolve("invalid.json");
        Files.writeString(catalog, """
                [{"category":"Defesas","item":"Canhão","from_level":1,"to_level":3,"stage":"Nível permanente","count":1,"base_days":1,"pass_days":0.8,"in_progress":false,"remaining_now_days":null,"source":"synthetic","confidence":"test"}]
                """);
        assertEquals(3, run("validate-catalog", "--catalog", catalog.toString()));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("STATUS: FAILED"));
    }

    @Test
    void offlineReportGeneratesSemanticArtifactsAndIsIdempotent() throws Exception {
        Path snapshot = writeSnapshot(temp.resolve("snapshot.json"), 1);
        Path catalog = writeCatalog(temp.resolve("catalog.json"));
        Path output = temp.resolve("output");
        String beforeSnapshot = Hashing.sha256(Files.readAllBytes(snapshot));
        String beforeCatalog = Hashing.sha256(Files.readAllBytes(catalog));
        String[] args = {"report", "--snapshot", snapshot.toString(), "--catalog", catalog.toString(),
                "--gold-pass-boost", "0.20", "--output", output.toString(), "--offline"};

        assertEquals(0, run(args));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("STATUS: PARTIAL_SUCCESS"));
        assertTrue(Files.isRegularFile(output.resolve("report.xlsx")));
        assertTrue(Files.isRegularFile(output.resolve("normalized-snapshot.json")));
        assertTrue(Files.isRegularFile(output.resolve("pending-validation.json")));
        assertTrue(Files.isRegularFile(output.resolve("report-summary.json")));
        assertTrue(Files.isRegularFile(output.resolve("execution-summary.log")));
        assertFalse(Files.exists(output.resolve("diff.json")));
        assertEquals(1, Files.list(output.resolve("history")).count());

        reset();
        assertEquals(0, run(args));
        assertEquals(1, Files.list(output.resolve("history")).count());
        assertEquals(beforeSnapshot, Hashing.sha256(Files.readAllBytes(snapshot)));
        assertEquals(beforeCatalog, Hashing.sha256(Files.readAllBytes(catalog)));
    }

    @Test
    void diffIsCreatedOnlyWhenPreviousSnapshotIsProvided() throws Exception {
        Path previous = writeSnapshot(temp.resolve("previous.json"), 0);
        Path current = writeSnapshot(temp.resolve("current.json"), 1);
        Path catalog = writeCatalog(temp.resolve("catalog.json"));
        Path output = temp.resolve("with-diff");

        assertEquals(0, run("report", "--snapshot", current.toString(), "--catalog", catalog.toString(),
                "--output", output.toString(), "--previous-snapshot", previous.toString()));
        assertTrue(Files.isRegularFile(output.resolve("diff.json")));
        String diff = Files.readString(output.resolve("diff.json"));
        assertTrue(diff.contains("currentLevel"));
        assertTrue(diff.contains("CHANGE_CONFIRMED"));
    }

    @Test
    void fetchPlayerRequiresTokenAndHelpDoesNotCallTheNetwork() {
        assertEquals(0, runWithEnvironment(Map.of(), "fetch-player", "--help"));
        assertTrue(stdout.toString(StandardCharsets.UTF_8).contains("CLASH_API_TOKEN"));

        reset();
        assertEquals(5, runWithEnvironment(Map.of(), "fetch-player", "--tag", "#TEST",
                "--output", temp.toString()));
        assertTrue(stderr.toString(StandardCharsets.UTF_8).contains("CONFIGURATION"));
        assertFalse(Files.exists(temp.resolve("supercell")));
    }

    private int run(String... args) {
        return ClashVillageTracker.run(args,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));
    }

    private int runWithEnvironment(Map<String, String> environment, String... args) {
        return ClashVillageTracker.run(args,
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8), environment);
    }

    private void reset() {
        stdout.reset();
        stderr.reset();
    }

    private static Path writeSnapshot(Path path, int level) throws Exception {
        Files.writeString(path, """
                {
                  "date":"2026-10-19","player_tag":"#TEST","player_name":"Fixture","town_hall":18,
                  "builders_permanent":6,"builder_goblin_temporary":true,"gold_pass_time_reduction":0.2,
                  "categories":["Defesas"],
                  "items":[{"category":"Defesas","name":"Canhão","quantity":1,"current_level":%d,"max_level":2,"count_at_max":0,"max_supercharge":0,"sc1_completed":0,"sc2_completed":0,"sc3_completed":0,"confidence":"test","notes":"synthetic","source":"synthetic"}],
                  "queue":[],"note":"synthetic"
                }
                """.formatted(level));
        return path;
    }

    private static Path writeCatalog(Path path) throws Exception {
        Files.writeString(path, """
                [{"category":"Defesas","item":"Canhão","from_level":1,"to_level":2,"stage":"Nível permanente","count":1,"base_days":1,"pass_days":0.8,"in_progress":false,"remaining_now_days":null,"source":"synthetic","confidence":"confirmed"}]
                """);
        return path;
    }
}
