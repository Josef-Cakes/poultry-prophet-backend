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

    @Test
    void lifecycleStagesFollowTheDayZeroBoundaries() {
        assertThat(BatchAgePolicy.stageName(0)).isEqualTo("brooding");
        assertThat(BatchAgePolicy.stageName(30)).isEqualTo("brooding");
        assertThat(BatchAgePolicy.stageName(31)).isEqualTo("ranging");
        assertThat(BatchAgePolicy.stageName(120)).isEqualTo("ranging");
        assertThat(BatchAgePolicy.stageName(121)).isEqualTo("pre-conditioning");
    }
}
