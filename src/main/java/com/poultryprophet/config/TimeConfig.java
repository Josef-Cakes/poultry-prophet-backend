package com.poultryprophet.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    @Bean
    public Clock applicationClock(@Value("${app.time-zone:Asia/Manila}") String timeZone) {
        return Clock.system(ZoneId.of(timeZone));
    }
}
