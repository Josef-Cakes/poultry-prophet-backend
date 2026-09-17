package com.poultryprophet.common;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateValidationServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 13);
    private final DateValidationService service = new DateValidationService(
            Clock.fixed(Instant.parse("2026-09-13T00:00:00Z"), ZoneId.of("Asia/Manila")));

    @Test
    void usesManilaClockForTodayAndDefaults() {
        assertThat(service.today()).isEqualTo(TODAY);
        assertThat(service.resolve(null)).isEqualTo(TODAY);
    }

    @Test
    void rejectsDatesBeforeBatchStartAndAfterToday() {
        assertThatThrownBy(() -> service.validate(TODAY.minusDays(1), TODAY))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.validate(TODAY.plusDays(1), TODAY.minusDays(2)))
                .isInstanceOf(BadRequestException.class);
    }
}
