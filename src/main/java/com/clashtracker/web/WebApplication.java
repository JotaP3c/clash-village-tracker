package com.clashtracker.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
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
@SpringBootApplication
@ConfigurationPropertiesScan
public class WebApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebApplication.class, args);
    }
}
