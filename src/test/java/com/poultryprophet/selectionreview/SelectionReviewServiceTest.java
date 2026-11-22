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
import com.poultryprophet.input.FarmInputLog;
import com.poultryprophet.input.InputProductType;
import com.poultryprophet.inventory.InventoryMovement;
import com.poultryprophet.inventory.InventoryMovementRepository;
import com.poultryprophet.inventory.InventoryMovementType;
import com.poultryprophet.population.PopulationProjectionService;
import com.poultryprophet.sexcomposition.BatchSexComposition;
import com.poultryprophet.sexcomposition.BatchSexCompositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SelectionReviewServiceTest {
    @Mock private BatchSelectionReviewRepository reviewRepository;
    @Mock private BatchEventRepository eventRepository;
    @Mock private FarmInputLogRepository inputRepository;
    @Mock private IncubationCycleRepository incubationRepository;
    @Mock private FinancialTransactionRepository financeRepository;
    @Mock private BatchService batchService;
    @Mock private BatchSexCompositionRepository sexCompositionRepository;
    @Mock private InventoryMovementRepository inventoryMovementRepository;

    @Test
    void hidesProductCostsFromHandlerInLiveAndSavedReviewsEvenWithoutFinanceSection() throws Exception {
        LocalDate date = LocalDate.of(2026, 10, 9);
        Batch batch = new Batch();
        batch.setId(7L); batch.setFarmId(3L); batch.setName("Batch");
        batch.setInitialPopulation(100); batch.setCurrentPopulation(100); batch.setStartDate(date);
        when(batchService.requireBatch(7L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(new LifecycleStage("brooding", 0), true));
        FarmInputLog input = new FarmInputLog();
        input.setId(55L); input.setRecordedAt(Instant.parse("2026-10-09T03:00:00Z"));
        input.setProductType(InputProductType.FEED); input.setBrandName("Feed");
        input.setQuantity(new BigDecimal("3")); input.setUnit("kg");
        InventoryMovement movement = new InventoryMovement();
        movement.setMovementType(InventoryMovementType.USAGE); movement.setFarmInputLogId(55L); movement.setUnitCostSnapshot(new BigDecimal("50"));
        movement.setInventoryValueDelta(new BigDecimal("-150"));
        when(inputRepository.findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(eq(3L), eq(7L), any(), any())).thenReturn(List.of(input));
        when(inventoryMovementRepository.findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(eq(3L), eq(7L), any(), any())).thenReturn(List.of(movement));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository, inputRepository,
                incubationRepository, financeRepository, batchService, mapper, null, new PopulationProjectionService(),
                null, null, inventoryMovementRepository);

        SelectionReviewPayload handler = service.preview(7L, 3L, date, date, date, false);
        assertThat(handler.batch().ageDays()).isZero();
        assertThat(handler.productUse()).hasSize(1);
        assertThat(handler.productUse().get(0).quantity()).isEqualByComparingTo("3");
        assertThat(handler.productUse().get(0).unitCost()).isNull();
        assertThat(handler.productUse().get(0).calculatedCost()).isNull();
        assertThat(handler.productUse().get(0).costStatus()).isNull();
        SelectionReviewPayload manager = service.preview(7L, 3L, date, date, date, true);
        assertThat(manager.productUse().get(0).unitCost()).isEqualByComparingTo("50");
        assertThat(manager.productUse().get(0).calculatedCost()).isEqualByComparingTo("150");

        var json = mapper.valueToTree(handler);
        var item = (com.fasterxml.jackson.databind.node.ObjectNode) json.path("productUse").get(0);
        item.put("unitCost", 50); item.put("calculatedCost", 150); item.put("costStatus", "VALUED");
        BatchSelectionReview review = new BatchSelectionReview();
        review.setId(44L); review.setFarmId(3L); review.setBatchId(7L); review.setPayloadJson(mapper.writeValueAsString(json));
        when(reviewRepository.findByIdAndFarmIdAndBatchId(44L, 3L, 7L)).thenReturn(Optional.of(review));
        SelectionReviewPayload saved = service.get(7L, 3L, 44L, false).payload();
        assertThat(saved.productUse().get(0).unitCost()).isNull();
        assertThat(saved.productUse().get(0).calculatedCost()).isNull();
        assertThat(saved.productUse().get(0).costStatus()).isNull();
    }

    @Test
    void marksMissingProductUseCostsPartialWithoutDoubleCountingAndKeepsKnownSubtotal() {
        LocalDate date = LocalDate.of(2026, 10, 9);
        Batch batch = new Batch();
        batch.setId(7L); batch.setFarmId(3L); batch.setName("Batch");
        batch.setInitialPopulation(100); batch.setCurrentPopulation(100); batch.setStartDate(date);
        when(batchService.requireBatch(7L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(new LifecycleStage("brooding", 0), true));
        FarmInputLog valued = costInput(1L, new BigDecimal("70"));
        FarmInputLog missingMovement = costInput(2L, null);
        FarmInputLog unvaluedMovement = costInput(3L, null);
        FarmInputLog free = costInput(4L, BigDecimal.ONE);
        FarmInputLog missingQuantity = costInput(5L, null);
        when(inputRepository.findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(eq(3L), eq(7L), any(), any()))
                .thenReturn(List.of(valued, missingMovement, unvaluedMovement, free, missingQuantity));
        InventoryMovement known = costMovement(1L, "-1890");
        InventoryMovement unknown = costMovement(3L, null);
        InventoryMovement freeMovement = costMovement(4L, "0");
        InventoryMovement quantityUnknown = costMovement(5L, "0");
        InventoryMovement unrelated = costMovement(2L, "-500");
        unrelated.setMovementType(InventoryMovementType.STOCK_IN);
        when(inventoryMovementRepository.findByFarmIdAndBatchIdAndOccurredAtBetweenOrderByOccurredAtAsc(eq(3L), eq(7L), any(), any()))
                .thenReturn(List.of(known, unknown, freeMovement, quantityUnknown, unrelated));
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository, inputRepository,
                incubationRepository, financeRepository, batchService, new ObjectMapper().registerModule(new JavaTimeModule()),
                null, new PopulationProjectionService(), null, null, inventoryMovementRepository);

        var report = service.preview(7L, 3L, date, date, date, true);
        assertThat(report.productUse()).filteredOn(item -> item.sourceId().equals(2L))
                .singleElement().satisfies(item -> assertThat(item.calculatedCost()).isNull());
        var finance = report.finance();
        assertThat(finance.totalRecordedBatchCost()).isEqualByComparingTo("1890");
        assertThat(finance.recordsMayBeIncomplete()).isTrue();
        assertThat(finance.unvaluedUsageCount()).isEqualTo(3);
        assertThat(finance.currencyTotals()).singleElement().satisfies(total -> {
            assertThat(total.currency()).isEqualTo("PHP");
            assertThat(total.recordsMayBeIncomplete()).isTrue();
            assertThat(total.totalRecordedBatchCost()).isEqualByComparingTo("1890");
        });
    }

    private static FarmInputLog costInput(long id, BigDecimal quantity) {
        FarmInputLog input = new FarmInputLog(); input.setId(id);
        input.setRecordedAt(Instant.parse("2026-10-09T03:00:00Z"));
        input.setProductType(InputProductType.FEED); input.setBrandName("Feed");
        input.setQuantity(quantity); input.setUnit("pack"); return input;
    }

    private static InventoryMovement costMovement(long inputId, String cost) {
        InventoryMovement movement = new InventoryMovement();
        movement.setMovementType(InventoryMovementType.USAGE); movement.setFarmInputLogId(inputId);
        movement.setInventoryValueDelta(cost == null ? null : new BigDecimal(cost)); return movement;
    }

    @Test
    void requiresAConcreteManagerDecisionBeforeFinalizingAReport() {
        Batch batch = new Batch();
        batch.setId(7L);
        batch.setFarmId(3L);
        batch.setStartDate(LocalDate.of(2026, 9, 1));
        BatchSelectionReview review = new BatchSelectionReview();
        review.setId(44L);
        review.setFarmId(3L);
        review.setBatchId(7L);
        review.setStatus(SelectionReviewStatus.DRAFT);
        review.setAsOfDate(LocalDate.of(2026, 9, 20));
        when(batchService.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(reviewRepository.findByIdAndFarmIdAndBatchId(44L, 3L, 7L)).thenReturn(Optional.of(review));

        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService,
                new ObjectMapper().registerModule(new JavaTimeModule()));

        assertThatThrownBy(() -> service.finalize(7L, 3L, 44L, 11L,
                new FinalizeSelectionReviewRequest(ManagerReviewStatus.NOT_REVIEWED, null, null)))
                .isInstanceOf(com.poultryprophet.common.BadRequestException.class)
                .hasMessageContaining("Choose a review decision");
        verify(reviewRepository, never()).save(any());
    }

    @Test
    void rejectsFollowUpDatesBeforeTodayEvenWhenAfterTheReportDate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        Batch batch = new Batch(); batch.setId(7L); batch.setFarmId(3L); batch.setStartDate(today.minusDays(10));
        BatchSelectionReview review = new BatchSelectionReview();
        review.setId(44L); review.setFarmId(3L); review.setBatchId(7L);
        review.setStatus(SelectionReviewStatus.DRAFT); review.setAsOfDate(today.minusDays(3));
        when(batchService.requireBatchForUpdate(7L, 3L)).thenReturn(batch);
        when(reviewRepository.findByIdAndFarmIdAndBatchId(44L, 3L, 7L)).thenReturn(Optional.of(review));
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService,
                new ObjectMapper().registerModule(new JavaTimeModule()));
        assertThatThrownBy(() -> service.finalize(7L, 3L, 44L, 11L,
                new FinalizeSelectionReviewRequest(ManagerReviewStatus.CONTINUE_OBSERVATION, null, today.minusDays(1))))
                .isInstanceOf(com.poultryprophet.common.BadRequestException.class).hasMessageContaining("today or later");
        verify(reviewRepository, never()).save(any());
    }

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
        when(inputRepository.findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(eq(3L), eq(7L), any(), any())).thenReturn(List.of());
        when(incubationRepository.findByFarmIdAndCreatedBatchId(3L, 7L)).thenReturn(List.of());

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService, objectMapper);

        SelectionReviewPayload payload = service.preview(7L, 3L,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 20),
                LocalDate.of(2026, 9, 20), false);

        verify(inputRepository).findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(
                3L, 7L, java.time.Instant.parse("2026-08-31T16:00:00Z"),
                java.time.Instant.parse("2026-09-20T16:00:00Z"));

        assertThat(payload.population().healthRelatedDeaths()).isEqualTo(2);
        assertThat(payload.batch().ageDays()).isEqualTo(19);
        assertThat(payload.population().totalDeaths()).isEqualTo(2);
        assertThat(payload.population().predation()).isEqualTo(3);
        assertThat(payload.population().missing()).isEqualTo(1);
        assertThat(payload.population().returned()).isEqualTo(1);
        assertThat(payload.population().legacyMortalityRecords()).isEqualTo(4);
        assertThat(payload.population().currentPopulation()).isEqualTo(91);
        assertThat(payload.population().reconciliationRequired()).isFalse();
        assertThat(payload.healthEvents()).hasSize(1);
        assertThat(payload.finance()).isNull();
        assertThat(payload.dataAvailability()).anyMatch(value -> value.section().equals("Population events")
                && value.recordCount() == 5);
    }

    @Test
    void preservesLegitimateTransferredPopulationInLiveAndSavedReports() throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 1), end = LocalDate.of(2026, 9, 20);
        Batch batch = new Batch(); batch.setId(7L); batch.setFarmId(3L); batch.setName("Flock");
        batch.setStartDate(start); batch.setInitialPopulation(10); batch.setCurrentPopulation(11);
        when(batchService.requireBatch(7L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(new LifecycleStage("brooding", 0), true));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(7L, start, end))
                .thenReturn(List.of(event(EventType.TRANSFER_IN, 1, 1)));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService, mapper);
        SelectionReviewPayload live = service.preview(7L, 3L, start, end, end, false);
        assertThat(live.population().currentPopulation()).isEqualTo(11);
        assertThat(live.population().reconciliationRequired()).isFalse();
        BatchSelectionReview saved = new BatchSelectionReview();
        saved.setId(44L); saved.setFarmId(3L); saved.setBatchId(7L); saved.setPayloadJson(mapper.writeValueAsString(live));
        when(reviewRepository.findByIdAndFarmIdAndBatchId(44L, 3L, 7L)).thenReturn(Optional.of(saved));
        SelectionReviewPayload loaded = service.get(7L, 3L, 44L, false).payload();
        assertThat(loaded.population().currentPopulation()).isEqualTo(11);
        assertThat(loaded.population().reconciliationRequired()).isFalse();
    }

    @Test
    void reportsTotalDeathsAcrossHealthAndAccidentalCauses() {
        Batch batch = new Batch();
        batch.setId(10L);
        batch.setFarmId(3L);
        batch.setName("Death breakdown batch");
        batch.setInitialPopulation(500);
        batch.setCurrentPopulation(175);
        batch.setStartDate(LocalDate.of(2026, 10, 1));
        LifecycleStage stage = new LifecycleStage("brooding", 0);

        when(batchService.requireBatch(10L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(stage, true));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                10L, batch.getStartDate(), LocalDate.of(2026, 10, 9)))
                .thenReturn(List.of(event(EventType.HEALTH_DEATH, 100, -100),
                        event(EventType.HEALTH_DEATH, 100, -100),
                        event(EventType.HEALTH_DEATH, 120, -120),
                        event(EventType.ACCIDENTAL_DEATH, 5, -5)));
        when(inputRepository.findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(eq(3L), eq(10L), any(), any())).thenReturn(List.of());
        when(incubationRepository.findByFarmIdAndCreatedBatchId(3L, 10L)).thenReturn(List.of());

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService, objectMapper);

        SelectionReviewPayload payload = service.preview(10L, 3L,
                batch.getStartDate(), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9), false);

        assertThat(payload.population().healthRelatedDeaths()).isEqualTo(320);
        assertThat(payload.population().accidentalDeaths()).isEqualTo(5);
        assertThat(payload.population().totalDeaths()).isEqualTo(325);
        assertThat(payload.population().currentPopulation()).isEqualTo(175);
    }

    @Test
    void includesLatestSexCompositionAtOrBeforeReportDate() {
        Batch batch = new Batch();
        batch.setId(11L);
        batch.setFarmId(3L);
        batch.setName("Sexed batch");
        batch.setInitialPopulation(100);
        batch.setCurrentPopulation(100);
        batch.setStartDate(LocalDate.of(2026, 10, 1));
        LifecycleStage stage = new LifecycleStage("brooding", 0);

        when(batchService.requireBatch(11L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(stage, true));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                11L, batch.getStartDate(), LocalDate.of(2026, 10, 9))).thenReturn(List.of());
        when(inputRepository.findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(eq(3L), eq(11L), any(), any())).thenReturn(List.of());
        when(incubationRepository.findByFarmIdAndCreatedBatchId(3L, 11L)).thenReturn(List.of());

        BatchSexComposition sex = new BatchSexComposition();
        sex.setId(21L);
        sex.setBatchId(11L);
        sex.setFarmId(3L);
        sex.setObservedOn(LocalDate.of(2026, 10, 8));
        sex.setPopulationAsOfObservation(100);
        sex.setMaleCount(55);
        sex.setFemaleCount(40);
        sex.setUnclassifiedCount(5);
        sex.setRecordedBy(99L);
        when(sexCompositionRepository
                .findFirstByBatchIdAndFarmIdAndObservedOnLessThanEqualOrderByObservedOnDescCreatedAtDesc(
                        11L, 3L, LocalDate.of(2026, 10, 9)))
                .thenReturn(Optional.of(sex));

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService, objectMapper,
                null, new PopulationProjectionService(), sexCompositionRepository);

        SelectionReviewPayload payload = service.preview(11L, 3L,
                batch.getStartDate(), LocalDate.of(2026, 10, 9), LocalDate.of(2026, 10, 9), false);

        assertThat(payload.sexComposition()).isNotNull();
        assertThat(payload.sexComposition().maleCount()).isEqualTo(55);
        assertThat(payload.sexComposition().femaleCount()).isEqualTo(40);
        assertThat(payload.sexComposition().unclassifiedCount()).isEqualTo(5);
        assertThat(payload.dataAvailability()).anyMatch(value -> value.section().equals("Sex composition")
                && value.status().equals("AVAILABLE"));
    }

    @Test
    void usesTheBoundedBatchCountAndFlagsAnImpossibleCurrentLedgerTotal() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Manila"));
        Batch batch = new Batch();
        batch.setId(9L);
        batch.setFarmId(3L);
        batch.setName("Legacy event batch");
        batch.setInitialPopulation(100);
        batch.setCurrentPopulation(39);
        batch.setStartDate(today.minusDays(23));
        LifecycleStage stage = new LifecycleStage("brooding", 0);
        BatchEvent invalidLegacyTotal = event(EventType.MORTALITY, 161, -161);
        invalidLegacyTotal.setEventDate(today.minusDays(2));

        when(batchService.requireBatch(9L, 3L)).thenReturn(batch);
        when(batchService.resolveStage(batch)).thenReturn(new BatchService.StageView(stage, true));
        when(eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                9L, batch.getStartDate(), today)).thenReturn(List.of(invalidLegacyTotal));
        when(inputRepository.findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(eq(3L), eq(9L), any(), any())).thenReturn(List.of());
        when(incubationRepository.findByFarmIdAndCreatedBatchId(3L, 9L)).thenReturn(List.of());

        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        SelectionReviewService service = new SelectionReviewService(reviewRepository, eventRepository,
                inputRepository, incubationRepository, financeRepository, batchService, objectMapper);

        SelectionReviewPayload payload = service.preview(9L, 3L,
                batch.getStartDate(), today, today, false);

        assertThat(payload.population().calculatedPopulationFromEvents()).isEqualTo(-61);
        assertThat(payload.population().currentPopulation()).isEqualTo(39);
        assertThat(payload.batch().currentPopulationDisplay()).isEqualTo("39 / 100");
        assertThat(payload.population().reconciliationRequired()).isTrue();
        assertThat(payload.population().reconciliationMessage())
                .contains("calculate -61 alive")
                .contains("batch record contains 39");
        assertThat(payload.dataAvailability()).anyMatch(item ->
                item.section().equals("Population reconciliation") && item.status().equals("PARTIAL"));
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
        when(inputRepository.findByFarmIdAndBatchIdAndRecordedAtGreaterThanEqualAndRecordedAtLessThanOrderByRecordedAtDesc(eq(3L), eq(8L), any(), any())).thenReturn(List.of());
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
