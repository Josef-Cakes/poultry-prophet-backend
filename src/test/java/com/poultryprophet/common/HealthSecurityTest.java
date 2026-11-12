package com.poultryprophet.common;

import com.poultryprophet.config.SecurityConfig;
import com.poultryprophet.config.SecurityProperties;
import com.poultryprophet.security.AppUserDetailsService;
import com.poultryprophet.security.JwtAuthenticationFilter;
import com.poultryprophet.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import javax.sql.DataSource;
import java.sql.Connection;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitConfig(HealthSecurityTest.Config.class)
@WebAppConfiguration
class HealthSecurityTest {
    @Autowired private WebApplicationContext context;
    @Autowired private DataSource dataSource;
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void healthRoutesArePublicButOtherRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk());
        mvc.perform(get("/api/health/ready")).andExpect(status().isOk());
        mvc.perform(get("/api/health/private")).andExpect(status().isUnauthorized());
    }

    @Configuration
    @EnableWebMvc
    @EnableWebSecurity
    @Import(SecurityConfig.class)
    static class Config {
        @Bean DataSource dataSource() { return mock(DataSource.class); }
        @Bean HealthController healthController(DataSource dataSource) { return new HealthController(dataSource); }
        @Bean SecurityProperties securityProperties() { return new SecurityProperties(); }
        @Bean JwtAuthenticationFilter jwtAuthenticationFilter() {
            return new JwtAuthenticationFilter(mock(JwtService.class), mock(AppUserDetailsService.class));
        }
    }
}
