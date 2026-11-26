package com.poultryprophet.common;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

class RenderUrlConfigurationTest {
    private MockEnvironment environment() {
        var yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("application.yml"));
        var env = new MockEnvironment();
        env.getPropertySources().addLast(new PropertiesPropertySource("application", Objects.requireNonNull(yaml.getObject())));
        return env;
    }

    @Test
    void renderUsesItsOwnExternalUrlWhenNoOverrideIsSet() {
        var env = environment().withProperty("RENDER_EXTERNAL_URL", "https://actual-service.example");
        assertThat(env.getProperty("app.render.url")).isEqualTo("https://actual-service.example");
    }

    @Test
    void explicitOverrideIncludingBlankTakesPrecedence() {
        var env = environment().withProperty("RENDER_EXTERNAL_URL", "https://actual-service.example")
                .withProperty("APP_RENDER_URL", "");
        assertThat(env.getProperty("app.render.url")).isEmpty();
        env.setProperty("APP_RENDER_URL", "http://local-test.example");
        assertThat(env.getProperty("app.render.url")).isEqualTo("http://local-test.example");
    }

    @Test
    void selfPingRemainsDisabledWhenNoUrlExists() {
        assertThat(environment().getProperty("app.render.url")).isEmpty();
    }
}
