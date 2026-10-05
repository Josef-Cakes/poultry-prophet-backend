package com.poultryprophet.testlab;

import com.poultryprophet.config.ReleaseProperties;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestLabSafetyServiceTest {

    @Test
    void enablesLocalEnvironmentWithLocalDatabase() {
        TestLabStatusResponse status = service("local",
                "jdbc:postgresql://localhost:5432/poultry_prophet").status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.environment()).isEqualTo("local");
    }

    @Test
    void enablesValidationDockerDatabase() {
        TestLabStatusResponse status = service("validation",
                "jdbc:postgresql://validation-db:5432/poultry_prophet_validation").status();

        assertThat(status.enabled()).isTrue();
    }

    @Test
    void rejectsProductionEnvironmentEvenWithLocalDatabase() {
        TestLabSafetyService service = service("production",
                "jdbc:postgresql://localhost:5432/poultry_prophet");

        assertThat(service.status().enabled()).isFalse();
        assertThatThrownBy(service::requireEnabled).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void rejectsRemoteDatabaseEvenWhenFrontendCallsItLocal() {
        TestLabSafetyService service = service("local",
                "jdbc:postgresql://production-db.example.com:5432/poultry_prophet");

        assertThat(service.status().enabled()).isFalse();
        assertThatThrownBy(service::requireEnabled).isInstanceOf(AccessDeniedException.class);
    }

    private TestLabSafetyService service(String environment, String datasourceUrl) {
        ReleaseProperties properties = new ReleaseProperties();
        properties.setEnvironment(environment);
        return new TestLabSafetyService(properties, datasourceUrl);
    }
}
