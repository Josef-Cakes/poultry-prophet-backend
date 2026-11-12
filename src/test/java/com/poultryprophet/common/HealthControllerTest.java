package com.poultryprophet.common;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTest {
    private final DataSource dataSource = mock(DataSource.class);
    private HealthController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        controller = new HealthController(dataSource, Duration.ofMillis(100));
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @AfterEach
    void tearDown() {
        controller.close();
    }

    @Test
    void livenessDoesNotAccessTheDatabase() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"));
        verifyNoInteractions(dataSource);
    }

    @Test
    void readinessReportsAvailableDatabaseAndClosesConnection() throws Exception {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(true);

        mvc.perform(get("/api/health/ready"))
                .andExpect(status().isOk()).andExpect(content().json("{\"status\":\"UP\"}"));
        verify(connection).close();
    }

    @Test
    void readinessHidesDatabaseFailureDetails() throws Exception {
        when(dataSource.getConnection()).thenThrow(new SQLException("secret database address"));

        mvc.perform(get("/api/health/ready"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().json("{\"status\":\"DOWN\"}", true));
    }

    @Test
    void invalidConnectionIsNotReady() throws Exception {
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.isValid(1)).thenReturn(false);

        mvc.perform(get("/api/health/ready")).andExpect(status().isServiceUnavailable());
        verify(connection).close();
    }

    @Test
    void blockedDatabaseProbeTimesOutAndDoesNotAccumulateWorkers() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch exited = new CountDownLatch(1);
        when(dataSource.getConnection()).thenAnswer(invocation -> {
            try {
                while (true) {
                    try {
                        release.await();
                        break;
                    } catch (InterruptedException ignored) {
                        // Simulate a JDBC driver that ignores interruption.
                    }
                }
                throw new SQLException("unavailable");
            } finally {
                exited.countDown();
            }
        });

        try {
            long start = System.nanoTime();
            mvc.perform(get("/api/health/ready")).andExpect(status().isServiceUnavailable());
            mvc.perform(get("/api/health/ready")).andExpect(status().isServiceUnavailable());
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            verify(dataSource, times(1)).getConnection();
            mvc.perform(get("/api/health")).andExpect(status().isOk());
        } finally {
            release.countDown();
            assertThat(exited.await(2, TimeUnit.SECONDS)).isTrue();
        }
    }
}
