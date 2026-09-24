package com.poultryprophet.version;

import com.poultryprophet.config.ReleaseProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VersionControllerTest {

    @Test
    void exposesBuildAndCompatibilityMetadata() {
        ReleaseProperties properties = new ReleaseProperties();
        properties.setVersion("0.1.0-validation");
        properties.setCommitSha("abc1234");
        properties.setBuildTime("2026-09-14T00:00:00Z");
        properties.setEnvironment("validation");
        properties.setFrontendVersion("0.1.0-validation");

        VersionResponse response = new VersionController(properties).version();

        assertThat(response.application()).isEqualTo("poultry-prophet-backend");
        assertThat(response.version()).isEqualTo("0.1.0-validation");
        assertThat(response.commitSha()).isEqualTo("abc1234");
        assertThat(response.environment()).isEqualTo("validation");
        assertThat(response.compatibleFrontendVersion()).isEqualTo("0.1.0-validation");
    }
}
