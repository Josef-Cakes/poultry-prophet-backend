package com.poultryprophet.selectionreview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.poultryprophet.batch.Batch;
import com.poultryprophet.batch.BatchStatus;
import com.poultryprophet.batch.BatchService;
import com.poultryprophet.batch.LifecycleStage;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import com.poultryprophet.event.EventType;
import com.poultryprophet.finance.FinancialTransactionRepository;
import com.poultryprophet.incubation.IncubationCycleRepository;
import com.poultryprophet.input.FarmInputLogRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelectionReviewServiceTest {
    @Mock private BatchSelectionReviewRepository reviewRepository;
    @Mock private BatchEventRepository eventRepository;
    @Mock private FarmInputLogRepository inputRepository;
    @Mock private IncubationCycleRepository incubationRepository;
    @Mock private FinancialTransactionRepository financeRepository;
    @Mock private BatchService batchService;

    @Test
    void keepsPopulationCausesSeparateAndDoesNotExposeFinanceToHandler() {
        Batch batch = new Batch();
        batch.setId(7L);
        batch.setFarmId(3L);
        batch.setName("September batch");
        batch.setInitialPopulation(100);
        batch.setCurrentPopulation(94);
        batch.setStartDate(LocalDate.of(2026, 9, 1));
        LifecycleStage stage = new LifecycleStage("brooding", 0);

        when(batchService.requireBatch(7L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(stage, true));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20)))
                .thenReturn(List.of(event(EventType.HEALTH_DEATH, 2, -2),
                        event(EventType.CONFIRMED_PREDATION, 3, -3),
                        event(EventType.MISSING, 1, -1),
                        event(EventType.FOUND_RETURNED, 1, 1),
                        event(EventType.MORTALITY, 4, -4)));
        when(inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(3L, 7L)).thenReturn(List.of());
        when(incubationRepository.findByFarmIdAndCreatedBatchId(3L, 7L)).thenReturn(List.of());

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService, objectMapper);

        SelectionReviewPayload payload = service.preview(7L, 3L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 20), false);

        assertThat(payload.population().healthRelatedDeaths()).isEqualTo(2);
        assertThat(payload.population().predation()).isEqualTo(3);
        assertThat(payload.population().missing()).isEqualTo(1);
        assertThat(payload.population().returned()).isEqualTo(1);
        assertThat(payload.population().legacyMortalityRecords()).isEqualTo(4);
        assertThat(payload.healthEvents()).hasSize(1);
        assertThat(payload.finance()).isNull();
        assertThat(payload.dataAvailability()).anyMatch(value -> value.section().equals("Population events")
                && value.recordCount() == 5);
    }

    @Test
    void savesSnapshotWithoutClosingBatchAndReturnsSameSnapshotForRetry() throws Exception {
        Batch batch = new Batch();
        batch.setId(8L);
        batch.setFarmId(3L);
        batch.setName("Review batch");
        batch.setInitialPopulation(20);
        batch.setCurrentPopulation(20);
        batch.setStartDate(LocalDate.of(2026, 9, 1));
        batch.setStatus(BatchStatus.ACTIVE);
        LifecycleStage stage = new LifecycleStage("brooding", 0);

        when(batchService.requireBatch(8L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(stage, true));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                8L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20))).thenReturn(List.of());
        when(inputRepository.findByFarmIdAndBatchIdOrderByRecordedAtDesc(3L, 8L)).thenReturn(List.of());
        when(incubationRepository.findByFarmIdAndCreatedBatchId(3L, 8L)).thenReturn(List.of());
        when(reviewRepository.countByFarmIdAndBatchId(3L, 8L)).thenReturn(0L);
        when(reviewRepository.save(any(BatchSelectionReview.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService, objectMapper);
        CreateSelectionReviewRequest request = new CreateSelectionReviewRequest(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 20),
                "SELECTION_REVIEW", "Before manager consultation", "retry-key-8");

        SelectionReviewResponse first = service.create(8L, 3L, 11L, request);

        assertThat(first.status()).isEqualTo(SelectionReviewStatus.DRAFT);
        assertThat(first.versionNumber()).isEqualTo(1);
        assertThat(first.purpose()).isEqualTo("SELECTION_REVIEW");
        assertThat(first.snapshotNote()).isEqualTo("Before manager consultation");
        assertThat(batch.getStatus()).isEqualTo(BatchStatus.ACTIVE);

        BatchSelectionReview saved = new BatchSelectionReview();
        saved.setId(44L);
        saved.setFarmId(3L);
        saved.setBatchId(8L);
        saved.setPeriodStart(first.periodStart());
        saved.setPeriodEnd(first.periodEnd());
        saved.setAsOfDate(first.asOfDate());
        saved.setStatus(first.status());
        saved.setReviewStatus(first.reviewStatus());
        saved.setPayloadVersion(first.payloadVersion());
        saved.setPayloadJson(objectMapper.writeValueAsString(first.payload()));
        saved.setGeneratedBy(first.generatedBy());
        saved.setGeneratedAt(first.generatedAt());
        saved.setSourceCutoffAt(first.sourceCutoffAt());
        saved.setVersionNumber(first.versionNumber());
        saved.setPurpose(first.purpose());
        saved.setSnapshotNote(first.snapshotNote());
        saved.setIdempotencyKey("retry-key-8");
        when(reviewRepository.findByFarmIdAndBatchIdAndIdempotencyKey(3L, 8L, "retry-key-8"))
                .thenReturn(Optional.of(saved));

        SelectionReviewResponse retry = service.create(8L, 3L, 11L, request);

        assertThat(retry.id()).isEqualTo(44L);
        assertThat(retry.versionNumber()).isEqualTo(1);
        assertThat(retry.snapshotNote()).isEqualTo("Before manager consultation");
    }

    private static BatchEvent event(EventType type, int affected, int delta) {
        BatchEvent event = new BatchEvent();
        event.setId((long) affected + type.ordinal());
        event.setEventDate(LocalDate.of(2026, 9, 10));
        event.setEventType(type);
        event.setAffectedCount(affected);
        event.setPopulationDelta(delta);
        event.setTitle(type.name());
        event.setHandlerId(99L);
        return event;
    }
}
