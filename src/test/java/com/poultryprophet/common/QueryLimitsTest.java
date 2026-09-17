package com.poultryprophet.common;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryLimitsTest {

    @Test
    void clampsInvalidAndOversizedPageRequests() {
        assertThat(QueryLimits.clamp(-1)).isEqualTo(1);
        assertThat(QueryLimits.clamp(0)).isEqualTo(1);
        assertThat(QueryLimits.clamp(25)).isEqualTo(25);
        assertThat(QueryLimits.clamp(101)).isEqualTo(QueryLimits.MAX_PAGE_SIZE);
    }
}
