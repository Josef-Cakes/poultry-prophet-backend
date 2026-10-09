package com.poultryprophet.batch;

import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class BatchAgePolicyTest {
    @Test
    void hatchDateIsDayZeroAndDatesBeforeHatchDoNotBecomeNegative() {
        LocalDate hatch = LocalDate.of(2026, 10, 8);
        assertThat(BatchAgePolicy.ageDays(hatch, hatch)).isZero();
        assertThat(BatchAgePolicy.ageDays(hatch, hatch.minusDays(2))).isZero();
        assertThat(BatchAgePolicy.ageDays(hatch, hatch.plusDays(31))).isEqualTo(31);
    }
}
