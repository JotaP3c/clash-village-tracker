package com.clashtracker.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for the read-only REST API that serves the Clash Village Tracker dashboard.
 *
 * <p>This web layer reuses the existing offline domain and use cases (notably
 * {@code ReportApplication}) without duplicating any business rule. The historical CLI, the Excel
 * report generation, and the strictly offline mode remain unchanged and are still packaged by the
 * shade plugin into {@code clash-village-tracker.jar}. This application is packaged separately as
 * {@code clash-village-tracker-web.jar}.
 */
@SpringBootApplication(exclude = {
        // Persistence is wired manually in PersistenceConfiguration and is active only when
        // tracker.db.enabled=true, so the app can start and serve the offline dashboard with no DB.
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        FlywayAutoConfiguration.class
})
@ConfigurationPropertiesScan
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
