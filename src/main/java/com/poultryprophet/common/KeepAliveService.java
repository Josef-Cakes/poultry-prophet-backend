package com.poultryprophet.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Keeps a free Render web service warm by periodically requesting its public
 * health endpoint. The service is disabled when {@code app.render.url} is not
 * configured, which keeps local development side-effect free.
 */
@Service
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final String renderUrl;

    public KeepAliveService(@Value("${app.render.url:}") String renderUrl) {
        this.renderUrl = renderUrl == null ? "" : renderUrl.trim();
    }

    @Scheduled(
            initialDelayString = "${app.keep-alive.initial-delay-ms:60000}",
            fixedRateString = "${app.keep-alive.fixed-rate-ms:600000}")
    public void selfPing() {
        if (renderUrl.isBlank()) {
            return;
        }

        String baseUrl = renderUrl.endsWith("/")
                ? renderUrl.substring(0, renderUrl.length() - 1)
                : renderUrl;
        String healthUrl = baseUrl + "/api/health";

        try {
            restTemplate.getForObject(healthUrl, String.class);
            log.info("Render keep-alive ping successful");
        } catch (Exception exception) {
            log.warn("Render keep-alive ping failed: {}", exception.getMessage());
        }
    }
}
