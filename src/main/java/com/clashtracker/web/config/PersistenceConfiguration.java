package com.clashtracker.web.config;

import com.clashtracker.web.persistence.OfficialCollectionRepository;
import com.clashtracker.web.persistence.SnapshotRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * Persistence wiring for SQL Server. This whole configuration is active only when
 * {@code tracker.db.enabled=true}; otherwise the application starts and serves the offline
 * dashboard without any database. Flyway migrations run explicitly at startup when enabled.
 *
 * <p>Default connection uses Windows integrated authentication to the local default instance, so no
 * password is stored anywhere. Override {@code tracker.db.url} to use SQL authentication.
 */
@Configuration
@ConditionalOnProperty(prefix = "tracker.db", name = "enabled", havingValue = "true")
@EnableTransactionManagement
public class PersistenceConfiguration {

    private static final Logger LOGGER = LoggerFactory.getLogger(PersistenceConfiguration.class);

    @Bean
    public DataSource trackerDataSource(
            @Value("${tracker.db.url:jdbc:sqlserver://localhost;databaseName=clash_village_tracker_dev;encrypt=true;trustServerCertificate=true;integratedSecurity=true}") String url,
            @Value("${tracker.db.user:}") String user,
            @Value("${tracker.db.password:}") String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        if (!user.isBlank()) {
            config.setUsername(user);
            config.setPassword(password);
        }
        config.setMaximumPoolSize(4);
        config.setPoolName("clash-tracker-pool");
        LOGGER.info("SQL Server persistence enabled (integratedSecurity={})",
                url.toLowerCase().contains("integratedsecurity=true"));
        return new HikariDataSource(config);
    }

    @Bean(initMethod = "migrate")
    public Flyway flyway(DataSource dataSource) {
        return Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
    }

    @Bean
    public JdbcTemplate trackerJdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    public SnapshotRepository snapshotRepository(JdbcTemplate trackerJdbcTemplate) {
        return new SnapshotRepository(trackerJdbcTemplate);
    }

    @Bean
    public OfficialCollectionRepository officialCollectionRepository(JdbcTemplate trackerJdbcTemplate) {
        return new OfficialCollectionRepository(trackerJdbcTemplate);
    }
}
