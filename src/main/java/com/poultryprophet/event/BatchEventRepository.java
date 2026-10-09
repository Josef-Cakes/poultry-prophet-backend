package com.poultryprophet.event;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BatchEventRepository extends JpaRepository<BatchEvent, Long> {

    List<BatchEvent> findByBatchIdOrderByEventDateDescCreatedAtDesc(Long batchId, Pageable pageable);

    List<BatchEvent> findByBatchIdAndEventDateBetweenOrderByEventDateAscCreatedAtAsc(
            Long batchId, LocalDate start, LocalDate end);

    List<BatchEvent> findByBatchIdAndEventDateAndEventType(Long batchId, LocalDate date, EventType type);

    Optional<BatchEvent> findByOperationId(UUID operationId);

    Optional<BatchEvent> findTopByBatchIdAndEventDateLessThanEqualOrderByIdDesc(Long batchId, LocalDate eventDate);

    @Query("select e from BatchEvent e "
            + "where e.eventType in :eventTypes "
            + "and not exists (select a.id from Alert a where a.sourceEvent.id = e.id) "
            + "order by e.createdAt asc")
    List<BatchEvent> findDeathEventsMissingAlerts(@Param("eventTypes") List<EventType> eventTypes);

    boolean existsByBatchIdAndCreatedAtAfter(Long batchId, Instant cutoff);

    @Query("select coalesce(sum(e.affectedCount), 0) from BatchEvent e "
            + "where e.batchId = :batchId and e.eventDate = :date and e.eventType = :type")
    long sumAffectedCountByBatchIdAndEventDateAndEventType(@Param("batchId") Long batchId,
                                                            @Param("date") LocalDate date,
                                                            @Param("type") EventType type);

}
