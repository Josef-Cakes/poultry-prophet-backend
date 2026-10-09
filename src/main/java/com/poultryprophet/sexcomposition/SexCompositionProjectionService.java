package com.poultryprophet.sexcomposition;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.common.BadRequestException;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.BatchEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Projects male/female/unclassified counts without mutating historical sex snapshots.
 * A snapshot is the baseline; attributed population events are the append-only adjustments.
 */
@Service
public class SexCompositionProjectionService {
    private final BatchSexCompositionRepository compositionRepository;
    private final BatchEventRepository eventRepository;
    private final ZoneId farmZone = ZoneId.of("Asia/Manila");

    public SexCompositionProjectionService(BatchSexCompositionRepository compositionRepository,
                                           BatchEventRepository eventRepository) {
        this.compositionRepository = compositionRepository;
        this.eventRepository = eventRepository;
    }

    public boolean hasBaseline(Long batchId, Long farmId, LocalDate asOfDate) {
        return findBaseline(batchId, farmId, asOfDate) != null;
    }

    public SexCompositionProjection project(Batch batch, Long farmId, LocalDate asOfDate) {
        LocalDate asOf = asOfDate == null ? LocalDate.now(farmZone) : asOfDate;
        BatchSexComposition baseline = findBaseline(batch.getId(), farmId, asOf);
        if (baseline == null) {
            return new SexCompositionProjection(0, 0, 0, null, asOf, null, null,
                    SexCompositionProjection.NO_BASELINE,
                    "Record the whole-batch male, female, and unclassified count first.");
        }
        return apply(baseline, eventsThrough(batch, asOf), asOf, null);
    }

    /** Validates a new event before it is committed. */
    public void validateCandidate(Batch batch, Long farmId, LocalDate eventDate, BatchEvent candidate) {
        SexCompositionProjection before = project(batch, farmId, eventDate);
        if (!before.hasBaseline()) {
            throw new BadRequestException("Record the whole-batch male/female count before assigning a population event to a sex");
        }
        if (!before.valid()) {
            throw new BadRequestException("Sex composition needs reconciliation before another sex-specific event can be recorded");
        }
        int male = before.maleCount() + candidate.getMaleDelta();
        int female = before.femaleCount() + candidate.getFemaleDelta();
        int unclassified = before.unclassifiedCount() + candidate.getUnclassifiedDelta();
        if (male < 0 || female < 0 || unclassified < 0) {
            throw new BadRequestException("This event would make a male, female, or unclassified count negative");
        }

        LocalDate today = LocalDate.now(farmZone);
        List<BatchEvent> withCandidate = new ArrayList<>(eventsThrough(batch, today));
        withCandidate.add(candidate);
        SexCompositionProjection after = apply(
                findBaseline(batch.getId(), farmId, today), withCandidate, today, candidate);
        if (SexCompositionProjection.NEGATIVE_CATEGORY.equals(after.status())
                || SexCompositionProjection.MISSING_ALLOCATION.equals(after.status())) {
            throw new BadRequestException(after.message());
        }
    }

    private BatchSexComposition findBaseline(Long batchId, Long farmId, LocalDate asOf) {
        return compositionRepository
                .findFirstByBatchIdAndFarmIdAndObservedOnLessThanEqualOrderByObservedOnDescCreatedAtDesc(
                        batchId, farmId, asOf)
                .orElse(null);
    }

    private List<BatchEvent> eventsThrough(Batch batch, LocalDate asOf) {
        return eventRepository.findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
                batch.getId(), batch.getStartDate(), asOf);
    }

    private SexCompositionProjection apply(BatchSexComposition baseline, List<BatchEvent> sourceEvents,
                                            LocalDate asOf, BatchEvent candidate) {
        if (baseline == null) {
            return new SexCompositionProjection(0, 0, 0, null, asOf, null, null,
                    SexCompositionProjection.NO_BASELINE,
                    "Record the whole-batch male, female, and unclassified count first.");
        }

        long cursor = baseline.getBaselineEventId() == null ? 0L : baseline.getBaselineEventId();
        int male = baseline.getMaleCount();
        int female = baseline.getFemaleCount();
        int unclassified = baseline.getUnclassifiedCount();
        boolean missing = false;
        List<BatchEvent> events = new ArrayList<>(sourceEvents == null ? List.of() : sourceEvents);
        events.sort(Comparator.comparing(BatchEvent::getEventDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BatchEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BatchEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        for (BatchEvent event : events) {
            if (event == candidate || event.getEventType() == null || !event.getEventType().isPopulationLedgerEvent()) continue;
            if (!belongsAfterBaseline(event, baseline, cursor)) continue;
            if (event.getMaleDelta() == null || event.getFemaleDelta() == null || event.getUnclassifiedDelta() == null) {
                missing = true;
                continue;
            }
            male += event.getMaleDelta();
            female += event.getFemaleDelta();
            unclassified += event.getUnclassifiedDelta();
            if (male < 0 || female < 0 || unclassified < 0) {
                return invalid(baseline, asOf, SexCompositionProjection.NEGATIVE_CATEGORY,
                        "Sex-specific population history contains a negative category; review the event log.");
            }
        }

        if (candidate != null) {
            male += candidate.getMaleDelta();
            female += candidate.getFemaleDelta();
            unclassified += candidate.getUnclassifiedDelta();
            if (male < 0 || female < 0 || unclassified < 0) {
                return invalid(baseline, asOf, SexCompositionProjection.NEGATIVE_CATEGORY,
                        "This event would make a male, female, or unclassified count negative.");
            }
        }
        if (missing) {
            return invalid(baseline, asOf, SexCompositionProjection.MISSING_ALLOCATION,
                    "A population event after the sex baseline has no sex allocation; review that event first.");
        }
        return new SexCompositionProjection(male, female, unclassified, baseline.getObservedOn(), asOf,
                baseline.getId(), baseline.getBaselineEventId(), SexCompositionProjection.VALID, null);
    }

    private boolean belongsAfterBaseline(BatchEvent event, BatchSexComposition baseline, long cursor) {
        if (event.getEventDate() == null) return false;
        if (event.getEventDate().isAfter(baseline.getObservedOn())) return true;
        return event.getId() != null && event.getId() > cursor;
    }

    private SexCompositionProjection invalid(BatchSexComposition baseline, LocalDate asOf,
                                             String status, String message) {
        return new SexCompositionProjection(baseline.getMaleCount(), baseline.getFemaleCount(),
                baseline.getUnclassifiedCount(), baseline.getObservedOn(), asOf, baseline.getId(),
                baseline.getBaselineEventId(), status, message);
    }
}
