package com.poultryprophet.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Set;

/**
 * Fails closed when the validation profile is pointed at a non-local database.
 *
 * This is deliberately profile-scoped. It protects the disposable validation stack without
 * changing the production database configuration or the application's domain architecture.
 */
@Component
@Profile("validation")
public class ValidationEnvironmentGuard {

    private static final Set<String> ALLOWED_DATABASE_HOSTS = Set.of(
            "localhost", "127.0.0.1", "::1", "validation-db", "postgres", "db");

    private final String environment;
    private final String datasourceUrl;

    public ValidationEnvironmentGuard(
            @Value("${app.release.environment:}") String environment,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.environment = environment;
        this.datasourceUrl = datasourceUrl;
    }

    @PostConstruct
    void verify() {
        if (!"validation".equalsIgnoreCase(environment)) {
            throw new IllegalStateException("Validation profile requires app.release.environment=validation");
        }
        if (datasourceUrl == null || datasourceUrl.isBlank()) {
            throw new IllegalStateException("Validation profile requires a datasource URL");
        }

        String jdbcUrl = datasourceUrl.startsWith("jdbc:")
                ? datasourceUrl.substring("jdbc:".length()) : datasourceUrl;
        URI uri;
        try {
            uri = URI.create(jdbcUrl);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Validation datasource URL is not parseable", exception);
        }

        String host = uri.getHost();
        if (host == null || !ALLOWED_DATABASE_HOSTS.contains(host.toLowerCase())) {
            throw new IllegalStateException(
                    "Validation database must be local; refusing datasource host " + host);
        }

        String path = uri.getPath() == null ? "" : uri.getPath();
        String databaseName = path.startsWith("/") ? path.substring(1) : path;
        if (databaseName.contains("?")) {
            databaseName = databaseName.substring(0, databaseName.indexOf('?'));
        }
        if (!databaseName.toLowerCase().contains("validation")
                && !databaseName.toLowerCase().contains("test")) {
            throw new IllegalStateException(
                    "Validation database name must contain validation or test; refusing " + databaseName);
        }
    }
}
