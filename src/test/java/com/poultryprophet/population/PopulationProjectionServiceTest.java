package com.poultryprophet.population;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.EventType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PopulationProjectionServiceTest {
    private final PopulationProjectionService service = new PopulationProjectionService();
    private final ZoneId zone = ZoneId.of("Asia/Manila");

    @Test
    void returnsAValidPopulationForAValidLedger() {
        Batch batch = batch(100, 90);
        PopulationProjection projection = service.project(batch,
                List.of(event(1L, EventType.HEALTH_DEATH, 10, null)), today(), zone);

        assertThat(projection.validPopulation()).isEqualTo(90);
        assertThat(projection.calculatedPopulation()).isEqualTo(90);
        assertThat(projection.status()).isEqualTo(PopulationProjection.VALID);
        assertThat(projection.reconciliationRequired()).isFalse();
    }

    @Test
    void marksAlegacyEventThatDrivesPopulationBelowZeroForReconciliation() {
        Batch batch = batch(100, 39);
        PopulationProjection projection = service.project(batch,
                List.of(event(12L, EventType.MORTALITY, 161, -161)), today(), zone);

        assertThat(projection.calculatedPopulation()).isEqualTo(-61);
        assertThat(projection.validPopulation()).isNull();
        assertThat(projection.boundedPopulation()).isZero();
        assertThat(projection.issueCode()).isEqualTo("BELOW_ZERO");
        assertThat(projection.firstInvalidEventId()).isEqualTo(12L);
    }

    @Test
    void marksAledgerThatGoesAboveTheInitialPopulation() {
        Batch batch = batch(10, 10);
        PopulationProjection projection = service.project(batch,
                List.of(event(13L, EventType.TRANSFER_IN, 1, null)), today(), zone);

        assertThat(projection.validPopulation()).isNull();
        assertThat(projection.issueCode()).isEqualTo("ABOVE_INITIAL");
        assertThat(projection.boundedPopulation()).isEqualTo(10);
    }

    @Test
    void doesNotTreatStoredCurrentCountMismatchAsAValidCount() {
        Batch batch = batch(100, 80);
        PopulationProjection projection = service.project(batch, List.of(), today(), zone);

        assertThat(projection.calculatedPopulation()).isEqualTo(100);
        assertThat(projection.validPopulation()).isNull();
        assertThat(projection.issueCode()).isEqualTo("STORED_COUNT_MISMATCH");
    }

    @Test
    void ledgerProjectionCanValidateAWriteAgainstTheEventHistoryWithoutComparingPreEventStoredCount() {
        Batch batch = batch(100, 90);
        PopulationProjection projection = service.projectLedger(batch,
                List.of(event(15L, EventType.HEALTH_DEATH, 10, null)), today(), zone);

        assertThat(projection.validPopulation()).isEqualTo(90);
        assertThat(projection.reconciliationRequired()).isFalse();
    }

    @Test
    void identifiesMalformedCountCorrectionWithoutThrowingDuringRead() {
        Batch batch = batch(100, 100);
        PopulationProjection projection = service.project(batch,
                List.of(event(14L, EventType.COUNT_CORRECTION, 0, null)), today().minusDays(1), zone);

        assertThat(projection.calculatedPopulation()).isEqualTo(100);
        assertThat(projection.validPopulation()).isNull();
        assertThat(projection.issueCode()).isEqualTo("INVALID_EVENT");
    }

    private static Batch batch(int initial, int current) {
        Batch batch = new Batch();
        batch.setInitialPopulation(initial);
        batch.setCurrentPopulation(current);
        batch.setStartDate(LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(10));
        return batch;
    }

    private static BatchEvent event(Long id, EventType type, int affected, Integer delta) {
        BatchEvent event = new BatchEvent();
        event.setId(id);
        event.setEventDate(LocalDate.now(ZoneId.of("Asia/Manila")).minusDays(1));
        event.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        event.setEventType(type);
        event.setAffectedCount(affected);
        event.setPopulationDelta(delta);
        return event;
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneId.of("Asia/Manila"));
    }
}
