package com.poultryprophet.analytics;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.LifecycleStage;
import com.poultryprophet.config.AnalyticsProperties;
import com.poultryprophet.record.DailyRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnalyticsServiceTest {

    private final AnalyticsService service = new AnalyticsService(new AnalyticsProperties());

    @Test
    void reportsInsufficientBaselineInsteadOfFabricatingPerfectBhi() {
        IndicatorResult result = service.compute(List.of(record("2026-09-14", 33, 1_000, 1_800)), batch());

        assertThat(result.bhi()).isNull();
        assertThat(result.sufficientData()).isFalse();
        assertThat(result.missingDataWarning()).contains("prior positive feed and water");
        assertThat(result.wfr()).isEqualTo(1.8);
    }

    @Test
    void exposesWeightedContributionsAfterBaselineExists() {
        List<DailyRecord> records = List.of(
                record("2026-09-14", 33, 1_100, 1_900),
                record("2026-09-13", 33, 1_000, 1_800));

        IndicatorResult result = service.compute(records, batch());

        assertThat(result.bhi()).isNotNull();
        assertThat(result.sufficientData()).isTrue();
        assertThat(result.temperatureContribution()).isNotNull();
        assertThat(result.mortalityContribution()).isNotNull();
        assertThat(result.feedContribution()).isNotNull();
        assertThat(result.waterContribution()).isNotNull();
        assertThat(result.formulaVersion()).isEqualTo(AnalyticsService.FORMULA_VERSION);
    }

    private static DailyRecord record(String date, double temperature, double feed, double water) {
        DailyRecord record = new DailyRecord();
        record.setRecordDate(LocalDate.parse(date));
        record.setTemperatureC(temperature);
        record.setMortalityCount(0);
        record.setFeedIntakeG(feed);
        record.setWaterIntakeMl(water);
        return record;
    }

    private static Batch batch() {
        Batch batch = new Batch();
        batch.setCurrentPopulation(100);
        batch.setInitialPopulation(100);
        batch.setStage(new LifecycleStage("brooding", 0));
        return batch;
    }
}
