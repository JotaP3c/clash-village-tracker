package com.clashtracker.importer;

import com.clashtracker.catalog.CatalogImporter;
import com.clashtracker.catalog.LoadedCatalog;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.Severity;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.normalization.CatalogNormalizer;
import com.clashtracker.normalization.LegacyKeyFactory;
import com.clashtracker.normalization.SnapshotNormalizer;
import com.clashtracker.util.JsonSupport;
import com.clashtracker.validation.SnapshotValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImporterTest {
    @TempDir
    Path temp;

    @Test
    void syntheticJsonAndCsvCatalogsNormalizeEquivalentlyAndPreserveNull() throws Exception {
        Path json = temp.resolve("steps.json");
        Path csv = temp.resolve("steps.csv");
        Files.writeString(json, """
                [
                  {"category":"Tropas","item":"Bebê Dragão","from_level":9,"to_level":10,"stage":"Nível permanente","count":1,"base_days":1.5,"pass_days":1.2,"in_progress":false,"remaining_now_days":null,"source":"synthetic","confidence":"confirmed"},
                  {"category":"Tropas","item":"Bebê Dragão","from_level":10,"to_level":11,"stage":"Nível permanente","count":1,"base_days":null,"pass_days":null,"in_progress":false,"remaining_now_days":null,"source":"synthetic","confidence":"pending"}
                ]
                """);
        Files.writeString(csv, """
                category,item,from_level,to_level,stage,count,base_days,pass_days,in_progress,remaining_now_days,source,confidence
                Tropas,Bebê Dragão,9,10,Nível permanente,1,1.5,1.2,False,,synthetic,confirmed
                Tropas,Bebê Dragão,10,11,Nível permanente,1,,,False,,synthetic,pending
                """);
        CatalogImporter importer = new CatalogImporter(JsonSupport.createMapper());
        GameCatalog fromJson = new CatalogNormalizer().normalize(importer.load(json));
        GameCatalog fromCsv = new CatalogNormalizer().normalize(importer.load(csv));

        assertEquals(fromJson.steps(), fromCsv.steps());
        assertEquals(129_600L, fromJson.steps().get(0).definition().baseDurationSeconds());
        assertNull(fromJson.steps().get(1).definition().baseDurationSeconds());
    }

    @Test
    void realJsonAndCsvCatalogsHaveEquivalent215LogicalRows() throws Exception {
        CatalogImporter importer = new CatalogImporter(JsonSupport.createMapper());
        GameCatalog json = new CatalogNormalizer().normalize(importer.load(Path.of("data/upgrade_steps.json")));
        GameCatalog csv = new CatalogNormalizer().normalize(importer.load(Path.of("data/upgrade_steps.csv")));

        assertEquals(215, json.steps().size());
        assertEquals(json.steps(), csv.steps());
        assertEquals(205, json.steps().stream().filter(step -> step.definition().baseDurationSeconds() != null).count());
        assertEquals(10, json.steps().stream().filter(step -> step.definition().baseDurationSeconds() == null).count());
        assertEquals(336, json.steps().stream().filter(step -> step.definition().baseDurationSeconds() != null)
                .mapToInt(step -> step.state().count()).sum());
        assertEquals(309, json.steps().stream().filter(step -> step.definition().baseDurationSeconds() == null)
                .mapToInt(step -> step.state().count()).sum());
        assertFalse(json.explicitlyVersioned());
        assertEquals("legacy-unversioned", json.catalogVersion());
    }

    @Test
    void realSnapshotImports123Items541Quantity26AggregatesAndSixQueueRows() throws Exception {
        SnapshotImporter importer = new SnapshotImporter(JsonSupport.createMapper());
        LoadedSnapshot loaded = importer.load(Path.of("data/snapshot_2026-10-19.json"));
        VillageSnapshot snapshot = new SnapshotNormalizer().normalize(loaded);

        assertEquals(123, snapshot.entities().size());
        assertEquals(541, snapshot.entities().stream().mapToInt(entity -> entity.quantity()).sum());
        assertEquals(26, snapshot.entities().stream().filter(entity -> entity.quantity() > 1).count());
        assertEquals(12, snapshot.declaredCategories().size());
        assertEquals(6, snapshot.activeUpgrades().size());
        assertEquals(6, snapshot.permanentBuilderCapacity());
        assertTrue(snapshot.goblinBuilderTemporary());
        assertTrue(new SnapshotValidator().validate(loaded.raw()).stream().noneMatch(issue -> issue.severity() == Severity.ERROR));
    }

    @Test
    void exactDuplicateSnapshotRowIsIdempotentlyDeduplicated() throws Exception {
        Path snapshotFile = temp.resolve("duplicate.json");
        String item = "{\"category\":\"Defesas\",\"name\":\"Canhão\",\"quantity\":1,\"current_level\":1,\"max_level\":2,\"count_at_max\":0,\"max_supercharge\":0,\"sc1_completed\":0,\"sc2_completed\":0,\"sc3_completed\":0,\"confidence\":\"test\",\"notes\":\"\",\"source\":\"synthetic\"}";
        Files.writeString(snapshotFile, "{\"date\":\"2026-10-19\",\"player_tag\":\"#TEST\",\"player_name\":\"Fixture\",\"town_hall\":18,\"builders_permanent\":6,\"builder_goblin_temporary\":false,\"gold_pass_time_reduction\":0.2,\"categories\":[\"Defesas\"],\"items\":[" + item + "," + item + "],\"queue\":[],\"note\":\"\"}");
        LoadedSnapshot loaded = new SnapshotImporter(JsonSupport.createMapper()).load(snapshotFile);

        assertTrue(new SnapshotValidator().validate(loaded.raw()).stream()
                .anyMatch(issue -> issue.code().equals("DUPLICATE_ITEM_DEDUPLICATED")));
        assertEquals(1, new SnapshotNormalizer().normalize(loaded).entities().size());
    }

    @Test
    void missingSuperchargeFieldsRemainUnknownInsteadOfBecomingZero() throws Exception {
        Path snapshotFile = temp.resolve("unknown-sc.json");
        Files.writeString(snapshotFile, """
                {
                  "date":"2026-10-19","player_tag":"#TEST","player_name":"Fixture","town_hall":18,
                  "builders_permanent":6,"builder_goblin_temporary":false,"gold_pass_time_reduction":0.2,
                  "categories":["Defesas"],
                  "items":[{"category":"Defesas","name":"Canhão","quantity":1,"current_level":1,
                    "max_level":2,"count_at_max":0,"confidence":"test","notes":"","source":"synthetic"}],
                  "queue":[],"note":""
                }
                """);
        LoadedSnapshot loaded = new SnapshotImporter(JsonSupport.createMapper()).load(snapshotFile);
        VillageSnapshot normalized = new SnapshotNormalizer().normalize(loaded);

        assertNull(normalized.entities().get(0).maxSupercharge());
        assertNull(normalized.entities().get(0).sc1Completed());
        assertNull(normalized.entities().get(0).sc2Completed());
        assertNull(normalized.entities().get(0).sc3Completed());
        assertTrue(new SnapshotValidator().validate(loaded.raw()).stream()
                .anyMatch(issue -> issue.code().equals("SUPERCHARGE_STATE_UNKNOWN")));
    }

    @Test
    void malformedSnapshotAndUnsupportedCatalogProduceClearInputErrors() throws Exception {
        Path malformed = temp.resolve("bad.json");
        Files.writeString(malformed, "{not-json");
        assertThrows(InputDataException.class,
                () -> new SnapshotImporter(JsonSupport.createMapper()).load(malformed));

        Path unsupported = temp.resolve("steps.txt");
        Files.writeString(unsupported, "data");
        assertThrows(InputDataException.class,
                () -> new CatalogImporter(JsonSupport.createMapper()).load(unsupported));
    }

    @Test
    void legacyKeyIsDeterministicAndExplicitlyNotAnOfficialIdentifier() {
        assertEquals("legacy_ptbr_bebe_dragao", LegacyKeyFactory.fromLegacyName("Bebê Dragão"));
        assertEquals(LegacyKeyFactory.fromLegacyName("Bebê Dragão"), LegacyKeyFactory.fromLegacyName("Bebê Dragão"));
        assertTrue(LegacyKeyFactory.fromLegacyName("Defesa Aérea").startsWith("legacy_ptbr_"));
    }
}
