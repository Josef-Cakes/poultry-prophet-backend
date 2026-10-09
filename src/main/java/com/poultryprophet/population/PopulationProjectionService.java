package com.poultryprophet.population;

import com.poultryprophet.batch.Batch;
import com.poultryprophet.event.BatchEvent;
import com.poultryprophet.event.EventType;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The single read-side population projection used by reports, comparisons, and selection
 * validation.  It replays the append-only event ledger deterministically and exposes data
 * quality problems instead of silently clamping them to zero.
 */
@Component
public class PopulationProjectionService {

    public PopulationProjection project(Batch batch, List<BatchEvent> sourceEvents,
                                         LocalDate asOfDate, ZoneId farmZone) {
        return projectInternal(batch, sourceEvents, asOfDate, farmZone, true);
    }

    /**
     * Replays only the append-only event ledger. This is intentionally separate from
     * {@link #project(Batch, List, LocalDate, ZoneId)} because a write validation can be
     * evaluating an event that has not yet been applied to the stored current count.
     * Comparing that projected post-event value with the pre-event batch row would reject
     * legitimate events and is the source of the sync failure seen in the validation app.
     */
    public PopulationProjection projectLedger(Batch batch, List<BatchEvent> sourceEvents,
                                              LocalDate asOfDate, ZoneId farmZone) {
        return projectInternal(batch, sourceEvents, asOfDate, farmZone, false);
    }

    private PopulationProjection projectInternal(Batch batch, List<BatchEvent> sourceEvents,
                                                  LocalDate asOfDate, ZoneId farmZone,
                                                  boolean compareStoredPopulation) {
        List<BatchEvent> events = new ArrayList<>(sourceEvents == null ? List.of() : sourceEvents);
        events.sort(Comparator
                .comparing(BatchEvent::getEventDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BatchEvent::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(BatchEvent::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        int initial = batch.getInitialPopulation();
        long calculated = initial;
        String issueCode = null;
        Long firstInvalidEventId = null;
        LocalDate firstInvalidEventDate = null;

        for (BatchEvent event : events) {
            DeltaResolution resolution = resolveDelta(event);
            if (!resolution.valid()) {
                if (issueCode == null) {
                    issueCode = "INVALID_EVENT";
                    firstInvalidEventId = event.getId();
                    firstInvalidEventDate = event.getEventDate();
                }
                continue;
            }

            calculated += resolution.delta();
            if (issueCode == null && calculated < 0) {
                issueCode = "BELOW_ZERO";
                firstInvalidEventId = event.getId();
                firstInvalidEventDate = event.getEventDate();
            } else if (issueCode == null && calculated > initial) {
                issueCode = "ABOVE_INITIAL";
                firstInvalidEventId = event.getId();
                firstInvalidEventDate = event.getEventDate();
            }
        }

        boolean currentAsOfDate = asOfDate != null && farmZone != null
                && asOfDate.equals(LocalDate.now(farmZone));
        int currentPopulation = batch.getCurrentPopulation();
        if (compareStoredPopulation) {
            if (issueCode == null && (currentPopulation < 0 || currentPopulation > initial)) {
                issueCode = "STORED_COUNT_OUT_OF_RANGE";
            } else if (issueCode == null && currentAsOfDate && calculated != currentPopulation) {
                issueCode = "STORED_COUNT_MISMATCH";
            }
        }

        boolean reconciliationRequired = issueCode != null;
        Integer validPopulation = reconciliationRequired ? null : toInt(calculated);
        int boundedPopulation = bounded(calculated, initial);
        String status = reconciliationRequired ? PopulationProjection.RECONCILIATION_REQUIRED
                : PopulationProjection.VALID;
        String message = reconciliationRequired
                ? message(batch, calculated, currentAsOfDate, issueCode, firstInvalidEventDate,
                compareStoredPopulation)
                : null;

        return new PopulationProjection(initial, calculated, validPopulation, boundedPopulation,
                events.size(), reconciliationRequired, status, issueCode, firstInvalidEventId,
                firstInvalidEventDate, message);
    }

    /** Returns the canonical stored or derived delta for read-side category totals. */
    public long effectiveDelta(BatchEvent event) {
        DeltaResolution resolution = resolveDelta(event);
        return resolution.valid() ? resolution.delta() : 0L;
    }

    private DeltaResolution resolveDelta(BatchEvent event) {
        if (event == null) return new DeltaResolution(0L, false);
        if (event.getPopulationDelta() != null) {
            return new DeltaResolution(event.getPopulationDelta(), true);
        }
        EventType type = event.getEventType();
        if (type == null) return new DeltaResolution(0L, false);
        try {
            if (!type.isPopulationLedgerEvent()) return new DeltaResolution(0L, true);
            return new DeltaResolution(type.populationDelta(event.getAffectedCount(), null), true);
        } catch (IllegalArgumentException exception) {
            return new DeltaResolution(0L, false);
        }
    }

    private String message(Batch batch, long calculated, boolean currentAsOfDate,
                           String issueCode, LocalDate firstInvalidEventDate,
                           boolean compareStoredPopulation) {
        if ("INVALID_EVENT".equals(issueCode)) {
            return "A population event has invalid ledger data"
                    + dateSuffix(firstInvalidEventDate) + "; review the event before using this count.";
        }
        if (compareStoredPopulation && currentAsOfDate && calculated != batch.getCurrentPopulation()
                && ("BELOW_ZERO".equals(issueCode) || "ABOVE_INITIAL".equals(issueCode))) {
            return "Population events calculate " + calculated + " alive, while the batch record contains "
                    + batch.getCurrentPopulation() + "; review duplicate, legacy, or incorrect population events.";
        }
        if ("BELOW_ZERO".equals(issueCode) || "ABOVE_INITIAL".equals(issueCode)) {
            return "Population events calculate " + calculated + " alive, outside the valid range of 0 to "
                    + Math.max(0, batch.getInitialPopulation()) + dateSuffix(firstInvalidEventDate)
                    + "; review duplicate, legacy, or incorrect population events.";
        }
        if ("STORED_COUNT_OUT_OF_RANGE".equals(issueCode)) {
            return "The stored current count is " + batch.getCurrentPopulation()
                    + ", outside the valid range of 0 to " + Math.max(0, batch.getInitialPopulation())
                    + "; review the batch count before using it.";
        }
        if (compareStoredPopulation && currentAsOfDate && "STORED_COUNT_MISMATCH".equals(issueCode)) {
            return "Population events calculate " + calculated + " alive, while the batch record contains "
                    + batch.getCurrentPopulation() + "; review duplicate, legacy, or incorrect population events.";
        }
        return "Population records need reconciliation before this count can be used.";
    }

    private String dateSuffix(LocalDate date) {
        return date == null ? "" : " on " + date;
    }

    private int bounded(long value, int initial) {
        long upper = Math.max(0L, initial);
        return (int) Math.max(0L, Math.min(upper, value));
    }

    private Integer toInt(long value) {
        return value < Integer.MIN_VALUE || value > Integer.MAX_VALUE ? null : (int) value;
    }

    private record DeltaResolution(long delta, boolean valid) {}
}
