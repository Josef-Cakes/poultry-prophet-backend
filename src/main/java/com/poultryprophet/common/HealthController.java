package com.poultryprophet.common;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final DataSource dataSource;
    private final Duration timeout;
    private final ThreadPoolExecutor probeExecutor;

    @Autowired
    public HealthController(DataSource dataSource) {
        this(dataSource, Duration.ofSeconds(2));
    }

    HealthController(DataSource dataSource, Duration timeout) {
        this.dataSource = dataSource;
        this.timeout = timeout;
        this.probeExecutor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
                new SynchronousQueue<>(), runnable -> {
                    Thread thread = new Thread(runnable, "database-readiness-probe");
                    thread.setDaemon(true);
                    return thread;
                });
    }

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }

    @GetMapping("/ready")
    public ResponseEntity<Map<String, String>> readiness() {
        Future<Boolean> probe;
        try {
            probe = probeExecutor.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    return connection.isValid(1);
                }
            });
        } catch (RejectedExecutionException ex) {
            return unavailable();
        }

        try {
            return Boolean.TRUE.equals(probe.get(timeout.toMillis(), TimeUnit.MILLISECONDS))
                    ? ResponseEntity.ok(Map.of("status", "UP")) : unavailable();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return unavailable();
        } catch (ExecutionException | TimeoutException ex) {
            return unavailable();
        } finally {
            probe.cancel(true);
        }
    }

    private ResponseEntity<Map<String, String>> unavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "DOWN"));
    }

    @PreDestroy
    public void close() {
        probeExecutor.shutdownNow();
    }
}
