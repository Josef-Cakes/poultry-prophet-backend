package com.poultryprophet.testlab;

import com.poultryprophet.config.ReleaseProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;

/**
 * Enables synthetic generation only when both the release label and datasource are local.
 * The check is server-side so a frontend flag cannot accidentally enable the feature on Render.
 */
@Service
public class TestLabSafetyService {

    private static final Set<String> ALLOWED_ENVIRONMENTS = Set.of("local", "validation", "test");
    private static final Set<String> ALLOWED_DATABASE_HOSTS = Set.of(
            "localhost", "127.0.0.1", "::1", "validation-db", "postgres", "db");

    private final ReleaseProperties release;
    private final String datasourceUrl;

    public TestLabSafetyService(ReleaseProperties release,
                                @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.release = release;
        this.datasourceUrl = datasourceUrl;
    }

    public TestLabStatusResponse status() {
        String environment = clean(release.getEnvironment());
        if (!ALLOWED_ENVIRONMENTS.contains(environment)) {
            return disabled(environment, "Test Lab is disabled outside a local or validation environment.");
        }

        String host = datasourceHost(datasourceUrl);
        if (host == null || !ALLOWED_DATABASE_HOSTS.contains(host.toLowerCase(Locale.ROOT))) {
            return disabled(environment, "Test Lab requires a database running on this computer.");
        }

        return new TestLabStatusResponse(true, environment,
                "Local database verified. Generated records stay in a labelled test-copy batch.");
    }

    public void requireEnabled() {
        TestLabStatusResponse status = status();
        if (!status.enabled()) {
            throw new AccessDeniedException(status.message());
        }
    }

    private TestLabStatusResponse disabled(String environment, String message) {
        return new TestLabStatusResponse(false,
                environment.isBlank() ? "unknown" : environment, message);
    }

    private String datasourceHost(String value) {
        if (value == null || value.isBlank()) return null;
        String uriValue = value.startsWith("jdbc:") ? value.substring("jdbc:".length()) : value;
        try {
            return URI.create(uriValue).getHost();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
