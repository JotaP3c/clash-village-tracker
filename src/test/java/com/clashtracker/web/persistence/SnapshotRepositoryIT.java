package com.clashtracker.web.persistence;

import com.clashtracker.application.ReportApplication;
import com.clashtracker.application.ReportRequest;
import com.clashtracker.application.VillageAnalysis;
import com.clashtracker.domain.VillageSnapshot;
import com.clashtracker.util.JsonSupport;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the SQL Server snapshot repository. It runs ONLY when CLASH_DB_PASSWORD is
 * present in the environment (and optionally CLASH_DB_URL/USER); otherwise JUnit skips it, so the
 * normal offline suite is unaffected. It applies Flyway migrations, imports the real historical
 * snapshot, proves idempotency, and reads it back. It does not drop or recreate the database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "CLASH_DB_PASSWORD", matches = ".+")
class SnapshotRepositoryIT {

    private HikariDataSource dataSource;
    private SnapshotRepository repository;
    private VillageSnapshot snapshot;

    @BeforeAll
    void setUp() throws Exception {
        String url = envOrDefault("CLASH_DB_URL",
                "jdbc:sqlserver://localhost;databaseName=clash_village_tracker_dev;encrypt=true;trustServerCertificate=true");
        String user = envOrDefault("CLASH_DB_USER", "clash_app");
        String password = System.getenv("CLASH_DB_PASSWORD");

        DataSource ds = dataSource = buildDataSource(url, user, password);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration")
                .baselineOnMigrate(true).load().migrate();
        repository = new SnapshotRepository(new JdbcTemplate(ds));

        Path moduleRoot = locateModuleRoot();
        ReportApplication application = new ReportApplication(JsonSupport.createMapper());
        VillageAnalysis analysis = application.analyze(new ReportRequest(
                moduleRoot.resolve("data/snapshot_2026-10-19.json"),
                moduleRoot.resolve("data/upgrade_steps.json"),
                null, new BigDecimal("0.20"), Path.of("output"), true));
        snapshot = analysis.snapshot();
    }

    @AfterAll
    void tearDown() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @Test
    void importsHistoricalSnapshotIdempotentlyAndReadsItBack() {
        int before = repository.countSnapshots();

        long firstId = repository.importSnapshot(snapshot);
        long secondId = repository.importSnapshot(snapshot);
        assertEquals(firstId, secondId, "re-importing the same snapshot must be idempotent");

        int after = repository.countSnapshots();
        assertTrue(after >= before, "count never decreases");

        List<PersistedSnapshotSummary> stored = repository.listSnapshots("#U8J89QP2");
        assertFalse(stored.isEmpty());
        PersistedSnapshotSummary summary = stored.get(0);
        assertEquals("#U8J89QP2", summary.playerTag());
        assertEquals(18, summary.townHallLevel());
        assertEquals("DATE", summary.observedPrecision());
        // Observation date is the historical date, kept distinct from the import instant.
        assertEquals("2026-10-19", summary.observedDate().toString());
        assertTrue(summary.entityCount() > 0);
    }

    private static HikariDataSource buildDataSource(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        config.setMaximumPoolSize(2);
        return new HikariDataSource(config);
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Path locateModuleRoot() {
        Path current = Path.of("").toAbsolutePath();
        for (int depth = 0; depth < 5 && current != null; depth++) {
            if (Files.exists(current.resolve("data/snapshot_2026-10-19.json"))) {
                return current;
            }
            current = current.getParent();
        }
        return Path.of("").toAbsolutePath();
    }
}
