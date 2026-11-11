package com.clashtracker.web.config;

import com.clashtracker.application.ReportApplication;
import com.clashtracker.util.JsonSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Wires the existing application layer into the Spring context. The domain {@link ObjectMapper}
 * configuration from {@link JsonSupport} is reused so API serialization matches the CLI exactly.
 */
@Configuration
public class TrackerConfiguration {

    /** Shared mapper reused by the domain application layer. */
    @Bean
    public ObjectMapper domainObjectMapper() {
        return JsonSupport.createMapper();
    }

    @Bean
    public ReportApplication reportApplication(ObjectMapper domainObjectMapper) {
        return new ReportApplication(domainObjectMapper);
    }

    /**
     * Local development CORS: allow the Angular dev server (default http://localhost:4200) to call
     * the API. This is intentionally restricted and is not a production publishing policy.
     */
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                        .allowedOrigins("http://localhost:4200", "http://127.0.0.1:4200")
                        .allowedMethods("GET", "OPTIONS");
            }
        };
    }
}
