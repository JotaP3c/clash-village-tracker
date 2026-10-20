package com.clashtracker.validation;

import com.clashtracker.TestFixtures;
import com.clashtracker.catalog.CatalogImporter;
import com.clashtracker.catalog.LoadedCatalog;
import com.clashtracker.domain.GameCatalog;
import com.clashtracker.domain.Severity;
import com.clashtracker.domain.UpgradeType;
import com.clashtracker.domain.ValidationIssue;
import com.clashtracker.domain.VillageEntity;
import com.clashtracker.importer.raw.RawQueueEntry;
import com.clashtracker.importer.raw.RawVillageItem;
import com.clashtracker.importer.raw.RawVillageSnapshot;
import com.clashtracker.util.JsonSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ValidationTest {
    @TempDir
    Path temp;

    @Test
    void reportsInvalidLevelsQuantitiesSuperchargeTargetDurationAndTimerDate() {
        RawVillageItem item = new RawVillageItem("Defesas", "Canhão", 1, 5, 4, 2,
                0, 1, 0, 0, "test", "", "synthetic");
        RawQueueEntry queue = new RawQueueEntry("Canhão", "Construtor", 1, 4, 3,
                new BigDecimal("-1"), "Nível base", "test");
        RawVillageSnapshot snapshot = new RawVillageSnapshot(null, "#TEST", "Fixture", 18,
                6, false, new BigDecimal("0.20"), List.of("Defesas"), List.of(item), List.of(queue), "");

        Set<String> codes = new SnapshotValidator().validate(snapshot).stream()
                .map(ValidationIssue::code).collect(Collectors.toSet());

        assertTrue(codes.contains("CURRENT_LEVEL_ABOVE_MAX"));
        assertTrue(codes.contains("COUNT_DISTRIBUTION_INCOHERENT"));
        assertTrue(codes.contains("SUPERCHARGE_NOT_ELIGIBLE"));
        assertTrue(codes.contains("TARGET_BELOW_CURRENT"));
        assertTrue(codes.contains("REMAINING_DURATION_NEGATIVE"));
        assertTrue(codes.contains("TIMER_WITHOUT_OBSERVATION_DATE"));
    }

    @Test
    void unknownLevelsWithCountAtMaxArePendingNotSilentlyFull() {
        RawVillageItem item = new RawVillageItem("Defesas Artesanais", "Unknown", 1,
                null, null, 1, 0, 0, 0, 0, "unknown", "", "synthetic");
        RawVillageSnapshot snapshot = validSnapshot(item);
        List<ValidationIssue> issues = new SnapshotValidator().validate(snapshot);

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("COUNT_AT_MAX_WITH_UNKNOWN_LEVEL")));
        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("CURRENT_LEVEL_UNKNOWN")));
        assertTrue(issues.stream().noneMatch(issue -> issue.severity() == Severity.ERROR));
    }

    @Test
    void catalogDetectsConflictingDuplicateJumpNegativeDurationAndToken() throws Exception {
        Path catalog = temp.resolve("invalid.json");
        Files.writeString(catalog, """
                [
                  {"category":"Defesas","item":"Canhão","from_level":1,"to_level":3,"stage":"Nível permanente","count":1,"base_days":-1,"pass_days":null,"in_progress":false,"remaining_now_days":null,"source":"authorization=Bearer abcdefgh1234","confidence":"test"},
                  {"category":"Defesas","item":"Canhão","from_level":1,"to_level":3,"stage":"Nível permanente","count":2,"base_days":2,"pass_days":null,"in_progress":false,"remaining_now_days":null,"source":"synthetic","confidence":"test"}
                ]
                """);
        LoadedCatalog loaded = new CatalogImporter(JsonSupport.createMapper()).load(catalog);
        Set<String> codes = new CatalogValidator().validateRaw(loaded).stream()
                .map(ValidationIssue::code).collect(Collectors.toSet());

        assertTrue(codes.contains("CATALOG_CONTAINS_TOKEN"));
        assertTrue(codes.contains("CATALOG_LEVEL_JUMP"));
        assertTrue(codes.contains("DURATION_NEGATIVE"));
        assertTrue(codes.contains("DUPLICATE_STEP_CONFLICT"));
    }

    @Test
    void nullDurationIsWarningRatherThanZeroOrFatalError() throws Exception {
        Path catalog = temp.resolve("null-duration.json");
        Files.writeString(catalog, """
                [{"category":"Defesas","item":"Canhão","from_level":1,"to_level":2,"stage":"Nível permanente","count":1,"base_days":null,"pass_days":null,"in_progress":false,"remaining_now_days":null,"source":"synthetic","confidence":"pending"}]
                """);
        LoadedCatalog loaded = new CatalogImporter(JsonSupport.createMapper()).load(catalog);
        List<ValidationIssue> issues = new CatalogValidator().validateRaw(loaded);

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("DURATION_UNKNOWN")
                && issue.severity() == Severity.WARNING));
        assertFalse(issues.stream().anyMatch(issue -> issue.severity() == Severity.ERROR));
    }

    @Test
    void crossValidationRejectsSuperchargeBeyondObservedEligibility() {
        VillageEntity entity = TestFixtures.entity("Canhão", "Defesas", 1, 10, 10, 1, 0, 0, 0, 0);
        GameCatalog catalog = TestFixtures.catalog(List.of(
                TestFixtures.step("Canhão", "Sobrecargas", UpgradeType.SUPERCHARGE, 0, 1, 1, 100L)));
        List<ValidationIssue> issues = new CrossValidator().validate(
                TestFixtures.snapshot(List.of(entity), List.of()), catalog);

        assertTrue(issues.stream().anyMatch(issue -> issue.code().equals("SC_INCOMPATIBLE_WITH_ELIGIBILITY")
                && issue.severity() == Severity.ERROR));
    }

    private static RawVillageSnapshot validSnapshot(RawVillageItem item) {
        return new RawVillageSnapshot(LocalDate.of(2026, 10, 19), "#TEST", "Fixture", 18,
                6, false, new BigDecimal("0.20"), List.of(item.category()), List.of(item), List.of(), "");
    }
}
